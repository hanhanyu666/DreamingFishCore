# 剧情系统侦察报告与「加一章」的施工说明

> 目的：把"做剧情章节链"这件事的**真实结构**摸清楚，避免另起一套并行系统（那会让同一个服务器有两套剧情进度，互相打架）。
> 结论先说：**阶段/任务的骨架在 Java 状态机里，只有文案是策划可改的数据文件。**加章节要先读三个文件的完整格式，再动最小的一处。

---

## 一、现有剧情系统（另一个会话的作品，18 个 Java 文件）

```
gameplay/story_system/
  StoryManager.java              2056 行 —— 唯一的阶段运行时（状态机 + 定义安装 + 发布 + 结算）
  StoryWorldState.java           世界层剧情状态（当前阶段、已完成任务、个人进度）
  StoryStageData.java            阶段定义 + 客户端视图 + 阶段怪物倍率
  StoryTaskData.java             任务定义 + 个人/世界任务 + 完成/失败状态
  StoryTaskOutcome.java          任务结算结果
  StoryStageCatalog.java         阶段常量（DREAM_BEGINNING_ID 等）
  StoryOperationsCatalog.java    运营目录（世界任务/前情草稿，**从文件读**）
  StoryOperationsState.java      运营目录状态
  StoryCheckpoint.java           接入点（把玩家放到某个阶段）
  StoryPlayerGate.java           玩家准入判断
  WorldHistoryLog.java           世界历史
  ContentPackManager.java        内容包门面：validate / reload（先文案后定义）
  runtime/StoryRuntimeEventHandler.java  运行时事件钩子（触发器落点）
  runtime/StoryTextCatalog.java          文案目录（键 → 文本）
  command/Command_Story.java     服主命令：status / stage set / content validate|reload / history / recap
  command/Command_StoryDebug.java 调试命令
  network/Packet_WorldHistory{Request,Response}.java
gameplay/afterdream_story_system/   余梦期（第二阶段）的剧情实现
```

## 二、定义是怎么"装配"起来的（关键）

```
StoryOperationsCatalog.read()          ← 从文件读运营目录（世界任务、前情草稿）
        │
        ▼
StoryManager.createDefaultDefinitions(operations)   ← 把运营目录 + Java 内置阶段合成 List<StoryStageData>
        │  （StoryManager.java:538 / 545）
        ▼
StoryManager.validateDefinitionDocument(document)   ← 校验（StoryManager.java:587）
        │   · 阶段必须有 id、number>0、name 非空，任务逐个校验
        │   · **必须存在默认阶段** StoryWorldState.DEFAULT_STAGE_ID（= StoryStageCatalog.DREAM_BEGINNING_ID）
        ▼
StoryManager.installDefinitions(document)           ← 建立索引（StoryManager.java:631）
        │   STAGES_BY_ID / STAGES_BY_NUMBER / TASKS_BY_KEY / TASKS_BY_NUMBER / TASK_STAGE_IDS
        ▼
StoryManager 状态机驱动：当前阶段 → 发布任务 → 完成/失败结算 → 切阶段
```

**文案**（与骨架分离，这才是策划可改的部分）：

```
JAR 内置默认：src/main/resources/dreamingfishcore/defaults/story_text.json
世界层可编辑副本：由 StoryTextCatalog 管理（loadWorldData / reloadDefinitions）
键名常量集中在 StoryTextCatalog 里（如 opening.stage.name / opening.task.settle.content）
   → Java 侧用常量引用，策划只改 JSON 里的句子，改不到顺序/奖励/触发条件
```

**内容包热重载顺序**（`ContentPackManager.reload`）：**先文案、后定义**——所以改文本后一次 reload 就生效，不会出现"要重载两次"。

## 三、"加一章"的施工顺序（下一刀照这个做）

1. **先读三份格式**（这一步不能跳，猜格式必错）：
   - `StoryOperationsCatalog`：运营目录文件的**路径与 JSON 结构**（世界任务/前情草稿怎么写）
   - `StoryTaskData`：任务的字段全集（任务类型、触发器、目标数量、奖励、个人/世界、默认发布与否）
   - `StoryStageCatalog` + `StoryWorldState`：阶段 id 常量与默认阶段约束
2. **加定义**：在运营目录（优先）或 `createDefaultDefinitions` 里新增一章：编号取**当前最大编号 +1**，
   id 用命名空间格式（`dreamingfishcore:xxx`），并按现有章节的写法补任务。
3. **加文案**：`defaults/story_text.json` 补该章所有键（阶段名/描述、每个任务的名称与内容、公告/引导），
   `StoryTextCatalog` 里补对应常量（Java 侧引用）。
4. **验证**：`/dreamingfish story content validate`（只校验不安装）→ 通过后
   `/dreamingfish story content reload <contentId>` → `/dreamingfish story status` 看是否落到新章。
5. **不要**新建并行的 `StoryChapterService` / `story_chapters/*.json`：那会让服务器同时存在两套剧情进度。

## 四、风险与红线

| 风险 | 说明 |
| --- | --- |
| **改坏状态机** | `StoryManager` 在管**真实玩家进度**。定义校验失败会抛异常；安装失败会保留旧定义（这是好事），但改坏了 `createDefaultDefinitions` 可能让服务器起不来 |
| **阶段编号冲突** | 编号必须唯一且 >0，`installDefinitions` 按编号排序；编号重复会让"当前阶段"判断错乱 |
| **默认阶段必须存在** | 校验强制要求 `DREAM_BEGINNING_ID` 对应的阶段存在，删改它会让校验直接失败 |
| **文案键缺失不报错** | 旧文案文件可能没有新键 → 运行时会回退内置文本（不阻断），所以**新加的键要同时补进内置 JSON** |
| **无法离线验证** | 剧情只在服务端跑，`validate` 也要开服。改完必须开服跑一遍 `story content validate`，不能只靠编译通过 |

## 五、给下一刀的最短路径建议

- 如果只是**继续写剧情内容**（文案、任务描述、公告）：只动 `defaults/story_text.json`（+ 世界层副本），
  **零风险**，不需要碰 Java。
- 如果要**新增一章**（含新任务与触发）：按第三节 1→5 走，且**先只加一章**、开服验证通过后再加第二章。
- 如果要给剧情**接上搜打撤**（例如"完成一次撤离"作为任务条件）：需要看
  `runtime/StoryRuntimeEventHandler` 现有的触发器落点，把撤离成功事件接进去——
  这是新增触发类型，属于状态机改动，要和剧情系统的作者（另一个会话）对齐写法。
