package net.sumitm.zotiera.ui

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerotier.sdk.Peer
import com.zerotier.sdk.VirtualNetworkConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.sumitm.zotiera.ZotieraApplication
import net.sumitm.zotiera.data.ZotieraRepository
import net.sumitm.zotiera.identity.InstanceStore
import net.sumitm.zotiera.model.MoonOrbit
import net.sumitm.zotiera.model.Network
import net.sumitm.zotiera.model.NetworkConfig
import net.sumitm.zotiera.service.ServiceCommands
import net.sumitm.zotiera.state.NodeState
import net.sumitm.zotiera.util.AppPrefs
import net.sumitm.zotiera.util.Constants

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
    /** 默认身份的节点 ID（10 位小写十六进制）；密钥还没生成时为空 */
    val identityNodeId: String = "",
    /** 首次读库是否完成；没完成之前概览不画「没有网络」的红叉 */
    val loaded: Boolean = false,
)

/**
 * 界面与零网络状态之间的唯一通道。
 *
 * 三件事互不相干：隧道开关（[NodeUiState.tunnelRunning]）、网络成员资格
 * （[NodeUiState.joinedNetworkIds]，存在 `Network.lastActivated`）、详情选中（屏内局部状态）。
 *
 * 命令发给服务（[ServiceCommands]），状态从 [NodeState] 读，不再有请求-应答往返。
 * 仓储方法都是挂起的，构造时不再直接查库；`init` 里读一次，之后每次写库由各自的方法重读。
 */
class NodeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: ZotieraRepository =
        (app as ZotieraApplication).repository

    private val prefs = AppPrefs.get(app)

    private val _state = MutableStateFlow(
        NodeUiState(routeAllTraffic = prefs.getBoolean(Constants.PREF_ROUTE_ALL_TRAFFIC, false))
    )
    val state: StateFlow<NodeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            NodeState.running.collect { running ->
                _state.update { it.copy(tunnelRunning = running) }
            }
        }
        viewModelScope.launch {
            NodeState.status.collect { payload ->
                _state.update {
                    if (payload == null) {
                        it.copy(online = false, configs = emptyMap())
                    } else {
                        it.copy(
                            online = payload.status.isOnline,
                            nodeId = String.format("%10x", payload.status.address),
                            coreVersion = "${payload.version.major}.${payload.version.minor}.${payload.version.revision}",
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            NodeState.configs.collect { configs -> _state.update { it.copy(configs = configs) } }
        }
        viewModelScope.launch {
            NodeState.peers.collect { peers -> _state.update { it.copy(peers = peers) } }
        }
        viewModelScope.launch { reload() }
    }

    /** 重读数据库里的网络列表与成员资格；隧道的实际状态由服务上报。 */
    fun refresh() {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        repository.ensureSeed()
        val networks = repository.networks()
        val joinedIds = repository.lastActivatedNetworks().map { it.networkId }.toSet()
        val identityNodeId = repository.defaultIdentity()?.nodeIdString.orEmpty()
        _state.update {
            it.copy(
                networks = networks,
                joinedNetworkIds = joinedIds,
                identityNodeId = identityNodeId,
                routeAllTraffic = prefs.getBoolean(Constants.PREF_ROUTE_ALL_TRAFFIC, false),
                loaded = true,
            )
        }
    }

    // ---- 隧道 ----

    /** 打开总开关：启动服务，服务会把数据库里所有「已加入」的网络一起 join。 */
    fun startTunnel() {
        ServiceCommands.start(getApplication())
        // startService 是异步的，先把开关置为打开，等服务的实际状态回来再校正
        _state.update { it.copy(tunnelRunning = true) }
        refresh()
    }

    fun stopTunnel() {
        ServiceCommands.stop(getApplication())
        _state.update { it.copy(tunnelRunning = false, online = false, configs = emptyMap()) }
        refresh()
    }

    /**
     * 加入 / 离开一个网络。
     *
     * 只改成员资格；隧道没开时仅写库，等下次打开隧道一起 join。
     * 一个网络都不剩时把隧道一起关掉——没有成员资格的隧道没有意义。
     */
    fun setJoined(network: Network, joined: Boolean) {
        viewModelScope.launch {
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
                    ServiceCommands.join(getApplication(), network.networkId)
                } else {
                    ServiceCommands.leave(getApplication(), network.networkId)
                }
            }
            reload()
            if (_state.value.joinedNetworkIds.isEmpty() && _state.value.tunnelRunning) {
                stopTunnel()
            }
        }
    }

    /**
     * 换这条网络用的身份。
     *
     * 身份变了，正在跑的节点就不是这条网络该用的那个实例了——隧道在跑时先停掉，由用户重新连接。
     * 用过的身份记下来，换回去不再二次确认。
     */
    fun setNetworkIdentity(network: Network, uid: Long) {
        if (network.identityUid == uid) return
        viewModelScope.launch {
            val wasJoined = network.lastActivated
            network.identityUid = uid
            repository.updateNetwork(network)
            repository.markIdentityUsed(network.networkId, uid)
            if (wasJoined) {
                InstanceStore(getApplication()).setActive(network.planetId, uid)
            }
            if (_state.value.tunnelRunning) {
                stopTunnel()
            }
            reload()
        }
    }

    /** 全局「所有流量走 ZeroTier」。 */
    fun setRouteAllTraffic(enabled: Boolean) {
        prefs.edit { putBoolean(Constants.PREF_ROUTE_ALL_TRAFFIC, enabled) }
        _state.update { it.copy(routeAllTraffic = enabled) }
        ServiceCommands.reconfigure(getApplication())
    }

    fun setDnsMode(network: Network, mode: Int) {
        viewModelScope.launch {
            val config = repository.networkConfig(network.networkConfigId) ?: return@launch
            config.dnsMode = mode
            repository.insertNetworkConfig(config)
            ServiceCommands.reconfigure(getApplication())
            reload()
        }
    }

    // ---- Moon ----

    fun orbitMoon(moon: MoonOrbit) {
        ServiceCommands.orbit(getApplication(), moon.moonWorldId, moon.moonSeed)
    }

    fun removeMoonOrbit(moon: MoonOrbit) {
        ServiceCommands.deorbit(getApplication(), moon.moonWorldId, moon.moonSeed)
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
        viewModelScope.launch {
            if (repository.hasNetwork(networkId)) return@launch
            // 一个实例在连着时，别的实例的网络不能同时加入
            if (repository.lastActivatedNetworks().any { it.planetId != planetId || it.identityUid != identityUid }) {
                return@launch
            }
            val config = NetworkConfig(
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
            repository.markIdentityUsed(networkId, identityUid)
            // 新网络直接是「已加入」，实例跟着它走
            InstanceStore(getApplication()).setActive(planetId, identityUid)
            if (_state.value.tunnelRunning) {
                ServiceCommands.join(getApplication(), networkId)
            }
            reload()
        }
    }

    fun deleteNetwork(network: Network) {
        viewModelScope.launch {
            if (_state.value.tunnelRunning && network.lastActivated) {
                ServiceCommands.leave(getApplication(), network.networkId)
            }
            repository.deleteNetwork(network)
            reload()
            if (_state.value.joinedNetworkIds.isEmpty() && _state.value.tunnelRunning) {
                stopTunnel()
            }
        }
    }
}
