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
    /** 序号，从 1 起按加入顺序分配。 */
    @ColumnInfo(name = "ordinal")
    var ordinal: Int = 0,
    /** 停用后从引擎摘掉并删掉缓存文件。 */
    @ColumnInfo(name = "enabled")
    var enabled: Boolean = true,
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

        /** 引擎 `World::Type`，序列化文件第一个 Byte 的值。 */
        const val TYPE_PLANET = 1     // World::TYPE_PLANET
        const val TYPE_MOON = 127     // World::TYPE_MOON

        /** 卫星文件（`World`）的序列化体里，根数量 Byte 之后第一个根身份的偏移。 */
        const val MOON_ROOT_ADDRESS_START = 114

        /** 引擎 `Address` 的 5 Byte/40 Bit 长度，与 `node/Address.hpp` 一致。 */
        const val ZT_ADDRESS_LEN = 5

        /**
         * 卫星文件（`World`）的序列化体里，第一个根身份（Root Identity）的 ZT 地址。
         *
         * 布局：类型(1) + world id(8) + 时间戳(8) + 更新签名公钥(32) + 签名(64) + 根数量(1) = 114 Byte，
         * 根身份的 `Identity` 序列化前 5 Byte 为大端 ZT 地址。
         *
         * 引擎用这个地址作为 `orbit` 的 seed：节点向它查询卫星，卫星回传的世界更新只有根地址
         * 与 seed 一致时 `Topology::addWorld` 才会接受。
         */
        @JvmStatic
        fun moonRootZtAddress(bytes: ByteArray): Long? =
            if (bytes.size < MOON_ROOT_ADDRESS_START + ZT_ADDRESS_LEN) {
                null
            } else {
                var address = 0L
                for (i in MOON_ROOT_ADDRESS_START until MOON_ROOT_ADDRESS_START + ZT_ADDRESS_LEN) {
                    address = (address shl 8) + (bytes[i].toLong() and 0xff)
                }
                address
            }
    }
}
