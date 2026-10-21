# 服务与工具层迁到 Kotlin（R1）

- 状态：草案（待确认）
- 建立日期：2026-10-21
- 基线提交：`437e1c8`
- 事实依据：
  - `find app/src/main/java -name '*.java' | xargs wc -l`：22 个文件、3059 行
  - [`plan/init-refactor.md`](init-refactor.md) 的 R1（服务与工具层迁到 Kotlin）
  - `app/src/main/AndroidManifest.xml`：`.service.ZeroTierOneService`、`.service.StartupReceiver`、
    `net.sumitm.zotiera.ZotieraApplication` 三个组件按类名注册

## 现状

| 文件 | 行数 | 职责 |
| --- | --- | --- |
| `service/ZeroTierOneService.java` | 1220 | `VpnService`：启停、隧道建立/拆除、通知、EventBus 订阅、JNI 回调转发、组播扫描线程 |
| `service/TunTapAdapter.java` | 550 | tun 包泵：读写线程、ARP/NDP 表、按网络分路由、IPv4/IPv6 报文处理 |
| `service/ARPTable.java` | 221 | IPv4 邻居缓存与应答 |
| `service/NDPTable.java` | 167 | IPv6 邻居发现缓存与应答 |
| `util/NetworkInfoUtils.java` | 109 | 网卡/组播组枚举（`tun0` 上的 join 列表） |
| `util/InetAddressUtils.java` | 103 | 地址↔路由、广播/MAC 换算 |
| `service/DataStore.java` | 96 | 核心的数据目录读写回调（`DataStoreGet/PutListener`） |
| `util/IPPacketUtils.java` | 87 | IPv4/IPv6 报头解析 |
| `util/StringUtils.java` | 79 | hex/MAC/字符串工具 |
| `service/UdpCom.java` | 78 | 核心的 UDP 发包回调（`PacketSender`） |
| `ZotieraApplication.java` | 65 | Application：Repository/偏好初始化、废弃键清理 |
| `model/type/DNSMode.java` | 45 | DNS 模式枚举 |
| `util/Constants.java` | 38 | 通道 ID、Intent action 等常量 |
| `util/FileUtil.java` | 30 | 临时文件（**当前无引用**） |
| `service/NDPEntry.java` | 28 | NDP 表项 |
| `service/ARPEntry.java` | 28 | ARP 表项 |
| `service/StartupReceiver.java` | 24 | 开机自启广播接收 |
| `util/DebugLog.java` | 23 | 用 `BuildConfig.DEBUG` 收口的日志 |
| `util/DatabaseUtils.java` | 21 | 静态读写锁 |
| `service/Route.java` | 21 | 路由条目（`belongsToRoute`） |
| `service/ARPReplyData.java` | 14 | ARP 应答数据 |
| `util/NetworkIdUtils.java` | 12 | 网络 ID hex→long |

`app/src/main/java` 之外已经全是 Kotlin；引用这些 Java 类的 Kotlin 文件有 5 个：`NodeViewModel`（服务、`isRunning`、
`ZT1_NETWORK_ID`）、`ZeroTierTileService`（`isRunning`）、`MainActivity` / `SettingsScreen` / `OverviewScreen`
（`Constants`，`OverviewScreen` 另用到 `NetworkIdUtils`）。

## 目标

- `find app/src/main/java -name '*.java' | wc -l` 为 0，空目录一并删掉。
- 行为不变：连接时序、日志内容、EventBus 事件、Intent extra 全部照旧。

## 迁移约束（先看这里）

1. **EventBus 订阅方法必须是类成员**。EventBus 3 反射扫描注册对象的类找 `@Subscribe`；Kotlin 扩展函数编译成静态方法
   （首参是接收者），扫描不到。12 个 `@Subscribe` 方法留在 `ZeroTierOneService` 的类体里。
2. **JNI 回调方法同理**：`EventListener.onEvent/onTrace`、`VirtualNetworkConfigListener.onNetworkConfigurationUpdated`、
   `VirtualNetworkFrameListener.onVirtualNetworkFrame` 是接口实现，必须留在类体内。
3. **跨文件的扩展函数只能访问 `internal` 成员**。要把隧道与扫描拆出去，就得把被它们用到的字段
   （`node`、`vpnSocket`、`tunTapAdapter`、`tunnelFingerprint`、`disableIPv6`、`routeAllTraffic` …）从 `private` 提到
   `internal`。只在本模块可见，可以接受。
4. **静态成员的调用写法不能变**：`ZeroTierOneService.isRunning`（`ZeroTierTileService`、`NodeViewModel` 在用）、
   `ZT1_NETWORK_ID` / `ZT1_USE_DEFAULT_ROUTE` 的**字符串值**是 Intent 契约，`MSG_*` 的值参与 `what` 分发。
   实现上用 `companion object` + `@JvmField` / `const val`。
5. **类名与包名不能变**：三个组件在清单里按名字注册。
6. 一批一提交；每批都要 `assemblePr` + `:app:testDebugUnitTest` + 下面写的模拟器回归项。

## 拆分方案

### ZeroTierOneService：1220 行 → 4 个文件（同包 `net.sumitm.zotiera.service`）

| 新文件 | 内容 | 原行范围 |
| --- | --- | --- |
| `ZeroTierOneService.kt` | 类本体：常量与字段、生命周期、`run`、Binder、`joinNetwork`/`leaveNetwork`/`orbitNetwork`/`getJoinedNetworkIds`、12 个 `@Subscribe`、JNI 回调 | 85–120、237–291、454–830、1181–1220 |
| `ZeroTierOneTunnel.kt` | `rebuildTunnel` / `teardownTunnel` / `addDnsServers` 作为 `internal` 扩展函数；`tunnelFingerprint(configs)` 作为顶层纯函数 | 857–1180 |
| `MulticastScanner.kt` | 组播扫描循环，v4/v6 共用一个类：`MulticastScanner(ipv6, node: () -> Node?, networkIds: () -> LongArray)`，保留 1 秒轮询、订阅差异比对、v4 的字节序反转 | 121–235 |
| `ServiceNotifications.kt` | 前台通知：通道创建、`updateConnectedNotification(configs)`、`cancelNotification()` | 1091–1123、526–528 |

`rebuildTunnel` 与 `teardownTunnel` 之间共享 `vpnSocket`、`tunnelFingerprint` 与 `virtualNetworkConfigMap`，
所以这两个必须落在同一个文件里，不能再拆。

### TunTapAdapter：550 行 → 2 个文件

| 新文件 | 内容 | 原行范围 |
| --- | --- | --- |
| `TunTapAdapter.kt` | 读写线程、`vpnSocket`、路由表与组播路由、`onVirtualNetworkFrame`、中断/join | 31–197、400–550 |
| `TunTapPackets.kt` | IPv4/IPv6 报文处理与邻居缓存应答（`handleIPv4Packet` / `handleIPv6Packet` / `isNeighborSolicitation` / `isNeighborAdvertisement`），作为 `internal` 扩展函数 | 198–399 |

### 其余 20 个文件 1:1 换成 `.kt`

不改名、不合并、不拆：`util/` 9 个、`service/` 里的 `ARPTable` / `NDPTable` / `ARPEntry` / `NDPEntry` /
`ARPReplyData` / `Route` / `UdpCom` / `DataStore`、`model/type/DNSMode`（`enum class`）、
`ZotieraApplication`、`StartupReceiver`。

`util/FileUtil.java` 当前没有引用，随迁移直接删掉；`util/DatabaseUtils.java` 只被服务用，照原样迁。

## 批次

| 批次 | 文件 | 行数 | 回归项 |
| --- | --- | --- | --- |
| R1.1 | `util/*` 9 个（`FileUtil` 直接删）+ `DNSMode` | 547 | 启动、加入网络、三个顶层页与四个管理页都能打开；网络卡片显示正常 |
| R1.2 | `DataStore`、`Route`、`ARPTable`、`NDPTable`、`ARPEntry`、`NDPEntry`、`ARPReplyData`、`UdpCom` | 653 | 加入网络拿到地址、Peers 列表有数据、切换实例后节点 ID 变化 |
| R1.3 | `TunTapAdapter` → 2 个文件 | 550 | 开隧道后节点上线（控制器侧在线）、局域网内能收发（`ping` 同网段地址） |
| R1.4 | `ZeroTierOneService` → 4 个文件 | 1220 | 完整链路：加入 → 上线 → 加第二个网络 → 切身份 → 离开 → 关闭隧道；通知文案正确 |
| R1.5 | `ZotieraApplication`、`StartupReceiver`，删 `app/src/main/java` | 89 | 冷启动正常；`adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p net.sumitm.zotiera.pr` 后服务按开关自启 |

R1.4 是唯一一批改到 `internal` 可见性的，单独成批便于回滚。

## 迁移时的写法约定

| Java | Kotlin |
| --- | --- |
| `static final long X = 1` | `companion object { const val X = 1L }`（对外仍是 `Class.X`） |
| `static volatile boolean isRunning` | `companion object { @JvmField @Volatile var isRunning = false }` |
| `implements Runnable` + `run()` | 保留 `Runnable` 接口，`override fun run()` |
| 匿名 `new Thread() { run() }` | `object : Thread() { override fun run() { … } }`，或按上面拆成类 |
| `private void foo()` 但被扩展函数用 | `internal fun foo()` |
| `synchronized` | `@Synchronized`（实例方法）或 `synchronized(obj) { }` |
| `Long` 装箱的 Map key | 保持 `Map<Long, …>`，不要用 `MutableMap<Long, …>` 的原始类型假设 |
| platform type（如 `getVirtualNetworkConfig()`） | 先按 Java 的实际可能返回 `null` 标 `?`；确实不会为空的用 `!!`，不顺手改成 `?: return` |
| `var`（Java 10 局部推断） | `val` / `var` 按是否重赋值选 |

`BuildConfig.DEBUG` 收口的 `DebugLog` 保持语义：release 里不输出。

## 不做

- 不动 EventBus（`R2` 才换 Flow）。R1 只保证 Kotlin 里订阅照旧工作。
- 不动线程模型（扫描线程、UDP 线程、tun 读写线程都照搬，`R2` 再换协程）。
- 不改任何分支、日志文案、方法名；不改 `VpnService.Builder` 的配置顺序。
- 不顺手删 `DatabaseUtils` 这类「看起来没用」的类，除非 `grep` 确认零引用（`FileUtil` 已确认）。

## 风险

| 风险 | 处理 |
| --- | --- |
| EventBus 反射找不到订阅方法（扩展函数化、方法变 private） | 订阅方法一律留在类体且 `public`；R1.4 的回归项专门走过事件路径（加/删网络、切身份、磁贴） |
| `internal` 放宽可见性后误用 | 只在本模块；R1.4 单独一批，回滚面小 |
| 空安全改写引入 NPE 或行为变化 | 按上表「platform type」处理，先保持 Java 的宽松语义，收敛 null 留到单独一轮 |
| 拆文件时把字段带错（如 `vpnSocket` 分到两个文件） | 拆分按上表行范围一次性对照；R1.3/R1.4 各跑一次完整链路 |
| Kotlin `@Volatile` 漏加导致跨线程看不到状态 | 对照 Java 的 `volatile` 逐个标；`isRunning`、`node`、`vpnSocket` 这几个跨线程读的必须有 |
