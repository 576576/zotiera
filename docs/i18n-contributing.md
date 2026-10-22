# 贡献翻译

[English](#english) · 中文

界面文案全部在 `app/src/main/res/` 下，每个语言一个目录。英文是基准，其余语言缺哪条就回落到英文。

| 目录 | 语言 |
| --- | --- |
| `values/` | 英文（基准，改文案先改这里） |
| `values-zh/` | 简体中文 |
| `values-fr/` | 法语 |
| `values-ru/` | 俄语 |
| `values-es/` | 西班牙语 |
| `values-ja/` | 日语 |

每个目录里只有一个 `strings.xml`，键名完全相同，共 139 条文案加 1 组复数。

## 改一条已有文案

1. 打开目标语言的 `app/src/main/res/values-xx/strings.xml`。
2. 找到对应的 `<string name="...">`，只改尖括号中间的文字。
3. **不要改 `name`**，也不要加删条目——键名少一条就会回落到英文，多一条则是没人用的死条目。
4. 编译验证：`./gradlew :app:assembleDebug`。AAPT 会在编译时检查格式符与转义。
5. 进应用 **设置 → 界面语言** 切到该语言，看一眼有没有截断或串行。

## 新增一门语言

以意大利语（`it`）为例，五步：

**1. 复制基准文件**

```sh
cp app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml
```

**2. 翻译条目**

`values/strings.xml` 里 `translatable="false"` 标记的条目不翻译（例如 `peer_version_unknown`），
保持原样即可。

**3. 在系统语言列表里注册**

`app/src/main/res/xml/locales_config.xml`：

```xml
<locale android:name="it" />
```

这一步决定 API 33+ 系统设置里的「应用语言」列表里有没有它。

**4. 加进应用内的语言选择**

`app/src/main/kotlin/net/sumitm/zotiera/util/AppLanguage.kt`：

```kotlin
val TAGS = listOf(SYSTEM, "en", "zh", "fr", "ru", "es", "ja", "it")
```

`normalize()` 会按 `TAGS` 过滤，加进这个列表就算接上了。

**5. 给语言选择器加显示名**

`app/src/main/kotlin/net/sumitm/zotiera/ui/screens/SettingsScreen.kt` 的 `languageName()`：

```kotlin
"it" -> "Italiano"
```

语言名**用该语言自己的写法**（`Français` 而不是 `French`），这样任何界面语言下都能认出来。

## 格式规则

**占位符必须原样保留。** `%1$s`、`%d` 的顺序和数量都要与英文一致，否则运行时会抛
`IllegalFormatException`，或者数字跑到错误的位置。

```xml
<string name="version_line">Version: %1$s</string>   <!-- 版本号 -->
<string name="planet_exists">Already added: %1$s</string>
<string name="identity_default_alias">Identity %1$d</string>
```

**不要用 `\'` 转义单引号。** 当前构建链（AGP 9 的 AAPT2）不接受反斜杠转义的单引号，
法语这类需要撇号的语言请直接用排版撇号 `’`（U+2019），或改写句子避开。

```xml
<!-- 不要 -->
<string name="delete_network_warning">Cela ne retire que de l\'liste.</string>
<!-- 要 -->
<string name="delete_network_warning">Cela ne retire que de l’liste.</string>
```

**XML 特殊字符要转义。** `&` 写成 `&amp;`，`<` 写成 `&lt;`；`"` 只有在属性里才需要处理。
文案里需要引号时，中英混排用「」或 “ ”，不要用 `\"`。

**换行用 `\n`。** 例如免责声明的两段之间是 `\n\n`，保留它。

**复数单独处理。** `network_count` 是 `<plurals>`，按目标语言的 CLDR 规则给量词。
俄语需要 `one` / `few` / `many` / `other` 四档，日语只有 `other`：

```xml
<plurals name="network_count">
    <item quantity="one">%d сеть</item>
    <item quantity="few">%d сети</item>
    <item quantity="many">%d сетей</item>
    <item quantity="other">%d сети</item>
</plurals>
```

拿不准时至少给 `one` 和 `other`，缺的档位会回落到 `other`。

## 长度与语气

- 设置项的 `summary` 是说明文字，可以比标题长，但整行必须在一屏内放得下（横屏平板除外）。
- 按钮文案尽量短：`Delete`、`Add`、`Cancel` 这类在按钮里，长一截就会换行。
- 语气跟英文一致：陈述句、不用感叹号、不对用户说「您应该」。

## 提交之前

```sh
./gradlew :app:assembleDebug :app:lintPr
```

两条都过，再在模拟器或真机上切一次语言确认。截图对比也行，但**编译通过不代表显示正确**——
最常出问题的是长句子把标签挤出屏幕。

改动一个语言包就够一次 PR，不必等其它语言。新增语言请同时在 PR 说明里给出该语言的自称
（`Italiano` 这种写法），方便核对设置页里的显示名。

---

# English

App strings live under `app/src/main/res/`, one directory per locale. English is the source of
truth; any missing entry falls back to it.

| Directory | Language |
| --- | --- |
| `values/` | English (source — change wording here first) |
| `values-zh/` | Simplified Chinese |
| `values-fr/` | French |
| `values-ru/` | Russian |
| `values-es/` | Spanish |
| `values-ja/` | Japanese |

Each directory holds a single `strings.xml` with the same keys: 139 strings plus one plural set.

## Editing an existing string

1. Open the target `app/src/main/res/values-xx/strings.xml`.
2. Find the `<string name="...">` and change only the text between the tags.
3. **Do not rename keys**, and do not add or remove entries — a missing key silently falls back to
   English, an extra one is dead weight.
4. Verify with `./gradlew :app:assembleDebug`. AAPT checks format specifiers and escaping at build time.
5. Switch to that language in **Settings → Interface language** and check for truncation.

## Adding a new language

Five steps, using Italian (`it`) as the example:

**1. Copy the source file**

```sh
cp app/src/main/res/values/strings.xml app/src/main/res/values-it/strings.xml
```

**2. Translate**

Skip entries marked `translatable="false"` (e.g. `peer_version_unknown`); leave them as they are.

**3. Register it for per-app language settings**

`app/src/main/res/xml/locales_config.xml`:

```xml
<locale android:name="it" />
```

This is what makes the language appear in the system's per-app language list on API 33+.

**4. Add it to the in-app picker**

`app/src/main/kotlin/net/sumitm/zotiera/util/AppLanguage.kt`:

```kotlin
val TAGS = listOf(SYSTEM, "en", "zh", "fr", "ru", "es", "ja", "it")
```

`normalize()` filters by this list, so adding the tag is all that is needed.

**5. Add the display name**

`languageName()` in `app/src/main/kotlin/net/sumitm/zotiera/ui/screens/SettingsScreen.kt`:

```kotlin
"it" -> "Italiano"
```

Write language names **in that language** (`Français`, not `French`) so they stay recognisable
whatever the UI language is.

## Formatting rules

**Keep format specifiers intact.** `%1$s`, `%1$d` and `%d` must appear with the same count and order
as the English source, otherwise you get an `IllegalFormatException` at runtime or a number in the
wrong place.

```xml
<string name="version_line">Version: %1$s</string>
<string name="planet_exists">Already added: %1$s</string>
<string name="identity_default_alias">Identity %1$d</string>
```

**Never escape apostrophes as `\'`.** The current toolchain (AAPT2 in AGP 9) rejects backslash-escaped
single quotes. For French and similar languages use the typographic apostrophe `’` (U+2019), or
rephrase the sentence.

```xml
<!-- Don't -->
<string name="delete_network_warning">Cela ne retire que de l\'liste.</string>
<!-- Do -->
<string name="delete_network_warning">Cela ne retire que de l’liste.</string>
```

**Escape XML special characters.** Write `&amp;` for `&` and `&lt;` for `<`. Use the locale's own
quotation marks instead of `\"`.

**Use `\n` for line breaks.** The disclaimer relies on `\n\n` between paragraphs — keep it.

**Plurals are separate.** `network_count` is a `<plurals>` block; provide the CLDR categories the
language needs. Russian wants `one` / `few` / `many` / `other`; Japanese only `other`:

```xml
<plurals name="network_count">
    <item quantity="one">%d сеть</item>
    <item quantity="few">%d сети</item>
    <item quantity="many">%d сетей</item>
    <item quantity="other">%d сети</item>
</plurals>
```

When unsure, provide at least `one` and `other`; missing categories fall back to `other`.

## Length and tone

- Setting summaries may be longer than their titles, but must still fit on one line on a phone.
- Keep button labels short. `Delete`, `Add`, `Cancel` sit inside buttons; a few extra characters wrap.
- Match the English tone: plain statements, no exclamation marks, no addressing the user as "you must".

## Before opening a PR

```sh
./gradlew :app:assembleDebug :app:lintPr
```

Then switch the language in the app and look at it. **A green build does not mean correct
rendering** — long sentences pushing labels off-screen are the usual failure mode.

One PR per language is fine; you do not have to wait for the others. For a new language, mention
its endonym (`Italiano`) in the PR description so the settings entry can be double-checked.
