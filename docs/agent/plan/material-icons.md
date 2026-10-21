# 图标改用内置 Vector Drawable

- 状态：草案
- 建立日期：2026-10-21
- 基线提交：`66358f4`
- 事实依据：
  - `androidx.compose.material.icons` 文档（<https://developer.android.com/reference/kotlin/androidx/compose/material/icons/package-summary>）：
    「This library is no longer recommended for displaying Material Icons in Compose. Material Symbols are the new way forward,
    and we have stopped publishing updates to this library. It has been removed from the latest Material 3 library release…
    we recommend downloading Vector Drawable XML files from the "Android" tab on Google Fonts」
  - 本仓库实测（`material-icons-extended-android:1.7.8`）：AAR 内 `classes.jar` 36 MB、11105 个类；
    单独 `d8 --min-api 30 --release` 出 21.9 MB dex（gzip 后 4.1 MB）
  - 当前包 `Zotiera-1.16.2-r161-arm64-v8a.apk` 62 MB：`classes*.dex` 共 57.9 MB，且四个 dex 全部以
    stored（不压缩）存放，因此那 21.9 MB 全额计入包体
  - `app/src/main/res/drawable/` 已有同形态资源（`baseline_cloud_download_24.xml` 等纯 vector）

## 现状

`app/build.gradle` 同时挂着 `material-icons-core`（128 行）与 `material-icons-extended`（135 行）。
`minifyEnabled false`，所以 11105 个图标类一个不落地进包。

代码里用到 15 个 `Icons.Rounded.*`：

| 图标 | 使用位置 | 来源 |
| --- | --- | --- |
| `Add` | 概览 / 行星 / 卫星 / 身份页的 FAB | core |
| `Check` | `Controls.kt`、`preference/Preference.kt` | core |
| `Settings` | `MainActivity`（底部导航） | core |
| `KeyboardArrowRight` | `preference/Preference.kt` | core |
| `Delete` | `NetworkDetail` | core |
| `ArrowBack`（`Icons.AutoMirrored.Rounded`） | `AppScaffold.kt` 顶栏返回 | core |
| `CheckCircleOutline` | `StatusCard`（工作中 / 连接中） | **extended** |
| `RemoveCircleOutline` | `StatusCard`（未连接） | **extended** |
| `HighlightOff` | `StatusCard`（没有网络） | **extended** |
| `Dns` | `MainActivity` | **extended** |
| `Devices` | `MainActivity` | **extended** |
| `Language` | `OverviewScreen` | **extended** |
| `Public` | `PlanetScreen` | **extended** |
| `Badge` | `IdentityScreen` | **extended** |
| `DarkMode` | `MoonsScreen` | **extended** |

15 个里只有 9 个必须靠 expanded 才有。为了这 9 个图标，包里多了约 21.9 MB dex。

## 目标

1. 移除 `material-icons-extended`，9 个 extended 图标改为项目内 vector drawable。
2. 顺带把 `material-icons-core` 也去掉：全部 15 个图标统一走 drawable，图标的来源只剩一处。
3. 单 ABI 包体从约 60 MB 降到约 40 MB，且不依赖「R8 一定开着」这个前提。

## 怎么取图标

Google Fonts 的图标页（<https://fonts.google.com/icons>）选中图标后，右侧面板可切换格式，
选 **Android** 直接给可用的 Vector Drawable XML，无需自己转 SVG。

下载时固定这组参数，保证 15 个图标风格一致：

| 参数 | 取值 | 说明 |
| --- | --- | --- |
| Style | Rounded | 与现在 `Icons.Rounded` 对齐 |
| Weight | 400 | 默认 |
| Fill | 0 | 线框；状态卡的大图标也靠线框，不用填充版 |
| Grade | 0 | 默认 |
| Optical size | 24 | 与代码里 `Icons` 的 24dp 对齐 |

产物落到 `app/src/main/res/drawable/ic_<name>.xml`：

| 图标 | 文件名 |
| --- | --- |
| Add | `ic_add.xml` |
| Check | `ic_check.xml` |
| Settings | `ic_settings.xml` |
| KeyboardArrowRight | `ic_chevron_right.xml` |
| Delete | `ic_delete.xml` |
| ArrowBack | `ic_arrow_back.xml` |
| CheckCircleOutline | `ic_check_circle_outline.xml` |
| RemoveCircleOutline | `ic_remove_circle_outline.xml` |
| HighlightOff | `ic_highlight_off.xml` |
| Dns | `ic_dns.xml` |
| Devices | `ic_devices.xml` |
| Language | `ic_language.xml` |
| Public | `ic_public.xml` |
| Badge | `ic_badge.xml` |
| DarkMode | `ic_dark_mode.xml` |

drawable 文件名不能驼峰，必须下划线。

## 代码改法

调用点统一成：

```kotlin
Icon(
    painter = painterResource(R.drawable.ic_add),
    contentDescription = null,
    modifier = Modifier.size(24.dp),
)
```

- **着色**：`Icon(painter, …)` 的 `tint` 会套 `ColorFilter`，vector 里写死 `android:fillColor="#FF000000"` 也能被覆盖，
  深浅色切换照旧由调用点传 `tint`。
- **方向**：返回箭头在 `<vector>` 上加 `android:autoMirrored="true"`，替代 `Icons.AutoMirrored.*`。
- **尺寸**：vector 保持 `width/height=24dp`、`viewportWidth/Height=24`；状态卡那 110dp 大图标继续用
  `Modifier.size(110.dp)` 放大，矢量无损，不另出资源。
- **不要放 mipmap**：`painterResource` 加载不了自适应图标（此前 `ic_launcher_round` 崩过），一律放 `drawable/`。
- **许可**：Material Symbols 为 Apache-2.0；在每个 XML 顶部留一行来源注释，
  并在仓库根 `NOTICE` 里补一段图标来源说明。

## 字形会变

Material Symbols 与旧的 Material Icons 不是同一套路径，`check_circle`、`highlight_off` 这类
在边角细节上能看出差别。要完全保持现状外观的话，改从 `google/material-design-icons` 仓库取
rounded 的 SVG（`src/<category>/<name>/materialiconsround/24px.svg`）再转 XML。

建议按 Material Symbols 走：那套是官方后续方向，也没有「旧仓库里 rounded 变体缺图标」的问题。
逐页截图确认后如果哪个图标明显走形，单独换回旧 SVG。

## 与 R8 的关系

只开 R8 也能把这 21.9 MB 裁掉（未引用的图标类会被删），但：

- 构建期仍要解析这 11105 个类，构建时间照旧；
- 该库已停止更新，后续没有安全/兼容修复；
- 包体不该挂在「R8 一直开着且保留规则没写错」这个前提上——规则一破就悄悄涨回 21.9 MB。

两者不冲突：本计划先做（改动面小、可回归），R8 按 [`init-refactor.md`](init-refactor.md) 的 R3 独立推进。

## 步骤

| 批次 | 范围 | 要点 |
| --- | --- | --- |
| I1 | 下载 15 个 XML 到 `drawable/` | 固定 Rounded / 400 / fill 0 / opsz 24；补来源注释 |
| I2 | 改调用点 | 15 处 `Icons.*` → `painterResource`；删掉 `material.icons` 导入 |
| I3 | 拆依赖 | `app/build.gradle` 去掉 `material-icons-core` 与 `material-icons-extended` |
| I4 | 防回归 | `UiConventionsTest` 加一条：`import androidx.compose.material.icons` 出现在 `app/src/main` 即失败 |
| I5 | 量包体 | 对比 `classes*.dex` 与 APK 总大小，写进 [`architecture.md`](../architecture.md) 的包体段 |

## 验收

- `./gradlew :app:dependencies --configuration prRuntimeClasspath | grep material-icons` 无输出。
- `assemblePr` 通过；`classes*.dex` 从 57.9 MB 降到约 36 MB，单 ABI 包从 62 MB 降到约 40 MB。
- 逐页截图对照（概览、行星、卫星、身份、网络详情、设置、关于、状态卡四态、顶栏返回），
  深浅色各一遍，图标位置、大小、着色与改动前一致。
- 英文界面 + 从右到左语言下返回箭头方向正确。
- `testDebugUnitTest`、`lintPr` 通过。

## 风险

| 风险 | 处理 |
| --- | --- |
| 字形与现状有细微差别 | 逐页截图确认；个别走形的换旧 SVG 转 XML |
| 某个图标在 Google Fonts 找不到同名 | 用语义最接近的替换，并在提交信息里记一笔 |
| tint 没盖住 vector 里的写死颜色 | 全部 `fillColor` 用 `#FF000000`，颜色只由 `Icon` 的 `tint` 决定 |
| 漏改一处导致依赖删不掉 | I4 的测试兜底；删依赖前先 `grep -rn "material.icons" app/src` |

## 不做

- 不引入 `material-symbols` 字体方案：图标数量少，字体比 15 个 vector 大得多。
- 不引入第三方的图标库或图标 Compose 封装。
- 不改状态卡的大图标尺寸与位置。
