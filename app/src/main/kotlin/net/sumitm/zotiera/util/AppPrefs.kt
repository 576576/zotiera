package net.sumitm.zotiera.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用偏好。
 *
 * 文件名与 `PreferenceManager.getDefaultSharedPreferences` 一致（`<包名>_preferences`），
 * 已经存下的设置照旧读得到。
 */
object AppPrefs {
    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
}
