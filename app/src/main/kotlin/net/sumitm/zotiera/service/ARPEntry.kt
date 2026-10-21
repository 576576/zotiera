package net.sumitm.zotiera.service

import java.net.InetAddress

/** ARP 表项：MAC 与 IPv4 地址的对应关系及记录时间。 */
data class ARPEntry(val mac: Long, val address: InetAddress) {
    var time: Long = System.currentTimeMillis()
        private set

    fun updateTime() {
        time = System.currentTimeMillis()
    }
}
