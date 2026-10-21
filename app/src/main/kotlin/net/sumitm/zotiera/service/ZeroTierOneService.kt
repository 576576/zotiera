package net.sumitm.zotiera.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zerotier.sdk.Event
import com.zerotier.sdk.EventListener
import com.zerotier.sdk.Node
import com.zerotier.sdk.ResultCode
import com.zerotier.sdk.VirtualNetworkConfig
import com.zerotier.sdk.VirtualNetworkConfigListener
import com.zerotier.sdk.VirtualNetworkConfigOperation
import com.zerotier.sdk.VirtualNetworkStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.sumitm.zotiera.R
import net.sumitm.zotiera.ZotieraApplication
import net.sumitm.zotiera.identity.InstanceStore
import net.sumitm.zotiera.model.Network
import net.sumitm.zotiera.model.type.DNSMode
import net.sumitm.zotiera.state.NodeState
import net.sumitm.zotiera.state.NodeStatusPayload
import net.sumitm.zotiera.ui.MainActivity
import net.sumitm.zotiera.util.AppPrefs
import net.sumitm.zotiera.util.Constants
import net.sumitm.zotiera.util.DatabaseUtils
import net.sumitm.zotiera.util.InetAddressUtils
import net.sumitm.zotiera.util.NetworkInfoUtils
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.locks.ReentrantReadWriteLock

class ZeroTierOneService : VpnService(), EventListener, VirtualNetworkConfigListener {

    /** 按实例创建，切换实例时重建。 */
    private var dataStore: DataStore? = null

    /** 当前实例的 planet 与身份，节点初始化时定下来，写节点 ID 时要用。 */
    private var instancePlanetId = 0L
    private var instanceIdentityUid = 0L

    private val virtualNetworkConfigMap = HashMap<Long, VirtualNetworkConfig>()

    private var disableIPv6 = false
    private var startId = -1
    private val joinedNetworkIds: MutableSet<Long> =
        Collections.synchronizedSet(LinkedHashSet())

    @Volatile
    private var tunnelFingerprint = ""
    private var routeAllTraffic = false

    @Volatile
    private var nextBackgroundTaskDeadline = 0L

    @Volatile
    private var node: Node? = null
    private var notificationManager: NotificationManager? = null
    private var tunTapAdapter: TunTapAdapter? = null
    private var udpCom: UdpCom? = null

    private var input: FileInputStream? = null
    private var output: FileOutputStream? = null
    private var svrSocket: DatagramSocket? = null
    private var vpnSocket: ParcelFileDescriptor? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnJob: Job? = null
    private var udpJob: Job? = null

    // ---- 命令 ----

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        if (startId == 3) {
            Log.i(TAG, "Authorizing VPN")
            return START_NOT_STICKY
        }
        this.startId = startId
        // 系统在进程被杀后重新拉起时 intent 为空，按「启动」处理，把隧道恢复起来
        val action = intent?.action ?: ServiceCommands.ACTION_START

        if (action == ServiceCommands.ACTION_STOP) {
            stopZeroTier()
            return START_NOT_STICKY
        }

        val requested = when (action) {
            ServiceCommands.ACTION_JOIN -> {
                val id = intent?.getLongExtra(ServiceCommands.EXTRA_NETWORK_ID, 0L) ?: 0L
                if (id == 0L) emptySet() else setOf(id)
            }

            ServiceCommands.ACTION_START -> lastActivatedNetworkIds()

            // 其余命令不参与「加入哪些网络」，节点没起来时直接忽略
            else -> emptySet()
        }

        if (action == ServiceCommands.ACTION_START || action == ServiceCommands.ACTION_JOIN) {
            if (!startNode(requested)) {
                return START_NOT_STICKY
            }
            joinedNetworkIds.addAll(requested)
            requested.forEach { joinNetwork(it) }
        }

        when (action) {
            ServiceCommands.ACTION_LEAVE -> {
                val id = intent?.getLongExtra(ServiceCommands.EXTRA_NETWORK_ID, 0L) ?: 0L
                if (id != 0L) leaveNetwork(id)
            }

            ServiceCommands.ACTION_RECONFIGURE -> {
                routeAllTraffic = prefs().getBoolean(Constants.PREF_ROUTE_ALL_TRAFFIC, false)
                rebuildTunnel()
            }

            ServiceCommands.ACTION_ORBIT -> {
                val worldId = intent?.getLongExtra(ServiceCommands.EXTRA_WORLD_ID, 0L) ?: 0L
                val seed = intent?.getLongExtra(ServiceCommands.EXTRA_SEED, 0L) ?: 0L
                if (worldId != 0L) orbitNetwork(worldId, seed)
            }

            ServiceCommands.ACTION_DEORBIT -> {
                val worldId = intent?.getLongExtra(ServiceCommands.EXTRA_WORLD_ID, 0L) ?: 0L
                if (worldId != 0L) deorbitNetwork(worldId)
            }

            else -> Unit
        }
        return START_STICKY
    }

    private fun prefs() = AppPrefs.get(this)

    private fun lastActivatedNetworkIds(): Set<Long> {
        DatabaseUtils.readLock.lock()
        return try {
            repository().lastActivatedNetworks().map { it.networkId }.toSet()
        } finally {
            DatabaseUtils.readLock.unlock()
        }
    }

    /**
     * 起节点：本地 UDP socket、Node、UDP 收包与后台任务循环。
     *
     * 已经起过就直接复用；[requested] 只用于判断「有没有网络可加」。
     */
    private fun startNode(requested: Set<Long>): Boolean {
        NodeState.setRunning(true)
        ZeroTierTileService.refreshTile(this)

        if (requested.isEmpty() && joinedNetworkIds.isEmpty()) {
            Log.e(TAG, "No network to join")
            abortStart()
            return false
        }

        val preferences = prefs()
        val useCellularData = preferences.getBoolean(Constants.PREF_NETWORK_USE_CELLULAR_DATA, false)
        disableIPv6 = preferences.getBoolean(Constants.PREF_NETWORK_DISABLE_IPV6, false)
        routeAllTraffic = preferences.getBoolean(Constants.PREF_ROUTE_ALL_TRAFFIC, false)
        when (NetworkInfoUtils.getNetworkInfoCurrentConnection(this)) {
            NetworkInfoUtils.CurrentConnection.CONNECTION_NONE -> {
                Toast.makeText(this, R.string.toast_no_network, Toast.LENGTH_SHORT).show()
                abortStart()
                return false
            }

            NetworkInfoUtils.CurrentConnection.CONNECTION_MOBILE -> if (!useCellularData) {
                Toast.makeText(this, R.string.toast_mobile_data, Toast.LENGTH_LONG).show()
                abortStart()
                return false
            }

            else -> Unit
        }

        try {
            if (svrSocket == null) {
                svrSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    soTimeout = 1000
                    bind(InetSocketAddress(9994))
                }
            }
            if (!protect(svrSocket)) {
                Log.e(TAG, "Error protecting UDP socket from feedback loop.")
            }

            if (node == null) {
                val udp = UdpCom(this, svrSocket)
                val adapter = TunTapAdapter(this)
                udpCom = udp
                tunTapAdapter = adapter

                val store = InstanceStore(this)
                instancePlanetId = store.activePlanetId
                instanceIdentityUid = store.activeIdentityUid
                // 没有身份（或记的那个已经不存在）时回落到默认身份
                if (repository().identity(instanceIdentityUid) == null) {
                    val uid = repository().ensureDefaultIdentity()
                    if (uid != null) {
                        instanceIdentityUid = uid
                        store.setActive(instancePlanetId, uid)
                    }
                }
                store.ensure(instancePlanetId, instanceIdentityUid)
                val data = DataStore(
                    store.instanceDir(instancePlanetId, instanceIdentityUid),
                    store.planetOverride(instancePlanetId),
                )
                dataStore = data

                val newNode = Node(System.currentTimeMillis())
                node = newNode
                val result = newNode.init(data, data, udp, this, adapter, this, null)
                if (result != ResultCode.RESULT_OK) {
                    Log.e(TAG, "Error starting ZT1 Node: $result")
                    abortStart()
                    return false
                }
                Log.d(TAG, "ZeroTierOne Node Initialized")
                publishStatus()

                // 持久化当前节点信息
                val address = newNode.address()
                DatabaseUtils.writeLock.lock()
                try {
                    repository().setNodeId(instanceIdentityUid, address)
                    store.syncUp(instancePlanetId, instanceIdentityUid)
                } finally {
                    DatabaseUtils.writeLock.unlock()
                }

                udp.setNode(newNode)
                adapter.setNode(newNode)
                NodeState.peersProvider = { node?.peers()?.toList().orEmpty() }

                udpJob = scope.launch { udp.receiveLoop() }
            }

            startVpnLoop()
        } catch (e: Exception) {
            Log.e(TAG, e.toString(), e)
            abortStart()
            return false
        }
        return true
    }

    /** 后台任务循环：在截止期前反复跑 `processBackgroundTasks`。 */
    private fun startVpnLoop() {
        if (vpnJob?.isActive == true) {
            return
        }
        vpnJob = scope.launch {
            Log.d(TAG, "ZeroTierOne Service Started")
            while (isActive) {
                try {
                    val current = node ?: break
                    val deadline = nextBackgroundTaskDeadline
                    val now = System.currentTimeMillis()
                    if (deadline <= now) {
                        val newDeadline = longArrayOf(0)
                        val result = current.processBackgroundTasks(now, newDeadline)
                        nextBackgroundTaskDeadline = newDeadline[0]
                        if (result != ResultCode.RESULT_OK) {
                            Log.e(TAG, "Error on processBackgroundTasks: $result")
                            shutdown()
                        }
                        delay(100)
                    } else {
                        delay(deadline - now)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, e.toString(), e)
                }
            }
            Log.d(TAG, "ZeroTierOne Service Ended")
        }
    }

    /** 启动条件不满足：把开关与磁贴退回未连接，再停掉自己。 */
    private fun abortStart() {
        NodeState.setRunning(false)
        ZeroTierTileService.refreshTile(this)
        stopSelf(startId)
    }

    fun stopZeroTier() {
        NodeState.clear()
        ZeroTierTileService.refreshTile(this)
        joinedNetworkIds.clear()
        tunnelFingerprint = ""
        svrSocket?.close()
        svrSocket = null
        udpJob?.cancel()
        udpJob = null
        tunTapAdapter?.let { adapter ->
            if (adapter.isRunning()) {
                adapter.interrupt()
            }
        }
        tunTapAdapter = null
        vpnJob?.cancel()
        vpnJob = null
        closeVpnSocket()
        node?.let {
            it.close()
            node = null
        }
        notificationManager?.cancel(ZT_NOTIFICATION_TAG)
        if (!stopSelfResult(startId)) {
            Log.e(TAG, "stopSelfResult() failed!")
        }
    }

    override fun onDestroy() {
        try {
            stopZeroTier()
            closeVpnSocket()
            stopSelf(startId)
        } catch (e: Exception) {
            Log.e(TAG, e.toString(), e)
        } finally {
            scope.cancel()
            super.onDestroy()
        }
    }

    override fun onRevoke() {
        stopZeroTier()
        closeVpnSocket()
        stopSelf(startId)
        super.onRevoke()
    }

    private fun closeVpnSocket() {
        vpnSocket?.let {
            runCatching { it.close() }.onFailure { e -> Log.e(TAG, "Error closing VPN socket: $e", e) }
        }
        vpnSocket = null
        runCatching { input?.close() }
        runCatching { output?.close() }
        input = null
        output = null
    }

    fun shutdown() {
        stopZeroTier()
        closeVpnSocket()
        stopSelf(startId)
    }

    // ---- 网络 ----

    /** 加入 ZT 网络。 */
    fun joinNetwork(networkId: Long) {
        val current = node
        if (current == null) {
            Log.e(TAG, "Can't join network if ZeroTier isn't running")
            return
        }
        val result = current.join(networkId)
        if (result != ResultCode.RESULT_OK) {
            Log.e(TAG, "Join network failed: $result")
            return
        }
        joinedNetworkIds.add(networkId)
        Log.i(TAG, "Joined network ${java.lang.Long.toHexString(networkId)} (total ${joinedNetworkIds.size})")
    }

    /** 离开 ZT 网络。 */
    fun leaveNetwork(networkId: Long) {
        val current = node
        if (current == null) {
            Log.e(TAG, "Can't leave network if ZeroTier isn't running")
            return
        }
        val result = current.leave(networkId)
        if (result != ResultCode.RESULT_OK) {
            Log.e(TAG, "Leave network failed: $result")
            return
        }
        joinedNetworkIds.remove(networkId)
        clearVirtualNetworkConfig(networkId)
        if (joinedNetworkIds.isEmpty()) {
            stopZeroTier()
        } else {
            rebuildTunnel()
        }
    }

    /** 当前网络入轨 Moon。 */
    fun orbitNetwork(moonWorldId: Long, moonSeed: Long) {
        val current = node
        if (current == null) {
            Log.e(TAG, "Can't orbit network if ZeroTier isn't running")
            return
        }
        Log.i(TAG, "Orbiting moon: ${java.lang.Long.toHexString(moonWorldId)}")
        val result = current.orbit(moonWorldId, moonSeed)
        if (result != ResultCode.RESULT_OK) {
            Log.e(TAG, "Failed to orbit ${java.lang.Long.toHexString(moonWorldId)}: $result")
        }
    }

    /** 撤掉 Moon 轨道。 */
    fun deorbitNetwork(moonWorldId: Long) {
        val current = node ?: return
        Log.i(TAG, "Deorbiting moon: ${java.lang.Long.toHexString(moonWorldId)}")
        val result = current.deorbit(moonWorldId)
        if (result != ResultCode.RESULT_OK) {
            Log.e(TAG, "Failed to deorbit ${java.lang.Long.toHexString(moonWorldId)}: $result")
        }
    }

    fun getVirtualNetworkConfig(networkId: Long): VirtualNetworkConfig? =
        synchronized(virtualNetworkConfigMap) { virtualNetworkConfigMap[networkId] }

    fun setVirtualNetworkConfig(
        networkId: Long,
        config: VirtualNetworkConfig,
    ): VirtualNetworkConfig? {
        val previous = synchronized(virtualNetworkConfigMap) {
            virtualNetworkConfigMap.put(networkId, config)
        }
        NodeState.updateConfig(networkId, config)
        return previous
    }

    fun clearVirtualNetworkConfig(networkId: Long): VirtualNetworkConfig? {
        val removed = synchronized(virtualNetworkConfigMap) {
            virtualNetworkConfigMap.remove(networkId)
        }
        NodeState.removeConfig(networkId)
        return removed
    }

    /** 已加入的网络 id 快照。适配器用它决定地址归属。 */
    fun getJoinedNetworkIds(): LongArray = synchronized(joinedNetworkIds) {
        joinedNetworkIds.toLongArray()
    }

    fun setNextBackgroundTaskDeadline(deadline: Long) {
        nextBackgroundTaskDeadline = deadline
    }

    private fun publishStatus() {
        val current = node ?: return
        if (!current.isInited) {
            return
        }
        NodeState.setStatus(NodeStatusPayload(current.status(), current.version))
    }

    private fun repository() = (application as ZotieraApplication).repository

    // ---- JNI 回调 ----

    override fun onEvent(event: Event) {
        Log.d(TAG, "Event: $event")
        publishStatus()
    }

    override fun onTrace(message: String) {
        Log.d(TAG, "Trace: $message")
    }

    override fun onNetworkConfigurationUpdated(
        networkId: Long,
        operation: VirtualNetworkConfigOperation,
        config: VirtualNetworkConfig,
    ): Int {
        Log.i(TAG, "Virtual Network Config Operation: $operation")
        DatabaseUtils.writeLock.lock()
        try {
            val network = repository().network(networkId)
            if (network == null) {
                throw IllegalStateException("Database is inconsistent")
            }
            when (operation) {
                VirtualNetworkConfigOperation.VIRTUAL_NETWORK_CONFIG_OPERATION_UP ->
                    Log.d(
                        TAG,
                        "Network Type: ${config.type} Network Status: ${config.status} Network Name: ${config.name}",
                    )

                VirtualNetworkConfigOperation.VIRTUAL_NETWORK_CONFIG_OPERATION_CONFIG_UPDATE -> {
                    Log.i(TAG, "Network Config Update!")
                    val changed = setVirtualNetworkConfigAndUpdateDatabase(network, config)
                    val ok = config.status == VirtualNetworkStatus.NETWORK_STATUS_OK
                    val rebuilt = changed && rebuildTunnel()
                    if (rebuilt || !ok) {
                        NodeState.updateConfig(networkId, config)
                    }
                }

                VirtualNetworkConfigOperation.VIRTUAL_NETWORK_CONFIG_OPERATION_DOWN,
                VirtualNetworkConfigOperation.VIRTUAL_NETWORK_CONFIG_OPERATION_DESTROY -> {
                    Log.d(TAG, "Network Down!")
                    clearVirtualNetworkConfig(networkId)
                    rebuildTunnel()
                }

                else -> Unit
            }
            return 0
        } finally {
            DatabaseUtils.writeLock.unlock()
        }
    }

    private fun setVirtualNetworkConfigAndUpdateDatabase(
        network: Network,
        config: VirtualNetworkConfig,
    ): Boolean {
        val lock = DatabaseUtils.writeLock
        if (lock is ReentrantReadWriteLock.WriteLock && !lock.isHeldByCurrentThread) {
            throw IllegalStateException("DatabaseUtils.writeLock not held")
        }
        val previous = getVirtualNetworkConfig(network.networkId)
        setVirtualNetworkConfig(network.networkId, config)
        config.name?.takeIf { it.isNotEmpty() }?.let { network.networkName = it }
        repository().updateNetwork(network)
        return config != previous
    }

    // ---- 隧道 ----

    /** 拆掉当前隧道：TUN 线程、VPN socket 与路由表。 */
    private fun teardownTunnel() {
        tunTapAdapter?.let { adapter ->
            if (adapter.isRunning()) {
                adapter.interrupt()
            }
            adapter.clearRouteMap()
        }
        closeVpnSocket()
    }

    /**
     * 配置指纹：地址、路由、DNS、MTU 任何一个变化才重建隧道。
     *
     * 不做这层判断的话，每次 CONFIG_UPDATE 都会拆一次隧道，连接会周期抖动。
     */
    private fun tunnelFingerprint(configs: Map<Long, VirtualNetworkConfig>): String {
        val builder = StringBuilder()
        for (id in getJoinedNetworkIds()) {
            builder.append(id).append(';')
        }
        builder.append("routeAll=").append(routeAllTraffic)
            .append(" disableV6=").append(disableIPv6)
        try {
            for ((id, config) in configs) {
                val dbNetwork = repository().network(id)
                builder.append(id).append('#')
                    .append(dbNetwork?.networkConfig?.dnsMode ?: "?")
                    .append('=')
                    .append(config.toString())
                    .append('|')
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot compute tunnel fingerprint", e)
        }
        return Integer.toHexString(builder.toString().hashCode()) + ":" + builder.length
    }

    /**
     * 按所有已加入网络重建隧道。
     *
     * 每次都处理全部网络：只处理一个的话，加第二个网络会把第一个的地址和路由冲掉。
     */
    private fun rebuildTunnel(): Boolean {
        val adapter = tunTapAdapter ?: return false
        if (node == null) {
            return false
        }
        val repository = repository()

        // 已加入且已经拿到配置的网络
        val configs = LinkedHashMap<Long, VirtualNetworkConfig>()
        for (id in getJoinedNetworkIds()) {
            getVirtualNetworkConfig(id)?.let { configs[id] = it }
        }

        if (configs.values.none { it.assignedAddresses.isNotEmpty() }) {
            // 全部网络都还没分配地址（未授权或还在配置中），先不建隧道
            if (vpnSocket != null) {
                teardownTunnel()
                tunnelFingerprint = ""
            }
            Log.w(TAG, "No assigned address yet, skip VPN setup")
            return false
        }

        val fingerprint = tunnelFingerprint(configs)
        if (fingerprint == tunnelFingerprint && vpnSocket != null) {
            return false
        }

        teardownTunnel()

        Log.i(TAG, "Configuring VpnService.Builder for ${configs.size} network(s)")
        val builder = Builder()
        var mtu = Int.MAX_VALUE

        try {
            val v4Loopback = InetAddress.getByName("0.0.0.0")
            val v6Loopback = InetAddress.getByName("::")

            for ((nwid, config) in configs) {
                val dbNetwork = repository.network(nwid)

                // 1) 该网络分配给本机的地址
                for (vpnAddress in config.assignedAddresses) {
                    val address = vpnAddress.address
                    val prefix = vpnAddress.port
                    if (disableIPv6 && address is Inet6Address) {
                        continue
                    }
                    val route = InetAddressUtils.addressToRoute(address, prefix)
                    if (route == null) {
                        Log.e(TAG, "NULL route calculated for $address")
                        continue
                    }
                    val rawAddress = address.address
                    val multicastGroup: Long
                    val multicastAdi: Long
                    if (rawAddress.size == 4) {
                        multicastGroup = InetAddressUtils.BROADCAST_MAC_ADDRESS
                        multicastAdi = ByteBuffer.wrap(rawAddress).int.toLong()
                    } else {
                        multicastGroup = ByteBuffer.wrap(
                            byteArrayOf(0, 0, 0x33, 0x33, 0xFF.toByte(), rawAddress[13], rawAddress[14], rawAddress[15]),
                        ).long
                        multicastAdi = 0
                    }
                    node?.multicastSubscribe(nwid, multicastGroup, multicastAdi)?.let { result ->
                        if (result != ResultCode.RESULT_OK) {
                            Log.e(TAG, "Error joining multicast group on ${java.lang.Long.toHexString(nwid)}")
                        }
                    }
                    builder.addAddress(address, prefix)
                    builder.addRoute(route, prefix)
                    adapter.addRouteAndNetwork(Route(route, prefix), nwid)
                }

                // 2) 该网络下发的路由
                for (routeConfig in config.routes) {
                    val target = routeConfig.target
                    val via = routeConfig.via
                    val targetAddress = target.address
                    val targetPort = target.port
                    val viaAddress = InetAddressUtils.addressToRoute(targetAddress, targetPort)
                    val isIPv6Route = targetAddress is Inet6Address || viaAddress is Inet6Address
                    if (disableIPv6 && isIPv6Route) {
                        continue
                    }
                    val shouldRouteToZeroTier = viaAddress != null &&
                        (routeAllTraffic || (viaAddress != v4Loopback && viaAddress != v6Loopback))
                    if (!shouldRouteToZeroTier) {
                        continue
                    }
                    builder.addRoute(viaAddress, targetPort)
                    val route = Route(viaAddress, targetPort)
                    via?.let { route.gateway = it.address }
                    adapter.addRouteAndNetwork(route, nwid)
                }

                // 3) 该网络的 DNS
                if (dbNetwork != null) {
                    addDNSServers(builder, dbNetwork)
                }

                // 4) MTU 取所有网络的最小值
                if (config.mtu > 0) {
                    mtu = minOf(mtu, config.mtu)
                }
            }
            builder.addRoute(InetAddress.getByName("224.0.0.0"), 4)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot configure VPN", e)
            return false
        }

        if (mtu == Int.MAX_VALUE) {
            mtu = 2800
        }
        Log.i(TAG, "MTU Set: $mtu")
        builder.setMtu(mtu)
        builder.setMetered(false)
        builder.setSession(Constants.VPN_SESSION_NAME)

        // 只有没开全局全隧道时才让部分应用绕开 VPN
        if (!routeAllTraffic) {
            for (app in DISALLOWED_APPS) {
                runCatching { builder.addDisallowedApplication(app) }
                    .onFailure { Log.e(TAG, "Cannot disallow app", it) }
            }
        }

        val socket = try {
            builder.establish()
        } catch (e: IllegalArgumentException) {
            // 配置在构建过程中失效（例如地址被回收）时不应打断事件分发
            Log.e(TAG, "Cannot establish VPN: ${e.message}", e)
            return false
        }
        if (socket == null) {
            return false
        }

        vpnSocket = socket
        val fileInput = FileInputStream(socket.fileDescriptor)
        val fileOutput = FileOutputStream(socket.fileDescriptor)
        input = fileInput
        output = fileOutput
        adapter.setVpnSocket(socket)
        adapter.setFileStreams(fileInput, fileOutput)
        adapter.startThreads()
        tunnelFingerprint = fingerprint

        updateConnectedNotification(configs)
        Log.i(TAG, "ZeroTier One Connected")
        return true
    }

    private fun updateConnectedNotification(configs: Map<Long, VirtualNetworkConfig>) {
        val manager = notificationManager
            ?: (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).also {
                notificationManager = it
            }

        val channel = NotificationChannel(
            Constants.CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.channel_description)
        }
        manager.createNotificationChannel(channel)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (configs.size == 1) {
            getString(
                R.string.notification_text_connected,
                java.lang.Long.toHexString(configs.keys.first()),
            )
        } else {
            getString(R.string.notification_text_connected_multi, configs.size)
        }
        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ID)
            .setPriority(1)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_stat_zerotier)
            .setContentTitle(getString(R.string.notification_title_connected))
            .setContentText(text)
            .setColor(ContextCompat.getColor(applicationContext, R.color.zerotier_orange))
            .setContentIntent(contentIntent)
            .build()
        manager.notify(ZT_NOTIFICATION_TAG, notification)
    }

    private fun addDNSServers(builder: Builder, network: Network) {
        val networkConfig = network.networkConfig ?: return
        val config = getVirtualNetworkConfig(network.networkId) ?: return
        when (DNSMode.fromInt(networkConfig.dnsMode)) {
            DNSMode.NETWORK_DNS -> {
                val dns = config.dns ?: return
                builder.addSearchDomain(dns.domain)
                for (server in dns.servers) {
                    val address = server.address
                    if (address is Inet4Address) {
                        builder.addDnsServer(address)
                    } else if (address is Inet6Address && !disableIPv6) {
                        builder.addDnsServer(address)
                    }
                }
            }

            DNSMode.CUSTOM_DNS -> {
                for (dnsServer in networkConfig.dnsServers) {
                    try {
                        val address = InetAddress.getByName(dnsServer.nameserver)
                        if (address is Inet4Address) {
                            builder.addDnsServer(address)
                        } else if (address is Inet6Address && !disableIPv6) {
                            builder.addDnsServer(address)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception parsing DNS server: $e", e)
                    }
                }
            }

            else -> Unit
        }
    }

    private companion object {
        const val TAG = "ZT1_Service"
        const val ZT_NOTIFICATION_TAG = 5919812
        val DISALLOWED_APPS = arrayOf("com.android.vending")
    }
}
