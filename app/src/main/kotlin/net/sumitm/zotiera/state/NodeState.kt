package net.sumitm.zotiera.state

import com.zerotier.sdk.NodeStatus
import com.zerotier.sdk.Peer
import com.zerotier.sdk.Version
import com.zerotier.sdk.VirtualNetworkConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** 节点状态快照：`NodeStatus` 与核心版本总是一起取，分开存会出现半新半旧。 */
data class NodeStatusPayload(val status: NodeStatus, val version: Version)

/**
 * 服务对外暴露的状态：服务写，界面读。
 *
 * 界面不再向服务发「请求一次」的事件，看到的就是服务那边的最新值。
 */
object NodeState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _status = MutableStateFlow<NodeStatusPayload?>(null)
    val status: StateFlow<NodeStatusPayload?> = _status.asStateFlow()

    private val _configs = MutableStateFlow<Map<Long, VirtualNetworkConfig>>(emptyMap())
    val configs: StateFlow<Map<Long, VirtualNetworkConfig>> = _configs.asStateFlow()

    /** 服务在节点就绪后设置；返回空表示取不到（节点还没建好或已销毁）。 */
    @Volatile
    var peersProvider: (() -> List<Peer>)? = null

    /** 有人订阅时才每 2 秒取一次，没人看就不查。 */
    val peers: StateFlow<List<Peer>> = flow {
        while (true) {
            emit(peersProvider?.invoke().orEmpty())
            delay(2000)
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(2000), emptyList())

    fun setRunning(value: Boolean) {
        _running.value = value
    }

    fun setStatus(payload: NodeStatusPayload?) {
        _status.value = payload
    }

    fun setConfigs(configs: Map<Long, VirtualNetworkConfig>) {
        _configs.value = configs
    }

    fun updateConfig(networkId: Long, config: VirtualNetworkConfig) {
        _configs.value = _configs.value + (networkId to config)
    }

    fun removeConfig(networkId: Long) {
        _configs.value = _configs.value - networkId
    }

    /** 隧道停掉：状态、配置、Peer 一起清干净，免得界面留着上一轮的残影。 */
    fun clear() {
        _running.value = false
        _status.value = null
        _configs.value = emptyMap()
        peersProvider = null
    }
}
