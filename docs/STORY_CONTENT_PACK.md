# 故事内容包（流程运行时）

故事模块现在只有一条运行链：外部模块提交已经在服务端验证过的 `StoryEvent`，
`StoryFlowEngine` 按当前手动阶段、玩家流程游标和节点条件选择一个节点，再按顺序执行
JSON 中的效果。阿拜多斯开场也完全走这条链，不再有单独的开场推进器。

## 内容文件

- `story_stage_data.json`：五个手动阶段和任务定义。
- `story_flows.json`：事件节点、流程状态、效果参数和 NPC 台词。
- `npc_data.json`：NPC 档案；本上线批次只保留 101 白芷、105 周岑。
- `npc_messages.json`：NPC 终端私信及预设回复。
- `notices.json`：公告配置；当前本地投放文件包含阿拜多斯安置公告。

玩家流程状态保存在世界数据 `data/dreamingfishcore/story/flow_player_progress.json`，
每条流程按玩家保存 `cursor`、一次性效果记录和流程旗标。它与全服故事阶段状态分开，
因此玩家选择加入逐光会不会改变全服阶段。

## 节点格式（schemaVersion 2）

```json
{
  "id": "enter_abydos",
  "event": "LOCATION_ENTERED",
  "locationId": "dreamingfishcore:location_d105866ccdc84c4da7b017a7f13ec7d3",
  "scope": "PLAYER",
  "conditions": {"cursor": "enter_abydos"},
  "effects": [
    {"id": "arrival_message", "type": "SEND_NPC_MESSAGE", "parameters": {
      "messageId": "dreamingfishcore:opening/baizhi/abydos_arrival"
    }},
    {"id": "resolve_travel", "type": "RESOLVE_GUIDANCE", "parameters": {
      "id": "dreamingfishcore:guidance/opening/travel_to_abydos"
    }}
  ],
  "nextNodeId": "meet_baizhi"
}
```

`subjectId` 用于公告 key、NPC ID 或消息定义 ID，`secondaryId` 用于预设回复 ID，
`locationId` 必须是稳定地点 ID。`initialNodeId` 是新玩家在该流程中的第一个游标。
每个效果都要有节点内唯一的稳定 `id`；一次性效果日志使用这个 ID，不依赖数组顺序。

可用条件包括：

- `cursor`：玩家当前等待的节点；
- `membership`：`ANY`、`MEMBER` 或 `NON_MEMBER`；
- `playerFlag`：该流程玩家旗标；
- `worldFlag`：全服故事旗标。

效果由服务端注册，当前通用效果有 `SEND_NPC_MESSAGE`、`CREATE_GUIDANCE`、
`RESOLVE_GUIDANCE`、`RECORD_PERSONAL_TASK`、`GIVE_ITEMS`、`NOTIFY_PLAYER`、
`SYNC_PLAYER`、`SET_PLAYER_FLAG` 和 `SET_WORLD_FLAG`。效果默认只执行一次；修复类
效果可以写 `"once": false`。未注册效果会让当前节点停留在原游标并记录错误，不会跳过节点。

`dialogueNpcId` 和 `dialogueLines` 可以直接放在节点上。只要节点的条件与玩家当前游标
匹配，打开对应 NPC 就会看到这些台词；台词不再写在 Java 状态分支里。

## 当前阿拜多斯流程

`阅读公告 → 进入阿拜多斯 → 与白芷交谈 → 回复周岑联络消息 → 阅读逐光会介绍 →
加入或保持独立`。每个事实只消费一个节点，重复打开公告、重复进入地点、重复点击或
重复回复都不会重复推进；加入分支只修改该玩家的组织身份。

## 阶段规则

阶段顺序固定为“梦的开始 → 余梦期 → 管制期 → 疑光期 → 破晓期”，只能由服主命令手动切换。
流程节点不能自动切换阶段。后四阶段本轮仍是空壳，随记本也没有接入流程运行时。

## 热重载

```text
/dreamingfish story content validate
/dreamingfish story content reload <contentId>
```

校验会同时检查阶段引用、节点跳转和已有玩家游标；失败时旧流程继续运行。部署到已有
服务器时，必须把 `story_flows.json` 更新到 schema 2。新版本不读取旧的开场专用状态、
不自动迁移旧地点名称；如需留档，请由服主在停服维护前自行备份旧文件。
