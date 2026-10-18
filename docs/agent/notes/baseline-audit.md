# 基线审计（2026-10-19）

重构计划 `../plan/init-refactor.md` 的事实来源。所有结论都附可复现的命令或文件路径。

## 1. 仓库拓扑

| 路径 | 内容 | 用途 |
| --- | --- | --- |
| `zotiera/` | fork 主体，`origin` = `github.com/576576/ZerotierFix`，`upstream` = `kaaass/ZerotierFix` | 重构对象 |
| `ZerotierFix/` | `kaaass/ZerotierFix` 干净副本，HEAD `0373a58` | 对照上游差异 |
| `zerotier-central-android/` | Flutter 版 Legacy Central 客户端 | 节点管理能力参考 |
| `ZeroTierOne/` | 上游全量克隆（master，HEAD 超前 tag 256 个提交） | 1.16.2 源码查证 |

`zotiera` 当前 HEAD `0373a58`，工作区干净。

## 2. zotiera 基线

| 项目 | 现状 |
| --- | --- |
| 包名 | `net.kaaass.zerotierfix`（应用名 `Zerotier Fix`）；已决定改为 `net.sumitm.zotiera` |
| 模块 | `:app` + `:core`（`:core` 仅承载 JNI 与 `com.zerotier.sdk` 类） |
| 构建 | Gradle 7.5、AGP 7.4.2 |
| SDK | compileSdk / targetSdk 33、minSdk 19 |
| Java | source/target 11，core 模块 8 |
| DB | GreenDAO 3.3.0（Gradle 插件），schemaVersion 22 |
| 事件 | EventBus 3.2.0 |
| UI | Material 1.9.0 + AppCompat + Navigation 2.5.3 + Preference |
| 代码量 | `app/src/main/java` 76 个 Java 文件 / 7386 行；0 个 Kotlin 文件 |
| 资源 | 18 个 layout；strings 中英各 153/156 行 |
| 测试 | `app/src/test` 1 个、`app/src/androidTest` 1 个 |
| 版本 | versionCode 14 / versionName 1.0.10 |

### 2.1 关键障碍：`core/src/main/java` 是 symlink

```
$ git ls-files -s core/src/main/java
120000 bdeafe96f16f29a6367d9c0916ceda9945b43153 0  core/src/main/java
$ git config --get core.symlinks
false
```

仓库以 symlink 指向 `../../../externals/core/java/src`。本机 `core.symlinks=false`，检出后它是个 32 字节的纯文本文件，Gradle 的 `src/main/java` 目录为空 —— 本地 Windows 构建拿不到 `com.zerotier.sdk.*`，只有 Linux CI 正常。

结论：core 模块的 Java 源目录必须改为显式 `sourceSets` 配置，不能依赖 symlink。

### 2.2 安全与合规观察

| 项 | 现状 | 影响 |
| --- | --- | --- |
| `android:allowBackup` | `true` | `identity.secret`（节点私钥）落在 `filesDir`，可被 adb/云备份带出 |
| `android:usesCleartextTraffic` | `true` | 自定义 planet URL 允许 http，但也放开全部明文 |
| 签名配置 | 无 `signingConfigs`，CI 用 `sign-android-release` 后置签名 | 本地 `assembleRelease` 产出不可安装，验证链路割裂 |
| 通知 | 声明 `POST_NOTIFICATIONS` 但无处申请 | Android 13+ 首次运行可能无通知，前台服务被降级 |

身份文件路径为 `/data/data/net.kaaass.zerotierfix/files/identity.secret`。Android 11+ 禁止跨应用读取其他应用私有目录，因此换包名后**不存在自动迁移路径**，只能由用户显式导出导入。

## 3. core 子模块（1.12.2）

| 项目 | 值 |
| --- | --- |
| 路径 | `externals/core` → `github.com/kaaass/ZeroTierOne.git` |
| HEAD | `d028a247`（detached，分支 `jni/1.12.2`） |
| 版本 | `version.h` = 1.12.2 / build 0 |
| 构建 | `core/build.gradle` → `../externals/core/java/CMakeLists.txt`，CMake 3.22.1 |

与上游 `1.12.2`（`327eb901`）的差异只有 2 个文件：

```
java/CMakeLists.txt    | 36 ++++++++----------------------------
java/jni/ZT_jniutils.h | 48 ++++++++++++++++++++++++++++++++++++++++--------
```

- `CMakeLists.txt`：改为 `file(GLOB ../node/*.cpp)`、补 `ext/` 与 prometheus include、链接 `log`。
- `ZT_jniutils.h`：`GETENV` 宏换成 `JNIEnvGuard`（必要时 AttachCurrentThread），修复 ICMPv6 NS 帧触发的 JNI 空指针崩溃。

## 4. 上游 1.16.2 查证

| 项目 | 值 |
| --- | --- |
| tag | `1.16.2` |
| commit | `fc5c3ec22090b5b2a0f274e863651fe9ca489bf4`（2026-05-27） |
| 发布 | 2026-05-28，确为当前 latest stable |

### 4.1 许可证变更（1.16.0 起）

- `node/`、`include/`、`service/`、`osdep/` → MPL-2.0。
- `controller/` → 商业 source-available 许可，默认构建不再包含。
- `make ZT_NONFREE=1` 才构建非 MPL 部分。

JNI 只用到 `node/` + `include/` + `osdep/OSUtils.cpp`，不涉及 controller，分发不受影响；需要在 About 页与仓库 LICENSE 说明中保留 MPL 声明。

### 4.2 源码与 JNI 差异

| 项 | 结论 |
| --- | --- |
| `java/src/com/zerotier/sdk/*` | 上游 1.16.2 的 8 个核心类（Node / NodeStatus / VirtualNetworkConfig / VirtualNetworkDNS / Version / Peer / PeerPhysicalPath / Event）与本地 1.12.2 子模块文件**逐行无差异**：`diff --strip-trailing-cr -q` 全部相同，`cmp` 有差异仅因本地检出为 CRLF |
| `java/jni/ZT_jniutils.cpp/.h`、`ZT_jnicache.cpp` | 与 1.12.2 相同（上游未采用 fork 的 `JNIEnvGuard`） |
| `java/jni/com_zerotierone_sdk_Node.cpp` | 有改动：`ZT_Node_new` 新增 `ZT_Node_Config *` 参数；新增 `ScopedJNIThreadAttacher`（等价于 fork 的 ICMPv6 修复） |
| `node/` | 新增 `ECC.cpp`、`PacketMultiplexer.cpp`；删除 `C25519.cpp`、`Defaults.cpp`、`Dictionary.cpp` |
| `osdep/` | 新增 `ExtOsdep.cpp`，仅被 `service/` 与 `osdep/Binder.hpp` 引用，JNI 不需要 |
| `include/ZeroTierOne.h` | `ZT_MIN_PHYSMTU` 1400→510（低带宽模式）；`ZT_MAX_NETWORK_SPECIALISTS` 256→512；`ZT_Node_Config` 新增 `lowBandwidthMode` / `encryptedHelloEnabled` |

结论：**App 侧 JNI 调用面无需改动**，升级集中在核心源码与 CMake。

### 4.3 上游 `java/CMakeLists.txt` 已失效

```
$ git show 1.16.2:java/CMakeLists.txt | grep -oE '\.\./node/[A-Za-z0-9]+\.cpp' | ...
MISSING: ../node/C25519.cpp
MISSING: ../node/Defaults.cpp
MISSING: ../node/Dictionary.cpp
```

上游脚本仍按 1.12 的显式文件清单写死，引用了三个已删除文件，**在 1.16.2 上无法 configure**。因此不能直接复用上游 JNI CMake，必须自持源清单（glob 或按 `objects.mk` 生成）。

### 4.4 构建标准

- `make-linux.mk` 使用 `-std=c++17`；根 `CMakeLists.txt` 里仍残留 `-std=c++11`。
- `node/AES_aesni.cpp`、`AES_armcrypto.cpp` 分别受 `ZT_AES_AESNI` / `ZT_AES_NEON` 保护，跨 ABI glob 安全。
- `node/Metrics.cpp` 依赖 `ext/prometheus-cpp-lite-1.0`（1.16.2 仍在仓库内），include 路径需保留；1.16.1 起指标默认关闭。

## 5. 参考工程能力盘点（zerotier-central-android）

| 项 | 内容 |
| --- | --- |
| Base URL | `https://api.zerotier.com/api/v1` |
| 认证 | 头 `Authorization: token <token>`（Legacy Central 个人 token，非新 Central 的 service account） |
| 端点 | `GET /status`（校验）、`GET /network`、`GET /network/{id}/member`、`POST /network/{id}/member/{nodeId}` |
| 更新载荷 | 授权 `{"config":{"authorized":bool}}`；改名 `{"name":string}` |
| 速率 | Legacy 免费账户 20 req/s，串行请求间隔 60ms |
| Token 存储 | Android Keystore 支持的加密存储，登出即删 |
| 成员字段 | `nodeId` / `name` / `description` / `config.authorized` / `config.ipAssignments` / `physicalAddress` / `lastSeen` / `clientVersion` / `hidden` |
| 明确不做 | 路由、地址池、流规则等高级网络配置 |

对照本 App：`AppNode` 表已有本机 nodeId，可与 Central 成员列表做「本机」标记，这是本仓库独有的融合点。
