# “梦的开始”运行指南

第一阶段的唯一流程代码是
`src/main/java/com/hhy/dreamingfishcore/gameplay/opening_story_system/OpeningStory.java`。
`StoryManager` 负责接收外部事件和保存状态；阶段文件不读写自己的进度文件。

## 状态链

```text
NOT_STARTED
  → TRAVEL_TO_ABYDOS
  → TALK_TO_BAIZHI
  → CONTACT_ZHOUCEN
  → CHOOSE_MEMBERSHIP
       ├─ BUILD_ZHUIGUANG_BASE
       └─ DECLINED_ZHUIGUANG
```

状态事实保存在唯一文件：

```text
<世界>/data/dreamingfishcore/story/story_state.json
```

其中 `openingPlayerProgress` 保存每名玩家的第一阶段状态和一次性补给标记。

## 玩家流程

1. 读 `opening.desert_town` 公告。
2. 进入阿拜多斯稳定地点
   `dreamingfishcore:location_d105866ccdc84c4da7b017a7f13ec7d3`。
3. 在地点内与白芷交谈，收到周岑联络消息。
4. 查看并回复周岑联络消息，阅读周岑介绍。
5. 选择加入逐光会或保持独立。

加入选择会先写入会员身份，再发放一次 starter supply；独立选择不发成员补给。所有
入口都在服务端检查阶段、玩家身份和地点，客户端不能提交“已完成”来跳过步骤。

## 代码入口

| 事实 | `StoryManager` 入口 | 阶段处理 |
| --- | --- | --- |
| 公告已读 | `onNoticeRead` | `OpeningStory.onNoticeRead` |
| 地点进入 | `onLocationObserved` | `OpeningStory.onLocationEntered` |
| 白芷/周岑交互 | `onNpcInteraction` | `OpeningStory.onNpcInteraction` |
| 周岑预设回复 | `onNpcReply` | `OpeningStory.onNpcReply` |
| 登录重连 | `onPlayerAuthenticated` | `OpeningStory.onPlayerAuthenticated` |

NPC 私信和公告系统只负责实际投递/记录；它们不能自行推进状态。引导和故事任务是状态
的显示投影，登录时可以重建投影，但不会凭空前进或重复发奖。

## 文案位置

对白、引导标题/正文和通知在：

```text
config/dreamingfishcore/story_text.json
```

私信正文和回复在 `config/dreamingfishcore/npc_messages.json`，NPC 身份和普通闲聊在
`npc_data.json`。这些配置只能改文字或关系条件，不能改变 Java 顺序和奖励。

## 第一阶段结束

第一阶段不会因某名玩家的选择自动切换全服阶段。服主切换到
`dreamingfishcore:afterdream` 后，建设基地任务统一作为历史收束，旧阶段入口不再响应。

## 当前不实现

基地贡献、随机线索、调查板、社区投票和自动阶段切换都不属于当前简易版。不要恢复旧
`story_flows.json` 或增加旧游标迁移代码。
