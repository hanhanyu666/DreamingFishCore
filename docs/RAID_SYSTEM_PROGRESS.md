# 搜打撤（撤离玩法）进度与交接

> 给**同一个工作树上的其他会话/同事**看的交接条：我做到哪了、按什么规矩做的、下一步该干嘛。
> 设计依据：服主的《搜打撤服务器：随机地图、撤离点与战利品系统设计》+ 本仓库
> [锚点系统设计](RAID_ANCHOR_SYSTEM.md)、[命令速查](TASK_LOCATION_COMMANDS.md)。
>
> 最后更新：第 0~2 步完成（对局与战利品数值层全部落地）。

## 一、现在能用的命令（都在 `/dreamingfish` 下）

| 命令 | 权限 | 作用 |
| --- | --- | --- |
| `/dreamingfish task_location ...` | 3 | 建/删区域、标尸潮、圈地（见 [命令速查](TASK_LOCATION_COMMANDS.md)） |
| `/dreamingfish raid_anchor place <区域> <类型> [id]` | 2 | 在准心位置放锚点 |
| `/dreamingfish raid_anchor list/validate/reload/enable/disable/remove/export` | 2 | 锚点日常维护 |
| `/dreamingfish raid new <地图> [难度档] [人数]` | 2 | 开一局（对局号递增，种子由服务器种子+地图+对局号决定） |
| `/dreamingfish raid info/history/seed/end` | 2 | 看当前局、历史、预测种子、结束 |
| `/dreamingfish raid loot reload/overview` | 2 | 重载并查看物品价值表与区域模板 |
| `/dreamingfish raid plan` | 2 | 用当前对局与锚点算出各区域战利品计划 |

## 二、四样数据各自存在哪

| 数据 | 位置 | 说明 |
| --- | --- | --- |
| 锚点定义层 | `data/<命名空间>/raid_anchors/*.json` | 随地图分发；数组或 `{"anchors":[...]}` |
| 锚点世界层 | `<存档>/dreamingfishcore/raid_anchors_overlay.json` | 服主游戏内微调，只记差异、优先于定义层 |
| 对局记录 | `<存档>/dreamingfishcore/raid_manifest.json` | 重启后**读回**而不是重随机；历史留最近 50 局 |
| 战利品配置 | `data/<命名空间>/raid_items/*.json`、`raid_zones/*.json` | 物品价值表与区域模板 |
| 规划明细 | `<存档>/dreamingfishcore/raid_plan_<对局号>.json` | `raid plan` 的完整结果，可对账 |

**区域复用现有任务地点**（`TaskLocationDefinition`），没有第二套区域概念；
锚点参数 **id 与显示名都认**（`resolveLocationReference`）。

## 三、我做事的规矩（请沿用，避免风格分裂）

1. **先纯逻辑 + 单测，再接线**。凡是数值/随机/规则，先写成不依赖 Minecraft 的纯函数
   （`RaidRandom`、`RaidAnchorCatalog`、`LootAllocator`、`AnchorActivator`、`RaidLootPlanner`），
   这样能在普通单测里飞快验证，不必启动游戏。接线层（Service/Command/Events）只做搬运。
2. **确定性优先**：所有遍历按稳定顺序（锚点/点按 id 排序、池顺序不重排），随机只走
   `RaidRandom`（SplitMix64 + FNV-1a 标签），**禁止** `java.util.Random`、`level.random`、时间种子。
   否则"同 seed 同结果"是假的。
3. **软失败**：单个文件/单条数据坏了只跳过它并记问题，绝不阻止服务器启动或让整局炸掉。
4. **不重复造轮子**：区域用任务地点、价格用 EconomySystem（本模组**不定义** `sell_value`，
   只出 `spawn_cost`/`combat_score`/`rarity_weight`）、结算口径沿用"区域内按在场、每台每玩家一次"。
5. **写操作立即落盘**，并保证幂等/可重入（`reload` 随便按）。
6. **验证三件套**：`test` + `runGameTestServer` + `build`，并且**必须在 `X:\` 下跑 Gradle**
   （中文路径会让测试 worker 的 classpath 参数文件编码错乱，表现为"所有测试 ClassNotFound"）。
   跑完读 `X:\build\test-results\test\*.xml` 求和核对用例数——`compileTestJava` 失败时
   `test` 会沿用上一轮结果仍报 BUILD SUCCESSFUL。
7. **推送策略**：本地提交可以随便攒，**推送必须等服主说"推"或"测完了"**（他的硬规矩）。

## 四、已经踩过并填掉的坑（别重踩）

- **锚点命令曾经整条不存在**：接命令时只加了 `import`，`register(...)` 那行因缩进不匹配没插进去，
  而我没核对返回值就提交。**教训：插完注册行必须显式断言它存在**。
- **稀有物品曾绕过单局上限**：普通填充阶段没检查 `raid_global_limit`，配额 1 的钥匙卡被放了 18 次。
  现在"买得起子集"会排除配额已用尽的物品，普通填充命中时也扣配额。
  这正是设计稿警告的"每个箱子独立抽 1% → 箱子变多后产出失控"。
- **设计稿伪代码里的死循环**：`while (budget >= minCost) { 全池抽; 贵就跳过 }` 在池子里大多是
  贵物品时会空转。实现里改成"每轮只在买得起子集里抽"，抽不到立即收尾。
- **整数预算取整**：切分预算必须用最大余数法，逐个四舍五入会让总额飘。
- **gametest 偶发失败**：共享测试世界 + `makeMockServerPlayerInLevel()` 造的模拟玩家不清理，
  会污染后面用例的 32 格扫描断言；新增 gametest **必须在 finally 里摘掉模拟玩家与生物**。
- **模型事故（另一个会话的领域，但值得记）**：Bedrock 的 `cubes.origin` 是**模型绝对坐标**，
  写成本地坐标会在 Blockbench 里"一节节散开"；只查 JSON 结构查不出这类问题，**改完几何要自己渲图看**。

## 五、还没做（下一步的入口）

| 步骤 | 内容 | 建议入口 |
| --- | --- | --- |
| 3 | **撤离点**（固定 + 普通随机 + 距离与连通性校验、`max_uses`、`min_raid_time`） | 从 `EXTRACTION` 类型锚点里选，复用 `RaidRandom.forSystem("extraction")` |
| 4 | **露天物品静态节点**（服务端权威 + 客户端显示 + 拾取校验） | 复用 `LOOSE_LOOT` 锚点；沿用"无方块实体、状态在世界数据 + 快照包"的惯例 |
| 5 | **部分随机地图**（先小范围方块状态，结构粘贴最后并加回滚） | `MAP_VARIANT` 锚点 + 手工连通图 |
| 2 尾巴 | 把规划结果**写回 RaidManifest**（现在只在内存 + 明细文件里） | 需要改 manifest 结构，改完记得补 JSON 往返测试 |
| 可选 | `raid_anchor visualize`（粒子/线框显示锚点） | 照 `TaskLocationBoundaryRenderer` 的既有做法 |
| 待修 | gametest 偶发（另一个会话新加的射手僵尸用例在污染共享世界） | 见上面第四节最后两条 |

## 六、验证现状（我最后一次跑的数字）

- 单测：**105 类 / 653 用例 / 0 失败**
- gametest：**37/37**
- `build` 通过；未改网络协议版本（仍是另一会话定的 0.31.0 附近，按需在
  `DreamingFishCore_NetworkManager.PROTOCOL_VERSION` 里确认）

**本地领先远端 9 个提交（未推送）**，等服主测完再推。
