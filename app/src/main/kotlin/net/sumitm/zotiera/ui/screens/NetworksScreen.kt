package net.sumitm.zotiera.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.sumitm.zotiera.BuildConfig
import net.sumitm.zotiera.R
import net.sumitm.zotiera.model.Network
import net.sumitm.zotiera.ui.Extras
import net.sumitm.zotiera.ui.NodeViewModel
import net.sumitm.zotiera.ui.view.NetworkDetailActivity
import net.sumitm.zotiera.util.NetworkIdUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NetworksScreen(viewModel: NodeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showJoin by remember { mutableStateOf(false) }
    var pendingNetworkId by remember { mutableStateOf<Long?>(null) }

    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingNetworkId
        pendingNetworkId = null
        if (result.resultCode == Activity.RESULT_OK && id != null) {
            viewModel.startService(id)
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    fun connect(network: Network, connect: Boolean) {
        if (!connect) {
            viewModel.stopService()
            return
        }
        val prepare = VpnService.prepare(context)
        if (prepare != null) {
            pendingNetworkId = network.networkId
            vpnPermission.launch(prepare)
        } else {
            viewModel.startService(network.networkId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(
                            stringResource(R.string.app_version_format, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showJoin = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.join_network)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            NodeCard(
                nodeId = state.nodeId,
                online = state.online,
                clientVersion = state.clientVersion,
            )
            Spacer(Modifier.height(12.dp))
            if (state.networks.isEmpty()) {
                Text(
                    stringResource(R.string.no_data),
                    modifier = Modifier.padding(top = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.networks, key = { it.networkId }) { network ->
                        NetworkRow(
                            network = network,
                            connected = state.connectNetworkId == network.networkId,
                            onToggle = { connect(network, it) },
                            onOpen = {
                                context.startActivity(
                                    Intent(context, NetworkDetailActivity::class.java)
                                        .putExtra(Extras.NETWORK_ID, network.networkId)
                                )
                            },
                            onDelete = { viewModel.deleteNetwork(network) },
                        )
                    }
                }
            }
        }
    }

    if (showJoin) {
        JoinNetworkDialog(
            onDismiss = { showJoin = false },
            onConfirm = { id, defaultRoute, dnsMode ->
                viewModel.joinNetwork(NetworkIdUtils.hexStringToLong(id), id, defaultRoute, dnsMode)
                showJoin = false
            },
        )
    }
}

@Composable
private fun NodeCard(nodeId: String, online: Boolean, clientVersion: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.device_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(
                        if (online) R.string.status_online else R.string.status_offline
                    ),
                    color = if (online) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = nodeId.ifEmpty { "----------" },
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
            if (clientVersion.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.peer_client_version) + ": " + clientVersion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NetworkRow(
    network: Network,
    connected: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true }),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = network.networkName?.takeIf { it.isNotBlank() }
                        ?: network.networkIdStr.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = network.networkIdStr.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = connected, onCheckedChange = onToggle)
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_network)) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun JoinNetworkDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean, Int) -> Unit,
) {
    var networkId by remember { mutableStateOf("") }
    var defaultRoute by remember { mutableStateOf(false) }
    var dnsMode by remember { mutableStateOf(1) }
    val valid = networkId.length == 16 && networkId.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.join_network)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = networkId,
                    onValueChange = { networkId = it.trim() },
                    label = { Text(stringResource(R.string.network_id)) },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.default_route))
                    Spacer(Modifier.weight(1f))
                    Switch(checked = defaultRoute, onCheckedChange = { defaultRoute = it })
                }
                Text(stringResource(R.string.network_dns_mode), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0 to R.string.network_dns_mode_no_dns,
                        1 to R.string.network_dns_mode_network_dns,
                        2 to R.string.network_dns_mode_custom_dns,
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = dnsMode == mode,
                            onClick = { dnsMode = mode },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(networkId.lowercase(), defaultRoute, dnsMode) },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
