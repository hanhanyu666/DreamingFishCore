# 搜打撤（Raid / 撤离射击）完整说明书

| 项目 | 值 |
| --- | --- |
| 模组 | `dreamingfishcore` |
| 版本 | `2.3.3`（未发布，开发中） |
| 环境 | Minecraft `1.21.1` / NeoForge `21.1.228` / Java 21 |
| **协议版本** | **`0.32.0`**（客户端与服务端必须一致，否则连不上） |
| 文档定位 | **唯一权威说明书**（合并了部署、制作、使用、排查、开发约定） |
| 适用读者 | 服主 · 地图作者 · 测试玩家 · 后续开发者 |

> 📌 本文所有命令以**游戏内 Tab 补全**为准。带 `<中文>` 的是参数占位符，实际输入英文。

---

## 目录

1. [一分钟上手](#1-一分钟上手)
2. [它是什么（玩法总览）](#2-它是什么玩法总览)
3. [功能清单与完成度](#3-功能清单与完成度)
4. [部署（服主）](#4-部署服主)
5. [配置项总表](#5-配置项总表)
6. [地图制作全流程（地图作者）](#6-地图制作全流程地图作者)
7. [开一局：完整流程与内部发生了什么](#7-开一局完整流程与内部发生了什么)
8. [玩家指南：一局怎么打](#8-玩家指南一局怎么打)
9. [命令总表](#9-命令总表)
10. [数据文件与事件日志](#10-数据文件与事件日志)
11. [排查手册](#11-排查手册)
12. [测试清单（可直接发给测试玩家）](#12-测试清单可直接发给测试玩家)
13. [设计约定与不变量（给后续开发者）](#13-设计约定与不变量给后续开发者)
14. [已知限制与未做项](#14-已知限制与未做项)
15. [本轮变更记录](#15-本轮变更记录)

---

## 1. 一分钟上手

```
【服主】
/dreamingfish raid new abandoned_factory 2 8     ← 开局（地图名 难度 期望人数）
/dreamingfish raid info                          ← 看本局状态
/dreamingfish raid end                           ← 收尾（清战利品并还原容器）

【玩家】
进图搜物资 → 地上有漂浮的小物品（走过去自动捡 / 右键捡最近的）
          → 站进发光点读条撤离（离开会中断）→ 撤出后背包带走
          → 死了就带不出去；掉线 5 分钟内回来还在局里
          → 必须生存/冒险模式（创造模式只会收到提示）
```

---

## 2. 它是什么（玩法总览）

一局搜打撤 = **服主开局 → 玩家进图搜物资 → 活着撤离 → 带走的归自己；死了丢在尸体里**。

mod 负责把"这一局"变成一个**可复现、可审计的对象**：

- **确定性**：每局有一个由 `服务器种子 + 地图名 + 局号` 算出的种子。**同一个局号重开，箱子内容、撤离点、地上物品完全一样**——所以玩家报 bug 时我们能复现，而不是"我这边看不一样"。
- **服务端权威**：谁捡走了什么、能不能撤、撤到哪里，全部由服务端判定。客户端只负责显示与发起请求。
- **可回滚**：往容器里填战利品前会**记录原内容**，结束或重开时**还原**。不会把你的地图箱子搞乱。
- **可审计**：`raid_stats.jsonl` 一行一条事件（开局 / 撤离 / 死亡 / 拾取 / 结束），用于排查与平衡。

---

## 3. 功能清单与完成度

| # | 模块 | 内容 | 状态 |
| --- | --- | --- | --- |
| 0 | 锚点系统 | 8 种锚点类型 + 标签体系 + 世界层覆盖 + 校验/导出 | ✅ |
| 1 | 对局身份与种子 | SplitMix64 确定性随机、子系统互不干扰、对局历史 | ✅ |
| 2 | 战利品 | 物品价值表、区域预算、稀有规则、容器填充、**覆盖与还原** | ✅ |
| 3 | 撤离 | 四类撤离点 + 权重/距离/出生组筛选 + 保底 + 粒子标识 | ✅ |
| 3 | 撤离流程 | 读条（半径/时长可配）→ 传送 → 结算 → 限次与持久化 | ✅ |
| 3 | 参与者名单 | 只有开局在线者算参与者、掉线宽限、无人自动结束 | ✅ |
| 4 | 露天物品 | 漂浮模型显示 + 附近物品列表 + 自动/右键拾取 | ✅ |
| 5 | 地图变体 | 按权重选状态 + **连通性校验** + 抽不合格退回无变体 | ✅ |
| 5 | 变体方块 | 准心登记 → 应用 → 一键还原（记原方块） | ✅ |
| 5 | 竞技场维度 | 虚空超平坦 + 不刷怪 + 有日夜 | ✅ |
| 5 | 区域块 | 抓取 / 放置 / 重置 / 列表（原版结构模板） | ✅ |
| 5 | 开局自动化 | 拨本局起始时间 + 自动送进竞技场 + 收尾自动召回 | ✅ |
| 5 | 统计 | 事件日志 JSONL | ✅ |
| 5 | **布局规划器** | 按种子自动挑零件拼一张新图 + 跨 tick 构建 + 等候区 | ❌ 未做 |
| 5 | mod 层刷怪拦截 | 当前靠虚空群系与维度类型保证 | ⚪ 不必要（见 §14） |

---

## 4. 部署（服主）

### 4.1 装什么

| 位置 | 内容 | 说明 |
| --- | --- | --- |
| 服务端 | `dreamingfishcore-2.3.3.jar` | NeoForge 21.1.228 / MC 1.21.1 |
| 客户端 | **同一个 jar** | 有客户端显示逻辑（漂浮物品模型、附近物品列表、UI），**必须装** |

> ⚠️ **协议版本 `0.32.0`**：服务端与客户端不一致会直接连不上。这是有意的设计——避免"半新半旧"导致难以定位的诡异 bug。更新时**两边一起换**。

### 4.2 权限等级

| 命令组 | 需要等级 | 说明 |
| --- | --- | --- |
| `/dreamingfish raid ...` | **2** | 开局、查询、收尾、地图工具 |
| `/dreamingfish raid_anchor ...` | **2** | 放/删/标签锚点 |
| `/dreamingfish task_location ...` | **3** | 区域（zone）定义，普通管理员改不到 |

### 4.3 部署检查单

- [ ] 服务端与所有客户端的 jar 版本一致（协议 `0.32.0`）
- [ ] 首次启动生成 `config/dreamingfishcore/raid.json`
- [ ] 地图数据包已放入（`raid_items` / `raid_zones` / 可选 `raid_maps`）
- [ ] 锚点已放好：`/dreamingfish raid_anchor validate` 无报错
- [ ] 试跑一局：`raid new` → 看箱子/撤离点/地上物品 → `raid end` → 容器已还原
- [ ] `<存档>/dreamingfishcore/` 已纳入备份

---

## 5. 配置项总表

### 5.1 服务器配置 `config/dreamingfishcore/raid.json`

首次启动自动生成。改完 `/reload` 生效（部分项重启更稳）。所有数值都会夹取到合法区间；文件写坏会回退默认值并且**不覆盖你的文件**（你能看出自己写错了）。

| 键 | 默认 | 范围 | 含义 |
| --- | --- | --- | --- |
| `extraction_radius` | `3.0` | 0.5 ~ 16 | 撤离读条半径（格）。走出这个范围读条中断 |
| `extraction_seconds` | `5` | 1 ~ 60 | 撤离读条时长（秒） |
| `auto_end_seconds` | `1800` | 0 ~ 86400 | 本局时长上限（秒）；**0 = 关闭"时间到自动结束"** |
| `auto_end_when_all_out` | `true` | 布尔 | 图里没参与者了就自动结束 |
| `logout_grace_seconds` | `300` | 0 ~ 3600 | 掉线宽限（秒）；超时按"未撤离"结算 |
| `exit_dimension` | `minecraft:overworld` | 维度 id | 撤离成功后送到哪个维度 |
| `exit_position` | `null` | `[x,y,z]` | 撤离落点；`null` = 该维度出生点 |

**快速验证技巧**：把 `extraction_seconds` 改成 `1`、`auto_end_seconds` 改成 `60`，一局几十秒就能跑完全流程。

### 5.2 代码内常量开关（改完要重新编译）

| 位置 | 常量 | 默认 | 作用 |
| --- | --- | --- | --- |
| `RaidArenaService` | `ARENA_ID` | `dreamingfishcore:raid_arena` | 竞技场维度 |
| | `SPAWN_X/Y/Z` | `0.5 / 100 / 0.5` | 进场落点 |
| | `START_TIME` | `1000` | 开局把世界时间拨到（1000 ≈ 清晨） |
| | `SET_TIME_ON_START` | `true` | 是否开局拨时间（原版共享时钟，会影响主世界） |
| | `TELEPORT_ON_START` | `false` | 是否开局自动把参与者送进竞技场（**默认关**：地图还没搬进竞技场前别开） |
| | `RECALL_ON_END` | `true` | 是否收尾时把竞技场里的人送回出口 |
| `LooseLootService` | `PICKUP_RADIUS` | `1.5` | 露天物品自动拾取半径（格） |
| | `MARKER_INTERVAL_TICKS` | `10` | 粒子标识与定时同步的节流 |
| `LooseLootPlanner` | `DEFAULT_VALUE_CAP` | `600` | 露天物品单个点的生成成本上限 |
| | `MAX_PICKUP_DISTANCE` | `3.0` | 服务端认可的拾取距离上限（格） |
| `RaidRegionService` | `MAX_EDGE` / `MAX_VOLUME` | `128` / `32768` | 区域块边长与体积上限 |
| `RaidVariantService` | 抽取次数上限 | `20` | 变体组合抽多少次仍不合格就退回无变体 |

> ⚠️ **上线前必看**：`TELEPORT_ON_START` 默认 `true`。如果你的地图内容还在**主世界**，开局会把玩家送进**虚空竞技场**。把地图搬进竞技场（用 §6.7 的区域块），或先把它改成 `false`。

---

## 6. 地图制作全流程（地图作者）

### 6.1 区域（zone）：先把地图分区

战利品预算按**区域**给。区域定义用：

```
/dreamingfish task_location <子命令...>      ← 需要权限 3
```

区域是一个 3D 盒子，有 `id` 与显示名，可被锚点与物品表引用（支持用 id 或显示名引用）。

### 6.2 物品价值表：`raid_items/<名字>.json`

每件物品声明**生成成本**（不是售价！）与稀有度权重：

```json
[
  { "item": "minecraft:iron_ingot", "category": "material", "rarity": "COMMON",
    "spawn_cost": 120, "rarity_weight": 100 },
  { "item": "minecraft:diamond", "category": "valuable", "rarity": "RARE",
    "spawn_cost": 1500, "rarity_weight": 5, "global_limit": 2 }
]
```

- `global_limit`：**本局全局上限**，与露天物品**共用同一份配额**（不会出现"容器里放满了钻石、地上还有一堆"）
- **本 mod 不定义售价**：只有 `spawn_cost` / `combat_score` / `rarity_weight`。售价以 EconomySystem 为唯一来源

### 6.3 区域模板：`raid_zones/<名字>.json`

按区域给预算、稀有规则、容器数量：

```json
{
  "zone": "factory_office",
  "budget": 4800,
  "container_count": 4,
  "rare_rules": [ { "rarity": "RARE", "max": 1 } ]
}
```

### 6.4 锚点：一切"在地图上标点"都用它

```
/dreamingfish raid_anchor place <区域id> <类型> <锚点id>
```

| 类型（写英文） | 用途 |
| --- | --- |
| `CONTAINER_LOOT` | 可刷战利品的箱子/容器 |
| `LOOSE_LOOT` | 地上随手能捡的物品（渲染成漂浮小模型） |
| `EXTRACTION` | 撤离点 |
| `MAP_VARIANT` | 预留（当前方块切换用 `raid variant_block`，不必用它） |

**例**：

```
/dreamingfish raid_anchor place factory_office CONTAINER_LOOT office_chest_01
/dreamingfish raid_anchor place factory_office LOOSE_LOOT     office_desk_01
/dreamingfish raid_anchor place factory_office EXTRACTION     exit_a
```

**维护命令**：

```
/dreamingfish raid_anchor list                     列出全部
/dreamingfish raid_anchor remove <锚点id>          删除
/dreamingfish raid_anchor enable|disable <锚点id>  启停（不用删掉重放）
/dreamingfish raid_anchor validate                 检查是否落在合法区域/位置
/dreamingfish raid_anchor reload                   重读数据包
/dreamingfish raid_anchor export                   导出到文件（备份/移交地图）
```

> 数据分两层：**数据包定义层**（随地图分发）+ **世界层覆盖**（存在存档里）。用命令改定义层的锚点会生成一份世界层副本，**以世界层为准**（命令会提示这一点）。

### 6.5 标签：决定每个点的行为

```
/dreamingfish raid_anchor tag <锚点id> add <标签>
/dreamingfish raid_anchor tag <锚点id> remove <标签>
```

**撤离点（EXTRACTION）**

| 标签 | 作用 |
| --- | --- |
| `extract:fixed` | 固定撤离点（必开，作为保底） |
| `extract:random` | 随机撤离点（默认类型，按权重抽取） |
| `extract:conditional` | 条件撤离点（需条件满足） |
| `extract:single_use` | 限次撤离点（用掉即关闭） |
| `uses:3` | 本局可用 3 次（配合限次类型） |
| `requires:factory_power_on` | 需要某条件已满足（条件由其它系统提供） |
| `mindist:350` / `maxdist:900` | 与出生点的距离筛选（米） |
| `nospawn:xxx` / `spawn:xxx` | 按出生组筛选 |

**露天物品（LOOSE_LOOT）**

| 标签 | 作用 |
| --- | --- |
| `loot:electronic` / `loot:document` / `loot:valuable` | 这个点**只允许**这些类别（可写多个，不写=不限） |
| `value:800` | 这个点物品的生成成本上限（默认 600；标签写坏只回落默认值，不会让点失效） |

> ⚠️ **标签里的冒号是正常的**：`add extract:fixed` 直接敲，**不要加引号**（引号也行，但不是必须）。

**粒子标识对照**（一眼区分类型）

| 撤离点类型 | 粒子 |
| --- | --- |
| 固定 `extract:fixed` | 末地烛 `END_ROD` |
| 条件 `extract:conditional` | 灵魂火 `SOUL_FIRE_FLAME` |
| 限次 `extract:single_use` | 电火花 `ELECTRIC_SPARK` |
| 随机 | 云 `CLOUD` |
| **地上物品（露天）** | **附魔微光 `ENCHANT`**（刻意与撤离点区分开） |

### 6.6 撤离点候选：`raid_extractions/<地图>.json`

一般**留空即可**（一切以锚点为准）：

```json
{ "random_count": 2, "tags": [] }
```

锚点候选与这份文件**合并**，同 id 时**锚点优先**。

### 6.7 区域块（"地图零件"）：把建筑搬进竞技场

```
# 在竞技场里搭好一间房，站在房间里框选存成零件（支持 ~ 相对坐标）
/dreamingfish raid region capture office ~-5 ~-1 ~-5 ~5 ~6 ~5

/dreamingfish raid region list                 # 看已有零件与体积
/dreamingfish raid region place office ~50 ~ ~ # 放到别处
/dreamingfish raid region reset office ~50 ~ ~ # 先清空该尺寸区域再放回（重置用）
```

- 零件存 `<存档>/dreamingfishcore/regions/<名字>.nbt`，**可直接备份/复制/分享**
- 用原版结构模板，**箱子内容物、告示牌、门、火把朝向都保留**，放置时光照正确
- **只抓方块、不抓实体**（否则每次放置都会复制箱子里的怪与掉落物）
- 限值：单边 **128** 格、体积 **32768** 方块

### 6.8 竞技场维度

mod 自带 `dreamingfishcore:raid_arena`：

| 特性 | 实现 |
| --- | --- |
| 虚空超平坦 | `minecraft:flat` + `minecraft:the_void` 群系，仅 y=-64 一层基岩兜底 |
| 不自然刷怪 | `the_void` 群系本身无自然生成 + 维度类型刷怪光照上限 0 + `has_raids: false` |
| 有日夜光照 | `has_skylight: true`、无 `fixed_time` → 有真正的日出日落 |

手动进入（调试用）：

```
/execute in dreamingfishcore:raid_arena run tp @s 0 100 0
```

### 6.9 地图变体与连通图：`raid_maps/<地图id>.json`

```json
{
  "map": "abandoned_factory",
  "edges": ["residential-road", "road-factory", "factory-laboratory"],
  "spawn_nodes": ["residential", "forest"],
  "extraction_nodes": ["laboratory"],
  "must_reach": ["factory"],
  "groups": [
    { "id": "factory_north_entrance", "choose": 1,
      "variants": [
        { "id": "open",     "weight": 40 },
        { "id": "blocked",  "weight": 30, "disabled_edges": ["road-factory"] },
        { "id": "breached", "weight": 10, "enabled_edges": ["forest-warehouse"] }
      ] }
  ]
}
```

规则（**这是"不给玩家一张走不通的图"的保证**）：

- 按权重抽，抽完**必须通过连通性校验**：
  - 每个 `spawn_nodes` 至少能到一个 `extraction_nodes`（否则 `SPAWN_NO_ROUTE`）
  - 有边的区域**不能被完全封死**（`ISOLATED_ZONE`）
  - `must_reach` 里的区域仍可达（`SEALED_OFF`）
- 抽 **20** 次都不合格 → **退回无变体**（地图不变）并在 `raid variants` 里说明
- ⚠️ **区域名里不能有连字符**（边名 `A-B` 靠第一个连字符拆分）

### 6.10 变体方块：门 / 路障 / 电梯

```
# 站在目标方块前、准心对着它
/dreamingfish raid variant_block blocked air        # 变体 blocked 生效时变成空气
/dreamingfish raid variant_block opened oak_door    # 变体 opened 生效时变成木门
/dreamingfish raid variant_block bricked bricks     # 变体 bricked 生效时变成砖块

/dreamingfish raid variant_block list               # 看登记（原方块 → 目标方块）
/dreamingfish raid variants apply blocked           # 只应用该变体（单独验证用）
/dreamingfish raid variants restore                 # 一键还原成登记时的原方块
```

- 登记时会**记下原方块**，所以 `restore` 一定能还原
- 同一位置重复登记会覆盖（不留冲突记录）
- 落盘 `raid_variant_blocks.json`（绑局号），**重启后仍能正确还原**
- 方块 id 可省略 `minecraft:`（`air` / `bricks` / `oak_door` 都行）
- ⚠️ **门的朝向/上下半**：第一版只放默认状态（可能看到"孤零零半扇门"）。先用 `air`/`bricks`/`stone` 这类整块验证机制

---

## 7. 开一局：完整流程与内部发生了什么

```
/dreamingfish raid new abandoned_factory 2 8        # 地图 难度 期望人数
```

`raid new` 的内部顺序（照设计稿，**顺序很重要**）：

| 步骤 | 做什么 | 为什么是这个顺序 |
| --- | --- | --- |
| ① 回滚上一局 | 清理上一局未清理的战利品、还原容器 | 防止"上一局的物品残留"污染新局 |
| ② 生成对局身份 | 局号 + 确定性种子（`服务器种子 + 地图 + 局号`） | 之后一切随机都挂在这个种子上 |
| ③ 抽地图变体 | 按权重抽 → **连通性校验** → 不合格重抽（≤20 次）→ 否则退回无变体 | **先定地图能不能走通**，再定撤离点 |
| ④ 选撤离点 | 按类型/标签/距离/出生组筛选 + 保底 + 加权抽取 | 玩家必须"有路可撤" |
| ⑤ 生成战利品计划 | 按区域预算分配（最大余数法）→ 稀有物受全局上限约束 | 预算与物品表共同决定内容 |
| ⑥ 落地填充 | 往容器里放，**记录原内容**（便于还原）+ 生成露天物品节点 | 双份记录：计划 + 实放 |
| ⑦ 开局自动化 | 拨世界时间到本局起始点 + 把参与者送进竞技场 | 玩家一到就能开打 |
| ⑧ 广播 | 公告开局 | — |

**收尾**：

```
/dreamingfish raid end       # 清战利品、还原容器、召回竞技场里的玩家、写结束事件
```

以下三种情况也会自动收尾（都会走同一套清理与召回）：

| 触发 | 事件日志 `reason` |
| --- | --- |
| 服主手动 `raid end` | `manual` |
| 到达 `auto_end_seconds` | `time_up` |
| 图里没有参与者了（都撤了/死了/掉线超时） | `all_out` |

`raid new` 也会**自动回滚上一局**，所以"忘记 end 就开新局"不会留下脏数据。

---

## 8. 玩家指南：一局怎么打

### 8.1 你能做什么

| 行为 | 规则 |
| --- | --- |
| **开箱子** | 普通开箱，里面是本局生成的战利品 |
| **捡地上物品** | 走到 **1.5 格**内**自动拾取**；或**右键**拾取最近的一件；屏幕右侧有"附近物品（右键拾取）"列表 |
| **撤离** | 站进发光点范围内保持不动读条；读满 **5 秒**即撤离，**背包带走** |
| **中途离开范围** | 读条中断，动作栏提示"撤离中断：已离开撤离点范围" |
| **受伤** | 默认**不打断**读条 |
| **死亡** | 本局战利品随尸体处理，**带不出去** |
| **掉线** | 背包与位置**保留**；**5 分钟内**回来继续本局；超时按"未撤离"结算（背包仍在） |

### 8.2 参与者规则

- **只有开局时在线的玩家算本局参与者**。
- **中途进服**的人会收到明确提示：`你不是本局参与者（本局开始时你不在），撤离点对你无效——等下一局`（不会静默无事发生）。
- 参与者掉线后宽限内回来 → 提示 `欢迎回来，本局仍在进行，你还可以撤离`。

### 8.3 模式限制

**只有生存/冒险模式**能拾取与撤离。创造/旁观站进撤离点会收到**明确提示**（不会让你以为是功能坏了）。

### 8.4 界面提示一览

| 位置 | 提示 |
| --- | --- |
| 动作栏 | `撤离中 42%（保持在范围内）` / `撤离中断：…` / `拾取：铁锭` |
| 聊天栏 | `撤离成功！` / `你不是本局参与者…` / `欢迎回来…` / `本局已无人留在图里，自动结束` |
| 屏幕右侧 | `附近物品（右键拾取）` + `物品名 3.2m` 列表（8 格内最近 5 件） |
| 世界内 | 漂浮自转的物品模型（地上战利品）；不同颜色的粒子标识不同类型的撤离点 |

---

## 9. 命令总表

> 权限：`raid*` = 2，`task_location` = 3。`<...>` 是参数占位符。

### 9.1 `/dreamingfish raid`（对局）

| 命令 | 作用 |
| --- | --- |
| `raid new <地图> [难度] [人数]` | **开新局**（回滚上一局 → 生成身份 → 抽变体 → 选撤离点 → 生成战利品 → 填充 → 拨时间与进场） |
| `raid info` | 本局状态（局号、地图、难度、种子、撤离点、地图变体…） |
| `raid history` | 最近 50 局记录 |
| `raid seed <地图> [局号]` | 预测某局种子（复现问题用） |
| `raid end` | **收尾**：清战利品、还原容器、送离竞技场、写结束事件 |
| `raid clear` | 手动清理未清理的填充记录（含还原覆盖前内容） |
| `raid plan` | 本局战利品分配计划（预算 / 花费 / 各点物品） |
| `raid loot reload` | 重载 `raid_items` 物品表 |
| `raid loot overview` | 物品表总览 |
| `raid extractions` | 本局开了哪些撤离点、为什么没开 |
| `raid extractions reload` | 重载撤离点候选 |
| `raid extractions overview` | 撤离点候选总览 |
| `raid loose` | 露天物品节点与拾取状态 |
| `raid variants` | 为当前对局抽地图变体（含校验结果） |
| `raid variants reload` | 重载 `raid_maps` |
| `raid variants overview` | 已加载的地图定义（节点/边/组/出生区/撤离区）与配置问题 |
| `raid variants apply [变体id]` | 应用变体方块（不给参数=用本局选中的变体） |
| `raid variants restore` | 还原变体方块 |
| `raid variant_block <变体id> <方块>` | 把**准心所指方块**登记为"该变体生效时变成 <方块>" |
| `raid variant_block list` | 变体方块登记列表 |
| `raid region capture <名字> <起点> <终点>` | 抓取区域块（支持 `~ ~ ~`） |
| `raid region place <名字> <坐标>` | 放置区域块 |
| `raid region reset <名字> <坐标>` | 清空该尺寸区域后再放回区域块 |
| `raid region list` | 已有区域块与体积 |

### 9.2 `/dreamingfish raid_anchor`（锚点）

| 命令 | 作用 |
| --- | --- |
| `raid_anchor place <区域id> <类型> <锚点id>` | 在当前位置放锚点 |
| `raid_anchor remove <锚点id>` | 删除 |
| `raid_anchor enable\|disable <锚点id>` | 启停 |
| `raid_anchor list` | 列出全部 |
| `raid_anchor reload` | 重读数据包 |
| `raid_anchor validate` | 校验（是否落在合法区域/位置） |
| `raid_anchor export` | 导出到文件 |
| `raid_anchor tag <锚点id> add\|remove <标签>` | 加/去标签（见 §6.5） |

### 9.3 其它

| 命令 | 作用 |
| --- | --- |
| `/dreamingfish task_location ...` | 区域（zone）定义，权限 3 |
| `/execute in dreamingfishcore:raid_arena run tp @s 0 100 0` | 手动进竞技场（调试） |

---

## 10. 数据文件与事件日志

### 10.1 文件一览

| 路径 | 内容 | 何时写 |
| --- | --- | --- |
| `<存档>/dreamingfishcore/raid_manifest.json` | 当前对局（种子、撤离点、地图变体、容器记录） | 开局/变更时 |
| `<存档>/dreamingfishcore/raid_history.json` | 最近 50 局 | 收尾时 |
| `<存档>/dreamingfishcore/raid_plan_<局号>.json` | 该局战利品分配计划 | 开局 |
| `<存档>/dreamingfishcore/raid_applied_<局号>.json` | 往哪些容器放了什么、覆盖了什么 | 填充时 |
| `<存档>/dreamingfishcore/raid_loose_loot_<局号>.json` | 露天物品节点与是否已拾取 | 生成/拾取时 |
| `<存档>/dreamingfishcore/raid_roster.json` | 参与者名单与状态 | 进出/掉线/撤离时 |
| `<存档>/dreamingfishcore/raid_extraction_uses.json` | 限次撤离点剩余次数 | 撤离时 |
| `<存档>/dreamingfishcore/raid_variant_blocks.json` | 变体方块登记（含原方块） | 登记时 |
| `<存档>/dreamingfishcore/raid_anchors_overlay.json` | 世界层锚点改动 | 改锚点时 |
| `<存档>/dreamingfishcore/regions/*.nbt` | 地图零件 | 抓取时 |
| `<存档>/dreamingfishcore/raid_stats.jsonl` | **事件日志** | 每次事件 |
| `config/dreamingfishcore/raid.json` | 服务器配置 | 首次启动 |

### 10.2 事件日志 `raid_stats.jsonl`

一行一条 JSON。**排查与平衡主要看它**：

| `type` | 关键字段 | 含义 |
| --- | --- | --- |
| `raid_start` | 局号、地图、难度、期望人数、种子 | 开局 |
| `extract` | 玩家、`carried_items` | 成功撤离（含带走的物品） |
| `death` | 玩家 | 对局内死亡 |
| `loot` | 玩家、`node`（锚点）、`item` | 拾取露天物品 |
| `raid_end` | `reason`（`manual` / `time_up` / `all_out`）、`duration_seconds` | 结束 |

---

## 11. 排查手册

| 症状 | 可能原因 | 处理 |
| --- | --- | --- |
| 站进撤离点没反应 | ① 创造/旁观（会有提示）② 不是本局参与者 ③ 没读满时间 ④ 半径/时长配置 | 切生存；`raid info` 确认本局；`raid extractions` 看该点是否开放 |
| 命令报"**参数后应有空格分隔，但发现了尾随数据**" | 旧版参数类型 bug（标签里的冒号） | 确认客户端也是 `0.32.0` 最新版 |
| 箱子没清空 / 残留上一局物品 | 正常应由 `raid new`/`raid end` 自动清理 | `/dreamingfish raid clear`；仍不行看 `raid_applied_*.json` 是否记录 |
| 地上物品不显示 | ① 客户端版本不一致 ② 服务端根本没节点 ③ 已被捡走 | 对齐版本；`raid loose` 看服务端节点；已拾取的不再显示（正常） |
| 一局结束不了 | ① `auto_end_seconds = 0` 且没人手动 end ② 还有参与者在图里（`all_out` 不触发） | 配好时长；`raid info` 看参与者；`raid end` 手动收尾 |
| 撤离后落在奇怪的地方 | `exit_dimension` / `exit_position` | 按 §5.1 配置 |
| 变体方块没还原 | 忘记执行还原 | `raid variants restore`（登记仍在，可反复应用/还原） |
| 部分玩家一开局就被传送走 | 你手动打开了 `TELEPORT_ON_START` | 改回 `false`（默认），或确认地图已放进竞技场 |
| 玩家卡在虚空 | 竞技场里没有落脚点 | 用 `raid region place` 放一份地面零件，或改 `SPAWN_Y` |
| 地图变体总是"退回无变体" | 配置的变体组合切断了必经之路 | `raid variants` 看 `SPAWN_NO_ROUTE` / `SEALED_OFF` 提示，放宽变体对边的禁用 |

**报 bug 时请附**：截图或聊天栏原文 + `raid_stats.jsonl` 相关几行 + `/dreamingfish raid info` 输出。

---

## 12. 测试清单（可直接发给测试玩家）

**开局前**
- [ ] `/dreamingfish raid info` 正常显示
- [ ] 图上能看到撤离点粒子（颜色区分类型）与地上物品的附魔微光

**对局中**
- [ ] 箱子里有战利品，且**没有上一局的残留**
- [ ] 地上物品：能看到**漂浮自转的小模型** + 屏幕右侧"附近物品（右键拾取）"列表
- [ ] 走过去**自动拾取**（动作栏"拾取：xxx"），或**右键**拾取最近一件；捡走后模型消失
- [ ] 撤离点：进范围有读条百分比 → 离开中断 → 读满传送 + "撤离成功！"
- [ ] 撤离后**背包物品还在**（应带走）
- [ ] 死亡后战利品拿不出去（符合预期）
- [ ] 掉线 5 分钟内回来：仍在局内、背包与位置不变
- [ ] 创造模式站进撤离点有**明确提示**（不是静默无效）
- [ ] 中途进服的人收到"你不是本局参与者"提示

**收尾**
- [ ] `/dreamingfish raid end` 后容器被清空/还原
- [ ] `raid_stats.jsonl` 能看到 开局 → 拾取/撤离/死亡 → 结束 的完整链路

**地图作者额外**
- [ ] `raid region capture/place` 后建筑完整（门、箱子内容、告示牌、朝向、光照）
- [ ] `raid variant_block` + `apply` + `restore` 能正确切换与还原

---

## 13. 设计约定与不变量（给后续开发者）

**必须遵守的约定**，否则会与现有系统冲突：

1. **确定性**：所有迭代按 id 排序；随机只走 `RaidRandom`（SplitMix64 + FNV-1a 标签哈希）。**禁止** `java.util.Random`、`level.random`、时间种子。
2. **服务端权威**：客户端只发"我想做什么"，距离/归属/背包校验全部在服务端重算。
3. **软失败**：坏文件、坏条目 → 跳过并记录问题，**绝不阻断服务器启动**；绝不无声覆盖玩家数据（覆盖前先记录）。
4. **纯逻辑优先**：游戏规则/数值尽量写成不依赖 Minecraft 的纯函数并配单测（现有：`RaidRandom`、`LooseLootPlanner`、`RaidMapVariants`、`LootAllocator`、`ExtractionSelector`、`RaidConfig.shouldAutoEnd`、`RaidRoster.Roster`…）。
5. **不要在网络包或静态初始化里放游戏环境静态量**（如 `FMLPaths.CONFIGDIR`）→ 单测会 `ExceptionInInitializerError`；用懒加载访问器。
6. **纯逻辑类里不要引用会触发游戏初始化的东西**（同上）。
7. **命令参数类型按字符集选**：带冒号/点的参数用 `string()`；**最后一个参数**且含空格/冒号可用 `greedyString()`；`word()` 只接受字母数字下划线。
8. **源码多行修改用编辑工具**，不要用 PowerShell 拼多行字符串（换行不匹配 / 插错位置会写坏文件）。
9. **提交信息写文件再 `git commit -F`**（PowerShell 里 `-m` 带多行中文会被拆成路径）。
10. **验证顺序：先看 Gradle 退出码，再看用例数**（编译失败时测试任务不运行，会读到上一轮的"0 失败"）。
11. **验证工程必须在 `X:\` 下跑**（含中文的工作目录路径会让测试 classpath 参数文件编码出错）。用 `subst X: "C:\叽叽团\工程\neoforge1.21.1模组开发"`。
12. **当前基线**：单测 **115 类 / 720 用例 / 0 失败**（game test 上次 37/37，本轮未重跑）。

---

## 14. 已知限制与未做项

### 14.1 原版机制带来的限制（不是 bug）

| 限制 | 说明 | 影响 |
| --- | --- | --- |
| **维度不能独立计时** | Minecraft 所有维度共享同一个昼夜时钟（只有主世界推进），除非 `fixed_time` 冻住（那样就没有日夜变化） | "昼夜跟本局走"实现为"开局拨到起始点，之后自然流动"，**主世界时钟也会变**；可用 `SET_TIME_ON_START` 关 |
| 一局跨约 1.5 个昼夜 | 默认 30 分钟 = 36000 tick，一个昼夜 24000 tick | 清晨进场、天黑、再天亮（其实很符合气氛）；要"整局白天"就把对局压到 20 分钟内 |
| 门的朝向/双半 | `raid variant_block` 只放默认方块状态 | 可能看到朝向不对的"半扇门"；先用整块方块验证 |

### 14.2 未做项

| 未做 | 说明 | 前置条件 |
| --- | --- | --- |
| **布局规划器 + 跨 tick 拼装 + 等候区** | 按种子自动挑零件拼一张新图；增量放置避免卡顿；玩家在等候区等构建完成 | 需先验证"原版结构模板能完整装下你的建筑"（用 `raid region` 试） |
| mod 层刷怪拦截 | 当前靠 `the_void` 群系 + 维度类型保证不刷怪 | 不必要；要双保险可加事件订阅 |
| 露天物品"抓到实体" | 现在只抓方块（防复制实体） | 需要时单开命令 |
| 附近物品列表的风格统一 | 目前是简单文本列表 | 需要与项目 HUD 框架统一 |

---

## 15. 本轮变更记录

| 提交 | 内容 |
| --- | --- |
| `7ab4ab8` → `edaffa0` | 修：标签命令参数类型（`word()`→`string()`→`greedyString()`），`extract:fixed` 这类带冒号的标签可用了；新增 Brigadier 参数类型护栏测试 |
| `45dbc16` `47f9cb3` | 交接文档：进度、续做清单、踩过的坑 |
| `38b53f4` `3e39a91` | **参与者名单**（开局在线才算 / 掉线 5 分钟宽限 / 无人自动结束）+ 宽限进配置 |
| `9c45eb0` `f631b6c` | **露天物品**：规划器（类别/价值标签、共用稀有配额）+ 服务端（自动拾取、粒子、落盘） |
| `016eddd` `9ac36b3` `3648d39` | **地图变体**：选择 + 连通性校验 + 退回无变体；配置加载与落账；`raid variants` 命令 |
| `14a1e80` `5d5e1e1` | **露天物品客户端**：S2C 同步包（协议 → `0.32.0`）+ 漂浮模型渲染 + 附近物品列表 + 右键拾取 |
| `9e41785` | **变体方块**：准心登记 / 应用 / 一键还原 |
| `a430a77` `7d00df5` `631f0d7` | **随机地图第一步**：区域块服务层 + 虚空竞技场维度 + 四个 `raid region` 命令 |
| `252c598` `b1968b6` | 本说明书 + **开局自动化**（拨本局起始时间、自动进场、收尾自动召回） |

> 详细进度与设计决策另见 `docs/RAID_SYSTEM_PROGRESS.md`、`docs/RAID_EXTRACTION_FLOW.md`、`docs/RAID_ANCHOR_SYSTEM.md`。

---

## 16. 交接给地图编辑

**给人做地图的人看这一节就够**（其余看 §6 全流程、§9 命令总表、§12 最后一组勾选项）。

### 16.1 你手上的工具

| 工具 | 命令 | 用途 |
| --- | --- | --- |
| 锚点 | `raid_anchor place <区域id> <类型> <锚点id>` | 标箱子 / 地上物品 / 撤离点 |
| 锚点标签 | `raid_anchor tag <锚点id> add <标签>` | 决定行为（撤离点类型、限次、只出某类物品…） |
| 区域（zone） | `task_location ...`（权限 3） | 划 3D 盒子，战利品预算按区域给 |
| 区域块 | `raid region capture/place/reset/list` | 把搭好的建筑存成"零件"、搬到竞技场、重置 |
| 变体方块 | `raid variant_block` + `variants apply/restore` | 门/路障/电梯的开合，且能一键还原 |
| 校验 | `raid_anchor validate`、`raid variants overview` | 检查有没有放错或配置写错 |

### 16.2 三条必须记住的规矩

1. **区域名不能有连字符** —— 连通图的边名 `A-B` 靠第一个连字符拆分，区域名里带连字符会解析错。
2. **标签里的冒号直接敲**：`add extract:fixed`、`add loot:electronic`、`add uses:3`，不要加引号也不需要转义。
3. **区域块只抓方块、不抓实体**（防复制实体），单边 ≤ 128、体积 ≤ 32768。

### 16.3 现在的地图状态（重要）

目前 mod **只带一个空的虚空竞技场**（`dreamingfishcore:raid_arena`，只有 y=-64 一层基岩）。
地图内容需要你们搭进去，推荐顺序：

```
① /execute in dreamingfishcore:raid_arena run tp @s 0 100 0     进去
② 在 y=100 附近搭第一块区域（房间/街区），注意留出撤离点与箱子位置
③ /dreamingfish raid region capture <零件名> ~-N ~-1 ~-N ~N ~M ~N   抓成零件
④ 反复 place / reset 验证：门、箱子内容、告示牌、朝向、光照是否都正确
⑤ 确认模板可用后，再让开发做"按种子自动拼装"（现在没有自动拼装，是手工摆放）
```

### 16.4 自动进场开关（当前默认关闭）

`RaidArenaService.TELEPORT_ON_START` **默认 `false`**：开局**不会**把玩家传进竞技场，
所以大家现在仍在你现有的主世界地图里玩。等地图搬进竞技场、并且你们确认落点安全后，
把这一行改成 `true`（改完要重新编译），开局就会自动送人进场；`raid end` 会自动把人送回出口。

同处的另外两个开关：`SET_TIME_ON_START`（开局拨本局起始时间；因原版所有维度共享时钟，会影响主世界）、
`RECALL_ON_END`（收尾自动召回）。

### 16.5 出问题找谁

- 命令报错 / 行为不对 → 记下**命令原文 + 聊天栏回显 + `/dreamingfish raid info` 输出**
- 箱子没清 / 地图被改乱 → `/dreamingfish raid clear`、`/dreamingfish raid variants restore`
- 需要新的制作工具（比如"批量放锚点""带朝向抓门"）→ 提出来，这些都好加
