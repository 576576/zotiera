# Zotiera 重构计划（剩余）

- 状态：执行中
- 建立日期：2026-10-19
- 精简日期：2026-10-21
- 事实依据：[`architecture.md`](../architecture.md)
- 基线提交：`0373a58`（重构前）

M0 基线、M1 核心 1.16.2、M2 包名/构建/Room、M4 Compose UI 主体、M6 发布与 CI 已完成；M5 Legacy Central 移出到独立 App。
已完成部分的结论写在 [`architecture.md`](../architecture.md)，本文只保留还没做的。

## 变更记录

| 日期 | 版本 | 变更 |
| --- | --- | --- |
| 2026-10-19 | v1–v4 | 建立并按执行情况回填里程碑 |
| 2026-10-21 | v6 | 精简：已完成里程碑删除，只保留 R1–R3 |
| 2026-10-21 | v7 | R1 的拆分方案、每批回归项独立成 [`kotlin-migration.md`](kotlin-migration.md)，本文只留结论 |

## 现状

- `app/src/main/java` 还有 22 个文件（工具类 9、服务 11、`DNSMode` 1、Application/Receiver 2）。
- 事件总线仍是 EventBus 3.2.0（服务、磁贴、ViewModel、三个页面共 6 处）。
- 旧 UI 依赖仍挂在 `app/build.gradle`；`minifyEnabled false`，单 ABI 包约 38MB。

## R1 服务与工具层迁到 Kotlin

分五批：`util/*` + `DNSMode` → 服务数据层 → `TunTapAdapter`（拆 2 个文件）→ `ZeroTierOneService`（拆 4 个文件）
→ Application/Receiver。逐文件拆分、行范围与每批回归项见 [`kotlin-migration.md`](kotlin-migration.md)。

验收：`find app/src/main/java -name '*.java' | wc -l` 为 0；两个 ABI 构建通过；真机跑通加入网络、开关隧道、切换实例。

## R2 事件与并发收敛

| 项 | 现状 | 目标 |
| --- | --- | --- |
| 事件 | EventBus 3.2.0，`events/Events.kt` + 6 处发送/订阅 | `SharedFlow` / `StateFlow` |
| 线程 | 服务里的裸 `Thread`（组播扫描、UDP 收包） | `CoroutineScope(SupervisorJob + Dispatchers.IO)` |

必须成对改：先接上 Flow 通道、再撤 EventBus，避免双轨期丢事件。

验收：无 `EventBus` 引用；连接、断开、节点上线状态与现状一致。

## R3 依赖与包体清理

| 项 | 内容 |
| --- | --- |
| 旧依赖 | 确认无引用后移除 `appcompat`、`material 1.9`、`constraintlayout`、`navigation-fragment`、`recyclerview`、`swiperefreshlayout`、`legacy-support-v4` |
| R8 | `minifyEnabled false` → 开 R8 + `shrinkResources`，补 JNI / Room / EventBus 保留规则；当前单 ABI 约 38MB |

验收：依赖树无旧 UI 库；`assemblePr` 包体明显下降；UI 回归通过。

## 不做

- 重写 `ZeroTierOneService` 的逻辑，只迁移语言与并发原语。
- Legacy Central：独立 App（`ZotieraManager` 仓库）。
- moon 迁移：保留能力 + 弃用提示 + 导出，见 `architecture.md`。

## 风险

| 风险 | 处理 |
| --- | --- |
| 服务层迁移引入连接回归 | 每批单独提交，改完跑一次真机连接用例 |
| EventBus/Flow 双轨丢事件 | 成对改造，一次只切一类事件 |
| R8 破坏 JNI/Room 反射 | 先开 `shrinkResources`，再逐步开 R8 并补保留规则 |
