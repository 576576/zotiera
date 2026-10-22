# 去掉主线程数据库访问（allowMainThreadQueries）

- 状态：草案（方案，待执行）
- 建立日期：2026-10-22
- 事实依据：[`architecture.md`](../architecture.md)、本文「实测」一节

## 现状

`ZotieraDatabase.create()` 里挂着 `allowMainThreadQueries()`，仓储的 44 个方法全是同步的，
52 处调用点分布在 8 个文件里。这个开关最初是为了让迁移期的旧 UI 能直读数据库，现在已经没有理由留着。

## 实测（API 36 模拟器，2026-10-22）

临时在 `ZotieraApplication.onCreate` 挂 `StrictMode.setThreadPolicy(detectDiskReads/Writes + penaltyLog)`，
走一遍「启动 → 概览 → Peer List → 设置 → 行星 → 身份 → 开隧道」：

| 项 | 数值 |
| --- | --- |
| 主线程磁盘违规 | 92 条，其中 57 条带数据库帧 |
| 最慢一次 | **271ms**（冷启动阶段） |
| 其次 | 166ms、146ms（`MainActivity.onCreate`）、55ms（`ZeroTierOneService.onStartCommand`） |
| 出现的入口 | `ZotieraApplication.onCreate`、`MainActivity.onCreate`、`ZeroTierOneService.onStartCommand`、`ZeroTierOneService.onNetworkConfigurationUpdated` |

`StrictMode` 只在首次违规时打印完整栈，所以上面的入口是从 release 包那几次带栈的违规里读出来的，
其余同源违规没有栈。要完整清单得按下面的分期逐文件清点，编译器的 suspend 传导正好能当清单用。

一个额外发现：`onNetworkConfigurationUpdated` 是 JNI 回调，**它在主线程执行**——
也就是说每次控制器下发新配置，都会在主线程上读库并重建隧道。

## 为什么不能直接删开关

删掉 `allowMainThreadQueries()` 之后，第一处主线程查询就抛
`IllegalStateException: Cannot access database on the main thread`。上表四个入口都会立刻命中：
冷启动的种子数据、ViewModel 构造里的网络列表、服务的 `onStartCommand`、JNI 配置回调。

## 目标形态

| 层 | 形态 |
| --- | --- |
| DAO | 保持同步（Room 允许）；线程由仓储负责 |
| `ZotieraRepository` | 全部方法 `suspend`，方法体一个 `withContext(Dispatchers.IO) { ... }` 包住整段（一次切换，不是每个 DAO 调用切一次） |
| ViewModel | 构造时不再查库；`init` 里 `viewModelScope.launch { reload() }`，写操作同样走协程 |
| 页面 | 组合期不再直接查库：`produceState` / `collectAsState`；写操作 `rememberCoroutineScope().launch` |
| 服务 | JNI 回调把工作丢进 `scope`（已有 `CoroutineScope(SupervisorJob() + Dispatchers.IO)`），按顺序串行处理 |
| 磁贴 | `onClick` 里读库改到 IO 协程，UI 更新回主线程 |

## 分期

### 第一期：仓储 suspend 化

- `ZotieraRepository` 44 个方法加 `suspend`，方法体包 `withContext(Dispatchers.IO)`。
- 这一步会把 52 处调用点全部标红，红点就是完整清单，不需要另外维护。
- 不改任何逻辑，只改线程。

### 第二期：UI 侧（ViewModel + 四个页面）

| 位置 | 改法 |
| --- | --- |
| `NodeViewModel` 的 `_state` 初值 | 不再同步读库，`networks` 给空列表 + 新增 `loaded = false` |
| `NodeViewModel.init` | 收集 `NodeState` 的四条协程之外，加一条 `viewModelScope.launch { reload() }` |
| `NodeViewModel` 的写方法 | `joinNetwork` / `leaveNetwork` / `deleteNetwork` / `setNetworkIdentity` 等改成 `viewModelScope.launch { 查改 → reload() }` |
| `IdentityScreen`（10 处） | `remember(refreshKey) { repository.identities() }` → `produceState(emptyList(), refreshKey) { value = repository.identities() }`；写操作走 `scope.launch` 后 `refreshKey++` |
| `PlanetScreen`（9 处） | 同上 |
| `MoonsScreen`（7 处） | 同上；`repository.moonOrbits().onEach { ... }` 那段改成协程里取一次 |
| `OverviewScreen`（3 处） | 同样处理；加入网络的对话框回调里 `joinNetwork` 已是 ViewModel 方法，跟着第二期一起变异步 |

**首帧空白**：`networks` 从「构造时就绪」变成「第一帧为空」，概览会先显示红叉「没有网络」再跳成正常状态。
必须在这一期一起处理，做法二选一：

1. `NodeUiState` 加 `loaded`，`DeviceCard` 在 `!loaded` 时不出红叉（显示上一状态或留白）；
2. 概览在 `!loaded` 时先渲染骨架（卡片占位 + 列表占位）。

推荐 1，改动小且不会引入新的视觉元素。

### 第三期：服务与磁贴

- `onStartCommand`：`lastActivatedNetworkIds()`（读库）与 `startNode` 里有 DB 读 → 整段丢进 `scope.launch`，
  但**必须先返回**一个值给系统：返回 `START_STICKY` 不受影响，DB 部分异步执行。
- `onNetworkConfigurationUpdated`：JNI 同步回调，返回 `Int`。改成
  `scope.launch { updates.send(...) }` + 一个单消费者协程（`Mutex` 或 `Dispatchers.IO.limitedParallelism(1)`）串行处理，
  回调本身立刻返回 `RESULT_OK`。**顺序必须保**：配置更新、重建隧道、写 `network_configs` 不能交错。
- `DatabaseUtils.readLock/writeLock`（服务里 4 处使用）：这套 `ReadWriteLock` 是旧并发模型的遗留，
  串行化交给单消费者之后可以删掉；`assertWriteLockHeld` 一并删。
- `ZeroTierTileService.onClick`：`lastActivatedNetworks().isNotEmpty()` 挪到 IO 协程，
  开/关隧道与磁贴状态更新回主线程。

### 第四期：收口

- 删 `allowMainThreadQueries()`。
- debug 变体常开 `StrictMode.ThreadPolicy.Builder().detectDiskReads().penaltyDeath()`，
  防止以后又有人从组合期直接查库。

## 验收

1. `grep -r allowMainThreadQueries` 无结果。
2. 全流程无 `Cannot access database on the main thread`：冷启动、切三个顶层页、进五个子页、
   加网络、开/关隧道、切实例、删网络、导入身份、增删 moon。
3. 同一条流程在 `penaltyDeath` 下跑通，不崩。
4. 概览首帧不出现红叉闪一下。
5. 隧道重建的日志顺序与改造前一致（配置回调不再交错）。

## 风险

| 风险 | 处理 |
| --- | --- |
| suspend 传染导致中途不能编译 | 第一期一次性改完再提交，不留半截 |
| 配置回调改成异步后乱序，隧道反复重建 | 单消费者 + `Mutex`，并在验收里比对重建日志顺序 |
| 首帧空列表造成的红叉闪烁 | `loaded` 字段兜住，验收第 4 条 |
| 写操作异步后 UI 状态更新时序变化（如加网络后列表没立刻刷新） | 所有写方法在协程里 `reload()` 之后再 `_state.update`，不依赖调用方时序 |
| 服务里删掉 `writeLock` 引入新的竞态 | 串行化由单消费者替代，同一时刻只有一个协程碰库 |

## 不做的

- 不改用 `Flow` 查询自动刷新列表。那是另一件事（会动 UI 的刷新语义），本次只解决线程。
- 不把 DAO 改成 suspend：仓储里有多段「查 A 查 B 拼装」的方法，手工 `withContext` 一次切换比每个 DAO 切一次更省。

## 工作量

| 文件 | 改动点 |
| --- | --- |
| `ZotieraRepository.kt` | 44 个方法（机械） |
| `NodeViewModel.kt` | 19 处调用 + `loaded` 字段 |
| `IdentityScreen.kt` / `PlanetScreen.kt` / `MoonsScreen.kt` / `OverviewScreen.kt` | 10 / 9 / 7 / 3 处 |
| `ZeroTierOneService.kt` | 2 处 + 串行化 + 删锁 |
| `ZeroTierTileService.kt` | 1 处 |
| `ZotieraApplication.kt` | 种子数据改异步 |
| `ZotieraDatabase.kt` | 删开关 |
