# Zotiera 与 ZerotierFix 的差异

- 状态：事实文档，固定在这一版，后续版本不再更新
- ZerotierFix 基准：`kaaass/ZerotierFix` `0373a58`（release 1.0.10，2023-09-17）
- Zotiera 基准：`576576/zotiera` `e77dbcd`（release 1.16.2，2026-10-22）

## 1. 版本与构建

| 项 | ZerotierFix 1.0.10 | Zotiera 1.16.2 |
| --- | --- | --- |
| 包名 | `net.kaaass.zerotierfix` | `net.sumitm.zotiera` |
| 版本 | `1.0.10` | `1.16.2-2` |
| ZeroTier 引擎 | `1.12.2`（fork 子模块 `d028a247`） | `1.16.2`（上游 `fc5c3ec`） |
| 实现语言 | Java（100%） | Kotlin（99.2%） |
| compile / target / minSdk | 33 / 33 / 19 | 37 / 36 / 30 |
| Gradle / AGP / JDK | 7.5 / 7.4.2 / 11 | 9.7.1 / 9.4.1 / 21 |
| ABI | `armeabi-v7a` `x86` + 同右 | `arm64-v8a` `x86_64` |
| 原生页对齐 | 4KB | 16KB |
| 数据层 | GreenDAO，schema 22 | Room，schema 6 |
| 并发与状态 | `Thread` / `Handler` + EventBus 事件总线 | Kotlin 协程 + Flow（`StateFlow`），服务与界面之间是命令与状态流 |
| 线程约束 | 主线程可直接查库 | 数据库与文件读写限定在 IO 调度器，主线程不碰磁盘 |
| UI | Fragment + XML layout + Material 2 | 单 Activity + Compose（Miuix / Material 3） |
| 签名 | CI 后置签名 | CI 后置签名 + `alpha`通道固定公开签名       |
| 包大小 | 8.3 MB | 7.5 MB（单 ABI 5.5 MB） |
| 许可 | GPL-2.0 | AGPL-3.0-or-later；核心 MPL-2.0 |

## 2. 能力差异

| 能力 | ZerotierFix 1.0.10 | Zotiera |
| --- | --- | --- |
| 多网络同时加入与连接 | 单网络 | 同时连接多网络 |
| 多行星 / 多身份 / 实例切换 | 单身份 + 单自定义行星 | 多条行星与身份，按实例切换 |
| 自定义行星 | 文件与 URL | 文件与 URL，带别名、默认项、启用开关 |
| moon 轨道 | 有 | 有，标注弃用并可导出 `.moon` |
| Peers 列表 | 有 | 有（角色、版本、延迟、首选路径） |
| DNS 三模式 | 有（加入网络时选择） | 有；自定义服务器无编辑界面 |
| 全隧道开关 | 有 | 有 |
| 分应用放行 | 硬编码放行 Play Store | 同左，无界面 |
| 身份导入 / 导出 | 无 | 有 |
| 应用内语言切换 | 无（跟随系统） | 有，且 API 33+ 可写进系统「应用语言」 |
| 界面风格 | Material 2 一套 | Miuix（默认）/ Material 3 两套 |
| 深色 / 动态取色 | 深色 | 深浅色 + Android 12 动态取色 |
| 从最近任务隐藏 | 无 | 有 |
| 16KB 页对齐 | 无 | 有 |

## 3. 兼容性差异

| 项 | 影响 |
| --- | --- |
| 系统版本 | minSdk 19 → 30：Android 10 及以下无法安装 |
| CPU 架构 | 只出 64 位：`armeabi-v7a` / `x86` 设备无法安装 |
| 引擎行为 | 1.12.2 → 1.16.2：MTU 下限 1400 → 510、metrics 默认关闭、moon 在上游被标弃用 |
