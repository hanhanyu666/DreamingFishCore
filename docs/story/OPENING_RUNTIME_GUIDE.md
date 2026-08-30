# “梦的开始”上线投放说明

本说明对应阿拜多斯开场的 `story_flows.json` schema 2 实现。开场已经完全迁移到
`StoryFlowEngine`；公告、地点、NPC 实体交互、NPC 预设回复和认证模块只提交事实事件，
由流程节点统一推进玩家游标和效果。这里不发布余梦期及后续阶段内容。

## 当前保留内容

- NPC 运行时只保留 `101` 白芷和 `105` 周岑。
- 公告只保留 `opening.desert_town`（“阿拜多斯 · 临时安置通知”）。
- 白芷、周岑的阿拜多斯文案，以及玩家与他们的私信历史继续保留。
- 随记本仍是独立收藏系统，本轮没有接入故事流程。

## 开场流程

1. 玩家完成登录认证后，流程从自己的 `read_abydos_notice` 游标开始；全服阶段不会因玩家行为自动变化。
2. 玩家真正读完阿拜多斯公告后，`NOTICE_READ` 节点创建“前往阿拜多斯”引导。
3. 玩家进入稳定地点 ID
   `dreamingfishcore:location_d105866ccdc84c4da7b017a7f13ec7d3` 后，`LOCATION_ENTERED` 节点发送白芷到达消息，
   完成前往引导、记录“抵达阿拜多斯”个人任务，并创建“去学校找白芷”引导。
4. 玩家与白芷实体交谈后，`NPC_INTERACTION` 节点发送周岑联络消息，完成会面任务并创建联络引导。
5. 玩家回复周岑的联络消息，再阅读介绍消息；选择加入或保持独立是每名玩家自己的组织身份分支。
6. 加入分支记录选择、发放一次性补给并创建基地建设引导；独立分支只记录选择，不承担成员建设任务。

每个节点都按玩家流程游标匹配。一次性效果会写入效果日志，重连、重复点击、重复进入地点和服务器重启
都不会重复发放物品或重复发送同一条开场消息。修复型节点可以声明 `once: false`，用于登录时补齐尚未
落下的建设引导。

## 阶段规则

阶段顺序固定为：梦的开始 → 余梦期 → 管制期 → 疑光期 → 破晓期。阶段只能由服主手动发布，故事流程节点
不会自动切换阶段。后四阶段本轮只有空壳定义。

## 运行文件

- `config/dreamingfishcore/story_stage_data.json`：阶段和任务定义。
- `config/dreamingfishcore/story_flows.json`：事实事件、节点条件、效果和流程台词。
- `config/dreamingfishcore/npc_data.json`：白芷、周岑档案和面对面对话。
- `config/dreamingfishcore/npc_messages.json`：白芷/周岑私信、预设回复和后续消息。
- `config/dreamingfishcore/notices.json`：阿拜多斯公告。
- 世界数据 `data/dreamingfishcore/story/flow_player_progress.json`：每名玩家的流程游标、完成节点、一次性效果日志和旗标。

流程进度与全服阶段状态分开保存；玩家加入逐光会不会改变全服阶段。

## 服主检查与热重载

```text
/npc reload
/npc list
/npc messages list
/dreamingfish story status
/dreamingfish story content validate
/dreamingfish story content reload <contentId>
```

阶段仍由服主手动发布，例如：

```text
/dreamingfish story stage set dreamingfishcore:dream_beginning
/dreamingfish story stage set dreamingfishcore:afterdream
```

流程配置必须使用 schema 2。校验失败时会使用只读内置阿拜多斯流程，拒绝覆盖损坏的配置和玩家流程存档；
本版本不读取旧的开场专用进度文件，也不把地点显示名称当作事实或自动映射。

## 扩展节点

以后新增公告、线索、随机本或更多 NPC 内容时，在 `story_flows.json` 增加节点和效果即可。模块代码只需
在边界处发出新的 `StoryEvent`，不再把一条剧情拆散写进公告、地点、NPC 和登录模块的互相调用中。
