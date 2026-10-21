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
import net.sumitm.zotiera.model.Planet

@Database(
    entities = [
        Network::class,
        NetworkConfig::class,
        DnsServer::class,
        AssignedAddress::class,
        Planet::class,
        Identity::class,
        MoonOrbit::class,
    ],
    version = 4,
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

    companion object {
        const val NAME = "zotiera.db"

        @JvmStatic
        fun create(context: Context): ZotieraDatabase =
            Room.databaseBuilder(context, ZotieraDatabase::class.java, NAME)
                // 迁移期：旧 UI 有在主线程直读数据库的调用点，Compose 版改为 suspend 后移除。
                .allowMainThreadQueries()
                // 行为稳定前不做数据迁移：旧版本的库直接丢掉，由「没有身份就建一条」兜底
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
