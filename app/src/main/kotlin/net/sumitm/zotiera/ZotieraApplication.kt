package net.sumitm.zotiera

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.sumitm.zotiera.data.ZotieraDatabase
import net.sumitm.zotiera.data.ZotieraRepository
import net.sumitm.zotiera.util.AppLanguage
import net.sumitm.zotiera.util.AppPrefs

/** 主程序入口。 */
class ZotieraApplication : Application() {

    lateinit var repository: ZotieraRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: Context) {
        // 应用内语言：服务、通知这些不走 Activity 的地方也要跟着变
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // debug 只记日志：SharedPreferences 首次读取也会命中，penaltyDeath 会让 debug 包开机即崩
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build(),
            )
        }
        Log.i("Application", "Starting Application")
        pruneObsoletePrefs()
        repository = ZotieraRepository(ZotieraDatabase.create(this))
        // 种子数据异步补：界面第一次读库前会 ensureSeed() 等着，谁先到都不会读到半成品
        appScope.launch { repository.ensureSeed() }
    }

    /** 这一版已经不再读的偏好键，启动时删掉，免得留在 shared_prefs 里。 */
    private fun pruneObsoletePrefs() {
        val prefs = AppPrefs.get(this)
        val editor = prefs.edit()
        var dirty = false
        for (key in OBSOLETE_PREF_KEYS) {
            if (prefs.contains(key)) {
                editor.remove(key)
                dirty = true
            }
        }
        if (dirty) {
            editor.apply()
        }
    }

    private companion object {
        val OBSOLETE_PREF_KEYS = arrayOf(
            "planet_use_custom",
            "set_planet_file",
            "disable_no_notification_alert",
            "amoled_black",
        )
    }
}
