package net.sumitm.zotiera.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * 已加入的 ZeroTier 网络。
 */
@Entity(tableName = "networks")
class Network @JvmOverloads constructor(
    @PrimaryKey
    @ColumnInfo(name = "network_id")
    var networkId: Long = 0L,
    @ColumnInfo(name = "network_id_str")
    var networkIdStr: String? = null,
    @ColumnInfo(name = "network_name")
    var networkName: String? = null,
    @ColumnInfo(name = "last_activated")
    var lastActivated: Boolean = false,
    @ColumnInfo(name = "network_config_id")
    var networkConfigId: Long = 0L,
    /** 所属实例：planet 与身份的组合决定用哪份数据目录。 */
    @ColumnInfo(name = "planet_id", defaultValue = "149604618")
    var planetId: Long = Planet.OFFICIAL_WORLD_ID,
    @ColumnInfo(name = "identity_uid", defaultValue = "1")
    var identityUid: Long = Identity.DEFAULT_UID,
) {
    /** 由 repository 装载，不参与持久化。 */
    @Ignore
    var networkConfig: NetworkConfig? = null
}
