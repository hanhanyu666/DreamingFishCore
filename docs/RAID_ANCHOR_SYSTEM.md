# 搜打撤：锚点系统设计（第 0 步）

> 这是「随机地图与战利品系统」的第一块地基。设计原则来自服主的设计稿：
> **地图制作者提前放置大量候选锚点，服务器在每局开始时按规则激活其中一部分。**
> 本文件只覆盖**锚点基建**，不含对局生成、撤离点、预算分配（那些是第 1~5 步）。

## 一、为什么先做锚点

设计稿里所有随机系统（撤离点、资源点、露天物品、刷怪点、地图变体、事件）都建立在同一个前提上：

```text
不从"任意坐标"生成内容，只从"人工确认过的合法候选位置"里选。
```

所以锚点不是一个功能，而是**后面所有随机内容的唯一入口**。它单独落地不产生任何玩法，
但如果不先做，后面每个系统都会各自发明一套坐标与配置，最后互不兼容。

## 二、与既有系统的关系（不重复造）

| 设计稿概念 | 本项目已有的对应物 | 结论 |
| --- | --- | --- |
| `zone`（区域） | `TaskLocationDefinition` / `TaskLocationManager`（三维盒子、世界数据持久化、命令、HUD、边界渲染） | **复用**。锚点只存 `zone` 字符串引用任务地点 id，不新建区域体系 |
| `MOB_SPAWN` + 结算 | 尸潮区域 + 刷怪箱（批次、冷却、剿灭结算、每台每玩家只发一次） | 第 4 步接进来，结算口径沿用现状 |
| 任务驱动刷新 / 全局稀有物 | 线索系统（稳定 id + 六个投放口）、蓝图系统 | 稀有物与线索/蓝图的载体复用，不新增发放通道 |
| `sell_value`（出售价格） | EconomySystem（价格、终端） | **不在本模组定义**。本模组只出 `spawn_cost` / `combat_score` / `rarity_weight`，价格归 EconomySystem 唯一真源 |
| 静态战利品节点的显示与拾取 | 「无方块实体、状态在世界数据、服务端权威 + 快照包同步」的既有惯例（研究桌、组织、刷怪箱） | 第 5 步照这个惯例做 |

## 三、锚点数据模型

```json
{
  "id": "factory_office_desk_01",
  "type": "LOOSE_LOOT",
  "zone": "abandoned_factory_office",
  "position": [124.5, 67.02, -83.4],
  "rotation": [0.0, 135.0, 0.0],
  "group": "office_tables",
  "tags": ["electronic", "civilian", "desk"],
  "weight": 100,
  "enabled": true,
  "quality_multiplier": 1.2
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 是 | 全局唯一；**同一张图内不得重复**，重复时启动校验报错并忽略后一个 |
| `type` | 是 | `PLAYER_SPAWN` / `EXTRACTION` / `CONTAINER_LOOT` / `LOOSE_LOOT` / `MOB_SPAWN` / `BOSS_SPAWN` / `MAP_VARIANT` / `EVENT` / `DOOR` / `QUEST` |
| `zone` | 是 | 引用任务地点 id；**必须存在**，否则校验失败 |
| `position` | 是 | 世界坐标（浮点，保留 `y` 的精度：露天物品要贴桌面） |
| `rotation` | 否 | 露天物品/容器朝向（度） |
| `group` | 否 | 子区域/点位分组，用来做"每个子区域至少一个、最多若干"的规则 |
| `tags` | 否 | 分类标签，供预算与允许列表筛选 |
| `weight` | 否 | 激活基础权重，默认 100 |
| `enabled` | 否 | 是否参与生成，默认 true（服主临时关某个点用） |
| `quality_multiplier` | 否 | 质量倍率（设计稿 §11.1），默认 1.0 |

**类型专属字段**留在各自类型的实现里（例如 `EXTRACTION` 的 `minimum_distance_from_spawn`、
`CONTAINER_LOOT` 的 `allowed_categories`），第一版不强行统一，避免造出一个全都可空的大杂烩。

## 四、存储（两段式）

```text
定义层（随地图分发、可版本控制）     data/dreamingfishcore/raid_anchors/*.json
世界层（服主游戏内微调、随存档走）   存档数据里的锚点覆盖层：禁用/移动/新增
```

理由：

- 地图作者需要把锚点**和地图一起**发给别人 → 必须能进资源包/数据包、能 diff、能审核；
- 服主又经常要在自己服务器上临时关掉几个点、挪一两个点 → 不能要求他改数据包再重启；
- 两者冲突时**世界层优先**，且世界层只记录"差异"（覆盖/禁用），这样重新分发地图时不会丢。

**放置边界校验**：锚点必须在它所声明的任务地点盒子里（留 1 格容差）。校验不通过时：
`/dreamingfish raid_anchor validate` 报错，但**不阻止服务器启动**（只把该点标为不可用）。

## 五、命令面（第 0 步就要可用）

```text
/dreamingfish raid_anchor place <type> [id]     在准心位置放一个锚点（服务端记录到世界层）
/dreamingfish raid_anchor remove <id>           删除（世界层标记删除）
/dreamingfish raid_anchor enable|disable <id>   临时开关
/dreamingfish raid_anchor list [zone] [type]    列出（含来源：数据包/世界层）
/dreamingfish raid_anchor reload                重新加载数据包定义
/dreamingfish raid_anchor validate              全量校验（id 重复/zone 不存在/越界/类型非法）
/dreamingfish raid_anchor export [zone]         导出为可分发 JSON（把世界层合并进去）
/dreamingfish raid_anchor visualize [zone]      可视化当前锚点（照任务地点边界渲染的既有做法）
```

`place` 的默认 id 用 `<zone>_<type>_<序号>` 生成，允许手动覆盖——**手工起名比自动编号更值钱**，
地图作者会用它标注用途（`warehouse_east_shelf_03`）。

## 六、第 0 步的验收标准

- [ ] 能从数据包目录加载锚点定义，能从世界存档加载覆盖层，冲突时世界层优先
- [ ] `validate` 能查出：id 重复、zone 不存在、位置越出所属区域、类型非法、坐标为 NaN
- [ ] `place/remove/enable/disable/list/export/visualize` 全部可用且服务端权威
- [ ] 校验失败的锚点被**跳过而不是崩溃**，并在日志里给出 id 与原因
- [ ] 单测覆盖：加载与合并、冲突优先级、各类校验、导出/回读往返一致
- [ ] 不改任何现有玩法：不动协议版本、不动任务地点/刷怪箱/线索既有行为

## 七、后续步骤（与设计稿对齐）

| 步 | 内容 | 依赖第 0 步的地方 |
| --- | --- | --- |
| 1 | 对局（Raid）概念 + RaidSeed + Manifest 持久化 + 管理命令 | 用锚点表达出生点/撤离点 |
| 2 | 区域预算 + 物品价值表 + 资源点激活器 + 全局稀有分配（纯函数 + 单测） | 从 `CONTAINER_LOOT`/`LOOSE_LOOT` 锚点里选 |
| 3 | 撤离点（固定 + 普通随机 + 距离与连通校验） | `EXTRACTION` 锚点 |
| 4 | 露天物品静态节点（服务端权威 + 客户端显示 + 拾取校验） | `LOOSE_LOOT` 锚点 |
| 5 | 部分随机地图（先小范围方块状态切换，结构粘贴最后并加回滚） | `MAP_VARIANT` 锚点与连通图 |

## 八、实施时要钉死的三条（来自设计稿评审）

1. **确定性遍历**：锚点集合的遍历顺序必须确定（按 id 排序），否则"同 seed 同结果"是假的。
2. **失败要软**：单个锚点数据有问题只跳过它并记日志，不能让整张图或服务器起不来。
3. **本局 / 跨局边界**：锚点**定义**跨局持久（不属于任何一局），锚点的**激活状态**属于本局，
   第 1 步的 manifest 负责记"本局启用了哪些"，第 0 步只提供候选集合。
