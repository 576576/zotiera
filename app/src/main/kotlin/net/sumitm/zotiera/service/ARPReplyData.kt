package net.sumitm.zotiera.service

import java.net.InetAddress

/** ARP 应答报文所需数据；报文内容总是本节点的 IP 与 MAC，所以只记应答目标。 */
data class ARPReplyData(val destMac: Long, val destAddress: InetAddress?)
