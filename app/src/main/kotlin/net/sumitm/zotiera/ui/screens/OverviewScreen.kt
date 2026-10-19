package net.sumitm.zotiera.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.sumitm.zotiera.R
import net.sumitm.zotiera.model.Network
import net.sumitm.zotiera.ui.NodeViewModel
import net.sumitm.zotiera.ui.component.AppCard
import net.sumitm.zotiera.ui.component.AppDialog
import net.sumitm.zotiera.ui.component.AppFab
import net.sumitm.zotiera.ui.component.AppHeader
import net.sumitm.zotiera.ui.component.AppKeyValueRow
import net.sumitm.zotiera.ui.component.AppListRow
import net.sumitm.zotiera.ui.component.AppTopBar
import net.sumitm.zotiera.ui.component.appColors
import net.sumitm.zotiera.ui.component.isWideLayout
import net.sumitm.zotiera.ui.component.preference.PreferenceSelect
import net.sumitm.zotiera.ui.component.preference.PreferenceSwitch
import net.sumitm.zotiera.ui.component.preference.PreferenceTextField
import net.sumitm.zotiera.ui.theme.LocalZotieraType
import net.sumitm.zotiera.util.NetworkIdUtils

@Composable
fun OverviewScreen(viewModel: NodeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showJoin by remember { mutableStateOf(false) }
    var pendingNetworkId by remember { mutableStateOf<Long?>(null) }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingNetworkId
        pendingNetworkId = null
        if (result.resultCode == Activity.RESULT_OK && id != null) viewModel.startService(id)
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

    val wide = isWideLayout()
    val selected = state.networks.firstOrNull { it.networkId == selectedId }

    // 详情展开时，返回键先收起详情，而不是直接退出应用
    androidx.activity.compose.BackHandler(enabled = selectedId != null) { selectedId = null }

    // 连接 / 断开提示
    var lastConnected by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.connectNetworkId) {
        val now = state.connectNetworkId
        val message = when {
            now != null && now != lastConnected -> R.string.connected_toast
            now == null && lastConnected != null -> R.string.disconnected_toast
            else -> null
        }
        lastConnected = now
        message?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
    }

    if (!wide && selected != null) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = selected.networkName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.network_detail_label),
                onBack = { selectedId = null },
            )
            NetworkDetail(viewModel, selected, Modifier.fillMaxSize())
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(if (wide) 0.42f else 1f).fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    AppHeader(
                        title = stringResource(R.string.app_name),
                        subtitle = stringResource(R.string.network_list),
                    )
                    NodeSummary(
                        nodeId = state.nodeId,
                        online = state.online,
                        clientVersion = state.clientVersion,
                    )
                    if (state.networks.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_data),
                            color = appColors().onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.networks, key = { it.networkId }) { network ->
                                val displayName = network.networkName?.takeIf { it.isNotBlank() }
                                AppListRow(
                                    title = displayName ?: java.lang.Long.toHexString(network.networkId),
                                    // 没有名字时标题已经是 ID，别再重复一行
                                    subtitle = if (displayName != null) {
                                        java.lang.Long.toHexString(network.networkId)
                                    } else {
                                        null
                                    },
                                    monoSubtitle = true,
                                    leading = Icons.Rounded.Language,
                                    leadingTint = if (state.connectNetworkId == network.networkId) {
                                        appColors().primary
                                    } else {
                                        androidx.compose.ui.graphics.Color.Unspecified
                                    },
                                    trailing = {
                                        net.sumitm.zotiera.ui.component.AppSwitch(
                                            checked = state.connectNetworkId == network.networkId,
                                            onCheckedChange = { connect(network, it) },
                                        )
                                    },
                                    onClick = { selectedId = network.networkId },
                                    onLongClick = { viewModel.deleteNetwork(network) },
                                )
                            }
                        }
                    }
                }
                AppFab(
                    label = stringResource(R.string.join_network),
                    icon = Icons.Rounded.Add,
                    onClick = { showJoin = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                )
            }
            if (wide) {
                Box(Modifier.weight(0.58f).fillMaxSize()) {
                    if (selected != null) {
                        NetworkDetail(viewModel, selected, Modifier.fillMaxSize())
                    } else {
                        Text(
                            text = stringResource(R.string.select_a_network),
                            color = appColors().onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
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
private fun statusLabel(status: com.zerotier.sdk.VirtualNetworkStatus?): String = when (status) {
    com.zerotier.sdk.VirtualNetworkStatus.NETWORK_STATUS_REQUESTING_CONFIGURATION ->
        stringResource(R.string.network_status_requesting)
    com.zerotier.sdk.VirtualNetworkStatus.NETWORK_STATUS_OK ->
        stringResource(R.string.network_status_ok)
    com.zerotier.sdk.VirtualNetworkStatus.NETWORK_STATUS_ACCESS_DENIED ->
        stringResource(R.string.network_status_access_denied)
    com.zerotier.sdk.VirtualNetworkStatus.NETWORK_STATUS_NOT_FOUND ->
        stringResource(R.string.network_status_not_found)
    com.zerotier.sdk.VirtualNetworkStatus.NETWORK_STATUS_PORT_ERROR ->
        stringResource(R.string.network_status_port_error)
    com.zerotier.sdk.VirtualNetworkStatus.NETWORK_STATUS_AUTHENTICATION_REQUIRED ->
        stringResource(R.string.network_status_auth_required)
    com.zerotier.sdk.VirtualNetworkStatus.NETWORK_STATUS_CLIENT_TOO_OLD ->
        stringResource(R.string.network_status_client_too_old)
    null -> "-"
    else -> status.name
}

@Composable
private fun NodeSummary(nodeId: String, online: Boolean, clientVersion: String) {
    val type = LocalZotieraType.current
    AppCard(Modifier.padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.device_label), style = type.rowSummary, color = appColors().onSurfaceVariant)
            Text(nodeId.ifEmpty { "----------" }, style = type.mono, color = appColors().onSurface)
            if (clientVersion.isNotEmpty()) {
                Text(clientVersion, style = type.monoSmall, color = appColors().onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NetworkDetail(viewModel: NodeViewModel, network: Network, modifier: Modifier = Modifier) {
    val config by viewModel.detailConfig.collectAsStateWithLifecycle()
    LaunchedEffect(network.networkId) { viewModel.requestNetworkConfig(network.networkId) }
    val type = LocalZotieraType.current

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 基本信息放一张卡片；只有分配到的 IP 单独成组（可能有多条）
        item {
            AppCard {
                val name = config?.name?.takeIf { it.isNotBlank() }
                    ?: network.networkName?.takeIf { it.isNotBlank() }
                val mac = config?.let {
                    com.zerotier.sdk.util.StringUtils.macAddressToString(it.mac)
                } ?: "-"
                val dns = network.networkConfig?.dnsServers.orEmpty()
                    .mapNotNull { it.nameserver?.takeIf(String::isNotBlank) }
                Column(Modifier.padding(vertical = 8.dp)) {
                    AppKeyValueRow(stringResource(R.string.network_name_label), name ?: "-")
                    AppKeyValueRow(stringResource(R.string.network_id), java.lang.Long.toHexString(network.networkId), mono = true)
                    AppKeyValueRow(stringResource(R.string.mac_address_label), mac, mono = true)
                    AppKeyValueRow(stringResource(R.string.network_status_label), statusLabel(config?.status))
                    AppKeyValueRow("MTU", config?.mtu?.toString() ?: "-", mono = true)
                    AppKeyValueRow(
                        label = stringResource(R.string.dns_label_string),
                        value = if (dns.isEmpty()) "-" else dns.joinToString(", "),
                        mono = dns.isNotEmpty(),
                    )
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(vertical = 8.dp)) {
                    val addresses = config?.assignedAddresses
                    if (addresses.isNullOrEmpty()) {
                        AppKeyValueRow(stringResource(R.string.ip_addresses_label), "-")
                    } else {
                        addresses.forEach { address ->
                            AppKeyValueRow(
                                label = stringResource(R.string.ip_addresses_label),
                                value = "${address.address.hostAddress}/${address.port}",
                                mono = true,
                            )
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                PreferenceSwitch(
                    title = stringResource(R.string.route_all_traffic),
                    summary = stringResource(R.string.route_all_traffic_summary),
                    checked = network.networkConfig?.routeViaZeroTier == true,
                    onCheckedChange = { viewModel.setRouteViaZeroTier(network, it) },
                )
            }
        }
    }
}

@Composable
private fun JoinNetworkDialog(onDismiss: () -> Unit, onConfirm: (String, Boolean, Int) -> Unit) {
    var networkId by remember { mutableStateOf("") }
    var defaultRoute by remember { mutableStateOf(false) }
    var dnsMode by remember { mutableStateOf(1) }
    val valid = networkId.length == 16 && networkId.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    AppDialog(
        title = stringResource(R.string.join_network),
        confirmLabel = stringResource(android.R.string.ok),
        onConfirm = { if (valid) onConfirm(networkId.lowercase(), defaultRoute, dnsMode) },
        onDismiss = onDismiss,
        dismissLabel = stringResource(android.R.string.cancel),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PreferenceTextField(
                value = networkId,
                onValueChange = { networkId = it.trim() },
                label = stringResource(R.string.network_id),
            )
            PreferenceSwitch(
                title = stringResource(R.string.default_route),
                summary = null,
                checked = defaultRoute,
                onCheckedChange = { defaultRoute = it },
            )
            PreferenceSelect(
                title = stringResource(R.string.network_dns_mode),
                summary = null,
                items = listOf(
                    stringResource(R.string.network_dns_mode_no_dns),
                    stringResource(R.string.network_dns_mode_network_dns),
                    stringResource(R.string.network_dns_mode_custom_dns),
                ),
                selectedIndex = dnsMode,
                onSelected = { dnsMode = it },
            )
        }
    }
}
