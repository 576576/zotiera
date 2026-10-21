package net.sumitm.zotiera.util

import java.math.BigInteger

/** 网络号处理工具类。 */
object NetworkIdUtils {
    @JvmStatic
    fun hexStringToLong(str: String): Long = BigInteger(str, 16).toLong()
}
