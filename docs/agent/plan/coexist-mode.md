# 共存模式（与其它 VPN / ZeroTier 客户端同时运行）

- 状态：方案已定（B：root 模式 tun），实施细节细化完成，待 P0 现场验证
- 建立日期：2026-10-21；细化：2026-10-22
- 事实依据：[`architecture.md`](../architecture.md)、本文「已实测」一节
- 相关代码：`service/ZeroTierOneService.kt`（建/拆隧道）、`service/TunTapAdapter.kt`（包泵）

## 变更记录

| 日期 | 版本 | 变更 |
| --- | --- | --- |
| 2026-10-21 | v1 | 调查完成：否决 hook 双 VPN，列出 A/B/C 候选 |
| 2026-10-22 | v2 | 定 B；补实施细节：helper 形态、fd 传递、规则/表号分配、代码接缝、清理与回退、验收用例 |

## 问题

现在只要本应用建立 VPN，系统会停掉已有的 VPN；反过来别的 VPN（包括其它 ZeroTier 客户端）启动也会把我们停掉。
用户要的是两者同时在线，本应用只负责局域网段。

## 结论

1. **注册成 LSPosed API 102 模块本身不能实现共存。**「每个用户只有一个 VPN」是 `VpnManagerService` /
   `ConnectivityService` 的结构（每用户一个 `Vpn` 对象、一个 tun），不是一处可以绕过的判断。
2. **可行路径是绕开 `VpnService`**：用 root 建 tun、按目的网段装路由规则，ZeroTier 核心照旧跑在我们的进程里。
3. 模块在这个方案里只负责两件事：判定「模块已启用」、给共存模式一个高级入口。没有模块也能做；没有 root 做不了。

## 候选方案

| 方案 | 做法 | 可行性 | 代价 |
| --- | --- | --- | --- |
| A 模块 hook 双 VPN | 改 `VpnManagerService` / `Vpn` 接受第二个 tun | 不可行 | 等于重写 VPN 栈，系统级风险 |
| B root 模式 tun + 策略路由 | root 建 tun、装 `ip rule`/`ip route`，核心用传进来的 fd | 可行 | 需要 root；DNS 与分流要另做；helper 生命周期 |
| C 不做共存 | 启动前检测到别的 VPN 就提示「会替换」 | 现状 | 用户诉求未满足 |

选 B。A 不列入计划：`Vpn` 是 per-user 单例（一个 tun、一套地址/路由/DNS），第二个 VPN 没地方挂；
即使 hook 掉校验让第二个 `establish()` 成功，系统也只有一个「当前 VPN」网络，应用侧的路由与解析
不会同时走两个 tun，等于把全局网络选择改坏。

## 已实测（API 36 模拟器，2026-10-22）

| 项 | 结果 |
| --- | --- |
| 无 VPN 时 `ip rule` | `0 local` / `11000`（`iif lo oif dummy0 uidrange 0-0`）/ `16000` / `17000` / `20000` 默认网络 / `22040`、`30040` 按 uid 的 unreachable |
| 本应用 VPN 起来后 | 多出 `12000: from all iif tun0 lookup <t>`，以及 `13000:` 那组应用分流规则（`fwmark` + `uidrange`） |
| 插 `to 10.99.0.0/24 lookup 3001 pref 12500` | `ip route get 10.99.0.5 uid 10231` → `dev zttest table 3001`，**VPN 的 13000 规则没抢走** → 12xxx 段可用 |
| `ip tuntap add` / `addr add` / `link set mtu` / `rule add` / `route add` / `rule del` / `flush` / `link del` | 全部成功，`pref` 关键字有效，删除后无残留 |
| `/dev/net/tun` | 存在（符号链接指向 `/dev/tun`） |
| `Os.recvmsg` / `StructMsghdr` / `StructCmsghdr` | 公开 API 存在，`msg_control` 字段可读 |
| `OsConstants` | **没有 `SCM_RIGHTS`**，要自带常量 `1`（Linux ABI 固定值） |
| `LocalSocketAddress.Namespace.ABSTRACT` | 存在，可用抽象命名空间，免文件权限问题 |

必须进 P0 复验的两条：`su` 域能否执行 APK 里的 helper（SELinux）、`Os.recvmsg` 在真机上是否真的回填 `msg_control`。

## 组件与文件布局

```
app/src/main/kotlin/net/sumitm/zotiera/
  service/tunnel/
    TunnelSpec.kt          地址 / 路由 / MTU / DNS 的纯数据，建隧道前的收集结果
    TunnelBackend.kt       interface：start(spec) → TunnelHandle?；stop()
    VpnTunnelBackend.kt    现有 Builder 逻辑搬进来（行为不变）
    RootTunnelBackend.kt   root 模式：拉起 helper、拿 fd、下发配置
  root/
    RootProbe.kt           环境探测：su / /dev/net/tun / 12xxx 空段 / 空闲表号 / 锁定模式
    RootShell.kt           su 调用、超时、helper 路径解析
    RootSession.kt         LocalSocket 连接、行协议、Os.recvmsg 收 fd
helper/
  CMakeLists.txt           add_executable，产物命名 libzotiera-root-helper.so 打进 lib/<abi>/
  root_helper.cpp          建 tun（ioctl）、ip 命令、规则自愈、EOF 清理
```

`helper/` 是独立 CMake 工程，由 `:app` 的 `externalNativeBuild` 一起构建。
`TunnelSpec` 让两种后端共用同一份收集逻辑，`rebuildTunnel` 只剩「收集 → 比指纹 → 换后端」。

## root helper

### 形态与打包

- C++ 可执行文件，**不依赖 `ip tuntap`**：自己 `open("/dev/net/tun")` + `TUNSETIFF` + `IFF_NO_PI`，
  其余（地址、MTU、路由、规则）通过 `execv("/system/bin/ip", argv)` 完成。
- 产物命名 `libzotiera-root-helper.so` 放进 `lib/<abi>/`，运行时路径
  `/data/app/<pkg>-<hash>/lib/<abi>/libzotiera-root-helper.so`。
  需要在 `build.gradle` 打开 `packaging { jniLibs { useLegacyPackaging = true } }`：
  Android 10+ 默认不把 .so 解压到磁盘，没有文件就没法 exec。
- 备选（P0 第一条不过时）：放 `assets/`，首次使用解到 `filesDir` 再 `chmod 700`。
- `IFF_NO_PI` 必须带：不带时每个包多 4 字节协议头，`TunTapAdapter` 现有解析会全错。

### 启动与握手

1. 应用：`su -c "<helper> --socket zotiera.<hash>.<nonce> --ifname ztr0 --uid <appUid> --table <T> --pref-base <P>"`
2. helper：清同名接口与残留规则 → 建 tun → 监听抽象 unix socket → 向 stdout 打印 `READY`
3. 应用读到 `READY`（超时 5 秒），用 `LocalSocket` 连抽象名，发 `HELLO <nonce>`
4. helper 校验对端 `SO_PEERCRED` 的 uid == `--uid`，通过后回一条带 `SCM_RIGHTS` 的消息（行 `FD` + 1 个 fd）
5. 应用 `Os.recvmsg` 收 fd → `ParcelFileDescriptor.adoptFd()`
6. 应用下发配置，helper 应用后回 `OK <规则数>`

抽象命名空间没有文件权限，鉴权靠 `SO_PEERCRED`；`nonce` 只用于避免同名 socket 串连。

### 协议

行协议，`\n` 结尾，请求/应答严格配对：

| 方向 | 行 | 说明 |
| --- | --- | --- |
| app → helper | `HELLO <nonce>` | 握手 |
| helper → app | `FD` + `SCM_RIGHTS` | 只发一次，之后不再传 |
| app → helper | `SYNC` | 开始一次整体配置 |
| app → helper | `ADDR <cidr>` | 可多行；helper 先 flush 再加 |
| app → helper | `MTU <n>` | 0 表示不改 |
| app → helper | `ROUTE <cidr>` | 可多行；helper 负责算规则并去重 |
| app → helper | `COMMIT` | 应用并回 `OK` / `ERR` |
| app → helper | `QUIT` | 主动收尾（清理后退出） |
| helper → app | `OK <n>` / `ERR <code> <msg>` | `ERR` 带 errno 名字与出错的那条命令 |

配置只有一种形态：**整体替换**。`COMMIT` 成功后才删旧规则；中途 `ERR` 用旧配置回滚。

### 网络配置细节

| 动作 | 命令 |
| --- | --- |
| 建接口 | `open("/dev/net/tun")` + `TUNSETIFF(ifr_name="ztr0", IFF_TUN\|IFF_NO_PI)` |
| 起接口 | `ip link set dev ztr0 up` |
| 地址 | `ip -4 addr add <cidr> dev ztr0` / `ip -6 addr add <cidr> dev ztr0`（先 `ip addr flush dev ztr0`） |
| MTU | `ip link set dev ztr0 mtu <n>` |
| 路由 | `ip -4 route add <cidr> dev ztr0 table <T>`，v6 用 `ip -6` |
| 分流规则 | 每条路由一条 `ip -4 rule add to <cidr> lookup <T> pref <P+i>`，v6 用 `ip -6` |
| 拆除 | 逐条 `ip rule del pref <P+i>` → `ip route flush table T` → `ip link del ztr0` |

## 表号与优先级

- **表号**：候选 3000–3099。netd 的表按接口命名，`/data/misc/net/rt_tables` 里实测是
  `local_network`=97、`dummy0`=1002、`eth0`=1017、`tun0`=1019，另有 `<iface>_local` 的 10 亿段，都不在候选区间。
  选第一个「`ip route show table N` 为空且无 rule 引用」的，写进偏好 `root_tunnel_table` 复用。
- **优先级**：候选段 12100–12900，步长 10，按 `ip rule show` 找长度 = 路由条数的连续空位。
  实测 VPN 分流规则在 13000，12xxx 稳定压过；11000 是 `uidrange 0-0` 系统专用、12000 是 `iif tun0`，都不冲突。
- 单次配置最多 64 条路由，超出记日志只装前 64 条（正常网络远少于这个数）。
- **规则自愈**：helper 每 15 秒比对一次 `ip rule show`，自己那几条不见了就补回去
  （netd 切网络、别的工具 flush 都能兜住）。这是常驻进程唯一的周期动作。

## 应用侧：怎么收 fd

```kotlin
// SCM_RIGHTS 是内核 ABI，恒为 1；OsConstants 里没有这个常量
val msg = StructMsghdr(null, arrayOf(ByteBuffer.allocate(16)), null, 0)
Os.recvmsg(socketFd, msg, 0)
val fd = msg.msg_control
    ?.firstOrNull { it.cmsg_level == OsConstants.SOL_SOCKET && it.cmsg_type == SCM_RIGHTS }
    ?.cmsg_data
    ?.let { ByteBuffer.wrap(it).order(ByteOrder.nativeOrder()).getInt() }
```

拿到 int fd 后 `ParcelFileDescriptor.adoptFd(fd)` 交给现有 `TunTapAdapter.setVpnSocket()`。
真机上若 `msg_control` 为空（不同 ROM 的 libcore 实现差异），退路是给 `:app` 加 20 行 JNI
`recvFd(int sockFd)`，协议与上层都不动。

## 与现有代码的接缝

| 位置 | 现在 | 共存模式 |
| --- | --- | --- |
| `rebuildTunnel()`（约 604–742 行） | 边遍历网络边往 `Builder` 塞，最后 `establish()` | 先收集成 `TunnelSpec` 再交给后端；指纹比对不变 |
| `teardownTunnel()`（约 560 行） | `adapter.interrupt()` + `closeVpnSocket()` | 增加 `backend.stop()`（root 模式关 socket，helper 自清） |
| `protect(svrSocket)`（约 212 行） | 让 UDP socket 绕开本应用的 tun | root 模式跳过（不是 VPN，`protect()` 必然失败），日志只说明一次 |
| `DISALLOWED_APPS`（约 725 行） | `Builder.addDisallowedApplication` | root 模式忽略；P3 用 `iptables -m owner` + fwmark 重做 |
| `onRevoke()`（约 359 行） | 系统撤销 VPN 时收尾 | root 模式不会触发，逻辑保留 |
| `onDestroy()` / `shutdown()` | 关 socket | 增加 helper 会话关闭，保证进程退出前摘掉规则 |

`ZeroTierOneService` 继续继承 `VpnService`：共存模式下只是不调 `establish()`，不占系统 VPN 槽位；
`foregroundServiceType="specialUse"` 与 VPN 授权无关，前台通知照旧。

## 能力边界（共存模式）

| 能力 | 行为 |
| --- | --- |
| 全隧道（所有流量走 ZeroTier） | **禁用**，开关灰掉并提示：全隧道本来就与「保留另一个 VPN」互斥 |
| DNS（网络下发 / 自定义） | **不支持**，加入网络时选的 DNS 被忽略；P3 再做本地转发 + `iptables -t nat` 重定向 |
| 按应用分流 | P2 不支持；P3 用 `iptables -t mangle -m owner` 打标 + `ip rule fwmark` |
| 全局多播路由 `224.0.0.0/4` | **不装**：装了会抢掉局域网 mDNS/SSDP；ZeroTier 网内组播订阅由引擎处理 |
| 系统 VPN 图标 | 没有，状态只在前台通知、磁贴与设备卡 |
| IPv6 | 跟着网络配置走，`disableIPv6` 语义不变 |

## 生命周期与残留清理

| 场景 | 处理 |
| --- | --- |
| 正常停止 | 发 `QUIT` → helper 删规则/路由/接口 → 退出；应用关 socket |
| 应用进程被杀 | socket 断开 → helper 读到 EOF → 同一套清理后退出 |
| helper 被杀 | 应用侧读 socket 得 EOF → 标记隧道失效 → 通知「共存隧道已断开」，下次 `rebuildTunnel` 重拉 |
| 上次崩溃残留 | helper 启动时先 `ip link del ztr0`（忽略不存在）、扫 `ip rule show` 删 `lookup <T>`、`ip route flush table T` |
| 重启 | 内核态随重启清空，无需处理 |
| 卸载 | 接口与规则随进程消失；应用数据删除后表号记录一并丢失 |

设置页提供「清理残留」手动入口：跑一次 `su -c "<helper> --cleanup-only --ifname ztr0 --table <T>"`。

## 失败模式与回退

| 失败 | 表现 | 处理 |
| --- | --- | --- |
| 没有 root / 用户拒绝 su | `su -c id` 5 秒内无 `uid=0` | 开关不显示；已开着则回落 `VpnService` 并提示 |
| helper 无法执行（SELinux） | `exec` 返回 `EACCES`，stderr 有 `avc: denied` | 回落 `VpnService`，日志带完整 stderr 供报 issue |
| `TUNSETIFF` 失败 | `EPERM` | 同上，并在设置页写「设备不支持」 |
| 表号 / 优先级被占 | `ip rule add` 返回 `EEXIST` | helper 换下一段重试一次，仍失败则 `ERR`，应用回落 |
| 另一个 VPN 处于 always-on + 锁定模式 | 规则装上了但不通 | 启动前读 `Settings.Secure.always_on_vpn_lockdown`，为 `1` 时拒绝开启并说明原因 |

回落不静默：降到 `VpnService` 时用通知说明「共存模式不可用，已切回普通模式，会顶掉其它 VPN」。

## 安全约束

- 唯一提权组件是 helper，只接受固定文法：接口名 `^[a-z][a-z0-9]{0,14}$`、表号 3000–3099、
  优先级 12100–12900、CIDR 走 `inet_pton` 校验；其余命令一律拒绝。
- helper **不经过 shell**：`execv("/system/bin/ip", argv)` 直接调，网络下发的路由字符串不会进命令行解析。
- 连接鉴权靠 `SO_PEERCRED` 的 uid，不靠 socket 名；`nonce` 只防同名串连。
- 应用不保存 root 凭据，每次建隧道重新 `su`，不常驻 su shell。

## 模块（API 102）接入

- 依赖：`compileOnly("io.github.libxposed:api:102.0.0")`，状态查询 `implementation("io.github.libxposed:service:102.0.0")`。
  API 102 的 AAR 要求 minSdk 26，本项目 30，满足。
- 资源：
  - `src/main/resources/META-INF/xposed/module.prop`：`minApiVersion=102`、`targetApiVersion=102`、`staticScope=true`
  - `src/main/resources/META-INF/xposed/java_init.list`：入口类全名
  - `src/main/resources/META-INF/xposed/scope.list`：`net.sumitm.zotiera`（`.pr` 通道再加 `net.sumitm.zotiera.pr`）
- 入口类继承 `io.github.libxposed.api.XposedModule`，public 无参构造（API 102 不再要求旧式构造）。
- R8 规则：

  ```
  -dontwarn io.github.libxposed.annotation.**
  -adaptresourcefilecontents META-INF/xposed/java_init.list
  -keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule { public <init>(); }
  ```
- 「模块已启用」判定：`XposedServiceHelper` 注册监听，拿到 `XposedService` 后 `getScope()` 含本应用包名即视为启用；
  不读 LSPosed 的数据库（要 root，格式也不稳定）。
- 作用域只放本应用：只做状态判定与 UI 门禁，不 hook 系统进程。

## 开关条件

设置项默认隐藏，同时满足才显示并可打开：

1. `su -c id` 返回 `uid=0` 且 helper 能执行（P0 通过）；
2. libxposed service 已连接且 scope 含本应用（模块启用中）。

任一条件失效时自动关闭并回落到普通 `VpnService` 模式。

## 里程碑

| 阶段 | 任务 | 出口条件 |
| --- | --- | --- |
| P0 现场验证 | ①目标机 `su -c id`；②从 `lib/<abi>/` 执行打印 uid 的假 helper；③`Os.recvmsg` 收 fd 的最小验证；④手工建 tun + 12500 规则，另一个 VPN 保持在线；⑤取 `ip rule show` 与空表号 | 五条全过才进 P1；任一条不过就写「该机型不支持共存」 |
| P1 门禁与探测 | `RootProbe`（su / 设备 / 表号 / 锁定模式）、设置项开关与灰态、模块启用判定（API 102，独立于隧道） | 开关只在「root + 模块启用」时出现；关闭时完整回落 `VpnService` |
| P2 root 隧道 MVP | helper（建 tun、ip 命令、规则自愈、清理）、协议、`RootSession` 收 fd、`TunnelBackend` 抽象、`rebuildTunnel` 改造、通知状态 | 验收用例 1–7 通过 |
| P3 收尾 | DNS（本地转发 + nat 重定向）、按应用分流（owner + fwmark）、磁贴/设备卡状态、崩溃残留回归 | 三项都有明确行为与回归用例 |

模块只在 P1 作为「高级入口」出现，**与隧道实现无关**：没有模块也能用 root 模式，只是开关条件少一条。

## 验收用例（P2）

1. 另一个 VPN 在线时开启共存：`ztr0` 建起，`ip rule` 出现 N 条 12xxx，另一个 VPN 的 tun 与规则不变。
2. 另一台机器 ping 本机 ZT 地址通；本机 ping 对端 ZT 地址通。
3. 浏览器访问外网仍走另一个 VPN（`ip route get 1.1.1.1` 落到对方 tun）。
4. 关闭共存：`ip rule | grep '^12'` 为空、`ip route show table T` 为空、`ip link show ztr0` 不存在。
5. `kill -9` 应用后 10 秒内上述三项全部消失。
6. 拒绝 su，或把 helper 改名制造执行失败：开关不可用；开着的自动回落并给通知。
7. 加/删网络触发 `CONFIG_UPDATE`：地址与路由更新，`ztr0` 的 fd 不变（接口不重建），连接不断。

## 待确认

- 目标机型 `ip rule show` 的 12xxx 段是否为空；该 OEM 的 netd 段位是否与实测一致。
- `su` 域能否 exec APK 的 `lib/<abi>/` 下的可执行文件（Magisk / KernelSU 分别测）。
- `Os.recvmsg` 是否回填 `msg_control`（真机 + 模拟器各一次）。
- 另一个 VPN 的 always-on 锁定模式键值是否可读、为 `1` 时的实际表现。
- 非 root 下能否读到「当前有别的 VPN 在运行」（`ConnectivityManager` 的 TRANSPORT_VPN 对普通应用的可见性）。
- APK 同时带 launcher 与模块元数据时，LSPosed 管理器的展示与启用是否受影响。

## 风险

- OEM SELinux 策略可能拒绝 helper 建 tun 或改路由，只能回落，无法绕过。
- 另一个 VPN 在 always-on + 锁定模式下共存无解。
- 规则自愈是 15 秒周期，极端情况（netd 在两次自愈之间切网络）会有短暂分流错误。
- helper 常驻的功耗与保活；应用被杀后规则残留（已由 EOF 清理兜住，仍以用例 5 回归）。
- 带 root / LSPosed 的功能不能上 Google Play；本项目仍走 GitHub 与 `.pr` 通道。
