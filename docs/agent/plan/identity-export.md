# 身份导入导出（identity.secret）

- 状态：执行中（导入已落地；导出未做）
- 建立日期：2026-10-19
- 事实依据：[`architecture.md`](../architecture.md) 的「身份文件」小节

## 0. 结论

**能导出，也能导入。** 身份就是两个普通文件，落在应用私有目录，路径由 `DataStore` 决定：

| 文件 | 实测大小 | 内容 | 敏感性 |
| --- | --- | --- | --- |
| `identity.public` | 141 字节 | `<10 位节点 ID>:0:<公钥 hex>` | 可公开 |
| `identity.secret` | 270 字节 | 私钥记录 | **等同私钥，泄露即可冒充该节点** |

debug 包实测（`adb shell run-as net.sumitm.zotiera.debug ls -l files/`）两个文件都在，
`identity.public` 的首段就是 App 里显示的节点 ID。

在 [`architecture.md`](../architecture.md) 的实例模型里，身份是**多条**的，
所以这两个文件不再是「全局一份」，而是归到「管理身份」页的某一条下面。

## 1. 事实：身份是怎么落盘的

- 核心不直接写文件，而是通过 `DataStorePutListener` 回调把「名字 + 字节」交给 App：
  `externals/core/java/jni/com_zerotierone_sdk_Node.cpp:407`（写 `identity.secret`）、`:509`（读回）。
- App 侧实现在 `app/src/main/java/net/sumitm/zotiera/service/DataStore.java`：
  名字里没有 `/` 时走 `context.openFileOutput(name, 0)`（第 53 行），即当前数据目录下的 `identity.secret`。
- `secure` 标志在 Java 侧被忽略，文件是**明文**的 ZeroTier 记录（桌面端也一样）。

也就是说：导出 = 读两个文件；导入 = 在新身份目录里写两个文件，不用改任何现有身份。

## 2. 用途

- 换机 / 重装：把身份带走，节点 ID 不变，已授权的网络不用重新审批。
- 桌面端复用：把两个文件放进桌面版 ZeroTier 的 data 目录，同一节点 ID 在电脑上出现。
- 多身份：库里多一条「办公」「家里」，各自一套网络，切换实例时换身份。

Android 11+ 不能跨应用读私有目录，所以只能由 App 自己通过 SAF 读写用户选的位置。

## 3. 方案

### 3.1 导出（挂在「管理身份」的行菜单里）

1. 读该身份的 `identity.secret` + `identity.public`；任一缺失 → 提示「这个身份还没生成密钥，
   先让它上线一次」。
2. SAF `CreateDocument("application/zip")`，默认名 `zotiera-identity-<nodeId>.zip`。
3. zip 内容：

   ```
   identity.secret
   identity.public
   README.txt      // 这两个文件是什么、怎么用、泄露的后果
   ```
4. 成功后提示：文件等同私钥，别上传。

### 3.2 导入（「添加身份」里的第二个选项）

1. SAF `OpenDocument`，只收 `.zip` 或单个 `identity.secret`。
2. 校验：
   - 两个文件都能完整读出；
   - `identity.public` 能解出节点 ID，且与 `identity.secret` 对应；
   - 节点 ID 不与已有身份重复（重复则提示「已存在（别名 x）」，不覆盖）。
3. 通过后建新身份行 + 目录，把两个文件按引擎要的名字写进去；别名默认取节点 ID，可改。
4. 校验失败时明确说哪一步没过（不是「导入失败」四个字）。

导入永远是**新增**，不覆盖任何现有身份——这是比「恢复备份」低风险的地方。

## 4. 代码落点

```
app/src/main/kotlin/net/sumitm/zotiera/identity/IdentityStore.kt   // 读、打包、解包、校验
app/src/main/kotlin/net/sumitm/zotiera/ui/screens/IdentityScreen.kt // 列表 + 菜单 + 两个入口
```

`IdentityStore` 只做四件事：列文件、读字节、打 zip、解 zip 并校验。写盘方向由 Compose 侧的
`CreateDocument` / `OpenDocument` 提供目标位置，和月亮页导出 `.moon` 同款写法。

## 5. 步骤

| 步 | 内容 | 验收 |
| --- | --- | --- |
| S1 | `IdentityStore`：读字节、打 zip、解 zip、校验 | 单测：造两个假文件 → 打包 → 解包 → 校验通过；改一个字节 → 校验失败 |
| S2 | 导出入口 + 提示文案 | 真机导出后 `adb pull` 解压，两个文件大小与源一致 |
| S3 | 导入入口 + 失败原因分档 | 导入自己的导出件，列表多一条且节点 ID 一致；重复导入被拒 |
| S4 | 桌面验证（人工） | 文件放进桌面 ZeroTier data 目录起服务，节点 ID 与手机一致 |

## 6. 验收标准

- 导出件的 `identity.public` 首段等于该身份的节点 ID。
- zip 内 `README.txt` 写明「等同私钥」。
- 身份还没生成密钥时，导出入口给出可读提示而不是崩。
- 导入重复身份被拒且不破坏已有身份。
- 两种风格 × 明暗看一遍。

## 7. 风险

| 风险 | 处理 |
| --- | --- |
| 私钥被导出到相册 / 网盘 | 只能靠用户选位置 + 文案明确「等同私钥，不要上传」 |
| 导出的旧身份与新身份混淆 | 文件名带节点 ID；README 里写清楚 |
| 导入重复导致两条同 ID 身份 | 校验阶段就拒掉，不做「覆盖」 |
| 用户以为换个身份就能「换个号」 | 身份页写明「不同身份 = 不同节点 ID，网络要重新授权」 |
