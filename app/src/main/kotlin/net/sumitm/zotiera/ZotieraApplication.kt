package net.sumitm.zotiera

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import net.sumitm.zotiera.data.ZotieraDatabase
import net.sumitm.zotiera.data.ZotieraRepository
import net.sumitm.zotiera.identity.InstanceStore
import net.sumitm.zotiera.util.AppLanguage

/** 主程序入口。 */
class ZotieraApplication : Application() {

    lateinit var repository: ZotieraRepository
        private set

    override fun attachBaseContext(base: Context) {
        // 应用内语言：服务、通知这些不走 Activity 的地方也要跟着变
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("Application", "Starting Application")
        pruneObsoletePrefs()
        repository = ZotieraRepository(ZotieraDatabase.create(this))
        // 行为稳定前不做数据接续：旧布局整个丢掉，身份由引擎重新生成
        InstanceStore(this).resetLegacyLayout()
        repository.ensureOfficialPlanet()
        repository.ensureDefaultIdentity()
    }

    /** 这一版已经不再读的偏好键，启动时删掉，免得留在 shared_prefs 里。 */
    private fun pruneObsoletePrefs() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
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
