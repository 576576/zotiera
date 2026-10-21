package net.sumitm.zotiera.util

import com.zerotier.sdk.Peer
import com.zerotier.sdk.Version
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.util.Locale

/** 字符串处理工具类。 */
object StringUtils {
    private const val VERSION_FORMAT = "%d.%d.%d"

    /** 将版本号转为可读字符串。 */
    @JvmStatic
    fun toString(version: Version): String = String.format(
        Locale.ROOT,
        VERSION_FORMAT,
        version.major,
        version.minor,
        version.revision,
    )

    /** 获得结点版本的可读字符串。 */
    @JvmStatic
    fun peerVersionString(peer: Peer): String = String.format(
        Locale.ROOT,
        VERSION_FORMAT,
        peer.versionMajor,
        peer.versionMinor,
        peer.versionRev,
    )

    /** 将 16 进制字符串转换为字符数组。 */
    @JvmStatic
    fun hexStringToBytes(hex: String): ByteArray {
        val length = hex.length
        if (length % 2 != 0) throw RuntimeException("String length must be even")
        val result = ByteArray(length / 2)
        var i = 0
        while (i < length) {
            val high = Character.digit(hex[i], 16)
            val low = Character.digit(hex[i + 1], 16)
            result[i / 2] = ((high shl 4) + low).toByte()
            i += 2
        }
        return result
    }

    /** 将 InetSocketAddress 转为 IP:Port（IPv6 则是 [IP]:Port）格式的字符串。 */
    @JvmStatic
    fun toString(address: InetSocketAddress): String {
        val inetAddress = address.address
        val ip = if (inetAddress is Inet6Address) "[${inetAddress.hostAddress}]" else inetAddress.hostAddress
        return "$ip:${address.port}"
    }
}
