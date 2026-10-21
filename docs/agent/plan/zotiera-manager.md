# Zotiera Manager（独立的 Legacy Central 管理 App）

- 状态：挂起（本轮不做，独立 App）
- 建立日期：2026-10-20
- 事实依据：[`architecture.md`](../architecture.md) 的来源与能力清单

## 0. 一句话

Legacy Central 的成员管理**不在 Zotiera 里做**，单开一个不含 VPN 的 App：贴 token，管理
`my.zerotier.com` 老账号下的网络成员。本文件是那个 App 的计划，与 Zotiera 代码库无耦合。

## 1. 与 Zotiera 的边界

| 项 | Zotiera | Zotiera Manager |
| --- | --- | --- |
| 跑 VPN / 有节点身份 | 是 | **否**，只发 HTTP 请求 |
| 需要 VPN 权限 / 前台服务 | 是 | 否，只要 `INTERNET` |
| 包名 | `net.sumitm.zotiera` | 独立（建议 `net.sumitm.zotiera.manager`） |
| 代码共享 | — | 复制 `ui/component` 与 `ui/theme`，不反向依赖 Zotiera |

不放进 Zotiera 的理由：Central 管理需要长期存第三方 token、会调外部写接口，与「本地 VPN 客户端」
的权限面完全不同；放在一起会让主 App 的权限与隐私描述都变复杂。

## 2. 功能范围

| 项 | 做不做 | 说明 |
| --- | --- | --- |
| Legacy Central（`my.zerotier.com`，token） | **做** | 老账号只有这一套 API |
| 新 Central（service account / 另一套 API） | 不做 | 与 Legacy 完全两套，token 不通用 |
| 成员：列表、搜索、授权、取消授权、改名 | **做** | 与现有 Flutter 客户端对齐 |
| 成员：地址、最后在线、客户端版本、物理地址 | **做** | 只读 |
| 网络：改名称 / 私有 / 路由 / IP 池 / flow rules | 不做 | 写网络配置，风险与界面量翻倍 |
| 成员删除 | 不做 | Legacy API 没有干净的删除语义，只做取消授权 |

## 3. API 事实

参考实现：`zerotier-central-android/lib/services/central_api.dart`（Flutter，已跑通）。

- base：`https://api.zerotier.com/api/v1`
- 鉴权：`Authorization: token <TOKEN>`、`Accept: application/json`
- 超时：连接 10s、读 20s；不跟随重定向

| 方法 | 路径 | 用途 | 备注 |
| --- | --- | --- | --- |
| GET | `/status` | 校验 token | 登录页先验，失败不入库 |
| GET | `/network` | 列网络 | 元素含 `id`（16 hex）、`name`、`description`、`config` |
| GET | `/network/{id}/member` | 列成员 | 含 `nodeId`（10 hex）、`config.authorized`、`config.ipAssignments`、`physicalAddress`、`lastSeen`、`clientVersion` |
| POST | `/network/{id}/member/{nodeId}` | 改成员 | 体：`{"name": ...}` 或 `{"config": {"authorized": true}}` |

错误语义与限速：

| 状态 | 含义 | 处理 |
| --- | --- | --- |
| 401 / 403 | token 无效或无权限 | 回登录页，清掉已存 token |
| 404 | 网络 / 成员不存在 | 「已被删除或无权访问」 |
| 429 | 限速 | 提示稍后再试；老免费账号 20 req/s，串行 + 间隔 60ms |

`lastSeen` / `physicalAddress` 是控制器的临时观测，可能缺；缺了显示「未知」。

## 4. 页面

| 路由 | 页面 | 内容 |
| --- | --- | --- |
| `signin` | 登录 | token 输入 + 校验 + 「去哪申请 token」说明 |
| `networks` | 网络列表 | 名称、已授权 / 总成员数；下拉刷新 |
| `network/{id}` | 成员列表 | 待授权在上、已授权在下；每行开关直接改授权 |
| `member/{net}/{node}` | 成员详情 | 改名、授权开关、地址、物理地址、最后在线、客户端版本 |

交互：

- 取消授权需二次确认；授权直接生效。
- 开关点下先置灰 + 转圈，失败回滚并提示。
- 空态与错误态分开。

## 5. 技术选型

| 项 | 选择 | 理由 |
| --- | --- | --- |
| UI | Kotlin + Compose，Miuix / Material 双风格 | 与 Zotiera 同一套观感，组件直接拷 `ui/component` |
| HTTP | `HttpURLConnection` + `org.json` | 四个调用，不引 OkHttp / Retrofit |
| token | Keystore AES-GCM 加密后写 SharedPreferences | 不依赖已 deprecated 的 security-crypto |
| 最低版本 | Android 11（API 30），仅 64 位 | 与 Zotiera 保持一致 |

## 6. 步骤

| 步 | 内容 | 验收 |
| --- | --- | --- |
| M1 | 建仓 + 拷贝 UI 骨架（主题、组件、导航壳） | 空壳能跑，双风格切换正常 |
| M2 | `CentralClient` + token 存储 + 登录页 | 好 token 能过、坏 token 被拒；杀进程后仍记得 |
| M3 | 网络列表 | 名称与成员数正确；断网有错误态 |
| M4 | 成员列表（只读） | 分组、地址、最后在线正确 |
| M5 | 授权 / 取消授权 | 网页端状态同步；失败回滚 |
| M6 | 改名 | 列表与详情同步 |

## 7. 验收标准

- 真机走一遍：登录 → 网络 → 成员 → 授权一个待授权成员 → 网页确认 → 取消授权 → 网页确认。
- token 无效 / 断网 / 网络被删：三种错误都有可读提示，不闪退。
- 双风格 × 明暗逐页看一遍。
- token 不出现在 logcat。

## 8. 风险

| 风险 | 处理 |
| --- | --- |
| Legacy Central 哪天关停 | 整个 App 不可用；登录页直接说明，不做降级 |
| 429 限速 | 串行 + 间隔；用户手动刷新不叠并发 |
| token 泄露 | 加密存储；界面只显示前后各 4 位；退出即删 |
| 与现有 Flutter 客户端功能重叠 | 先用它做对照验收（同样操作在两边结果一致），再决定是否下架 |
