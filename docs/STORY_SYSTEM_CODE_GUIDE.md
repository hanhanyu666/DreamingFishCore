# 剧情系统代码阅读指南

手动总结、人数门槛及并行引导的使用方式见 [剧情运营指南](story/STORY_OPERATIONS_GUIDE.md)。

医院小章位于 `gameplay/hospital_system/`，发布、地点绑定、验收和每日维护规则见 [医院发布指南](story/HOSPITAL_ROLLOUT.md)。医院世界记录纳入 `StoryWorldState`；每日额度随玩家属性保存，待入账付款随玩家背包保存。

当前剧情已经不再使用通用 Flow/节点图。阅读和修改时只需要沿着一条链看：

```text
游戏事件 / 网络包
        ↓
StoryManager                 // 唯一入口、唯一故事存档
        ↓
OpeningStory 或 AfterdreamStory // 当前阶段的 Java 顺序
        ↓
公告 / 私信 / NPC / 引导 / 任务投影
```

## 文件职责

| 文件 | 作用 |
| --- | --- |
| `gameplay/story_system/StoryManager.java` | 加载阶段、分发事件、保存唯一故事状态、生成客户端视图 |
| `gameplay/story_system/StoryWorldState.java` | 纯故事事实对象和状态校验 |
| `gameplay/story_system/StoryOperationsState.java` | 发布正文、个人接入和归档事实，独立于真实完成 |
| `gameplay/story_system/StoryOperationsCatalog.java` | 手写总结草稿和有固定人数门槛的世界任务配置 |
| `gameplay/story_system/StoryCheckpoint.java` | 当前 Java 阶段允许的安全接入点 |
| `gameplay/story_system/StoryPlayerGate.java` | 统计同时做完全部前置任务的不同玩家 |
| `gameplay/opening_story_system/OpeningStory.java` | 第一阶段完整流程 |
| `gameplay/opening_story_system/OpeningStoryStep.java` | 第一阶段状态枚举 |
| `gameplay/afterdream_story_system/AfterdreamStory.java` | 第二阶段完整流程 |
| `gameplay/afterdream_story_system/AfterdreamMedicalStep.java` | 第二阶段状态枚举 |
| `gameplay/afterdream_story_system/AfterdreamWorldProgress.java` | 第二阶段全服倒计时和公告事实 |
| `gameplay/story_system/runtime/StoryTextCatalog.java` | 读取文案 JSON，不执行流程 |
| `gameplay/story_system/runtime/StoryRuntimeEventHandler.java` | 把游戏事件转发给 `StoryManager`，不保存进度 |

阶段 Java 文件不得自行订阅事件或创建进度管理器；新增入口先加到 `StoryManager`，再
调用当前阶段文件。

## 唯一存档

```text
<世界>/data/dreamingfishcore/story/story_state.json
```

`StoryWorldState` 的 `openingPlayerProgress`、`afterdreamPlayerProgress` 和
`afterdreamWorldProgress` 是阶段事实的唯一来源。公告已读、私信历史、引导和普通任务
各有自己的存档，但它们只是投影/交互记录，不能反过来当作剧情游标。

当前代码不会读取或迁移以下旧文件：

```text
world_state.json
opening_player_progress.json
afterdream_player_progress.json
flow_player_progress.json
```

旧服务器切换时由运营者停服后手工写新的 `story_state.json`；不要在运行时添加猜测式
迁移器。

## 第一阶段入口

`OpeningStory` 的状态只有：

```text
NOT_STARTED → TRAVEL_TO_ABYDOS → TALK_TO_BAIZHI → CONTACT_ZHOUCEN
             → CHOOSE_MEMBERSHIP → BUILD_ZHUIGUANG_BASE / DECLINED_ZHUIGUANG
```

对应入口：

- `StoryManager.onNoticeRead`：读阿拜多斯公告；
- `StoryManager.onLocationObserved`：进入阿拜多斯地点；
- `StoryManager.onNpcInteraction`：与白芷交谈；
- `StoryManager.onNpcReply`：周岑回复并作出加入/独立选择。

加入时先确认会员身份，再发一次补给；所有副作用都有事实字段保护。

## 第二阶段入口

`AfterdreamStory` 的顺序：

```text
NOT_STARTED → MESSAGE_RECEIVED → MESSAGE_READ → RECEPTION_READY
             → INTRODUCTION → RESULT_* → AWAITING_TREATMENT
             → COMPLETED / MASK_RECEIVED
```

关键规则写在 `AfterdreamStory.java` 的方法中：

- `onStageActivated`：创建公开救治公告；
- `onPlayerAuthenticated`：幂等发送白芷私信并重建投影；
- `onNpcMessageRead`：读信后创建医疗地点引导；
- `onLocationEntered`：进入地点后开放江晚；
- `onNpcInteraction`：共同开场、复核、发药和再次领面具；
- `onGeneRevivalPotionUsed`：实际服药且感染清零后完成医疗；
- `tickActiveTime`：推进 48,000 个在线活动 tick 倒计时；
- `onNpcDialogueOpened`：标记一次新的江晚会话，防止首次接待同场发面具。

一次江晚接待即使发生在倒计时到期之后，也只发药；必须关闭界面并重新打开江晚，
且面具真正进入背包，才调用 `PlayerInfectionManager.onProtectiveMaskGranted`。

## 感染规则入口

所有感染增加都调用 `PlayerInfectionManager.addInfection`。这里集中处理当前上限、
阈值、面具阶段和新一级感染者的 24,000 tick 治疗窗口。实际首件面具发放只通过
`onProtectiveMaskGranted` 开启全局旗标；感染事件层不再复制这些判断。

## 文案修改

编辑：

```text
config/dreamingfishcore/story_text.json
```

这里只能修改 `texts` 和 `dialogues` 的值。不要改 key，也不要把条件、奖励或状态名
写进 JSON。NPC 私信正文/回复在 `npc_messages.json`，NPC 身份和普通对白在
`npc_data.json`；什么时候发送仍由阶段 Java 文件决定。

## 常见修改

- 改顺序：修改对应阶段的状态枚举和 `on...` 方法。
- 改奖励：修改同一事件方法，并在副作用成功后再推进状态。
- 改一句话：只改 `story_text.json`。
- 新增阶段：新增一个 `*Story.java` 和状态枚举，然后在 `StoryManager` 注册。
- 改 HUD/故事页：只修改客户端投影，不在客户端新增剧情判断。

不要恢复 `story_flows.json`、`StoryFlow*` 或第二套玩家进度文件。流程代码改完后，当前
开发阶段只做一次必要的本地 `compileJava`；不需要为无关系统运行测试或操作远程服务器。
