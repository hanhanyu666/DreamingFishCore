# 任务地点系统

任务地点是服主在固定地图中划定的官方三维区域。它为剧情任务提供同一份空间定义，用于保护场景、判断玩家是否在场，以及以后生成局部尸潮或剧情对象。地点有两种运行模式：`PROTECTED`（强制保护）和 `BUILDABLE`（可建造聚居地）。

它不是玩家领地，也不保存任务结果。玩家进入地点不会自动完成任务、切换故事阶段或触发结局；这些决定仍由任务脚本和 `StoryManager` 负责。

## 第一次创建地点

管理命令需要 3 级权限。执行：

```text
/dreamingfish task_location select 旧医疗中心
# 新建或更新可建造地点：
/dreamingfish task_location select buildable 逐光会聚居地
# 显式恢复强制保护模式：
/dreamingfish task_location select protected 旧医疗中心
```

随后：

1. 左键一个方块，设置第一个角点。
2. 右键另一个方块，设置第二个角点。
3. 执行 `/dreamingfish task_location confirm` 保存。

选区过程会调用服务器公开的顶部提示接口，显示当前步骤和坐标。两个角点必须在同一维度；边界会自动整理为最小点和最大点，选择顺序不影响结果。

不需要挖掉目标方块。切换到旁观模式并飞到目标坐标后，可以用当前位置选点；也可以直接填写绝对或相对坐标：

```text
/dreamingfish task_location pos1
/dreamingfish task_location pos2
/dreamingfish task_location pos1 <x> <y> <z>
/dreamingfish task_location pos2 <x> <y> <z>
```

如果不想保存，执行：

```text
/dreamingfish task_location cancel
```

## 管理命令

```text
/dreamingfish task_location list
/dreamingfish task_location info 旧医疗中心
/dreamingfish task_location reload
/dreamingfish task_location remove 旧医疗中心
```

- `list`：列出地点名称、维度和边界。
- `info`：显示单个地点，并统计此刻在场的合格玩家。
- `reload`：重新读取配置；配置校验失败时继续保留当前内存版本。
- `remove`：删除地点并立即写回配置。

## 配置文件

服务器首次启动后会生成：

```text
config/dreamingfishcore/task_locations.json
```

也可以直接编辑它，再执行热重载：

```json
{
  "schemaVersion": 1,
  "locations": [
    {
      "id": "dreamingfishcore:location_4ad2b8570cf34ca6978429fa164a2ee5",
      "name": "旧医疗中心",
      "dimension": "minecraft:overworld",
      "min": {
        "x": 100,
        "y": 55,
        "z": 200
      },
      "max": {
        "x": 150,
        "y": 90,
        "z": 250
      },
      "enabled": true,
      "mode": "PROTECTED"
    },
    {
      "id": "dreamingfishcore:zhuguang_settlement",
      "name": "逐光会聚居地",
      "dimension": "minecraft:overworld",
      "min": { "x": -300, "y": 0, "z": -300 },
      "max": { "x": -100, "y": 320, "z": -100 },
      "enabled": true,
      "mode": "BUILDABLE"
    }
  ]
}
```

启用的地点在同一维度内不能重叠。`enabled` 为 `false` 时，该地点仍保留在配置里，但不提供保护，也不参与玩家收集。`mode` 不区分大小写，缺省值为 `PROTECTED`，因此旧配置无需修改。

### 两种地点模式

- `PROTECTED`：生存玩家进入后临时切换为冒险模式，普通方块仍不可破坏/放置；容器和书架交互保持可用，剧情 NPC 与场景装饰仍受保护。
- `BUILDABLE`：玩家保持生存模式，可以正常建造、破坏、使用容器、书架和活塞。禁止 TNT、岩浆放置/流入和生物爆炸；打火石平时禁止，仅在实际用于点燃下界传送门时放行。其他交互、普通火焰和水保持原有规则，剧情 NPC 与装饰仍受保护。

可建造地点允许 EconomySystem 私人领地叠加，但不是全世界唯一的圈地地点。故事区域外保持 EconomySystem 原本的自由圈地规则；在故事区域内，玩家使用 `economy_system:claim_wand` 选取两个角点，再执行 `/confirm_claim <名称>`。`BUILDABLE` 只会给出故事规则提醒，领地可以跨出故事地点；`PROTECTED` 内不能圈地，领地矩形只要覆盖到 `PROTECTED` 就会被拒绝。领地的所有权、成员、权限、费用和持久化全部由 EconomySystem 负责。`/confirm_modify` 也遵守这一保护规则。没有故事地点时，DreamingFishCore 不干预 EconomySystem 圈地。

EconomySystem 虽然保存两个选点的 Y，并要求选点处于同一 Y，但进入判定、权限保护、重叠检测和价格都只使用 X/Z；实际领地是一根贯穿全部高度的竖直柱。因此 DreamingFishCore 检查其是否覆盖 `PROTECTED` 时也只比较 X/Z，不会通过在保护区上方或下方选点来绕过限制。任务地点自身仍是包含 Y 的三维区域。

`id` 是系统自动生成的内部稳定 ID，用于故事任务、世界存档和代码引用。它会显示在 JSON 中，但服主在游戏内创建、查看、更新和删除地点时只使用地点名称；不要手动修改它。地点名称必须唯一，同名执行 `select` 会更新原地点范围。

## 当前保护规则

任何处于生存模式的玩家进入 `PROTECTED` 地点后，都会由服务端自动切换为冒险模式，OP 管理员也不例外；顶部通知以任务场景名称为标题，并在下方用小字显示“已切换冒险模式”。离开地点后恢复为进入前的生存模式。`BUILDABLE` 地点不会切换模式。

创造模式玩家进入时仍会看到场景名称，但下方显示“您处于创造模式，所以不切换”。旁观模式和原本就是冒险模式的玩家也会收到对应说明，且保持原游戏模式。

冒险模式是 `PROTECTED` 地点的第一层低成本保护，方块事件仍作为模组方块路径的服务端兜底；容器、书架和普通交互不再被地点事件层拦截。两种模式都禁止 TNT、生物爆炸和岩浆；打火石普通使用会被拦截，但会实际生成下界传送门的操作放行。

普通玩家在 `PROTECTED` 地点内不能：

- 破坏或放置方块；
- 放置或引爆 TNT、生物爆炸、放置或流入岩浆、普通使用打火石；打火石用于点燃下界传送门时允许；
- 伤害剧情 NPC、盔甲架和悬挂装饰实体。

在 `BUILDABLE` 地点内，普通的方块建造、破坏、容器/书架使用和活塞操作允许进行；水和其他未列入限制的交互按原版规则处理。不能放置或引爆 TNT，不能发生生物爆炸，不能放置或流入岩浆；打火石仅能用于实际点燃下界传送门，普通点火（火、营火、蜡烛等）会被拦截。故事区域外不受这些故事地点建筑规则影响，仍由服务器和 EconomySystem 的普通规则处理。

保护不会禁止玩家与敌对怪物战斗；两种地点都过滤 TNT、生物爆炸和岩浆，打火石只允许实际点燃下界传送门，其他爆炸、火焰和流体按各自游戏模式的正常规则处理。创造模式只绕过普通方块保护，TNT、生物爆炸、岩浆和普通打火石使用禁令仍然对所有游戏模式生效；OP 权限仅用于管理命令，OP 以生存模式参与时与普通玩家遵守相同规则。剧情 NPC 的所有伤害仍会被拦截，应通过未来的剧情状态接口处理剧情杀。

## 与故事任务连接

任务定义可以增加可选字段：

```json
{
  "id": "dreamingfishcore:defend_medical_center",
  "number": 101,
  "name": "守住医疗中心",
  "content": "前往旧医疗中心确认求救信号。",
  "publishedByDefault": true,
  "locationId": "dreamingfishcore:location_4ad2b8570cf34ca6978429fa164a2ee5"
}
```

任务脚本判定任务成功或失败后，调用：

```java
StoryManager.resolveTaskAtConfiguredLocation(server, taskKey, outcome);
```

系统会在结算瞬间收集地点内的生存和冒险模式玩家，并把他们作为该任务的个人记录获得者。创造和旁观玩家不会被记录。调用地点接口只负责结算，仍不会自动切换故事阶段。

## 后续扩展点

第一版只完成低成本上线所需的官方地点能力。以下内容留给具体任务系统：

- 尸潮生成点、入口和 NPC 锚点；
- 进入或离开地点的剧情触发器；
- 临时允许玩家操作指定方块的交互白名单；
- 任务期间的局部规则和失败代价；
- 玩家建筑登记为剧情场地。
