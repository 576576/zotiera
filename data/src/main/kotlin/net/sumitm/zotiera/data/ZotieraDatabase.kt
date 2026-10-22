package net.sumitm.zotiera.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.sumitm.zotiera.model.AssignedAddress
import net.sumitm.zotiera.model.DnsServer
import net.sumitm.zotiera.model.Identity
import net.sumitm.zotiera.model.MoonOrbit
import net.sumitm.zotiera.model.Network
import net.sumitm.zotiera.model.NetworkConfig
import net.sumitm.zotiera.model.NetworkIdentity
import net.sumitm.zotiera.model.Planet

/**
 * 应用数据库。
 *
 * schema 从 v6 起只增不毁：改表必须同时加 `@AutoMigration` 或手写 `Migration`，
 * 不允许再用破坏式兜底，否则用户一升级就丢网络与身份。
 */
@Database(
    entities = [
        Network::class,
        NetworkConfig::class,
        DnsServer::class,
        AssignedAddress::class,
        Planet::class,
        Identity::class,
        MoonOrbit::class,
        NetworkIdentity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ZotieraDatabase : RoomDatabase() {

    abstract fun networkDao(): NetworkDao

    abstract fun networkConfigDao(): NetworkConfigDao

    abstract fun dnsServerDao(): DnsServerDao

    abstract fun assignedAddressDao(): AssignedAddressDao

    abstract fun planetDao(): PlanetDao

    abstract fun identityDao(): IdentityDao

    abstract fun moonOrbitDao(): MoonOrbitDao

    abstract fun networkIdentityDao(): NetworkIdentityDao

    companion object {
        const val NAME = "zotiera.db"

        @JvmStatic
        fun create(context: Context): ZotieraDatabase =
            Room.databaseBuilder(context, ZotieraDatabase::class.java, NAME)
                // 启动时的种子数据（官方行星、默认身份）仍是阻塞调用，见 ZotieraApplication。
                .allowMainThreadQueries()
                .build()
    }
}
