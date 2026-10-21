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
import androidx.compose.material.icons.rounded.Badge
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
import net.sumitm.zotiera.identity.IdentityStore
import net.sumitm.zotiera.model.Identity
import net.sumitm.zotiera.ui.component.AppDialog
import net.sumitm.zotiera.ui.component.AppDialogAction
import net.sumitm.zotiera.ui.component.AppFab
import net.sumitm.zotiera.ui.component.AppListRow
import net.sumitm.zotiera.ui.component.AppTopBar
import net.sumitm.zotiera.ui.component.appColors
import net.sumitm.zotiera.ui.component.preference.PreferenceTextField

/**
 * 管理身份：结构照 Moon 页，可以有多条。
 *
 * 「生成新身份」只建一行，密钥等这个身份第一次启用时由引擎补齐；「从文件导入」直接写正本。
 */
@Composable
fun IdentityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as ZotieraApplication).getRepository() }
    val store = remember { IdentityStore(context) }

    var refreshKey by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var menuIdentity by remember { mutableStateOf<Identity?>(null) }
    var renameTarget by remember { mutableStateOf<Identity?>(null) }
    var deleteTarget by remember { mutableStateOf<Identity?>(null) }
    var aliasDraft by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val identities = remember(refreshKey) { repository.identities() }
    // 文案在组合期取好，回调里不再摸 LocalContext 的资源
    val defaultName = stringResource(R.string.identity_default_name)
    val importFailed = stringResource(R.string.identity_import_failed)
    val existsMsg = stringResource(R.string.identity_exists)
    val inUseMsg = stringResource(R.string.identity_in_use)
    val aliasFormat = stringResource(R.string.identity_default_alias)

    val importIdentity = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        val imported = bytes?.let { store.parseZip(it) }
        if (imported == null) {
            message = importFailed
            return@rememberLauncherForActivityResult
        }
        if (repository.identities().any { it.nodeId == imported.nodeId }) {
            message = existsMsg
            return@rememberLauncherForActivityResult
        }
        val uid = repository.insertIdentity(
            Identity(
                alias = String.format("%010x", imported.nodeId),
                nodeId = imported.nodeId,
                createdAt = System.currentTimeMillis(),
            )
        )
        store.writeKeys(uid, imported.secret, imported.public)
        refreshKey++
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = stringResource(R.string.manage_identity), onBack = onBack)
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(identities, key = { it.uid }) { identity ->
                    val active = store.hasKeys(identity.uid) || identity.nodeId != null
                    AppListRow(
                        title = identity.alias.ifEmpty { defaultName },
                        subtitle = identity.nodeIdString.ifEmpty { stringResource(R.string.identity_not_generated) },
                        monoSubtitle = true,
                        leading = Icons.Rounded.Badge,
                        leadingTint = if (active) appColors().primary else Color.Unspecified,
                        onClick = { menuIdentity = identity },
                        onLongClick = { menuIdentity = identity },
                    )
                }
            }
            AppFab(
                label = stringResource(R.string.add_identity),
                icon = Icons.Rounded.Add,
                onClick = { showAdd = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            )
        }
    }

    message?.let { text ->
        AppDialog(
            title = stringResource(R.string.manage_identity),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = { message = null },
            onDismiss = { message = null },
        ) {
            Text(text, color = appColors().onSurface)
        }
    }

    if (showAdd) {
        AppDialog(
            title = stringResource(R.string.add_identity),
            confirmLabel = stringResource(android.R.string.cancel),
            onConfirm = { showAdd = false },
            onDismiss = { showAdd = false },
        ) {
            Column {
                AppDialogAction(stringResource(R.string.identity_generate)) {
                    val index = repository.identities().size + 1
                    repository.insertIdentity(
                        Identity(
                            alias = String.format(aliasFormat, index),
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                    showAdd = false
                    refreshKey++
                }
                AppDialogAction(stringResource(R.string.identity_import)) {
                    showAdd = false
                    importIdentity.launch(arrayOf("application/zip", "*/*"))
                }
            }
        }
    }

    menuIdentity?.let { identity ->
        AppDialog(
            title = identity.alias.ifEmpty { defaultName },
            confirmLabel = stringResource(android.R.string.cancel),
            onConfirm = { menuIdentity = null },
            onDismiss = { menuIdentity = null },
        ) {
            Column {
                AppDialogAction(stringResource(R.string.action_rename)) {
                    aliasDraft = identity.alias.ifEmpty { defaultName }
                    renameTarget = identity
                    menuIdentity = null
                }
                AppDialogAction(stringResource(R.string.action_delete), error = true) {
                    deleteTarget = identity
                    menuIdentity = null
                }
            }
        }
    }

    renameTarget?.let { identity ->
        AppDialog(
            title = stringResource(R.string.action_rename),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = {
                val alias = aliasDraft.trim()
                if (alias.isNotEmpty()) {
                    identity.alias = alias
                    repository.updateIdentity(identity)
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

    deleteTarget?.let { identity ->
        val inUse = remember(identity.uid) { repository.networks().any { it.identityUid == identity.uid } }
        AppDialog(
            title = stringResource(R.string.action_delete),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = {
                if (inUse) {
                    message = inUseMsg
                } else {
                    store.dir(identity.uid).deleteRecursively()
                    repository.deleteIdentity(identity)
                    refreshKey++
                }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
            dismissLabel = stringResource(android.R.string.cancel),
        ) {
            Text(
                text = stringResource(if (inUse) R.string.identity_in_use else R.string.identity_delete_confirm),
                color = appColors().onSurface,
            )
        }
    }
}
