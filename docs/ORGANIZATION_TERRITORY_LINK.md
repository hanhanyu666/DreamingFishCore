# 组织 ↔ 圈地联动

> 相关决策：[ADR 0017](../../adr/0017-settlements-can-suppress-passive-contagion.md)（聚居地抑制设备）、
> [ADR 0035](../../adr/0035-buildable-story-locations-use-economy-territories.md)（不建第二份领地数据库）、
> [ADR 0041](../../adr/0041-organization-fund-pool-is-an-internal-ledger.md)（资金池是内部账）
>
> 术语以 `CONTEXT.md` 为准；领地数据、权限与费用**全部归 EconomySystem**。

## 一、三块功能

| 功能 | 玩家看到的东西 | 真值在哪 |
| --- | --- | --- |
| 组织领地登记 | 终端「组织」页的领地分区；`/organization territory` | EconomySystem（我们只存 territoryId 引用） |
| 组织资金池 | 终端里的余额与「捐款」按钮；`/organization deposit <金额>` | 本模组的组织存档（内部账） |
| 聚居地过滤装置 | 一台可放置的方块，右键绑定组织 | 设备登记表 + 方块状态 |

## 二、组织领地（只存引用）

- 会长与管理员可以把自己名下、**尚未被任何组织登记**的领地登记给组织；拆除登记随时可做。
- 我们的存档里只有 `territoryId → 登记时间`。展示与使用前都会用 `territoriesByOwner` /
  `territory(id)` 重新校验：
  - 领地已不存在 → 界面上显示「已失效的登记」；
  - 领地主人已退会/转会后组织成员 → 自动摘除（登录与服务启动时各校验一次）；
  - **经济服务读不到时直接跳过校验**，绝不清空登记（把"读不到"当成"没有领地"会一次删光）。
- 领地**不额外收费**：EconomySystem 已经收过圈地费，我们无权取消别人的领地，所以不给它加罚则。

## 三、组织资金池

- 任何成员都能捐款：`EconomySystemBridge.debit` 从**个人**梦鱼币账户扣除 → 入组织账 → **立刻写盘**；
  写盘失败则自动退款并把失败原因告诉玩家（不会出现"钱花了组织没记上"）。
- 余额**只用于系统扣费**（设备维护费），**不提供取现入口** —— 所以不存在"谁能动这笔钱"的争议。
- 解散组织时余额**全额退给会长**（只有会长能解散，所以发起人就是会长）；退款失败只记日志、不阻塞解散。
- 组织解散会同时解绑它名下的所有设备，避免设备继续"工作"却没人付费。

## 四、聚居地过滤装置（ADR 0017）

- 方块 ID `dreamingfishcore:settlement_filter`；配方：铁块×4 + 玻璃×4 + 金苹果 + 下界合金锭。
- **工作条件**（三者同时满足，由维护周期判定）：
  1. 已绑定组织；
  2. 仍位于该组织**已登记的领地内**；
  3. 本周期维护费已从组织资金池扣除成功。
- 任一条件不满足 → 停机（方块变暗、不再发光），并把原因发给在线组织成员：
  被拆 / 不在领地内 / 组织解散 / 资金不足。
- 抑制范围：**水平半径**（默认 32 格，`filterRadius`）、同维度、贯穿高度 —— 与 EconomySystem
  把领地当作竖直柱的语义一致，避免"在同一块地的地下就不算被保护"。
- 只阻断**被动接触暴露**：不治疗既有感染，也不阻止受伤、污染物与特殊袭击造成的感染（ADR 0017）。
  接入点是 `InfectionEventHandler.hasSpreadingSourceNearby`：命中设备覆盖就直接返回"没有传播来源"。
- 无方块实体：`SettlementFilterRegistry`（`<world>/data/dreamingfishcore/settlement_filters.json`）
  是设备状态的唯一真相，方块只通过 `active` 方块状态把"在不在干活"显示给玩家。

## 五、权限

| 动作 | 要求 |
| --- | --- |
| 捐款 | 任何成员（只进不出） |
| 登记 / 移除组织领地 | 会长、管理员（`canManageTerritories`） |
| 绑定 / 解绑设备 | 会长、管理员；且必须站在本组织已登记的领地内 |
| 放置设备本身 | 任何玩家（但没绑定就不工作） |

界面按钮的可点性由服务端下发的开关决定（`canDepositFunds` / `canManageTerritories`），
客户端不自己判断权限，避免"按钮画得出来、服务端拒绝"。

## 六、配置

`config/dreamingfishcore/organization.json`，改完用 `/organization reload` 生效：

| 键 | 默认 | 含义 |
| --- | --- | --- |
| `maxRegisteredTerritories` | 4 | 每个组织最多登记的领地数 |
| `maxFilterDevices` | 2 | 每个组织最多绑定的设备数 |
| `filterRadius` | 32 | 设备抑制的水平半径（格） |
| `filterMaintenanceCost` | 20 | **每台设备每周期**从资金池扣的梦鱼币（0 = 免费） |
| `filterMaintenanceIntervalTicks` | 24000 | 维护周期（一个剧情活动日，仅在线累计） |
| `maxDeposit` | 10000 | 单次捐款上限 |

维护费是**每台设备每周期**扣一次：2 台就是双倍。想在"按组织收一次"或"按面积收"的方向改，
改 `SettlementFilterService.tick` 里的循环即可。

## 七、命令与界面

```
/organization territory                              查看已登记与可登记的领地
/organization territory register <领地名或领地 id>    登记（同名多块时要求用 id）
/organization territory remove <领地名>              移除
/organization deposit <金额>                         向资金池捐款
```

终端「组织」页（U 键 → 组织）在公告下方新增「领地与资金」分区：资金池余额与捐款按钮、
已登记领地（含「已失效」标记与移除按钮）、可登记领地（登记按钮）、设备列表（工作/停机）。

## 八、边界与未实装

- 领地**免费**登记；我们没有取消别人领地的能力，因此也没有"欠费没收"这类罚则。
- 设备维护费只从**组织资金池**扣，不从个人账户直接扣（否则离线成员会被莫名扣钱）。
- 组织之间不能互相转账，资金池也不能取现 —— 这是刻意的（见 ADR 0041）。
- 领地权限（谁能建造、谁能进出）仍由 EconomySystem 判定，本模组不参与。
- 里程碑 6 的其他部分（领地所有者/成员/场地管理员权限、剧情事件使用玩家场地）尚未实装。

## 九、验证

- 单测：配置钳制与旧配置补键、资金池账本（入账/扣费/不足）、领地差集与坐标覆盖、
  设备状态语义（未绑定不算工作）与维护到期判定、组织权限（领地管理/捐款）。
- 无头 gametest：`workingFilterSuppressesPassiveExposure` —— 用真实服务端玩家与真实方块验证
  "停机不抑制 / 工作则阻断被动暴露"这条链路。
- 实机：尚未在真人环境跑过完整的"圈地 → 登记 → 放设备 → 绑定 → 观察抑制"流程。
