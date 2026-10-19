# 界面结构

## 风格切换

`ui/UiMode.kt`

```kotlin
enum class UiMode(val value: String) { Miuix("miuix"), Material("material") }
val LocalUiMode = staticCompositionLocalOf { UiMode.Miuix }
```

默认 Miuix，Material 是备选。风格分支只允许出现在 `component/` 里，屏幕本身不判断风格。

## 主题

`ui/theme/Theme.kt`

- `ZotieraTheme(uiMode, colorMode, amoled)` 按风格分发到 `MaterialTheme` / `MiuixTheme`
- `ColorMode`：`SYSTEM / LIGHT / DARK`；纯黑是深色下的独立开关（`PREF_AMOLED`）
- `ZotieraType`：语义字体（等宽数字、设备标题、行标题、摘要），两套风格共用，切换时排版不跳
- `LocalDarkTheme`：当前是否深色，需要按明暗换色的地方读它

## 组件

`ui/component/`

| 文件 | 内容 |
| --- | --- |
| `AppScaffold.kt` | `AppScaffold`、`AppTopBar`、`AppHeader`、`AppBottomBar`、`AppNavigationRail`、`isWideLayout()`、`ScreenSurface` |
| `Controls.kt` | `AppCard`、`AppListRow`、`AppSwitch`、`AppTunnelSwitch`、`AppDialog`、`AppButton`、`AppFab`、`AppKeyValueRow`、`AppRoleChip`、`AppDangerCard`、`appColors()` |
| `preference/Preference.kt` | `PreferenceGroup`、`PreferenceSwitch`、`PreferenceArrow`、`PreferenceSelect`、`PreferenceTextField`、`SectionTitle` |

每个组件内部按 `LocalUiMode` 给出两份实现，对外是同一个函数。

## 页面与导航

`ui/screens/`：概览、设备、网络详情、对等节点、设置、月亮。

路由：`overview` / `peers` / `settings`（顶层）、`device` / `network/{id}` / `moons`（推入）。
顶层页切换不做位移动画；详情页入栈右滑、出栈整页右移；每个目的地都包一层不透明的 `ScreenSurface`，
避免切换时两页文字互相透出。

宽屏（>=840dp，或 >=600dp 且横屏）用导航栏 + 列表/详情双栏；窄屏用底栏，详情单独成页。

## 约定

1. 屏幕里不写 `if (LocalUiMode.current == ...)`，分支收在组件里。
2. 可点控件用框架控件（Miuix 或 Material3），不自绘按钮与下拉。
3. 列表行左边线统一：页面内 20dp、卡片内 16dp；卡片宽度铺满内容区并留 16dp 外边距。
4. 明暗两套配色都要看：只改一侧必然漏。
5. 编译通过不算验收，真机/模拟器逐页截图确认。
