# 任务地点与锚点：命令速查

> 两份系统共用 `/dreamingfish` 根节点，但**权限级别不同**——建区域要 3 级，放锚点 2 级即可。
> 相关文档：[尸潮区域与刷怪箱](HORDE_AREA_AND_SPAWNER.md)、[搜打撤锚点系统](RAID_ANCHOR_SYSTEM.md)。

## 一、任务地点（区域）——需要 **3 级权限**

区域是**三维盒子**（X / Y / Z 都判），玩家圈地只看 X / Z，别混。

```
# 圈一块新区域：先起名，再点两个角，最后确认
/dreamingfish task_location select <区域名>
/dreamingfish task_location pos1                 ← 不写坐标 = 用你脚下的方块
/dreamingfish task_location pos2                 ← 也可以直接给坐标：pos2 100 60 -50
/dreamingfish task_location confirm
/dreamingfish task_location cancel               ← 中途反悔

# 查看与维护
/dreamingfish task_location list                 列出全部区域
/dreamingfish task_location info <区域>          看某个区域的详情
/dreamingfish task_location reload               重新加载
/dreamingfish task_location remove <区域>        删除（立即写盘）
/dreamingfish task_location horde on <区域>      标成「尸潮区域」（刷怪箱的工作前提）
/dreamingfish task_location horde off <区域>     取消尸潮标记

# 圈地模式（在 select 时选定，影响保护与建造规则）
/dreamingfish task_location select <区域>                 默认模式
/dreamingfish task_location select buildable <区域>       可建造
/dreamingfish task_location select protected <区域>       强制保护
```

**区域参数 id 与显示名都可以**（`remove` / `horde` / 锚点命令都走同一套
`resolveLocationReference`：先按 id 匹配，再退回按显示名匹配）。Tab 补全给的是显示名。

三条容易踩的：

- **区域别划得太薄**：不足 5 格高时，`horde on` 会提示你——因为刷怪箱高放/低放一格就会"不在区域内"。
- **重新 `select` 同名区域会重置范围**，`horde` 标记需要重新打。
- 删除区域是**立即写盘**的；引用它的锚点会变成"区域不存在"被跳过（但**保留在文件里**，
  把区域用同样的 id 建回来就会自动复活），里面的刷怪箱会立刻停。

## 二、锚点——**2 级权限**

锚点是"人工确认过的候选位置"，后面所有随机内容（撤离点、资源点、露天物品、刷怪点、地图变体、
事件）都从这里选。**它本身不产生玩法**，是给后续系统准备的位置库。

```
/dreamingfish raid_anchor place <区域> <类型> [id]   在准心位置放一个锚点（id 可自动生成）
/dreamingfish raid_anchor list [区域] [类型]         列出锚点
/dreamingfish raid_anchor validate                   全量校验（区域不存在 / 越界 / id 重复…）
/dreamingfish raid_anchor enable|disable <id>        临时开关
/dreamingfish raid_anchor remove <id>                删除
/dreamingfish raid_anchor reload                     重新加载定义层与覆盖层
/dreamingfish raid_anchor export [区域]              导出可分发 JSON 到存档目录
```

类型（十个）：

```
PLAYER_SPAWN  EXTRACTION  CONTAINER_LOOT  LOOSE_LOOT  MOB_SPAWN
BOSS_SPAWN    MAP_VARIANT EVENT           DOOR        QUEST
```

放置规则（命令会逐条校验并给出具体原因）：

- 必须**站在该区域内**（贴合墙脚/门槛有 1 格容差）；
- **维度必须与区域一致**；
- 落点取**准心所指方块的表面**（贴面 0.02 格，避免与方块重叠）；没指到方块就用你脚下；
- **朝向按 45° 吸附**，摆桌面物品比随手转整齐得多；
- 所有写操作**改完立刻落盘**。

数据分两层：**定义层**在 `data/<命名空间>/raid_anchors/*.json`（随地图分发），
**世界层**在 `<存档>/dreamingfishcore/raid_anchors_overlay.json`（服主微调，只记差异、优先于定义层）。
`export` 输出的是两层合并后的结果，可以直接交给别人。

## 三、`validate` 干净之后做什么

`validate` 通过只说明"现在放的东西都合法"，接下来是**产出位置库**本身：

1. **按类型走一遍地图放锚点**（这是这一层真正的体力活）：
   对着箱子/保险柜放 `CONTAINER_LOOT`，对着桌面货架放 `LOOSE_LOOT`，标出 `EXTRACTION`、
   `PLAYER_SPAWN`、`MOB_SPAWN`、`BOSS_SPAWN`。
2. `list <区域> <类型>` 检查分布，`disable` 临时关掉不放心的点、`remove` 删掉放错的点。
3. 需要交给别人（地图作者/同事）时 `export`，把导出的 JSON 放进数据包。
4. 放完再 `validate` 一次收口。

**然后就没有下一步了**——锚点目前只是候选库，不会自己生成任何东西。
消费它的是后续步骤：对局（Raid）与撤离点、区域预算与资源点、露天物品节点、地图变体。
