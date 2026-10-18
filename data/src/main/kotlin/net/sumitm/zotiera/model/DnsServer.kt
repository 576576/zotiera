package net.sumitm.zotiera.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 网络下发的或用户自定义的 DNS 服务器。
 */
@Entity(
    tableName = "dns_servers",
    foreignKeys = [
        ForeignKey(
            entity = NetworkConfig::class,
            parentColumns = ["id"],
            childColumns = ["network_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("network_id")],
)
class DnsServer @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long? = null,
    @ColumnInfo(name = "network_id")
    var networkId: Long = 0L,
    @ColumnInfo(name = "nameserver")
    var nameserver: String? = null,
)
