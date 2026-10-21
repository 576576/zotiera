package net.sumitm.zotiera.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
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
import net.sumitm.zotiera.ui.component.preference.SectionTitle
import net.sumitm.zotiera.ui.theme.ColorMode
import net.sumitm.zotiera.util.Constants

@Composable
fun SettingsScreen(
    @Suppress("UNUSED_PARAMETER") viewModel: NodeViewModel,
    uiMode: UiMode,
    onUiModeChange: (UiMode) -> Unit,
    colorMode: ColorMode,
    onColorModeChange: (ColorMode) -> Unit,
    amoled: Boolean,
    onAmoledChange: (Boolean) -> Unit,
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

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppHeader(
            title = stringResource(R.string.settings),
            subtitle = "Zotiera ${BuildConfig.VERSION_NAME}",
        )

        SectionTitle(stringResource(R.string.appearance))
        // 外观的几项属于同一组，放在一张卡片里
        PreferenceGroup {
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
            // 纯黑只是深色的一种画法；不处在深色时没有意义，不显示
            if (colorMode.isDark(isSystemInDarkTheme())) {
                PreferenceSwitch(
                    title = stringResource(R.string.amoled),
                    summary = stringResource(R.string.amoled_summary),
                    checked = amoled,
                    onCheckedChange = onAmoledChange,
                )
            }
        }

        SectionTitle(stringResource(R.string.preferences_network))
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

        // 网络相关的管理入口，跟着网络设置走，不单独占一栏；简易模式只留 Moon
        PreferenceGroup {
            if (!simpleMode) {
                PreferenceArrow(
                    title = stringResource(R.string.manage_identity),
                    summary = stringResource(R.string.manage_identity_summary),
                    onClick = onOpenIdentities,
                )
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
        }

        SectionTitle(stringResource(R.string.preferences_general))
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


@Composable
private fun colorModeLabel(mode: ColorMode): String = when (mode) {
    ColorMode.SYSTEM -> stringResource(R.string.color_mode_system)
    ColorMode.LIGHT -> stringResource(R.string.color_mode_light)
    ColorMode.DARK -> stringResource(R.string.color_mode_dark)
}
