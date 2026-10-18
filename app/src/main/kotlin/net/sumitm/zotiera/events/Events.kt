package net.sumitm.zotiera.events

import com.zerotier.sdk.NodeStatus
import com.zerotier.sdk.Peer
import com.zerotier.sdk.ResultCode
import com.zerotier.sdk.Version
import com.zerotier.sdk.VirtualNetworkConfig
import net.sumitm.zotiera.model.MoonOrbit
import net.sumitm.zotiera.model.Network

// 服务与 UI 之间的事件。迁移期仍走 EventBus，Compose 侧由 NodeViewModel 收敛成 StateFlow。

class NetworkListRequestEvent

class NetworkListReplyEvent(val networkList: Array<VirtualNetworkConfig>)

class NodeStatusRequestEvent

class NodeStatusEvent(val status: NodeStatus, val clientVersion: Version)

class NodeIDEvent(val nodeId: Long)

class NodeDestroyedEvent

class IsServiceRunningRequestEvent

class IsServiceRunningReplyEvent(val isRunning: Boolean)

class JoinNetworkEvent(val networkId: Long, val defaultRoute: Boolean)

class AfterJoinNetworkEvent

class StopEvent

class ManualDisconnectEvent

class ErrorEvent(val result: ResultCode) {
    val error: String get() = result.toString()
}

class VPNErrorEvent(val message: String)

class NetworkReconfigureEvent(
    val isChanged: Boolean,
    val network: Network,
    val virtualNetworkConfig: VirtualNetworkConfig,
)

class NetworkConfigChangedByUserEvent(val network: Network)

class DefaultRouteChangedEvent(val networkId: Long, val isDefaultRoute: Boolean)

class VirtualNetworkConfigRequestEvent(val networkId: Long)

class VirtualNetworkConfigReplyEvent(val virtualNetworkConfig: VirtualNetworkConfig)

class VirtualNetworkConfigChangedEvent(val virtualNetworkConfig: VirtualNetworkConfig)

class PeerInfoRequestEvent

class PeerInfoReplyEvent(val peers: Array<Peer>)

class AddMoonOrbitEvent(
    val moonWorldId: Long,
    val moonSeed: Long,
    val isFromFile: Boolean,
)

class RemoveMoonOrbitEvent(val moonWorldId: Long, val moonSeed: Long)

class OrbitMoonEvent(val moonOrbits: List<MoonOrbit>)
