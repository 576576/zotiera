package net.sumitm.zotiera.util

import android.util.Log
import java.math.BigInteger
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer

object InetAddressUtils {
    const val TAG = "InetAddressUtils"

    const val BROADCAST_MAC_ADDRESS = 0xffffffffffffL

    /** 获得地址指定 CIDR 的子网掩码。 */
    @JvmStatic
    fun addressToNetmask(address: InetAddress, cidr: Int): ByteArray {
        val length = address.address.size
        val subnetLength = length * 8 - cidr
        val fullMasked = ByteArray(length) { -1 }
        if (length == 4) {
            val mask = ByteBuffer.wrap(fullMasked).int
            return ByteBuffer.allocate(4).putInt((mask shr subnetLength) shl subnetLength).array()
        }
        // CIDR 为 0 时没有掩码位
        if (cidr == 0) {
            return ByteArray(length)
        }
        val shifted = BigInteger(fullMasked).shiftRight(subnetLength).shiftLeft(subnetLength).toByteArray()
        if (shifted.size == length) {
            return shifted
        }
        // 高位为 0 时 BigInteger 会缩掉前导字节，需要补回
        val netmask = ByteArray(length)
        val offset = length - shifted.size
        for (i in 0 until offset) {
            netmask[i] = shifted[0]
        }
        System.arraycopy(shifted, 0, netmask, offset, shifted.size)
        return netmask
    }

    @JvmStatic
    fun addressToRoute(address: InetAddress, cidr: Int): InetAddress? {
        if (cidr == 0) {
            if (address is Inet4Address) {
                return try {
                    Inet4Address.getByAddress(byteArrayOf(0, 0, 0, 0))
                } catch (e: UnknownHostException) {
                    null
                }
            }
            if (address is Inet6Address) {
                return try {
                    Inet6Address.getByAddress(ByteArray(16))
                } catch (e: UnknownHostException) {
                    null
                }
            }
        }
        return addressToRouteNo0Route(address, cidr)
    }

    /** 获得地址对应的网络前缀。 */
    @JvmStatic
    fun addressToRouteNo0Route(address: InetAddress, cidr: Int): InetAddress? {
        val netmask = addressToNetmask(address, cidr)
        val raw = address.address
        val masked = ByteArray(netmask.size) { i -> (raw[i].toInt() and netmask[i].toInt()).toByte() }
        return try {
            InetAddress.getByAddress(masked)
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unknown Host Exception calculating route")
            null
        }
    }

    @JvmStatic
    fun ipv6ToMulticastAddress(address: InetAddress): Long {
        val raw = address.address
        if (raw.size != 16) {
            return 0
        }
        return ByteBuffer.wrap(byteArrayOf(0, 0, 51, 51, -1, raw[13], raw[14], raw[15])).long
    }
}
