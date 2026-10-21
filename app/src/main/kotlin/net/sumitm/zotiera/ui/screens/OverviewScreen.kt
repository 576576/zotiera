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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.preference.PreferenceManager
import net.sumitm.zotiera.util.Constants
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ZotieraApplication
import net.sumitm.zotiera.identity.InstanceStore
import net.sumitm.zotiera.model.Identity
import net.sumitm.zotiera.model.Planet
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
    val repository = remember { (context.applicationContext as ZotieraApplication).getRepository() }
    val store = remember { InstanceStore(context) }
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val simpleMode = remember { prefs.getBoolean(Constants.PREF_SIMPLE_MODE, true) }
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

    // 当前实例由「已加入」的网络决定；一条都没加入时用上次用过的那个
    val joinedNetworks = state.networks.filter { it.lastActivated }
    val activePlanetId: Long? = joinedNetworks.firstOrNull()?.planetId
    val activeIdentityUid: Long? = joinedNetworks.firstOrNull()?.identityUid
    val planets = remember(state.networks) { repository.planets() }
    val identities = remember(state.networks) { repository.identities() }
    val activeAlias = planets.firstOrNull { it.worldId == activePlanetId }?.alias?.takeIf { it.isNotEmpty() }
        ?: stringResource(R.string.identity_default_name)
    val activeIdentityAlias = identities.firstOrNull { it.uid == activeIdentityUid }
        ?.alias?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.identity_default_name)
    val instanceHint = stringResource(R.string.instance_busy_hint)
    val defaultIdentityName = stringResource(R.string.identity_default_name)

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(if (wide) 0.42f else 1f).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                AppHeader(title = stringResource(R.string.app_name))
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
                            // 已经连着某个实例时，别的实例的网络不能同时开
                            val enabled = activePlanetId == null ||
                                (network.planetId == activePlanetId && network.identityUid == activeIdentityUid)
                            val planetAlias = planets.firstOrNull { it.worldId == network.planetId }?.alias.orEmpty()
                            val identityAlias = identities.firstOrNull { it.uid == network.identityUid }
                                ?.alias?.takeIf { it.isNotEmpty() } ?: defaultIdentityName
                            NetworkRow(
                                network = network,
                                joined = network.lastActivated,
                                enabled = enabled,
                                // 同 planet 时差异在身份上，提示就报身份名
                                hint = if (enabled) null else String.format(
                                    instanceHint,
                                    if (network.planetId == activePlanetId) activeIdentityAlias else activeAlias,
                                ),
                                instanceLabel = if (simpleMode) null else "$planetAlias · $identityAlias",
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
            simpleMode = simpleMode,
            planets = planets,
            identities = identities,
            defaultPlanetId = activePlanetId ?: store.activePlanetId,
            defaultIdentityUid = activeIdentityUid ?: store.activeIdentityUid,
            // 已经有网络在连时，新网络只能挂同一个实例
            locked = activePlanetId != null && activeIdentityUid != null,
            onDismiss = { showJoin = false },
            onConfirm = { id, dnsMode, planetId, uid ->
                viewModel.joinNetwork(NetworkIdUtils.hexStringToLong(id), id, false, dnsMode, planetId, uid)
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

/**
 * 网络行：开关 = 加入/离开该网络；点行 = 看详情。
 *
 * 已经连着别的实例时 [enabled] 为 false，副标题换成提示。
 */
@Composable
private fun NetworkRow(
    network: Network,
    joined: Boolean,
    enabled: Boolean,
    hint: String?,
    instanceLabel: String?,
    onClick: () -> Unit,
    onJoinedChange: (Boolean) -> Unit,
) {
    val displayName = network.networkName?.takeIf { it.isNotBlank() }
    val hex = java.lang.Long.toHexString(network.networkId)
    AppListRow(
        title = displayName ?: hex,
        subtitle = when {
            hint != null -> hint
            // 没有网络名时标题就是 hex，副标题不再重复一遍
            instanceLabel != null && displayName == null -> instanceLabel
            instanceLabel != null -> "$hex · $instanceLabel"
            displayName != null -> hex
            else -> null
        },
        monoSubtitle = hint == null,
        leading = Icons.Rounded.Language,
        leadingTint = if (joined) appColors().primary else Color.Unspecified,
        trailing = { AppSwitch(checked = joined, onCheckedChange = onJoinedChange, enabled = enabled) },
        onClick = onClick,
    )
}

@Composable
private fun JoinNetworkDialog(
    simpleMode: Boolean,
    planets: List<Planet>,
    identities: List<Identity>,
    defaultPlanetId: Long,
    defaultIdentityUid: Long,
    locked: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Long, Long) -> Unit,
) {
    var networkId by remember { mutableStateOf("") }
    var dnsMode by remember { mutableStateOf(1) }
    var planetIndex by remember {
        mutableIntStateOf(planets.indexOfFirst { it.worldId == defaultPlanetId }.coerceAtLeast(0))
    }
    var identityIndex by remember {
        mutableIntStateOf(identities.indexOfFirst { it.uid == defaultIdentityUid }.coerceAtLeast(0))
    }
    val defaultIdentityName = stringResource(R.string.identity_default_name)
    val valid = networkId.length == 16 && networkId.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    AppDialog(
        title = stringResource(R.string.join_network),
        confirmLabel = stringResource(android.R.string.ok),
        onConfirm = {
            if (valid && planets.isNotEmpty() && identities.isNotEmpty()) {
                onConfirm(
                    networkId.lowercase(),
                    dnsMode,
                    planets[planetIndex].worldId,
                    identities[identityIndex].uid,
                )
            }
        },
        onDismiss = onDismiss,
        dismissLabel = stringResource(android.R.string.cancel),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PreferenceTextField(
                value = networkId,
                onValueChange = { networkId = it.trim() },
                label = stringResource(R.string.network_id),
            )
            // 非简易模式：这条网络挂到哪个 Planet x 身份；已有网络在连时锁死同一个实例
            if (!simpleMode && !locked && planets.isNotEmpty() && identities.isNotEmpty()) {
                PreferenceSelect(
                    title = stringResource(R.string.planet_label),
                    summary = null,
                    items = planets.map { it.alias },
                    selectedIndex = planetIndex,
                    onSelected = { planetIndex = it },
                )
                PreferenceSelect(
                    title = stringResource(R.string.identity_label),
                    summary = null,
                    items = identities.map { it.alias.ifEmpty { defaultIdentityName } },
                    selectedIndex = identityIndex,
                    onSelected = { identityIndex = it },
                )
            }
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
