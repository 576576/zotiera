package net.sumitm.zotiera.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一个 planet（根服务器集合）。
 *
 * 官方 planet 是内置行：别名固定 [DEFAULT_ALIAS]，没有对应文件；自定义 planet 的字节存在
 * `files/planets/<worldId>.planet`。
 */
@Entity(tableName = "planets")
class Planet @JvmOverloads constructor(
    @PrimaryKey
    @ColumnInfo(name = "world_id")
    var worldId: Long = OFFICIAL_WORLD_ID,
    @ColumnInfo(name = "alias")
    var alias: String = DEFAULT_ALIAS,
    @ColumnInfo(name = "built_in")
    var builtIn: Boolean = false,
    @ColumnInfo(name = "added_at")
    var addedAt: Long = 0L,
) {
    companion object {
        /** `ZT_WORLD_ID_EARTH`，见 `externals/core/node/World.hpp`。 */
        const val OFFICIAL_WORLD_ID = 149604618L

        const val DEFAULT_ALIAS = "zerotier.com"
    }
}
