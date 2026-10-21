# 共存模式（与其它 VPN / ZeroTier 客户端同时运行）

- 状态：草案（调查完成，待决策）
- 建立日期：2026-10-21
- 基线提交：`9578f7a`
- 事实依据：
  - Android VPN 指南 <https://developer.android.com/develop/connectivity/vpn>：每个用户/资料只有一个活动 VPN，
    「Starting a new service automatically stops an existing service.」
  - `VpnService.Builder.establish()` 文档：`To avoid conflicts, there can be only one active VPN interface at the same time.`
  - 现有实现：`app/src/main/java/net/sumitm/zotiera/service/ZeroTierOneService.java`（`VpnService.Builder` 建 tun）、
    `TunTapAdapter.java`（用 `ParcelFileDescriptor` 收发）
  - libxposed API 102：`io.github.libxposed:api:102.0.0`（AAR 声明 minSdk 26）；
    元数据为 `META-INF/xposed/module.prop` / `java_init.list` / `scope.list`
  - libxposed service 102：`XposedServiceHelper`、`XposedService.getScope()`、`getRunningTargets()`
  - LSPosed 的模块启用状态存在它自己的数据库（`daemon/.../ConfigFileManager.dbPath`），模块应用读不到，需走 service

## 问题

现在只要本应用建立 VPN，系统会停掉已有的 VPN；反过来别的 VPN（包括其它 ZeroTier 客户端）启动也会把我们停掉。
用户要的是两者同时在线，本应用只负责局域网段。

## 结论（先看这里）

1. **注册成 LSPosed API 102 模块本身不能实现共存。** 「每个用户只有一个 VPN」是
   `VpnManagerService` / `ConnectivityService` 的结构（每用户一个 `Vpn` 对象、一个 tun），
   不是一处可以绕过的判断。
2. **可行路径是绕开 `VpnService`**：用 root 建 tun、按目的网段装路由规则，ZeroTier 核心照旧跑在我们的进程里。
3. 模块在这个方案里只负责两件事：判定「模块已启用」、给共存模式一个高级入口。没有模块也能做；没有 root 做不了。

## 为什么 hook 双 VPN 不可行

- `Vpn` 是 per-user 单例：一个 tun、一套地址/路由/DNS；第二个 VPN 没有地方挂。
- 即使 hook 掉校验让第二个 `establish()` 成功，系统也只有一个「当前 VPN」网络，
  应用侧的路由与解析不会同时走两个 tun。
- 代价是全局网络选择被改坏，影响所有应用，风险远大于收益。

因此不把「hook 系统 VPN 栈」列入候选。

## 候选方案

| 方案 | 做法 | 可行性 | 代价 |
| --- | --- | --- | --- |
| A 模块 hook 双 VPN | 改 `VpnManagerService` / `Vpn` 接受第二个 tun | 不可行 | 等于重写 VPN 栈，系统级风险 |
| B root 模式 tun + 策略路由 | root 建 tun、装 `ip rule`/`ip route`，核心用传进来的 fd | 可行 | 需要 root；DNS 与分流要另做；helper 生命周期 |
| C 不做共存 | 启动前检测到别的 VPN 就提示「会替换」 | 现状 | 用户诉求未满足 |

推荐 B，A 不列入计划。

## B 方案技术拆解

### 建立隧道

1. `su` 启动 helper（native 可执行文件或固定命令脚本）：
   - `ip tuntap add dev zt<x> mode tun`
   - `ip addr add <assigned>/<prefix> dev zt<x>`（用 ZeroTier 分配的地址）
   - `ip link set zt<x> up mtu <mtu>`
   - 每条托管路由：`ip route add <cidr> dev zt<x> table <t>`，
     `ip rule add to <cidr> lookup <t> pref <p>`
2. helper 用 `AF_UNIX` + `SCM_RIGHTS` 把 tun fd 交给应用；应用用 `ParcelFileDescriptor.fromFd()` 包一层，
   交给现有 `TunTapAdapter`。核心与包泵不改。
3. 停止时逆序删 rule / route / 接口；崩溃后下次启动先清理残留。

### 为什么能共存

不走 `VpnService`，系统的「只有一个 VPN」规则完全不参与；`ip rule` 按目的网段查我们自己的表，
优先级高于另一条 VPN 的默认路由。访问 ZeroTier 网段走我们的 tun，其余流量照旧走对方的 VPN。

### 难点

1. **DNS**：Android 解析器只认「网络」的 DNS，我们的 tun 不是网络。共存模式先只支持「不使用 DNS」，
   后续再做本地 DNS 转发 + 按 uid 的 `iptables` 重定向，或按目的地址把查询导进我们的表。
2. **按应用分流**：现有 `DISALLOWED_APPS` 只对 `VpnService` 生效，root 模式要改成 uid 规则。
3. **路由优先级**：`netd` 自己也管 `ip rule`，表号与优先级段要避开；不同 OEM 段位不同，需目标机型确认。
4. **root 安全**：helper 只接受固定命令模板，参数来自本地配置，不拼接网络下发的内容。
5. **没有 VPN 图标**：系统不显示钥匙图标，状态只能靠前台通知与磁贴。

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

1. `su -c id` 返回 `uid=0`；
2. libxposed service 已连接且 scope 含本应用（模块启用中）。

任一条件失效时自动关闭并回落到普通 `VpnService` 模式。

## 里程碑

| 阶段 | 内容 | 出口条件 |
| --- | --- | --- |
| P0 现场验证 | 临时脚本在目标机型建 tun、装 `ip rule`，另一个 VPN 保持连接 | ping 通 ZeroTier 网段地址，另一个 VPN 不断线，删除规则后恢复原状 |
| P1 模块骨架 | API 102 注册、service 状态判定、设置项门禁 | LSPosed 模块列表出现本应用；开关只在「模块启用 + root」时出现 |
| P2 root 隧道 MVP | helper、fd 传递、路由生命周期、共存开关；DNS 固定「不使用」 | 与另一个 ZeroTier 客户端同时在线且 LAN 可达；停止后无残留 rule/route/接口 |
| P3 收尾 | DNS 方案、按应用分流、崩溃/重启清理、磁贴状态 | 三项都有明确行为与回归用例 |

P0 不通过就停在 P0，明确写「不支持共存」，只在启动时检测并提示会替换现有 VPN。

## 待确认

- 目标机型上 `netd` 的 `ip rule` 优先级段与可用表号（真机取 `ip rule show`、`ip route show table all`）。
- 非 root 下能否读到「当前有别的 VPN 在运行」（`ConnectivityManager` 的 TRANSPORT_VPN 对普通应用的可见性）。
- APK 同时带 launcher 与模块元数据时，LSPosed 管理器的展示与启用是否受影响（需实机确认）。

## 风险

- OEM SELinux 策略可能拒绝 helper 建 tun 或改路由。
- helper 常驻的功耗与保活；应用被杀后规则残留。
- 带 root / LSPosed 的功能不能上 Google Play，本项目仍走 GitHub 与 `.pr` 通道。
