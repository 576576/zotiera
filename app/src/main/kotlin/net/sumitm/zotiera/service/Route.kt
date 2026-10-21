package net.sumitm.zotiera.service

import net.sumitm.zotiera.util.InetAddressUtils
import java.net.InetAddress

/** 路由记录。 */
data class Route @JvmOverloads constructor(
    val address: InetAddress,
    val prefix: Int,
    var gateway: InetAddress? = null,
) {
    fun belongsToRoute(inetAddress: InetAddress): Boolean =
        address == InetAddressUtils.addressToRoute(inetAddress, prefix)
}
