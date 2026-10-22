package net.sumitm.zotiera.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用偏好。
 *
 * 文件名与 `PreferenceManager.getDefaultSharedPreferences` 一致（`<包名>_preferences`），
 * 已经存下的设置照旧读得到。
 *
 * 实例缓存在这里：`Context.getSharedPreferences` 每次调用都要走一遍文件存在性检查，
 * 在主线程上会被 StrictMode 记成磁盘读。取到的 `SharedPreferences` 本身不持有 Context，缓存不会泄漏。
 */
object AppPrefs {

    @Volatile
    private var cached: SharedPreferences? = null

    fun get(context: Context): SharedPreferences =
        cached ?: synchronized(this) {
            cached ?: context
                .getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
                .also { cached = it }
        }
}
