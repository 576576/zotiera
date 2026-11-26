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

/**
 * 卫星文件（`.moon`）的解析结果：world id 与卫星根身份的 ZT 地址（后作 `orbit` 的 seed）。
 */
private data class MoonStageResult(
    val worldId: Long,
    val seed: Long,
)

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
    /** 导入/导出失败时显示提示，不再静默吞掉 */
    var message by remember { mutableStateOf<String?>(null) }
    val wrongFormat = stringResource(R.string.moon_wrong_format)
    val existsFmt = stringResource(R.string.moon_exists)
    val readFailed = stringResource(R.string.moon_read_failed)
    val writeFailed = stringResource(R.string.moon_write_failed)
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

    /**
     * 解析卫星文件头：第一个字节是文件类型（0x7f 才是卫星 World::TYPE_MOON），
     * 接着 8 个字节是 world id，再往根身份处取扎实的 ZT 地址作为 seed。
     *
     * @return 解析出的 world id 与 seed，解析失败/重复时返回 null 并在 message 里告知用户
     */
    suspend fun stage(bytes: ByteArray?): MoonStageResult? {
        if (bytes == null) {
            message = wrongFormat
            return null
        }
        if (bytes[0] != MoonOrbit.TYPE_MOON.toByte()) {
            message = wrongFormat
            return null
        }
        var worldId = 0L
        for (i in 1..8) worldId = (worldId shl 8) + (bytes[i].toLong() and 0xff)
        if (repository.moonOrbit(worldId) != null) {
            message = String.format(existsFmt, java.lang.Long.toHexString(worldId))
            return null
        }
        // 卫星根身份的 ZT 地址是引擎回传卫星世界更新时判定 seed 的唯一依据，
        // 不能直接取 world id 的高 24 位——只对少数官方卫星碰巧成立。
        MoonOrbit.moonRootZtAddress(bytes)?.let { seed ->
            return MoonStageResult(worldId, seed)
        }
        message = wrongFormat
        return null
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Android 10 起文件走 SAF，读 URI 可能没有权限或抛安全异常，先包起来
            val bytes = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
            }.getOrNull()
            if (bytes == null) {
                message = readFailed
                return@launch
            }
            val moon = stage(bytes) ?: return@launch
            val error = withContext(Dispatchers.IO) {
                val dest = File(instanceDir, String.format(MoonOrbit.MOON_FILE_PATH, moon.worldId))
                dest.parentFile?.mkdirs()
                runCatching { dest.writeBytes(bytes) }.exceptionOrNull()
            }
            if (error != null) {
                message = error.message.takeIf { it?.isNotEmpty() == true } ?: writeFailed
            } else {
                // 用卫星根身份的 ZT 地址作为 seed：引擎回传卫星世界更新时只有 seed 匹配的根才会被接受，
                // 否则卫星不入 `_moons`，结点列表里就看不到月亮角色。
                addMoon(moon.worldId, moon.seed, fromFile = true)
            }
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

    message?.let { text ->
        AppDialog(
            title = stringResource(R.string.manage_moon),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = { message = null },
            onDismiss = { message = null },
        ) {
            Text(text, color = appColors().onSurface)
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

