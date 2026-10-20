# 多网络同时连接 + 连接开关与选中解耦

## 0. 一句话

把「一个 VPN 里只能挂一个 ZeroTier 网络」改成「一个 VPN 挂多个网络」，并把
**隧道开关 / 网络成员资格 / 详情选中** 三件事拆成三个互不相干的状态。

| 概念 | 谁决定 | 存在哪 | 数量 |
| --- | --- | --- | --- |
| 隧道是否运行 | 设备卡上的总开关 | 服务是否存活 | 1 |
| 加入了哪些网络 | 每个网络行自己的开关 | `Network.lastActivated` | N |
| 正在看哪个网络 | 点哪一行 | UI 局部状态 | 1（与上面两个无关） |

> 2026-10-20 补充：同时在线还有一个前提——**同一个实例**（同 Planet 且同身份）。
> 跨实例的网络在概览页会被置灰，见 [`planet-identity-instances.md`](planet-identity-instances.md) 的 4.4。

---

## 1. 现状：单网络假设分布在哪

| 位置 | 现状 | 多网络下会怎样 |
| --- | --- | --- |
| `ZeroTierOneService.networkId`（第 102 行） | 单个 long | 只能 join 一个；组播扫描只扫这个 |
| `onStartCommand`（267-310） | 从 intent 取一个 id；数据库里多于一条直接 `throw IllegalStateException` | 第二个网络连上就崩 |
| `updateTunnelConfig(Network)`（772） | 每次先拆 TUN 与 VPN socket，再按**单个**网络重建 | 加第二个网络会把第一个的地址和路由全丢掉 |
| `TunTapAdapter.networkId`（第 37 行） | 构造时固定 | 出站包永远发往第一个网络；MAC、源地址也取错 |
| `NodeViewModel.connectNetworkId`（第 45 行） | 单个 `Long?` | UI 无法表达「两个都在线」 |
| `markActive(id)`（168） | 把其它网络的 `lastActivated` 全清零 | 数据库层面互相顶掉 |
| `onNetworkList`（180） | 用 `networks.first()` 猜当前网络 | 两个网络时状态来回跳 |
| `startService(networkId)`（117） | 启动顺序：先写 active 再 start | 同上的单值语义 |

## 2. 引擎其实已经具备的条件

这部分不用改核心、也不用改数据库结构：

- 核心 `node.join()` / `node.leave()` 本来就是按网络计的，`node.networkConfigs()` 返回数组。
- 服务里已有 `Map<Long, VirtualNetworkConfig> virtualNetworkConfigMap`，按网络存。
- `TunTapAdapter` 里已有 `routeMap: Map<Route, networkId>` 和 `networkIdForDestination()` ——
  但 `networkIdForDestination()` **定义了从没被调用**，出站包仍然用固定的 `this.networkId`。
- DB 里 `last_activated` 是每行一个布尔，`lastActivated()` 本来就返回列表（`ORDER BY network_id`）。

所以这不是「加功能」，而是「把已经预留的多网络路径接通，并拆掉三处单值假设」。

## 3. 服务层改造

### 3.1 网络集合取代单个 id

```
networkId: long            →   joinedNetworkIds: MutableSet<Long>（并发保护用 synchronized）
```

- `onStartCommand`：从 DB `lastActivatedNetworks()` **全量** join；intent 里的 id 作为「额外加入」；
  不再因为多于一条而抛异常。
- 新增两个事件指令：`JoinNetworkEvent(nwid)` / `LeaveNetworkEvent(nwid)`，服务侧订阅后
  join/leave 并维护集合。
- 组播扫描线程（v4/v6）遍历集合，而不是固定的 `this.networkId`。

### 3.2 隧道按「所有网络」重建

`updateTunnelConfig(Network)` → `rebuildTunnel()`：

| 项 | 规则 |
| --- | --- |
| 地址 | 每个网络的 `assignedAddresses` 全部 `addAddress` |
| 路由 | 并集；同一 CIDR 冲突时按 join 顺序先到先得，记一条 warning |
| 默认路由 | 只要**任一**网络是「全隧道」，就加该网络的默认路由 |
| DNS | 并集去重 |
| MTU | 取所有网络的最小值（0 视为 2800） |
| 分应用代理 | 仅当**没有**网络要求全隧道时，才 `addDisallowedApplication` |
| session 名 | 不变 |

触发重建的时机：任一网络 UP / CONFIG_UPDATE / DOWN、用户切换某网络的路由开关、leave 掉一个网络。

**幂等**：先算一个「配置指纹」= hash(地址集合 + 路由集合 + DNS 集合 + MTU + 是否全隧道)，
与上次相同就跳过重建。否则每次 `CONFIG_UPDATE` 都会拆一次隧道，连接会周期性抖动。

### 3.3 重建的代价

`establish()` 必须先关掉旧的 `vpnSocket`，中间有几百毫秒断流。可接受，但不能频繁发生——
所以指纹比对是必需的，不是优化项。

## 4. TunTapAdapter 改造

- 构造函数不再接收 `networkId`。
- 出站包（`handleIPv4Packet` / `handleIPv6Packet`）：
  1. `nwid = networkIdForDestination(destIP)`；
  2. 为 0 时用**源地址**反查（本机在哪个网络上有这个地址），处理回包；
  3. 仍为 0：丢弃并计数，日志里带上目的地址。
- 用解析出的网络的配置取 `mac`、本机地址、前缀长度——**每个网络的 MAC 不同**，不能共用。
- ARP / NDP 表必须按网络隔离：现在是一张全局表，两个网络的 MAC 空间会串味。
  表 key 加 `nwid`，或每个网络一张表（推荐后者，改动更小：`Map<Long, ArpTable>`）。
- `multicastSubscribe` 用解析出的 `nwid`。

## 5. UI 改造

### 5.1 设备卡（概览页顶部）

```
┌──────────────────────────────────────────────┐
│ (o)  69ca7484d4                      ⬤ 绿勾  │
│      2 networks · ONLINE                     │
└──────────────────────────────────────────────┘
```

- 大卡片：48dp 圆底图标 + 18sp 等宽节点 ID + 一行状态。
- 去掉 `1.16.2` 副标题（版本号移到设备详情页）。
- **不显示 MAC**：每个网络各有一个 MAC，放设备卡上只能挑一个，会误导；MAC 放到设备详情页的每网络卡片里。
- 副标题：`N 个网络 · 在线/正在连接/离线`。
- 右侧：**隧道总开关**，激活时整条轨道变绿、轨道左侧显示对勾（框架 Switch 画不了对勾，按同一尺寸自绘，交互仍走 `toggleable`）。
- 卡片整体可点，进入设备详情页；点开关不会触发跳转。

### 5.2 网络行

- 开关语义改成「加入此网络」：on = join，off = leave。
- 点行 = 选中看详情，**不改变任何连接状态**（现在也是这样，但要保证开关点击不冒泡到行）。
- 已加入的网络用主色标题，未加入的用普通色。
- 长按删除取消，删除改到网络详情页底部（带二次确认）。

### 5.3 设备详情页（点设备卡进入）

| 顺序 | 内容 |
| --- | --- |
| 1 | 客户端版本 / 核心版本 |
| 2 | 隧道 MTU + 「所有流量走 ZeroTier」全局开关 |
| 3 | 每个已加入网络两张卡：① 名称 / 网络 ID / MAC / 状态 ② 分配 IP |

### 5.4 网络详情页（点网络行进入）

- 名称 / 网络 ID / MAC / 状态 / MTU / DNS + 分配 IP。
- 底部「删除网络」，二次确认后删除；删除时若已加入会先离开该网络。
- 不提供 join/leave（成员资格只在列表开关上）。

## 6. 数据与迁移

- **不改 schema**：`last_activated` 已经是每网络一个布尔，只是以前只允许一行为 1。
- 老版本升级：升级前只有一条 `last_activated = 1`，升级后行为完全一致。
- 不需要 Room 迁移。

## 7. 分阶段

| 阶段 | 内容 | 完成标志 |
| --- | --- | --- |
| 1 | 服务：网络集合、rebuildTunnel、指纹比对、组播遍历 | 日志里两个网络同时 `NETWORK_STATUS_OK`，`ip addr` 能看到两组地址 |
| 2 | TunTapAdapter：按目的解析网络、MAC 按网络、ARP/NDP 分表 | 两个网络的子网都能 ping 通，互不串 |
| 3 | ViewModel：`connectedNetworkIds: Set<Long>`，join/leave 指令 | 断网重连后集合正确恢复 |
| 4 | UI：设备卡（图标/MAC/总开关）、网络行开关语义 | 关掉 A，B 仍在线；关总开关再开，A/B 都回来 |
| 5 | 截图验收 8 张 + 文档 + 提交 | 两风格 × 明暗 × 竖横 |

阶段 1、2 是引擎改动，只能实测（模拟器 + 两个可达网络），编译通过不算完成。

## 8. 验收清单

- [ ] 两个网络同时 `已连接`，Peers 页两个网络的节点都在
- [ ] 详情页分别显示各自分配的 IP（可能多个）
- [ ] 关掉 A：B 不断流，A 的地址从隧道移除
- [ ] 关总开关再打开：所有 `last_activated` 网络自动重连
- [ ] 一个全隧道网络 + 一个普通网络共存：默认路由只加一次
- [ ] 重复 `CONFIG_UPDATE` 不会引发隧道反复重建（日志里没有连续 `Configuring VpnService.Builder`）
- [ ] 两套风格 × 明暗 × 竖横截图

## 9. 风险

| 风险 | 说明 | 应对 |
| --- | --- | --- |
| 隧道重建断流 | 每次 `establish()` 都会断几百毫秒 | 配置指纹比对，未变不重建 |
| MTU 取最小 | 一个网络 MTU 小会拖累全部 | 可接受；极端情况再考虑按网络拆隧道 |
| ARP/NDP 串味 | 不同网络 MAC 空间独立，共用一张表会错发 | 按网络分表，阶段 2 必须做完 |
| 路由冲突 | 两个网络都下发 `10.0.0.0/8` | 先到先得 + warning，不做自动仲裁 |
| 全隧道冲突 | 两个网络都要默认路由 | 只加一次，第二个记 warning |
| 只能实测 | 涉及 JNI 与真实网络 | 阶段 1/2 用两个真实网络验证 |

## 10. 已确认的决策

| 问题 | 决定 |
| --- | --- |
| 总开关是否影响网络列表 | 不影响。网络行开关是多选，设备卡开关只管隧道 |
| 设备卡上的 MAC | 不显示；MAC 放在设备详情页的每网络卡片里 |
| MTU | 取所有已加入网络的最小值 |
| 网络详情页 | 加「删除网络」按钮（二次确认） |
| 全隧道开关 | 提升为设备级全局开关，放设备详情页；网络卡片上移除 |
| 全隧道与普通网络共存 | 不允许：全局开关是设备级的，不存在「某网络全隧道」的混合状态 |
| DNS | 保留每网络配置，建隧道时取并集 |

## 11. 实施进度

- [x] 服务：`joinedNetworkIds` 集合、`rebuildTunnel()` 聚合、配置指纹、组播按网络遍历
- [x] 适配器：按目的地址选网络、MAC 取所属网络、ARP/NDP 按网络分表
- [x] ViewModel：`joinedNetworkIds` / `tunnelRunning` / `configs` 三个状态分离
- [x] UI：设备卡（图标 / 节点 ID / N 个网络 · 状态 / 绿色带勾开关）、网络行多选开关
- [x] UI：设备详情页、网络详情页删除入口
- [x] 实测：两个网络同时 `Joined network … (total 2)`，关掉一个另一个不断
- [ ] 带地址的双网络路由（需要能授权的网络；当前两个测试网络都是 NOT_FOUND，拿不到地址）
