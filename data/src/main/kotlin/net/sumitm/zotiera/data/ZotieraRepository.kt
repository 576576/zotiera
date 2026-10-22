package net.sumitm.zotiera.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.sumitm.zotiera.model.AssignedAddress
import net.sumitm.zotiera.model.DnsServer
import net.sumitm.zotiera.model.Identity
import net.sumitm.zotiera.model.MoonOrbit
import net.sumitm.zotiera.model.Network
import net.sumitm.zotiera.model.NetworkConfig
import net.sumitm.zotiera.model.NetworkIdentity
import net.sumitm.zotiera.model.Planet

/**
 * 唯一的持久化入口。对外只暴露这个类，Room 的 DAO 不出 :data 模块。
 *
 * 公开方法都是 `suspend`：整段切到 IO 一次，不要求调用方自己挑线程。
 * 内部互相调用的版本是私有的非挂起函数，避免嵌套 `withContext`。
 */
class ZotieraRepository(private val db: ZotieraDatabase) {

    private val seedMutex = Mutex()

    @Volatile
    private var seeded = false

    /**
     * 补齐官方行星与默认身份。
     *
     * 每个进程只做一次：界面第一次读库前等着它，服务启动时也会调，两处谁先到都不吃亏。
     */
    suspend fun ensureSeed() {
        if (seeded) return
        seedMutex.withLock {
            if (seeded) return
            ensureOfficialPlanet()
            ensureDefaultIdentity()
            seeded = true
        }
    }

    // ---- networks ----

    suspend fun networks(): List<Network> = io { db.networkDao().all().map { attach(it) } }

    suspend fun lastActivatedNetworks(): List<Network> =
        io { db.networkDao().lastActivated().map { attach(it) } }

    suspend fun network(networkId: Long): Network? =
        io { db.networkDao().byId(networkId)?.let { attach(it) } }

    suspend fun hasNetwork(networkId: Long): Boolean = io { db.networkDao().count(networkId) > 0 }

    suspend fun insertNetwork(network: Network) = io { db.networkDao().insert(network) }

    suspend fun updateNetwork(network: Network) = io { db.networkDao().update(network) }

    /** 这条网络用过的身份。 */
    suspend fun usedIdentityUids(networkId: Long): Set<Long> =
        io { db.networkIdentityDao().uidsFor(networkId).toSet() }

    /** 记下「这条网络用过这个身份」，换回去时不再二次确认。 */
    suspend fun markIdentityUsed(networkId: Long, uid: Long) = io {
        db.networkIdentityDao().insert(
            NetworkIdentity(networkId = networkId, identityUid = uid, usedAt = System.currentTimeMillis())
        )
    }

    /** 删除网络及其配置；DNS 与地址由外键级联清理。 */
    suspend fun deleteNetwork(network: Network) = io {
        db.runInTransaction {
            db.networkIdentityDao().deleteFor(network.networkId)
            db.networkDao().delete(network)
            db.networkConfigDao().deleteById(network.networkConfigId)
        }
    }

    // ---- network config ----

    suspend fun networkConfig(id: Long): NetworkConfig? =
        io { db.networkConfigDao().byId(id)?.let { attach(it) } }

    suspend fun insertNetworkConfig(config: NetworkConfig) = io { db.networkConfigDao().insert(config) }

    suspend fun deleteNetworkConfig(config: NetworkConfig) = io { db.networkConfigDao().delete(config) }

    // ---- dns ----

    suspend fun dnsServers(networkId: Long): List<DnsServer> = io { db.dnsServerDao().forNetwork(networkId) }

    suspend fun insertDnsServer(server: DnsServer) = io { db.dnsServerDao().insert(server) }

    /** 用给定地址整体替换某网络的 DNS 列表。 */
    suspend fun replaceDnsServers(networkId: Long, nameservers: List<String>) = io {
        db.runInTransaction {
            db.dnsServerDao().deleteForNetwork(networkId)
            nameservers.forEach { db.dnsServerDao().insert(DnsServer(networkId = networkId, nameserver = it)) }
        }
    }

    suspend fun deleteDnsServers(networkId: Long) = io { db.dnsServerDao().deleteForNetwork(networkId) }

    // ---- assigned addresses ----

    suspend fun assignedAddresses(networkId: Long): List<AssignedAddress> =
        io { db.assignedAddressDao().forNetwork(networkId) }

    suspend fun insertAssignedAddress(address: AssignedAddress) = io { db.assignedAddressDao().insert(address) }

    suspend fun deleteAssignedAddress(address: AssignedAddress) = io { db.assignedAddressDao().delete(address) }

    // ---- planets / identities ----

    suspend fun planets(): List<Planet> = io { db.planetDao().all() }

    suspend fun planet(worldId: Long): Planet? = io { planetRow(worldId) }

    /** 官方行星是内置行，没有就补一条（序号固定 0，默认行星也归它）。 */
    suspend fun ensureOfficialPlanet() = io {
        if (planetRow(Planet.OFFICIAL_WORLD_ID) == null) {
            insertPlanetRow(
                Planet(
                    worldId = Planet.OFFICIAL_WORLD_ID,
                    alias = "",
                    builtIn = true,
                    ordinal = Planet.OFFICIAL_ORDINAL,
                    isDefault = true,
                )
            )
        }
        if (defaultPlanetRow() == null) {
            planetRows().firstOrNull()?.let { setDefaultPlanetTx(it.worldId) }
        }
    }

    suspend fun defaultPlanet(): Planet? = io { defaultPlanetRow() }

    /** 没有默认行星时把序号最小的那颗扶正。 */
    suspend fun ensureDefaultPlanet() = io {
        if (defaultPlanetRow() != null) return@io
        planetRows().firstOrNull()?.let { setDefaultPlanetTx(it.worldId) }
    }

    /** 设为默认行星：先清掉旧的，再标记这一条。 */
    suspend fun setDefaultPlanet(worldId: Long) = io { setDefaultPlanetTx(worldId) }

    suspend fun insertPlanet(planet: Planet) = io { insertPlanetRow(planet) }

    suspend fun updatePlanet(planet: Planet) = io { db.planetDao().update(planet) }

    suspend fun deletePlanet(planet: Planet) = io { db.planetDao().delete(planet) }

    suspend fun identities(): List<Identity> = io { db.identityDao().all() }

    suspend fun identity(uid: Long): Identity? = io { db.identityDao().byUid(uid) }

    /** 插入身份并返回自增 uid；序号按加入顺序分配。 */
    suspend fun insertIdentity(identity: Identity): Long = io { insertIdentityRow(identity) }

    suspend fun defaultIdentity(): Identity? = io { defaultIdentityRow() }

    /** 设为默认身份：先清掉旧的，再标记这一条。 */
    suspend fun setDefaultIdentity(uid: Long) = io { setDefaultIdentityTx(uid) }

    /** 没有任何身份时补一条并设为默认。返回当前默认身份的 uid（可能为 null 只在插入失败时）。 */
    suspend fun ensureDefaultIdentity(): Long? = io {
        defaultIdentityRow()?.let { return@io it.uid }
        val first = db.identityDao().all().firstOrNull()
        if (first != null) {
            setDefaultIdentityTx(first.uid)
            return@io first.uid
        }
        insertIdentityRow(Identity(alias = "", createdAt = System.currentTimeMillis(), isDefault = true))
    }

    suspend fun updateIdentity(identity: Identity) = io { db.identityDao().update(identity) }

    suspend fun deleteIdentity(identity: Identity) = io { db.identityDao().delete(identity) }

    /** 引擎生成密钥后回写节点 ID。 */
    suspend fun setNodeId(uid: Long, nodeId: Long) = io {
        val identity = db.identityDao().byUid(uid) ?: return@io
        if (identity.nodeId == nodeId) return@io
        identity.nodeId = nodeId
        db.identityDao().update(identity)
    }

    // ---- moons ----

    suspend fun moonOrbits(): List<MoonOrbit> = io { db.moonOrbitDao().all() }

    suspend fun moonOrbit(worldId: Long): MoonOrbit? = io { db.moonOrbitDao().byWorldId(worldId) }

    suspend fun moonOrbit(worldId: Long, seed: Long): MoonOrbit? =
        io { db.moonOrbitDao().byWorldIdAndSeed(worldId, seed) }

    suspend fun hasMoonOrbit(worldId: Long): Boolean = io { db.moonOrbitDao().count(worldId) > 0 }

    suspend fun updateMoonOrbit(moon: MoonOrbit) = io { db.moonOrbitDao().update(moon) }

    suspend fun insertMoonOrbit(moon: MoonOrbit) = io {
        moon.ordinal = db.moonOrbitDao().nextOrdinal()
        db.moonOrbitDao().insert(moon)
    }

    suspend fun deleteMoonOrbit(moon: MoonOrbit) = io { db.moonOrbitDao().delete(moon) }

    // ---- 内部：不加协程切换，由公开方法统一包 ----

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun attach(network: Network): Network {
        val config = db.networkConfigDao().byId(network.networkConfigId)
        if (config != null) {
            config.dnsServers = db.dnsServerDao().forNetwork(config.id)
            config.assignedAddresses = db.assignedAddressDao().forNetwork(config.id)
        }
        network.networkConfig = config
        return network
    }

    private fun attach(config: NetworkConfig): NetworkConfig {
        config.dnsServers = db.dnsServerDao().forNetwork(config.id)
        config.assignedAddresses = db.assignedAddressDao().forNetwork(config.id)
        return config
    }

    private fun planetRow(worldId: Long): Planet? = db.planetDao().byId(worldId)

    private fun planetRows(): List<Planet> = db.planetDao().all()

    private fun defaultPlanetRow(): Planet? = db.planetDao().defaultRow()

    private fun setDefaultPlanetTx(worldId: Long) {
        db.runInTransaction {
            db.planetDao().clearDefault()
            planetRow(worldId)?.let {
                it.isDefault = true
                db.planetDao().update(it)
            }
        }
    }

    private fun insertPlanetRow(planet: Planet) {
        if (!planet.builtIn) planet.ordinal = db.planetDao().nextOrdinal()
        db.planetDao().insert(planet)
    }

    private fun defaultIdentityRow(): Identity? = db.identityDao().defaultRow()

    private fun setDefaultIdentityTx(uid: Long) {
        db.runInTransaction {
            db.identityDao().clearDefault()
            db.identityDao().byUid(uid)?.let {
                it.isDefault = true
                db.identityDao().update(it)
            }
        }
    }

    private fun insertIdentityRow(identity: Identity): Long {
        identity.ordinal = db.identityDao().nextOrdinal()
        return db.identityDao().insert(identity)
    }
}
