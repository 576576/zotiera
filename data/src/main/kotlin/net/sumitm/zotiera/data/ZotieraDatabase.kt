package net.sumitm.zotiera.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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

        /**
         * v1 → v2：引入 planet / 身份。
         *
         * 老的 `app_node` 只有一行节点 ID，迁移时并进默认身份；所有网络默认挂在
         * 「官方 planet + 默认身份」这个实例上，行为与升级前一致。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `planets` (" +
                        "`world_id` INTEGER NOT NULL, `alias` TEXT NOT NULL, " +
                        "`built_in` INTEGER NOT NULL, `added_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`world_id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `identities` (" +
                        "`uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `alias` TEXT NOT NULL, " +
                        "`node_id` INTEGER, `created_at` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `networks` ADD COLUMN `planet_id` INTEGER NOT NULL DEFAULT 149604618")
                db.execSQL("ALTER TABLE `networks` ADD COLUMN `identity_uid` INTEGER NOT NULL DEFAULT 1")

                db.execSQL(
                    "INSERT OR REPLACE INTO `planets` (`world_id`, `alias`, `built_in`, `added_at`) " +
                        "VALUES (149604618, 'zerotier.com', 1, 0)"
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO `identities` (`uid`, `alias`, `node_id`, `created_at`) " +
                        "VALUES (1, '', (SELECT `node_id` FROM `app_node` LIMIT 1), 0)"
                )
                db.execSQL("DROP TABLE IF EXISTS `app_node`")
            }
        }

        @JvmStatic
        fun create(context: Context): ZotieraDatabase =
            Room.databaseBuilder(context, ZotieraDatabase::class.java, NAME)
                // 迁移期：旧 UI 有在主线程直读数据库的调用点，Compose 版改为 suspend 后移除。
                .allowMainThreadQueries()
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
