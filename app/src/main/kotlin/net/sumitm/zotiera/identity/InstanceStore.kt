package net.sumitm.zotiera.identity

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import net.sumitm.zotiera.model.Planet
import java.io.File

/**
 * 实例（Planet × 身份）在磁盘上的布局。
 *
 * ```
 * files/
 * ├── planets/<worldId>.planet        自定义 planet 的字节
 * ├── identities/<uid>/identity.*     身份密钥的正本
 * └── instances/<planetId>-<uid>/     引擎的数据目录（identity、networks.d、peers.d…）
 * ```
 *
 * 引擎只认数据目录里的 `identity.*` 与 `networks.d/`，所以每个实例各一份；身份密钥在
 * [ensure] 时下发、在 [syncUp] 时回收，正本始终留在 `identities/`。
 */
class InstanceStore(private val context: Context) {

    private val root = File(context.filesDir, INSTANCES_DIR)
    private val identitiesRoot = File(context.filesDir, IDENTITIES_DIR)
    private val planetsRoot = File(context.filesDir, PLANETS_DIR)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    fun instanceDir(planetId: Long, uid: Long): File = File(root, "$planetId-$uid")

    fun identityDir(uid: Long): File = File(identitiesRoot, uid.toString())

    fun planetFile(worldId: Long): File = File(planetsRoot, "$worldId.planet")

    /** 自定义 planet 的字节来源；内置 planet 返回 null。 */
    fun planetOverride(worldId: Long): File? =
        planetFile(worldId).takeIf { worldId != Planet.OFFICIAL_WORLD_ID && it.exists() }

    /**
     * 建实例目录，并把身份密钥下发进去。
     *
     * 密钥还不存在时什么都不做——引擎第一次启动会自己生成，随后由 [syncUp] 收回正本。
     */
    fun ensure(planetId: Long, uid: Long) {
        val dir = instanceDir(planetId, uid)
        dir.mkdirs()
        val src = identityDir(uid)
        for (name in IDENTITY_FILES) {
            val target = File(dir, name)
            if (target.exists()) continue
            val from = File(src, name)
            if (from.exists()) from.copyTo(target, overwrite = true)
        }
    }

    /** 引擎生成密钥后，把实例目录里的密钥收回身份正本。 */
    fun syncUp(planetId: Long, uid: Long) {
        val dir = instanceDir(planetId, uid)
        val dst = identityDir(uid)
        for (name in IDENTITY_FILES) {
            val from = File(dir, name)
            if (!from.exists()) continue
            dst.mkdirs()
            val target = File(dst, name)
            if (target.exists() && target.length() == from.length()) continue
            from.copyTo(target, overwrite = true)
        }
    }

    // ---- 当前实例 ----

    var activePlanetId: Long
        get() = prefs.getLong(PREF_INSTANCE_PLANET, Planet.OFFICIAL_WORLD_ID)
        private set(value) = prefs.edit { putLong(PREF_INSTANCE_PLANET, value) }

    /** -1 表示还没定过；调用方应换成仓库里的默认身份。 */
    var activeIdentityUid: Long
        get() = prefs.getLong(PREF_INSTANCE_IDENTITY, -1L)
        private set(value) = prefs.edit { putLong(PREF_INSTANCE_IDENTITY, value) }

    fun setActive(planetId: Long, uid: Long) {
        activePlanetId = planetId
        activeIdentityUid = uid
    }

    // ---- 一次性清掉旧版本留下的数据 ----

    /**
     * 行为稳定前不做数据接续：第一次跑到这一版时，把旧布局（`files/` 根目录）与
     * 之前拆出来的身份/行星目录整个丢掉，让引擎重新生成身份。
     *
     * 只按 [PREF_DATA_EPOCH] 执行一次，之后正常启动不再碰用户数据。
     */
    fun resetLegacyLayout() {
        if (prefs.getInt(PREF_DATA_EPOCH, 0) >= DATA_EPOCH) return
        runCatching {
            root.deleteRecursively()
            identitiesRoot.deleteRecursively()
            planetsRoot.deleteRecursively()
            LEGACY_ROOT_ENTRIES.forEach { File(context.filesDir, it).deleteRecursively() }
        }
        prefs.edit(commit = true) { putInt(PREF_DATA_EPOCH, DATA_EPOCH) }
    }

    companion object {
        const val INSTANCES_DIR = "instances"
        const val IDENTITIES_DIR = "identities"
        const val PLANETS_DIR = "planets"

        private const val PREF_INSTANCE_PLANET = "instance_planet"
        private const val PREF_INSTANCE_IDENTITY = "instance_identity"

        private val IDENTITY_FILES = listOf("identity.secret", "identity.public")

        /** 旧布局里引擎直接写在 `files/` 根目录的那些条目。 */
        private val LEGACY_ROOT_ENTRIES = listOf(
            "identity.secret", "identity.public", "planet", "planet.custom",
            "networks.d", "peers.d", "moons.d", "profileInstalled",
        )

        /** 数据布局变了就 +1，旧数据在第一次启动时清掉。 */
        private const val DATA_EPOCH = 1
        private const val PREF_DATA_EPOCH = "data_epoch"
    }
}
