package net.sumitm.zotiera.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * 网络的本地配置。主键与网络 ID 相同，便于按网络直查。
 */
@Entity(tableName = "network_configs")
class NetworkConfig @JvmOverloads constructor(
    @PrimaryKey
    @ColumnInfo(name = "id")
    var id: Long = 0L,
    @ColumnInfo(name = "route_via_zerotier")
    var routeViaZeroTier: Boolean = false,
    @ColumnInfo(name = "dns_mode")
    var dnsMode: Int = 0,
) {
    /** 由 repository 装载，不参与持久化。 */
    @Ignore
    var dnsServers: List<DnsServer> = emptyList()

    /** 由 repository 装载，不参与持久化。 */
    @Ignore
    var assignedAddresses: List<AssignedAddress> = emptyList()
}
