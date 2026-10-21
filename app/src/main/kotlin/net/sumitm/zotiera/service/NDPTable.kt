package net.sumitm.zotiera.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.sumitm.zotiera.util.IPPacketUtils
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.HashMap

class NDPTable {
    private val entriesMap = HashMap<Long, NDPEntry>()
    private val inetAddressToMacAddress = HashMap<InetAddress, Long>()
    private val ipEntriesMap = HashMap<InetAddress, NDPEntry>()
    private val macAddressToInetAddress = HashMap<Long, InetAddress>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            while (isActive) {
                sweep()
                delay(1000)
            }
        }
    }

    /** 清掉超时的表项。 */
    private fun sweep() {
        for (entry in HashMap(entriesMap).values) {
            if (entry.time + ENTRY_TIMEOUT < System.currentTimeMillis()) {
                synchronized(macAddressToInetAddress) { macAddressToInetAddress.remove(entry.mac) }
                synchronized(inetAddressToMacAddress) { inetAddressToMacAddress.remove(entry.address) }
                synchronized(entriesMap) { entriesMap.remove(entry.mac) }
                synchronized(ipEntriesMap) { ipEntriesMap.remove(entry.address) }
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    fun setAddress(inetAddress: InetAddress, mac: Long) {
        synchronized(inetAddressToMacAddress) { inetAddressToMacAddress[inetAddress] = mac }
        synchronized(macAddressToInetAddress) { macAddressToInetAddress[mac] = inetAddress }
        val entry = NDPEntry(mac, inetAddress)
        synchronized(entriesMap) { entriesMap[mac] = entry }
        synchronized(ipEntriesMap) { ipEntriesMap[inetAddress] = entry }
    }

    fun hasMacForAddress(inetAddress: InetAddress): Boolean =
        synchronized(inetAddressToMacAddress) { inetAddressToMacAddress.containsKey(inetAddress) }

    fun hasAddressForMac(mac: Long): Boolean =
        synchronized(macAddressToInetAddress) { macAddressToInetAddress.containsKey(mac) }

    fun getMacForAddress(inetAddress: InetAddress): Long {
        synchronized(inetAddressToMacAddress) {
            val mac = inetAddressToMacAddress[inetAddress] ?: return -1
            updateNDPEntryTime(mac)
            return mac
        }
    }

    fun getAddressForMac(mac: Long): InetAddress? {
        synchronized(macAddressToInetAddress) {
            val address = macAddressToInetAddress[mac] ?: return null
            updateNDPEntryTime(address)
            return address
        }
    }

    private fun updateNDPEntryTime(inetAddress: InetAddress?) {
        synchronized(ipEntriesMap) {
            inetAddress?.let { ipEntriesMap[it]?.updateTime() }
        }
    }

    private fun updateNDPEntryTime(mac: Long) {
        synchronized(entriesMap) {
            entriesMap[mac]?.updateTime()
        }
    }

    /**
     * 邻居请求报文。
     *
     * 先在 48–71 字节填好 ICMPv6 内容算校验和，再整体把 0–39 清掉重写成 IPv6 头。
     */
    fun getNeighborSolicitationPacket(
        address: InetAddress,
        targetAddress: InetAddress,
        mac: Long,
    ): ByteArray {
        val packet = ByteArray(72)
        System.arraycopy(address.address, 0, packet, 0, 16)
        System.arraycopy(targetAddress.address, 0, packet, 16, 16)
        System.arraycopy(ByteBuffer.allocate(4).putInt(32).array(), 0, packet, 32, 4)
        packet[39] = 58
        packet[40] = -121
        System.arraycopy(targetAddress.address, 0, packet, 48, 16)
        val macBytes = ByteBuffer.allocate(8).putLong(mac).array()
        packet[64] = 1
        packet[65] = 1
        System.arraycopy(macBytes, 2, packet, 66, 6)
        System.arraycopy(
            ByteBuffer.allocate(2).putShort(IPPacketUtils.calculateChecksum(packet, 0, 0, 72).toShort()).array(),
            0,
            packet,
            42,
            2,
        )
        for (i in 0 until 40) {
            packet[i] = 0
        }
        packet[0] = 96
        System.arraycopy(ByteBuffer.allocate(2).putShort(32).array(), 0, packet, 4, 2)
        packet[6] = 58
        packet[7] = -1
        System.arraycopy(address.address, 0, packet, 8, 16)
        System.arraycopy(targetAddress.address, 0, packet, 24, 16)
        return packet
    }

    companion object {
        const val TAG = "NDPTable"

        private const val ENTRY_TIMEOUT = 120000L
    }
}
