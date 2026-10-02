# 合成蓝图系统

玩家必须**先学会某件物品的蓝图**，才能在工作台里合成它。蓝图由**模组自定义丧尸（攻城丧尸）**
以概率掉落，也可以从宝箱里开出来。整套限制由 `config/dreamingfishcore/blueprint.json` 控制，
**默认关闭**。

改这块代码前请先读「不变量」一节。

---

## 1. 配置文件 `config/dreamingfishcore/blueprint.json`

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `schemaVersion` | `1` | 结构版本，不认识的值会让配置回落到内存默认值 |
| `enabled` | `false` | **总开关**。关闭时所有配方照常可合成、也不掉落蓝图 |
| `siegeZombieDropPercent` | `5.0` | 攻城丧尸掉落蓝图的概率（百分比，支持小数） |
| `chestDropPercent` | `25.0` | 宝箱容器掉落蓝图的概率（百分比） |
| `defaultUnlockedItems` | 见下 | **免蓝图**的物品规则（开局就能造） |
| `exemptNamespaces` | `[]` | **赦免的模组命名空间**，整包免蓝图 |
| `blueprintWhitelist` | `[]` | 空 = 不限制；非空时**只有**命中的物品能进抽取池 |
| `blueprintBlacklist` | `[]` | 命中则**不参与抽取**（注意：这**不等于**放行，见第 2 节） |

所有名单都支持通配符，匹配对象是**带命名空间的完整物品 ID**：

| 写法 | 含义 |
| --- | --- |
| `minecraft:oak_planks` | 精确匹配 |
| `minecraft:*_planks` | 该命名空间下所有以 `_planks` 结尾的物品 |
| `*_planks` | **所有命名空间**下的木板（跨模组，慎用） |
| `minecraft:wooden_?xe` | `?` 匹配任意单个字符 |

`defaultUnlockedItems` 的出厂默认值：

```
minecraft:*_planks
minecraft:stick
minecraft:torch
minecraft:crafting_table
minecraft:furnace
minecraft:campfire
minecraft:chest
minecraft:wooden_pickaxe / _axe / _shovel / _hoe / _sword
minecraft:stone_pickaxe  / _axe / _shovel / _hoe / _sword
dreamingfishcore:spawner         （创造模式物品，放进来只是避免它变成造不了的死物）
dreamingfishcore:research_table  （它是「获得蓝图」的工具本身，不能要求先有它自己的蓝图）
```

---

## 2. 拦不拦，只看两件事

一件物品能不能被合成，取决于：

1. **它是不是「免蓝图」** —— 总开关关闭 / 命中 `defaultUnlockedItems` / 来自 `exemptNamespaces`；
2. **玩家有没有学过它的蓝图**（存在玩家 NBT 的 `unlocked_items` 里）。

白名单与黑名单**只影响「什么能进抽取池」**，不直接参与拦截判断。由此产生一条必须知道的后果：

> **被黑名单排除、又不在默认放行或赦免命名空间里的物品，玩家将永远无法通过工作台合成它。**

这是刻意设计（这类物品应当有别的获取途径，例如宝藏、交易或指令），但很容易被忘掉，
所以服务器启动时会打一条警告，把这类物品逐一点名。命令 `/dreamingfish blueprint info`
也能看到当前白/黑名单规模。

---

## 3. 掉落渠道

| 渠道 | 判定 | 概率 | 按玩家去重 |
| --- | --- | --- | --- |
| 攻城丧尸 | `LivingDropsEvent`，必须是**玩家击杀**的 `SiegeZombieEntity` | `siegeZombieDropPercent` | **是**（排除击杀者已学会的） |
| 宝箱 | 战利品修饰器，只在上下文带 `BLOCK_ENTITY`（容器战利品）时生效 | `chestDropPercent` | 否（拿不到击杀者） |

「不重复」的口径是**按玩家、只看已学**：已经学会的不再掉给他；所以背包里躺着一张还没学的
蓝图时，仍可能再掉一张同款的。学习后即从候选里消失；玩家把池里全部学完后不再掉落。

丧尸渠道走事件而不是战利品修饰器，是因为修饰器拿不到「击杀者是谁」，也就无法按玩家去重。
宝箱渠道相反：容器战利品本来就没有玩家上下文。

---

## 4. 命令（权限跟随 `/dreamingfish` 根节点的 2 级）

```
/dreamingfish blueprint info                     总开关、抽取池与各名单规模
/dreamingfish blueprint pool [页码]              列出抽取池（每页 20）
/dreamingfish blueprint reload                   重载 blueprint.json 并重建抽取池
/dreamingfish blueprint give <物品ID> [玩家]     发一张解锁该物品的蓝图
/dreamingfish blueprint learn <物品ID> [玩家]    直接学会（调试用）
/dreamingfish blueprint list [玩家]              查看某人已学会的蓝图
/dreamingfish blueprint reset [玩家]             清空某人的蓝图进度
```

抽取池是**懒重建**的：配置重载与配方重载都会把它标脏，下次访问时按新配置重算。

---

## 5. 不变量（改这块代码前必读）

- **拦截入口唯一**：`PlayerBlueprintData.canCraftItem`。工作台与背包 2×2 的合成结果都经过它
  ——原版 `InventoryMenu.slotsChanged` 会调用 `CraftingMenu.slotChangedCraftingGrid`，
  所以 `CraftingMenuMixin` 一处注入就能覆盖两处。
- **拦截方式**是「把结果槽清空并同步给客户端」，不是禁用配方：客户端配方书里仍看得到配方，
  但拿不出成品。
- **配方收集只收集、不过滤**：`RecipeManagerMixin` 不做任何「哪些物品需要蓝图」的判断，
  那件事完全由 `BlueprintConfig` 在构建池时决定。早期版本在这里顺手把命中的物品塞进
  「默认放行」集合，等于配方加载悄悄改全局状态，且名单写死在代码里——不要再走回头路。
- **配置读取一律走 `BlueprintConfig.current()`**，不要缓存到字段里；重载后会换成新实例。
- **玩家进度存在玩家 NBT 的 `unlocked_items`**，死亡经 `BlueprintEventHandler` 清空。

---

## 6. 已知边界

- 只拦**工作台配方**。熔炼、切石机、锻造台等其它配方类型不受限制。
- 特殊合成（盔甲染色、烟花、地图复制……）不进抽取池：它们的输出与输入同物，做成蓝图没有意义。
- 宝箱渠道无法按玩家去重（容器战利品没有击杀者）。
- 玩家死亡会遗忘全部蓝图，这是既有设定（见 `BlueprintEventHandler`），与本次改造无关。
- 本系统**默认关闭**。上线前请确认 `blueprint.json` 的默认放行名单符合服务器定位，
  再看一遍启动日志里的「无法合成」警告列表。
