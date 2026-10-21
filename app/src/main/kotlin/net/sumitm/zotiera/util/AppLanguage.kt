package net.sumitm.zotiera.util

import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import java.util.Locale

/**
 * 应用内语言。
 *
 * 只改本应用的资源解析，不动系统设置。空串表示跟随系统。
 * minSdk 30 上 `LocaleManager` 要到 33 才有，所以统一走 [wrap] 覆盖配置，行为与系统版本无关。
 */
object AppLanguage {

    const val PREF = "app_language"
    const val SYSTEM = ""

    /** 可选语言；标签与系统 Locale 一致。 */
    val TAGS = listOf(SYSTEM, "en", "zh")

    @JvmStatic
    fun current(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(PREF, SYSTEM).orEmpty()

    /** 按当前偏好包一层带 locale 的 Context；跟随系统时原样返回。 */
    @JvmStatic
    fun wrap(base: Context): Context {
        val tag = current(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
