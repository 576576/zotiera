package net.sumitm.zotiera.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import net.sumitm.zotiera.ui.MoonOrbitActivity
import net.sumitm.zotiera.ui.NodeViewModel
import net.sumitm.zotiera.util.Constants
import java.io.File
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(@Suppress("UNUSED_PARAMETER") viewModel: NodeViewModel) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val scope = rememberCoroutineScope()

    var cellular by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_NETWORK_USE_CELLULAR_DATA, false)) }
    var disableIpv6 by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_NETWORK_DISABLE_IPV6, false)) }
    var startOnBoot by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_GENERAL_START_ZEROTIER_ON_BOOT, true)) }
    var customPlanet by remember { mutableStateOf(prefs.getBoolean(Constants.PREF_PLANET_USE_CUSTOM, false)) }
    var planetMessage by remember { mutableStateOf<String?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }

    fun importPlanet(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes[0] != 0x01.toByte()) return false
        val dest = File(context.filesDir, Constants.FILE_CUSTOM_PLANET)
        dest.writeBytes(bytes)
        prefs.edit { putBoolean(Constants.PREF_SET_PLANET_FILE, true) }
        return true
    }

    val pickPlanet = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        planetMessage = if (bytes != null && importPlanet(bytes)) null else context.getString(R.string.planet_wrong_format)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingSwitch(
                title = stringResource(R.string.preferences_network_use_cell_data),
                checked = cellular,
                onChange = {
                    cellular = it
                    prefs.edit { putBoolean(Constants.PREF_NETWORK_USE_CELLULAR_DATA, it) }
                },
            )
            SettingSwitch(
                title = stringResource(R.string.preferences_network_disable_ipv6),
                checked = disableIpv6,
                onChange = {
                    disableIpv6 = it
                    prefs.edit { putBoolean(Constants.PREF_NETWORK_DISABLE_IPV6, it) }
                },
            )
            SettingSwitch(
                title = stringResource(R.string.preferences_general_start_on_boot),
                checked = startOnBoot,
                onChange = {
                    startOnBoot = it
                    prefs.edit { putBoolean(Constants.PREF_GENERAL_START_ZEROTIER_ON_BOOT, it) }
                },
            )
            SettingSwitch(
                title = stringResource(R.string.use_custom_planet),
                checked = customPlanet,
                onChange = {
                    customPlanet = it
                    prefs.edit { putBoolean(Constants.PREF_PLANET_USE_CUSTOM, it) }
                },
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.planet_settings), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (File(context.filesDir, Constants.FILE_CUSTOM_PLANET).exists())
                            stringResource(R.string.load_planet) else stringResource(R.string.no_data),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickPlanet.launch(arrayOf("application/octet-stream", "*/*")) }) {
                            Text(stringResource(R.string.import_via_file))
                        }
                        TextButton(onClick = { showUrlDialog = true }) {
                            Text(stringResource(R.string.import_via_url))
                        }
                    }
                    planetMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            HorizontalDivider()
            TextButton(
                onClick = { context.startActivity(Intent(context, MoonOrbitActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.moon_orbit_activity))
            }
            HorizontalDivider()
            Text(
                text = "Zotiera ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }

    if (showUrlDialog) {
        var url by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text(stringResource(R.string.import_via_url)) },
            text = {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    label = { Text(stringResource(R.string.enter_planet_file_url)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUrlDialog = false
                    scope.launch {
                        val error = runCatching {
                            require(url.startsWith("https://")) { "https only" }
                            val bytes = withContext(Dispatchers.IO) {
                                URL(url).openStream().use { it.readBytes() }
                            }
                            if (!importPlanet(bytes)) throw IllegalStateException("bad header")
                            null
                        }.getOrElse { context.getString(R.string.cannot_download_planet_file) }
                        planetMessage = error
                    }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
