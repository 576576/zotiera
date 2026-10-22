package net.sumitm.zotiera.data

import androidx.room.withTransaction
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
 * 方法都是同步的；DAO 只在 :data 内部可见。
 */
class ZotieraRepository(private val db: ZotieraDatabase) {

    // ---- networks ----

    fun networks(): List<Network> = db.networkDao().all().map { attach(it) }

    fun lastActivatedNetworks(): List<Network> =
        db.networkDao().lastActivated().map { attach(it) }

    fun network(networkId: Long): Network? = db.networkDao().byId(networkId)?.let { attach(it) }

    fun hasNetwork(networkId: Long): Boolean = db.networkDao().count(networkId) > 0

    fun insertNetwork(network: Network) = db.networkDao().insert(network)

    fun updateNetwork(network: Network) = db.networkDao().update(network)

    /** 这条网络用过的身份。 */
    fun usedIdentityUids(networkId: Long): Set<Long> =
        db.networkIdentityDao().uidsFor(networkId).toSet()

    /** 记下「这条网络用过这个身份」，换回去时不再二次确认。 */
    fun markIdentityUsed(networkId: Long, uid: Long) = db.networkIdentityDao().insert(
        NetworkIdentity(networkId = networkId, identityUid = uid, usedAt = System.currentTimeMillis())
    )

    /** 删除网络及其配置；DNS 与地址由外键级联清理。 */
    fun deleteNetwork(network: Network) {
        db.runInTransaction {
            db.networkIdentityDao().deleteFor(network.networkId)
            db.networkDao().delete(network)
            db.networkConfigDao().deleteById(network.networkConfigId)
        }
    }

    // ---- network config ----

    fun networkConfig(id: Long): NetworkConfig? = db.networkConfigDao().byId(id)?.let { attach(it) }

    fun insertNetworkConfig(config: NetworkConfig) = db.networkConfigDao().insert(config)

    fun deleteNetworkConfig(config: NetworkConfig) = db.networkConfigDao().delete(config)

    // ---- dns ----

    fun dnsServers(networkId: Long): List<DnsServer> = db.dnsServerDao().forNetwork(networkId)

    fun insertDnsServer(server: DnsServer) = db.dnsServerDao().insert(server)

    /** 用给定地址整体替换某网络的 DNS 列表。 */
    fun replaceDnsServers(networkId: Long, nameservers: List<String>) {
        db.runInTransaction {
            db.dnsServerDao().deleteForNetwork(networkId)
            nameservers.forEach { db.dnsServerDao().insert(DnsServer(networkId = networkId, nameserver = it)) }
        }
    }

    fun deleteDnsServers(networkId: Long) = db.dnsServerDao().deleteForNetwork(networkId)

    // ---- assigned addresses ----

    fun assignedAddresses(networkId: Long): List<AssignedAddress> =
        db.assignedAddressDao().forNetwork(networkId)

    fun insertAssignedAddress(address: AssignedAddress) = db.assignedAddressDao().insert(address)

    fun deleteAssignedAddress(address: AssignedAddress) = db.assignedAddressDao().delete(address)

    // ---- planets / identities ----

    fun planets(): List<Planet> = db.planetDao().all()

    fun planet(worldId: Long): Planet? = db.planetDao().byId(worldId)

    /** 官方行星是内置行，没有就补一条（序号固定 0，默认行星也归它）。 */
    fun ensureOfficialPlanet() {
        if (planet(Planet.OFFICIAL_WORLD_ID) == null) {
            insertPlanet(
                Planet(
                    worldId = Planet.OFFICIAL_WORLD_ID,
                    alias = "",
                    builtIn = true,
                    ordinal = Planet.OFFICIAL_ORDINAL,
                    isDefault = true,
                )
            )
        }
        ensureDefaultPlanet()
    }

    fun defaultPlanet(): Planet? = db.planetDao().defaultRow()

    /** 设为默认行星：先清掉旧的，再标记这一条。 */
    fun setDefaultPlanet(worldId: Long) = db.runInTransaction {
        db.planetDao().clearDefault()
        planet(worldId)?.let {
            it.isDefault = true
            db.planetDao().update(it)
        }
    }

    /** 没有默认行星时把序号最小的那颗扶正。 */
    fun ensureDefaultPlanet() {
        if (defaultPlanet() != null) return
        planets().firstOrNull()?.let { setDefaultPlanet(it.worldId) }
    }

    fun insertPlanet(planet: Planet) {
        if (!planet.builtIn) planet.ordinal = db.planetDao().nextOrdinal()
        db.planetDao().insert(planet)
    }

    fun updatePlanet(planet: Planet) = db.planetDao().update(planet)

    fun deletePlanet(planet: Planet) = db.planetDao().delete(planet)

    fun identities(): List<Identity> = db.identityDao().all()

    fun identity(uid: Long): Identity? = db.identityDao().byUid(uid)

    /** 插入身份并返回自增 uid；序号按加入顺序分配。 */
    fun insertIdentity(identity: Identity): Long {
        identity.ordinal = db.identityDao().nextOrdinal()
        return db.identityDao().insert(identity)
    }

    fun defaultIdentity(): Identity? = db.identityDao().defaultRow()

    /** 设为默认身份：先清掉旧的，再标记这一条。 */
    fun setDefaultIdentity(uid: Long) = db.runInTransaction {
        db.identityDao().clearDefault()
        db.identityDao().byUid(uid)?.let {
            it.isDefault = true
            db.identityDao().update(it)
        }
    }

    /** 没有任何身份时补一条并设为默认。返回当前默认身份的 uid（可能为 null 只在插入失败时）。 */
    fun ensureDefaultIdentity(): Long? {
        defaultIdentity()?.let { return it.uid }
        val first = identities().firstOrNull()
        if (first != null) {
            setDefaultIdentity(first.uid)
            return first.uid
        }
        val uid = insertIdentity(
            Identity(alias = "", createdAt = System.currentTimeMillis(), isDefault = true)
        )
        return uid
    }

    fun updateIdentity(identity: Identity) = db.identityDao().update(identity)

    fun deleteIdentity(identity: Identity) = db.identityDao().delete(identity)

    /** 引擎生成密钥后回写节点 ID。 */
    fun setNodeId(uid: Long, nodeId: Long) {
        val identity = db.identityDao().byUid(uid) ?: return
        if (identity.nodeId == nodeId) return
        identity.nodeId = nodeId
        db.identityDao().update(identity)
    }

    // ---- moons ----

    fun moonOrbits(): List<MoonOrbit> = db.moonOrbitDao().all()

    fun moonOrbit(worldId: Long): MoonOrbit? = db.moonOrbitDao().byWorldId(worldId)

    fun moonOrbit(worldId: Long, seed: Long): MoonOrbit? =
        db.moonOrbitDao().byWorldIdAndSeed(worldId, seed)

    fun hasMoonOrbit(worldId: Long): Boolean = db.moonOrbitDao().count(worldId) > 0

    fun updateMoonOrbit(moon: MoonOrbit) = db.moonOrbitDao().update(moon)

    fun insertMoonOrbit(moon: MoonOrbit) {
        moon.ordinal = db.moonOrbitDao().nextOrdinal()
        db.moonOrbitDao().insert(moon)
    }

    fun deleteMoonOrbit(moon: MoonOrbit) = db.moonOrbitDao().delete(moon)

    // ---- relations ----

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
}
