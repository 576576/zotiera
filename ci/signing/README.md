# PR 通道签名

`pr.keystore` 是 **公开的测试密钥**，随仓库分发，口令也在 `app/build.gradle` 里写死：

- alias: `zotierapr`
- store/key password: `zotierapr`
- 有效期 10000 天

它只用于签发 `net.sumitm.zotiera.pr`（应用名 `Zotiera PR`）的自动构建包。任何人都能用它签出同包名的包，所以：

- **不得用于正式分发**，也不要在 Release 页面上传用它签出的包；
- 正式包只认 4 个 `ANDROID_*` 环境变量（`ANDROID_KEYSTORE_BASE64` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD`），缺任何一个 `assembleRelease` 都拿不到签名配置，构建会失败而不是产出未签名包；
- 更换这个密钥会让旧的 `.pr` 包无法覆盖安装，需要卸载重装。
