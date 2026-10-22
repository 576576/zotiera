# agent 文档

本目录存放本仓库的协作者约定、现状描述与未完成计划。面向人类与自动化协作者，要求可追溯：结论必须能追到命令、文件或提交。

`zotiera` 是 ZeroTier Android 客户端（源自官方客户端反编译，经 `kaaass/ZerotierFix` 修补，现更名 Zotiera，包名 `net.sumitm.zotiera`）。它把 ZeroTier 核心（C++，`externals/core` 子模块）编译成 JNI 库，由 `:core` 模块暴露 `com.zerotier.sdk.*`，`:app` 用 `VpnService` 接管流量。

## 结构

```
docs/agent/
├── README.md          本文件：约定、硬约束、索引
├── architecture.md    现状：模块、数据、实例、网络行为、界面（事实）
├── fork-comparison.md 与上游 ZerotierFix 的差异（能力、兼容性、包体）
└── plan/              未完成的计划
    └── coexist-mode.md
```

事实写进 `architecture.md`，计划写进 `plan/`；不设 `notes/`。仓库根与代码目录下不放 agent 文档。

## 硬约束

1. **包名 `net.sumitm.zotiera` 不再改**：`applicationId` 与 `namespace` 必须一致。改包名等于新应用，老用户数据无法继承。
2. **`com.zerotier.sdk` 包名与类名不可动**：JNI 符号（`Java_com_zerotier_sdk_Node_*`）与之绑死，改名等于重写整套 JNI。
3. **版本号以引擎为主**：`versionName = "<engine>-<revision>"`（如 `1.16.2-1`），`versionCode = major*1e6 + minor*1e4 + patch*100 + revision`。引擎版本唯一来源是 `externals/core/version.h`，修订号在 `version.txt`。
4. **minSdk 30 / targetSdk 36 / compileSdk 37 是下限**：不为更老设备引入兼容分支，不写 `SDK_INT < 30` 的兜底。
5. **ABI 只保留 64 位**：`arm64-v8a` + `x86_64`。不加回 `armeabi-v7a` / `x86`。
6. **签名分两条通道**：`.pr` 通道（appId `net.sumitm.zotiera.pr`、应用名 `Zotiera PR`）用仓库内固定 keystore，只用于测试，**不得分发**；正式包只认 4 个 `ANDROID_*` 环境变量，缺变量必须构建失败。
7. **Room schema 可自由设计**：不沿用旧 GreenDAO 表名列名。行为稳定前不做数据接续——schema 变更走 `fallbackToDestructiveMigration`，旧库与旧文件在首次启动直接丢弃；宣布稳定之后必须改成带迁移，并删掉这个兜底。
8. **不动 `externals/core` 的工作区**：需要改核心源码时走 fork + 最小补丁分支，不在子模块里留未提交改动。
9. **升级核心必须逐项回归**：16KB 页对齐、MTU 下限、双栈、多播、自定义 planet、moon、peers 列表，缺一项都不算完成。
10. **一次只改一个变量**：一项改动只解决一个里程碑任务；顺手重构会掩盖回归来源。
11. **提交前跑通构建**：涉及 `:core` 或子模块时两个 ABI 都要过。网络行为只能真机验证，编译通过不等于功能可用。
12. **页面层不许自绘控件**：`ui/screens/**` 里出现 `DropdownMenu(`、`AlertDialog(`、`Switch(`、`Modifier.border(` 这类底层控件，`UiConventionsTest` 直接失败；风格分支只允许待在 `ui/component/**`。
13. **本地出包不用等吩咐**：代码一改完就跑 `scripts/build-local.sh`，三种产物（arm64-v8a / x86_64 / all）拷进 `../zt-playground`，在交付说明里给出实际路径。

## 构建

```sh
git submodule update --init --recursive   # 缺了核心，:core 直接失败
./gradlew :app:assembleDebug              # 本地开发
./gradlew :app:assemblePr                 # 测试通道（.pr 包名 + 固定签名）
scripts/build-local.sh                    # 三种架构各出一份，拷到 ../zt-playground
```

`scripts/build-local.sh` 是本地出包的正规入口：版本号按 `release.yml` 的规则推（`<engine>-r<提交数>`），
产物名 `Zotiera-<版本>-<arch>.apk`，逐个校验包内 ABI 后拷到 `../zt-playground`（或 `--out` 指定的目录）。
默认走 `.pr` 测试签名，`-c release` 才用正式签名并且要求 4 个 `ANDROID_*` 变量齐全。

Android SDK 必需（compileSdk 37）；NDK 固定 `28.2.13676358`，CMake 由 AGP 提供。`assembleRelease` 需要 4 个 `ANDROID_*` 环境变量（或 `local.properties` 里的本地签名配置）。

## 文档约定

**命名**

- 文件名不带日期：`architecture.md`、`plan/<topic>.md`。日期写在正文的「建立日期」「更新日期」「变更记录」里。
- 一个主题一份：事实进 `architecture.md`，未完成的工作进 `plan/`。

**计划**

- 文首必带：状态、建立日期、事实依据、基线提交。
- 状态取值：`草案` / `决策已确认` / `执行中` / `挂起` / `完成` / `废弃`。
- 计划完成或被放弃后移出 `plan/`：能沉淀的事实并入 `architecture.md`，没有保留价值的直接删除。
- 历史不覆盖：结论变化时追加「变更记录」，写清改了什么、为什么、影响哪些里程碑。

**事实**

- `architecture.md` 只写当前代码的事实与可复现命令/路径；推测放「待确认」并标注。
- 代码变更后同步回填对应小节；不再单设审计目录。

**文档与代码对应**

- 计划里引用的路径、版本号、提交哈希必须指向真实存在的对象，不写占位符。
- 里程碑完成后回填计划状态；已完成计划的结论进 `architecture.md`。

## 当前文档

| 文档 | 状态 | 内容 |
| --- | --- | --- |
| [architecture.md](architecture.md) | 事实 | 模块、工具链、引擎与 JNI、数据层、实例、隧道、界面、能力清单、不变项 |
| [fork-comparison.md](fork-comparison.md) | 事实 | 与上游 ZerotierFix 的逐项差异 |
| [plan/coexist-mode.md](plan/coexist-mode.md) | 搁置 | 与其它 VPN 共存：root 模式 tun + 策略路由，设计完成，未排期 |
