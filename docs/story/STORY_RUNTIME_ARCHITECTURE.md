# 当前剧情运行架构

前情总结、固定人数门槛、世界任务运营和并行引导的操作说明见 [剧情运营指南](STORY_OPERATIONS_GUIDE.md)。

这份文档只描述当前开服切片的真实实现。设计稿中的随机本、线索、投票和后续阶段
不属于当前代码，也不能据此推断运行时行为。

## 一句话模型

`StoryManager` 是唯一的剧情运行时入口和唯一的剧情事实存档所有者。每个阶段有一个
独立 Java 文件：

```text
StoryManager
  ├─ OpeningStory.java       // 梦的开始
  └─ AfterdreamStory.java    // 余梦期
```

阶段文件只写顺序、条件、奖励和事件处理；它们不订阅游戏事件、不读写自己的进度文件。
所有外部系统（公告、私信、NPC、地点、物品、感染）先调用 `StoryManager`，再由当前
阶段文件处理。这样同一件事只有一条入口。

## 三类数据

### Java 流程

- `OpeningStory`：第一阶段玩家是否加入逐光会。
- `AfterdreamStory`：第二阶段公告、白芷私信、江晚复核、药剂、面具和相关引导。
- `PlayerInfectionManager`：实际领取首件面具后的感染规则、一级治疗窗口和二级转化。

### 文案 JSON

`config/dreamingfishcore/story_text.json`（首次启动由
`src/main/resources/dreamingfishcore/defaults/story_text.json` 生成）只放标题、正文、
对白和提示。它没有节点、条件、奖励或游标。修改句子不会改变流程。

NPC 身份/普通闲聊仍在 `npc_data.json`，私信正文和预设回复仍在 `npc_messages.json`；
阶段 Java 文件决定什么时候调用它们。

### 唯一剧情事实文件

```text
<世界>/data/dreamingfishcore/story/story_state.json
```

`StoryWorldState` 在这里保存：

- 当前全服阶段和活动 tick；
- 第一阶段玩家状态；
- 第二阶段玩家状态；
- 第二阶段全服面具倒计时、丧尸记忆事件倒计时和一次性公告事实；
- 需要时使用的故事任务/世界旗标历史。
- `operations`：已发布的前情正文、个人收件与已读、接入位置、跳过任务和历史归档。
- `hospital`：医院小章是否已发布、绑定的医院地点。阅读、建设结果和正式复查复用真实任务记录。

公告已读、私信历史、引导和普通任务仍由各自系统保存，它们是显示投影，不是剧情游标。

公告的 `noticeTitle` 是玩家识别公告的正式标题。客户端收到公告快照后，右下角提醒会
逐条显示所有未读公告标题（长标题自动换行）；公告正文仍在终端详情页查看。

旧的 `world_state.json`、`opening_player_progress.json`、`afterdream_player_progress.json`
和 `flow_player_progress.json` 不会被当前运行时代码读取或迁移。统一状态 schema 3/4
可自动升级到 5，原有事实不变，补充运营和医院记录；不要重新建立或清空现有故事存档。

## 第一阶段：`OpeningStory.java`

```text
NOT_STARTED
  → TRAVEL_TO_ABYDOS
  → TALK_TO_BAIZHI
  → CONTACT_ZHOUCEN
  → CHOOSE_MEMBERSHIP
       ├─ BUILD_ZHUIGUANG_BASE
       └─ DECLINED_ZHUIGUANG
```

公告已读、进入阿拜多斯、白芷交互、周岑回复分别由 `StoryManager` 的公开方法进入。
加入分支写入逐光会身份并发放一次补给；独立分支只记录选择。离开第一阶段后，第一阶段
入口全部停止，建设任务在进入第二阶段时归档，保留实际结果，不再补写个人完成。

## 第二阶段：`AfterdreamStory.java`

```text
NOT_STARTED
  → MESSAGE_RECEIVED → MESSAGE_READ
  → RECEPTION_READY
  → INTRODUCTION
  → RESULT_LEVEL_ONE / RESULT_NONINFECTED / RESULT_LEVEL_TWO
  → AWAITING_TREATMENT
  → COMPLETED                 // 0/1 级实际服药且感染值归零
  → MASK_RECEIVED             // 两天后重新打开江晚并成功领面具
```

实际链路：

1. 服主切换阶段，Java 创建一次公开救治公告；已认证玩家收到白芷私信。
2. 玩家读信后获得前往医疗接待点的引导，进入地点后才可与江晚交互。
3. 第一次江晚交互显示共同开场，第二次交互完成终端复核并尝试发放一瓶药剂。
   0、1、2 级都发药；2 级不能用当前药剂治疗。
4. 0/1 级只有实际使用药剂并清零感染值后才完成医疗任务。二级感染者的真实首次接待完成后结束复核任务与不适用的服药引导，治疗事实仍保持未完成。
5. 第一名完成首次接待的玩家启动全服 `48,000` 个在线活动 tick 倒计时。
6. 到期发布面具公告；首次接待所在的同一对话会话绝不会直接发面具。玩家关闭并
   再次打开江晚后，背包成功收到面具才记录领取事实。
7. 第二阶段首次加载时启动另一个 `48,000` tick 倒计时，但使用主世界 `gameTime`，
   所以服务器空服或重启不会暂停。到期后阶段脚本只把当前阶段的丧尸 `digging`
   覆盖项打开，再发布“丧尸记忆”公告；听力、开门、破门、广播和保护区规则保持原配置。

NPC 界面刷新不是新会话。`NpcManager` 只有明确的“打开 NPC”入口才调用
`StoryManager.onNpcDialogueOpened`，所以分页/状态刷新不会绕过“再次交互领取面具”的
边界。

## 余梦期医院小章

`HospitalStory` 处理模板说明阅读、固定人数解锁建设、人工验收、医院正式复查。医院小章在余梦期服务就绪或进入余梦期时自动发布，旧存档的未发布状态同样自动接入；第一阶段不会提前开放。所有玩家入口使用当前余梦期和认证状态校验；验收先保存真实世界结果再发布开诊公告。启用后基础医疗引导更新到配置的医院地点，检查不会伪造治愈。

`DailyTemplateSupportService` 从 NPC 106 的独立动作收取物资；普通对话不扣物。每日额度按主世界游戏日期计算，付款收据随背包保存后才更新属性中的余量和领取记录，重试通过收据去重。它是发布医院小章后持续开放的服务，不依赖玩家组织身份或个人剧情完成。

详见 [医院发布与验收指南](HOSPITAL_ROLLOUT.md)。

## 面具后的感染规则

`PlayerInfectionManager.onProtectiveMaskGranted` 是唯一的全局切换入口，而且只在首件
面具实际进入玩家背包后调用。它写入世界旗标
`dreamingfishcore:afterdream/protective_mask_distributed`，然后同步所有在线玩家。

旗标前后规则：

| 项目 | 旗标前 | 旗标后 |
| --- | --- | --- |
| 新感染上限 | 100 | 200 |
| 白天自然回落 | 有 | 停止 |
| 面具传播防护 | 无 | 只阻断一级感染者传播 |
| 新达到阈值 | 一级感染者 | 一级感染者，并开始 24,000 tick 治疗窗口 |

治疗窗口只属于新产生的那一名一级感染者；到期后该玩家单独变为二级并触发一次病毒
进化公告。已有一级感染者不会被批量改写。所有感染值增加都经过
`PlayerInfectionManager.addInfection`，事件层不再复制阈值逻辑。

## 外部入口表

| 外部事实 | 唯一入口 |
| --- | --- |
| 登录/重连 | `StoryManager.onPlayerAuthenticated` |
| 离线清理 | `StoryManager.onPlayerDisconnected` |
| 公告已读 | `StoryManager.onNoticeRead` |
| NPC 私信已读 | `StoryManager.onNpcMessageRead` |
| NPC 预设回复 | `StoryManager.onNpcReply` |
| NPC 新会话 | `StoryManager.onNpcDialogueOpened` |
| NPC 面对面交互 | `StoryManager.onNpcInteraction` |
| 地点进入/观察 | `StoryManager.onLocationObserved` |
| 药剂实际使用 | `StoryManager.onGeneRevivalPotionUsed` |
| 病毒进化 | `StoryManager.onVirusEvolution` |
| 世界时间推进 | `StoryManager.tickActiveTime` → `AfterdreamStory.tickWorldTime` |
| 江晚动态对白 | `StoryManager.getDialogueOverride` |

NPC、公告、任务和 HUD 只读取这些入口产生的状态/投影；不要在它们内部再写一个阶段
游标或自动触发器。

## 修改规则

- 改一句文案：只改 `story_text.json`。
- 改第一阶段顺序/奖励：只改 `OpeningStory.java` 及其状态枚举。
- 改第二阶段顺序/奖励：只改 `AfterdreamStory.java` 及其状态枚举。
- 改感染阈值、传播或治疗窗口：只改 `PlayerInfectionManager`。
- 改丧尸记忆公告的延迟或触发效果：只改 `AfterdreamWorldProgress` 和
  `AfterdreamStory.tickWorldTime`，不要在丧尸 AI 中另加剧情计时器。
- 新增阶段：新增一个阶段 Java 文件，再在 `StoryManager.createDefaultDefinitions()` 注册。

不要恢复 `story_flows.json` 或通用 Flow 引擎；不要让客户端任务完成包、NPC 配置或公告
配置直接推进剧情。流程变更完成后只需本地编译确认，当前开发阶段不要求运行无关测试。
