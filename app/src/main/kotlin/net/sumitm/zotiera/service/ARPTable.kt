package net.sumitm.zotiera.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.HashMap

class ARPTable {
    private val entriesMap = HashMap<Long, ARPEntry>()
    private val inetAddressToMacAddress = HashMap<InetAddress, Long>()
    private val ipEntriesMap = HashMap<InetAddress, ARPEntry>()
    private val macAddressToInetAdddress = HashMap<Long, InetAddress>()

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
                Log.d(TAG, "Removing ${entry.address} from ARP cache")
                synchronized(macAddressToInetAdddress) { macAddressToInetAdddress.remove(entry.mac) }
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
        synchronized(macAddressToInetAdddress) { macAddressToInetAdddress[mac] = inetAddress }
        val entry = ARPEntry(mac, inetAddress)
        synchronized(entriesMap) { entriesMap[mac] = entry }
        synchronized(ipEntriesMap) { ipEntriesMap[inetAddress] = entry }
    }

    private fun updateArpEntryTime(mac: Long) {
        synchronized(entriesMap) {
            entriesMap[mac]?.updateTime()
        }
    }

    private fun updateArpEntryTime(inetAddress: InetAddress?) {
        synchronized(ipEntriesMap) {
            inetAddress?.let { ipEntriesMap[it]?.updateTime() }
        }
    }

    fun getMacForAddress(inetAddress: InetAddress): Long {
        synchronized(inetAddressToMacAddress) {
            val mac = inetAddressToMacAddress[inetAddress] ?: return -1
            Log.d(TAG, "Returning MAC for $inetAddress")
            updateArpEntryTime(mac)
            return mac
        }
    }

    fun getAddressForMac(mac: Long): InetAddress? {
        synchronized(macAddressToInetAdddress) {
            val address = macAddressToInetAdddress[mac] ?: return null
            updateArpEntryTime(address)
            return address
        }
    }

    fun hasMacForAddress(inetAddress: InetAddress): Boolean =
        synchronized(inetAddressToMacAddress) { inetAddressToMacAddress.containsKey(inetAddress) }

    fun hasAddressForMac(mac: Long): Boolean =
        synchronized(macAddressToInetAdddress) { macAddressToInetAdddress.containsKey(mac) }

    fun getRequestPacket(mac: Long, address: InetAddress, targetAddress: InetAddress): ByteArray =
        getARPPacket(REQUEST, mac, 0, address, targetAddress)

    fun getReplyPacket(mac: Long, address: InetAddress, destMac: Long, destAddress: InetAddress): ByteArray =
        getARPPacket(REPLY, mac, destMac, address, destAddress)

    fun getARPPacket(
        type: Int,
        mac: Long,
        destMac: Long,
        address: InetAddress,
        destAddress: InetAddress,
    ): ByteArray {
        val packet = ByteArray(28)
        packet[0] = 0
        packet[1] = 1
        packet[2] = 8
        packet[3] = 0
        packet[4] = 6
        packet[5] = 4
        packet[6] = 0
        packet[7] = type.toByte()
        System.arraycopy(longToBytes(mac), 2, packet, 8, 6)
        System.arraycopy(address.address, 0, packet, 14, 4)
        System.arraycopy(longToBytes(destMac), 2, packet, 18, 6)
        System.arraycopy(destAddress.address, 0, packet, 24, 4)
        return packet
    }

    fun processARPPacket(packetData: ByteArray): ARPReplyData? {
        Log.d(TAG, "Processing ARP packet")

        // 解析包内 IP、MAC 地址
        val rawSrcMac = ByteArray(8)
        System.arraycopy(packetData, 8, rawSrcMac, 2, 6)
        val rawSrcAddress = ByteArray(4)
        System.arraycopy(packetData, 14, rawSrcAddress, 0, 4)
        val rawDstMac = ByteArray(8)
        System.arraycopy(packetData, 18, rawDstMac, 2, 6)
        val rawDstAddress = ByteArray(4)
        System.arraycopy(packetData, 24, rawDstAddress, 0, 4)

        val srcAddress = runCatching { InetAddress.getByAddress(rawSrcAddress) }.getOrNull()
        val dstAddress = runCatching { InetAddress.getByAddress(rawDstAddress) }.getOrNull()
        val srcMac = ByteBuffer.wrap(rawSrcMac).long
        val dstMac = ByteBuffer.wrap(rawDstMac).long

        if (srcMac != 0L && srcAddress != null) {
            setAddress(srcAddress, srcMac)
        }
        if (dstMac != 0L && dstAddress != null) {
            setAddress(dstAddress, dstMac)
        }

        return if (packetData[7] == REQUEST.toByte()) {
            Log.d(TAG, "Reply needed")
            ARPReplyData(srcMac, srcAddress)
        } else {
            null
        }
    }

    companion object {
        const val TAG = "ARPTable"

        private const val ENTRY_TIMEOUT = 120000L
        private const val REPLY = 2
        private const val REQUEST = 1

        @JvmStatic
        fun longToBytes(value: Long): ByteArray = ByteBuffer.allocate(8).putLong(value).array()
    }
}
