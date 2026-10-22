package net.sumitm.zotiera.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale
import net.sumitm.zotiera.util.AppPrefs

/**
 * 应用语言。
 *
 * API 33+ 系统设置里的「应用语言」与这里的偏好保持同一份状态：系统里设过就以系统为准并回写偏好，
 * 应用内切换也写进系统；30–32 没有系统入口，只走偏好。
 */
object AppLanguage {

    const val PREF = "app_language"
    const val SYSTEM = ""

    /** 可选语言；标签与系统 Locale 一致。 */
    val TAGS = listOf(SYSTEM, "en", "zh", "fr", "ru", "es", "ja")

    @JvmStatic
    fun current(context: Context): String {
        val system = systemTag(context)
        if (system.isNotEmpty()) {
            if (prefs(context).getString(PREF, SYSTEM) != system) {
                prefs(context).edit { putString(PREF, system) }
            }
            return system
        }
        return normalize(prefs(context).getString(PREF, SYSTEM).orEmpty())
    }

    /** 按当前语言包一层带 locale 的 Context；跟随系统时原样返回。 */
    @JvmStatic
    fun wrap(base: Context): Context {
        val tag = current(base)
        if (tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(localeTag(tag)))
        return base.createConfigurationContext(config)
    }

    /**
     * 写入语言选择。
     *
     * 返回 true 表示 API 33+ 已交给系统保存，系统会自行重建界面，调用方不要再 [android.app.Activity.recreate]。
     */
    @JvmStatic
    fun apply(context: Context, tag: String): Boolean {
        val normalized = normalize(tag)
        prefs(context).edit { putString(PREF, normalized) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val manager = context.getSystemService(LocaleManager::class.java) ?: return false
        manager.applicationLocales = if (normalized.isEmpty()) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(localeTag(normalized))
        }
        return true
    }

    /** 系统「应用语言」里选过的值；没设过返回空串。 */
    private fun systemTag(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return SYSTEM
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
        if (locales == null || locales.isEmpty) return SYSTEM
        return normalize(locales[0]?.toLanguageTag().orEmpty())
    }

    /** 只保留本项目带资源的语言，其它值一律按跟随系统处理。 */
    private fun normalize(tag: String): String {
        val lang = tag.substringBefore('-').lowercase()
        return if (lang in TAGS) lang else SYSTEM
    }

    /** 传给 LocaleList 的标签要带书写系统，才能命中 values-zh。 */
    private fun localeTag(tag: String): String = if (tag == "zh") "zh-Hans" else tag

    private fun prefs(context: Context) = AppPrefs.get(context)
}
