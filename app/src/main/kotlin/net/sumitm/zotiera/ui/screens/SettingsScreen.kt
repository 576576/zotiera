package net.sumitm.zotiera.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.sumitm.zotiera.BuildConfig
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ui.NodeViewModel
import net.sumitm.zotiera.ui.UiMode
import net.sumitm.zotiera.ui.component.AppDialog
import net.sumitm.zotiera.ui.component.AppHeader
import net.sumitm.zotiera.ui.component.preference.PreferenceArrow
import net.sumitm.zotiera.ui.component.preference.PreferenceGroup
import net.sumitm.zotiera.ui.component.preference.PreferenceSelect
import net.sumitm.zotiera.ui.component.preference.PreferenceSwitch
import net.sumitm.zotiera.ui.component.preference.PreferenceTextField
import net.sumitm.zotiera.ui.component.preference.SectionTitle
import net.sumitm.zotiera.ui.theme.ColorMode
import net.sumitm.zotiera.util.Constants
import java.io.File
import java.net.URL

@Composable
fun SettingsScreen(
    @Suppress("UNUSED_PARAMETER") viewModel: NodeViewModel,
    uiMode: UiMode,
    onUiModeChange: (UiMode) -> Unit,
    colorMode: ColorMode,
    onColorModeChange: (ColorMode) -> Unit,
    amoled: Boolean,
    onAmoledChange: (Boolean) -> Unit,
    onOpenMoons: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val scope = rememberCoroutineScope()

    var cellular by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_NETWORK_USE_CELLULAR_DATA, false)) }
    var disableIpv6 by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_NETWORK_DISABLE_IPV6, false)) }
    var startOnBoot by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_GENERAL_START_ZEROTIER_ON_BOOT, true)) }
    var customPlanet by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_PLANET_USE_CUSTOM, false)) }
    var planetMessage by remember { mutableStateOf<String?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }

    fun planetLoaded(): Boolean = File(context.filesDir, Constants.FILE_CUSTOM_PLANET).exists()

    fun importPlanet(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes[0] != 0x01.toByte()) return false
        File(context.filesDir, Constants.FILE_CUSTOM_PLANET).writeBytes(bytes)
        prefs.edit { putBoolean(Constants.PREF_SET_PLANET_FILE, true) }
        return true
    }

    val pickPlanet = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        planetMessage = if (bytes != null && importPlanet(bytes)) null
        else context.getString(R.string.planet_wrong_format)
    }

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

        SectionTitle(stringResource(R.string.preferences_general))
        PreferenceGroup {
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

        SectionTitle(stringResource(R.string.planet_settings))
        PreferenceGroup {
            PreferenceSwitch(
                title = stringResource(R.string.use_custom_planet),
                summary = stringResource(R.string.use_custom_planet_summary),
                checked = customPlanet,
                onCheckedChange = {
                    customPlanet = it
                    prefs.edit { putBoolean(Constants.PREF_PLANET_USE_CUSTOM, it) }
                },
            )
            PreferenceArrow(
                title = stringResource(R.string.import_via_file),
                summary = if (planetLoaded()) stringResource(R.string.load_planet) else null,
                onClick = { pickPlanet.launch(arrayOf("application/octet-stream", "*/*")) },
            )
            PreferenceArrow(
                title = stringResource(R.string.import_via_url),
                summary = null,
                onClick = { showUrlDialog = true },
            )
        }
        planetMessage?.let {
            Text(it, modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error)
        }

        SectionTitle(stringResource(R.string.moon_orbit_activity))
        PreferenceGroup {
            PreferenceArrow(
                title = stringResource(R.string.moon_orbit_activity),
                summary = stringResource(R.string.moon_orbit_summary),
                onClick = onOpenMoons,
            )
        }

        // 末尾留白：滚动到底时最后一张卡片不贴住底栏
        Spacer(Modifier.height(24.dp))
    }

    if (showUrlDialog) {
        var url by remember { mutableStateOf("") }
        AppDialog(
            title = stringResource(R.string.import_via_url),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = {
                showUrlDialog = false
                scope.launch {
                    planetMessage = runCatching {
                        require(url.startsWith("https://")) { "https only" }
                        val bytes = withContext(Dispatchers.IO) {
                            URL(url).openStream().use { it.readBytes() }
                        }
                        if (!importPlanet(bytes)) error("bad header")
                        null
                    }.getOrElse { context.getString(R.string.cannot_download_planet_file) }
                }
            },
            onDismiss = { showUrlDialog = false },
            dismissLabel = stringResource(android.R.string.cancel),
        ) {
            PreferenceTextField(
                value = url,
                onValueChange = { url = it.trim() },
                label = stringResource(R.string.enter_planet_file_url),
            )
        }
    }
}


@Composable
private fun colorModeLabel(mode: ColorMode): String = when (mode) {
    ColorMode.SYSTEM -> stringResource(R.string.color_mode_system)
    ColorMode.LIGHT -> stringResource(R.string.color_mode_light)
    ColorMode.DARK -> stringResource(R.string.color_mode_dark)
}
