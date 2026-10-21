# 空状态占位

- 状态：草案
- 建立日期：2026-10-21
- 基线提交：`9578f7a`
- 事实依据：
  - 概览页：`app/src/main/kotlin/net/sumitm/zotiera/ui/screens/OverviewScreen.kt`，`state.networks.isEmpty()` 时左上角画 `R.string.no_data`
  - 对等节点页：`app/src/main/kotlin/net/sumitm/zotiera/ui/screens/PeersScreen.kt`，`state.peers.isEmpty()` 时左上角画 `peer_no_connection` / `wait_to_fetch`
  - LSPosed 实现：`LSPosed/app/src/main/java/org/lsposed/manager/ui/widget/EmptyStateRecyclerView.java`；文案在 `LSPosed/app/src/main/res/values/strings.xml` 的 `list_empty`
  - Miuix 0.9.4：`miuix-ui-android-0.9.4-sources.jar` 内没有空状态组件（`basic/` 下无 Empty 相关文件）

## 现状

| 页面 | 触发条件 | 现在显示 | 位置 |
| --- | --- | --- | --- |
| 概览 | 一条网络都没有加入 | `无数据` / `No data` | 设备卡下方，左上角 |
| 对等节点 | `peers` 为空 | `无连接` / `等待获取` | 顶栏下方，左上角 |

两处都是 13sp 的说明性文字，贴在内容区左上，与卡片列表的节奏不一致；列表拉长后这行字也不会跟着居中。

## LSPosed 的做法

`EmptyStateRecyclerView` 在「已加载且条目为 0」时，把 `list_empty` 画到控件正中：

- 文案：`¯\_(ツ)_/¯\nNothing here`，中文为 `¯\_(ツ)_/¯\n空空如也`
- 字号 16sp，颜色取 `android.R.attr.textColorSecondary`
- `StaticLayout` 水平居中换行，垂直居中（`(height >> 1) - (textLayout.height >> 1)`）

它不是一个可复用组件，而是 `RecyclerView` 的子类在 `dispatchDraw` 里画字。Compose 侧没有对等实现，
Miuix 也没有现成的空状态控件，所以「有没有现成组件」的答案是：没有，但用 `Text` 复刻的成本极低。

## 方案

1. `ui/component/` 新增 `AppEmptyPlaceholder(text: String, modifier: Modifier = Modifier)`：
   `Box(fillMaxSize, contentAlignment = Center)` 内放一个 `Text`，`textAlign = TextAlign.Center`、16sp、
   `appColors().onSurfaceVariant`、`lineHeight` 略放大。两套风格共用同一个 `Text`（与 `AppRoleChip` 一致），
   不自绘任何内容。
2. 新增字符串 `empty_placeholder`：`¯\_(ツ)_/¯\n空空如也` / `¯\_(ツ)_/¯\nNothing here`。
   旧的 `no_data` / `peer_no_connection` / `wait_to_fetch` 保留，其它页面不动。
3. 概览：`networks.isEmpty()` 时占位放在设备卡下方的剩余空间里居中（现在那里只有一个列表，没有滚动内容）。
4. 对等节点：`peers.isEmpty()` 时占位铺满顶栏下方剩余空间并居中。
5. 「等待获取」是加载中，不是空：`peersLoaded == false` 时保留一行小字状态，只有「已加载且没有 peer」
   才用空占位；概览没有加载态，直接进占位。

## 验收

- 浅色 / 深色、Miuix / Material 四种组合下，占位都在内容区水平垂直居中。
- `:app:testDebugUnitTest` 通过（`UiConventionsTest` 只看 `ui/screens/**` 的底层控件，本方案不触碰）。
- 中英文都能显示完整两行，不被截断。

## 备选

如果居中占位在两种风格下观感仍然不好，按既定备选直接删掉这两处文字，不显示空状态提示。
删除时同时清掉只被这两处引用的字符串键。

## 影响面

只涉及 `:app` 的 UI 与字符串资源，不动数据层与 `:core`。
