# 服务层重写：Kotlin + Flow + 协程（R1 + R2）

- 状态：决策已确认（2026-10-21：进程被杀后自动恢复；启动/加网络拆成显式 action；peers 订阅者驱动）
- 建立日期：2026-10-21
- 修订：v2（并入原 R2：EventBus → Flow、裸线程 → 协程）
- 基线提交：`938e7e6`
- 事实依据：
  - `find app/src/main/java -name '*.java' | xargs wc -l`：22 个文件、3059 行
  - `events/Events.kt`：27 个事件类 —— 两侧都在用的 17 个、只有单侧引用的 7 个、零引用 3 个
  - `ZeroTierOneService`：12 个 `@Subscribe`（`POSTING` 3、`BACKGROUND` 6、`ASYNC` 2、默认 1）
  - 线程：`vpnThread`（`new Thread(this, "ZeroTier Service Thread")`）、`udpThread`（`new Thread(udpCom, …)`）、
    `v4MulticastScanner` / `v6MulticastScanner`、`TunTapAdapter.receiveThread`、`ARPTable` / `NDPTable` 的 1 秒清理线程
  - `app/src/main/AndroidManifest.xml`：`.service.ZeroTierOneService`、`.service.StartupReceiver`、
    `net.sumitm.zotiera.ZotieraApplication` 按类名注册

## 为什么要合并做

原计划分两轮：R1 迁 Kotlin、R2 换事件与并发。实际是同一批方法改两遍 —— `rebuildTunnel`、12 个事件处理、
7 个线程循环都要先翻译一次、再重写一次。更麻烦的是 R1 为了让扩展函数够得着服务字段，得把 `node`、`vpnSocket`
从 `private` 提到 `internal`；而 R2 一换 Flow，这些字段本来就该收进状态对象，`internal` 白放宽一次。

合并后每段代码只改一次：**语言、事件、并发在同一批里换完**，也不再需要放宽可见性。

## 目标形态

```
UI / 磁贴 ──Intent（命令）──▶ ZeroTierOneService
UI ◀──StateFlow（状态）──── NodeState（进程内单例）
```

### 命令：Intent + `action`

命令必须能在服务没起来时生效（磁贴、页面里加网络）；Flow 需要消费者在场，靠它发命令会丢。所以命令走
`startService` + `action`：

| action | extra | 替代 |
| --- | --- | --- |
| `ACTION_START` | — | 「打开总开关」：启节点 + join 全部已加入 |
| `ACTION_STOP` | — | `StopEvent` |
| `ACTION_JOIN` | `networkId` | 现有裸 `startService`（`ZT1_NETWORK_ID`） |
| `ACTION_LEAVE` | `networkId` | `LeaveNetworkEvent` |
| `ACTION_RECONFIGURE` | — | `TunnelConfigChangedEvent` |
| `ACTION_ORBIT` / `ACTION_DEORBIT` | `worldId`、`seed` | `OrbitMoonEvent` / `RemoveMoonOrbitEvent` |
| `ACTION_REFRESH_PEERS` | — | `PeerInfoRequestEvent` |

`ServiceCommands.kt` 提供 `start(context)`、`stop(context)`、`join(context, id)` 这类构造器，UI 与磁贴都不手拼 Intent。
`onStartCommand` 按 `intent?.action` 分发；`ACTION_JOIN` 只 join 指定 id，不再隐式兼带「全部已加入」。

返回值保持 `START_STICKY`，并且 `intent == null`（系统在进程被杀后重新拉起）按 `ACTION_START` 处理：
重新 join 全部已加入并重建隧道。原来那个「声明 STICKY 但 null 就静默返回」的分支删掉，
不再留下空转的服务实例。重建隧道要重新 `establish()`，部分 ROM 上会重新弹 VPN 授权框——这是自动恢复的代价。

### 状态：`NodeState` 单例

| 属性 | 类型 | 替代 |
| --- | --- | --- |
| `running` | `StateFlow<Boolean>` | `isRunning` 静态布尔 + `IsServiceRunningRequest/ReplyEvent` |
| `status` | `StateFlow<NodeStatus?>` | `NodeStatusEvent`、`NodeIDEvent`、`NodeDestroyedEvent` |
| `configs` | `StateFlow<Map<Long, VirtualNetworkConfig>>` | `NetworkListReplyEvent`、`VirtualNetworkConfig{Request,Reply,Changed}Event` |
| `peers` | `StateFlow<List<Peer>>`（`WhileSubscribed(2000)`，有人订阅才每 2 秒查一次） | `PeerInfoReplyEvent` + 页面里的 2 秒轮询 |
| `moonOrbits` | `StateFlow<List<MoonOrbit>>` | `OrbitMoonEvent` 的入参侧 |

`ZeroTierOneService.isRunning` 保留为兼容读法（磁贴在用），内部读 `NodeState.running.value`。

### 删除的事件与理由

| 事件 | 现状 | 处理 |
| --- | --- | --- |
| `JoinNetworkEvent`、`DefaultRouteChangedEvent`、`AddMoonOrbitEvent` | 零引用 | 删 |
| `AfterJoinNetworkEvent` | 服务自己 post，无人订阅 | 删 |
| `ErrorEvent`、`VPNErrorEvent` | 服务 post，无人订阅（这些错误用户看不到） | 删，只留日志 |
| `NetworkReconfigureEvent` | 服务 post 又订阅自己 | 改成直接调用 |
| `NetworkConfigChangedByUserEvent`、`ManualDisconnectEvent` | 只订阅，没人 post | 删 |
| `RemoveMoonOrbitEvent` | 月页 post，服务没有订阅者 —— **撤轨从来没到过引擎** | 改走 `ACTION_DEORBIT`，顺带修掉 |

换完后 `events/Events.kt` 只剩 UI 要展示的快照类型，或直接清空。

### 并发：协程

| 现在 | 之后 |
| --- | --- |
| `vpnThread`（`run()` 里按 100ms / 剩余时间 `Thread.sleep` 的后台任务循环） | `serviceScope.launch { while (isActive) { … ; delay(…) } }` |
| 两个组播扫描线程（1 秒轮询） | `MulticastScanner` 内部 `while (isActive) { scan(); delay(1000) }` |
| `UdpCom implements Runnable` + `udpThread` | `UdpCom.receiveLoop()`，阻塞 `receive` 用 `runInterruptible` 包住 |
| `TunTapAdapter.receiveThread` + `Thread.sleep(10)` | 同上，`runInterruptible { input.read(buf) }` |
| `ARPTable` / `NDPTable` 的 1 秒清理线程 | 表只留数据结构与 `sweep()`，由持有方在 scope 里定时调 |
| EventBus 的 `POSTING` / `BACKGROUND` / `ASYNC` | 命令分发（主线程）+ IO 协程显式表达；JNI 回调直接写 `StateFlow`（线程安全） |

`serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`，`onDestroy` 里 `cancel()`；
`TunTapAdapter` 用自己的子 scope，随 `interrupt()` 一起取消。

## 文件拆分

服务状态收进 `NodeState` 之后，拆分靠构造参数注入，不需要放宽可见性。

### `ZeroTierOneService`：1220 行 → 4 个文件（`net.sumitm.zotiera.service`）

| 新文件 | 内容 |
| --- | --- |
| `ZeroTierOneService.kt` | 生命周期、`onStartCommand` 的命令分发、`stopSelf` 收尾、JNI 回调（`onTrace` / `onNetworkConfigurationUpdated`）→ 写 `NodeState` |
| `TunnelController.kt` | `rebuild()` / `teardown()` / `addDnsServers()` / `tunnelFingerprint()`；持有 `vpnSocket`、`virtualNetworkConfigMap`、`tunnelFingerprint`；前台通知文案也在这里 |
| `MulticastScanner.kt` | `class MulticastScanner(scope, ipv6, node: () -> Node?, networkIds: () -> LongArray)` |
| `ServiceCommands.kt` | action 常量 + Intent 构造器（UI 与磁贴共用） |

`ZeroTierBinder` / `bindCount` / `onBind` / `onUnbind` 一并删除：命令走 Intent，状态走 Flow，没人再需要 binder。

### `TunTapAdapter`：550 行 → 2 个文件

| 新文件 | 内容 |
| --- | --- |
| `TunTapAdapter.kt` | 读协程、`vpnSocket`、路由表与组播路由、`onVirtualNetworkFrame` |
| `TunTapPackets.kt` | IPv4/IPv6 报文处理与邻居应答（`handleIPv4Packet` / `handleIPv6Packet` / `isNeighbor*`） |

### 其余 20 个文件 1:1 换成 `.kt`

`util/` 9 个（`FileUtil` 无引用直接删）、`ARPTable` / `NDPTable` / `ARPEntry` / `NDPEntry` / `ARPReplyData` /
`Route` / `UdpCom` / `DataStore`、`model/type/DNSMode`、`ZotieraApplication`、`StartupReceiver`。

## 批次

每批同时完成「语言 + 并发 + 事件」，做完即终态，不再二次改。

| 批次 | 范围 | 回归项 |
| --- | --- | --- |
| P1 | `util/*` 9 个 + `DNSMode`（547 行） | 启动、加入网络、三个顶层页与四个管理页；网络卡片正常 |
| P2 | `DataStore`、`Route`、`ARPTable`、`NDPTable`、`ARPEntry`、`NDPEntry`、`ARPReplyData`（653 行），表内 1 秒清理改为 `sweep()` | 加入网络拿到地址；Peers 有数据；切换实例后节点 ID 变化 |
| P3 | 新增 `NodeState` + `ServiceCommands`；`NodeViewModel` 改读 `NodeState`；服务侧按 `ACTION_*` 处理命令并写 `NodeState`（服务此时仍是 Java） | 加/删网络、开关隧道、切身份、磁贴开关；`running` 与服务实际状态一致 |
| P4 | `UdpCom`（去掉 `Runnable`）、`TunTapAdapter` → 2 个文件 + 读协程 | 隧道上线、同网段能 ping 通、断开后没有残留线程 |
| P5 | `ZeroTierOneService` → 4 个文件；删 binder、删 EventBus 注册与 12 个订阅、线程改协程 | 完整链路：加入 → 上线 → 加第二个网络 → 切身份 → 离开 → 关闭；通知文案；撤轨后引擎里 `LEAF` 消失 |
| P6 | `ZotieraApplication`、`StartupReceiver`；删 `app/src/main/java`；`app/build.gradle` 去掉 `eventbus` | 冷启动；`am broadcast -a android.intent.action.BOOT_COMPLETED -p …` 后按开关自启 |

P3 是唯一「新旧并存」的一批：命令先切成 Intent、UI 先切到 `NodeState`，服务内部暂时留着 EventBus 骨架当过渡；
P5 把骨架删干净。这样每批都能单独跑、单独回滚。

## 行为变化（需确认）

| 变化 | 影响 | 处理 |
| --- | --- | --- |
| 停止从同步变异步 | 现在 `StopEvent` 是 `POSTING`，`post` 返回时已停完；改成命令后要等 `onStartCommand` | UI 只看 `NodeState.running`，不再依赖「已停完」 |
| 去掉绑定 | 以前绑定期间 `stopService` 无效，磁贴得「先 post 再 stop」 | 服务收到 `ACTION_STOP` 后自己 `stopSelf()`；磁贴只发命令 |
| 请求/应答变状态订阅 | 少了 `refresh()` 往返，UI 始终看到最新值 | `refresh()` 只重读数据库 |
| peers 轮询位置 | 从 `PeersScreen` 的定时器挪到 `NodeState` 的 `WhileSubscribed` | 界面不看就不查；刷新节奏由 `NodeState` 统一 |
| 撤轨走命令 | 以前根本没到引擎 | 顺带修好，P5 回归项验证 |
| 错误事件删除 | 用户本就看不到这些错误 | 只留日志，不新增提示 |

## 不做

- 不改 `VpnService.Builder` 的配置顺序、路由与 DNS 规则。
- 不改命令之外的行为：加入网络的时机、隧道重建判据（指纹比较）照旧。
- 不动 Room，不动 UI 结构（除 `NodeViewModel` 的取值方式与月页的命令调用）。
- 不引入 DI 框架：`NodeState` 是普通 object，`TunnelController` 用构造参数。

## 风险

| 风险 | 处理 |
| --- | --- |
| 命令时序变化引出回归（尤其停止、切身份） | P3 单独成批并跑完整链路；`running` 以服务上报为准 |
| 阻塞读不可取消，取消后线程还在 | `runInterruptible` + `socket.close()` 双保险；P4 验证断开后无残留线程 |
| `StateFlow` 初值与首帧不一致（服务还没起） | 初值取数据库与服务静态状态；界面已有「未连接」态兜底 |
| 一次改太多、定位困难 | 六批独立提交，P3 保留旧骨架直到 P5 |
| JNI 回调线程直接写 `StateFlow` | `MutableStateFlow` 线程安全，不切 `MAIN`；只有通知涉及 Android 服务对象，与线程无关 |
