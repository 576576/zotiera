package net.sumitm.zotiera.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import net.sumitm.zotiera.BuildConfig
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ZotieraApplication
import net.sumitm.zotiera.identity.InstanceStore
import net.sumitm.zotiera.model.Identity
import net.sumitm.zotiera.model.Network
import net.sumitm.zotiera.model.Planet
import net.sumitm.zotiera.ui.NodeUiState
import net.sumitm.zotiera.ui.NodeViewModel
import net.sumitm.zotiera.ui.component.AppDialog
import net.sumitm.zotiera.ui.component.AppDialogAction
import net.sumitm.zotiera.ui.component.AppFab
import net.sumitm.zotiera.ui.component.AppLargeTitlePage
import net.sumitm.zotiera.ui.component.AppListRow
import net.sumitm.zotiera.ui.component.AppRoleChip
import net.sumitm.zotiera.ui.component.AppStatusCard
import net.sumitm.zotiera.ui.component.AppStatusTone
import net.sumitm.zotiera.ui.component.AppSwitch
import net.sumitm.zotiera.ui.component.appColors
import net.sumitm.zotiera.ui.component.instanceChipTint
import net.sumitm.zotiera.ui.component.isWideLayout
import net.sumitm.zotiera.ui.component.planetLetter
import net.sumitm.zotiera.ui.component.preference.PreferenceSelect
import net.sumitm.zotiera.ui.component.preference.PreferenceTextField
import net.sumitm.zotiera.ui.toast
import net.sumitm.zotiera.util.Constants
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
    // 点胶囊换身份：先选身份，没用过的再确认一次
    var identityTarget by remember { mutableStateOf<Network?>(null) }
    var pendingSwitch by remember { mutableStateOf<Pair<Network, Identity>?>(null) }
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
    val defaultPlanetId = planets.firstOrNull { it.isDefault }?.worldId ?: Planet.OFFICIAL_WORLD_ID
    val visibleNetworks = if (simpleMode) {
        state.networks.filter { it.planetId == defaultPlanetId }
    } else {
        state.networks
    }
    val activePlanet = planets.firstOrNull { it.worldId == activePlanetId }
    val activeAlias = when {
        activePlanet == null -> ""
        activePlanet.builtIn -> stringResource(R.string.official_planet)
        activePlanet.alias.isNotEmpty() -> activePlanet.alias
        else -> activePlanet.worldIdHex
    }
    val activeIdentity = identities.firstOrNull { it.uid == activeIdentityUid }
    val activeIdentityAlias = when {
        activeIdentity == null -> ""
        activeIdentity.alias.isNotEmpty() -> activeIdentity.alias
        else -> stringResource(R.string.identity_default_alias, activeIdentity.ordinal)
    }
    val instanceHint = stringResource(R.string.instance_busy_hint)
    // 卡片左下角的实例标记：行星字母 + 身份序号，和网络行上的胶囊同一套
    val activeInstance = if (activePlanet != null && activeIdentity != null) {
        "${planetLetter(activePlanet.ordinal)}${activeIdentity.ordinal}"
    } else {
        null
    }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(if (wide) 0.42f else 1f).fillMaxSize()) {
            AppLargeTitlePage(title = stringResource(R.string.app_name)) {
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(8.dp))
                    DeviceCard(
                        state = state,
                        instance = activeInstance,
                        onOpen = onOpenDevice,
                        onToggleTunnel = { toggleTunnel(!state.tunnelRunning) },
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 简易模式只服务默认实例，别的行星上的网络在这里没有意义
                        items(visibleNetworks, key = { it.networkId }) { network ->
                            // 已经连着某个实例时，别的实例的网络不能同时开
                            val enabled = activePlanetId == null ||
                                (network.planetId == activePlanetId && network.identityUid == activeIdentityUid)
                            // 简易模式不显示胶囊（那里只有默认实例），其余情况常显
                            val planet = planets.firstOrNull { it.worldId == network.planetId }
                            val identity = identities.firstOrNull { it.uid == network.identityUid }
                            val chip = if (!simpleMode && planet != null && identity != null) {
                                "${planetLetter(planet.ordinal)}${identity.ordinal}" to
                                    instanceChipTint(planet.ordinal, identity.ordinal)
                            } else {
                                null
                            }
                            NetworkRow(
                                network = network,
                                joined = network.lastActivated,
                                enabled = enabled,
                                // 同行星时差异在身份上，提示就报身份名
                                hint = if (enabled) null else String.format(
                                    instanceHint,
                                    if (network.planetId == activePlanetId) activeIdentityAlias else activeAlias,
                                ),
                                chip = chip,
                                onChipClick = { identityTarget = network },
                                onClick = { openNetwork(network.networkId) },
                                onJoinedChange = { joined -> viewModel.setJoined(network, joined) },
                            )
                        }
                    }
                }
            }
            AppFab(
                label = stringResource(R.string.join_network),
                icon = R.drawable.ic_add,
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
            planets = planets.filter { it.enabled },
            identities = identities.filter { it.enabled },
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

    // 点胶囊：换这条网络用哪个身份（行星不动）
    identityTarget?.let { network ->
        val used = remember(network.networkId) { repository.usedIdentityUids(network.networkId) }
        val letter = planetLetter(planets.firstOrNull { it.worldId == network.planetId }?.ordinal ?: 0)
        AppDialog(
            title = stringResource(R.string.switch_identity),
            confirmLabel = stringResource(android.R.string.cancel),
            onConfirm = { identityTarget = null },
            onDismiss = { identityTarget = null },
        ) {
            Column {
                identities.filter { it.enabled }.forEach { identity ->
                    val known = identity.uid in used
                    val name = identity.alias.ifEmpty {
                        stringResource(R.string.identity_default_alias, identity.ordinal)
                    }
                    val current = identity.uid == network.identityUid
                    AppDialogAction(
                        label = "$letter${identity.ordinal}  $name" +
                            if (current) "  ·  ${stringResource(R.string.identity_current)}" else "",
                        muted = !known,
                        onClick = {
                            identityTarget = null
                            when {
                                current -> Unit
                                known -> viewModel.setNetworkIdentity(network, identity.uid)
                                else -> pendingSwitch = network to identity
                            }
                        },
                    )
                }
            }
        }
    }

    // 没在这条网络上用过的身份：换过去就是新节点，先讲清楚
    pendingSwitch?.let { (network, identity) ->
        AppDialog(
            title = stringResource(R.string.switch_identity_confirm_title),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = {
                viewModel.setNetworkIdentity(network, identity.uid)
                pendingSwitch = null
            },
            onDismiss = { pendingSwitch = null },
            dismissLabel = stringResource(android.R.string.cancel),
        ) {
            Text(
                text = stringResource(R.string.switch_identity_confirm_body),
                color = appColors().onSurface,
            )
        }
    }
}

/**
 * 设备卡：状态、版本、当前实例与网络数；单击开关隧道，长按进设备页。
 *
 * 没有加入任何网络时卡片是红底大叉，底行留空。
 */
@Composable
private fun DeviceCard(
    state: NodeUiState,
    instance: String?,
    onOpen: () -> Unit,
    onToggleTunnel: () -> Unit,
) {
    val tone = when {
        state.joinedNetworkIds.isEmpty() -> AppStatusTone.EMPTY
        !state.tunnelRunning -> AppStatusTone.STOPPED
        state.online -> AppStatusTone.WORKING
        else -> AppStatusTone.CONNECTING
    }
    val title = stringResource(
        when (tone) {
            AppStatusTone.WORKING -> R.string.status_working
            AppStatusTone.CONNECTING -> R.string.status_connecting
            else -> R.string.status_disconnected
        }
    )
    val footer = if (instance == null) {
        null
    } else {
        "$instance · " + pluralStringResource(
            R.plurals.network_count,
            state.joinedNetworkIds.size,
            state.joinedNetworkIds.size,
        )
    }
    AppStatusCard(
        title = title,
        subtitle = stringResource(R.string.version_line, BuildConfig.VERSION_NAME),
        footer = footer,
        tone = tone,
        onClick = onToggleTunnel,
        onLongClick = onOpen,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/**
 * 网络行：开关 = 加入/离开该网络；点行 = 看详情。
 *
 * 已经连着别的实例时 [enabled] 为 false，副标题换成提示；[chip] 非空时在开关左边显示
 * 「行星字母 + 身份序号」一枚胶囊，点它换这条网络用的身份。
 */
@Composable
private fun NetworkRow(
    network: Network,
    joined: Boolean,
    enabled: Boolean,
    hint: String?,
    chip: Pair<String, Color>?,
    onChipClick: () -> Unit,
    onClick: () -> Unit,
    onJoinedChange: (Boolean) -> Unit,
) {
    val displayName = network.networkName?.takeIf { it.isNotBlank() }
    val hex = java.lang.Long.toHexString(network.networkId)
    AppListRow(
        title = displayName ?: hex,
        subtitle = when {
            hint != null -> hint
            displayName != null -> hex
            else -> null
        },
        monoSubtitle = hint == null,
        leading = R.drawable.ic_language,
        leadingTint = if (joined) appColors().primary else Color.Unspecified,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chip != null) {
                    AppRoleChip(
                        label = chip.first,
                        tint = chip.second,
                        modifier = Modifier.clickable(onClick = onChipClick),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                AppSwitch(checked = joined, onCheckedChange = onJoinedChange, enabled = enabled)
            }
        },
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
                    items = planets.map {
                        when {
                            it.builtIn -> stringResource(R.string.official_planet)
                            it.alias.isNotEmpty() -> it.alias
                            else -> it.worldIdHex
                        }
                    },
                    selectedIndex = planetIndex,
                    onSelected = { planetIndex = it },
                )
                PreferenceSelect(
                    title = stringResource(R.string.identity_label),
                    summary = null,
                    items = identities.map {
                        it.alias.ifEmpty { stringResource(R.string.identity_default_alias, it.ordinal) }
                    },
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
