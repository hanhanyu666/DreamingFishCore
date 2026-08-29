# NPC 私信与个人引导

这两个系统在终端中是独立入口、独立页面和独立存档：

- **NPC 私信**保存玩家与 NPC 实际收发的会话，并提供服务端配置的预设回复。
- **个人引导**保存玩家实际收到某段剧情内容后产生的行动提示；客户端不能手动勾选完成。

一条 NPC 消息可以显式携带 `guidance`，玩家收到该消息时才会生成对应引导。没有配置 `guidance` 的消息不会被自动提炼成任务。

## 配置文件

第一次启动后会生成：

`config/dreamingfishcore/npc_messages.json`

消息定义示例：

```json
{
  "schemaVersion": 1,
  "messages": [
    {
      "id": "dreamingfishcore:mengyu/build_request",
      "npcId": 1,
      "subject": "逐光会筹建",
      "content": "如果你愿意帮忙，请先去旧车站确认那里的情况。",
      "trigger": "MANUAL",
      "once": true,
      "priority": 0,
      "minimumFavorability": 100,
      "maximumFavorability": 1000,
      "replies": [
        {
          "id": "accept",
          "text": "我会去看看。",
          "minimumFavorability": -1000,
          "maximumFavorability": 1000,
          "favorabilityDelta": 2,
          "followUpMessageId": ""
        }
      ],
      "guidance": {
        "id": "dreamingfishcore:guidance/check_old_station",
        "title": "确认旧车站的情况",
        "content": "前往旧车站，确认那里是否适合逐光会使用。",
        "storyStageId": "dreamingfishcore:afterdream",
        "locationLabel": "旧车站",
        "dimension": "minecraft:overworld",
        "hasLocation": true,
        "x": 120,
        "y": 64,
        "z": -45
      }
    }
  ]
}
```

`trigger` 支持：

- `INTERACTION`：玩家打开该 NPC 的现场对话时，系统按优先级最多投递一条当前可用且尚未收到的一次性消息。
- `MANUAL`：由服主命令或后续剧情代码主动发送。
- `FOLLOW_UP`：供预设回复的 `followUpMessageId` 引用。

消息和每项预设回复都有自己的好感度上下限。当前关系区间为：敌对 `< -99`、陌生 `-99～99`、熟悉 `100～299`、朋友 `300～599`、信任 `600～849`、亲近 `>= 850`。

修改配置后执行：

```text
/npc messages reload
```

## 运营命令

```text
/npc messages list
/npc message <玩家> <消息ID>
/guidance list <玩家>
/guidance resolve <玩家> <引导记录ID或定义ID>
```

`/guidance resolve` 是服主调试与运营入口。正式玩法应由对应地点、建造、交付或剧情事件的服务端验证器调用同一个完成接口，而不是让客户端按钮声明完成。

## 世界存档

- NPC 私信：`<世界>/data/dreamingfishcore/communication/npc_messages.json`
- 个人引导：`<世界>/data/dreamingfishcore/guidance/player_guidance.json`

两个文件均使用原子写入和备份恢复；配置或存档损坏时，本次会话不会覆盖无法安全读取的文件。
