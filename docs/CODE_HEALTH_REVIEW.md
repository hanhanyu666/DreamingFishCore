# 代码健康审计（防屎山）

> 审计对象：`dreamingfishcore`（MC 1.21.1 / NeoForge）  
> 审计日期：2026-11-20  
> 方法：全量结构扫描 + 模式扫描 + 抽样阅读 + 纯逻辑测试比对  
> 结论：**整体底子不差，但有 5 处正在长成屎山的地方**，其中 1 处（配置类复制粘贴）已经成型，越晚拆越贵。

---

## 一、规模基线

| 项 | 数值 |
| --- | --- |
| 主代码 | **754 个文件 / 109,485 行** |
| 测试 | 134 个文件 / 12,820 行 |
| 测试/主代码文件比 | 17.8% |
| 最大包 | `gameplay`（431 个文件），其中 `playerattributes_system` 78、`zombie_system` 65、`raid_system` 37 |

## 二、五个问题（按严重度）

### 🔴 1. 配置类复制粘贴家族（已成型，最该先拆）

**证据**：六种生物各有一份 ~150~300 行的 JSON 配置类，**方法骨架一模一样**：

```
ArcherZombieConfig      default→init→reload→current→getConfigPath→getSchemaVersion
                        →load→readValidated→resolve→Resolved→validateAndNormalize→requireRange
CharredZombieConfig     同上（19 个方法，去掉特有逻辑后完全同构）
AdamantZombieConfig     同上
ZombieCommanderConfig   同上
EnderCreeperConfig      同上（17 个方法）
PhantomCreeperConfig    同上（16 个方法）
```
连 `requireRange` 都各写了两份重载。六个类合计实测 **1,484 行**（298 / 275 / 262 / 258 / 206 / 185），其中估计 **700~800 行是纯管道代码**（读文件、原子写、schema 校验、范围钳制）。

**为什么是屎山**：下一个僵尸变体会被"照着抄一份"——这是复制粘贴的复利。改一处（比如加 `reload` 失败日志格式）要改六处，漏一处就是不一致。

**怎么改**（建议顺序，每步都能独立编译）：

1. 抽 `common/config/JsonConfigFile<T>`：`load(defaults)` / `save` / 原子写 / `readValidated` / `requireRange` / `requireList` 全放进去；
2. 六个类改成 `extends JsonConfigFile<XxxConfig>`，只留**字段 + defaults + Resolved + 各自的业务校验**；
3. 顺手把 `ZombieSpeciesConfig`（838 行）里与六个子配置重复的部分收进基类。

**收益重估（自查后下调）**：真正重复的是每个类的**管道代码约 35~45 行**（init/reload/current/getConfigPath/load/readValidated/schema 检查/两组 requireRange），
字段校验里那些 `requireRange(x, 1, 200, "maxHealth")` 是**各自真实的业务范围**，不算重复。
所以实际能省的是 **约 180 行**（6 × 30），不是我初估的 700~800 行。

**结论：本批不做。** 理由：收益 180 行 vs 风险（六个文件同时被另一个会话在改、且要重新验证配置热重载）；
等这六个类不再被同时修改时再做，或者干脆等下一个生物变体出现时"以新带旧"——新类直接用基类，旧类自然折旧。

### 🔴 2. 三个超级类

| 类 | 行数 | 方法数 |
| --- | --- | --- |
| `StoryManager` | **1,941** | **100** |
| `AfterdreamStory` | 1,466 | 62 |
| `ImmersiveChatManager` | 1,331 | 66 |
| `OrganizationManager` | 1,098 | 47 |

**为什么是屎山**：`StoryManager` 一个类装了 100 个方法，等于把"阶段推进 / 任务 / 公告 / 存档 / 命令 / 校验"全塞进一个文件。任何改动都要在这 1941 行里找位置，合并冲突概率极高。

**怎么改**：按职责切（例如 `StoryStageService` / `StoryTaskService` / `StoryPersistence` / `StoryValidator`），**用委托而不是继承**，对外仍暴露 `StoryManager` 门面 → 调用方零改动。一次切一个职责，切完跑测试。

**工作量**：分批，每批 1~2 小时；**风险**：中（需要保证门面行为不变）。

### ✅ 3. `catch` 分类（已复核完毕：**无需改动**）

**先纠正我自己的初判**：第一轮用正则统计出"33 处无日志"，逐个复核后发现大部分是**误判**：

| 分类 | 数量 | 说明 |
| --- | --- | --- |
| 有日志 | **71** | `LOGGER.warn/error` 带上下文 ✓ |
| 重抛（close 后 throw） | **22** | `catch (Throwable t) { close(); throw t; }` ✓ 完全正确 |
| 带注释的刻意忽略 | **5** | `catch (Exception ignored) { // 理由 }` ✓ 可接受 |
| **真正可疑** | **14** | 无日志、无注释、也不重抛 ✗ |

**14 处的分布**（都不算致命，但都该补一行话）：

```
Command_TaskLocation.java ×6   命令解析失败后静默 return（玩家看不到任何提示）
Command_Clue / Command_Biomes / Command_RaidAnchor   同上（各自 1 处）
ImmersiveChatManager.java:1226 / TitleScreenUi.java:401   UI 静默失败
LoadingOverlayMixin / OnlineServerEntryMixin ×2           mixin 里静默失败
```

**逐个复核后：这 14 处全部是正确处理**（我的第一版正则把它们算成了"静默吞异常"）：

| 位置 | 实际处理 |
| --- | --- |
| `Command_TaskLocation` ×6、`Command_Clue`、`Command_Biomes`、`Command_RaidAnchor` | 都调了 `sendFailure(...)` 给玩家提示 ✓ |
| `TitleScreenUi:373/401` | `catch (Exception ignored)` + 注释说明 + 兜底值（"离线"） ✓ |
| `LoadingOverlayMixin:54` | 把异常传给 `onFinish.accept(Optional.of(throwable))` ✓ |
| `OnlineServerEntryMixin:116/159` | 置 `UNREACHABLE` 状态 / 返回 false ✓ |
| `ImmersiveChatManager:1226` | 日期探测，失败返回 false 是**语义**不是吞错 ✓ |

**结论：这一项关掉，不需要任何改动。** 教训：正则只能用来"缩小范围"，不能直接当结论。

**怎么改**：命令里改成 `sender.sendFailure(Component.literal("§c…"))`；mixin 里至少 `LOGGER.debug`。

**工作量**：30 分钟；**风险**：极低。

### 🟠 4. 服务级静态可变状态（不区分存档）

**证据**（节选）：
```
DisasterService.java:48/50        static volatile long announcedDay / warnedDay
MarkerManager.java:18/19          static List ACTIVE_SNAPSHOT / snapshotDirty
RaidRoster.java:216/217           static volatile Roster roster / boundRaidId
RaidAnchorService.java:51-54      static volatile catalog/overlay/loadProblems/loaded
ExtractionService.java:53-61      static volatile 六个快照字段
LooseLootService.java:99/100      同上
LimbDamageEventHandler.java:52/57 public static boolean ENABLED/DEBUG
ModItemModelProvider.java:22      static LinkedHashMap trimMaterials
```

**为什么是屎山**：这些状态**不区分"哪个存档"**。单人存档快速来回切、或一个服务端跑多个世界时，会出现"上个存档的日程/名单还在"这类灵异现象；测试也没法并行。

**怎么改**：分两类处理——
- **可以是全局的**（配置、缓存）：保留，但**必须在 `ServerStopped` 时清空**（现在部分清了，部分没清，见 `clear()` 的覆盖情况）；
- **必须跟存档走**（日程、名单、快照）：搬到 `SavedData` 或显式的 `WorldContext`，禁止 static。

**工作量**：大（分模块推进，每次只动一个 service）；**风险**：中高（涉及存档）。

### 🟡 5. 硬编码中文与"日志即文档"的边界

**证据**：主代码里有 **3,579 处**中文字符串字面量（聊天/HUD 文本）。

**判断**：对中文服这是**可接受的刻意选择**（不折腾 lang 文件），不算屎山。但要立两条边界：
- **物品/方块/生物名称必须走 lang**（否则 JEI/资源包会露 `item.xxx`）；
- **命令与错误的用户可见文本**可以硬编码，但**日志必须英文短语 + 参数**，便于 grep。

### 🟡 6. 其他（记一笔，不急着动）

- `EventType` 的 `switch` 分散在 5 个文件（`DisasterService` ×2、`TerrainDisasterEffects`、`DisasterPage` ×2、`UiCanvas`、`ClueGrantSource`）——其中 `UiCanvas`/`ClueGrantSource` 是别的枚举，**新增天灾类型时容易漏**。建议把"颜色/名字"收进 `EventType` 自身（枚举带颜色字段），switch 数量从 4 降到 1。
- `RaidConfig` 与其他配置类风格不统一（它是手写 getter + `clamp`，其余是 record 式）——并入第 1 条的基类时顺手统一。
- 待观察：`docs/` 里说明书已 800+ 行，属于"文档也是代码"的负债，但结构清晰（按章分节），暂不建议拆。

---

## 三、已经做对的地方（别在重构里弄丢）

| 好东西 | 证据 |
| --- | --- |
| **纯逻辑优先** | `RaidRandom`、`DisasterSchedule`、`DisasterTiming`、`DisasterReactorRules`、`FrontierRules`、`InfectionRules` 都不碰 Minecraft 世界，配套单测 134 个类 |
| **软失败** | 数据读坏的路径基本是"跳过 + 记日志 + 不阻塞启动"（`readValidated` 家族） |
| **确定性随机** | 搜打撤/天灾的抽取都用 `RaidRandom`，不用时间种子 |
| **服务端权威** | 奖池/日程/名单都在服务端算，客户端只渲染（除天灾 UI 的编辑请求，且服务端二次校验权限） |
| **注册显式** | 每个子系统一个 `XxxEntities/Blocks/Items` 注册器，`DreamingFishCore` 里一眼能看到全部挂载点 |
| **常量具名** | 数值参数基本都提成了具名常量并有注释（如 `HORDE_PER_PLAYER`、`STORM_FOV_SCALE`） |

## 四、建议的清理顺序

```
第 1 步（止血，约 0.5h） 14 处可疑 catch 补提示/日志（已逐个定位）
第 2 步（拆复利，约 3h） 配置类基类：JsonConfigFile<T>，六种生物配置合并
第 3 步（拆门面，分批）  StoryManager 按职责切，先切"存档/校验"这两块最独立的
第 4 步（治状态，分模块）把跟存档走的状态从 static 挪到 SavedData/WorldContext
第 5 步（加护栏）        见下方"护栏清单"
```

## 五、护栏清单（防止以后再长出来）

1. **单文件行数上限 800**：超过就必须拆（超限清单：`StoryManager` 1941、`AfterdreamStory` 1466、`ImmersiveChatManager` 1331、`OrganizationConfig`? 否；前三个 + `OrganizationManager` 1098、`SiegeZombieEntity` 911、`UiCanvas` 901、`UiNode` 861、`ZombieSpeciesConfig` 838、`DeathCorpseManager` 820、`NpcMessageManager` 802）。
2. **新配置类不得再手写 load/save**：必须继承 `JsonConfigFile<T>`。
3. **`catch` 必须带日志**：提交前 grep `catch\s*\(\s*Exception` 人工过一遍。
4. **禁止 `System.currentTimeMillis` 参与玩法判定**（日志/超时除外）。
5. **新增枚举分支前先 grep**：`case ZOMBIE_HORDE` 之类要一次改齐（或把 switch 收进枚举）。
6. **每个新玩法必须带纯逻辑测试**：现有 134 个测试类的习惯要保持。

---

## 附：本次审计未改动任何代码

报告里的"怎么改"都还没动手——按服主的规矩，**改代码要等明确指令**。
建议从"第 1 步：33 处 catch 补日志"开始（风险最低、收益立竿见影）。

---

## 六、本批已落地（低风险部分）

### ✅ 1. 护栏：`CodeHealthTest`（可执行的屎山预算）

新增 `src/test/java/com/hhy/dreamingfishcore/architecture/CodeHealthTest.java`：

| 规则 | 判定 |
| --- | --- |
| 单文件 **800 行** 软上限 | 超限文件数**不得超过基线 11 个**（只许减不许增） |
| 单文件 **2500 行** 硬上限 | 任何文件都不许突破 |

失败信息会列出所有超限文件与行数，所以"下次再加一个大文件"会直接 CI 红 ✗。
这是把"评审时说一句"变成"机器强制"的最小成本做法（0 运行时开销）。

### ✅ 2. `EventType` 自带名字与颜色（switch 从 4 处降到 2 处转发）

- `EventType` 新增 `chatColor`（如 `§4`）、`uiColor`（ARGB）、`coloredName()`；
- `DisasterService.coloredName(...)` 与 `DisasterPage.colorOf(...)` 改为**一行转发**；
- 效果：新增一种天灾时，**颜色与名字只有一处要填**，编译器会提醒构造器参数变化。

> 过程中被测试抓到一个真疏漏：改写枚举常量时把四个地形天灾的**群系标签弄丢了**
>（`DisasterTypeTest` 三条直接报红），已补回。这说明现有测试网是有效的。

## 七、更新后的清理顺序

```
✅ 已完成  护栏：CodeHealthTest + EventType 颜色收敛（本批）
✅ 已关闭  33 处 catch（复核后全部正常，无需改动）
⏸  暂缓   配置类基类合并（收益 180 行 vs 六个文件正被并行修改）
▷  下一步 StoryManager（1941 行 / 100 方法）按职责切门面 —— 收益最大，风险中
▷  之后    静态状态迁 SavedData（分模块）
```

## 八、给未来的三条硬约定

1. **新配置类**不得手写 load/save —— 用 `JsonDataStore`（已有 `read` / `writeAtomic`）+ 各自的 `validateAndNormalize`；
2. **新增枚举分支前先 grep**：`case ZOMBIE_HORDE` 之类要一次改齐；名字/颜色这类展示信息直接放进枚举；
3. **每个新玩法必须带纯逻辑测试**：现有 135 个测试类 / 846 个用例是这套代码最值钱的资产。

### ✅ 3. StoryManager 拆分：第 1 刀（统计查询）

| 项 | 变化 |
| --- | --- |
| `StoryManager` | **2077 → 2010 行**（−67） |
| 新增 `StoryStatistics` | 8 个只读统计方法（约 150 行，逐个从门面**原样搬运**） |
| `StoryManager` 侧 | 8 个方法各留一行转发 + 6 个包内访问器 |
| 调用方 | **零改动**（签名不变） |

**为什么先切这块**：它没有任何副作用（只读世界状态与定义表），搬运可逐行对照，风险最低。
`getTaskStatisticsString` / `getStageStatisticsString` 的命令输出格式逐字保留。

**下一刀建议**（按"内聚 + 低耦合"排序，每刀都要能独立通过测试）：

| 顺序 | 目标类 | 规模估计 | 说明 |
| --- | --- | --- | --- |
| 2 | `StoryDefinitionRegistry` | ~300 行 | 定义加载/校验/安装（`loadWorldData` 起的 12 个方法 + 4 张定义表） |
| 3 | `StoryStateStore` | ~80 行 | `ensureLoaded`/`ensureWritable`/`saveIfDirty`/`statePath`/`clearWorldCache` + `state/dirty/loaded` 字段 |
| 4 | `StoryViewFactory` | ~250 行 | `createStageView`/`createTaskView`/进度计算（依赖前两块先就位） |
| 5 | `StoryProgression` | 剩余 | 阶段推进、任务激活、门禁、回顾 |

> 经验：**先切只读的，再切带状态的**。带状态的那刀要连字段一起搬，会牵动很多调用点，
> 所以永远在"测试全绿 + 没有并行改动"的时候做。

