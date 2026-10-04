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

## 七、补记：本轮新踩的两个坑（2026-10-04）

1. **命令参数类型要按字符集选**。`StringArgumentType.word()` **只接受字母、数字、下划线**；
   标签（`extract:fixed`、`uses:4`、`mindist:350`）与锚点 id（允许 `.`、`-`）都带特殊字符，
   用 `word()` 会报「参数后应有空格分隔，但发现了尾随数据」，且错误信息指向冒号位置，很容易误判成语法错。
   凡是带冒号/点/连字符的参数一律用 `StringArgumentType.string()`（读未加引号的单个 token 时接受任意非空白字符）。
   **这条命令加进来那天就注定不可能成功，属于"写了但没自测"** —— 加带特殊字符的参数后，必须在游戏里真敲一次。
2. **PowerShell 里多行中文提交信息必须写文件再 `git commit -F`**。`-m '多行文本'` 会被拆成多个参数
   （换行被当分隔，`|`、`+` 还会被当管道/运算符），表现为 `error: pathspec 'xxx' did not match any file(s) known to git`。
   本人这轮连踩三次。稳妥做法：here-string 写进 `build/dsh_commit_msg.txt`，再 `git commit -F`。

第三条同样值得记：**校验必须"先看 Gradle 退出码，再看用例数"**。编译失败时 `test` 任务不会运行，
读到的仍是上一轮的 `test-results`，「0 失败」会把编译不过的提交放过去（本轮真发生过一次，
见提交 `5068247` 与修复 `3fcb09c`）。

## 八、当前进度与续做清单（截至 2026-10-04，最新提交 9ac36b3）

### 步骤状态

| 步骤 | 状态 | 说明 |
| --- | --- | --- |
| 0 锚点系统 | ✅ 完成 | 数据包定义层 + 世界层覆盖 + 9 个命令（含 `raid_anchor tag <id> add\|remove <标签>`） |
| 1 对局身份与种子 | ✅ 完成 | `RaidRandom`（SplitMix64 + FNV-1a，子系统互不干扰）、`RaidManifest` 持久化、`raid info/history/new/seed/end` |
| 2 战利品 | ✅ 完成 | 物品价值表、区域模板、激活器、分配器（最大余数法切预算）、规划器；开新局自动填容器（可覆盖非空并记账还原）、`raid clear` |
| 3 撤离点与流程 | ✅ 完成 | 四类撤离点 + 权重/距离/出生组筛选 + 保底 + 粒子标识；读条（半径/时长可配）→ 传送结算 → 限次扣减与持久化；参与者名单（只有开局在线的人算参与，掉线 5 分钟宽限、超时按未撤离）；时间到或无人留在图里自动结束 |
| 4 露天物品 | 🔶 服务端完成 | `LooseLootPlanner`（类别/价值标签、与容器共用稀有配额）+ `LooseLootService`（自动拾取 1.5 格、附魔微光粒子、落盘含 picked）；**差客户端显示与手动拾取**（需新包 → 协议 0.31.0 → 0.32.0） |
| 5 部分随机地图 | 🔶 一半 | `RaidMapVariants`（变体选择 + 连通性校验 + 抽不合格退回无变体）+ `RaidVariantService`（`data/<ns>/raid_maps/*.json` 加载与落账）；**差命令接线与按状态切换方块** |

### 续做清单（都很小，按顺序做）

1. **`raid variants` 命令接线**：`RaidVariantService.selectAndRecord/reload/overview` 都写好了，
   只差在 `Command_Raid` 里加三个分支（照 `raid extractions` 的写法）。做完 `raid info` 就能看到本局地图变体。
2. **`raid loose` 命令接线**：`LooseLootService.describe()` 已写好，只差一个分支。
3. **方块状态切换（设计稿方式②）**：变体选中后切换少量方块（门/路障/电梯）。**需要地图里先有可切的方块**，
   否则做了无法验证。结构粘贴（方式①）留到第二版，并加"先快照、失败回滚"。
4. **客户端显示 + 手动拾取**（第 4 步收尾）：S2C 节点同步 + C2S 拾取请求 + 客户端渲染；
   **必须把 `PROTOCOL_VERSION` 从 0.31.0 提到 0.32.0**（协议版本在 `DreamingFishCore_NetworkManager`）。

### 本轮新增的坑（都已修，别再重踩）

1. **命令参数类型要按字符集选**：`word()` 只接受字母数字下划线；`string()` 读**未加引号**的 token 时
   字符集与 `word()` **完全相同**（都不含冒号）。带冒号的参数只有两条路：**加引号**或 **`greedyString()`**
   （后者只能用在命令最后一个参数）。已有 `BrigadierArgumentTypeTest` 4 例钉住。
2. **源码多行修改一律用编辑工具**，不要用 PowerShell 拼多行字符串：
   ① 文件是 LF 而用 CRLF 拼替换串会导致永不匹配；② 拼错位置会把内容插到 `package` 之前直接写坏文件
   （本轮真发生过一次，用 `git checkout` 恢复）。
3. **提交信息用文件 + `git commit -F`**：PowerShell 里 `-m '多行中文'` 必被拆成多个参数。
4. **校验顺序：先看 Gradle 退出码，再看用例数**。编译失败时 `test` 不运行，旧结果会伪装成"0 失败"。
5. **集合归一化要幂等**：边键先被 `Variant` 归一成 `"A|B"`，`Selection` 再归一化时按"第一个连字符"拆
   就把它丢掉了 → 关边失效。同一份数据被两层归一化时，第二次必须能接受第一次的输出。

## 九、撤离地图专用维度 raid_arena（2026-10-04）

需求（服主定）：**虚空超平坦、不自然刷怪、有日夜光照、昼夜跟着本局走**。

落地（随 mod 发布，所有服务器自动具备，不用每个存档单独配）：
- `data/dreamingfishcore/dimension_type/raid_arena.json`：`has_skylight: true`（有天空光）、
  `has_ceiling: false`、`ambient_light: 0`、`effects: minecraft:overworld`（天空/云/雾按主世界）、
  **没有 `fixed_time`**（所以时间是流动的，会有真正的日出日落）、
  `monster_spawn_light_level: 0` 且 `monster_spawn_block_light_limit: 0`（只在全黑处才可能刷）、
  `has_raids: false`、`bed_works: true`。
- `data/dreamingfishcore/dimension/raid_arena.json`：`minecraft:flat` 生成器 + `minecraft:the_void` 生物群系
  （该群系**没有任何自然生成物**，这是"不刷怪"的关键）+ 仅一层基岩作为兜底地板（防止掉进虚空）。
  需要的话把 `layers` 改成 `[]` 就是纯虚空，或加更多层。

进入方式：`/execute in dreamingfishcore:raid_arena run tp @s 0 0 0`（后续会做成命令/传送门）。

### 昼夜时间：一个必须知道的原版限制

**Minecraft 的所有维度共享同一个昼夜时钟**（只有主世界推进它），维度无法各自独立计时——
除非用 `fixed_time` 把时间**冻住**（那就没有日夜变化了）。所以"昼夜按本局时间"的可行实现是：
**开局时把世界时间设到本局起始点（例如清晨 1000 tick），之后随时间自然流动**。

副作用：这会**同时移动主世界的时钟**（因为共享）。因此这件事做成配置开关
（`arena_set_time_on_raid_start`，默认开），不想要就关掉。

另外提醒：默认对局时长 30 分钟 = 36000 tick，而一个昼夜是 24000 tick——
**一局会跨过约 1.5 个昼夜**（清晨进场、天黑、再天亮）。这其实很符合搜打撤的气氛，
但如果你想让"整局都在白天"，就得把对局时长压到 20 分钟以内，或改成冻住时间。

### 还没做

1. `raid region capture|place|reset|list` 四个命令接线（服务层 `RaidRegionService` 已就绪）
2. 开局时设置本局起始时间 +（可选）把玩家传送进竞技场/从竞技场撤出
3. mod 侧再兜一层"禁止自然刷怪"（现在靠虚空群系与维度类型；再加一道服务端拦截更保险）
