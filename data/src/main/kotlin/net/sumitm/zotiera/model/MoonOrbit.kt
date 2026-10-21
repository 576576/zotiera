package net.sumitm.zotiera.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.io.File

/**
 * Moon 入轨信息。
 *
 * 上游 1.16 起把 moon 标为 deprecated，官方推荐改用 network-specific relays（仍为 preview）。
 * 这里保留完整读写能力，`source` 记录这条记录的来源，便于导出与后续迁移。
 */
@Entity(tableName = "moon_orbits")
class MoonOrbit @JvmOverloads constructor(
    @PrimaryKey
    @ColumnInfo(name = "moon_world_id")
    var moonWorldId: Long = 0L,
    @ColumnInfo(name = "moon_seed")
    var moonSeed: Long = 0L,
    @ColumnInfo(name = "from_file")
    var fromFile: Boolean = false,
    @ColumnInfo(name = "source")
    var source: String = SOURCE_MANUAL,
    @ColumnInfo(name = "added_at")
    var addedAt: Long = 0L,
) {
    /** moon 文件是否已缓存，运行期状态，不持久化。 */
    @Ignore
    var isCacheFile: Boolean = false

    /** moon 文件由引擎写在当前实例的数据目录里，路径由调用方给。 */
    fun checkCacheFile(dir: File) {
        isCacheFile = moonFile(dir).exists()
    }

    fun deleteCacheFile(dir: File) {
        moonFile(dir).delete()
        isCacheFile = false
    }

    private fun moonFile(dir: File): File = File(dir, String.format(MOON_FILE_PATH, moonWorldId))

    companion object {
        const val MOON_FILE_PATH = "moons.d/%016x.moon"

        const val SOURCE_MANUAL = "manual"
        const val SOURCE_FILE = "file"
        const val SOURCE_URL = "url"
    }
}
