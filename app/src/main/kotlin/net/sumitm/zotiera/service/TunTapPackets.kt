package net.sumitm.zotiera.service

import android.util.Log
import com.zerotier.sdk.ResultCode
import net.sumitm.zotiera.util.DebugLog
import net.sumitm.zotiera.util.IPPacketUtils
import net.sumitm.zotiera.util.InetAddressUtils
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * 从 TUN 收到的报文：决定走哪个网络、目标 MAC 是谁，必要时先发 ARP / 邻居请求。
 *
 * 都是 [TunTapAdapter] 的扩展：表、路由与节点都由适配器持有，这里只做查表与组包。
 */
internal fun TunTapAdapter.handleIPv4Packet(packetData: ByteArray) {
    var destIP = IPPacketUtils.getDestIP(packetData)
    val sourceIP = IPPacketUtils.getSourceIP(packetData)
    if (destIP == null || sourceIP == null) {
        Log.e(TunTapAdapter.TAG, "destAddress or sourceAddress is null")
        return
    }
    val networkId = resolveNetworkIdOf(destIP, sourceIP)
    if (networkId == 0L) {
        Log.w(TunTapAdapter.TAG, "No joined network covers destination $destIP")
        return
    }
    val config = ztService.getVirtualNetworkConfig(networkId)
    if (config == null) {
        Log.e(TunTapAdapter.TAG, "TunTapAdapter has no network config yet")
        return
    }

    val arpTable = arpTableFor(networkId)
    val isMulticast = if (isIPv4Multicast(destIP)) {
        val result = node?.multicastSubscribe(networkId, TunTapAdapter.multicastAddressToMAC(destIP))
        if (result != ResultCode.RESULT_OK) {
            Log.e(TunTapAdapter.TAG, "Error when calling multicastSubscribe: $result")
        }
        true
    } else {
        false
    }
    val gateway = routeForDestination(destIP)?.gateway

    // 当前节点在该网络的 v4 地址
    var localV4Address: InetAddress? = null
    var cidr = 0
    for (assigned in config.assignedAddresses) {
        if (assigned.address is Inet4Address) {
            localV4Address = assigned.address
            cidr = assigned.port
            break
        }
    }

    val destRoute = InetAddressUtils.addressToRouteNo0Route(destIP, cidr)
    val sourceRoute = InetAddressUtils.addressToRouteNo0Route(sourceIP, cidr)
    if (gateway != null && destRoute != sourceRoute) {
        destIP = gateway
    }
    val localAddress = localV4Address
    if (localAddress == null) {
        Log.e(TunTapAdapter.TAG, "Couldn't determine local address")
        return
    }

    val localMac = config.mac
    val deadline = LongArray(1)
    if (isMulticast || arpTable.hasMacForAddress(destIP)) {
        // 目标 MAC 已知，直接发送
        val destMac = if (isIPv4Multicast(destIP)) {
            TunTapAdapter.multicastAddressToMAC(destIP)
        } else {
            arpTable.getMacForAddress(destIP)
        }
        val result = sendFrame(networkId, localMac, destMac, TunTapAdapter.IPV4_PACKET, packetData, deadline)
        if (result != ResultCode.RESULT_OK) {
            Log.e(TunTapAdapter.TAG, "Error calling processVirtualNetworkFrame: $result")
            return
        }
        Log.d(TunTapAdapter.TAG, "Packet sent to ZT")
        ztService.setNextBackgroundTaskDeadline(deadline[0])
    } else {
        // 目标 MAC 未知，先做 ARP 查询
        Log.d(TunTapAdapter.TAG, "Unknown dest MAC address.  Need to look it up. $destIP")
        val arpRequest = arpTable.getRequestPacket(localMac, localAddress, destIP)
        val result = sendFrame(
            networkId,
            localMac,
            InetAddressUtils.BROADCAST_MAC_ADDRESS,
            TunTapAdapter.ARP_PACKET,
            arpRequest,
            deadline,
        )
        if (result != ResultCode.RESULT_OK) {
            Log.e(TunTapAdapter.TAG, "Error sending ARP packet: $result")
            return
        }
        Log.d(TunTapAdapter.TAG, "ARP Request Sent!")
        ztService.setNextBackgroundTaskDeadline(deadline[0])
    }
}

internal fun TunTapAdapter.handleIPv6Packet(packetDataIn: ByteArray) {
    var packetData = packetDataIn
    var destIP = IPPacketUtils.getDestIP(packetData)
    val sourceIP = IPPacketUtils.getSourceIP(packetData)
    if (destIP == null || sourceIP == null) {
        Log.e(TunTapAdapter.TAG, "destAddress or sourceAddress is null")
        return
    }
    val networkId = resolveNetworkIdOf(destIP, sourceIP)
    if (networkId == 0L) {
        Log.w(TunTapAdapter.TAG, "No joined network covers destination $destIP")
        return
    }
    val config = ztService.getVirtualNetworkConfig(networkId)
    if (config == null) {
        Log.e(TunTapAdapter.TAG, "TunTapAdapter has no network config yet")
        return
    }

    val ndpTable = ndpTableFor(networkId)
    if (isIPv6Multicast(destIP)) {
        val result = node?.multicastSubscribe(networkId, TunTapAdapter.multicastAddressToMAC(destIP))
        if (result != ResultCode.RESULT_OK) {
            Log.e(TunTapAdapter.TAG, "Error when calling multicastSubscribe: $result")
        }
    }
    val gateway = routeForDestination(destIP)?.gateway

    // 当前节点在该网络的 v6 地址
    var localAddress: InetAddress? = null
    var cidr = 0
    for (assigned in config.assignedAddresses) {
        if (assigned.address is Inet6Address) {
            localAddress = assigned.address
            cidr = assigned.port
            break
        }
    }

    val destRoute = InetAddressUtils.addressToRouteNo0Route(destIP, cidr)
    val sourceRoute = InetAddressUtils.addressToRouteNo0Route(sourceIP, cidr)
    if (gateway != null && destRoute != sourceRoute) {
        destIP = gateway
    }
    val local = localAddress
    if (local == null) {
        Log.e(TunTapAdapter.TAG, "Couldn't determine local address")
        return
    }

    val localMac = config.mac
    val deadline = LongArray(1)

    var destMac: Long
    var sendNeighborSolicitation = false
    when {
        isNeighborSolicitation(packetData) ->
            // 本地 NS 报文：表里有就单播，没有就发到 solicited-node 组播
            destMac = if (ndpTable.hasMacForAddress(destIP)) {
                ndpTable.getMacForAddress(destIP)
            } else {
                InetAddressUtils.ipv6ToMulticastAddress(destIP)
            }

        isIPv6Multicast(destIP) -> destMac = TunTapAdapter.multicastAddressToMAC(destIP)

        isNeighborAdvertisement(packetData) -> {
            // 本地 NA 报文：表里没有就不发数据包，只补一次 NS
            destMac = if (ndpTable.hasMacForAddress(destIP)) {
                ndpTable.getMacForAddress(destIP)
            } else {
                0L
            }
            sendNeighborSolicitation = true
        }

        else -> {
            // 普通数据包：MAC 未知就先查
            if (ndpTable.hasMacForAddress(destIP)) {
                destMac = ndpTable.getMacForAddress(destIP)
            } else {
                destMac = 0L
                sendNeighborSolicitation = true
            }
        }
    }

    if (destMac != 0L) {
        val result = sendFrame(networkId, localMac, destMac, TunTapAdapter.IPV6_PACKET, packetData, deadline)
        if (result != ResultCode.RESULT_OK) {
            Log.e(TunTapAdapter.TAG, "Error calling processVirtualNetworkFrame: $result")
        } else {
            DebugLog.d(TunTapAdapter.TAG, "Packet sent to ZT")
            ztService.setNextBackgroundTaskDeadline(deadline[0])
        }
    }
    if (sendNeighborSolicitation) {
        if (destMac == 0L) {
            destMac = InetAddressUtils.ipv6ToMulticastAddress(destIP)
        }
        Log.d(TunTapAdapter.TAG, "Sending Neighbor Solicitation")
        packetData = ndpTable.getNeighborSolicitationPacket(sourceIP, destIP, localMac)
        val result = sendFrame(networkId, localMac, destMac, TunTapAdapter.IPV6_PACKET, packetData, deadline)
        if (result != ResultCode.RESULT_OK) {
            Log.e(TunTapAdapter.TAG, "Error calling processVirtualNetworkFrame: $result")
        } else {
            Log.d(TunTapAdapter.TAG, "Neighbor Solicitation sent to ZT")
            ztService.setNextBackgroundTaskDeadline(deadline[0])
        }
    }
}

private fun isNeighborSolicitation(packetData: ByteArray): Boolean =
    packetData[6] == 58.toByte() && packetData[40] == (-121).toByte()

private fun isNeighborAdvertisement(packetData: ByteArray): Boolean =
    packetData[6] == 58.toByte() && packetData[40] == (-120).toByte()
