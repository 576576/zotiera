package net.sumitm.zotiera.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.File
import net.sumitm.zotiera.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.sumitm.zotiera.ZotieraApplication
import net.sumitm.zotiera.identity.InstanceStore
import net.sumitm.zotiera.model.MoonOrbit
import net.sumitm.zotiera.ui.NodeViewModel
import net.sumitm.zotiera.ui.component.AppDialog
import net.sumitm.zotiera.ui.component.AppDialogAction
import net.sumitm.zotiera.ui.component.AppFab
import net.sumitm.zotiera.ui.component.AppListRow
import net.sumitm.zotiera.ui.component.AppSwitch
import net.sumitm.zotiera.ui.component.AppTopBar
import net.sumitm.zotiera.ui.component.appColors
import net.sumitm.zotiera.ui.component.preference.PreferenceTextField
import net.sumitm.zotiera.ui.theme.LocalZotieraType

@Composable
fun MoonsScreen(
    viewModel: NodeViewModel,
    onBack: () -> Unit,
    embedded: Boolean = false,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val repository = remember { (context.applicationContext as ZotieraApplication).repository }
    val scope = rememberCoroutineScope()

    var refreshKey by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<MoonOrbit?>(null) }
    var menuMoon by remember { mutableStateOf<MoonOrbit?>(null) }
    var deleteMoon by remember { mutableStateOf<MoonOrbit?>(null) }
    val store = remember { InstanceStore(context) }
    // 身份在服务起来之前可能还没定过，回落到默认身份
    val activeUid by produceState(store.activeIdentityUid) {
        val stored = store.activeIdentityUid
        value = if (repository.identity(stored) != null) stored else repository.defaultIdentity()?.uid ?: -1L
    }
    val instanceDir = store.instanceDir(store.activePlanetId, activeUid)
    val moons by produceState(emptyList<MoonOrbit>(), refreshKey, instanceDir) {
        // checkCacheFile 要看文件在不在，和查库一起放 IO
        value = withContext(Dispatchers.IO) {
            repository.moonOrbits().onEach { it.checkCacheFile(instanceDir) }
        }
    }

    suspend fun addMoon(worldId: Long, seed: Long, fromFile: Boolean) {
        if (repository.hasMoonOrbit(worldId)) return
        val moon = MoonOrbit(
            moonWorldId = worldId,
            moonSeed = seed,
            fromFile = fromFile,
            source = if (fromFile) MoonOrbit.SOURCE_FILE else MoonOrbit.SOURCE_MANUAL,
            addedAt = System.currentTimeMillis(),
        )
        repository.insertMoonOrbit(moon)
        viewModel.orbitMoon(moon)
        refreshKey++
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes == null || bytes.size < 9 || bytes[0] != 0x01.toByte()) {
                return@launch
            }
            var worldId = 0L
            for (i in 1..8) worldId = (worldId shl 8) + (bytes[i].toLong() and 0xff)
            withContext(Dispatchers.IO) {
                val dest = File(instanceDir, String.format(MoonOrbit.MOON_FILE_PATH, worldId))
                dest.parentFile?.mkdirs()
                dest.writeBytes(bytes)
            }
            addMoon(worldId, worldId ushr 24, fromFile = true)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val moon = pendingExport
        pendingExport = null
        if (uri == null || moon == null) return@rememberLauncherForActivityResult
        val source = File(instanceDir, String.format(MoonOrbit.MOON_FILE_PATH, moon.moonWorldId))
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            } ?: error("no output stream")
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!embedded) AppTopBar(title = stringResource(R.string.manage_moon), onBack = onBack)
        Box(Modifier.fillMaxSize()) {
            if (moons.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_data),
                    color = appColors().onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(moons, key = { it.moonWorldId }) { moon ->
                        AppListRow(
                            title = java.lang.Long.toHexString(moon.moonWorldId),
                            subtitle = when {
                                moon.fromFile -> stringResource(R.string.external_file)
                                moon.isCacheFile -> stringResource(R.string.cached)
                                else -> stringResource(R.string.wait_to_fetch)
                            },
                            leading = R.drawable.ic_dark_mode,
                            leadingTint = if (moon.isCacheFile) appColors().primary else Color.Unspecified,
                            trailing = {
                                AppSwitch(
                                    checked = moon.enabled,
                                    onCheckedChange = { on ->
                                        moon.enabled = on
                                        scope.launch {
                                            repository.updateMoonOrbit(moon)
                                            if (on) {
                                                viewModel.orbitMoon(moon)
                                            } else {
                                                // 停用等于从引擎摘掉：删缓存文件再撤掉入轨
                                                withContext(Dispatchers.IO) { moon.deleteCacheFile(instanceDir) }
                                                viewModel.removeMoonOrbit(moon)
                                            }
                                            refreshKey++
                                        }
                                    },
                                )
                            },
                            onClick = { menuMoon = moon },
                            onLongClick = { menuMoon = moon },
                        )
                    }
                }
            }
            AppFab(
                label = stringResource(R.string.add),
                icon = R.drawable.ic_add,
                onClick = { showAdd = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            )
        }
    }

    menuMoon?.let { moon ->
        AppDialog(
            title = java.lang.Long.toHexString(moon.moonWorldId),
            confirmLabel = stringResource(android.R.string.cancel),
            onConfirm = { menuMoon = null },
            onDismiss = { menuMoon = null },
        ) {
            Column {
                AppDialogAction(stringResource(R.string.copy_moon_world_id_to_clipboard)) {
                    clipboard.setText(
                        androidx.compose.ui.text.AnnotatedString(java.lang.Long.toHexString(moon.moonWorldId))
                    )
                    menuMoon = null
                }
                if (moon.isCacheFile) {
                    AppDialogAction(stringResource(R.string.export_moon_file)) {
                        pendingExport = moon
                        exportLauncher.launch(String.format("%016x.moon", moon.moonWorldId))
                        menuMoon = null
                    }
                }
                if (moon.isCacheFile && !moon.fromFile) {
                    AppDialogAction(stringResource(R.string.delete_cache)) {
                        moon.deleteCacheFile(instanceDir)
                        refreshKey++
                        menuMoon = null
                    }
                }
                AppDialogAction(stringResource(R.string.delete_moon_orbit), error = true) {
                    deleteMoon = moon
                    menuMoon = null
                }
            }
        }
    }

    deleteMoon?.let { moon ->
        AppDialog(
            title = stringResource(R.string.delete_moon_orbit),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = {
                deleteMoon = null
                scope.launch {
                    withContext(Dispatchers.IO) { moon.deleteCacheFile(instanceDir) }
                    repository.deleteMoonOrbit(moon)
                    viewModel.removeMoonOrbit(moon)
                    refreshKey++
                }
            },
            onDismiss = { deleteMoon = null },
            dismissLabel = stringResource(android.R.string.cancel),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.delete_irreversible_warning),
                    color = appColors().onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = java.lang.Long.toHexString(moon.moonWorldId),
                    style = LocalZotieraType.current.mono,
                    color = appColors().onSurfaceVariant,
                )
            }
        }
    }

    if (showAdd) {
        var worldIdText by remember { mutableStateOf("") }
        var seedText by remember { mutableStateOf("") }
        val worldIdValid = worldIdText.length in 1..16 &&
            worldIdText.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        val seedValid = seedText.isEmpty() || (seedText.length <= 10 &&
            seedText.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })

        AppDialog(
            title = stringResource(R.string.moon_orbit_info),
            confirmLabel = stringResource(R.string.add),
            onConfirm = {
                if (worldIdValid && seedValid) {
                    val worldId = java.lang.Long.parseUnsignedLong(worldIdText, 16)
                    val seed = if (seedText.isEmpty()) worldId ushr 24
                    else java.lang.Long.parseUnsignedLong(seedText, 16)
                    scope.launch { addMoon(worldId, seed, fromFile = false) }
                    showAdd = false
                }
            },
            onDismiss = { showAdd = false },
            dismissLabel = stringResource(android.R.string.cancel),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PreferenceTextField(
                    value = worldIdText,
                    onValueChange = { worldIdText = it.trim() },
                    label = stringResource(R.string.moon_world_id),
                )
                PreferenceTextField(
                    value = seedText,
                    onValueChange = { seedText = it.trim() },
                    label = stringResource(R.string.moon_seed),
                )
                AppDialogAction(stringResource(R.string.import_via_file)) {
                    showAdd = false
                    importLauncher.launch(arrayOf("*/*"))
                }
            }
        }
    }
}

