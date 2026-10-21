package net.sumitm.zotiera.util

import android.util.Log
import java.net.InetAddress
import java.net.UnknownHostException

object IPPacketUtils {
    private const val TAG = "IPPacketUtils"

    @JvmStatic
    fun getSourceIP(packet: ByteArray): InetAddress? = when (getIPVersion(packet).toInt()) {
        4 -> addressAt(packet, 12, 4)
        6 -> addressAt(packet, 8, 16)
        else -> {
            Log.e(TAG, "Unknown IP version")
            null
        }
    }

    @JvmStatic
    fun getDestIP(packet: ByteArray): InetAddress? = when (getIPVersion(packet).toInt()) {
        4 -> addressAt(packet, 16, 4)
        6 -> addressAt(packet, 24, 16)
        else -> {
            Log.e(TAG, "Unknown IP version")
            null
        }
    }

    private fun addressAt(packet: ByteArray, offset: Int, length: Int): InetAddress? {
        val raw = ByteArray(length)
        System.arraycopy(packet, offset, raw, 0, length)
        return try {
            InetAddress.getByAddress(raw)
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Error creating InetAddress", e)
            null
        }
    }

    @JvmStatic
    fun getIPVersion(packet: ByteArray): Byte = (packet[0].toInt() shr 4).toByte()

    /**
     * 计算校验和。
     *
     * 从 [initial] 的累加值接着算 [start] 到 [end] 之间的字节，返回值已取反。
     */
    @JvmStatic
    fun calculateChecksum(data: ByteArray, initial: Long, start: Int, end: Int): Long {
        var sum = initial
        var index = start
        var remaining = end - start
        while (remaining > 1) {
            sum += (((data[index].toInt() and 0xFF) shl 8) or (data[index + 1].toInt() and 0xFF)).toLong()
            if ((sum and -0x10000L) > 0) {
                sum = (sum and 0xFFFFL) + 1
            }
            index += 2
            remaining -= 2
        }
        if (remaining > 0) {
            sum += (((data[index].toInt() and 0xFF) shl 8) and 0xFF00).toLong()
            if ((sum and -0x10000L) > 0) {
                sum = (sum and 0xFFFFL) + 1
            }
        }
        return sum.inv() and 0xFFFFL
    }
}
