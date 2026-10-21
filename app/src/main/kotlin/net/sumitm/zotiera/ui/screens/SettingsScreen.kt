package net.sumitm.zotiera.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import net.sumitm.zotiera.BuildConfig
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ui.NodeViewModel
import net.sumitm.zotiera.ui.UiMode
import net.sumitm.zotiera.ui.component.AppHeader
import net.sumitm.zotiera.ui.component.preference.PreferenceArrow
import net.sumitm.zotiera.ui.component.preference.PreferenceGroup
import net.sumitm.zotiera.ui.component.preference.PreferenceSelect
import net.sumitm.zotiera.ui.component.preference.PreferenceSwitch
import net.sumitm.zotiera.ui.theme.ColorMode
import net.sumitm.zotiera.util.AppLanguage
import net.sumitm.zotiera.util.Constants

@Composable
fun SettingsScreen(
    @Suppress("UNUSED_PARAMETER") viewModel: NodeViewModel,
    uiMode: UiMode,
    onUiModeChange: (UiMode) -> Unit,
    colorMode: ColorMode,
    onColorModeChange: (ColorMode) -> Unit,
    onLanguageChange: (String) -> Unit,
    onOpenIdentities: () -> Unit,
    onOpenPlanet: () -> Unit,
    onOpenMoons: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var cellular by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_NETWORK_USE_CELLULAR_DATA, false)) }
    var disableIpv6 by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_NETWORK_DISABLE_IPV6, false)) }
    var startOnBoot by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_GENERAL_START_ZEROTIER_ON_BOOT, false)) }
    var simpleMode by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_SIMPLE_MODE, true)) }
    var hideFromRecents by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_HIDE_FROM_RECENTS, false)) }
    val language = remember { AppLanguage.current(context) }
    val languageLabels = AppLanguage.TAGS.map {
        if (it.isEmpty()) stringResource(R.string.app_language_system) else languageName(it)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppHeader(title = stringResource(R.string.settings))

        // 通用
        PreferenceGroup {
            PreferenceSwitch(
                title = stringResource(R.string.simple_mode),
                summary = stringResource(R.string.simple_mode_summary),
                checked = simpleMode,
                onCheckedChange = {
                    simpleMode = it
                    prefs.edit { putBoolean(Constants.PREF_SIMPLE_MODE, it) }
                },
            )
            PreferenceSwitch(
                title = stringResource(R.string.preferences_general_start_on_boot),
                summary = stringResource(R.string.start_on_boot_summary),
                checked = startOnBoot,
                onCheckedChange = {
                    startOnBoot = it
                    prefs.edit { putBoolean(Constants.PREF_GENERAL_START_ZEROTIER_ON_BOOT, it) }
                },
            )
            PreferenceSwitch(
                title = stringResource(R.string.hide_from_recents),
                summary = stringResource(R.string.hide_from_recents_summary),
                checked = hideFromRecents,
                onCheckedChange = {
                    hideFromRecents = it
                    prefs.edit { putBoolean(Constants.PREF_HIDE_FROM_RECENTS, it) }
                },
            )
        }

        // 外观
        PreferenceGroup {
            PreferenceSelect(
                title = stringResource(R.string.app_language),
                summary = stringResource(R.string.app_language_summary),
                items = languageLabels,
                selectedIndex = AppLanguage.TAGS.indexOf(language).coerceAtLeast(0),
                onSelected = { onLanguageChange(AppLanguage.TAGS[it]) },
            )
            PreferenceSelect(
                title = stringResource(R.string.ui_style),
                summary = stringResource(R.string.ui_style_summary),
                items = UiMode.entries.map { it.label },
                selectedIndex = UiMode.entries.indexOf(uiMode),
                onSelected = { onUiModeChange(UiMode.entries[it]) },
            )
            PreferenceSelect(
                title = stringResource(R.string.color_mode),
                summary = stringResource(R.string.color_mode_summary),
                items = ColorMode.entries.map { colorModeLabel(it) },
                selectedIndex = ColorMode.entries.indexOf(colorMode),
                onSelected = { onColorModeChange(ColorMode.entries[it]) },
            )
        }

        // 网络
        PreferenceGroup {
            PreferenceSwitch(
                title = stringResource(R.string.preferences_network_use_cell_data),
                summary = stringResource(R.string.use_cell_data_summary),
                checked = cellular,
                onCheckedChange = {
                    cellular = it
                    prefs.edit { putBoolean(Constants.PREF_NETWORK_USE_CELLULAR_DATA, it) }
                },
            )
            PreferenceSwitch(
                title = stringResource(R.string.preferences_network_disable_ipv6),
                summary = stringResource(R.string.disable_ipv6_summary),
                checked = disableIpv6,
                onCheckedChange = {
                    disableIpv6 = it
                    prefs.edit { putBoolean(Constants.PREF_NETWORK_DISABLE_IPV6, it) }
                },
            )
        }

        // 网络相关的管理入口：行星 → 卫星 → 身份；简易模式只留卫星
        PreferenceGroup {
            if (!simpleMode) {
                PreferenceArrow(
                    title = stringResource(R.string.manage_planet),
                    summary = stringResource(R.string.manage_planet_summary),
                    onClick = onOpenPlanet,
                )
            }
            PreferenceArrow(
                title = stringResource(R.string.manage_moon),
                summary = stringResource(R.string.moon_orbit_summary),
                onClick = onOpenMoons,
            )
            if (!simpleMode) {
                PreferenceArrow(
                    title = stringResource(R.string.manage_identity),
                    summary = stringResource(R.string.manage_identity_summary),
                    onClick = onOpenIdentities,
                )
            }
        }

        PreferenceGroup {
            PreferenceArrow(
                title = stringResource(R.string.about),
                summary = "Zotiera ${BuildConfig.VERSION_NAME}",
                onClick = onOpenAbout,
            )
        }

        // 末尾留白：滚动到底时最后一张卡片不贴住底栏
        Spacer(Modifier.height(24.dp))
    }
}


/** 语言名用各自的写法，切到英文界面也不会把「简体中文」翻掉。 */
private fun languageName(tag: String): String = when (tag) {
    "zh" -> "简体中文"
    "en" -> "English"
    else -> tag
}

@Composable
private fun colorModeLabel(mode: ColorMode): String = when (mode) {
    ColorMode.SYSTEM -> stringResource(R.string.color_mode_system)
    ColorMode.LIGHT -> stringResource(R.string.color_mode_light)
    ColorMode.DARK -> stringResource(R.string.color_mode_dark)
}
