package net.sumitm.zotiera.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import com.zerotier.sdk.NodeStatus
import com.zerotier.sdk.Peer
import com.zerotier.sdk.Version
import com.zerotier.sdk.VirtualNetworkConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.sumitm.zotiera.ZotieraApplication
import net.sumitm.zotiera.data.ZotieraRepository
import net.sumitm.zotiera.events.IsServiceRunningReplyEvent
import net.sumitm.zotiera.events.IsServiceRunningRequestEvent
import net.sumitm.zotiera.events.LeaveNetworkEvent
import net.sumitm.zotiera.events.NetworkListReplyEvent
import net.sumitm.zotiera.events.NetworkListRequestEvent
import net.sumitm.zotiera.events.NodeDestroyedEvent
import net.sumitm.zotiera.events.NodeIDEvent
import net.sumitm.zotiera.events.NodeStatusEvent
import net.sumitm.zotiera.events.NodeStatusRequestEvent
import net.sumitm.zotiera.events.PeerInfoReplyEvent
import net.sumitm.zotiera.events.PeerInfoRequestEvent
import net.sumitm.zotiera.events.StopEvent
import net.sumitm.zotiera.events.TunnelConfigChangedEvent
import net.sumitm.zotiera.events.VirtualNetworkConfigChangedEvent
import net.sumitm.zotiera.events.VirtualNetworkConfigReplyEvent
import net.sumitm.zotiera.events.VirtualNetworkConfigRequestEvent
import net.sumitm.zotiera.identity.InstanceStore
import net.sumitm.zotiera.model.Network
import net.sumitm.zotiera.service.ZeroTierOneService
import net.sumitm.zotiera.util.Constants
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

data class NodeUiState(
    val networks: List<Network> = emptyList(),
    val nodeId: String = "",
    val online: Boolean = false,
    /** 核心版本（引擎自报） */
    val coreVersion: String = "",
    /** VPN 隧道是否在跑 */
    val tunnelRunning: Boolean = false,
    /** 已加入（成员资格）的网络 id；与隧道开关无关 */
    val joinedNetworkIds: Set<Long> = emptySet(),
    /** 全局：所有流量走 ZeroTier */
    val routeAllTraffic: Boolean = false,
    /** 每个已加入网络的实时配置，键是 network id */
    val configs: Map<Long, VirtualNetworkConfig> = emptyMap(),
    val peers: List<Peer> = emptyList(),
    val peersLoaded: Boolean = false,
)

/**
 * UI 与 ZeroTierOneService 之间的唯一通道。
 *
 * 三件事互不相干：隧道开关（[tunnelRunning]）、网络成员资格
 * （[joinedNetworkIds]，存在 `Network.lastActivated`）、详情选中（屏内局部状态）。
 */
class NodeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: ZotieraRepository =
        (app as ZotieraApplication).getRepository()

    private val prefs = PreferenceManager.getDefaultSharedPreferences(app)
    private val bus = EventBus.getDefault()
    private val _state = MutableStateFlow(
        NodeUiState(
            networks = repository.networks(),
            joinedNetworkIds = repository.lastActivatedNetworks().map { it.networkId }.toSet(),
            routeAllTraffic = prefs.getBoolean(Constants.PREF_ROUTE_ALL_TRAFFIC, false),
        )
    )
    val state: StateFlow<NodeUiState> = _state.asStateFlow()

    private var service: ZeroTierOneService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            // 绑定成功只说明服务实例在，不代表隧道在跑；开关状态由服务自己上报
            service = (binder as? ZeroTierOneService.ZeroTierBinder)?.service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    init {
        bus.register(this)
    }

    override fun onCleared() {
        bus.unregister(this)
        runCatching { getApplication<Application>().unbindService(connection) }
        super.onCleared()
    }

    // ---- 请求 ----

    fun refresh() {
        _state.update {
            it.copy(
                networks = repository.networks(),
                joinedNetworkIds = repository.lastActivatedNetworks().map { n -> n.networkId }.toSet(),
                // 服务可能已经被通知栏磁贴停掉，这里以服务实例为准
                tunnelRunning = ZeroTierOneService.isRunning,
            )
        }
        bus.post(NetworkListRequestEvent())
        bus.post(NodeStatusRequestEvent())
        bus.post(IsServiceRunningRequestEvent())
        requestConfigs()
    }

    /** 已加入网络的实时配置（MAC、状态、分配地址）。 */
    fun requestConfigs() {
        _state.value.joinedNetworkIds.forEach { bus.post(VirtualNetworkConfigRequestEvent(it)) }
    }

    fun requestPeers() {
        bus.post(PeerInfoRequestEvent())
    }

    // ---- 隧道 ----

    /** 打开总开关：启动服务，服务会把数据库里所有「已加入」的网络一起 join。 */
    fun startTunnel() {
        val context = getApplication<Application>()
        runCatching {
            context.bindService(Intent(context, ZeroTierOneService::class.java), connection, Context.BIND_AUTO_CREATE)
        }
        context.startService(Intent(context, ZeroTierOneService::class.java))
        // 先按服务实际状态刷新，再把开关置为打开（startService 是异步的，服务还没起来）
        refresh()
        _state.update { it.copy(tunnelRunning = true) }
    }

    fun stopTunnel() {
        val context = getApplication<Application>()
        runCatching { service?.stopZeroTier() }
        bus.post(StopEvent())
        context.stopService(Intent(context, ZeroTierOneService::class.java))
        runCatching { context.unbindService(connection) }
        service = null
        _state.update {
            it.copy(tunnelRunning = false, online = false, configs = emptyMap())
        }
        refresh()
    }

    /**
     * 加入 / 离开一个网络。
     *
     * 只改成员资格；隧道没开时仅写库，等下次打开隧道一起 join。
     * 一个网络都不剩时把隧道一起关掉——没有成员资格的隧道没有意义。
     */
    fun setJoined(network: Network, joined: Boolean) {
        if (joined) {
            val store = InstanceStore(getApplication())
            if (store.activePlanetId != network.planetId || store.activeIdentityUid != network.identityUid) {
                store.setActive(network.planetId, network.identityUid)
            }
        }
        if (network.lastActivated != joined) {
            network.lastActivated = joined
            repository.updateNetwork(network)
        }
        if (_state.value.tunnelRunning) {
            if (joined) {
                val context = getApplication<Application>()
                context.startService(
                    Intent(context, ZeroTierOneService::class.java)
                        .putExtra(ZeroTierOneService.ZT1_NETWORK_ID, network.networkId)
                )
            } else {
                bus.post(LeaveNetworkEvent(network.networkId))
            }
        }
        val joinedIds = repository.lastActivatedNetworks().map { it.networkId }.toSet()
        _state.update {
            it.copy(networks = repository.networks(), joinedNetworkIds = joinedIds)
        }
        if (joinedIds.isEmpty() && _state.value.tunnelRunning) {
            stopTunnel()
            return
        }
        refresh()
    }

    /** 全局「所有流量走 ZeroTier」。 */
    fun setRouteAllTraffic(enabled: Boolean) {
        prefs.edit { putBoolean(Constants.PREF_ROUTE_ALL_TRAFFIC, enabled) }
        _state.update { it.copy(routeAllTraffic = enabled) }
        bus.post(TunnelConfigChangedEvent())
    }

    fun setDnsMode(network: Network, mode: Int) {
        val config = repository.networkConfig(network.networkConfigId) ?: return
        config.dnsMode = mode
        repository.insertNetworkConfig(config)
        bus.post(TunnelConfigChangedEvent())
        refresh()
    }

    // ---- 网络增删 ----

    /** 新增网络：默认就加入（用户加它就是为了连），隧道没开时只写库。 */
    fun joinNetwork(
        networkId: Long,
        networkIdStr: String,
        defaultRoute: Boolean,
        dnsMode: Int,
        planetId: Long,
        identityUid: Long,
    ) {
        if (repository.hasNetwork(networkId)) return
        // 一个实例在连着时，别的实例的网络不能同时加入
        if (repository.lastActivatedNetworks().any { it.planetId != planetId || it.identityUid != identityUid }) {
            return
        }
        val config = net.sumitm.zotiera.model.NetworkConfig(
            id = networkId,
            routeViaZeroTier = false,
            dnsMode = dnsMode,
        )
        repository.insertNetworkConfig(config)
        repository.insertNetwork(
            Network(
                networkId = networkId,
                networkIdStr = networkIdStr,
                networkConfigId = networkId,
                lastActivated = true,
                planetId = planetId,
                identityUid = identityUid,
            )
        )
        // 新网络直接是「已加入」，实例跟着它走
        InstanceStore(getApplication()).setActive(planetId, identityUid)
        if (_state.value.tunnelRunning) {
            val context = getApplication<Application>()
            context.startService(
                Intent(context, ZeroTierOneService::class.java)
                    .putExtra(ZeroTierOneService.ZT1_NETWORK_ID, networkId)
            )
        }
        refresh()
    }

    fun deleteNetwork(network: Network) {
        if (_state.value.tunnelRunning && network.lastActivated) {
            bus.post(LeaveNetworkEvent(network.networkId))
        }
        repository.deleteNetwork(network)
        val joinedIds = repository.lastActivatedNetworks().map { it.networkId }.toSet()
        _state.update {
            it.copy(networks = repository.networks(), joinedNetworkIds = joinedIds)
        }
        if (joinedIds.isEmpty() && _state.value.tunnelRunning) {
            stopTunnel()
            return
        }
        refresh()
    }

    // ---- 事件 ----

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNetworkList(event: NetworkListReplyEvent) {
        // 成员资格以数据库为准；这里只是刷新列表与实时配置
        _state.update { it.copy(networks = repository.networks()) }
        requestConfigs()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onServiceRunning(event: IsServiceRunningReplyEvent) {
        _state.update { it.copy(tunnelRunning = event.isRunning) }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNodeStatus(event: NodeStatusEvent) {
        val status: NodeStatus = event.status
        val version: Version = event.clientVersion
        _state.update {
            it.copy(
                online = status.isOnline,
                nodeId = String.format("%10x", status.address),
                coreVersion = "${version.major}.${version.minor}.${version.revision}",
            )
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNodeId(event: NodeIDEvent) {
        _state.update { it.copy(nodeId = String.format("%10x", event.nodeId)) }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNodeDestroyed(event: NodeDestroyedEvent) {
        _state.update { it.copy(online = false, configs = emptyMap()) }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onConfigReply(event: VirtualNetworkConfigReplyEvent) {
        val config = event.virtualNetworkConfig ?: return
        _state.update { it.copy(configs = it.configs + (config.nwid to config)) }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onConfigChanged(event: VirtualNetworkConfigChangedEvent) {
        val config = event.virtualNetworkConfig ?: return
        _state.update { it.copy(configs = it.configs + (config.nwid to config)) }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPeers(event: PeerInfoReplyEvent) {
        _state.update { it.copy(peers = event.peers.toList(), peersLoaded = true) }
    }
}
