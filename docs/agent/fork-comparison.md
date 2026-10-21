# 与上游 ZerotierFix 的差异

- 状态：事实文档
- 更新日期：2026-10-21
- 对比对象：`kaaass/ZerotierFix` `0373a58`（release 1.0.10，2023-09-17）
- 事实来源：本地 `ZerotierFix/` 副本、其 GitHub release 元数据、[`architecture.md`](architecture.md)

两者同源于官方 ZeroTier Android 客户端的反编译修补版。本仓库换掉了引擎、数据层与整套 UI，
能力面更大，兼容面更窄。

## 1. 版本与构建

| 项 | ZerotierFix 1.0.10 | Zotiera |
| --- | --- | --- |
| ZeroTier 引擎 | 1.12.2（fork 子模块 `d028a247`） | 1.16.2（上游 `fc5c3ec`） |
| versionName | `1.0.10` | `1.16.2-<revision>` |
| 包名 | `net.kaaass.zerotierfix` | `net.sumitm.zotiera`（`.pr` 通道 `net.sumitm.zotiera.pr`） |
| compile / target / minSdk | 33 / 33 / 19 | 37 / 36 / 30 |
| Gradle / AGP / JDK | 7.5 / 7.4.2 / 11 | 9.7.1 / 9.4.1 / 21 |
| ABI | 未收敛，默认四个 | `arm64-v8a` + `x86_64` |
| 原生页对齐 | 4KB | 16KB |
| 数据层 | GreenDAO，schema 22 | Room，schema 6 |
| UI | Fragment + XML layout + Material 2 | 单 Activity + Compose（Miuix / Material 3） |
| 签名 | CI 后置签名 | 双通道：`.pr` 固定 keystore / 正式包 `ANDROID_*` |
| 许可 | GPL-2.0 | AGPL-3.0-or-later；核心 MPL-2.0 |

## 2. 能力差异

| 能力 | ZerotierFix 1.0.10 | Zotiera |
| --- | --- | --- |
| 多网络同时加入与连接 | 单网络 | 同一实例内多网络 |
| 多行星 / 多身份 / 实例切换 | 单身份 + 一个自定义 planet | 多条行星与身份，按实例切换 |
| 自定义行星 | 文件与 URL | 文件与 URL，带别名、默认项、启用开关 |
| moon 轨道 | 有 | 有，标注弃用并可导出 `.moon` |
| Peers 列表 | 有 | 有（角色、版本、延迟、首选路径） |
| DNS 三模式 | 有（加入网络时选择） | 有；自定义服务器无编辑界面 |
| 全隧道开关 | 有 | 有 |
| 分应用放行 | 硬编码放行 Play Store | 同左，无界面 |
| 身份导入 / 导出 | 无 | zip 导入与导出 |
| 应用内语言切换 | 无（跟随系统） | 有，且 API 33+ 可写进系统「应用语言」 |
| 界面风格 | Material 2 一套 | Miuix（默认）/ Material 3 两套 |
| 深色 / 动态取色 | 深色 | 深浅色 + Android 12 动态取色 |
| 从最近任务隐藏 | 无 | 有 |
| 16KB 页对齐 | 无 | 有 |
| targetSdk 36（Android 16 行为） | 无 | 有 |

## 3. 兼容性差异

| 项 | 影响 |
| --- | --- |
| 系统版本 | minSdk 19 → 30：Android 10 及以下无法安装 |
| CPU 架构 | 只出 64 位：`armeabi-v7a` / `x86` 设备无法安装 |
| 覆盖安装 | 包名不同，不能覆盖升级；两版可共存但数据互不相通 |
| 节点身份 | 新包新身份，节点 ID 会变，需要在控制器重新授权 |
| 旧数据 | Android 11+ 不能跨应用读私有目录，网络/身份/moon 只能人工导出导入 |
| 引擎行为 | 1.12.2 → 1.16.2：MTU 下限 1400 → 510、metrics 默认关闭、moon 在上游被标弃用 |
| 上游生态 | 上游的更新、issue、插件经验不直接适用，本仓库独立维护 |

## 4. 包体

| 项 | 数值 |
| --- | --- |
| 上游 release `app-release.apk` | 8.3 MB |
| 本仓库单 ABI（arm64-v8a / x86_64） | 约 60 MB |
| 本仓库双 ABI（all） | 约 62 MB |

差异来源：Compose + Miuix + Material 3 两套 UI、`material-icons-extended`、`minifyEnabled false`（未开 R8）。
代价是下载与安装占用明显更大；收敛计划见 [`plan/init-refactor.md`](plan/init-refactor.md) 的 R3。

## 5. 适用场景

- 用 Android 11+ 的 64 位设备、需要多网络/多实例/新引擎：用本仓库。
- 还在 32 位或 Android 10 及以下、要原地升级旧数据：只能用上游 1.0.10。
