# Zotiera 重构计划

- 状态：**执行中**（M0/M1/M2/M6 已完成，M3/M4 部分完成）
- 建立日期：2026-10-19
- 事实依据：[`architecture.md`](../architecture.md)
- 基线提交：`0373a58`（`origin/master`，工作区干净）
- 适用版本：Zotiera 1.16.2-x 起

## 变更记录

| 日期 | 版本 | 变更 | 影响 |
| --- | --- | --- | --- |
| 2026-10-19 | v1 | 初稿，M0–M6 全量规划 | — |
| 2026-10-19 | v2 | 依据决策确认：包名改 `net.sumitm.zotiera`；版本号以引擎版本为主；minSdk 30 / target 36 / compile 37；Legacy Central 降为挂起；接受退掉 fork。据此取消「旧库原地升级」前提，新增 16KB 页对齐与 target 36 行为义务 | M2 任务重写；M5 挂起；风险表与验证矩阵重排 |
| 2026-10-19 | v2.1 | 文档结构定稿为 README + `plan/` + `notes/`，硬约束并入 README，不单设 AGENTS 文件；新增 `init-baseline-diff` 记录重构前后逐项对比（已并入 [`architecture.md`](../architecture.md)） | 文档结构简化 |
| 2026-10-20 | v4 | Legacy Central 明确移出本仓库，单开 Zotiera Manager（见 [`zotiera-manager`](zotiera-manager.md)）；新增 Planet / 身份 / 实例分组计划（已落地，见 [`architecture.md`](../architecture.md) 的实例模型） | M5 由「挂起」改为「移出」 |
| 2026-10-19 | v3 | ABI 收敛为 `arm64-v8a` + `x86_64`；双通道签名（`.pr` 固定密钥 / dispatch `ANDROID_*`）；Room schema 命名自由；新增 §8 Moon 迁移方案；CI 改为五文件矩阵（依据 `ci-matrix`） | M1 T1.6、M2 T2.5/T2.8、M6 T6.1 重写；风险与验证矩阵扩充 |
| 2026-10-19 | v3.1 | 文档名去掉日期（本文件改名 `plan/init-refactor.md`，对比文档移出 plan 为 `init-baseline-diff.md`）；CI 补 `update_i18n.yml`，建文件但阻塞至 Weblate 接上 | 文档命名与 CI 矩阵（六文件） |
| 2026-10-19 | v3.2 | `update_i18n.yml` 改为悬空：只留 `workflow_dispatch`，暂不加 `push: main` | CI 矩阵 |
| 2026-10-19 | v4 | 实际执行：核心 1.16.2 + AGP 9 + Room + 包名切换 + 双通道签名 + CI 六文件落地；Compose 覆盖主界面，详情/月亮仍为 Fragment | 里程碑状态回填，剩余项见 §13 |
| 2026-10-21 | v5 | 文档重组：`notes/` 移除，已完成的计划与基线对比并入 [`architecture.md`](../architecture.md)，`plan/` 只留未完成项 | 文档结构 |

## 1. 目标

| # | 目标 | 完成判据 | 本轮 |
| --- | --- | --- | --- |
| G1 | ZeroTier 核心 1.12.2 → 1.16.2（当前 latest stable，commit `fc5c3ec`） | 两个 64 位 ABI 编译通过且 16KB 页对齐；`Version` 返回 1.16.2；IPv4/IPv6/多播/自定义 planet/moon/peers 全部真机回归通过 | 是 |
| G2 | Java 逐步迁移到 Kotlin，UI 迁到最新 Compose + Material 3 | `app/src/main` 的 Java 文件数归零；旧 Fragment/layout 删除；Android 11–16 真机 UI 一致 | 是 |
| G3 | 融入 Legacy Central 节点管理 | 登录 token、浏览网络与成员、授权/取消授权、改名；本机节点可识别 | **不做**，移到独立 App（`zotiera-manager.md`） |

G3 已移出本仓库：Legacy Central 的管理能力放到独立的 Zotiera Manager App（见 [`zotiera-manager.md`](zotiera-manager.md)），本仓库不再为它建模块、也不预写代码。

非目标：iOS/桌面端、自建 controller、新 Central API、network relays（月亮替代方案）、旧包数据自动迁移。

## 2. 已确认决策

| ID | 决策 | 依据 |
| --- | --- | --- |
| D1 | applicationId 改为 `net.sumitm.zotiera`，与 `namespace` 一致；源码包同步改名 | Q1 答复 |
| D2 | 版本号以引擎版本为基准：`1.16.2-<修订号>` | Q1 答复 |
| D3 | minSdk 30 / targetSdk 36 / compileSdk 37，放弃 Android 10 及以下 | Q2 答复 |
| D4 | Legacy Central 只支持 Legacy，且不在本 App 实现，单开 Zotiera Manager | Q3 答复 + 2026-10-20 调整 |
| D5 | 子模块指向上游 tag `1.16.2`，CMake 胶水收回本仓库；必要时再 fork + 打补丁 | Q4 答复 |
| D6 | 设置项直接用 DataStore Preferences + 新 key 规范，不做旧 key 迁移 | Q5 答复（在 D3 前提下） |
| D7 | ABI 只保留 64 位：`arm64-v8a` + `x86_64`，去掉 `armeabi-v7a` | 2026-10-19 追加 |
| D8 | 双通道签名：CI 自动构建用仓库内固定 keystore 签发 `.pr` 通道包（appId `net.sumitm.zotiera.pr`，应用名 `Zotiera PR`）；手动 dispatch 发布用 4 个 `ANDROID_*` 密钥变量签发正式包 | 2026-10-19 追加 |
| D9 | Room schema 不沿用旧表名/列名，按新规范重新设计（包名已变，无存量库） | 2026-10-19 追加 |
| D10 | Moon 保留支持，但按上游「已弃用」定位处理：UI 明示、提供迁移到 network relays 的指引与导出能力 | 2026-10-19 追加 |

决策连带结论：

- **「旧库原地升级」不再成立**。换包名后新应用的数据目录、数据库、SharedPreferences 全是空的，Room 不需要兼容 22 版 GreenDAO schema，`MigrationV18`–`V22` 那条链可以整条丢弃。R4 从「高风险数据迁移」降级为「老用户从零开始」。
- **老用户身份无法自动继承**。`identity.secret` 在旧包的私有目录里，Android 11+ 不允许跨应用读取，只能由用户在旧 App 内导出、新 App 导入。本轮不做（见 Q6）。
- **`com.zerotier.sdk` 包名绝不能跟着改**。JNI 的 `Java_com_zerotier_sdk_Node_*` 符号与该包名绑死，改名等于重写整套 JNI。

## 3. 结论先行

审计得出 6 个会改变方案走向的事实：

1. **1.16.2 的 JNI Java API 与 1.12.2 完全一致**（8 个核心类逐行无差异，仅 CRLF/LF 之别）。G1 不改 App 的 JNI 调用面，风险集中在 C++ 构建。
2. **上游 `java/CMakeLists.txt` 在 1.16.2 上是坏的**，写死了三个已删除的源文件。不能"直接跟上游"，必须自持源清单。
3. **上游已修复 fork 里那个 ICMPv6 NS 崩溃**（`ScopedJNIThreadAttacher`，与 fork 的 `JNIEnvGuard` 同类）。可以退掉 fork。
4. **1.16 起核心改 MPL-2.0**，`controller/` 变商业许可并从默认构建移除；我们只用 `node/` + `include/` + `osdep/`。
5. **GreenDAO 3.3.0 的 Gradle 插件是升级 AGP 的硬阻塞**。数据层必须换 Room；换包名让这件事变简单而不是更难。
6. **`core/src/main/java` 是 symlink**，本机 `core.symlinks=false`，Windows 下拿不到 JNI Java 源。M0 必须先修，否则本地无法验证任何后续改动。

## 4. 平台与工具链基线

| 项 | 目标 | 现状 |
| --- | --- | --- |
| Gradle | 9.8.1 | 7.5 |
| AGP | 9.4.1 | 7.4.2 |
| Kotlin | 2.4.21（K2） | 无 |
| JDK | 21（toolchain 固定；本机 25 仅用于运行 Gradle） | 11 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 30 | 33 / 33 / 19 |
| Compose BOM | 2026.09.00 | 无 |
| NDK | 28.2.13676358（固定，16KB 页对齐支持） | 未固定 |
| ABI | `arm64-v8a` + `x86_64` | `armeabi-v7a` `arm64-v8a` `x86` `x86_64` |
| 应用包名 | `net.sumitm.zotiera`（`.pr` 通道加后缀） | `net.kaaass.zerotierfix` |

版本号落地时按官方兼容表核对，不盲信上表数字；核对结果回填到本表。

### 4.1 minSdk 30 直接消掉的历史包袱

- 删除 `multiDexEnabled`、`androidx.multidex`（21+ 原生多 dex）。
- 删除 `coreLibraryDesugaring` / `desugar_jdk_libs_nio`（`java.nio.file`、`java.time`、`java.util.Base64` 原生可用）。
- 删除所有 `Build.VERSION.SDK_INT >= 26` 的通知渠道兼容分支（`NotificationChannel` 是 API 26 起就有）。
- 源码级别可提到 Java 17 / Kotlin 2.4 默认，`var`、`switch` 表达式、record（Kotlin 侧 data class）随便用。

### 4.2 minSdk 30 + targetSdk 36 新增的义务

| 义务 | 说明 | 落点 |
| --- | --- | --- |
| 16KB 页对齐 | Android 15+ 设备要求 native `.so` 按 16KB 页对齐。NDK r28 + AGP 9 默认给 `-Wl,-z,max-page-size=16384`，但必须实测 `LOAD` 段 `Align` 为 `0x4000` | T1.1 |
| 前台服务类型 | target 34+ 起 `startForeground` 必须声明 `foregroundServiceType`；`VpnService` 用 `specialUse` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`，按当前平台文档核对 | T2.4 |
| 运行时通知权限 | Android 13+ 需 `POST_NOTIFICATIONS`，首次启动引导申请 | T2.4 |
| 强制 edge-to-edge | target 35+ 忽略 `windowOptOutEdgeToEdgeEnforcement`，Compose 侧用 `WindowInsets` 消费 | T4.1 |
| 大屏自适应 | target 36 起忽略方向/尺寸/可调整性限制，需接受多窗口与横屏 | T4.1 |
| 预测式返回 | 默认开启，导航用 Navigation Compose 的原生返回栈 | T4.2 |
| 跨应用私有目录不可读 | Android 11+ 无法读取旧包 `files/`，任何"自动迁移"方案直接判死 | Q6 |

## 5. 版本号与构建号规范

- `versionName = "<引擎版本>-<修订号>"`，例：`1.16.2-1`、`1.16.2-2`。
  - 修订号是 zotiera 侧单调自增整数（`1` 起），每发一次版 +1；引擎版本不变时连续递增。
  - 引擎升级时修订号归 `1`（`1.16.3-1`）。
  - 预发布用 `1.16.2-1-beta.1`，正式版去掉后缀。
- `versionCode = major*1_000_000 + minor*10_000 + patch*100 + revision`，例：`1.16.2-3` → `1160203`。
  - 单调递增，且与 versionName 一一对应；`revision` 上限 99，超过必须发引擎版本或升 minor。
  - 预发布版 `versionCode` 用 `+90` 段（`1160291`），正式版重发更高码，避免"预发布装过就装不了正式版"。
- 引擎版本号的唯一来源是 `externals/core/version.h`，CI 校验 `versionName` 前缀与之相等，不一致直接失败。

### 5.1 发布通道

两个通道是不同的应用（appId 不同），versionCode 空间互相独立。

| 通道 | 触发 | appId | 应用名 | versionName | versionCode | 签名 |
| --- | --- | --- | --- | --- | --- | --- |
| `release` | 手动 dispatch | `net.sumitm.zotiera` | Zotiera | `1.16.2-<rev>` | `1160200 + rev` | 4 个 `ANDROID_*` 变量 |
| `pr` | push main / PR | `net.sumitm.zotiera.pr` | Zotiera PR | `1.16.2-pr.<commitCount>` | `100000 + commitCount` | 仓库内固定 keystore |

- `rev` 来自仓库根 `version.txt`（单整数），发布提交时 +1；CI 只读不写。
- `commitCount` 由 CI 用 `git rev-list --count HEAD` 计算，因此 `pr` 包的 versionCode 随提交单调递增，可覆盖安装。
- Gradle 侧实现为 build type：`debug`（本地）、`pr`（后缀 + 改名 + 固定签名）、`release`（正式签名）。

## 6. 执行顺序

顺序原则：**同一时间只动一个变量**。

```
M0 基线固化 ──► M1 核心 1.16.2 ──► M2 包名/构建/数据层 ──► M3 Kotlin 业务层 ──► M4 Compose UI ──► M6 发布
   (纯工程)        (C++ 变量)          (工具链变量)              (语言变量)          (UI 变量)

M5 Legacy Central：移出本仓库，见 zotiera-manager.md
```

| 里程碑 | 主题 | 主要变量 | 出口条件 |
| --- | --- | --- | --- |
| M0 | 基线固化 | 无（不改行为） | Windows 本地与 CI 都能出 APK；基线冒烟结果入库 |
| M1 | 核心 1.16.2 | C++ 源码 | 两个 64 位 ABI 编译 + 16KB 对齐；真机 VPN 全功能回归；`Version` = 1.16.2 |
| M2 | 包名 + 构建 + 数据层 | 工具链 | 新包名可安装；AGP 9 构建通过；Room 全新库可用 |
| M3 | Kotlin 业务层 | 语言 | 非 UI 代码 Java 归零 |
| M4 | Compose UI | UI 框架 | 旧 Activity/Fragment/layout 删除；Android 11–16 表现一致 |
| M5 | Legacy Central | 移出 | 不做，改由独立 App 承担 |
| M6 | 发布 | 打包/文档 | 签名 release 可直接安装，版本号规则生效 |

## 7. 里程碑分解

### M0 基线固化

目的：把"能构建、能验证、能回滚"变成确定的事。后面每一步都要靠它判断回归来源。

| 任务 | 内容 | 验收 |
| --- | --- | --- |
| T0.1 | 删 `core/src/main/java` 这个 symlink 条目，`core/build.gradle` 改显式 `sourceSets { main { java.srcDirs += "../../externals/core/java/src" } }` | Windows 本地 `:core:compileDebugJavaWithJavac` 通过 |
| T0.2 | 引入 version catalog（`gradle/libs.versions.toml`），依赖版本集中管理；暂不升版本 | 产物与基线等价 |
| T0.3 | 固化基线 APK（旧包名 `1.0.10-baseline`）与冒烟清单（§9） | 真机跑通，结果写入 `architecture.md` |
| T0.4 | 确认工具链组合：本机 JDK 25 跑 Gradle、NDK r28 在 AGP 7.4 下是否可用、CMake 来源 | 记录结论；若 NDK r28 不可用，把 M2 的工具链部分前移到 M1 之前 |
| T0.5 | 建立 `.editorconfig` 与 ktlint/detekt 位置（仅占位，不启强制） | 文件入库 |

回滚点：单提交 `git revert`。

### M1 核心升级 1.12.2 → 1.16.2

策略：**退掉 kaaass fork，子模块指向上游 tag，Android 构建胶水收回本仓库**（D5）。若上游源码需要改动，再 fork 并只保留最小补丁分支。

| 任务 | 内容 | 验收 |
| --- | --- | --- |
| T1.1 | 新增 `core/src/main/cpp/CMakeLists.txt`：`file(GLOB)` 收 `node/*.cpp`；显式加 `osdep/OSUtils.cpp`、`java/jni/*.cpp`、`ext/lz4/lz4.c`、`ext/json-parser/json.c`、`ext/http-parser/http_parser.c`；include `include/`、`node/`、`ext/` 与 prometheus 两个目录；`CMAKE_CXX_STANDARD 17`；`-Wl,-z,max-page-size=16384`；链接 `log` | `:core:externalNativeBuildDebug` 两个 ABI 通过；`llvm-readelf -l` 显示 LOAD 段 `Align 0x4000` |
| T1.2 | `.gitmodules` 改指 `https://github.com/zerotier/ZeroTierOne.git`，pin 到 `fc5c3ec`；`core/build.gradle` 的 cmake path 指向 T1.1 脚本 | `git submodule status` 干净，子模块无本地改动 |
| T1.3 | `Version` 断言：debug 首页或单测输出 JNI 报告的引擎版本 | 显示 1.16.2 |
| T1.4 | MTU 回归：1.16 把 `ZT_MIN_PHYSMTU` 从 1400 下调到 510（低带宽模式），确认 `TunTapAdapter` 不依赖 1400 下限 | 低 MTU 网络可连通 |
| T1.5 | ICMPv6 NS 场景复测（双栈、邻居发现） | 无崩溃；若复现，按 D5 的「必要时 fork + 补丁」路径移植 `JNIEnvGuard` |
| T1.6 | `abiFilters` 收敛为 `arm64-v8a` + `x86_64`（D7）；`x86` 模拟器通过 x86_64 镜像覆盖 | `:core` 只产出两个 ABI，APK 体积下降 |
| T1.7 | 合规：LICENSE 与 About 页补 MPL-2.0 与上游版权声明 | 文案落地 |

注意事项：`ZT_MAX_NETWORK_SPECIALISTS` 256→512 无需代码动作；`ZT_Node_Config` 的 `enableEncryptedHello` / `lowBandwidthMode` 上游 JNI 硬编码为 0，本期不暴露。

回滚点：子模块回 pin `d028a247` + `core/build.gradle` 回退。

### M2 包名、构建与数据层

三件事挤在一起，必须按顺序做，每步单独可构建。**先把包名切干净，再动工具链**——否则出问题时无法区分是包名漏改还是 AGP 升级导致。

| 任务 | 内容 | 验收 |
| --- | --- | --- |
| T2.1 | 包名切换：`applicationId`/`namespace` 改 `net.sumitm.zotiera`；源码目录 `net/kaaass/zerotierfix` → `net/sumitm/zotiera`；全仓替换全限定名（Java import、Manifest、XML `tools:` 属性、Preference key 前缀类引用）；应用名改 `Zotiera` | 新包名可安装；旧包可与之共存；`grep -r "kaaass"` 只剩注释或历史说明 |
| T2.2 | 版本号落地：`versionName = 1.16.2-1`，`versionCode = 1160201`；CI 校验与 `externals/core/version.h` 一致（§5） | 构建产物 `aapt dump badging` 版本正确 |
| T2.3 | Gradle wrapper → 9.8.1、AGP → 9.4.1、清 `buildscript{}` 改 plugins DSL、`buildConfig` 按需开启 | `assembleDebug` 通过，产物可安装 |
| T2.4 | 启用 Kotlin（K2）、KSP、`jvmTarget` 对齐 | 空 Kotlin 文件能编译 |
| T2.5 | **GreenDAO → Room，抽出 `:data` 模块**：按新规范设计 schema（`networks` / `network_configs` / `app_node` / `dns_servers` / `moon_orbits` / `assigned_addresses`，snake_case 列名、外键 + 索引、枚举走 `@TypeConverters`），Room schema **v1 全新建库**；删除 `MigrationV18`–`V22` 链式迁移；DAO 只在 `:data` 暴露 | 新装可用；无 GreenDAO 依赖残留；schema JSON 入库 |
| T2.6 | 系统行为改造：`foregroundServiceType="specialUse"` + `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`（按当前文档核对）；Android 13+ 运行时申请 `POST_NOTIFICATIONS`；`allowBackup="false"`（`identity.secret` 不外泄）；`usesCleartextTraffic` 改 `networkSecurityConfig` 白名单 | Android 14/16 启动 VPN 不抛 `MissingForegroundServiceTypeException`；通知可见；备份不含身份文件 |
| T2.7 | 删除 minSdk 30 后无用的兼容代码：multidex、desugaring、SDK_INT 分支（§4.1） | 依赖树无 `multidex` / `desugar_jdk_libs` |
| T2.8 | 双通道签名（D8）：`pr` build type 用仓库内 `assets/ci/signing/pr.keystore` + 固定口令，并加 `.pr` 后缀与 `Zotiera PR` 应用名；`release` build type 从 4 个 `ANDROID_*` 变量读密钥，缺失即失败（不静默降级为未签名）；本地开发用 `local.properties` | `assemblePr` 与 `assembleRelease` 均可直接安装；`aapt dump badging` 的包名/应用名/版本正确 |

关于 Room 建库：新包名下没有历史数据，schema 一次设计到位（D9），不迁就 GreenDAO 的命名。表名列名可自由设计，但**任何后续 schema 变更必须带 Room 迁移**，不允许 `fallbackToDestructiveMigration`。

`pr` 通道的 keystore 会进版本库，因此它是公开的：只能用于签同包名的测试包，绝不能用于正式分发。这一点写进 `assets/ci/signing/README.md`，并在 workflow 里注释。正式包只认 4 个 `ANDROID_*` 变量（`ANDROID_KEYSTORE_BASE64` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD`）。

回滚点：T2.1–T2.8 各自独立提交；T2.1 之后不可回退到旧包名（会与已发布数据分叉），如需回退走分支。

### M3 Kotlin 业务层

按依赖倒置从叶子往根走，每批迁移完保持可编译、可运行。

| 批次 | 范围 | 文件数 | 要点 |
| --- | --- | --- | --- |
| B1 | `util/*`、`model/type/*` | 12 | 纯函数与枚举；`DebugLog` → `Timber` 或自留 |
| B2 | `events/*` | 27 | 27 个事件类合并为 `sealed interface NodeEvent`，EventBus 换 `SharedFlow` |
| B3 | `model/*`（含 Room 实体） | 7 | 与 T2.5 合并做，避免二次改写 |
| B4 | `service/*`：`DataStore`、ARP/NDP、`TunTapAdapter`、`ZeroTierOneService` 等 | 10 | 最高风险；裸 `Thread` 收敛到 `CoroutineScope(SupervisorJob + Dispatchers.IO)`，回调改 `Flow` |
| B5 | `ui/viewmodel/*`、`ui/*`（Activity/Fragment） | 18 | 与 M4 合并，直接被 Compose 取代 |
| B6 | `ZerotierFixApplication`、`StartupReceiver` | 2 | 设置项直接落 DataStore Preferences |

合计 76 个文件，与 `app/src/main/java` 现状一致。

关键取舍：

- `ZeroTierOneService`（1063 行）**只做语言迁移 + 并发原语替换**，不重写逻辑；逻辑重构排到 M4 之后单独立项。
- 设置项：直接用 DataStore Preferences，新 key 规范（如 `network.use_cellular`），不保留旧 key、不写迁移代码（D6）。
- 事件总线替换必须成对做（B2 与 B4 同批），否则出现半总线半 Flow 的双轨期。
- 所有依赖 Context 的类改成构造注入，为 Compose 侧 `ViewModel` 铺路。

出口条件：`app/src/main` 下 Java 文件为 0（`ui/` 随 M4 一起消失）。

### M4 Compose UI

技术选型：单 Activity + `NavHost` + Material 3（`dynamicColor` 在 Android 12+ 开启），强制 edge-to-edge + 预测式返回，横屏/多窗口按 target 36 要求自适应。

| 任务 | 内容 |
| --- | --- |
| T4.1 | `ui/theme`（Color/Typography/Shape）、`ZotieraTheme`；`WindowInsets` 全量消费；深夜/浅色双模式；中文资源保持 `res/values-zh` |
| T4.2 | 单 Activity 骨架：`NavHost` + 底部导航（网络 / Peers / 设置），删 `SingleFragmentActivity` 家族 |
| T4.3 | 屏幕逐个替换：设置 → 月亮轨道 → Peers → 加入网络 → 网络详情 → 网络列表（781 行，最重，放最后） |
| T4.4 | 组件化：`NetworkCard` / `PeerRow`，统一状态色（连接中/已连接/请求配置中）；列表用 `LazyColumn` + 稳定 key |
| T4.5 | 清理：18 个 layout、`appcompat`、`navigation-fragment`、`recyclerview`、`swiperefreshlayout`、`legacy-support-v4`、`commons-validator`（如无引用） |
| T4.6 | 大屏与横屏：网络详情/列表在 `sw600dp` 下走双栏或限宽，避免 target 36 下被拉伸 |

出口条件：无 Fragment、无 `findViewById`、无 `*.xml` layout（`themes.xml` 与 `xml/` 配置除外）。

### M5 Legacy Central（移出本仓库）

不再在本仓库实施；设计约束留在 [`zotiera-manager.md`](zotiera-manager.md) 里，给独立 App 用：

- 只支持 Legacy Central（D4）；token 走 `Authorization: token <t>`，`GET /status` 校验。
- 端点：`GET /network`、`GET /network/{id}/member`、`POST /network/{id}/member/{nodeId}`（授权 `{"config":{"authorized":bool}}`、改名 `{"name":...}`）。
- 串行请求 + 60ms 间隔（Legacy 免费额度 20 req/s），429 单独提示。
- token 存 `EncryptedSharedPreferences`（Keystore 主密钥），登出即删，不落日志。
- 独立 `:feature:central` 模块，读 `:data` 的 `app_node` 表做「本机」标记；VPN 核心不依赖它。
- 启动条件：M4 完成且 `:data` 稳定后重新评估，不在 M4 期间并行开工。

### M6 发布与收尾

| 任务 | 内容 |
| --- | --- |
| T6.1 | CI 重写为六文件矩阵（见下表），按 `ci-matrix` 的分工与规则组织；checkout 带 submodules、`setup-java 21`、NDK 固定版本、Gradle 缓存、版本校验（§5） |
| T6.2 | 测试：`Version` 断言、`NetworkIdUtils`/`IPPacketUtils` 单测、Room schema 导出测试、Compose UI 冒烟 |
| T6.3 | 文档：README 重写（新包名、双通道、构建方式、版本规则、G1/G2 状态、G3 挂起）、About 页 MPL 声明、计划状态回填 |
| T6.4 | 分发：新包名等于新应用，需要独立的发布渠道与说明；旧包保留为只读历史版本 |

**CI 矩阵**（依据 `ci-matrix` skill，六个文件）

| 文件 | 触发 | 角色 | 权限 | 要点 |
| --- | --- | --- | --- | --- |
| `lint.yml` | `workflow_call` + `workflow_dispatch` | 质量门禁 | `contents: read` | `:app:lintPr :app:testPrUnitTest`；全仓唯一的命令副本 |
| `build.yml` | `workflow_call` | 编译打包 | `contents: read` | 入参 `channel` / `version_name` / `version_code` / `targets`；执行 `assemblePr` 或 `assembleRelease`；`upload-artifact@v4` 且 `if-no-files-found: error` |
| `release.yml` | `push: main`（`paths-ignore` 文档）+ `workflow_dispatch(channel)` | **唯一入口** | `prep` read，`publish` `contents: write` | `prep` 算版本（§5）→ `lint` → `build`（`needs: [prep, lint]`）→ `publish`；push 只产出 `.pr` 包与 artifacts，dispatch `channel=release` 才建 Release |
| `pr.yml` | `pull_request: main` + `workflow_dispatch` | PR 门禁 | `contents: read` | 调 `lint` + `build`（channel=pr），不发布；**不加 `paths-ignore`** |
| `clear.yml` | `workflow_dispatch` | 清理多余 prerelease | `contents: write` | 按时间窗从旧到新删，先 `checkout` 再删 tag |
| `update_i18n.yml` | `workflow_dispatch` | 翻译同步（**悬空**） | `contents: write` | 只保留手动触发；主体打印缺失前置条件并 `exit 1`，Weblate 接上后再接线并补 `push` 触发 |

必须遵守的几条（违反了不会红，只会静默失效）：

- `lint.yml` / `build.yml` 不得自带 `push` / `pull_request` 触发，只能被 `uses:` 调用。
- 门禁是 job 依赖（`needs`），不是同 job 里的前置 step，否则可能被 `continue-on-error` 绕过。
- 版本号与目标矩阵只在 `release.yml` 的 `prep` 里算一次，`build.yml` 不再重新推导。
- 作业显示名即分支保护的必需检查名，确定后不再改。
- `$GITHUB_OUTPUT` 里只写 `k=v`，诊断信息走 stderr。
- 本地先跑 `ci-matrix` 的 `verify_workflows.py`，再推。

`update_i18n.yml` 悬空：只留 `workflow_dispatch`，**不加 `push` 触发**（Weblate 还没接上，任何自动运行都只会失败）。文件顶部注释写清待办；主体打印缺失的前置条件（`WEBLATE_TOKEN`、项目 slug、目标分支）并 `exit 1`，不写静默成功的空壳。Weblate 接上后，再补 `push: main` + job 级 `if:` 过滤，并把 guard 换成真实的 pull → 提交 → push 流程。

## 8. Moon 迁移方案（保留支持）

上游 1.16.0 的发布说明把 moons 定为「更加额外的 deprecated」，新部署不应再使用，替代方案是 **network-specific relays**（网络专用中继，目前仍为 preview/beta）。1.16.2 的 moon 机制本身没有删除：仍然读写 `moons.d/*.moon`、仍可 `orbit`。因此策略是「保留能力 + 明确弃用 + 给出迁移路径」，而不是直接砍掉。

| 阶段 | 条件 | 动作 |
| --- | --- | --- |
| 现在（M2/M4） | 上游仍支持 | 功能原样保留；UI 在列表页与添加页标注「已弃用，官方建议改用网络专用中继」并给出说明入口；新增 `moon_orbits.source` 字段记录来源（文件 / URL / 手动） |
| 现在（M6） | 用户要备份或换机 | 提供「导出 moon 配置」：把 `moons.d/*.moon` 与列表元数据打包成 zip，可再导入；导出物同时可作为迁移到 relay 时的参照 |
| 上游移除 moon 时 | 引擎升级后 JNI 不再有 `orbit` | 退化为只读：保留列表与现有 orbit 文件，禁用新增/删除，页面顶部提示「当前引擎已移除 moon 支持」，并引导导出 |
| 替代路径 | 用户接受 relay | App 只做说明与跳转：relay 由控制器按网络指派，不是 App 本地配置；moon 是全局节点、relay 是网络级，两者不是一对一，**不做自动转换** |

明确不做的：不在 App 内实现 relay 配置 UI（上游还是 preview，协议与配置形态可能变）；不把 moon 文件翻译成 relay 配置（语义不对应，会制造"看起来迁移了其实没生效"的假象）。

数据层注意：包名变更后 `moon_orbits` 表是新建的，没有历史数据要迁；若将来做旧包导入，moon 只能靠用户导出的 zip 恢复，无法从旧库直读。

## 9. 风险登记

| ID | 风险 | 概率 | 影响 | 缓解 |
| --- | --- | --- | --- | --- |
| R1 | AGP 9 与既有插件/依赖不兼容，构建卡死 | 高 | 高 | T2.3 与 T2.5 绑定推进；先验证最小可构建集再迁代码 |
| R2 | 1.16.2 在 NDK 上编译失败（新源文件、C++17、平台差异） | 中 | 高 | 自持 glob 清单逐文件排除；保留 1.12.2 回滚点 |
| R3 | native `.so` 未按 16KB 页对齐，Android 15+ 装不上 | 中 | 高 | T1.1 验收强制 `readelf` 检查，CI 加同样断言 |
| R4 | 包名变更后老用户身份/网络配置无法继承 | 高（已接受） | 中 | 本轮接受从零开始；如需照顾存量，走 Q6 的导出/导入方案 |
| R5 | `ZeroTierOneService` 并发改造引入偶现崩溃/断流 | 中 | 高 | 只换原语不重写逻辑；保留旧实现分支；长稳测试 |
| R6 | target 36 行为变更（edge-to-edge、大屏、FGS 类型）导致 UI 错位或启动失败 | 中 | 中 | T2.6 + T4.1/T4.6 专项验证；按平台文档核对 FGS 类型取值 |
| R7 | 包名切换漏改（全限定名、Preference 引用、CI 路径） | 中 | 中 | T2.1 用 `grep -rn "kaaass"` 归零作为验收 |
| R8 | MPL-2.0 合规疏漏 | 低 | 中 | T1.7 补声明；不分发 controller |
| R9 | symlink 在 Windows/CI 行为不一致 | 高（已发生） | 中 | T0.1 彻底改为显式 srcDirs |
| R10 | 中文文案在 Compose 迁移中丢失或串行 | 中 | 低 | 字符串留在 `res/values*`，Compose 用 `stringResource` |
| R11 | 放弃 Android 10 及以下引发用户流失 | 低 | 低 | 已在 D3 接受；发布说明写清 |
| R12 | `pr` 通道 keystore 公开（入库），他人可签同包名包 | 高（已接受） | 低 | `.pr` 包不用于分发；`assets/ci/signing/README.md` 注明用途与禁令；正式包只认 `ANDROID_*` |
| R13 | moon 被上游移除后用户配置静默失效 | 中 | 中 | §8 的分阶段方案：先标注弃用，再退化为只读 + 导出，不静默丢数据 |
| R14 | CI 矩阵漂移（重复命令、PR 加了 `paths-ignore`、作业改名） | 中 | 中 | 命令只写在 `lint.yml`/`build.yml`；PR 永不 `paths-ignore`；作业名即检查名；推前跑 `verify_workflows.py` |
| R15 | 去掉 `armeabi-v7a` 后仍有 32 位设备用户 | 低 | 低 | 与 minSdk 30 一起在发布说明中声明 |

## 10. 验证矩阵

每完成一个里程碑跑一遍，结果写入 `docs/agent/architecture.md`。

| 维度 | 用例 |
| --- | --- |
| 设备 | Android 11(30) / 12(31) / 13(33) / 14(34) / 16(36)，至少一台 arm64 真机 + x86_64 模拟器；另需一台 Android 15+ 验证 16KB 页对齐 |
| ABI | APK 只含 `arm64-v8a` + `x86_64`；`x86` 设备与 32 位 arm 设备安装应明确失败而不是崩溃 |
| 安装 | 新包名全新安装；与旧包并存；同通道覆盖安装；`.pr` 与正式包并存互不影响；卸载后重装 |
| 通道 | `assemblePr` 的包名 `net.sumitm.zotiera.pr`、应用名 `Zotiera PR`、签名为固定 keystore；`assembleRelease` 用 `ANDROID_*` 签名；缺变量时构建失败 |
| VPN 生命周期 | 首次授权、开机自启、杀进程后重连、飞行模式切换、Wi-Fi↔蜂窝切换 |
| 网络 | 加入/离开网络、多网络并存、默认路由、局域网广播/多播、IPv6 双栈 |
| 配置 | 自定义 planet（文件 + URL）、moon 添加/删除/导出、DNS 模式（系统/自定义/网络推送） |
| 展示 | 节点状态、peers 列表、网络详情、通知文案、深浅色、中英文、横屏与大屏 |
| 系统 | FGS 类型声明生效、通知权限申请、备份不含 `identity.*` |
| 版本 | `aapt dump badging` 的 versionName/versionCode 符合 §5，且与 `externals/core/version.h` 一致 |

## 11. 待决问题

| ID | 问题 | 建议 |
| --- | --- | --- |
| Q6 | 是否要在旧包里补一个「导出身份 + 网络配置」的功能，帮存量用户迁到新包？ | 先不做。跨应用私有目录在 Android 11+ 不可读，只能人工导出/导入；要做就得在旧包发一版 1.0.11，成本不高但会暂停主线 |
| Q7 | `:data` 是否也承担 JNI 之外的核心状态（如 node 状态缓存）？ | 不。`:data` 只管持久化；节点运行时状态留在 service 层的 Flow 里 |

## 12. 文档约定

- 计划文档：`docs/agent/plan/<topic>.md`（文件名不带日期），文首必须有 `状态`、日期、基线提交；重大变更追加「变更记录」，不覆盖历史。
- 事实与验证：写进 `docs/agent/architecture.md`，只记事实与命令输出。
- 硬约束（包名、版本号规则、minSdk、子模块纪律）只写在 README 的「硬约束」小节，计划与笔记引用而不复述。
- 重构前后逐项对照见 [`../architecture.md`](../architecture.md)。
- 详细规范见 [`docs/agent/README.md`](../README.md)。

## 13. 执行记录（2026-10-19）

| 里程碑 | 状态 | 证据 |
| --- | --- | --- |
| M0 基线固化 | 完成 | symlink 改显式 `sourceSets`；Gradle 9.7.1；`local.properties` |
| M1 核心 1.16.2 | 完成 | 子模块 `fc5c3ec`；`core/src/main/cpp/CMakeLists.txt`；两 ABI 编译；`readelf` LOAD `Align=0x4000` |
| M2 包名/构建/数据层 | 完成 | `net.sumitm.zotiera`；AGP 9.4.1；Room schema v1；`.pr` 与 release 双通道签名 |
| M3 Kotlin 业务层 | 部分 | 事件类全部迁 Kotlin；`service/`、`util/` 仍为 Java |
| M4 Compose UI | 部分 | 主 Activity/网络/加入/Peers/设置已迁；详情页与月亮页仍为 Fragment + 13 个 layout |
| M5 Legacy Central | 挂起 | 未动 |
| M6 CI 与发布 | 完成 | 六个 workflow；版本计算脚本本地验证通过 |

尚未完成（下一轮直接接续）：

1. `service/*`（含 1063 行 `ZeroTierOneService`）与 `util/*` 迁 Kotlin，EventBus → `SharedFlow`。
2. 网络详情页、月亮页迁 Compose，删除剩余 13 个 layout 与 `SingleFragmentActivity`。
3. 设置项从 SharedPreferences 迁 DataStore（当前仍用旧 key，行为未变）。
4. 真机回归：VPN 连通、DNS、多播、planet、moon、16KB 设备安装。
