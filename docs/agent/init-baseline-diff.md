# 重构前后基线对比

- 状态：参考（随计划同步更新）
- 建立日期：2026-10-19
- 基线提交：`0373a58`（重构前）；计划见 [`plan/init-refactor.md`](plan/init-refactor.md)
- 事实来源：[`notes/baseline-audit.md`](notes/baseline-audit.md)

本文件回答两个问题：**改了什么**、**什么必须保持不变**。执行时按里程碑对照使用——M1 看 §3，M2 看 §2/§4，M3/M4 看 §4/§5，M6 看 §9。

## 0. 一句话概览

重构前是一个 1.12.2 引擎、Java + Fragment、minSdk 19、包名沿用上游的客户端；重构后是 1.16.2 引擎、Kotlin + Compose、minSdk 30、独立包名与双通道版本体系的应用，能力集合不变（Central 除外，本轮挂起）。

## 1. 架构形态

**重构前**

```
6 个 Activity + Fragment + XML layout
        │  EventBus（27 个事件类）
        ▼
ZeroTierOneService ──► DataStore（文件）──► core(JNI) ──► ZeroTier 1.12.2
        │
        └──► GreenDAO（同进程直连 SQLite）

UI 与数据层无边界：Fragment 直接持有 DaoSession。
```

**重构后**

```
Compose Screen（单 Activity + NavHost）
        │  StateFlow
        ▼
ViewModel ──► Repository ──► :data（Room / DataStore）
                  │
                  ▼
        ZeroTierOneService ──► core(JNI) ──► ZeroTier 1.16.2
                  │  Flow
                  └──────────────► ViewModel
```

关键差别：UI 不再直接触碰数据库与服务；跨层只有 `Flow` 一种信号；持久化收敛到 `:data` 一个模块。

## 2. 工程标识与构建

| 维度 | 重构前 | 重构后 | 变更原因 |
| --- | --- | --- | --- |
| applicationId | `net.kaaass.zerotierfix` | `net.sumitm.zotiera`（`.pr` 通道 `net.sumitm.zotiera.pr`） | D1/D8，脱离上游标识 |
| namespace / 源码包 | `net.kaaass.zerotierfix` | `net.sumitm.zotiera` | 与 applicationId 一致 |
| 应用名 | Zerotier Fix | Zotiera（`.pr` 通道 `Zotiera PR`） | 品牌 + 通道区分 |
| versionName | `1.0.10` | 正式 `1.16.2-<rev>`；`.pr` `1.16.2-pr.<commitCount>` | D2，以引擎为准 |
| versionCode | `14` | 正式 `1160200+rev`；`.pr` `100000+commitCount` | 两通道 appId 不同，空间独立 |
| Gradle | 7.5 | 9.8.1 | AGP 9 前置 |
| AGP | 7.4.2 | 9.4.1 | Kotlin/Compose/Room 前置 |
| Kotlin | 无 | 2.4.21（K2） | G2 |
| JDK | 11（core 8） | 21（toolchain） | AGP 9 要求 |
| compileSdk | 33 | 37 | D3 |
| targetSdk | 33 | 36 | D3 |
| minSdk | 19 | 30 | D3，放弃 Android 10 及以下 |
| 模块 | `:app` `:core` | `:app` `:core` `:data` | 数据层独立 |
| NDK | 未固定 | `28.2.13676358` | 16KB 页对齐 |
| ABI | 四 ABI | `arm64-v8a` + `x86_64` | D7，只保留 64 位 |
| 签名 | 仅 CI 后置签名 | 双通道：`.pr` 固定 keystore；正式 `ANDROID_*` 变量 | D8，本地与 CI 都可验证 |
| 依赖管理 | 各 build.gradle 硬编码 | version catalog | 集中管理 |

## 3. 核心与 JNI

| 维度 | 重构前 | 重构后 | 备注 |
| --- | --- | --- | --- |
| 引擎版本 | 1.12.2（`327eb901`） | 1.16.2（`fc5c3ec`） | G1 |
| 子模块来源 | `kaaass/ZeroTierOne` @ `d028a247` | `zerotier/ZeroTierOne` @ `fc5c3ec` | D5，退掉 fork |
| 许可证 | GPL-3.0（含商业双许可） | MPL-2.0（`node/` `include/` `osdep/`） | 1.16 起变更，需补声明 |
| JNI 构建脚本 | `externals/core/java/CMakeLists.txt`（fork 改 glob） | `core/src/main/cpp/CMakeLists.txt` | 上游脚本在 1.16.2 已失效 |
| CMake 版本 | 3.22.1 | 由 AGP 提供，`CMAKE_CXX_STANDARD 17` | 上游实际按 C++17 编译 |
| `.so` 页对齐 | 4KB | 16KB（`-Wl,-z,max-page-size=16384`） | Android 15+ 要求 |
| JNI Java API | 22 个 `com.zerotier.sdk` 类 | **不变** | 8 个核心类逐行一致 |
| `ZT_Node_new` | 无 config 参数 | 增加 `ZT_Node_Config*` | 上游 JNI 已适配，App 无感 |
| 核心线程回调 | `GETENV` 宏，未附着线程直接断言 | `ScopedJNIThreadAttacher` | 修复 ICMPv6 NS 崩溃 |
| `node/` 源 | 含 `C25519.cpp` | `ECC.cpp` + `PacketMultiplexer.cpp` | 需重新固定源清单 |
| MTU 下限 | 1400 | 510（低带宽模式） | `TunTapAdapter` 需回归 |
| 网络 specialist 上限 | 256 | 512 | 无代码动作 |
| Metrics | 默认开启 | 默认关闭（1.16.1 起） | 无动作 |
| 新增能力 | — | `encryptedHello`、`lowBandwidthMode` | 上游 JNI 硬编码为 0，本轮不暴露 |

## 4. 数据与并发

| 维度 | 重构前 | 重构后 |
| --- | --- | --- |
| 持久化框架 | GreenDAO 3.3.0（Gradle 插件生成 DAO） | Room 2.8.x + KSP |
| schema | 22，带 `MigrationV18`–`V22` 链 | v1 全新建库，无迁移链 |
| 表/列命名 | `NETWORK` `NETWORK_CONFIG` `APP_NODE` `DNS_SERVER` `MOON_ORBIT` `ASSIGNED_ADDRESS`（全大写，GreenDAO 默认） | 按新规范重新设计（`networks` / `network_configs` / `app_node` / `dns_servers` / `moon_orbits` / `assigned_addresses`，snake_case） |
| DAO 可见范围 | 全 App（Fragment 直接持有 `DaoSession`） | 仅 `:data`，对外只暴露 Repository |
| 事件总线 | EventBus 3.2.0，27 个事件类 | `sealed interface NodeEvent` + `SharedFlow` |
| 线程模型 | 裸 `Thread` + `runOnUiThread` | `CoroutineScope(SupervisorJob + Dispatchers.IO)` |
| 设置存储 | SharedPreferences，6 个 key，散落各处 | DataStore Preferences，新 key 规范 |
| 状态表达 | 事件 + 成员变量 | `StateFlow<UiState>` |
| 备份 | `allowBackup=true`（含 `identity.secret`） | `allowBackup=false` |
| 明文流量 | `usesCleartextTraffic=true`（全局） | `networkSecurityConfig` 白名单 |
| schema 演进 | 手写 `ALTER TABLE` 迁移链 | Room `Migration` + schema JSON 入库，禁止破坏性迁移 |

## 5. UI

| 维度 | 重构前 | 重构后 |
| --- | --- | --- |
| 框架 | Fragment + ViewBinding + Material Components 1.9 | Compose + Material 3（BOM 2026.09.00） |
| 导航 | 6 个 Activity + Navigation 2.5.3 | 单 Activity + Navigation Compose |
| 布局文件 | 18 个 XML | 0（`themes.xml` 等配置除外） |
| 主题 | `Theme.ZerotierFix`（Material 2 XML） | `ZotieraTheme`（Compose，`dynamicColor` 于 Android 12+） |
| 深浅色 | `values-night` 资源 | `darkColorScheme` + 系统跟随 |
| 边到边 | 未处理 | 强制，`WindowInsets` 全量消费 |
| 横屏/大屏 | 依赖 XML 限定符 | target 36 下自适应（`sw600dp` 限宽/双栏） |
| 屏幕清单 | 网络列表、加入网络、网络详情、Peers、月亮轨道、设置 | 同上（Compose 重写），入口为底部导航 |
| 语言 | 中英（`values-zh`） | 不变 |

## 6. 能力矩阵

| 能力 | 重构前 | 重构后 | 变化 |
| --- | --- | --- | --- |
| 多网络并存 / 加入离开 | 有 | 有 | 保持 |
| 默认路由 | 有 | 有 | 保持 |
| IPv6 双栈 / 禁用 IPv6 | 有 | 有 | 保持 |
| 局域网广播 / 多播 | 有（含 ICMPv6 NS 修复） | 有（上游修复） | 保持 |
| 自定义 planet（文件 + URL） | 有 | 有 | 保持 |
| Moon 轨道管理 | 有 | 有，但标注「已弃用」并提供导出 | 保留 + §8 迁移方案 |
| Peers / 网络详情展示 | 有 | 有 | 保持 |
| DNS 三模式 | 有 | 有 | 保持 |
| 开机自启 / 前台服务 | 有 | 有（补 FGS 类型） | 行为对齐新平台 |
| Legacy Central 节点管理 | 无 | **挂起** | 本轮不做（D4） |
| 新 Central（service account） | 无 | 无 | 明确不支持 |

## 7. 代码规模

| 指标 | 重构前 | 重构后（目标） |
| --- | --- | --- |
| `app/src/main` Java | 76 个文件 / 7386 行 | 0 |
| Kotlin | 0 | 等量迁移 + Compose UI |
| 事件类 | 27 个 | 1 个 `sealed interface` + 若干 data class |
| XML layout | 18 | 0 |
| 测试 | 2（1 单测 + 1 仪器） | 增补：版本断言、工具类单测、Room schema、Compose 冒烟 |

代码行数不作为验收指标；文件数与依赖树作为结构指标。

## 8. 必须保持不变的部分

这些是"重构不得触碰"的契约，改动即视为回归：

| 不变项 | 原因 |
| --- | --- |
| `com.zerotier.sdk` 包名、类名、方法签名 | JNI 符号（`Java_com_zerotier_sdk_Node_*`）绑死 |
| VPN 行为语义（路由、MTU、DNS、多播、planet/moon 机制） | 用户实际依赖的功能面 |
| 中英文文案 key 与内容语义 | 已发布文案，改动会导致翻译回退 |
| 上游版权与 MPL-2.0 声明义务 | 合规 |
| `externals/core` 子模块内容 | 核心源码只在 fork 分支演化 |

与上一版不同：数据库表名列名**不再**属于不变项（D9）。新包名没有存量库，schema 按新规范重建；代价是将来若做旧包导入，需要一层显式映射。

## 9. 用户可感知的差异

| 差异 | 说明 |
| --- | --- |
| 需要重新安装 | 包名变更，与旧版互不覆盖，可并存 |
| 节点 ID 会变 | 新包新 `identity.secret`；需在控制器重新授权该节点（见 Q6） |
| 两条安装通道 | `.pr` 包（Zotiera PR）用于测试，可与正式包同时存在；两者数据互不相通 |
| 最低系统 | Android 11（API 30）；32 位设备无法安装 |
| 版本号形态 | `1.16.2-1` 而非 `1.0.10` |
| UI 全面改版 | Material 3、边到边、深浅色与动态取色 |
| 网络行为 | 引擎跨 4 个小版本，重连与 MTU 处理可能不同（有回归清单兜底） |
| Moon | 功能仍在，但界面会提示已弃用并建议改用网络专用中继 |
| Central 功能 | 未见即未做，不提供入口 |

## 10. 重构前必须先清理的障碍

| 障碍 | 现状 | 处理 |
| --- | --- | --- |
| `core/src/main/java` 是 symlink | 本机 `core.symlinks=false`，Windows 拿不到 JNI Java 源 | M0 T0.1 改显式 `sourceSets` |
| GreenDAO Gradle 插件 | 停留在 AGP 4 时代 API，是 AGP 9 的硬阻塞 | M2 T2.5 换 Room |
| 无签名配置 | 本地 release 不可安装 | M2 T2.8 双通道签名 |
| 上游 JNI CMake 失效 | 引用三个已删除源文件 | M1 T1.1 自持脚本 |
| 通知权限未申请 | Android 13+ 可能无通知 | M2 T2.6 |
| `allowBackup` / 明文流量 | 身份文件可被备份带走；全局允许明文 | M2 T2.6 |
