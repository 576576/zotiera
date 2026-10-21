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
| 2026-10-21 | v8 | R1 与 R2 合并为一个里程碑：语言、事件、并发同一批换完，方案见 [`kotlin-migration.md`](kotlin-migration.md) |

## 现状

- `app/src/main/java` 还有 22 个文件（工具类 9、服务 11、`DNSMode` 1、Application/Receiver 2）。
- 事件总线仍是 EventBus 3.2.0（服务、磁贴、ViewModel、三个页面共 6 处）。
- 旧 UI 依赖仍挂在 `app/build.gradle`；`minifyEnabled false`，单 ABI 包约 38MB。

## R1+R2 服务层重写（Kotlin + Flow + 协程）

`app/src/main/java` 22 个文件迁 Kotlin；同时把 EventBus 换成 `NodeState` 的 `StateFlow`、命令换成 `Intent action`、
裸 `Thread` 换成协程。两者合并是因为改的是同一批方法，分开做等于同一段代码改两遍。

分六批：工具层 → 数据层 → 命令与状态骨架 → `TunTapAdapter` → `ZeroTierOneService` → Application/Receiver。
拆分方式、事件去留、行为变化与每批回归项见 [`kotlin-migration.md`](kotlin-migration.md)。

验收：`find app/src/main/java -name '*.java' | wc -l` 为 0；`grep -r "greenrobot" app` 无输出；
两版 ABI 构建通过；加入/上线/切实例/离开/关隧道全链路在模拟器上跑通。

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
