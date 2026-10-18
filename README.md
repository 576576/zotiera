# Zotiera

非官方 ZeroTier Android 客户端。基于官方客户端反编译代码，经
[kaaass/ZerotierFix](https://github.com/kaaass/ZerotierFix) 修补而来。

## 功能

- 加入/离开多个 ZeroTier 网络，开机自启，前台服务常驻
- 自定义 planet（文件或 HTTPS URL）、moon 轨道管理
- Peers 列表、网络详情、DNS 三种模式（不使用 / 网络下发 / 自定义）
- 中英文界面，Material 3 深浅色与动态取色

## 要求

- Android 11（API 30）及以上，仅 64 位（`arm64-v8a`、`x86_64`）

## 构建

```sh
git submodule update --init --recursive
./gradlew :app:assembleDebug        # 本地调试
./gradlew :app:assemblePr           # 测试通道，包名 net.sumitm.zotiera.pr
./gradlew :app:assembleRelease      # 正式包，需要 4 个 ANDROID_* 环境变量
```

正式签名读取 `ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、
`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`；缺任何一个都不会产出签名包。
`.pr` 通道使用仓库内的测试密钥，只用于自测，不可分发。

## 版本号

以 ZeroTier 引擎版本为基准：`versionName = <engine>-<revision>`（如 `1.16.2-1`），
`versionCode = major*1e6 + minor*1e4 + patch*100 + revision`。引擎版本号唯一来源是
`externals/core/version.h`，修订号在 `version.txt`。

## 许可与来源

- ZeroTier 核心（`externals/core`，1.16.2 起为 MPL-2.0）来自
  [zerotier/ZeroTierOne](https://github.com/zerotier/ZeroTierOne)。
- 应用代码源自官方 Android 客户端的反编译结果，原作者 Grant Limberg
  (glimberg@gmail.com)，详见上游 [AUTHORS.md](https://github.com/zerotier/ZeroTierOne/blob/master/AUTHORS.md#primary-authors)。
- 应用图标是 `ZeroTier, Inc.` 的商标。

本项目与 ZeroTier, Inc. 无隶属或背书关系。

## 文档

重构计划与基线对比见 [`docs/agent/`](docs/agent/)。
