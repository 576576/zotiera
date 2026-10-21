package net.sumitm.zotiera.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ZotieraApplication
import net.sumitm.zotiera.identity.InstanceStore
import net.sumitm.zotiera.model.Planet
import net.sumitm.zotiera.ui.component.AppDialog
import net.sumitm.zotiera.ui.component.AppDialogAction
import net.sumitm.zotiera.ui.component.AppFab
import net.sumitm.zotiera.ui.component.AppListRow
import net.sumitm.zotiera.ui.component.AppTopBar
import net.sumitm.zotiera.ui.component.appColors
import net.sumitm.zotiera.ui.component.preference.PreferenceTextField
import net.sumitm.zotiera.ui.theme.LocalZotieraType

/**
 * 管理 Planet：官方 planet 固定在最上面，自定义的从文件导入并起别名。
 *
 * 结构照 Moon 页：列表 + 右下角添加按钮；添加时先选文件，再填别名。
 */
@Composable
fun PlanetScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as ZotieraApplication).getRepository() }
    val store = remember { InstanceStore(context) }

    var refreshKey by remember { mutableIntStateOf(0) }
    var menuPlanet by remember { mutableStateOf<Planet?>(null) }
    var renameTarget by remember { mutableStateOf<Planet?>(null) }
    var deleteTarget by remember { mutableStateOf<Planet?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingWorldId by remember { mutableStateOf<Long?>(null) }
    var aliasDraft by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val planets = remember(refreshKey) { repository.planets() }
    val inUseMsg = stringResource(R.string.planet_in_use)
    val wrongFormat = stringResource(R.string.planet_wrong_format)
    val existsFmt = stringResource(R.string.planet_exists)

    val pickPlanet = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null || bytes.size < 9 || bytes[0] != 0x01.toByte()) {
            message = wrongFormat
            return@rememberLauncherForActivityResult
        }
        var worldId = 0L
        for (i in 1..8) worldId = (worldId shl 8) + (bytes[i].toLong() and 0xff)
        val existing = repository.planet(worldId)
        if (existing != null) {
            message = String.format(existsFmt, existing.alias)
            return@rememberLauncherForActivityResult
        }
        pendingBytes = bytes
        pendingWorldId = worldId
        aliasDraft = java.lang.Long.toHexString(worldId)
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
                        leading = Icons.Rounded.Public,
                        leadingTint = if (planet.builtIn) appColors().primary else Color.Unspecified,
                        trailing = {
                            if (planet.isDefault) {
                                Text(
                                    text = stringResource(R.string.planet_default_tag),
                                    style = LocalZotieraType.current.rowSummary,
                                    color = appColors().primary,
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
                icon = Icons.Rounded.Add,
                onClick = { pickPlanet.launch(arrayOf("application/octet-stream", "*/*")) },
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

    // 选完文件：填别名
    val bytes = pendingBytes
    val worldId = pendingWorldId
    if (bytes != null && worldId != null) {
        AppDialog(
            title = stringResource(R.string.add_planet),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = {
                val alias = aliasDraft.trim()
                if (alias.isNotEmpty()) {
                    store.planetFile(worldId).parentFile?.mkdirs()
                    store.planetFile(worldId).writeBytes(bytes)
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
        AppDialog(
            title = planet.alias,
            confirmLabel = stringResource(android.R.string.cancel),
            onConfirm = { menuPlanet = null },
            onDismiss = { menuPlanet = null },
        ) {
            Column {
                if (!planet.isDefault) {
                    AppDialogAction(stringResource(R.string.action_set_default)) {
                        repository.setDefaultPlanet(planet.worldId)
                        refreshKey++
                        menuPlanet = null
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
                    repository.updatePlanet(planet)
                    refreshKey++
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
        val inUse = remember(planet.worldId) {
            repository.networks().any { it.planetId == planet.worldId } ||
                repository.moonOrbits(planet.worldId).isNotEmpty()
        }
        AppDialog(
            title = stringResource(R.string.action_delete),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = {
                if (inUse) {
                    message = inUseMsg
                } else {
                    store.planetFile(planet.worldId).delete()
                    repository.deletePlanet(planet)
                    repository.ensureDefaultPlanet()
                    refreshKey++
                }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
            dismissLabel = stringResource(android.R.string.cancel),
        ) {
            Text(
                text = stringResource(if (inUse) R.string.planet_in_use else R.string.planet_delete_confirm),
                color = appColors().onSurface,
            )
        }
    }
}
