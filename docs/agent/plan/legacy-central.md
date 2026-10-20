# Legacy Central 管理页

## 0. 一句话

给 App 加一个可选的「Legacy Central 管理」入口：贴一串 token，就能管理 my.zerotier.com
老账号下的网络成员（看列表、授权 / 取消授权、改名、看地址与最后在线），不碰网络本身的配置。

## 1. 边界

| 项 | 做不做 | 说明 |
| --- | --- | --- |
| Legacy Central（`my.zerotier.com`，token 鉴权） | **做** | 老账号只有这一套 API |
| 新 Central（service account / 另一套 API） | 不做 | 与 Legacy 完全两套，token 也不通用 |
| 成员：列表、搜索、授权、取消授权、改名 | **做** | 参考实现（`zerotier-central-android`）已验证过这几个调用 |
| 成员：地址、最后在线、客户端版本、物理地址（只读） | **做** | 与授权决策直接相关 |
| 网络：改名称 / 私有 / 路由 / IP 池 / flow rules | 不做 | 写网络配置，风险与界面量都翻倍 |
| 成员删除 | 不做 | Legacy API 没有干净的删除语义，先只做取消授权 |

## 2. API 事实

参考实现：`zerotier-central-android/lib/services/central_api.dart`（Flutter，已跑通）。

- base：`https://api.zerotier.com/api/v1`
- 鉴权：请求头 `Authorization: token <TOKEN>`，`Accept: application/json`
- 超时：连接 10s、读 20s；参考实现关掉了重定向跟随

| 方法 | 路径 | 用途 | 备注 |
| --- | --- | --- | --- |
| GET | `/status` | 校验 token | 登录页先用它验，失败就不用存 |
| GET | `/network` | 列账号下的网络 | 数组，元素带 `id`（16 位 hex）、`name`、`description`、`config` |
| GET | `/network/{id}/member` | 列成员 | 数组，元素带 `nodeId`（10 位 hex）、`name`、`config.authorized`、`config.ipAssignments`、`physicalAddress`、`lastSeen`、`clientVersion` |
| POST | `/network/{id}/member/{nodeId}` | 改成员 | 体：`{"name": "..."}` 或 `{"config": {"authorized": true}}`；返回更新后的成员，也可能返回空体 |

错误语义：

| 状态 | 含义 | 界面表现 |
| --- | --- | --- |
| 401 / 403 | token 无效或无权限 | 回到登录页并标红提示，清掉已存 token |
| 404 | 网络或成员不存在 | 「已被删除或无权访问」，返回上一级 |
| 429 | 触发限速 | 提示稍后再试 |

限速：老免费账号 20 请求/秒。参考实现拉账号快照时**串行**发请求并间隔 60ms，我们照做。

`lastSeen` 与 `physicalAddress` 是控制器的临时观测，随时可能缺；缺了显示「未知」，不要当成 0。

## 3. 数据模型

只做展示与回写，不落库（每次进页面拉一次，下拉刷新）：

```kotlin
data class CentralNetwork(
    val id: String,            // 16 位 hex
    val name: String,          // 空则用 "未命名网络"
    val description: String?,
    val authorized: Int?,      // 由成员列表算或 config 里取
    val total: Int?,
)

data class CentralMember(
    val networkId: String,
    val nodeId: String,        // 10 位 hex
    val name: String?,
    val authorized: Boolean,
    val addresses: List<String>,
    val physicalAddress: String?,
    val lastSeen: Long?,       // 毫秒
    val clientVersion: String?,
)
```

`nodeId` 可能来自 `nodeId`、`config.id`，或旧形状里 `id` 的最后一段（`xxx-<nodeId>`），取到哪个用哪个。

## 4. 页面结构

入口放在设置页「管理 Planet / 管理 Moon」那一组的下一行：`管理 Legacy Central`，
summary 未登录时显示「未登录」，已登录显示「已连接」。

| 路由 | 页面 | 内容 |
| --- | --- | --- |
| `central` | 登录 / 账户 | token 输入 + 校验 + 说明（去哪申请 token）；已登录时显示「已连接 / 退出登录」 |
| `central/networks` | 网络列表 | 名称、成员数（已授权 / 总数）；下拉刷新 |
| `central/network/{id}` | 成员列表 | 分两组：待授权在上、已授权在下；每行开关直接改授权状态 |
| `central/member/{net}/{node}` | 成员详情 | 改名、授权开关、ZeroTier 地址、物理地址、最后在线、客户端版本 |

交互约定：

- 取消授权要二次确认（参考实现也这么做），授权直接生效。
- 开关点下去先本地置灰 + 转圈，返回后再落状态；失败回滚并提示。
- 列表空态与错误态分开：空列表是「这个网络还没有成员」，错误是「拉取失败 + 重试」。

## 5. 代码落点

```
data/src/main/kotlin/net/sumitm/zotiera/central/
├── CentralClient.kt        // HttpURLConnection + org.json，四个调用 + 错误映射
├── CentralModels.kt        // 上面两个 data class
└── CentralTokenStore.kt    // token 的存取
app/src/main/kotlin/net/sumitm/zotiera/ui/screens/central/
├── CentralLoginScreen.kt
├── CentralNetworkListScreen.kt
├── CentralMemberListScreen.kt
└── CentralMemberScreen.kt
```

- **不引新依赖**：`java.net.HttpURLConnection` + `org.json` 够用，也不给 APK 增体积。
- 网络调用放 `Dispatchers.IO`，页面用 `rememberCoroutineScope` + 局部状态；不新建 ViewModel，
  这一块没有跨页共享状态。
- UI 复用现有组件：`AppTopBar` / `AppListRow` / `AppRoleChip` / `PreferenceGroup` / `AppDialog`。
- 路由加在 `MainActivity` 的 `NavHost` 里，三个新页面都走 `ScreenSurface` + `popBackStack`。

### 5.1 token 存在哪

两个选项，落地前定一个：

| 方案 | 优点 | 代价 |
| --- | --- | --- |
| `androidx.security:security-crypto` 的 `EncryptedSharedPreferences` | 一行拿到 Keystore 支持的加密存储 | 库已标记 deprecated，但仍是主流做法 |
| 自己用 Keystore AES-GCM 加密后写 SharedPreferences | 不引依赖，不依赖 deprecated 库 | 要自己管 IV 与密钥失效后的兜底 |

倾向第二种：这个 App 现在一个多余依赖都没加，token 加密逻辑不到 60 行。

## 6. 步骤（每步可独立验收）

| 步 | 内容 | 验收 |
| --- | --- | --- |
| S1 | `CentralClient` + token 存储 + 登录页 | 好 token 能过、坏 token 被拒且不入库；杀进程后仍记得登录 |
| S2 | 网络列表 | 名称与成员数正确；断网有错误态与重试 |
| S3 | 成员列表（只读） | 待授权 / 已授权分组正确，地址与最后在线显示正确 |
| S4 | 授权 / 取消授权 | 点开关后网页端状态同步；取消授权有二次确认；失败回滚 |
| S5 | 改名 | 改名后列表与详情同步；空名字按「未命名」处理 |
| S6 | 用量提示（可选） | 已授权设备数达到 25 时给出提示（老账号额度，API 不返回） |

## 7. 验收标准

- 真机（不是模拟器网络）走一遍：登录 → 网络列表 → 成员列表 → 授权一个待授权成员 →
  在 my.zerotier.com 网页上确认已授权 → 取消授权 → 网页上确认已取消。
- 三种错误都有可读提示且不闪退：token 无效、断网、网络被删。
- 两种风格（Miuix / Material）× 明暗逐页看一遍，行尾控件左边线对齐。
- token 不出现在 logcat、不出现在导出的日志里。

## 8. 风险

| 风险 | 处理 |
| --- | --- |
| Legacy API 哪天关停 | 页面整体不可用；入口保留但明确提示「Legacy Central 不可用」，不做自动降级 |
| 429 限速 | 串行 + 60ms 间隔；用户手动刷新时不叠加并发 |
| token 泄露 | 加密存储；界面只显示前后各 4 位；退出登录即删除 |
| `lastSeen` / `physicalAddress` 缺失被误读成「离线」 | 缺失显示「未知」，与「很久以前」区分开 |
| 成员列表规模大（>200） | 先全量拉取 + 本地搜索，不做分页；超过 500 再考虑分页 |
