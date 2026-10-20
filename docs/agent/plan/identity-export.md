# 身份导出（identity.secret）

## 0. 结论

**能导出。** 身份就是两个普通文件，落在应用私有目录，路径由 `DataStore` 决定：

| 文件 | 实测大小 | 内容 | 敏感性 |
| --- | --- | --- | --- |
| `files/identity.public` | 141 字节 | `<10 位节点 ID>:0:<公钥 hex>` | 可公开 |
| `files/identity.secret` | 270 字节 | 私钥记录 | **等同私钥，泄露即可冒充该节点** |

debug 包实测（`adb shell run-as net.sumitm.zotiera.debug ls -l files/`）两个文件都在，
`identity.public` 的首段就是 App 里显示的节点 ID。

## 1. 事实：身份是怎么落盘的

- 核心不直接写文件，而是通过 `DataStorePutListener` 回调把「名字 + 字节」交给 App：
  `externals/core/java/jni/com_zerotierone_sdk_Node.cpp:407`（`identity.secret`）、`:509`（读回）。
- App 侧实现在 `app/src/main/java/net/sumitm/zotiera/service/DataStore.java`：
  名字里没有 `/` 时走 `context.openFileOutput(name, 0)`（第 53 行），即 `files/identity.secret`。
- `secure` 标志在 Java 侧被忽略，文件是**明文**的 ZeroTier 记录（不只是本 App，桌面端也一样）。

也就是说：导出 = 读两个文件；导入 = 写两个文件 + 重启节点。

## 2. 导出的用途

- 换机 / 重装：把身份带走，节点 ID 不变，已授权的网络不用重新审批。
- 桌面端复用：把两个文件放进桌面版 ZeroTier 的 data 目录，同一节点 ID 在电脑上出现。
- 备份：删 App 数据前留一份。

Android 11+ 不能跨应用读私有目录，所以只能由 App 自己通过 SAF 写到用户选的位置。

## 3. 方案

### 3.1 导出（本轮做）

入口放在**设备页**（`DeviceScreen`）底部，一行「导出身份」：

1. 读 `files/identity.secret` + `files/identity.public`；任一缺失 → 提示「身份还没生成，先连一次网络」。
2. 用 SAF `CreateDocument("application/zip")`，默认文件名 `zotiera-identity-<nodeId>.zip`。
3. zip 内容：
   ```
   identity.secret
   identity.public
   README.txt      // 这两个文件是什么、怎么用、泄露的后果
   ```
4. 写成功后 Toast/提示：文件等同私钥，别上传。

### 3.2 导入（不做，只留计划位）

风险远大于收益（覆盖即丢失原节点 ID，所有网络成员资格作废），本轮不做。
真要做时的顺序：停服务 → 校验两个文件（secret 能被读回、public 的节点 ID 与之匹配）→
把旧身份改名备份 → 写入 → 起服务 → 提示「节点 ID 已变为 xxx」。

## 4. 代码落点

```
app/src/main/kotlin/net/sumitm/zotiera/ui/screens/DeviceScreen.kt   // 加入口
app/src/main/kotlin/net/sumitm/zotiera/identity/IdentityStore.kt    // 读文件 + 打 zip
```

`IdentityStore` 只做三件事：列出身份文件是否存在、读字节、把两个文件打成 zip 字节数组。
写盘交给 Compose 侧的 `rememberLauncherForActivityResult(CreateDocument)`，与月亮页导出 `.moon` 同款写法。

## 5. 步骤

| 步 | 内容 | 验收 |
| --- | --- | --- |
| S1 | `IdentityStore`：判定文件存在、读字节、打包 zip | 单测：给两个假文件能打出可解压的 zip |
| S2 | 设备页入口 + SAF 导出 + 提示文案 | 真机导出后 `adb pull` 出来能解压，两个文件大小与源一致 |
| S3 | 桌面验证（人工） | 把文件放进桌面 ZeroTier data 目录起服务，节点 ID 与手机一致 |

## 6. 验收标准

- 导出文件的 `identity.public` 首段等于 App 里显示的节点 ID。
- zip 内 `README.txt` 明确写了「等同私钥」。
- 身份文件不存在时，入口给出可读提示而不是崩。
- 两种风格 × 明暗看一遍。

## 7. 风险

| 风险 | 处理 |
| --- | --- |
| 私钥被导出到相册/网盘 | 只能靠用户选择位置与提示；文案明确「等同私钥，不要上传」 |
| 导出的旧身份与新身份混淆 | 文件名带节点 ID；README 里写清楚 |
| 未来做导入时覆盖身份 | 导入前强制备份旧身份，且二次确认里写出「节点 ID 会变」 |
| 身份文件被别的进程读到 | 文件在应用私有目录、`openFileOutput` 模式 0，不做额外暴露 |
