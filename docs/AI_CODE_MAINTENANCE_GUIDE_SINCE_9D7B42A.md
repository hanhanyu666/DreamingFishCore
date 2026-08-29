# 9d7b42a 之后的 AI 代码维护总览

> 项目：DreamingFishCore 1.21.1
> 对比基线：9d7b42a55d9233199d5f8e83287055c10427944c
> 当前检查分支：1.21.1
> 当前检查 HEAD：5a5cfdd48a695d1f2c67bf03327cf382580971fd
> 整理日期：2026-08-21
> 口径：基线提交之后的提交和当前工作区改动，均视为 AI 参与开发内容。

## 1. 这份文档解决什么问题

这不是源码的第二份副本，而是后续维护时的“地图”：

- 说明从基线以后改了多少内容、涉及哪些提交。
- 说明每个新系统位于哪里、由哪些类负责。
- 说明服务器权威数据、客户端缓存、配置和世界存档之间如何流转。
- 标出当前尚未提交的新文件与已修改文件，避免漏提或误删。
- 标出已发现的配置错位、运行目录差异和第三方依赖风险。
- 给出以后修改短信、引导、教程、剧情阶段、死亡尸体等功能时的检查顺序。

不建议把所有 Java 源码逐字粘贴进本文。逐字副本会在第一次修改后立刻过期，并且无法替代 Git。本文以“完整文件索引 + 职责 + 数据流 + 修改入口”的方式记录代码；真实实现仍以 src、resources 和 Git 历史为准。

## 2. AI 开发范围和对比结论

基线提交信息：

- 提交：9d7b42a55d9233199d5f8e83287055c10427944c
- 日期：2026-05-08
- 标题：将加载提示移入配置目录
- 该提交是当前 HEAD 的祖先，可以直接进行范围比较。

在创建本文档之前统计到：

| 范围 | 文件数 | 新增行 | 删除行 |
| --- | ---: | ---: | ---: |
| 基线到当前 HEAD | 409 | 26,403 | 14,113 |
| 当前 HEAD 到工作区（仅已跟踪文件） | 59 | 3,770 | 1,958 |
| 基线到当前工作区（仅已跟踪文件） | 412 | 28,919 | 14,817 |

另外还有：

- 155 个未跟踪文件。
- 其中 51 个是新的 Java 源码文件，约 4,958 行。
- 4 个是新的测试文件，约 150 行。
- 6 个是已有的未跟踪说明文档，约 557 行。
- 暂存区为空，当前改动都还没有被 git add。

基线到当前工作区的 Git 状态类型：

| 类型 | 数量 |
| --- | ---: |
| 新增 A | 170 |
| 删除 D | 49 |
| 修改 M | 42 |
| 重命名 R | 151 |

注意：大量 R、D、A 来自包结构重构和文件迁移，不代表 151 个文件都被完全重写。审阅时应启用 Git rename detection，避免把移动误判为删除后重建。

## 3. 基线之后的 12 个提交

| 顺序 | 提交 | 日期 | 标题 | 变更规模 |
| ---: | --- | --- | --- | ---: |
| 1 | eef1f15 | 2026-06-08 | UI更新 | 33 files，+891/-340 |
| 2 | 130fc64 | 2026-06-17 | UI更新 | 47 files，+3078/-724 |
| 3 | 6af16b0 | 2026-06-19 | 添加多人标点系统 | 8 files，+722 |
| 4 | df3afa1 | 2026-07-19 | Validate stored respawn locations | 1 file，+11/-8 |
| 5 | 549e35e | 2026-07-19 | Fix UTF-8 config migration | 18 files，+279/-66 |
| 6 | c89a61c | 2026-07-21 | 重构项目包结构并统一全服故事系统 | 296 files，+8202/-5638 |
| 7 | 28173d9 | 2026-07-27 | 新增任务地点系统 | 131 files，+4195/-1837 |
| 8 | 9503e9b | 2026-07-27 | 新增 Builder FISH 与玩家 Rank 管理器 | 19 files，+468/-58 |
| 9 | 7960b5a | 2026-08-05 | 保存当前开发进度 | 17 files，+762/-49 |
| 10 | b0cea5d | 2026-08-05 | 改进玩家状态 HUD | 8 files，+368/-68 |
| 11 | 9755a23 | 2026-08-06 | 完成 1.20.1 功能向 NeoForge 1.21.1 的移植 | 84 files，+815/-435 |
| 12 | 5a5cfdd | 2026-08-20 | 完善沉浸式聊天、界面交互与数据同步 | 38 files，+9307/-7585 |

提交作者字段不能准确表示是谁实际写了代码。根据当前约定，上表 12 个提交全部纳入 AI 代码范围，不再区分是本助手还是其他 AI。

## 4. 当前架构总原则

### 4.1 服务器权威

会影响玩法、公平性或剧情的状态应只由服务器决定：

- 故事阶段和全服剧情进度。
- NPC 好感度、短信解锁条件和回复结果。
- 玩家是否加入逐光会。
- 个人引导状态。
- 死亡选择、物品恢复和尸体内容。
- 经济数据桥接结果。

客户端只负责请求、缓存和显示。不要把“客户端按钮点击后直接改本地字段”当成真实状态变更。

### 4.2 三类数据位置

1. 模组配置
   位于 config/dreamingfishcore/，适合管理员可编辑、可随整合包分发的规则和内容。

2. 世界存档数据
   位于当前世界目录 data/dreamingfishcore/，适合故事进度、玩家短信、个人引导等运行状态。

3. 客户端缓存
   位于 client/cache 包，只用于界面快速渲染。重新登录或收到同步包时会刷新，不能作为最终依据。

### 4.3 数据修改链路

常见链路如下：

玩家操作
→ 客户端 Screen
→ Request 网络包
→ 服务端 Manager 校验并修改
→ 保存配置或世界数据
→ Response/Snapshot 网络包
→ ClientCache
→ Screen 重新渲染

排查“点了没反应”时，按这条链路从前到后检查，不要只看 UI。

### 4.4 JSON 写入

现有新系统倾向先写临时文件，再原子替换正式文件。维护时不要改回直接覆盖写入，否则崩溃或断电可能留下半份 JSON。

### 4.5 包结构

2026-07-21 的大规模重构把代码大体划分为：

- client：纯客户端界面、渲染、缓存和客户端事件。
- gameplay：具体玩法域，例如 NPC、故事、死亡、玩家数据。
- server：服务器管理、桥接和生命周期。
- network：公共网络注册与通用包。
- mixin：对 Minecraft 原界面的注入。
- commands：命令入口。
- init：注册与初始化。

增加新功能时应放进对应业务域，不要再把所有类堆进一个 UI 或 manager 包。

## 5. 模块总索引

| 模块 | 核心目录/入口 | 状态保存 | 客户端显示 |
| --- | --- | --- | --- |
| 全服故事 | gameplay/story_system | world_state.json | 终端故事页 |
| 任务地点 | gameplay/task_location_system | 配置与世界状态 | 地点/标点界面 |
| NPC 与好感度 | gameplay/npc_system | NPC/玩家关系数据 | NPC 交互与档案 |
| NPC 短信 | gameplay/npc_message_system | npc_messages.json | 终端短信页 |
| 个人引导 | gameplay/guidance_system | player_guidance.json | 终端引导区域/页 |
| 逐光会身份 | gameplay/zhuiguang_system + PlayerData | 玩家数据 | 个人档案，仅成员显示 |
| 新手教程 | server/notice_system + server/login_system + 终端帮助页 | PlayerLoginData 标记 | 左上角提示、帮助页 |
| 沉浸聊天 | client/ui/chat + 服务端聊天逻辑 | 配置、JSONL 历史 | 游戏 HUD |
| 体征系统 | gameplay/playerattributes_system + server/playerdata_system | 玩家数据 | HUD、个人档案 |
| 死亡与尸体 | gameplay/playerattributes_system/death | 临时死亡数据、实体 NBT | 死亡页、尸体容器 |
| 多人标点 | gameplay/marker_system | 服务端/玩家状态 | 世界标点 |
| 经济桥接 | server/economy_bridge | 外部 EconomySystem | 终端经济入口 |
| 终端 UI | server/server_ui_system | 客户端状态 | U 键服务器菜单 |
| 加载过渡 | client/ui/loading + mixin | 无长期状态 | 连接/加载界面 |
| 通知 | client/ui/notification + server/notice_system | 客户端队列 | 屏幕通知 |

## 6. 全服故事系统

主要入口：

- src/main/java/com/hhy/dreamingfishcore/gameplay/story_system/StoryManager.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/story_system/StoryWorldState.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/story_system/
- docs/STORY_SYSTEM_CODE_GUIDE.md

当前阶段约定：

| 顺序 | 稳定 ID | 中文名 | 含义 |
| ---: | --- | --- | --- |
| 1 | dreamingfishcore:dream_beginning | 梦的开始 | 玩家刚进入服务器，危机刚出现，逐光会正在筹建 |
| 2 | dreamingfishcore:afterdream | 余梦期 | 原本的第一阶段后移为第二阶段 |

关键原则：

- 阶段 ID 是存档和配置的稳定键，中文显示名可以改，稳定 ID 不应随意改。
- StoryWorldState.DEFAULT_STAGE_ID 当前应为 dreamingfishcore:dream_beginning。
- afterdream 已被历史配置和数据引用，保留该 ID 比改成“第二阶段”的拼音更安全。
- 全服共同推进和玩家个人选择是两条不同轴。全服阶段由服务器统一保存；个人对 NPC 的关系、短信和组织身份分别保存。

世界状态文件：

<世界目录>/data/dreamingfishcore/story/world_state.json

管理员阶段配置：

config/dreamingfishcore/story_stage_data.json

修改阶段时至少同步检查：

1. Java 默认阶段。
2. run/config 的开发配置。
3. dev2/config 的第二开发实例配置。
4. 已有世界的 world_state.json 迁移。
5. NPC 短信中的 storyStageId。
6. UI 展示名和世界历史文本。
7. 测试中的固定阶段 ID。

不要只修改终端显示文字。显示为“梦的开始”但服务端仍处于 afterdream，会导致短信、引导和条件判断全部错位。

## 7. 任务地点与世界历史

任务地点系统来自提交 28173d9，主要代码位于：

- src/main/java/com/hhy/dreamingfishcore/gameplay/task_location_system/
- 相关网络包、客户端缓存和终端界面入口。

用途：

- 给剧情目标提供可追踪地点。
- 将 NPC 的文字指令关联到实际坐标或区域。
- 配合多人标点帮助全服共同完成建设、调查和救援。

世界历史用于回看全服共同经历，不应等同于“当前任务列表”。故事阶段负责宏观状态，世界历史负责已发生事件，个人引导负责某个玩家现在应该做什么。

## 8. NPC 基础系统与好感度

主要入口：

- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_system/NpcManager.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_system/NpcRelationData.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_system/NpcRelationManager.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_system/command/Command_Npc.java

管理员 NPC 定义：

config/dreamingfishcore/npc_data.json

每个世界中的玩家关系：

<世界目录>/data/dreamingfishcore/npc/relations.json

当前设计已经支持“不同好感度影响 NPC 行为和提供的信息”。关系数据与短信条件结合后，可以实现：

- 好感度不足时不出现某些回复。
- 好感度达到阈值后解锁私密线索。
- 某次回复增加或降低好感度。
- 是否逐光会成员影响可见消息和选项。
- 同一个 NPC 对不同玩家呈现不同关系。

维护时必须区分：

- NPC 定义：这个人物是谁，通常是管理员配置。
- 玩家与 NPC 的关系：每个玩家不同，属于世界运行数据。
- 短信记录：已经发生的对话。
- 引导条目：对话产生的可执行目标。

不要把好感度直接写进 NPC 公共配置，否则一个玩家的选择可能污染所有玩家。

## 9. NPC 短信系统

### 9.1 文件清单

核心领域：

- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcConversationViewData.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcMessageConfig.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcMessageDefinition.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcMessageManager.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcMessageRecord.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcMessageReplyDefinition.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcMessageViewData.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcReplyViewData.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/PlayerNpcMessageData.java

客户端缓存：

- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/client/cache/NpcMessageClientCache.java

事件：

- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/event/NpcMessageSyncEvent.java

网络包：

- Packet_NpcMessageReadRequest.java
- Packet_NpcMessageReplyRequest.java
- Packet_NpcMessageSnapshotRequest.java
- Packet_NpcMessageSnapshotResponse.java

以上网络包位于 npc_message_system/network。

### 9.2 配置和存档

管理员短信内容：

config/dreamingfishcore/npc_messages.json

玩家短信运行记录：

<世界目录>/data/dreamingfishcore/communication/npc_messages.json

管理员配置描述“什么消息可以发生”；世界存档记录“某个玩家已经收到、已读或回复了什么”。两者不可混用。

### 9.3 触发方式

当前定义支持：

- INTERACTION：玩家与 NPC 交互后触发。
- MANUAL：命令、剧情脚本或管理员主动发送。
- FOLLOW_UP：收到或回复上一条消息后继续对话。

### 9.4 服务端校验

发消息和回复前，服务端应统一验证：

- 消息定义是否存在。
- NPC 是否存在。
- 当前故事阶段是否满足。
- 玩家是否满足逐光会身份条件。
- 好感度是否达到上下限。
- once 类型消息是否已经发送。
- 回复是否属于当前消息且仍可选择。
- 回复附带的好感度、组织身份和引导副作用是否允许执行。

客户端显示出的选项不能替代服务端验证。恶意客户端可以自行构造网络包。

### 9.5 容量限制

当前保护性限制：

- 每名玩家服务端最多保留 2,048 条短信记录。
- 客户端每个会话最多取最近 256 条。
- 快照最多返回 64 个会话。

这些限制是防止无限增长和超大网络包。若要扩容，应同时评估 JSON 大小、登录同步耗时和界面滚动性能。

### 9.6 UI 位置

短信是终端中的独立功能：

- 主页为短信留出大区域。
- Dock 保留广播，并新增短信快捷入口。
- 短信页与引导数据分开，不能再合成同一个“短信与引导”页面。
- 当前 ServerScreenUI_Screen 的短信页索引为 10。

## 10. 个人引导系统

### 10.1 文件清单

- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/GuidanceEntry.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/GuidanceManager.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/GuidanceSeed.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/GuidanceViewData.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/client/cache/GuidanceClientCache.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/command/Command_Guidance.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/network/Packet_GuidanceSnapshotRequest.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/network/Packet_GuidanceSnapshotResponse.java

### 10.2 数据位置

<世界目录>/data/dreamingfishcore/guidance/player_guidance.json

引导是个人状态：同一全服阶段下，不同玩家可能因为 NPC 关系和选择看到不同目标。

### 10.3 与短信的关系

短信和引导是两个系统：

- 短信负责“谁说了什么、玩家怎么回复”。
- 引导负责“玩家接下来可以做什么、目标是否完成”。

短信定义可以显式附带 guidance，从而在消息或回复发生时创建引导。当前没有自然语言识别，不会从“去码头看看”自动推断一个任务；要产生引导必须在配置里明确写出。

服务端负责解析和落盘，客户端只收到可展示的 GuidanceViewData。

### 10.4 UI 现状

设计要求是短信和引导分开。目前代码虽有独立引导数据与页索引 11，但界面还没有完全满足该要求：主页把引导摘要并入故事卡，独立引导点击区域被隐藏，旧的引导入口也会重定向到故事页内的个人引导分区。后续重构时应恢复真正独立的入口和页面：

- 短信页：会话列表、消息记录、回复。
- 引导页：当前目标、地点、进度、来源 NPC。
- 故事页：全服阶段、阶段说明、世界历史。

不要再次把三种信息合并到同一个页面。

## 11. 逐光会成员身份

主要入口：

- src/main/java/com/hhy/dreamingfishcore/gameplay/zhuiguang_system/ZhuiguangMembershipAction.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/zhuiguang_system/ZhuiguangMembershipManager.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/zhuiguang_system/ZhuiguangMembershipRequirement.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/zhuiguang_system/command/Command_Zhuiguang.java
- src/main/java/com/hhy/dreamingfishcore/server/playerdata_system/PlayerData.java

它是一条独立身份轴，不等于 Rank、称号、职业或 NPC 好感度。玩家可以选择加入，也可以保持非成员。

当前条件枚举语义：

- ANY：不限制身份。
- MEMBER：必须是逐光会成员。
- NON_MEMBER：必须不是成员。

当前动作枚举语义：

- NONE：不改变身份。
- JOIN：加入逐光会。
- LEAVE：离开逐光会。

显示规则：

- 只有成员才在个人档案显示“逐光会成员”。
- 非成员不显示“独立协作者”标签。
- 命令反馈或系统说明中仍可能使用“独立协作者”来描述非成员状态；如果产品要求任何地方都不出现，还需统一搜索和替换。

加入/退出动作应由服务器执行并保存到 PlayerData，再同步客户端。

## 12. 终端主页、Dock 与页面结构

核心界面：

- src/main/java/com/hhy/dreamingfishcore/server/server_ui_system/client/serverscreen/ServerScreenUI_Screen.java
- src/main/java/com/hhy/dreamingfishcore/server/server_ui_system/client/serverscreen/ServerScreenUI_RendererUtils.java
- src/main/java/com/hhy/dreamingfishcore/server/server_ui_system/client/serverscreen/ServerScreenUI_ClientEventHandler.java
- src/main/java/com/hhy/dreamingfishcore/server/server_ui_system/client/serverscreen/ServerScreenUI_RoundedRenderer.java

ServerScreenUI_Screen 当前约 3,877 个物理行，是维护风险最高的单文件之一。建议后续按 page 或 panel 拆分，不要继续向一个类中添加所有布局、点击、滚动和网络请求。

当前产品要求：

- 主页删除设置卡片，但 Dock 中设置保留。
- 主页删除玩家与排行。
- 主页删除服务器成就。
- 故事进展卡片缩小，避免空白浪费。
- 给短信和引导分别留出明显区域。
- Dock 保留广播。
- Dock 增加短信快捷跳转。
- 短信与引导不能合成一个模块。

当前 Dock 目标结构：

主页 / 档案 / 广播 / 短信 / 故事 / 设置

引导可从主页大卡片或故事附近的独立入口进入；不要用“短信”按钮同时切换两个系统。

故事/任务页的阶段列表由 `ServerScreenUI_Screen.renderStoryStageList` 按可用宽度计算列数和紧凑卡片尺寸，使用响应式紧凑网格并支持按行滚动。当前阶段读取同步到 `StoryStageData` 的运行时 `currentStage` 标记；不能按最大 `stageNumber` 或最大编号推断当前阶段。

UI 修改时同时检查：

1. render 绘制位置。
2. mouseClicked 点击热区。
3. mouseScrolled 滚动区域。
4. 页面切换时缓存请求。
5. 窗口缩放后的相对布局。
6. 中文长文本的裁切和换行。
7. 未读红点与已读请求。

## 13. 新手教程与帮助页

主要入口：

- src/main/java/com/hhy/dreamingfishcore/server/notice_system/event/NewPlayerGuide.java
- src/main/java/com/hhy/dreamingfishcore/server/notice_system/network/Packet_NewPlayerGuideViewed.java
- src/main/java/com/hhy/dreamingfishcore/server/notice_system/network/Packet_NewPlayerGuideCompleted.java
- src/main/java/com/hhy/dreamingfishcore/server/login_system/PlayerLoginData.java
- src/main/java/com/hhy/dreamingfishcore/server/login_system/PlayerLoginDataManager.java
- src/main/java/com/hhy/dreamingfishcore/server/server_ui_system/client/serverscreen/ServerScreenUI_Screen.java

当前帮助页的实际入口在 `ServerScreenUI_Screen.java`：

- `HELP_TOPICS` 保存六个帮助主题数据。
- `renderModulePage` 的 `case 1` 选择帮助页。
- `renderHelpTerminalPage` 负责当前帮助页渲染，并在真正渲染后调用 `reportNewPlayerGuideViewed`。
- `mouseClicked` 中的帮助主题点击区域负责切换主题。

`ServerScreenUI_PageRenderer.renderHelpPage` 是旧帮助页实现；当前入口不使用它。修改帮助页时应以 `ServerScreenUI_Screen` 中的上述入口为准，避免改到旧渲染器。

左上角提示的目标文案：

欢迎游玩梦鱼服，请按下u打开服务器菜单查看新手教程。

正确状态流：

1. 玩家尚未查看教程，`PlayerLoginData` 的 `hasCompletedNewPlayerGuidence` 持久标记为 false；该登录数据由 `PlayerLoginDataManager` 保存到 `config/dreamingfishcore/data/login_data.json`。
2. 玩家登录后由公告事件统一补发左上角常驻提示（单人模式也必须发送），客户端持续显示，不能仅靠时间自动消失。
3. 玩家按 U 打开终端并真正进入/渲染新手帮助内容。
4. 客户端发送 Packet_NewPlayerGuideViewed。
5. 服务端保存已查看状态。
6. 服务端发送 Packet_NewPlayerGuideCompleted 确认。
7. 客户端收到确认后才停止提示。

这样可以防止只打开终端首页就误判完成，也防止客户端本地标记在重登后丢失。

帮助页当前实现为六个主题（主题数据来自 `ServerScreenUI_Screen.HELP_TOPICS`）：

1. 身体与肢体
   介绍分部位生命值、头部受到 1.2 倍伤害等机制，并给出可以跳起调整受击部位的实用提示。

2. 体力与勇气
   说明增加、消耗和恢复条件，以及勇气高低带来的增益与负面效果。

3. 死亡与重生
   说明保留物品栏、尸体、分裂/重生点数、点数不足等待救援，以及感染者重生消耗更多。

4. 感染与体征
   说明受伤可能提高感染，感染状态会随世界故事变化，并提示在个人档案查看体征监测。

5. 共同推进剧情
   说明玩家共同完成事件推进全服剧情，不同对话改变 NPC 好感度并解锁不同线索。

6. 选择与后果
   说明个人与群体选择会影响后续剧情，不保证所有玩家看到同样内容。

帮助页文字应尽量来自稳定的帮助主题数据，而不是分散硬编码在多个 render 分支里。

## 14. 沉浸式聊天系统

主要入口：

- src/main/java/com/hhy/dreamingfishcore/client/ui/chat/ImmersiveChatManager.java
- 与聊天有关的网络包、Mixin、配置和客户端 HUD。

当前系统包含的责任不只是替换聊天框：

- 接收服务端富文本聊天数据。
- 在游戏画面上按持续时间和优先级显示。
- 保存并读取近期聊天历史。
- 处理断线、换服和缓存清理。
- 与原版聊天界面或发送流程的 Mixin 配合。

聊天历史采用 JSONL 思路保存，当前保留目标大致是：

- 最近 5 天。
- 最多约 450 条消息。

修改聊天历史格式时要提供兼容读取或迁移。JSONL 的优势是单条追加，但如果直接改变一行的字段含义，旧记录会在启动时解析失败。

## 15. 加载界面与过渡

新增入口：

- src/main/java/com/hhy/dreamingfishcore/client/ui/loading/LoadingTransitionController.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/ConnectScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/DisconnectedScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/GenericDirtMessageScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/GenericWaitingScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/LevelLoadingScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/LoadingOverlayMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/ReceivingLevelScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/TitleScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/MinecraftScreenTransitionMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/ProgressScreenMixin.java

相关已有文件：

- client/ui/loading/LoadingScreenUi.java
- src/main/java/com/hhy/dreamingfishcore/client/ui/util/LoadingTips.java
- src/main/java/com/hhy/dreamingfishcore/client/ui/util/UiBackgroundRenderer.java

LoadingTransitionController 目前按不同场景使用约 280、520、920 毫秒的过渡时间。修改时注意：

- Mixin 可能同时在多个原版 Screen 生命周期触发。
- 过渡控制器必须能处理重复调用和界面突然被替换。
- 不要在 render 线程进行文件或网络阻塞。
- 新增 Mixin 后同步更新 src/main/resources/dreamingfishcore.mixins.json。

## 16. 屏幕通知系统

主要入口：

- src/main/java/com/hhy/dreamingfishcore/client/ui/notification/Notification.java
- src/main/java/com/hhy/dreamingfishcore/client/ui/notification/NotificationRenderer.java
- src/main/java/com/hhy/dreamingfishcore/server/notice_system/client/NotificationClientDisplay.java

当前约定中，负数持续时间代表持久通知。新手教程左上角提示依赖“直到服务器确认已经查看才消失”，因此不要把所有负数时长统一钳制为 0，也不要在普通超时清理中删除持久通知。

测试：

- src/test/java/com/hhy/dreamingfishcore/client/ui/notification/NotificationTest.java

## 17. 玩家体征、体力、勇气、肢体与感染

主要数据和界面入口：

- src/main/java/com/hhy/dreamingfishcore/server/playerdata_system/PlayerData.java
- src/main/java/com/hhy/dreamingfishcore/server/playerdata_system/event/LoginSync.java
- src/main/java/com/hhy/dreamingfishcore/server/playerdata_system/network/Packet_RequestAllPlayerData.java
- src/main/java/com/hhy/dreamingfishcore/server/playerdata_system/network/Packet_SyncPlayerData.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/client/ui/hud/CustomStatueGUI.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/client/ui/hud/CustomHotbarGUI.java
- 终端个人档案页面。

系统概念：

- 身体不是单一血条，存在肢体状态。
- 头部伤害倍率为 1.2。
- 体力影响持续行动能力。
- 勇气会因环境、怪物、附近死亡等因素降低，也能通过安全、光亮或其他机制恢复。
- 勇气高低可带来增益或负面效果。
- 受伤或接近感染者可能提高感染状态。
- 感染表现允许随世界阶段变化。

PlayerData 已承载较多独立字段。添加字段时必须同步检查：

1. 默认值。
2. NBT/JSON 序列化与反序列化。
3. 老存档缺字段时的兼容值。
4. 登录同步包。
5. 全量玩家数据请求。
6. 死亡/重生是否保留。
7. 克隆玩家事件。
8. HUD 和个人档案是否正确刷新。

## 18. 死亡、重生与尸体系统

### 18.1 原有死亡流程入口

- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/PendingDeathData.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/event/DeathEventHandler.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/event/LoginDeathSync.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/client/ui/screen/Screen_RevivalCharm.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/network/Packet_KeepInventoryRequest.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/network/Packet_NormalRespawnRequest.java
- src/main/java/com/hhy/dreamingfishcore/mixin/death/DeathScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/death/RespawnMixin.java

### 18.2 新增尸体实现

实体和服务端：

- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/DeathCorpseEntities.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/DeathCorpseEntity.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/DeathCorpseInventory.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/DeathCorpseManager.java

客户端：

- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/client/DeathCorpseRenderer.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/client/DummyCorpsePlayer.java

通用兼容层：

- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/compat/CorpseAccessoryBridge.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/compat/CorpseAccessoryCompat.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/compat/CorpseAccessoryEntry.java

Accessories 兼容：

- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/compat/accessories/AccessoriesCorpseBridge.java

Curios 兼容：

- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/compat/curios/CuriosCorpseBridge.java

### 18.3 死亡事件顺序

当前实现的关键顺序不能随意交换：

1. 在高优先级死亡事件中抓取玩家完整装备和背包快照。
2. 只在原版 dropEquipment 流程中临时重定向 keepInventory 语义。
3. 在较低优先级 LivingDrops 阶段收集最终掉落。
4. 在清理原掉落前成功创建并填充尸体。
5. 普通重生时生成可解锁尸体；选择保留物品栏时原子恢复快照。

这段流程与原版 gamerule、其他死亡模组和事件优先级强相关。重构时如果先清空掉落再生成尸体，异常会直接吞掉玩家物品。

### 18.4 当前行为

- 玩家可以在死亡界面选择普通尸体重生或消耗资源保留物品。
- 一旦选择，应锁定选择，防止重复发包或两种恢复同时执行。
- 尸体可提供六行容器界面和快速拿取。
- 虚空或危险位置会尝试调整尸体生成位置。
- Curios/Accessories 中的额外装备通过桥接层快照和恢复。
- 感染者重生可以消耗更多点数。
- 点数不足时玩家需要等待其他玩家救援。

### 18.5 第三方依赖

build.gradle 中以 compileOnly 方式接入：

- Curios 9.5.1
- Accessories 1.1.0-beta.53

兼容代码必须在对应模组不存在时安全跳过，不能在类加载阶段直接引用可选类导致 NoClassDefFoundError。

### 18.6 风险与测试缺口

目前没有专门覆盖尸体全流程的自动化测试。上线前至少人工验证：

- 原版 keepInventory 开/关。
- 普通背包、护甲、副手。
- Curios 单独安装。
- Accessories 单独安装。
- 两者均未安装。
- 虚空死亡、岩浆死亡、跨维度死亡。
- 尸体位置区块未加载。
- 死亡后立即掉线再登录。
- 两次快速死亡。
- 容器已满时 shift-click。
- 保留物品和普通尸体请求重复发送。

这是当前开服风险最高的系统之一。

## 19. 多人标点系统

多人标点主要来自提交 6af16b0，核心包括：

- gameplay/marker_system 下的服务端数据和网络逻辑。
- src/main/java/com/hhy/dreamingfishcore/gameplay/marker_system/client/render/MarkerRenderer.java
- 命令、地点系统和客户端世界渲染。

标点会与剧情地点和玩家导航发生联系。修改坐标格式、维度 ID 或权限时，必须同时检查任务地点系统，避免“任务显示有地点但世界中没有标记”。

## 20. Rank、称号与 Builder FISH

主要涉及：

- 玩家 Rank 管理器。
- 称号/身份展示。
- Builder FISH 管理。
- PlayerData 同步。

重要区分：

- Rank 是管理或成长维度。
- 称号是展示维度。
- Builder FISH 是建设/权限相关身份。
- 逐光会成员是剧情组织身份。

四者不要复用同一个布尔值，也不要因为 UI 上都显示在个人档案就合并数据模型。

## 21. 经济系统桥接

新增服务端桥：

- src/main/java/com/hhy/dreamingfishcore/server/economy_bridge/EconomySystemBridge.java

客户端缓存和 UI 桥：

- src/main/java/com/hhy/dreamingfishcore/client/cache/EconomyTerminalClientCache.java
- src/main/java/com/hhy/dreamingfishcore/client/integration/EconomySystemUiBridge.java

网络包：

- src/main/java/com/hhy/dreamingfishcore/server/economy_bridge/network/Packet_EconomyTerminalRequest.java
- src/main/java/com/hhy/dreamingfishcore/server/economy_bridge/network/Packet_EconomyTerminalResponse.java

界面注入：

- src/main/java/com/hhy/dreamingfishcore/mixin/ui/ServerScreenUiEconomyBridgeMixin.java

本地 API：

- libs/economy_system-neoforge-1.21.1-1.3.0-api.jar

当前桥接策略：

- 服务端先确认 EconomySystem 模组已加载。
- 检查 API 主版本为 1。
- 服务端获取余额、领地、市场等摘要。
- 市场预览最多返回约 24 项，防止网络包过大。
- 客户端尝试通过反射打开 EconomySystem 原生界面。

风险：

- libs 下 API JAR 当前是未跟踪文件，换电脑或 CI 构建可能缺失。
- 反射依赖外部模组的类名、构造器和方法签名，升级后可能编译不报错但运行失效。
- API 版本检查只看主版本时，仍需关注同一主版本内的破坏性变化。
- 客户端没安装外部模组时必须显示不可用状态，而不是崩溃。

上线前应明确 API JAR 的合法分发方式，并让 CI 从稳定来源获取，而不是依赖开发者本机 libs。

## 22. 其他基线后系统

以下模块也位于 AI 开发范围内，虽然本轮主要需求不集中在它们：

- 服务器校验与安全检查。
- 防 TNT 或爆炸相关保护。
- 蓝图系统。
- 故事书/剧情文本内容。
- 玩家等级与成长。
- 世界数据生命周期。
- 登录数据同步。
- 命令建议 Mixin。
- 服务端信息显示。
- 配置 UTF-8 迁移。
- 保存的重生位置合法性校验。

对应入口可从以下文件和目录继续追踪：

- src/main/java/com/hhy/dreamingfishcore/DreamingFishCore.java
- src/main/java/com/hhy/dreamingfishcore/init/CommonInit.java
- src/main/java/com/hhy/dreamingfishcore/server/persistence/event/WorldDataLifecycleEvents.java
- src/main/java/com/hhy/dreamingfishcore/commands/DreamingFishCore_CommandManager.java
- src/main/java/com/hhy/dreamingfishcore/network/DreamingFishCore_NetworkManager.java

## 23. 网络协议注册

主要入口：

- src/main/java/com/hhy/dreamingfishcore/network/DreamingFishCore_NetworkManager.java

当前协议版本为 0.11.0。

全量任务数据包中的每个 `StoryStageData` 都额外同步运行时 `currentStage` 标记；该字段不写入故事定义 JSON，编码和解码必须保持相同顺序。协议不兼容时由握手版本阻止旧客户端连接。

当前文件中大致位置：

- 经济桥接包：约第 61–62 行。
- 新手教程确认包：约第 100–101 行。
- NPC 短信包：约第 110–113 行。
- 引导包：约第 114–115 行。

行号会随代码变化，只用于快速定位。新增网络包时要同时确认：

1. 唯一 Type/ID。
2. 编解码字段顺序一致。
3. 客户端包只在客户端线程操作 UI/cache。
4. 服务端包通过 context 获取真实发送玩家。
5. 服务端验证权限、状态和输入长度。
6. 修改不兼容字段时提升协议版本。
7. 登录早期发送时客户端缓存已经初始化。

## 24. 初始化和生命周期

关键文件：

- src/main/java/com/hhy/dreamingfishcore/DreamingFishCore.java
- src/main/java/com/hhy/dreamingfishcore/client/ClientSetup.java
- src/main/java/com/hhy/dreamingfishcore/init/CommonInit.java
- src/main/java/com/hhy/dreamingfishcore/server/persistence/event/WorldDataLifecycleEvents.java
- src/main/java/com/hhy/dreamingfishcore/client/cache/ClientCacheManager.java

新 manager 写好但没有在生命周期注册，会表现为“代码存在，功能完全不运行”。新增系统应检查：

- 模组构造阶段的注册。
- 客户端专用初始化是否隔离。
- 服务启动时加载配置。
- 世界加载时加载存档。
- 世界保存/服务器停止时落盘。
- 玩家登录时发送快照。
- 玩家退出/换世界时清理缓存。
- 客户端断开时清空上一个服务器的数据。

## 25. run 与 dev2 的真实含义

build.gradle 中存在两个开发运行目录：

- 普通客户端使用 run。
- clientDev2 使用 dev2，配置大约位于 build.gradle 第 90–93 行。

重要事实：

- run 被 .gitignore 忽略，不会进入提交，也不会自动发布到服务器。
- dev2 是第二客户端/第二账号的独立运行目录，不应成为主要内容配置来源。
- 当前 run 中约有 45 条短信、6 个 NPC、2 个故事阶段。
- 当前 dev2 中约有 4 条短信、1 个 NPC，故事阶段配置为空或不完整。
- Java 默认配置当前只提供约 4 条短信，远少于 run 中完整开场内容。

因此之前“内容为什么塞进 dev2”会造成真实问题：在 dev2 测试成功，不代表主 run 或正式服务器存在同样配置。

正确策略：

1. Java 默认值只保留最小可启动和迁移能力。
2. 正式剧情内容放入可版本控制、可部署的配置模板或数据包目录。
3. run 只作为本机主开发实例。
4. dev2 只作为第二账号联机测试实例。
5. 开服部署脚本明确复制哪一套配置。

当前最需要处理的发布风险是：完整开场短信和 NPC 内容主要存在被忽略的 run/config 中，不会随 JAR 或 Git 自动到达正式服务器。

## 26. 当前数据状态提醒

检查时观察到：

- run/config 下当前有 45 条 NPC 短信、6 个 NPC、2 个故事阶段。
- dev2/config 下当前只有 4 条短信、1 个 NPC，故事阶段内容缺失。
- run 的 npc_messages 配置中，10 条带 guidance 的消息仍把 storyStageId 写为 afterdream。
- 但当前第一阶段已经改为 dreamingfishcore:dream_beginning。

这会导致第一阶段的开场引导可能无法触发。上线前应逐条判断这 10 条消息属于：

- 第一阶段：改成 dreamingfishcore:dream_beginning。
- 第二阶段：保留 dreamingfishcore:afterdream。
- 跨阶段：移除该限制或改为支持阶段集合的模型。

不能机械地全部替换，必须按每条剧情含义判断。

此外，run/config 中两个测试账号的新手教程状态在当前检查时已经是 true，说明它们后来真正查看过帮助页。若要重新测首次进入流程，应使用管理命令或明确修改测试存档，并区分 run 和 dev2。

## 27. 测试与构建状态

最近一次完整测试结果：

- 31 个测试。
- 0 failures。
- 0 errors。

最近一次完整编译成功。

新增但尚未跟踪的测试：

- src/test/java/com/hhy/dreamingfishcore/client/ui/notification/NotificationTest.java
- src/test/java/com/hhy/dreamingfishcore/gameplay/guidance_system/GuidanceEntryTest.java
- src/test/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcMessageDomainTest.java
- src/test/java/com/hhy/dreamingfishcore/server/playerdata_system/PlayerZhuiguangMembershipTest.java

已有修改测试：

- src/test/java/com/hhy/dreamingfishcore/gameplay/story_system/StoryWorldStateTest.java

“编译通过”不代表配置内容会发布，也不代表双客户端联机、死亡物品安全或旧世界迁移正确。开服前还需要运行时验证。

## 28. 当前未跟踪的全部 Java 代码

下面 51 个主源码文件在检查时仍是未跟踪状态。它们如果不执行 git add，就不会进入下一次提交，也不会出现在其他电脑拉取的仓库中。

### 28.1 经济桥接与客户端过渡（3）

- src/main/java/com/hhy/dreamingfishcore/client/cache/EconomyTerminalClientCache.java
- src/main/java/com/hhy/dreamingfishcore/client/integration/EconomySystemUiBridge.java
- src/main/java/com/hhy/dreamingfishcore/client/ui/loading/LoadingTransitionController.java

### 28.2 个人引导（8）

- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/GuidanceEntry.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/GuidanceManager.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/GuidanceSeed.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/GuidanceViewData.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/client/cache/GuidanceClientCache.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/command/Command_Guidance.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/network/Packet_GuidanceSnapshotRequest.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/guidance_system/network/Packet_GuidanceSnapshotResponse.java

### 28.3 NPC 短信（15）

- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcConversationViewData.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcMessageConfig.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcMessageDefinition.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcMessageManager.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcMessageRecord.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcMessageReplyDefinition.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcMessageViewData.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcReplyViewData.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/PlayerNpcMessageData.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/client/cache/NpcMessageClientCache.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/event/NpcMessageSyncEvent.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/network/Packet_NpcMessageReadRequest.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/network/Packet_NpcMessageReplyRequest.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/network/Packet_NpcMessageSnapshotRequest.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/network/Packet_NpcMessageSnapshotResponse.java

### 28.4 死亡尸体（11）

- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/DeathCorpseEntities.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/DeathCorpseEntity.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/DeathCorpseInventory.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/DeathCorpseManager.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/client/DeathCorpseRenderer.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/client/DummyCorpsePlayer.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/compat/CorpseAccessoryBridge.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/compat/CorpseAccessoryCompat.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/compat/CorpseAccessoryEntry.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/compat/accessories/AccessoriesCorpseBridge.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/corpse/compat/curios/CuriosCorpseBridge.java

### 28.5 逐光会身份（4）

- src/main/java/com/hhy/dreamingfishcore/gameplay/zhuiguang_system/ZhuiguangMembershipAction.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/zhuiguang_system/ZhuiguangMembershipManager.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/zhuiguang_system/ZhuiguangMembershipRequirement.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/zhuiguang_system/command/Command_Zhuiguang.java

### 28.6 Mixin（4）

- src/main/java/com/hhy/dreamingfishcore/mixin/ui/CommandSuggestionsMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/MinecraftScreenTransitionMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/ProgressScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/ServerScreenUiEconomyBridgeMixin.java

### 28.7 经济服务端与网络（3）

- src/main/java/com/hhy/dreamingfishcore/server/economy_bridge/EconomySystemBridge.java
- src/main/java/com/hhy/dreamingfishcore/server/economy_bridge/network/Packet_EconomyTerminalRequest.java
- src/main/java/com/hhy/dreamingfishcore/server/economy_bridge/network/Packet_EconomyTerminalResponse.java

### 28.8 新手教程确认包（2）

- src/main/java/com/hhy/dreamingfishcore/server/notice_system/network/Packet_NewPlayerGuideCompleted.java
- src/main/java/com/hhy/dreamingfishcore/server/notice_system/network/Packet_NewPlayerGuideViewed.java

### 28.9 终端圆角渲染（1）

- src/main/java/com/hhy/dreamingfishcore/server/server_ui_system/client/serverscreen/ServerScreenUI_RoundedRenderer.java

## 29. 当前未跟踪的测试和说明文档

### 29.1 测试（4）

- src/test/java/com/hhy/dreamingfishcore/client/ui/notification/NotificationTest.java
- src/test/java/com/hhy/dreamingfishcore/gameplay/guidance_system/GuidanceEntryTest.java
- src/test/java/com/hhy/dreamingfishcore/gameplay/npc_message_system/NpcMessageDomainTest.java
- src/test/java/com/hhy/dreamingfishcore/server/playerdata_system/PlayerZhuiguangMembershipTest.java

### 29.2 本文之外的未跟踪文档（6）

- docs/NPC_MESSAGES_AND_GUIDANCE.md
- docs/adr/0033-zhuiguang-membership-is-an-independent-identity-axis.md
- docs/adr/0034-dream-beginning-precedes-afterdream-with-a-new-stable-id.md
- docs/story/OPENING_PRELUDE_PLAN.md
- docs/story/OPENING_RUNTIME_GUIDE.md
- docs/story/OPENING_STORY_CONTENT.md

这些文档分别记录短信/引导配置、逐光会身份决策、阶段 ID 决策以及开场剧情内容。应与本文一起提交，避免只有代码没有设计依据。

### 29.3 其他未跟踪目录

- .agents/：本地代理协作资料，不属于模组运行代码。
- .ai-bridge/：AI 工具桥接资料，不属于模组运行代码。
- .claude/：其他 AI 工具配置，不属于模组运行代码。
- design_previews/：UI 设计预览图。
- dev2/：第二客户端运行目录，当前约 76 个未跟踪项。
- libs/：本地 EconomySystem API JAR。
- server-data/：服务端数据样例或运行数据，当前约 4 个未跟踪项。

提交前不要直接执行 git add .。应先决定这些本地工具目录、运行数据和 API JAR 哪些应被版本控制，哪些应加入 .gitignore。

## 30. 当前已跟踪但尚未提交的 59 个修改文件

这份清单是当前 HEAD 到工作区的差异，不包括上节的未跟踪新文件。

### 30.1 工程与文档（3）

- CONTEXT.md
- build.gradle
- docs/STORY_SYSTEM_CODE_GUIDE.md

### 30.2 核心初始化、命令与网络（7）

- src/main/java/com/hhy/dreamingfishcore/DreamingFishCore.java
- src/main/java/com/hhy/dreamingfishcore/client/ClientSetup.java
- src/main/java/com/hhy/dreamingfishcore/client/cache/ClientCacheManager.java
- src/main/java/com/hhy/dreamingfishcore/commands/DreamingFishCore_CommandManager.java
- src/main/java/com/hhy/dreamingfishcore/init/CommonInit.java
- src/main/java/com/hhy/dreamingfishcore/network/DreamingFishCore_NetworkManager.java
- src/main/java/com/hhy/dreamingfishcore/server/persistence/event/WorldDataLifecycleEvents.java

### 30.3 客户端聊天、组件、加载与通知（7）

- src/main/java/com/hhy/dreamingfishcore/client/ui/chat/ImmersiveChatManager.java
- src/main/java/com/hhy/dreamingfishcore/client/ui/components/UiPanelRenderer.java
- src/main/java/com/hhy/dreamingfishcore/client/ui/loading/LoadingScreenUi.java
- src/main/java/com/hhy/dreamingfishcore/client/ui/notification/Notification.java
- src/main/java/com/hhy/dreamingfishcore/client/ui/notification/NotificationRenderer.java
- src/main/java/com/hhy/dreamingfishcore/client/ui/util/LoadingTips.java
- src/main/java/com/hhy/dreamingfishcore/client/ui/util/UiBackgroundRenderer.java

### 30.4 标点与 NPC（5）

- src/main/java/com/hhy/dreamingfishcore/gameplay/marker_system/client/render/MarkerRenderer.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_system/NpcManager.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_system/NpcRelationData.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_system/NpcRelationManager.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/npc_system/command/Command_Npc.java

### 30.5 玩家体征、死亡和尸体接入（8）

- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/client/ui/hud/CustomHotbarGUI.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/client/ui/hud/CustomStatueGUI.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/PendingDeathData.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/client/ui/screen/Screen_RevivalCharm.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/event/DeathEventHandler.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/network/Packet_KeepInventoryRequest.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/death/network/Packet_NormalRespawnRequest.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/playerattributes_system/event/LoginDeathSync.java

### 30.6 故事（2）

- src/main/java/com/hhy/dreamingfishcore/gameplay/story_system/StoryManager.java
- src/main/java/com/hhy/dreamingfishcore/gameplay/story_system/StoryWorldState.java

### 30.7 Mixin（10）

- src/main/java/com/hhy/dreamingfishcore/mixin/death/DeathScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/death/RespawnMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/ConnectScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/DisconnectedScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/GenericDirtMessageScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/GenericWaitingScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/LevelLoadingScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/LoadingOverlayMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/ReceivingLevelScreenMixin.java
- src/main/java/com/hhy/dreamingfishcore/mixin/ui/TitleScreenMixin.java

### 30.8 登录、公告与教程（4）

- src/main/java/com/hhy/dreamingfishcore/server/login_system/client/Screen_LoginUI.java
- src/main/java/com/hhy/dreamingfishcore/server/notice_system/client/NotificationClientDisplay.java
- src/main/java/com/hhy/dreamingfishcore/server/notice_system/client/Screen_NoticeDetail.java（停用的旧公告详情子屏幕兼容类）
- src/main/java/com/hhy/dreamingfishcore/server/notice_system/event/NewPlayerGuide.java

### 30.9 玩家数据（4）

- src/main/java/com/hhy/dreamingfishcore/server/playerdata_system/PlayerData.java
- src/main/java/com/hhy/dreamingfishcore/server/playerdata_system/event/LoginSync.java
- src/main/java/com/hhy/dreamingfishcore/server/playerdata_system/network/Packet_RequestAllPlayerData.java
- src/main/java/com/hhy/dreamingfishcore/server/playerdata_system/network/Packet_SyncPlayerData.java

### 30.10 服务器终端（4）

- src/main/java/com/hhy/dreamingfishcore/server/server_ui_system/client/ServerInformationDisplay.java
- src/main/java/com/hhy/dreamingfishcore/server/server_ui_system/client/serverscreen/ServerScreenUI_ClientEventHandler.java
- src/main/java/com/hhy/dreamingfishcore/server/server_ui_system/client/serverscreen/ServerScreenUI_RendererUtils.java
- src/main/java/com/hhy/dreamingfishcore/server/server_ui_system/client/serverscreen/ServerScreenUI_Screen.java

### 30.11 资源、元数据与测试（5）

- src/main/resources/assets/dreamingfishcore/lang/en_us.json
- src/main/resources/assets/dreamingfishcore/lang/zh_cn.json
- src/main/resources/dreamingfishcore.mixins.json
- src/main/templates/META-INF/neoforge.mods.toml
- src/test/java/com/hhy/dreamingfishcore/gameplay/story_system/StoryWorldStateTest.java

## 31. 当前最重要的已知风险

按开服影响排序：

### P0：完整剧情配置不会自动发布

run 被 Git 忽略，而完整开场内容主要在 run/config。只构建并上传 JAR，正式服务器只能得到 Java 默认的少量内容。

处理建议：建立受版本控制的正式配置模板，并在开服前做一次“全新空目录部署演练”。

### P0：大量新源码仍未跟踪

短信、引导、逐光会、尸体和经济桥接的大部分核心类都在 51 个未跟踪文件中。若只提交已修改文件，项目会在另一台机器直接缺类或编译失败。

处理建议：按模块审阅后显式 git add 对应目录，不要依赖 IDE 自动选择。

### P0：死亡尸体可能造成物品损失

该系统跨事件优先级、原版 keepInventory、网络请求和可选装备模组，且没有完整自动化测试。

处理建议：先备份测试世界，按第 18.6 节逐项做双账号测试，再允许正式玩家使用。

### P1：第一阶段与 10 条引导消息的阶段 ID 不一致

第一阶段已是 dream_beginning，但 run 配置中 10 条带引导短信仍限制 afterdream。可能造成开场流程断链。

处理建议：按剧情逐条归类后修改，不要全局盲替换。

### P1：dev2 内容明显落后

第二账号看到的 NPC、短信和阶段可能和主开发实例不同，导致联机测试结论失真。

处理建议：把内容配置放到共同来源，每次双开前同步到 run 与 dev2。

### P1：本地 EconomySystem API JAR 未跟踪

CI 和其他电脑可能无法编译。即使编译成功，客户端反射调用也可能随外部模组升级失效。

处理建议：确定依赖来源和版本锁定方式，并增加“外部模组存在/不存在”两套启动测试。

### P1：ServerScreenUI_Screen 过大

约 3,877 行同时负责多个页面的渲染和交互，任何主页布局修改都可能破坏其他页的点击区域、滚动或缓存请求。

处理建议：功能稳定后按页面提取 renderer/controller；开服前不要进行无验证的大规模 UI 重写。

### P2：文案与规则存在硬编码

帮助主题、默认短信、阶段显示名和部分身份反馈可能分别存在 Java、语言文件和 JSON 中。

处理建议：每次改文案先全仓搜索旧文本，再决定唯一数据源。

### P2：LF/CRLF 噪声

Git 已提示大量 LF 将被转换为 CRLF。若用格式化工具重写全项目，会制造难以审阅的大 diff。

处理建议：保持现有 .gitattributes 规则，不要为本次功能统一换行；提交前执行 git diff --check。

## 32. 后续修改入口速查

| 想改什么 | 首先查看 |
| --- | --- |
| 第一、第二阶段名称和顺序 | StoryWorldState.java、StoryManager.java、story 配置 |
| NPC 人物定义 | NpcManager.java、NPC 配置 |
| NPC 好感度 | NpcRelationData.java、NpcRelationManager.java |
| 短信条件与副作用 | NpcMessageDefinition.java、NpcMessageReplyDefinition.java |
| 短信保存/发送 | NpcMessageManager.java、PlayerNpcMessageData.java |
| 短信 UI | ServerScreenUI_Screen.java、NpcMessageClientCache.java |
| 引导创建与完成 | GuidanceManager.java、GuidanceEntry.java |
| 引导 UI | ServerScreenUI_Screen.java、GuidanceClientCache.java |
| 加入/退出逐光会 | ZhuiguangMembershipManager.java、PlayerData.java |
| 个人档案身份显示 | ServerScreenUI_Screen.java |
| 左上角教程提示 | NewPlayerGuide.java、NotificationClientDisplay.java |
| 帮助页六个主题 | ServerScreenUI_Screen.java |
| 玩家肢体/体力/勇气/感染 | gameplay/playerattributes_system |
| 重生选择 | Screen_RevivalCharm.java、死亡网络包 |
| 尸体物品安全 | DeathEventHandler.java、DeathCorpseManager.java |
| Dock 与主页布局 | ServerScreenUI_Screen.java |
| 经济摘要 | EconomySystemBridge.java |
| 打开外部经济 UI | EconomySystemUiBridge.java |
| 网络包注册 | DreamingFishCore_NetworkManager.java |
| 世界加载和保存 | WorldDataLifecycleEvents.java |
| 双客户端运行目录 | build.gradle 中 clientDev2 |

## 33. 安全修改清单

### 33.1 修改剧情阶段

- 保持稳定 ID。
- 修改 Java 默认值。
- 修改受版本控制的正式配置。
- 迁移已有世界状态。
- 检查所有 storyStageId 引用。
- 检查短信、引导、任务地点和世界历史。
- 跑 StoryWorldStateTest。
- 用已有世界和新世界各启动一次。

### 33.2 修改短信

- 确认 message ID 唯一。
- 确认 NPC ID 存在。
- 检查阶段、好感度和逐光会条件。
- 检查 once/follow-up 是否形成死循环。
- 检查回复是否会重复应用好感度或身份动作。
- 若附带 guidance，确认引导 ID 唯一且目标文本完整。
- 用两个玩家验证各自记录不串号。
- 重登验证已读和回复记录仍在。

### 33.3 修改引导

- 不从短信自然语言自动推断。
- 服务端创建和完成。
- 客户端只显示快照。
- 检查故事阶段切换后的过期策略。
- 检查地点不存在时的降级文本。
- 检查短信页与引导页仍然独立。

### 33.4 修改逐光会

- PlayerData 默认非成员。
- 加入/退出只由服务端处理。
- 保存、登录同步和克隆事件都覆盖该字段。
- 非成员个人档案不显示“独立协作者”。
- 短信条件 MEMBER/NON_MEMBER 用双账号验证。
- 离开组织后旧短信和旧引导如何处理要明确。

### 33.5 修改教程

- 未查看时提示一直存在。
- 只打开终端首页不能完成。
- 真正看到帮助页后才发送 viewed。
- 服务端持久化成功后才发送 completed。
- 重登后提示状态正确。
- run 和 dev2 两个测试账号分别重置并验证。

### 33.6 修改死亡尸体

- 先备份测试世界。
- 不改变快照、掉落、生成尸体、清理掉落的安全顺序。
- 任何异常都优先保留物品而不是清空。
- 验证可选模组不存在时不崩溃。
- 验证双重请求不可复制物品。
- 验证掉线重登、跨维度和连续死亡。

### 33.7 修改终端 UI

- 在不同 GUI Scale 测试。
- 检查中文长文本和省略。
- 检查 render 与点击热区一致。
- 检查滚轮只影响当前面板。
- 页面切换后请求正确快照。
- Dock 保留广播和设置。
- Dock 的短信按钮只进入短信。
- 引导保持独立入口。

## 34. Git 对比与复查命令

以下命令均为只读，可随时重新核对本文：

查看基线之后的提交：

    git log --oneline 9d7b42a55d9233199d5f8e83287055c10427944c..HEAD

查看基线到 HEAD 的总量：

    git diff --stat 9d7b42a55d9233199d5f8e83287055c10427944c..HEAD
    git diff --shortstat 9d7b42a55d9233199d5f8e83287055c10427944c..HEAD

查看基线到当前工作区：

    git diff --stat 9d7b42a55d9233199d5f8e83287055c10427944c
    git diff --name-status 9d7b42a55d9233199d5f8e83287055c10427944c

查看当前尚未提交的已跟踪文件：

    git diff --name-status HEAD

查看所有未跟踪文件：

    git ls-files --others --exclude-standard

只看新的 Java 文件：

    git ls-files --others --exclude-standard -- "src/main/java/**/*.java"

检查空白和行尾问题：

    git diff --check

查看某一个提交实际做了什么：

    git show --stat <commit>
    git show --find-renames <commit>

因为中间有一次大规模包重构，比较文件历史时建议使用：

    git log --follow -- <path>
    git diff --find-renames 9d7b42a55d9233199d5f8e83287055c10427944c..HEAD

## 35. 推荐提交顺序

当前改动很多，建议按可回退模块分批提交：

1. 文档与 ADR。
2. 故事阶段、逐光会和 PlayerData。
3. NPC 短信领域、网络与测试。
4. 个人引导领域、网络与测试。
5. 终端主页、短信页、引导页和帮助页。
6. 教程持久提示。
7. 死亡尸体与可选装备兼容。
8. 加载过渡和 Mixin。
9. EconomySystem 桥接。
10. 正式配置模板与部署说明。

每一批都应至少编译一次。死亡尸体、网络协议和正式配置发布应单独提交，方便开服前发现问题时快速回退。

## 36. 开服前十天的最小优先级

为了兑现开服时间，优先保证闭环而不是继续扩功能：

1. 把完整剧情/NPC/短信配置从 run 移到可部署来源。
2. 修正 dream_beginning 与开场短信、引导的阶段条件。
3. 确认未跟踪的 51 个源码和 4 个测试都被正确纳入提交。
4. 双账号验证：教程 → 短信 → 回复 → 好感度 → 引导 → 加入或拒绝逐光会。
5. 验证重登后所有个人状态仍在。
6. 完成尸体物品安全测试。
7. 用一个全新服务端目录完整部署一次，确认不是依赖 run 或 dev2 才能工作。
8. 再处理纯视觉润色和非开服必要系统。

## 37. 文档维护规则

以后每次新增 AI 代码时，至少更新：

- 模块入口文件。
- 配置路径与世界存档路径。
- 新网络包。
- 新 PlayerData 字段。
- 新的未跟踪/已跟踪边界。
- 已知风险和验证结果。

本文记录的是 2026-08-21 的快照。判断代码真实状态时，优先级应为：

当前源码与 Git
→ 自动化测试和运行日志
→ 本文
→ 对话记忆

这样即使更换 AI 或开发者，也能从代码和可复现证据继续维护，而不是依赖某次聊天内容。

## 38. 公告双分类与阶段投递

本章记录本轮“游戏公告 / 服务器通知（MAINTENANCE）”拆分后的实际实现。公告系统仍然是服务端权威：配置、故事阶段、教程完成状态和玩家存档都在服务端判定，客户端只接收当前玩家可读的公告快照并负责绘制。教程状态只参与左上角自动投递，不再阻止玩家打开终端查看公告。

### 38.1 文件清单与职责

公告领域的核心文件如下：

| 文件 | 职责 |
| --- | --- |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/NoticeData.java` | 公告 JSON 数据模型；旧四参数构造仍表示服务器通知（`MAINTENANCE`）。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/NoticeCategory.java` | 公告类别枚举：`GAME`、`MAINTENANCE`。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/NoticeManager.java` | 读取/保存 `config/dreamingfishcore/notices.json`、按 ID/稳定 key 查询、补齐内置开场公告。写入使用 `JsonDataStore` 的原子替换。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/BuiltInNoticeCatalog.java` | 两条开场游戏公告的稳定 key、默认标题/正文、阶段和剧情日期；缺失时幂等补齐。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/NoticeVisibilityPolicy.java` | 与 Minecraft 无关的可见性、投递去重和排序规则，便于单元测试。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/NoticeDeliveryService.java` | 登录、教程完成、公告发布、阶段切换后的左上角投递；投递成功后记录 delivered。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/PlayerNoticeDataManager.java` | 当前世界内按玩家保存 read 和 delivered 两套公告 ID 集合。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/event/NoticeEventHandler.java` | 处理 `PlayerLoggedInEvent`，统一发送教程常驻提示并调用登录补投。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/event/NewPlayerGuide.java` | 新手教程常驻左上角提示、教程完成状态迁移。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/network/Packet_NoticeListRequest.java` | 服务端收到终端请求后，只下发当前玩家当前可读的公告。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/network/Packet_NoticeListResponse.java` | 公告列表网络协议；传输类别、故事阶段和剧情日期并执行数量/字符串长度限制。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/network/Packet_MarkNoticeReadRequest.java` | 标记已读前校验公告存在且当前对该玩家可读。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/network/Packet_NewPlayerGuideViewed.java` | 帮助页真正显示后，由客户端回传教程查看回执。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/network/Packet_NewPlayerGuideCompleted.java` | 服务端保存教程状态后，通知客户端移除常驻教程提示。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/client/NotificationClientDisplay.java` | 客户端左上角堆叠通知和新手提示的显示/移除。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/client/Screen_NoticeDetail.java` | 已停用的旧公告详情子屏幕兼容类；当前公告详情不再通过它打开，而是在 `ServerScreenUI_Screen` 内部渲染。 |
| `src/main/java/com/hhy/dreamingfishcore/server/notice_system/command/Command_Notice.java` | `/notice` 管理命令，包含三种 add 语法、list、delete、reload。 |
| `src/main/java/com/hhy/dreamingfishcore/server/server_ui_system/client/serverscreen/ServerScreenUI_Screen.java` | 当前终端的公告缓存、双 tab 页面、卡片点击和未读角标；实际页面入口是 `renderModulePage` 的 `case 2`，调用 `renderNoticeFeedPage`。 |
| `src/main/java/com/hhy/dreamingfishcore/server/server_ui_system/client/serverscreen/ServerScreenUI_PageRenderer.java` | 较早的通用公告列表/卡片渲染器；维护旧页面时可能仍会被引用，但当前双 tab 广播页的主实现位于 `ServerScreenUI_Screen`。 |
| `src/main/java/com/hhy/dreamingfishcore/network/DreamingFishCore_NetworkManager.java` | 注册公告 payload，并把整体协议版本设为 `0.12.0`。 |
| `src/main/java/com/hhy/dreamingfishcore/server/persistence/event/WorldDataLifecycleEvents.java` | 世界启动时加载公告 read/delivered 状态，自动保存并在停服时清理缓存。 |

### 38.2 `NoticeData` 的 JSON 字段

`config/dreamingfishcore/notices.json` 是一个公告数组。每一项字段如下：

| JSON 字段 | Java 类型 | 含义 |
| --- | --- | --- |
| `noticeId` | `int` | 公告数值 ID；命令新增公告时取当前最大值加一。 |
| `noticeTitle` | `String` | 标题。 |
| `noticeContent` | `String` | 正文。 |
| `publishTime` | `long` | Unix 毫秒时间戳，用于发布时间、排序和投递顺序。 |
| `category` | `NoticeCategory` | `GAME` 或 `MAINTENANCE`。 |
| `storyStageId` | `String` | 游戏公告绑定的故事阶段 ID；服务器通知（`MAINTENANCE`）通常为空。 |
| `storyDate` | `String` | 公告的剧情时间线元数据，例如 `危机第1日`；保留用于运营配置和后台查询，不在玩家公告卡片、详情或左上角提示中展示，也不替代 `publishTime`。 |
| `noticeKey` | `String` | 可选的稳定业务 key；用于内置开场公告幂等补齐，不通过公告列表网络协议发送。 |

字符串字段的 setter 会把 null 归一为空字符串；`category` 为 null 时归一为 `MAINTENANCE`。因此没有新增字段的旧 JSON 可以继续读取。

当前 `run/config/dreamingfishcore/notices.json` 已包含两条开场公告：

- `opening.desert_town`：`沙海灯火 · 临时安置通知`，阶段 `dreamingfishcore:dream_beginning`，剧情日期 `危机第1日`。
- `opening.zhuiguang_invitation`：`逐光会 · 筹建公开邀请`，阶段 `dreamingfishcore:dream_beginning`，剧情日期 `危机第1日`。

### 38.3 GAME / MAINTENANCE 兼容和可见规则

兼容旧配置的规则是：没有 `category` 的旧公告按 `MAINTENANCE` 处理；旧的四参数 `new NoticeData(...)` 构造也会创建服务器通知（`MAINTENANCE`）。这样旧的服务器通知不会因升级而消失。服务器通知没有阶段门槛，当前对所有玩家可见。

游戏公告必须有非空 `storyStageId`，并且该阶段必须存在。它在公告的 `storyStageId` 与 `StoryManager.getSnapshot().currentStageId()` 完全相等时即可在终端中查看；是否完成新手教程不再影响列表、详情或标记已读。服务器通知没有阶段门槛，当前对所有玩家可读。

“可读”“可投递”和“已投递”是三件不同的事：

1. **可读**：终端请求和标记已读只按类别/当前阶段判断；即使教程未完成或教程标记因旧版本/异常被跳过，玩家仍能打开当前阶段的游戏公告。
2. **可投递**：为保持新玩家引导顺序，游戏公告的左上角自动弹窗仍要求教程完成；教程未完成时登录和阶段切换只自动投递服务器通知。
3. **已投递**：只有左上角发送成功后才记录 delivered。客户端打开终端详情只影响 read，不会代替 delivered。

世界进入第二阶段后，第一阶段游戏公告仍然保留在配置和历史 read/delivered 集合中，但不会进入新玩家的当前阶段列表、未读角标或补投；新玩家直接接收当前阶段的公告，不会被旧阶段未读内容干扰。

### 38.4 两份世界存档：read 与 delivered

玩家公告状态位于当前世界的：

- `<世界根目录>/data/dreamingfishcore/notice/player_read_state.json`：终端打开公告后的已读 ID。
- `<世界根目录>/data/dreamingfishcore/notice/player_delivery_state.json`：左上角公告已经投递过的 ID。

两者均按玩家 UUID 映射到整数 ID 集合，由 `PlayerNoticeDataManager` 管理；世界启动时加载，自动保存和停服前保存，服务器停止后清理内存缓存。read 只决定终端中的“已读/未读”和相关角标，delivered 只用于左上角投递去重。

首次升级到双状态时，如果 `player_delivery_state.json` 不存在，系统会把旧的 read 状态做深拷贝迁移到 delivered，避免玩家因为历史上已经读过的旧公告而被一次性重新弹出；迁移结果等待自动保存。没有任何旧记录时不会强行创建空的 delivery 文件。

损坏保护由 `JsonDataStore` 和 `PlayerNoticeDataManager` 共同提供：先尝试主文件，主文件解析失败时尝试 `.bak`；两者都无法读取时禁止本次会话覆盖该文件，并保留可用的另一份状态。空文件也会进入写保护。写入采用临时文件加原子替换，写失败会保留 dirty 标记等待重试，不能为了“修复”而把未知损坏内容直接覆盖成空对象。

公告配置也遵循同一安全原则：不存在时创建默认配置并补齐内置开场公告；已有配置加载后，缺少任一开场稳定 key 会幂等追加；零字节配置拒绝覆盖；主文件和备份都无法读取时保留当前内存状态并禁止写回。未知或损坏配置需要人工备份后处理，不应直接删除世界存档。

### 38.5 两条开场公告与坐标修改位置

两条开场公告由 `BuiltInNoticeCatalog` 定义，稳定 key 是：

- `opening.desert_town`
- `opening.zhuiguang_invitation`

它们的默认正文目前使用 `X: [待定]，Y: [待定]，Z: [待定]`，这是有意保留的运营占位符。若要修改当前运行实例，编辑：

    run/config/dreamingfishcore/notices.json

若要让以后首次生成配置或缺失公告自动补齐时也使用新坐标，同时编辑：

    src/main/java/com/hhy/dreamingfishcore/server/notice_system/BuiltInNoticeCatalog.java

两处都应保持同一组坐标和正文。已有稳定 key 的公告不会被自动覆盖，因此只改 Java 默认常量不会改变已经存在的 `notices.json`；正式运营时必须先改实际服务器 `config/dreamingfishcore/notices.json`。不要修改 `noticeKey`，否则系统会把它当成一条新的开场公告并再次补齐。

### 38.6 教程、登录、发布、阶段切换时序

完整链路可以按下面顺序理解：

1. **世界启动**：`CommonInit` 调用 `NoticeManager.loadFromConfig()` 读取公告配置；`WorldDataLifecycleEvents.onServerStarting` 加载故事世界状态、登录数据和公告 read/delivered 存档。
2. **玩家登录**：`NoticeEventHandler.onPlayerLoggedIn` 调用 `NoticeDeliveryService.deliverPendingOnLogin`，并统一调用 `NewPlayerGuide.sendNewPlayerGuide`。服务端按当前阶段、类别、教程完成状态和 delivered 集合选择待投递公告，按 `publishTime` 升序（ID 为并列时的次序）逐条发送左上角提示；单人模式也走这条教程提示路径，不再因为跳过登录认证而漏发。教程未完成时游戏公告仍可在终端查看，但不会自动弹出。
3. **真正查看教程**：客户端只有在新手帮助页实际渲染后才发送 `Packet_NewPlayerGuideViewed`。服务端的 `NewPlayerGuide.markViewed` 将登录数据中的教程标记设为完成并保存；随后发送 `Packet_NewPlayerGuideCompleted` 让客户端移除常驻提示。只有从未完成变为已完成的这一次，才调用 `deliverPendingGameAfterTutorial`，把当前阶段尚未 delivered 的游戏公告投递给玩家。
4. **发布新公告**：管理员使用 `/notice add ...` 成功写入 `NoticeManager` 后，命令统一调用 `NoticeDeliveryService.publishToEligibleOnlinePlayers`。服务器通知（`MAINTENANCE`）投递给所有在线玩家；游戏公告只投递给教程已完成且处于对应阶段的在线玩家，但未完成教程的玩家仍可立即在终端查看。投递结果写入 delivered，重复发布同一 ID不会重复弹出。
5. **切换故事阶段**：`StoryManager.changeStage` 先验证阶段存在并写入世界状态、激活默认任务和历史记录，然后调用 `TaskDataManager.broadcastFullTaskDataToAllPlayers()`，让在线客户端立即刷新 `currentStage` 与新阶段任务；同步失败不回滚阶段，公告补投仍继续，随后调用 `NoticeDeliveryService.deliverPendingToAllOnlinePlayers`。新阶段游戏公告因此会补投给已完成教程且处于对应阶段的在线玩家；教程未完成者仍能在终端查看新阶段公告，只是不自动弹出。阶段切换后，旧阶段游戏公告不再可见。
6. **重登和终端查看**：重登再次按“当前可投递 + 未 delivered”补投；进入终端由 `Packet_NoticeListRequest` 获取当前可读列表，打开详情后再由 `Packet_MarkNoticeReadRequest` 校验并写入 read。客户端不能通过伪造不属于当前阶段的游戏公告 ID 越过阶段边界，但不需要先完成教程。

`/notice reload` 只重新读取配置并报告当前数量，不代表发布事件，也不会广播或触发左上角投递。需要让玩家收到新提示，应使用新增命令或在代码中经过 `publishToEligibleOnlinePlayers` 的发布入口。

### 38.7 终端双 tab 与客户端协议

终端公告页当前在 `ServerScreenUI_Screen.renderNoticeFeedPage` 中绘制两个 tab：

- `游戏公告`：显示当前故事阶段可读的游戏公告，不要求教程已完成；卡片和详情只显示所属故事阶段，不显示“危机第 X 日”等剧情日期。分类按钮与“梦屿广播”标题同排并靠右对齐，已开放阶段以紧凑、低对比度的导航条在左侧竖向排列；导航条只包住实际按钮，不铺满整块内容区，按钮宽度按实际文案自适应。客户端只展示当前阶段及其之前已经开放的阶段，首次进入默认定位当前阶段，未推进的后续阶段不会提前剧透。
- `服务器通知`：玩家显示名为“服务器通知”；内部类别仍为 `MAINTENANCE`，卡片显示实际发布时间。

默认打开游戏公告 tab；切换 tab 会重置该页滚动位置。点击卡片后由 `ServerScreenUI_Screen` 在当前终端内部切换到详情态，不再创建或打开 `Screen_NoticeDetail`；该类只保留为停用的旧兼容实现。详情态支持返回按钮、ESC 返回列表、点击 tab 返回并切换列表，以及在正文区域使用鼠标滚轮。服务器通知详情可显示发布时间，游戏公告详情不显示剧情日期。首页和 Dock 的未读判断使用服务端已过滤的当前可见列表；广播 Dock 入口仍然保留。修改页面布局时优先改 `ServerScreenUI_Screen` 的当前 `renderNoticeFeedPage`、详情渲染和点击热区，不要只改 `ServerScreenUI_PageRenderer` 中较早的单列表实现。

公告列表网络协议随字段扩展升至 `DreamingFishCore_NetworkManager.PROTOCOL_VERSION = "0.12.0"`。响应新增传输 `category`、`storyStageId`、`storyDate`；`noticeKey` 只留在服务端配置，不下发给客户端。全量任务数据包同时传输每个故事阶段的运行时 `currentStage` 标记，该字段不写入故事定义 JSON。编码/解码限制为：公告最多 1024 条，已读 ID 最多 4096 个，标题/正文最多 32767 个 UTF 字符，类别最多 32 个字符，阶段 ID 和剧情日期各最多 256 个字符；负数计数或超限会拒绝。未知类别在客户端解码时安全回退为服务器通知（`MAINTENANCE`）。服务端列表请求只返回当前玩家可读公告，并只同步这些公告对应的已读 ID；标记已读请求也会再次校验存在性和可读性。

### 38.8 `/notice` 三种新增语法

命令需要权限等级 2。三种新增形式是：

    /notice add "标题" "内容"
    /notice add maintenance "标题" "内容"
    /notice add game "stageId" "storyDate" "标题" "内容"

第一种是兼容旧服主脚本的写法，明确按服务器通知（`MAINTENANCE`）创建；第二种是显式创建服务器通知（`MAINTENANCE`）；第三种创建游戏公告，命令会校验 `stageId` 在 `StoryManager` 中存在且 `storyDate` 非空。标题和正文中的 `&a` 等颜色写法会转换为 Minecraft 的 `§` 颜色符号。`/notice list` 会列出类别，游戏公告额外列出阶段和剧情日期；`/notice delete <ID>` 只删除配置中的公告；`/notice reload` 不广播。

### 38.9 本轮测试文件与验证状态

本轮公告领域新增/改动对应的纯 Java 测试文件为：

- `src/test/java/com/hhy/dreamingfishcore/server/notice_system/NoticeVisibilityPolicyTest.java`：旧字段默认值、服务器通知（`MAINTENANCE`）无门槛、游戏公告的终端阶段可读性、独立的教程投递门槛、投递排序和 delivered 去重。
- `src/test/java/com/hhy/dreamingfishcore/server/notice_system/BuiltInNoticeCatalogTest.java`：两条开场公告的稳定 key、ID 分配、叙事顺序、缺一补一和幂等行为。
- `src/test/java/com/hhy/dreamingfishcore/server/notice_system/PlayerNoticeStatePersistenceTest.java`：delivery 状态从 read 状态迁移、空文件写保护、深拷贝以及损坏文件不覆盖另一份状态。

本轮在联网补齐 `net.neoforged:neoform-runtime:2.0.18` 后，已实际执行：

    .\\gradlew.bat check --no-daemon --max-workers=1

结果为 `BUILD SUCCESSFUL`：`compileJava`、`compileTestJava` 和 `test` 全部通过。进入游戏后仍应重点做人工联调：教程未完成/完成两种账号（两种账号都应能在终端查看当前阶段游戏公告）、单人模式教程常驻提示、第一阶段到第二阶段切换、服务器通知和游戏公告的左上角投递、旧阶段公告过滤、损坏/空的 read 与 delivery 文件，以及实际服务器配置中的两组坐标。
