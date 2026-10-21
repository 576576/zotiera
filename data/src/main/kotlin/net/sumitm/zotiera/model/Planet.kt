package net.sumitm.zotiera.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一个 planet（行星，根服务器集合）。
 *
 * 官方行星是内置行：[ordinal] 固定为 0，显示名由界面本地化，没有对应文件；
 * 自定义行星的字节存在 `files/planets/<worldId>.planet`。
 */
@Entity(tableName = "planets")
class Planet @JvmOverloads constructor(
    @PrimaryKey
    @ColumnInfo(name = "world_id")
    var worldId: Long = OFFICIAL_WORLD_ID,
    @ColumnInfo(name = "alias")
    var alias: String = "",
    @ColumnInfo(name = "built_in")
    var builtIn: Boolean = false,
    @ColumnInfo(name = "added_at")
    var addedAt: Long = 0L,
    /** 序号：官方固定 0，自定义从 1 起按加入顺序分配。 */
    @ColumnInfo(name = "ordinal")
    var ordinal: Int = 0,
    /** 默认行星：新建网络与简易模式都用它，全局只有一个。 */
    @ColumnInfo(name = "is_default")
    var isDefault: Boolean = false,
) {
    /** 16 位 hex 去掉前导 0，与卫星的显示方式一致。 */
    val worldIdHex: String
        get() = java.lang.Long.toHexString(worldId)

    companion object {
        /** `ZT_WORLD_ID_EARTH`，见 `externals/core/node/World.hpp`。 */
        const val OFFICIAL_WORLD_ID = 149604618L

        /** 官方行星的序号。 */
        const val OFFICIAL_ORDINAL = 0
    }
}
