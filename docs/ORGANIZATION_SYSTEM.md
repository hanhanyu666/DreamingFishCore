# 玩家组织系统

玩家自建社群（俗称"玩家工会"）。入口是终端「组织」页（按 `U` 打开终端 → 底部快捷栏「组织」或左侧一级模块「组织」），
同时保留 `/organization` 命令作为无界面入口与排障手段。

> 与世界观里的**逐光会成员身份**是两件事：逐光会是剧情设定的官方归属，由 `gameplay/zhuiguang_system` 管理；
> 本系统的「组织」是玩家之间的自愿结社，不改剧情阶段、不改感染身份。术语定义见根目录 `CONTEXT.md` 的「玩家组织」条目。

## 数据在哪里

| 内容 | 位置 | 说明 |
| --- | --- | --- |
| 组织数据 | 世界存档 `data/dreamingfishcore/organizations.json` | 会长、成员与职位、申请、邀请、公告、创建时间、**创建时实付金额** |
| 服主配置 | `config/dreamingfishcore/organization.json` | 开关、组织数上限、人数上限、名称长度、公告长度、**创建费**、**解散退款比例** |

写入与世界数据的加载/保存/清理都挂在 `server/persistence/event/WorldDataLifecycleEvents`，
与公告、体征等其它世界数据同一套生命周期，不单独开线程也不直接覆盖写文件。

## 创建费与退款

创建组织要消耗**梦鱼币**（EconomySystem 的货币，本模组通过其 Public API v1 扣款）。

| 配置项 | 默认 | 含义 |
| --- | --- | --- |
| `creationCost` | `150` | 创建一个组织需要的梦鱼币；`0` = 免费创建 |
| `disbandRefundPercent` | `50` | 解散时按**实付**金额退还的百分比；`0` = 不退 |

规则：

- **经济服务不可用时按免费放行**（未安装 EconomySystem 或 API 版本不兼容时，创建不收费也不失败）；
- 余额不足会**在建会之前**就被拦下，提示带上当前余额，不会出现"钱扣了但组织没建成"；
- 退款按组织记录里的 `creationCostPaid`（**当初实际付了多少**）计算，不是按当前配置 ——
  所以经济服务不可用时免费建的组织，解散时一分钱也退不出来；
- 退款只发给**发起解散的会长**；`/organization force-disband`（OP 强制解散）**不退款**；
- 扣款成功后会**立即写盘一次**；若这次写盘失败，会自动退款并回滚。

> 实现位置：`OrganizationManager.chargeCreationCost` / `refundDisbandCost`；
> 账户操作统一走 `EconomySystemBridge.debit` / `credit`（外层不暴露 EconomySystem 类型，
> 未安装时整个桥接层可安全卸载）。

## 服务端（唯一权威）

- `OrganizationManager`——**所有写操作的唯一入口**：创建 / 解散 / 强制解散 / 申请 / 撤回申请 / 审批 /
  邀请 / 应邀请 / 退出 / 踢人 / 任免 / 转让 / 公告 / 改名 / 登录刷新名 / 构建快照。
  界面与命令都调它，不存在"命令一套、界面一套"。
- `OrganizationPermissions`——权限矩阵，纯静态逻辑，可单测。**界面按钮的显隐也读这里**，
  所以「界面画得出来」和「服务端真的允许」永远一致。
- `OrganizationRank`——职位层级与权重：`会长 > 管理员 > 干部 > 成员`；比较一律走权重。
- `OrganizationNames`——名称 / 公告校验（按**码点**算长度，禁止内部空格、颜色代码、控制符）。
- `Organization` / `OrganizationDocument`——持久化模型与文件结构；加载时逐条校验，
  损坏条目跳过并记日志，**不会用空数据覆盖存档**。

规则要点：一人只能在一个组织；会长不能直接退出（必须先转让或解散）；踢人 / 任免要求严格高于对方，
且都不能作用于会长；任何人都不能修改自己的职位。

## 权限矩阵

| 操作 | 会长 | 管理员 | 干部 | 成员 |
| --- | --- | --- | --- | --- |
| 审批申请 / 邀请 / 编辑公告 | ✅ | ✅ | ✅ | ❌ |
| 踢人 | ✅ | ✅ | 仅成员 | ❌ |
| 任免职位 | ✅ | 仅干部 ↔ 成员 | ❌ | ❌ |
| 改名 / 转让 / 解散 | ✅ | ❌ | ❌ | ❌ |

## 读档失败时的只读保护

组织数据**一旦读不出来就绝不回写**。`loadWorldData` 会把读档结果分成三类
（`OrganizationManager.classify`，纯函数、有单测）：

| 结果 | 场景 | 处理 |
| --- | --- | --- |
| `OK` | 正常读到；或文件不存在、使用默认空文档 | 正常读写 |
| `UNSUPPORTED_SCHEMA` | 文件 `schemaVersion` 不是本版本能处理的（例如用旧版本打开新存档） | **进入只读保护**，保留文件内容 |
| `FAILED` | 解析结果不可用 / 读档抛异常 | **进入只读保护**，保留文件内容 |

只读保护期间：

- 内存保持空数据，终端页面照常打开（不会一直卡在"正在同步"）；
- `saveIfDirty` 直接跳过写盘，并在日志里报一条错误；
- 一切写操作（如创建组织）返回「组织数据读取失败，本次已进入只读保护；请重启服务器后重试」；
- **重启后自动重试读取**，不需要人工干预。

> 为什么要这么做：只读保护缺失时，一次瞬时读取失败会让内存变成空列表，而关服保存
> （或任何一次数据变更后的保存）就会把这个空列表写回文件 —— **等于一次读取故障清空全部组织**。
> 老实现虽然在日志里写着"不覆盖文件"，但并没有真正拦住写盘。

## 网络协议

| 包 | 方向 | 用途 |
| --- | --- | --- |
| `Packet_OrganizationSnapshotRequest` | C→S | 请求快照（打开页面、点刷新、打开终端时各发一次） |
| `Packet_OrganizationSnapshotResponse` | S→C | 整份只读快照；**权限开关由服务端算好** |
| `Packet_OrganizationActionRequest` | C→S | 一个包承载全部动作，用 `Action` 枚举区分 |
| `Packet_OrganizationActionResult` | S→C | 操作结果，直接以聊天栏回话 |

客户端传来的一切都当不可信输入：动作在服务端重新校验身份、职位、上限。
任何一次成功操作之后服务端都会**广播新快照**，界面不需要自己拼增量。

> 新增包属于协议契约变更，`DreamingFishCore_NetworkManager.PROTOCOL_VERSION` 已随之提升；
> 旧客户端会在握手阶段被拒绝。**测试时必须前后端用同一份构建。**

## 终端页面

- `OrganizationPage`（`server_ui_system/client/terminal/`）——终端底部 Dock 的「组织」模块，基于 UI 框架：
  左侧组织名录（实时搜索、创建组织），右侧组织详情（公告、资金与领地、入会申请、成员管理）；
  紧凑布局下点组织进入单独的详情页。组件与弹窗动作在 `OrganizationViews` 里。
- 建会、改名、写公告、邀请、捐款与各类确认通过 `TerminalPrompt` 弹出，直接浮在终端上，
  不切换界面，关闭后玩家仍停留在组织页。
- 操作结果除了进聊天栏，也会写入 `OrganizationClientCache.lastResult()`，组织页底部显示几秒提示（终端挡住了聊天栏）。
- 客户端只读缓存 `OrganizationClientCache`；玩家断开连接时由 `ClientCacheManager.clear()` 一并清空。

## 命令

```
/organization create <名称>          创建组织
/organization rename <新名称>        改名（会长）
/organization announce <内容>        编辑公告（干部及以上）
/organization apply <组织>           申请加入
/organization cancel <组织>          撤回申请
/organization review <玩家>          批准申请（干部及以上）
/organization reject <玩家>          拒绝申请（干部及以上）
/organization invite <玩家>          邀请（干部及以上，要求对方在线）
/organization accept <组织>          接受邀请
/organization decline <组织>         拒绝邀请
/organization kick <玩家>            移出成员
/organization rank <玩家> <职位>      调整职位
/organization transfer <玩家>        转让会长（会长）
/organization leave                  退出组织
/organization disband                解散组织（会长）
/organization list | info            列表 / 详情
/organization reload                 重载配置（OP）
/organization force-disband <组织>    强制解散（OP）
```

涉及其他玩家的操作**要求对方在线**：离线时拿不到权威显示名，宁可报错也不把猜测的名字写进存档。

## 尚未实现

- 组织等级、经验、任务、仓库、共享领地等成长型玩法；
- 组织聊天频道（当前只有终端页面与命令）；
- 组织徽章 / 图标；
- 邀请的离线投递（当前邀请也要求对方在线）。
