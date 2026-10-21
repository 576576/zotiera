package net.sumitm.zotiera.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import net.sumitm.zotiera.model.AssignedAddress
import net.sumitm.zotiera.model.DnsServer
import net.sumitm.zotiera.model.Identity
import net.sumitm.zotiera.model.MoonOrbit
import net.sumitm.zotiera.model.Network
import net.sumitm.zotiera.model.NetworkConfig
import net.sumitm.zotiera.model.Planet

@Dao
interface NetworkDao {

    @Query("SELECT * FROM networks ORDER BY network_id")
    fun all(): List<Network>

    @Query("SELECT * FROM networks WHERE last_activated = 1 ORDER BY network_id")
    fun lastActivated(): List<Network>

    @Query("SELECT * FROM networks WHERE network_id = :networkId")
    fun byId(networkId: Long): Network?

    @Query("SELECT COUNT(*) FROM networks WHERE network_id = :networkId")
    fun count(networkId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(network: Network)

    @Update
    fun update(network: Network)

    @Delete
    fun delete(network: Network)
}

@Dao
interface NetworkConfigDao {

    @Query("SELECT * FROM network_configs WHERE id = :id")
    fun byId(id: Long): NetworkConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(config: NetworkConfig)

    @Delete
    fun delete(config: NetworkConfig)

    @Query("DELETE FROM network_configs WHERE id = :id")
    fun deleteById(id: Long)
}

@Dao
interface DnsServerDao {

    @Query("SELECT * FROM dns_servers WHERE network_id = :networkId ORDER BY id")
    fun forNetwork(networkId: Long): List<DnsServer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(server: DnsServer)

    @Query("DELETE FROM dns_servers WHERE network_id = :networkId")
    fun deleteForNetwork(networkId: Long)
}

@Dao
interface AssignedAddressDao {

    @Query("SELECT * FROM assigned_addresses WHERE network_id = :networkId ORDER BY id")
    fun forNetwork(networkId: Long): List<AssignedAddress>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(address: AssignedAddress)

    @Delete
    fun delete(address: AssignedAddress)
}

@Dao
interface PlanetDao {

    @Query("SELECT * FROM planets ORDER BY built_in DESC, added_at, world_id")
    fun all(): List<Planet>

    @Query("SELECT * FROM planets WHERE world_id = :worldId")
    fun byId(worldId: Long): Planet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(planet: Planet)

    @Update
    fun update(planet: Planet)

    @Delete
    fun delete(planet: Planet)
}

@Dao
interface IdentityDao {

    @Query("SELECT * FROM identities ORDER BY uid")
    fun all(): List<Identity>

    @Query("SELECT * FROM identities WHERE uid = :uid")
    fun byUid(uid: Long): Identity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(identity: Identity): Long

    @Update
    fun update(identity: Identity)

    @Delete
    fun delete(identity: Identity)
}

@Dao
interface MoonOrbitDao {

    @Query("SELECT * FROM moon_orbits ORDER BY moon_world_id")
    fun all(): List<MoonOrbit>

    @Query("SELECT * FROM moon_orbits WHERE moon_world_id = :worldId")
    fun byWorldId(worldId: Long): MoonOrbit?

    @Query("SELECT * FROM moon_orbits WHERE moon_world_id = :worldId AND moon_seed = :seed")
    fun byWorldIdAndSeed(worldId: Long, seed: Long): MoonOrbit?

    @Query("SELECT COUNT(*) FROM moon_orbits WHERE moon_world_id = :worldId")
    fun count(worldId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(moon: MoonOrbit)

    @Delete
    fun delete(moon: MoonOrbit)
}
