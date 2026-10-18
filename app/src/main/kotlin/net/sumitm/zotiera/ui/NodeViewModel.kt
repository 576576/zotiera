package net.sumitm.zotiera.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import com.zerotier.sdk.NodeStatus
import com.zerotier.sdk.Peer
import com.zerotier.sdk.Version
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.sumitm.zotiera.ZotieraApplication
import net.sumitm.zotiera.data.ZotieraRepository
import net.sumitm.zotiera.events.IsServiceRunningRequestEvent
import net.sumitm.zotiera.events.NetworkListReplyEvent
import net.sumitm.zotiera.events.NetworkListRequestEvent
import net.sumitm.zotiera.events.NodeDestroyedEvent
import net.sumitm.zotiera.events.NodeIDEvent
import net.sumitm.zotiera.events.NodeStatusEvent
import net.sumitm.zotiera.events.NodeStatusRequestEvent
import net.sumitm.zotiera.events.PeerInfoReplyEvent
import net.sumitm.zotiera.events.PeerInfoRequestEvent
import net.sumitm.zotiera.events.StopEvent
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
    val clientVersion: String = "",
    val connectNetworkId: Long? = null,
    val peers: List<Peer> = emptyList(),
    val peersLoaded: Boolean = false,
)

/**
 * UI 与 ZeroTierOneService 之间的唯一通道。
 *
 * 服务仍然通过 EventBus 汇报状态（Java 侧尚未改完），这里把事件收敛成 StateFlow，
 * 新代码只读 StateFlow，不再直接注册 EventBus。
 */
class NodeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: ZotieraRepository =
        (app as ZotieraApplication).getRepository()

    private val bus = EventBus.getDefault()
    private val _state = MutableStateFlow(NodeUiState(networks = repository.networks()))
    val state: StateFlow<NodeUiState> = _state.asStateFlow()

    private var service: ZeroTierOneService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
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
        _state.update { it.copy(networks = repository.networks()) }
        bus.post(NetworkListRequestEvent())
        bus.post(NodeStatusRequestEvent())
        bus.post(IsServiceRunningRequestEvent())
    }

    fun requestPeers() {
        bus.post(PeerInfoRequestEvent())
    }

    // ---- 服务控制 ----

    fun startService(networkId: Long) {
        val context = getApplication<Application>()
        markActive(networkId)
        runCatching { context.bindService(Intent(context, ZeroTierOneService::class.java), connection, Context.BIND_AUTO_CREATE) }
        context.startService(
            Intent(context, ZeroTierOneService::class.java)
                .putExtra(ZeroTierOneService.ZT1_NETWORK_ID, networkId)
        )
        _state.update { it.copy(connectNetworkId = networkId) }
        refresh()
    }

    fun stopService() {
        val context = getApplication<Application>()
        runCatching { service?.stopZeroTier() }
        bus.post(StopEvent())
        context.stopService(Intent(context, ZeroTierOneService::class.java))
        runCatching { context.unbindService(connection) }
        service = null
        _state.update { it.copy(connectNetworkId = null, online = false) }
        refresh()
    }

    // ---- 网络增删 ----

    fun joinNetwork(networkId: Long, networkIdStr: String, defaultRoute: Boolean, dnsMode: Int) {
        if (repository.hasNetwork(networkId)) return
        val config = net.sumitm.zotiera.model.NetworkConfig(
            id = networkId,
            routeViaZeroTier = defaultRoute,
            dnsMode = dnsMode,
        )
        repository.insertNetworkConfig(config)
        repository.insertNetwork(
            Network(
                networkId = networkId,
                networkIdStr = networkIdStr,
                networkConfigId = networkId,
            )
        )
        refresh()
    }

    fun deleteNetwork(network: Network) {
        repository.deleteNetwork(network)
        if (_state.value.connectNetworkId == network.networkId) {
            stopService()
        }
        refresh()
    }

    private fun markActive(networkId: Long) {
        repository.networks().forEach { network ->
            val active = network.networkId == networkId
            if (network.lastActivated != active) {
                network.lastActivated = active
                repository.updateNetwork(network)
            }
        }
    }

    // ---- 事件 ----

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNetworkList(event: NetworkListReplyEvent) {
        val networks = event.networkList
        val connecting = networks.firstOrNull()?.nwid
        _state.update {
            it.copy(
                networks = repository.networks(),
                connectNetworkId = connecting ?: it.connectNetworkId,
            )
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNodeStatus(event: NodeStatusEvent) {
        val status: NodeStatus = event.status
        val version: Version = event.clientVersion
        _state.update {
            it.copy(
                online = status.isOnline,
                nodeId = String.format("%10x", status.address),
                clientVersion = "${version.major}.${version.minor}.${version.revision}",
            )
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNodeId(event: NodeIDEvent) {
        _state.update { it.copy(nodeId = String.format("%10x", event.nodeId)) }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNodeDestroyed(event: NodeDestroyedEvent) {
        _state.update { it.copy(online = false) }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPeers(event: PeerInfoReplyEvent) {
        _state.update { it.copy(peers = event.peers.toList(), peersLoaded = true) }
    }
}
