package net.sumitm.zotiera.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.sumitm.zotiera.R
import net.sumitm.zotiera.model.Network
import net.sumitm.zotiera.ui.NodeUiState
import net.sumitm.zotiera.ui.NodeViewModel
import net.sumitm.zotiera.ui.component.AppCard
import net.sumitm.zotiera.ui.component.AppDialog
import net.sumitm.zotiera.ui.component.AppFab
import net.sumitm.zotiera.ui.component.AppHeader
import net.sumitm.zotiera.ui.component.AppListRow
import net.sumitm.zotiera.ui.component.AppSwitch
import net.sumitm.zotiera.ui.component.AppTunnelSwitch
import net.sumitm.zotiera.ui.component.appColors
import net.sumitm.zotiera.ui.component.isWideLayout
import net.sumitm.zotiera.ui.component.preference.PreferenceSelect
import net.sumitm.zotiera.ui.component.preference.PreferenceTextField
import net.sumitm.zotiera.ui.theme.LocalZotieraType
import net.sumitm.zotiera.ui.toast
import net.sumitm.zotiera.util.NetworkIdUtils

@Composable
fun OverviewScreen(
    viewModel: NodeViewModel,
    onOpenDevice: () -> Unit,
    onOpenNetwork: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showJoin by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val wide = isWideLayout()

    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            toast(context, R.string.connecting_toast)
            viewModel.startTunnel()
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // 只有宽屏才在原地展开详情；窄屏是独立页面，返回由导航处理
    BackHandler(enabled = wide && selectedId != null) { selectedId = null }

    fun openNetwork(id: Long) {
        if (wide) selectedId = id else onOpenNetwork(id)
    }

    fun toggleTunnel(on: Boolean) {
        if (!on) {
            viewModel.stopTunnel()
            return
        }
        if (state.joinedNetworkIds.isEmpty()) {
            toast(context, R.string.tunnel_needs_network)
            return
        }
        val prepare = VpnService.prepare(context)
        if (prepare != null) {
            vpnPermission.launch(prepare)
        } else {
            // 连上要等节点上线，这里先给「正在连接」
            toast(context, R.string.connecting_toast)
            viewModel.startTunnel()
        }
    }

    val selected = state.networks.firstOrNull { it.networkId == selectedId }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(if (wide) 0.42f else 1f).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                AppHeader(
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.network_list),
                )
                DeviceCard(
                    state = state,
                    onOpen = onOpenDevice,
                    onToggleTunnel = ::toggleTunnel,
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
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.networks, key = { it.networkId }) { network ->
                            NetworkRow(
                                network = network,
                                joined = network.lastActivated,
                                onClick = { openNetwork(network.networkId) },
                                onJoinedChange = { joined -> viewModel.setJoined(network, joined) },
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
                    NetworkDetailPane(
                        network = selected,
                        config = state.configs[selected.networkId],
                        onDelete = {
                            viewModel.deleteNetwork(selected)
                            selectedId = null
                        },
                    )
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

    if (showJoin) {
        JoinNetworkDialog(
            onDismiss = { showJoin = false },
            onConfirm = { id, dnsMode ->
                viewModel.joinNetwork(NetworkIdUtils.hexStringToLong(id), id, false, dnsMode)
                showJoin = false
            },
        )
    }
}

/**
 * 设备卡：图标 + 节点 ID + 「N 个网络 · 在线」+ 隧道总开关。
 *
 * 总开关只管隧道；加入了哪些网络由列表里各自的开关决定。
 */
@Composable
private fun DeviceCard(
    state: NodeUiState,
    onOpen: () -> Unit,
    onToggleTunnel: (Boolean) -> Unit,
) {
    val type = LocalZotieraType.current
    val colors = appColors()
    val subtitle = buildString {
        append(pluralStringResource(R.plurals.network_count, state.joinedNetworkIds.size, state.joinedNetworkIds.size))
        append(" · ")
        append(
            stringResource(
                when {
                    state.tunnelRunning && state.online -> R.string.status_online
                    state.tunnelRunning -> R.string.status_connecting
                    else -> R.string.status_offline
                }
            )
        )
    }
    AppCard(Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标放一个浅色圆底，和列表里的单色小图标区分开
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (state.tunnelRunning) colors.primary.copy(alpha = 0.14f)
                        else colors.onSurfaceVariant.copy(alpha = 0.10f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Smartphone,
                    contentDescription = null,
                    tint = if (state.tunnelRunning) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = state.nodeId.ifEmpty { "----------" },
                    style = type.deviceTitle,
                    color = colors.onSurface,
                )
                Text(text = subtitle, style = type.rowSummary, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            AppTunnelSwitch(checked = state.tunnelRunning, onCheckedChange = onToggleTunnel)
        }
    }
}

/** 网络行：开关 = 加入/离开该网络；点行 = 看详情。 */
@Composable
private fun NetworkRow(
    network: Network,
    joined: Boolean,
    onClick: () -> Unit,
    onJoinedChange: (Boolean) -> Unit,
) {
    val displayName = network.networkName?.takeIf { it.isNotBlank() }
    AppListRow(
        title = displayName ?: java.lang.Long.toHexString(network.networkId),
        subtitle = if (displayName != null) java.lang.Long.toHexString(network.networkId) else null,
        monoSubtitle = true,
        leading = Icons.Rounded.Language,
        leadingTint = if (joined) appColors().primary else Color.Unspecified,
        trailing = { AppSwitch(checked = joined, onCheckedChange = onJoinedChange) },
        onClick = onClick,
    )
}

@Composable
private fun JoinNetworkDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var networkId by remember { mutableStateOf("") }
    var dnsMode by remember { mutableStateOf(1) }
    val valid = networkId.length == 16 && networkId.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    AppDialog(
        title = stringResource(R.string.join_network),
        confirmLabel = stringResource(android.R.string.ok),
        onConfirm = { if (valid) onConfirm(networkId.lowercase(), dnsMode) },
        onDismiss = onDismiss,
        dismissLabel = stringResource(android.R.string.cancel),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PreferenceTextField(
                value = networkId,
                onValueChange = { networkId = it.trim() },
                label = stringResource(R.string.network_id),
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
