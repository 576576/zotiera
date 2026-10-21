package net.sumitm.zotiera.identity

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import net.sumitm.zotiera.data.ZotieraRepository
import net.sumitm.zotiera.model.Identity
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

    var activeIdentityUid: Long
        get() = prefs.getLong(PREF_INSTANCE_IDENTITY, Identity.DEFAULT_UID)
        private set(value) = prefs.edit { putLong(PREF_INSTANCE_IDENTITY, value) }

    fun setActive(planetId: Long, uid: Long) {
        activePlanetId = planetId
        activeIdentityUid = uid
    }

    // ---- 首次启动：把老布局搬进默认实例 ----

    /**
     * 1.5 之前所有东西都写在 `files/` 根目录。这里把身份密钥与已加入的网络搬进默认实例目录，
     * 保证升级后连的网络还在、节点 ID 不变。
     *
     * 只在默认实例目录还没有 identity.secret 时执行一次。
     */
    fun bootstrapLegacy(repository: ZotieraRepository) {
        root.mkdirs()
        val planetId = legacyPlanetId(repository)
        val uid = Identity.DEFAULT_UID
        val dir = instanceDir(planetId, uid)
        dir.mkdirs()

        if (!File(dir, "identity.secret").exists()) {
            for (name in IDENTITY_FILES) {
                val from = File(context.filesDir, name)
                if (from.exists()) from.copyTo(File(dir, name), overwrite = true)
            }
            for (sub in RUNTIME_DIRS) {
                val from = File(context.filesDir, sub)
                if (from.isDirectory) from.copyRecursively(File(dir, sub), overwrite = false)
            }
        }
        syncUp(planetId, uid)
        ensure(planetId, uid)
        setActive(planetId, uid)
    }

    /**
     * 老布局里如果开了自定义 planet，默认实例就应该落在这个 planet 上，行为才和升级前一致。
     * 顺带把它写成 planets 表里的一条，别名 `custom`。
     */
    private fun legacyPlanetId(repository: ZotieraRepository): Long {
        val custom = File(context.filesDir, LEGACY_CUSTOM_PLANET)
        val useCustom = prefs.getBoolean(PREF_LEGACY_USE_CUSTOM_PLANET, false)
        if (!useCustom || !custom.exists()) return Planet.OFFICIAL_WORLD_ID

        val bytes = runCatching { custom.readBytes() }.getOrNull() ?: return Planet.OFFICIAL_WORLD_ID
        if (bytes.size < 9 || bytes[0] != 0x01.toByte()) return Planet.OFFICIAL_WORLD_ID
        var worldId = 0L
        for (i in 1..8) worldId = (worldId shl 8) + (bytes[i].toLong() and 0xff)
        if (worldId == Planet.OFFICIAL_WORLD_ID) return Planet.OFFICIAL_WORLD_ID

        planetFile(worldId).parentFile?.mkdirs()
        if (!planetFile(worldId).exists()) planetFile(worldId).writeBytes(bytes)
        if (repository.planet(worldId) == null) {
            repository.insertPlanet(
                Planet(worldId = worldId, alias = "custom", builtIn = false, addedAt = System.currentTimeMillis())
            )
        }
        return worldId
    }

    companion object {
        const val INSTANCES_DIR = "instances"
        const val IDENTITIES_DIR = "identities"
        const val PLANETS_DIR = "planets"

        private const val PREF_INSTANCE_PLANET = "instance_planet"
        private const val PREF_INSTANCE_IDENTITY = "instance_identity"

        /** 老布局的两个 key，只在迁移时读。 */
        private const val LEGACY_CUSTOM_PLANET = "planet.custom"
        private const val PREF_LEGACY_USE_CUSTOM_PLANET = "planet_use_custom"

        private val IDENTITY_FILES = listOf("identity.secret", "identity.public")
        private val RUNTIME_DIRS = listOf("networks.d", "peers.d")
    }
}
