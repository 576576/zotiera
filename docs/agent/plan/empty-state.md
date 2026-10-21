# 空状态占位

- 状态：完成
- 建立日期：2026-10-21
- 基线提交：`9578f7a`
- 决策：**直接删除空状态文字**，不做居中占位（2026-10-21）
- 事实依据：
  - 概览页原实现：`OverviewScreen.kt`，`state.networks.isEmpty()` 时左上角画 `R.string.no_data`
  - 对等节点页原实现：`PeersScreen.kt`，`state.peers.isEmpty()` 时左上角画 `peer_no_connection` / `wait_to_fetch`
  - LSPosed 的空状态：`LSPosed/app/src/main/java/org/lsposed/manager/ui/widget/EmptyStateRecyclerView.java`；
    文案 `LSPosed/app/src/main/res/values/strings.xml` 的 `list_empty`（`¯\_(ツ)_/¯\n空空如也`）
  - Miuix 0.9.4（`miuix-ui-android-0.9.4-sources.jar`）没有空状态组件

## 背景

概览与对等节点在列表为空时，会在内容区左上角显示一行 13sp 的说明文字（`无数据`、`无连接` / `等待获取`），
与卡片列表的视觉节奏不一致。

## 调查结论

LSPosed 的 `EmptyStateRecyclerView` 是 `RecyclerView` 子类在 `dispatchDraw` 里用 `StaticLayout` 居中画
`¯\_(ツ)_/¯\n空空如也`（16sp、次要色），不是可复用组件；Compose / Miuix 也没有现成的空状态控件。

## 决策

不新增占位组件，直接删掉这两处文字：

- 概览：`LazyColumn` 为空时什么都不画，页面只剩设备卡。
- 对等节点：`LazyColumn` 为空时什么都不画，顶栏下方留空。
- 删除只被这两处使用的字符串 `peer_no_connection`；`no_data`、`wait_to_fetch` 仍被设备页/卫星页使用，保留。

## 已落地改动

- `OverviewScreen.kt`：去掉 `if (networks.isEmpty())` 分支与 `no_data` 文本，列表直接渲染。
- `PeersScreen.kt`：去掉 `if (peers.isEmpty())` 分支，列表直接渲染。
- `values/strings.xml`、`values-zh/strings.xml`：删除 `peer_no_connection`。

## 不采用的部分

居中占位组件（`AppEmptyPlaceholder`）与 `empty_placeholder` 字符串不再实现。

## 变更记录

- 2026-10-21：建立时方案为「居中 `¯\_(ツ)_/¯` 占位」；同日改为直接删除文字，状态置为完成。
