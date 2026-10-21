# Zotiera

![Zotiera](../../assets/images/icon-zotiera-128.png)

[中文](../../README.md) | English

An unofficial [ZeroTier](https://www.zerotier.com/) client for Android, with a current
Material / Miuix look and several networks online at the same time.

## Features

- Join and connect several ZeroTier networks at once: the switch on a network row means join / leave,
  the master switch on the device card only controls the tunnel
- A quick settings tile toggles the tunnel; a notification stays while connected
- Two UI styles (Miuix / Material 3), light, dark and pure black
- Custom planet (file or HTTPS URL), moon orbit management and `.moon` export
- Network details (status, MAC, MTU, DNS, assigned addresses) and a peer list (role, version,
  latency, preferred path)
- Three DNS modes: none / network DNS / custom
- Identity import / export (zip with both key files)
- Chinese and English UI; on Android 13+ the app language can also be set in system settings

## Screenshots

![Peer list](../../assets/images/screenshots/peers-material-dark.png)

Material dark style. Emulators cannot reproduce the typography and controls of the Miuix style;
Miuix looks as designed only on real devices. More screenshots in
[`assets/images/screenshots/`](../../assets/images/screenshots/readme.md).

## Requirements

- Android 11 (API 30) or newer
- 64-bit only: `arm64-v8a`, `x86_64`

## Building

```sh
git submodule update --init --recursive
./gradlew :app:assembleDebug        # local debug build
./gradlew :app:assemblePr           # test channel, package name net.sumitm.zotiera.pr
./gradlew :app:assembleRelease      # release build, needs the 4 ANDROID_* variables
```

The release build reads `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`; if any of them is missing the build fails
instead of producing an unsigned package. The `.pr` channel uses the public test key in
`assets/ci/signing/pr.keystore` and must not be distributed.

## Versioning

Versions follow the ZeroTier engine: `versionName = <engine>-<revision>` (e.g. `1.16.2-1`),
`versionCode = major*1e6 + minor*1e4 + patch*100 + revision`. The engine version comes from
`externals/core/version.h`, the revision from `version.txt`.

## Docs

- [Architecture and current state](../agent/architecture.md) (Chinese)
- [Differences from upstream ZerotierFix](../agent/fork-comparison.md) (Chinese)
- [Open plans](../agent/plan/) (Chinese)
- [Screenshots](../../assets/images/screenshots/readme.md)

## License and credits

UI layout follows [KernelSU](https://github.com/tiann/KernelSU).

This project is licensed under **AGPL-3.0-or-later**, see [LICENSE](../../LICENSE).

The ZeroTier core in `externals/core` (since 1.16.2) is **MPL-2.0**, taken from
[zerotier/ZeroTierOne](https://github.com/zerotier/ZeroTierOne).

ZeroTier is a trademark of ZeroTier, Inc. This project is not affiliated with or endorsed by
ZeroTier, Inc.
