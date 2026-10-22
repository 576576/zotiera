package net.sumitm.zotiera.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ZotieraApplication
import net.sumitm.zotiera.identity.InstanceStore
import net.sumitm.zotiera.model.Planet
import net.sumitm.zotiera.ui.component.AppDialog
import net.sumitm.zotiera.ui.component.AppDialogAction
import net.sumitm.zotiera.ui.component.AppFab
import net.sumitm.zotiera.ui.component.AppListRow
import net.sumitm.zotiera.ui.component.AppRoleChip
import net.sumitm.zotiera.ui.component.AppSwitch
import net.sumitm.zotiera.ui.component.AppTopBar
import net.sumitm.zotiera.ui.component.appColors
import net.sumitm.zotiera.ui.component.preference.PreferenceTextField
import net.sumitm.zotiera.ui.theme.LocalZotieraType

/**
 * 管理行星：官方行星固定在最上面，自定义的从文件或 URL 导入并起别名。
 *
 * 结构照卫星页：列表 + 右下角添加按钮；每行尾部有启用开关，默认行星不能停用。
 */
@Composable
fun PlanetScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as ZotieraApplication).repository }
    val store = remember { InstanceStore(context) }
    val scope = rememberCoroutineScope()

    var refreshKey by remember { mutableIntStateOf(0) }
    var menuPlanet by remember { mutableStateOf<Planet?>(null) }
    var renameTarget by remember { mutableStateOf<Planet?>(null) }
    var deleteTarget by remember { mutableStateOf<Planet?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showUrlInput by remember { mutableStateOf(false) }
    var urlDraft by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingWorldId by remember { mutableStateOf<Long?>(null) }
    var aliasDraft by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val planets by produceState(emptyList<Planet>(), refreshKey) { value = repository.planets() }
    val inUseMsg = stringResource(R.string.planet_in_use)
    val wrongFormat = stringResource(R.string.planet_wrong_format)
    val existsFmt = stringResource(R.string.planet_exists)
    val urlFailed = stringResource(R.string.planet_url_failed)

    /** 解析行星文件头：第一个字节是类型，接着 8 个字节是 world id。 */
    suspend fun stage(bytes: ByteArray?): Boolean {
        if (bytes == null || bytes.size < 9 || bytes[0] != 0x01.toByte()) {
            message = wrongFormat
            return false
        }
        var worldId = 0L
        for (i in 1..8) worldId = (worldId shl 8) + (bytes[i].toLong() and 0xff)
        val existing = repository.planet(worldId)
        if (existing != null) {
            message = String.format(
                existsFmt,
                existing.alias.ifEmpty { existing.worldIdHex },
            )
            return false
        }
        pendingBytes = bytes
        pendingWorldId = worldId
        aliasDraft = java.lang.Long.toHexString(worldId)
        return true
    }

    val pickPlanet = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val data = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            stage(data)
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.manage_planet), onBack = onBack)
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(planets, key = { it.worldId }) { planet ->
                    AppListRow(
                        title = if (planet.builtIn) stringResource(R.string.official_planet) else planet.alias,
                        subtitle = planet.worldIdHex + " · " +
                            stringResource(if (planet.builtIn) R.string.planet_builtin else R.string.planet_from_file),
                        leading = R.drawable.ic_public,
                        leadingTint = if (planet.builtIn) appColors().primary else Color.Unspecified,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (planet.isDefault) {
                                    AppRoleChip(
                                        label = stringResource(R.string.planet_default_tag),
                                        tint = appColors().primary,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                AppSwitch(
                                    checked = planet.enabled,
                                    // 默认行星停用后没有可用默认，直接锁住
                                    enabled = !planet.isDefault,
                                    onCheckedChange = {
                                        planet.enabled = it
                                        scope.launch {
                                            repository.updatePlanet(planet)
                                            refreshKey++
                                        }
                                    },
                                )
                            }
                        },
                        onClick = { menuPlanet = planet },
                        onLongClick = { menuPlanet = planet },
                    )
                }
            }
            AppFab(
                label = stringResource(R.string.add_planet),
                icon = R.drawable.ic_add,
                onClick = { showAdd = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            )
        }
    }

    message?.let { text ->
        AppDialog(
            title = stringResource(R.string.manage_planet),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = { message = null },
            onDismiss = { message = null },
        ) {
            Text(text, color = appColors().onSurface)
        }
    }

    // 添加：从文件或 URL
    if (showAdd) {
        AppDialog(
            title = stringResource(R.string.add_planet),
            onDismiss = { showAdd = false },
            dismissLabel = stringResource(android.R.string.cancel),
        ) {
            Column {
                AppDialogAction(stringResource(R.string.import_from_file)) {
                    showAdd = false
                    pickPlanet.launch(arrayOf("application/octet-stream", "*/*"))
                }
                AppDialogAction(stringResource(R.string.import_from_url)) {
                    showAdd = false
                    urlDraft = ""
                    showUrlInput = true
                }
            }
        }
    }

    if (showUrlInput) {
        AppDialog(
            title = stringResource(R.string.import_from_url),
            confirmLabel = if (downloading) {
                stringResource(R.string.downloading)
            } else {
                stringResource(android.R.string.ok)
            },
            onConfirm = {
                if (downloading) return@AppDialog
                val url = urlDraft.trim()
                if (url.isEmpty()) return@AppDialog
                downloading = true
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        runCatching { java.net.URL(url).openStream().use { it.readBytes() } }.getOrNull()
                    }
                    downloading = false
                    if (bytes == null) {
                        message = urlFailed
                    } else if (stage(bytes)) {
                        showUrlInput = false
                    }
                }
            },
            onDismiss = { if (!downloading) showUrlInput = false },
            dismissLabel = stringResource(android.R.string.cancel),
        ) {
            PreferenceTextField(
                value = urlDraft,
                onValueChange = { urlDraft = it },
                label = stringResource(R.string.planet_url_label),
            )
        }
    }

    // 选完文件或下载完：填别名
    val bytes = pendingBytes
    val worldId = pendingWorldId
    if (bytes != null && worldId != null) {
        AppDialog(
            title = stringResource(R.string.add_planet),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = {
                val alias = aliasDraft.trim()
                if (alias.isNotEmpty()) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            store.planetFile(worldId).parentFile?.mkdirs()
                            store.planetFile(worldId).writeBytes(bytes)
                        }
                        repository.insertPlanet(
                            Planet(
                                worldId = worldId,
                                alias = alias,
                                builtIn = false,
                                addedAt = System.currentTimeMillis(),
                            )
                        )
                        pendingBytes = null
                        pendingWorldId = null
                        refreshKey++
                    }
                }
            },
            onDismiss = {
                pendingBytes = null
                pendingWorldId = null
            },
            dismissLabel = stringResource(android.R.string.cancel),
        ) {
            PreferenceTextField(
                value = aliasDraft,
                onValueChange = { aliasDraft = it },
                label = stringResource(R.string.alias_label),
            )
        }
    }

    menuPlanet?.let { planet ->
        val name = if (planet.builtIn) stringResource(R.string.official_planet) else planet.alias
        AppDialog(
            title = name,
            confirmLabel = stringResource(android.R.string.cancel),
            onConfirm = { menuPlanet = null },
            onDismiss = { menuPlanet = null },
        ) {
            Column {
                if (!planet.isDefault) {
                    AppDialogAction(stringResource(R.string.action_set_default)) {
                        scope.launch {
                            repository.setDefaultPlanet(planet.worldId)
                            refreshKey++
                            menuPlanet = null
                        }
                    }
                }
                if (!planet.builtIn) {
                    AppDialogAction(stringResource(R.string.action_rename)) {
                        aliasDraft = planet.alias
                        renameTarget = planet
                        menuPlanet = null
                    }
                    AppDialogAction(stringResource(R.string.action_delete), error = true) {
                        deleteTarget = planet
                        menuPlanet = null
                    }
                }
            }
        }
    }

    renameTarget?.let { planet ->
        AppDialog(
            title = stringResource(R.string.action_rename),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = {
                val alias = aliasDraft.trim()
                if (alias.isNotEmpty()) {
                    planet.alias = alias
                    scope.launch {
                        repository.updatePlanet(planet)
                        refreshKey++
                    }
                }
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
            dismissLabel = stringResource(android.R.string.cancel),
        ) {
            PreferenceTextField(
                value = aliasDraft,
                onValueChange = { aliasDraft = it },
                label = stringResource(R.string.alias_label),
            )
        }
    }

    deleteTarget?.let { planet ->
        val inUse by produceState(false, planet.worldId) {
            value = repository.networks().any { it.planetId == planet.worldId }
        }
        AppDialog(
            title = stringResource(R.string.action_delete),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = {
                if (inUse) {
                    message = inUseMsg
                    deleteTarget = null
                } else {
                    deleteTarget = null
                    scope.launch {
                        withContext(Dispatchers.IO) { store.planetFile(planet.worldId).delete() }
                        repository.deletePlanet(planet)
                        repository.ensureDefaultPlanet()
                        refreshKey++
                    }
                }
            },
            onDismiss = { deleteTarget = null },
            dismissLabel = stringResource(android.R.string.cancel),
        ) {
            Column {
                Text(
                    text = stringResource(
                        if (inUse) R.string.planet_in_use else R.string.delete_irreversible_warning
                    ),
                    color = appColors().onSurface,
                )
                if (!inUse) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${planet.worldIdHex}  ${planet.alias}",
                        style = LocalZotieraType.current.mono,
                        color = appColors().onSurfaceVariant,
                    )
                }
            }
        }
    }
}
