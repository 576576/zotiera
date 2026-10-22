# Zotiera 现状

- 状态：事实文档（随代码更新）
- 更新日期：2026-10-21
- 基线提交：`7c4cd69`
- 适用版本：1.16.2-x

本文件描述当前代码的事实：架构、数据、网络行为与界面约定。计划与未完成项在 [`plan/`](plan/)。

## 1. 项目标识

| 项 | 值 |
| --- | --- |
| 来源 | fork 自 `kaaass/ZerotierFix`（上游 1.0.10），引擎升级到 1.16.2 |
| applicationId / namespace | `net.sumitm.zotiera` |
| 测试通道 | `net.sumitm.zotiera.pr`，应用名 `Zotiera PR`，仓库内固定 keystore，不分发 |
| 版本号 | `versionName = <engine>-<revision>`（如 `1.16.2-1`）；`versionCode = major*1e6 + minor*1e4 + patch*100 + revision` |
| 测试包版本 | `versionName = <engine>-r<提交数>` |
| 许可 | 本仓库 AGPL-3.0-or-later；`externals/core` 自 1.16 起 MPL-2.0 |

## 2. 模块与工具链

```
:app   Compose UI + 服务（Kotlin，少量 Java 服务类）
:core  JNI 胶水 + com.zerotier.sdk.*
:data  Room 实体、DAO、Repository
```

| 项 | 值 |
| --- | --- |
| Gradle / AGP | 9.7.1 / 9.4.1（AGP 9 自带 Kotlin 支持） |
| Kotlin / KSP | Compose 插件 2.3.21 / KSP 2.3.12 |
| JDK | 21（Android Studio JBR） |
| SDK | compileSdk 37 / targetSdk 36 / minSdk 30 |
| NDK | 28.2.13676358，CMake 由 AGP 提供 |
| ABI | `arm64-v8a` + `x86_64`，16KB 页对齐 |
| 依赖要点 | Compose BOM 2026.09.00、Material3 1.5.0-alpha29、Miuix 0.9.4、Room 2.8.5、kotlinx-coroutines |
| 语言 | 全部 Kotlin；`app/src/main/java` 已删除 |
| 签名 | `.pr` 通道仓库内固定 keystore；正式包读 4 个 `ANDROID_*` 变量 |
| CI | `.github/workflows/` 六文件矩阵：`lint` / `build` / `release` / `pr` / `clear` / `update_i18n` |
| 混淆 | `pr` 与 `release` 都开 R8：`minifyEnabled` + `shrinkResources`，规则在 `app/proguard-rules.pro` |
| 包体 | 单 ABI 5.4 MB（dex 3.1 MB）；双 ABI 7.4 MB |

## 3. 引擎与 JNI

| 项 | 值 |
| --- | --- |
| 引擎版本 | 1.16.2（tag `1.16.2`，commit `fc5c3ec22090b5b2a0f274e863651fe9ca489bf4`） |
| 子模块 | `externals/core` 指上游 `zerotier/ZeroTierOne`，不使用 fork |
| JNI 构建 | 自持 `core/src/main/cpp/CMakeLists.txt`（上游 `java/CMakeLists.txt` 在 1.16.2 引用了已删除的源文件，无法 configure） |
| JNI Java API | `com.zerotier.sdk.*` 8 个核心类与 1.12.2 逐行一致；`ZT_Node_new` 的 config 参数由上游 JNI 适配 |
| 用到源码 | `node/`、`include/`、`osdep/`；不构建 `controller/` |
| 线程附着 | 上游 `ScopedJNIThreadAttacher`（修复 ICMPv6 NS 触发的 JNI 崩溃） |
| 核心常量 | `ZT_MIN_PHYSMTU` 510；`ZT_MAX_NETWORK_SPECIALISTS` 512；metrics 默认关闭 |

`com.zerotier.sdk` 的包名与类名不可改：JNI 符号 `Java_com_zerotier_sdk_Node_*` 与之绑死。
R8 里整包 `-keep class com.zerotier.sdk.** { *; }`：原生侧用字面量类名与签名找类、找方法、找回调接口。

## 4. 数据层

Room 2.8.5，schema **v6**，`exportSchema = true`。

| 表 | 内容 |
| --- | --- |
| `planets` | 行星：worldId、别名、是否内置、是否默认、是否启用 |
| `identities` | 身份：别名、序号、是否默认、是否启用 |
| `networks` | 网络：networkId、名称、`planet_id`、`identity_uid`、`last_activated` |
| `network_identities` | 某条网络用过哪些身份（换身份时的二次确认依据） |
| `network_configs` | 网络配置缓存 |
| `dns_servers` | 每个网络的 DNS |
| `assigned_addresses` | 每个网络分配到的地址 |
| `moon_orbits` | moon 轨道 |

- schema 从 v6 起只增不毁：改表必须带 `@AutoMigration` 或手写 `Migration`，没有破坏式兜底；
  v1–v5 是稳定前的开发库，不提供迁移（现网只有 v6）。首启由「没有身份就建一条」兜底。
- 导出 schema 在 `data/schemas/`，KSP 编译时校验迁移与实体一致。
- **线程**：`ZotieraRepository` 的公开方法全是 `suspend`，整段切到 `Dispatchers.IO`；DAO 不出 :data 模块，调用方不需要自己挑线程。数据库没有 `allowMainThreadQueries`，主线程查库会直接抛异常。
- **种子数据**：官方行星与默认身份的补齐收敛在 `repository.ensureSeed()`（进程内一次，`Mutex` 保护）——界面第一次 `reload()` 前会等它，服务启动时也会调，谁先到都不会读到半成品。
- 设置项是 SharedPreferences，文件 `<包名>_preferences`，读写统一走 `util/AppPrefs`（项目不依赖 `androidx.preference`）；
  键集中在 `util/Constants.kt`，启动时 `pruneObsoletePrefs` 删除已废弃的键。

## 5. 实例模型

实例 = 行星 × 身份。引擎的数据目录里同时放着身份、planet 与已加入网络，所以「换实例」等于换一套目录。

```
files/
├── planets/<worldId>.planet          自定义行星的字节
├── identities/<uid>/identity.*        身份密钥正本
└── instances/<planetId>-<uid>/        引擎数据目录
    ├── identity.secret / identity.public
    ├── planet                         （非官方行星时写入）
    ├── networks.d/  peers.d/
```

- `InstanceStore`：`ensure()` 把身份正本下发到实例目录，`syncUp()` 把引擎生成的密钥收回正本。
- `DataStore(baseDir, planetOverride)` 按实例目录构造；`hookPlanetFile()` 只在实例内有 planet 文件时顶替。
- 同一时刻只有一个节点在线（UDP 9993/9994 只能绑一次）；切换实例 = 停节点 → 重建 DataStore/Node → 重新 join。
- 概览页只允许同一 `(planetId, identityUid)` 的网络同时开启；其它网络置灰并提示需先断开当前实例。
- 简易模式默认开：隐藏「管理行星」「管理身份」，只用默认行星 + 默认身份；默认项不能停用。

## 6. 隧道与网络

`ZeroTierOneService`（`VpnService`）按所有已加入网络聚合出一个 tun：

| 项 | 规则 |
| --- | --- |
| 地址 | 每个网络的 `assignedAddresses` 全部加入 |
| 路由 | 所有网络下发的路由取并集；同一 CIDR 冲突先到先得 |
| 默认路由 | 全局「所有流量走 ZeroTier」开关决定，不按网络区分 |
| DNS | 每网络各自配置，建隧道时取并集 |
| MTU | 所有网络的最小值，0 视为 2800 |
| 分应用代理 | 仅在未开全隧道时按 `DISALLOWED_APPS` 放行 |
| 重建 | 配置指纹（地址 + 路由 + DNS + MTU + 全隧道）不变就跳过，避免 `CONFIG_UPDATE` 反复拆隧道 |

- 出站包按目的地址反查网络；每个网络有自己的 MAC 与 ARP/NDP 表。
- **命令与状态**：界面不再和服务互相发事件。命令走 `startService` + action（`ServiceCommands`：
  `START` / `STOP` / `JOIN` / `LEAVE` / `RECONFIGURE` / `ORBIT` / `DEORBIT`），状态走 `NodeState`
  （`running` / `status` / `configs` 三个 `StateFlow`，`peers` 是 `WhileSubscribed(2000)` 的轮询流）。
- **返回值**：`START_STICKY`；`intent == null` 走与 `START` 相同的路径，重新 join 全部已加入网络并重建隧道。
  这条路径只在系统真的重拉服务时才走到：模拟器（API 36）上 `kill -9` 之后 `dumpsys activity services`
  显示 `startRequested=true`、`app=null`，但没有重启日志，进程也没起来；真机行为待验。
- **并发**：服务持一个 `CoroutineScope(SupervisorJob + Dispatchers.IO)`；后台任务循环、UDP 收包、TUN 读、
  ARP/NDP 表的 1 秒清理都是协程，`delay` 取代 `Thread.sleep`，阻塞读靠关流/socket 超时退出。
- **串行化**：命令处理与 JNI 配置回调都投到 `serialScope`（`Dispatchers.IO.limitedParallelism(1)`），同一时刻只有一个协程读写数据库、重建隧道。`onNetworkConfigurationUpdated` 是主线程上的同步回调，只把更新塞进 `Channel` 就返回，真正的处理在消费者协程里按顺序做。
- 起节点已经不在主线程，Toast 与磁贴刷新要自己切回主线程（`showToast` / `ZeroTierTileService.refreshTile`）。
- 服务本体仍在单个文件 `ZeroTierOneService.kt`：隧道部分与生命周期共用 `vpnSocket`、`tunnelFingerprint`，
  拆开要把这些字段放开给另一个类，收益不抵风险；`ServiceCommands` 已独立成文件。
- 网络行开关 = 加入/离开该网络；设备卡单击 = 开关隧道，长按 = 进设备页。
- DNS 三模式（加入网络时选择）：不使用 / 网络下发 / 自定义；自定义服务器没有编辑界面，选它等于不加 DNS。
- moon 保留但标为弃用：界面提示 + 支持导出 `.moon`；moon 是独立根，不与行星关联。

## 7. 界面

单 Activity + Navigation Compose。风格分 Miuix（默认）与 Material 3 两套，分支只出现在 `ui/component/`。

**顶层页**：概览（`overview`）、结点列表（`peers`）、设置（`settings`），窄屏底部导航、宽屏导航栏。
**推入页**：`device`、`network/{id}`、`moons`、`planet`、`identities`、`about`、`licenses`。

| 组件 | 行为 |
| --- | --- |
| `AppLargeTitlePage` | 顶层页顶栏：大标题随内容上滑收缩成一条并停顶（Miuix `MiuixScrollBehavior` / Material `exitUntilCollapsedScrollBehavior`，Material 固定 56/104dp 与 Miuix 同高） |
| `AppTopBar` | 详情页顶栏：返回键 + 标题，固定 |
| `BarWithoutTopInset` | 顶栏内部把 `systemBars ∪ displayCutout` 的顶部标成已消费，状态栏留白只让一次 |
| `AppStatusCard` | 概览设备卡：单击开关隧道、长按进设备页；五种语气 + 右下角大图标 |
| `AppRoleChip` / `AppSwitch` / `AppListRow` / `AppDialog` / `AppFab` | 列表、开关、弹窗、浮动按钮 |

设备卡状态：

| 状态 | 条件 | 外观 | 底行 |
| --- | --- | --- | --- |
| 工作中 | 隧道运行、节点在线、所有已加入网络 `OK` | 绿底 + 空心大对勾 | `A1 · N 个网络` |
| 工作中（部分） | 隧道运行、节点在线，至少一个网络 `OK`、至少一个不是 | 黄底 + 空心大对勾 | 同上 |
| 连接中 | 隧道运行、节点未上线；或一个网络都没连上但还有可能在连 | 黄底 + 空心大对勾 | 同上 |
| 未连接 | 有网络、隧道关闭 | 黄底 + 空心停止图标 | 同上 |
| 没有可用网络 | 未加入任何网络，或加入的全部被控制器拒绝（`ACCESS_DENIED` / `NOT_FOUND`） | 红底 + 空心大叉 | 未加入任何网络时为空 |

判定顺序即上表顺序，实现在 `screens/OverviewScreen.kt` 的 `statusToneOf`，单测见
`test/…/StatusToneTest.kt`。「全被拒」排在「部分」之前：控制器不认的网络再等也不会好，
按「部分」显示等于让人白等；而「还在请求配置」「需要认证」算还有可能，归「连接中」。

卡片三行文字固定为：状态 / `版本：<versionName>` / `实例 · N 个网络`。

`NodeUiState.loaded` 为 false（首次读库还没回来）时卡片按「未连接」画，不显示「没有网络」的红叉，
避免冷启动闪一下红底大叉。

设置页分组顺序：通用（简易模式、开机自启、从最近任务隐藏）→ 外观（界面语言、界面风格、颜色模式）→
网络（移动网络、IPv6）→ 管理入口（行星 → 卫星 → 身份，简易模式下只留卫星）→ 关于。

**系统栏留白**：`AppScaffold` 显式给 `contentWindowInsets = systemBars ∪ displayCutout`，顶部取两者最大值而不是相加——相加会让刘海机多让出一整个状态栏的高度（实测 62px）。不含输入法 inset，键盘弹出时内容不被顶起。

**图标**：15 个图标以 vector drawable 放在 `res/drawable/ic_*.xml`（Material Symbols Rounded，Apache-2.0），
组件参数用 `@DrawableRes Int`，内部按框架能力取 `painterResource` 或 `ImageVector.vectorResource`。
项目不依赖 `material-icons-core` / `material-icons-extended`，`UiConventionsTest` 会拦住重新引入。

**主题**：`res/values/themes.xml` 只有三条。`Theme.Zotiera` 挂在清单上，跟随系统深浅色，决定系统启动画面底色；
`Theme.Zotiera.Light` / `Theme.Zotiera.Dark` 由 `MainActivity.applyWindowTheme()` 在 `super.onCreate` 之前按
应用内的颜色模式 `setTheme`。界面全由 Compose 绘制，XML 主题只管 Compose 画出第一帧之前的窗口底色。
系统启动画面只认清单主题，运行时的 `setTheme` 改不到它。

**语言**：`values`（英文）+ `values-zh`；`res/xml/locales_config.xml` 声明 `en` / `zh-Hans` 并挂到
`android:localeConfig`，API 33+ 可在系统设置里改本应用语言。`AppLanguage` 以系统值为准并回写偏好，
应用内切换同时写系统；30–32 只有偏好。

## 8. 身份文件

| 文件 | 大小 | 内容 |
| --- | --- | --- |
| `identity.public` | 141 字节 | `<10 位节点 ID>:0:<公钥 hex>` |
| `identity.secret` | 270 字节 | 私钥记录，泄露即可冒充该节点 |

- 正本在 `files/identities/<uid>/`，实例目录里是副本；导入按 zip（两个文件）或单个 `identity.secret` 写入，
  校验节点 ID 且不覆盖已有身份。
- 导出为 zip：`identity.secret` + `identity.public` + `README.txt`；桌面端复用未在本轮验证。
- Android 11+ 不能跨应用读私有目录，换包名后旧版身份无法自动继承。

## 9. 能力清单

| 能力 | 状态 |
| --- | --- |
| 多网络同时加入 / 连接 | 有（同一实例内） |
| 多行星 / 多身份 / 实例切换 | 有 |
| 自定义行星（文件、HTTPS URL） | 有 |
| moon 轨道管理 | 有，标注弃用并提供导出 |
| DNS 三模式 | 有；自定义服务器无编辑界面 |
| 全隧道 / 分应用放行 | 全隧道开关有；分应用放行硬编码 `com.android.vending`，无界面 |
| 开机自启、快捷设置磁贴、前台通知 | 有 |
| 身份导入 / 导出 | 都有（zip：两个密钥 + README） |
| Legacy Central 成员管理 | 不在本仓库：独立 App（`ZotieraManager` 仓库） |
| 与其它 VPN 同时运行 | 不支持：Android 每个用户只有一个活动 VPN |

## 10. 重构前后对照

| 维度 | 重构前（`0373a58`） | 现在 |
| --- | --- | --- |
| 引擎 | 1.12.2（fork 子模块） | 1.16.2（上游子模块） |
| 包名 | `net.kaaass.zerotierfix` | `net.sumitm.zotiera` |
| UI | 6 Activity + Fragment + 18 个 XML layout | 单 Activity + Compose |
| 数据 | GreenDAO 3.3.0，schema 22 | Room，schema 6 |
| 事件 | EventBus，27 个事件类 | `sealed interface NodeEvent` + `SharedFlow` |
| 并发 | 裸 `Thread` + `runOnUiThread` | 协程 + `StateFlow` |
| 构建 | Gradle 7.5 / AGP 7.4.2 / JDK 11 | Gradle 9.7.1 / AGP 9.4.1 / JDK 21 |
| SDK | 33 / 33 / 19 | 37 / 36 / 30 |
| ABI | 4 个 | `arm64-v8a` + `x86_64` |
| 备份 / 明文 | `allowBackup=true`、全局明文 | `allowBackup=false`、`networkSecurityConfig` 白名单 |
| 签名 | 仅 CI 后置签名 | 双通道签名 |

代码规模从 76 个 Java 文件 / 7386 行迁移到 Kotlin + Compose；行数不作为验收指标。

## 11. 不变项

| 不变项 | 原因 |
| --- | --- |
| `com.zerotier.sdk` 包名、类名、方法签名 | JNI 符号绑死 |
| VPN 行为语义（路由、MTU、DNS、多播、planet/moon 机制） | 用户依赖的功能面 |
| 中英文文案 key 与语义 | 已发布文案 |
| 上游版权与 MPL-2.0 声明义务 | 合规 |
| `externals/core` 工作区 | 核心源码只在 fork 分支演化 |

## 12. 已知限制

- 系统只允许一个活动 `VpnService`：本应用启动会停掉其它 VPN，反之亦然。共存方案（root 模式 tun + 策略路由）已设计完但无限期搁置，见 [`plan/coexist-mode.md`](plan/coexist-mode.md)。
- 旧包数据（含 `identity.secret`）无法自动继承，只能人工导出导入。
- Legacy Central 与新 Central（service account）均不支持。

## 13. 相关计划

| 文档 | 状态 |
| --- | --- |
| [`plan/coexist-mode.md`](plan/coexist-mode.md) | 搁置（root 模式 tun + 策略路由，设计完成，未做 P0） |
