package net.sumitm.zotiera.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 网络分配的地址。
 */
@Entity(
    tableName = "assigned_addresses",
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
class AssignedAddress @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long? = null,
    @ColumnInfo(name = "network_id")
    var networkId: Long = 0L,
    @ColumnInfo(name = "type")
    var type: AddressType? = null,
    @ColumnInfo(name = "address_bytes")
    var addressBytes: ByteArray? = null,
    @ColumnInfo(name = "address_string")
    var addressString: String? = null,
    @ColumnInfo(name = "prefix")
    var prefix: Short = 0,
) {
    enum class AddressType(val id: Int) {
        UNKNOWN(0),
        IPV4(1),
        IPV6(2);

        override fun toString(): String = when (this) {
            IPV4 -> "IPv4"
            IPV6 -> "IPv6"
            UNKNOWN -> "Unknown"
        }

        companion object {
            @JvmStatic
            fun fromInt(value: Int): AddressType =
                entries.firstOrNull { it.id == value } ?: UNKNOWN
        }
    }
}
