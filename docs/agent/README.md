# agent 文档

本目录存放本仓库的协作者约定、重构计划与审计记录。面向人类与自动化协作者，要求可追溯：结论必须能追到命令、文件或提交。

`zotiera` 是 ZeroTier Android 客户端（源自官方客户端反编译，经 `kaaass/ZerotierFix` 修补，现更名 Zotiera，包名 `net.sumitm.zotiera`）。它把 ZeroTier 核心（C++，`externals/core` 子模块）编译成 JNI 库，由 `:core` 模块暴露 `com.zerotier.sdk.*`，`:app` 用 `VpnService` 接管流量。

## 结构

```
docs/agent/
├── README.md              本文件：约定、硬约束、索引
├── init-baseline-diff.md  重构前后基线对比（参考，不是计划）
├── plan/                  计划（前瞻、可变更）
│   └── init-refactor.md   核心升级 + Kotlin/Compose（Central 挂起）
└── notes/                 审计与验证（只记事实）
    └── baseline-audit.md  现状基线审计
```

仓库根与代码目录下不放 agent 文档，全部收敛到这里。

## 硬约束

1. **包名 `net.sumitm.zotiera` 不再改**：`applicationId` 与 `namespace` 必须一致。改包名等于新应用，老用户数据无法继承。
2. **`com.zerotier.sdk` 包名与类名不可动**：JNI 符号（`Java_com_zerotier_sdk_Node_*`）与之绑死，改名等于重写整套 JNI。
3. **版本号以引擎为主**：`versionName = "<engine>-<revision>"`（如 `1.16.2-1`），`versionCode = major*1e6 + minor*1e4 + patch*100 + revision`。引擎版本唯一来源是 `externals/core/version.h`。
4. **minSdk 30 / targetSdk 36 / compileSdk 37 是下限**：不为更老设备引入兼容分支，不写 `SDK_INT < 30` 的兜底。
5. **ABI 只保留 64 位**：`arm64-v8a` + `x86_64`。不加回 `armeabi-v7a` / `x86`。
6. **签名分两条通道**：`.pr` 通道（appId `net.sumitm.zotiera.pr`、应用名 `Zotiera PR`）用仓库内固定 keystore，只用于测试，**不得分发**；正式包只认 4 个 `ANDROID_*` 环境变量，缺变量必须构建失败。
7. **Room schema 可自由设计**：不沿用旧 GreenDAO 表名列名；任何 schema 变更必须带迁移，禁止 `fallbackToDestructiveMigration`。
8. **不动 `externals/core` 的工作区**：需要改核心源码时走 fork + 最小补丁分支，不在子模块里留未提交改动。
9. **升级核心必须逐项回归**：16KB 页对齐、MTU 下限、双栈、多播、自定义 planet、moon、peers 列表，缺一项都不算完成。
10. **一次只改一个变量**：一项改动只解决一个里程碑任务；顺手重构会掩盖回归来源。
11. **提交前跑通构建**：涉及 `:core` 或子模块时两个 ABI 都要过。网络行为只能真机验证，编译通过不等于功能可用。

## 构建

```sh
git submodule update --init --recursive   # 缺了核心，:core 直接失败
./gradlew :app:assembleDebug              # 本地开发
./gradlew :app:assemblePr                 # 测试通道（.pr 包名 + 固定签名）
```

Android SDK 必需（compileSdk 37）；NDK 固定 `28.2.13676358`，CMake 由 AGP 提供。`assembleRelease` 需要 4 个 `ANDROID_*` 环境变量（或 `local.properties` 里的本地签名配置）。

## 文档约定

**命名**

- 文件名不带日期：`plan/<topic>.md`、`notes/<topic>.md`、根目录 `<topic>.md`。日期写在正文的「建立日期」「变更记录」里。
- 计划放 `plan/`，只记事实的审计与验证放 `notes/`，既非计划也非审计的参考材料（如基线对比）放本目录根。

**计划**

- 一个主题一份。
- 文首必带：状态、建立日期、事实依据链接、基线提交。
- 状态取值：`草案` / `决策已确认` / `执行中` / `完成` / `废弃`。
- 历史不覆盖：结论变化时追加「变更记录」，写清改了什么、为什么、影响哪些里程碑。

**审计与验证**

- 只写观察到的事实与命令输出，推测放在「待确认」小节并标注。
- 每条结论尽量带可复现命令。

**文档与代码对应**

- 里程碑完成后回填计划状态与出口条件，真机验证结果落到 `notes/`。
- 计划里引用的路径、版本号、提交哈希必须指向真实存在的对象，不写占位符。

## 当前文档

| 文档 | 状态 | 内容 |
| --- | --- | --- |
| [plan/init-refactor.md](plan/init-refactor.md) | 决策已确认，待开工 | 核心 1.12.2→1.16.2；Java→Kotlin + Compose/M3；Legacy Central 挂起 |
| [init-baseline-diff.md](init-baseline-diff.md) | 参考 | 重构前后的逐项对比、不变项、用户可感知差异 |
| [notes/baseline-audit.md](notes/baseline-audit.md) | 事实记录 | 现状审计与证据 |
