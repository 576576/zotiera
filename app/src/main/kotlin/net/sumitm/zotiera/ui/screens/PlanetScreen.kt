package net.sumitm.zotiera.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ui.component.AppDialog
import net.sumitm.zotiera.ui.component.AppTopBar
import net.sumitm.zotiera.ui.component.appColors
import net.sumitm.zotiera.ui.component.preference.PreferenceArrow
import net.sumitm.zotiera.ui.component.preference.PreferenceGroup
import net.sumitm.zotiera.ui.component.preference.PreferenceSwitch
import net.sumitm.zotiera.ui.component.preference.PreferenceTextField
import net.sumitm.zotiera.util.Constants
import java.io.File
import java.net.URL

@Composable
fun PlanetScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 提示文案在组合期取好，回调里不再摸 LocalContext 的资源
    val planetWrongFormat = stringResource(R.string.planet_wrong_format)
    val cannotDownloadPlanet = stringResource(R.string.cannot_download_planet_file)
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val scope = rememberCoroutineScope()

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
        else planetWrongFormat
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.manage_planet), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
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
                Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = appColors().error)
            }
            Spacer(Modifier.height(24.dp))
        }
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
                    }.getOrElse { cannotDownloadPlanet }
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
