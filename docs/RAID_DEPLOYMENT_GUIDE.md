# 搜打撤（Raid / 撤离射击）功能部署与使用说明书

> 适用版本：`dreamingfishcore` 2.3.3（Minecraft 1.21.1 / NeoForge 21.1.228）
> 协议版本：`0.32.0`（客户端与服务端必须一致，否则连不上）
> 本文面向：**服主、地图作者、测试玩家**
> 命令以游戏内 Tab 补全为准；本文列出的是常用部分。

---

## 一、它是什么（一句话）

进图搜物资 → 活着走到撤离点读条撤出 → 带走的物资归你；死了就丢在尸体里。
对局由服主用命令开局，地图上的**箱子、地上物品、撤离点**都由 mod 按种子确定性地安排。

---

## 二、部署（服主）

### 2.1 装什么

| 位置 | 内容 |
| --- | --- |
| 服务端 | `dreamingfishcore-2.3.3.jar`（NeoForge 21.1.228 / MC 1.21.1） |
| 客户端 | 同一个 jar（有客户端显示逻辑，**必须装**） |

> 协议版本 `0.32.0`：服务端与客户端版本不一致会直接连不上，这是有意的——避免"半新半旧"的诡异 bug。

### 2.2 配置文件（服务端）

首次启动自动生成 `config/dreamingfishcore/raid.json`：

```json
{
  "extraction_radius": 3.0,          // 撤离读条半径（格），0.5 ~ 16
  "extraction_seconds": 5,           // 撤离读条时长（秒），1 ~ 60
  "auto_end_seconds": 1800,          // 本局时长上限（秒）；0 = 关闭"时间到自动结束"
  "auto_end_when_all_out": true,     // 图里没人了就自动结束
  "logout_grace_seconds": 300,       // 掉线宽限（秒）；超时按"未撤离"结算
  "exit_dimension": "minecraft:overworld",   // 撤离成功后送到哪个维度
  "exit_position": null              // 撤离后的落点；null = 该维度出生点
}
```

改完 `/reload` 生效（部分项重启更稳）。

**建议的做法**：先用默认值跑一局，确认没问题后再调。想快速验证自动结束，把 `auto_end_seconds` 改成 60。

### 2.3 地图内容（数据包）

放在**存档的数据包**或**服务端的世界数据包**里，目录结构：

```
data/dreamingfishcore/raid_items/starter.json          物品价值表（生成成本、稀有度、权重）
data/dreamingfishcore/raid_zones/factory_office.json   区域模板（预算、稀有规则、容器数量）
data/dreamingfishcore/raid_extractions/*.json          撤离点旋钮（一般留空，用锚点即可）
data/dreamingfishcore/raid_maps/abandoned_factory.json 地图变体与连通图（可选）
```

最省事的办法：把开发存档里的示例数据包复制走，改数值即可。

### 2.4 在地图上放锚点（**核心操作**）

一切"在地图上标点"都通过**锚点**做，站在目标位置用命令记下来：

```
/dreamingfish raid_anchor place <区域id> <类型> <锚点id>
```

**类型（写英文）**：

| 类型 | 用途 |
| --- | --- |
| `CONTAINER_LOOT` | 可刷战利品的箱子/容器 |
| `LOOSE_LOOT` | 地上随手能捡的物品（会渲染成漂浮小模型） |
| `EXTRACTION` | 撤离点 |
| `MAP_VARIANT` | 预留给地图变体（当前方块切换用 `raid variant_block`，不必用它） |

**例子**：

```
/dreamingfish raid_anchor place factory_office CONTAINER_LOOT office_chest_01
/dreamingfish raid_anchor place factory_office LOOSE_LOOT office_desk_01
/dreamingfish raid_anchor place factory_office EXTRACTION exit_a
```

**高级：给锚点打标签**（决定这个点的行为）：

```
/dreamingfish raid_anchor tag <锚点id> add <标签>
/dreamingfish raid_anchor tag <锚点id> remove <标签>
```

| 标签 | 作用 | 用在哪 |
| --- | --- | --- |
| `extract:fixed` / `extract:random` / `extract:conditional` / `extract:single_use` | 撤离点类型（固定/随机/条件/限次） | EXTRACTION |
| `uses:3` | 限次撤离点，本局可撤 3 次 | EXTRACTION |
| `requires:factory_power_on` | 条件撤离点，需要某条件已满足 | EXTRACTION |
| `mindist:350` / `maxdist:900` | 与出生点的距离筛选（米） | EXTRACTION |
| `nospawn:xxx` / `spawn:xxx` | 按出生组筛选 | EXTRACTION |
| `loot:electronic`、`loot:document`、`loot:valuable` | 这个点只允许这些类别（可多个） | LOOSE_LOOT |
| `value:800` | 这个点物品的生成成本上限（默认 600） | LOOSE_LOOT |

> ⚠️ **标签里有冒号是正常的**，命令已支持（`add extract:fixed` 直接敲，不要加引号）。

**常用维护命令**：

```
/dreamingfish raid_anchor list                 列出所有锚点
/dreamingfish raid_anchor remove <锚点id>      删除
/dreamingfish raid_anchor enable|disable <锚点id>
/dreamingfish raid_anchor validate             检查锚点是否在合法区域/位置
/dreamingfish raid_anchor reload               重新读数据包
/dreamingfish raid_anchor export               导出到文件（便于备份/移交）
```

### 2.5 权限

| 命令组 | 需要权限等级 |
| --- | --- |
| `/dreamingfish raid ...` | 2（管理员） |
| `/dreamingfish raid_anchor ...` | 2 |
| `/dreamingfish task_location ...` | 3（区域定义，普通管理员改不到） |

---

## 三、开一局（服主操作）

```
# ① 开新局：地图名、难度、期望人数
/dreamingfish raid new abandoned_factory 2 8

# 它会依次做：
#   回滚上一局没清掉的战利品 → 生成本局身份与种子 → 抽地图变体（若配了）
#   → 选撤离点 → 生成并填充战利品 → 广播开局

# ② 看本局状态（含种子、撤离点、地图变体、剩余容器）
/dreamingfish raid info

# ③ 看细节
/dreamingfish raid extractions        本局开了哪些撤离点、为什么没开
/dreamingfish raid plan               本局战利品分配计划（预算/花费/各点物品）
/dreamingfish raid loose              露天物品节点与拾取状态
/dreamingfish raid variants           地图变体选择与连通性校验结果
/dreamingfish raid history            最近 50 局记录

# ④ 结束本局（会清战利品并把容器还原成开局前的内容）
/dreamingfish raid end
```

**收尾相关的命令**：

```
/dreamingfish raid clear              手动清理"未清理的填充记录"（含还原覆盖前的内容）
/dreamingfish raid loot reload        重载物品价值表
/dreamingfish raid loot overview      看物品表总览
/dreamingfish raid extractions reload 重载撤离点候选
```

> **重要**：`raid new` 会**自动回滚上一局**，`raid end` 会**清空并还原**容器。
> 两者都不会把你的原始箱子内容弄丢（开局会记录原内容，结束/重开时还原）。

---

## 四、玩家视角：一局怎么打

1. 服主开局后，图上出现**发光点**（撤离点）与**漂浮的小物品**（露天物资）；箱子里有战利品。
2. **生存/冒险模式**才能拾取与撤离（创造/旁观会收到明确提示，不会静默无效）。
3. **露天物资**：走到 1.5 格内**自动拾取**，或**右键**手动拾取最近的一件；屏幕右侧会列出"附近物品（右键拾取）"。
4. **撤离**：站进撤离点粒子范围内保持 3 格内不动，动作栏显示读条百分比；读满即传送撤离，**背包带走**。
   - 离开范围会中断（动作栏提示"撤离中断"）
   - 受伤默认不打断
5. **死亡**：本局战利品随尸体处理（拿不出去）。
6. **掉线**：背包与位置保留；**5 分钟内回来**继续本局；超时按"未撤离"结算。

**参与者规则**：只有**开局时在线**的玩家算本局参与者。中途进服的人会收到提示，撤离点对其无效。

---

## 五、地图作者：造随机地图的工具（第一阶段）

### 5.1 竞技场维度（虚空、不刷怪、有日夜）

mod 自带维度 `dreamingfishcore:raid_arena`：

- **虚空超平坦**：只有 y=-64 一层基岩兜底，其余全空
- **不自然刷怪**：群系用 `minecraft:the_void`（本身无自然生成）＋ 维度类型限制刷怪光照
- **有日夜光照**：有天空光、没有 `fixed_time`，所以有真正的日出日落

进入方式：

```
/execute in dreamingfishcore:raid_arena run tp @s 0 100 0
```

> ⚠️ **昼夜时间的原版限制**：Minecraft 的**所有维度共享同一个昼夜时钟**（只有主世界推进它），
> 维度无法各自独立计时——除非用 `fixed_time` 冻住（那就没有日夜变化了）。
> 因此"昼夜跟着本局走"的可行做法是：**开局时把世界时间设到本局起始点**（例如清晨），之后自然流动。
> 副作用：这会同时移动主世界时钟。
> 另注意：默认对局 30 分钟 = 36000 tick，而一个昼夜 24000 tick，**一局会跨约 1.5 个昼夜**。

### 5.2 区域块（"地图零件"）的抓取与放置

```
# 在竞技场里搭好一间房，站在房间里框选存成零件（支持 ~ 相对坐标）
/dreamingfish raid region capture office ~-5 ~-1 ~-5 ~5 ~6 ~5

# 看已有零件（名字 + 体积）
/dreamingfish raid region list

# 放到别处
/dreamingfish raid region place office ~50 ~ ~

# 重置：先清空该尺寸区域，再把零件放回（竞技场"重置"用这个）
/dreamingfish raid region reset office ~50 ~ ~
```

- 零件存 `<存档>/dreamingfishcore/regions/<名字>.nbt`，**可直接备份/复制/分享**
- 用原版结构模板存取，所以**箱子内容物、告示牌、门、火把朝向都保留**，放置时光照也正确
- **只抓方块、不抓实体**（否则每次放置都会复制一遍箱子里的怪与掉落物）
- 限值：单边 128 格、体积 32768 方块

### 5.3 地图变体（门/路障/电梯的开合）

给"同一个位置在不同变体状态下变成不同方块"用，且**一键还原**：

```
# 站在目标方块前、准心对着它
/dreamingfish raid variant_block blocked air          ← 变体 blocked 生效时变成空气
/dreamingfish raid variant_block opened oak_door      ← 变体 opened 生效时变成木门
/dreamingfish raid variant_block bricked bricks

/dreamingfish raid variant_block list                 看登记
/dreamingfish raid variants apply blocked             只应用 blocked（单独验证用）
/dreamingfish raid variants restore                   一键还原成登记时的原方块
```

地图变体与连通图的配置文件（可选）：`data/dreamingfishcore/raid_maps/<地图id>.json`

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

规则：
- `raid new` 时按权重抽变体，**抽完必须通过连通性校验**（每个出生区至少能到一个撤离区、
  区域不会被完全封死、`must_reach` 里的区域仍可达）
- 抽 20 次都不合格就**退回无变体**并在 `raid variants` 里说明（宁可地图没变化，也不给玩家一张走不通的图）
- **区域名里不能有连字符**（边名 `A-B` 靠第一个连字符拆分）

---

## 六、数据都在哪（备份/迁移/排查）

| 文件 | 内容 |
| --- | --- |
| `<存档>/dreamingfishcore/raid_manifest.json` | 当前对局（种子、撤离点、地图变体、容器记录） |
| `<存档>/dreamingfishcore/raid_history.json` | 最近 50 局 |
| `<存档>/dreamingfishcore/raid_plan_<局号>.json` | 该局战利品分配计划 |
| `<存档>/dreamingfishcore/raid_applied_<局号>.json` | 该局往哪些容器放了什么、覆盖了什么（还原依据） |
| `<存档>/dreamingfishcore/raid_loose_loot_<局号>.json` | 露天物品节点与是否已拾取 |
| `<存档>/dreamingfishcore/raid_roster.json` | 本局参与者名单与状态 |
| `<存档>/dreamingfishcore/raid_extraction_uses.json` | 限次撤离点剩余次数 |
| `<存档>/dreamingfishcore/raid_variant_blocks.json` | 变体方块登记（含原方块，用于还原） |
| `<存档>/dreamingfishcore/regions/*.nbt` | 地图零件 |
| `<存档>/dreamingfishcore/raid_anchors_overlay.json` | 世界层锚点改动 |
| `<存档>/dreamingfishcore/raid_stats.jsonl` | **事件日志**（每行一条：开局/撤离/死亡/拾取/结束） |
| `config/dreamingfishcore/raid.json` | 服务器配置 |

**`raid_stats.jsonl` 是排查与平衡的主要依据**，事件类型：

| type | 字段 |
| --- | --- |
| `raid_start` | 局号、地图、难度、期望人数、种子 |
| `extract` | 谁、带了哪些物品 |
| `death` | 谁 |
| `loot` | 谁、哪个节点、什么物品（露天拾取） |
| `raid_end` | 原因（`manual` 手动 / `time_up` 时间到 / `all_out` 图里没人）、时长（秒） |

---

## 七、常见问题

| 现象 | 原因 / 处理 |
| --- | --- |
| 站进撤离点没反应 | ① 创造/旁观模式（会提示）；② 不是本局参与者；③ 半径/时长配置改过；④ 没站够时间 |
| 命令报"参数后应有空格分隔，但发现了尾随数据" | 老的协议/参数问题，已在 0.32.0 修好；确认客户端也是最新版 |
| 箱子没清空 | `raid new` 会自动回滚上一局；也可手动 `/dreamingfish raid clear` |
| 地上物品不显示 | 客户端与服务端版本要一致（协议 0.32.0）；`raid loose` 看服务端是否真的有节点 |
| 一局结束不了 | 检查 `auto_end_seconds` / `auto_end_when_all_out`；有参与者还在图里就不会触发 `all_out` |
| 撤离后落在奇怪的地方 | 配 `exit_dimension` / `exit_position` |
| 变体方块没还原 | `/dreamingfish raid variants restore`（登记仍在，可反复应用/还原） |

---

## 八、测试检查清单（给测试玩家）

**开局前**
- [ ] `/dreamingfish raid info` 能正常显示
- [ ] 图上能看到撤离点的发光粒子（不同颜色代表不同类型）

**对局中**
- [ ] 箱子里有战利品；**新局不会残留上一局的物品**
- [ ] 露天物资：能看到漂浮小模型 + 屏幕右侧"附近物品"列表
- [ ] 左键走过自动拾取 / 右键手动拾取，动作栏有提示，物品消失
- [ ] 撤离点读条：进范围有百分比，离开中断，读满传送 + "撤离成功"
- [ ] 撤离后背包物品还在（应带走）
- [ ] 死亡后战利品拿不出去（符合预期）
- [ ] 掉线回来 5 分钟内仍在局内（背包/位置不变）
- [ ] 创造模式站进撤离点有明确提示（不是静默无效）

**收尾**
- [ ] `/dreamingfish raid end` 后容器被清空/还原
- [ ] `raid_stats.jsonl` 里能看到开局→撤离/死亡→结束的完整记录

**报 bug 时请附上**：截图或聊天栏原文 + `raid_stats.jsonl` 相关几行 + `/dreamingfish raid info` 的输出。

---

## 九、当前完成度（诚实说明）

| 模块 | 状态 |
| --- | --- |
| 锚点系统（含标签） | ✅ 完成 |
| 对局身份与确定性种子 | ✅ 完成 |
| 战利品（价值表、区域预算、容器填充、覆盖与还原） | ✅ 完成 |
| 撤离点（四类、筛选、保底、粒子）与撤离流程（读条/传送/结算/限次） | ✅ 完成 |
| 参与者名单（开局在线才算、掉线宽限、无人自动结束） | ✅ 完成 |
| 露天物品（服务端 + 客户端显示 + 自动/右键拾取 + 附近物品列表） | ✅ 完成 |
| 统计事件日志 | ✅ 完成 |
| 地图变体（选择 + 连通性校验 + 退回无变体） | ✅ 完成 |
| 变体方块切换（登记/应用/还原） | ✅ 完成（门的朝向/双半未处理） |
| 竞技场维度（虚空、不刷怪、日夜） | ✅ 完成 |
| 区域块抓取/放置/重置 | ✅ 完成 |
| **开局自动设本局起始时间 + 自动把玩家送进/送出竞技场** | ❌ 未做（现在用 `/execute in ... tp` 手动） |
| **布局规划器 + 跨 tick 拼装 + 等候区** | ❌ 未做（随机地图的下一阶段） |
| **mod 层额外拦截自然刷怪** | ❌ 未做（当前靠虚空群系 + 维度类型保证） |

> 未做的三项都不影响当前玩一局搜打撤：地图是手工摆的，箱子/撤离点/露天物品都已经能跑。
