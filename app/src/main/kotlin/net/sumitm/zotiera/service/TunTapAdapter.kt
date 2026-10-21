package net.sumitm.zotiera.service

import android.os.ParcelFileDescriptor
import android.util.Log
import com.zerotier.sdk.Node
import com.zerotier.sdk.ResultCode
import com.zerotier.sdk.VirtualNetworkFrameListener
import com.zerotier.sdk.util.StringUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.sumitm.zotiera.util.DebugLog
import net.sumitm.zotiera.util.IPPacketUtils
import net.sumitm.zotiera.util.InetAddressUtils
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.HashMap

class TunTapAdapter(internal val ztService: ZeroTierOneService) : VirtualNetworkFrameListener {

    internal val routeMap = HashMap<Route, Long>()
    internal var node: Node? = null

    private var input: FileInputStream? = null
    private var output: FileOutputStream? = null
    private var vpnSocket: ParcelFileDescriptor? = null

    // ARP / NDP 按网络隔离：不同网络的 MAC 空间互不相干
    private val arpTables = HashMap<Long, ARPTable>()
    private val ndpTables = HashMap<Long, NDPTable>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiveJob: Job? = null

    internal fun arpTableFor(networkId: Long): ARPTable = synchronized(this) {
        arpTables.getOrPut(networkId) { ARPTable() }
    }

    internal fun ndpTableFor(networkId: Long): NDPTable = synchronized(this) {
        ndpTables.getOrPut(networkId) { NDPTable() }
    }

    private fun clearTables() {
        synchronized(this) {
            arpTables.clear()
            ndpTables.clear()
        }
    }

    private fun stopTables() {
        synchronized(this) {
            arpTables.values.forEach { it.stop() }
            ndpTables.values.forEach { it.stop() }
            arpTables.clear()
            ndpTables.clear()
        }
    }

    /**
     * 出站包属于哪个网络：先按目的地址找路由，找不到再用源地址反查（回包场景）。
     */
    private fun resolveNetworkId(destAddress: InetAddress, sourceAddress: InetAddress): Long {
        val byRoute = networkIdForDestination(destAddress)
        if (byRoute != 0L) {
            return byRoute
        }
        for (id in ztService.getJoinedNetworkIds()) {
            val config = ztService.getVirtualNetworkConfig(id) ?: continue
            for (address in config.assignedAddresses) {
                if (address.address == sourceAddress) {
                    return id
                }
            }
        }
        return 0
    }

    fun setNode(node: Node) {
        this.node = node
        // 组播订阅在 rebuildTunnel 里按地址做；此处节点尚未 join 网络，订阅必然失败
    }

    fun setVpnSocket(vpnSocket: ParcelFileDescriptor) {
        this.vpnSocket = vpnSocket
    }

    fun setFileStreams(input: FileInputStream, output: FileOutputStream) {
        this.input = input
        this.output = output
    }

    fun addRouteAndNetwork(route: Route, networkId: Long) {
        synchronized(routeMap) { routeMap[route] = networkId }
    }

    fun clearRouteMap() {
        synchronized(routeMap) { routeMap.clear() }
        clearTables()
    }

    /** 读 TUN 的协程：把包按 IP 版本分给 [TunTapPackets]。 */
    fun startThreads() {
        if (receiveJob?.isActive == true) {
            return
        }
        receiveJob = scope.launch {
            Log.d(TAG, "TUN Receive Thread Started")
            val buffer = ByteArray(32767)
            while (isActive) {
                try {
                    val source = input
                    if (source == null) {
                        delay(10)
                        continue
                    }
                    val readCount = source.read(buffer)
                    if (readCount < 0) {
                        // 流被关掉了（隧道拆除），收尾退出
                        break
                    }
                    if (readCount > 0) {
                        DebugLog.d(TAG, "Sending packet to ZeroTier. $readCount bytes.")
                        val readData = buffer.copyOf(readCount)
                        when (IPPacketUtils.getIPVersion(readData).toInt()) {
                            4 -> handleIPv4Packet(readData)
                            6 -> handleIPv6Packet(readData)
                            else -> Log.e(TAG, "Unknown IP version")
                        }
                    } else {
                        delay(10)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Log.e(TAG, "Error in TUN Receive: ${e.message}", e)
                }
            }
            Log.d(TAG, "TUN Receive Thread ended")
            stopTables()
        }
    }

    /** 拆隧道：关流让读协程退出，再把表停掉。 */
    fun interrupt() {
        runCatching { input?.close() }.onFailure { Log.e(TAG, "Error stopping in/out: ${it.message}", it) }
        runCatching { output?.close() }.onFailure { Log.e(TAG, "Error stopping in/out: ${it.message}", it) }
        receiveJob?.cancel()
        receiveJob = null
        scope.cancel()
    }

    fun isRunning(): Boolean = receiveJob?.isActive == true

    internal fun isIPv4Multicast(address: InetAddress): Boolean =
        (address.address[0].toInt() and 0xF0) == 224

    internal fun isIPv6Multicast(address: InetAddress): Boolean =
        (address.address[0].toInt() and 0xFF) == 0xFF

    internal fun routeForDestination(destAddress: InetAddress): Route? = synchronized(routeMap) {
        routeMap.keys.firstOrNull { it.belongsToRoute(destAddress) }
    }

    internal fun networkIdForDestination(destAddress: InetAddress): Long = synchronized(routeMap) {
        routeMap.entries.firstOrNull { it.key.belongsToRoute(destAddress) }?.value ?: 0L
    }

    internal fun resolveNetworkIdOf(destAddress: InetAddress, sourceAddress: InetAddress): Long =
        resolveNetworkId(destAddress, sourceAddress)

    internal fun sendFrame(
        networkId: Long,
        srcMac: Long,
        destMac: Long,
        etherType: Int,
        data: ByteArray,
        deadline: LongArray,
    ): ResultCode? = node?.processVirtualNetworkFrame(
        System.currentTimeMillis(),
        networkId,
        srcMac,
        destMac,
        etherType,
        0,
        data,
        deadline,
    )

    /**
     * 响应并处理 ZT 网络发送至本节点的以太网帧。
     */
    override fun onVirtualNetworkFrame(
        networkId: Long,
        srcMac: Long,
        destMac: Long,
        etherType: Long,
        vlanId: Long,
        frameData: ByteArray,
    ) {
        DebugLog.d(
            TAG,
            "Got Virtual Network Frame. Network ID: ${StringUtils.networkIdToString(networkId)}" +
                " Source MAC: ${StringUtils.macAddressToString(srcMac)}" +
                " Dest MAC: ${StringUtils.macAddressToString(destMac)}" +
                " Ether type: ${StringUtils.etherTypeToString(etherType)}" +
                " VLAN ID: $vlanId Frame Length: ${frameData.size}",
        )
        if (vpnSocket == null) {
            Log.e(TAG, "vpnSocket is null!")
            return
        }
        if (input == null || output == null) {
            Log.e(TAG, "no in/out streams")
            return
        }
        when (etherType.toInt()) {
            ARP_PACKET -> handleArpFrame(networkId, srcMac, frameData)
            IPV4_PACKET -> handleIpv4Frame(networkId, srcMac, frameData)
            IPV6_PACKET -> handleIpv6Frame(networkId, srcMac, frameData)
            else -> if (frameData.size >= 14) {
                Log.d(TAG, "Unknown Packet Type Received: 0x${"%02X%02X".format(frameData[12], frameData[13])}")
            } else {
                Log.d(TAG, "Unknown Packet Received.  Packet Length: ${frameData.size}")
            }
        }
    }

    private fun handleArpFrame(networkId: Long, srcMac: Long, frameData: ByteArray) {
        Log.d(TAG, "Got ARP Packet")
        val arpTable = arpTableFor(networkId)
        val arpReply = arpTable.processARPPacket(frameData) ?: return
        if (arpReply.destMac == 0L || arpReply.destAddress == null) {
            return
        }
        val networkConfig = node?.networkConfig(networkId) ?: return
        val localV4Address = networkConfig.assignedAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address }
            ?: return
        val deadline = LongArray(1)
        val packetData = arpTable.getReplyPacket(
            networkConfig.mac,
            localV4Address,
            arpReply.destMac,
            arpReply.destAddress,
        )
        val result = sendFrame(networkId, networkConfig.mac, srcMac, ARP_PACKET, packetData, deadline)
        if (result != ResultCode.RESULT_OK) {
            Log.e(TAG, "Error sending ARP packet: $result")
            return
        }
        Log.d(TAG, "ARP Reply Sent!")
        ztService.setNextBackgroundTaskDeadline(deadline[0])
    }

    private fun handleIpv4Frame(networkId: Long, srcMac: Long, frameData: ByteArray) {
        DebugLog.d(TAG, "Got IPv4 packet. Length: ${frameData.size} Bytes")
        try {
            IPPacketUtils.getSourceIP(frameData)?.let { sourceIP ->
                if (isIPv4Multicast(sourceIP)) {
                    val result = node?.multicastSubscribe(networkId, multicastAddressToMAC(sourceIP))
                    if (result != ResultCode.RESULT_OK) {
                        Log.e(TAG, "Error when calling multicastSubscribe: $result")
                    }
                } else {
                    arpTableFor(networkId).setAddress(sourceIP, srcMac)
                }
            }
            output?.write(frameData)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing data to vpn socket: ${e.message}", e)
        }
    }

    private fun handleIpv6Frame(networkId: Long, srcMac: Long, frameData: ByteArray) {
        DebugLog.d(TAG, "Got IPv6 packet. Length: ${frameData.size} Bytes")
        try {
            IPPacketUtils.getSourceIP(frameData)?.let { sourceIP ->
                if (isIPv6Multicast(sourceIP)) {
                    val result = node?.multicastSubscribe(networkId, multicastAddressToMAC(sourceIP))
                    if (result != ResultCode.RESULT_OK) {
                        Log.e(TAG, "Error when calling multicastSubscribe: $result")
                    }
                } else {
                    ndpTableFor(networkId).setAddress(sourceIP, srcMac)
                }
            }
            output?.write(frameData)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing data to vpn socket: ${e.message}", e)
        }
    }

    internal fun outputStream(): FileOutputStream? = output

    internal fun nodeConfig(networkId: Long) = node?.networkConfig(networkId)

    companion object {
        const val TAG = "TunTapAdapter"

        internal const val ARP_PACKET = 2054
        internal const val IPV4_PACKET = 2048
        internal const val IPV6_PACKET = 34525

        @JvmStatic
        fun multicastAddressToMAC(address: InetAddress): Long = when {
            address is Inet4Address -> {
                val raw = address.address
                ByteBuffer.wrap(
                    byteArrayOf(0, 0, 1, 0, 94, (raw[1].toInt() and Byte.MAX_VALUE.toInt()).toByte(), raw[2], raw[3]),
                ).long
            }

            address is Inet6Address -> {
                val raw = address.address
                ByteBuffer.wrap(byteArrayOf(0, 0, 51, 51, raw[12], raw[13], raw[14], raw[15])).long
            }

            else -> 0L
        }
    }
}
