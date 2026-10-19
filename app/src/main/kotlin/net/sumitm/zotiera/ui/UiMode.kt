package net.sumitm.zotiera.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 界面风格。默认 Miuix，Material 是备选。
 */
enum class UiMode(val value: String, val label: String) {
    Miuix("miuix", "Miuix"),
    Material("material", "Material");

    companion object {
        val DEFAULT = Miuix
        fun fromValue(value: String?): UiMode =
            entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

val LocalUiMode = staticCompositionLocalOf { UiMode.DEFAULT }
