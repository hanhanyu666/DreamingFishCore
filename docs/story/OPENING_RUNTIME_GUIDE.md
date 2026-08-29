# 《灯还亮着》内容投放说明

## 已落地文件

- run/config/dreamingfishcore/npc_data.json：普通开发实例实际读取的 NPC 配置；保留剧情记录员，并加入 100～104 五个开场发送者。
- run/config/dreamingfishcore/npc_messages.json：45 条 NPC 私信，其中 10 条会生成独立的个人引导，并包含加入、延后加入和退出逐光会的身份入口。
- docs/story/OPENING_PRELUDE_PLAN.md：前置篇轮次、玩家行为、分支与失败补救。
- docs/story/OPENING_STORY_CONTENT.md：广播、临时约章、现场对白和环境文本。

## 测试前

开发环境启动后执行：

    /npc reload
    /npc list
    /npc messages list
    /zhuiguang status

远程通信角色不必生成实体也能由服主发送私信。若需要测试现场对话，可执行：

    /npc spawn 101
    /npc spawn 102

## 建议投放顺序

当前主动剧情消息使用 MANUAL 触发，由服主在对应世界事实发生后发送。命令格式：

    /npc message <玩家> <消息ID>

| 轮次 | 先发消息 | 结算后消息 |
| --- | --- | --- |
| R0 | dreamingfishcore:opening/liaison/network_online | 玩家回复自动产生对应后续消息 |
| R1 | 对愿意建设的玩家补发 dreamingfishcore:opening/liaison/station_request | 无 |
| R2 | dreamingfishcore:opening/liaison/first_rescue_call；dreamingfishcore:opening/baizhi/triage_request | dreamingfishcore:opening/liaison/first_rescue_record；dreamingfishcore:opening/baizhi/after_rescue |
| R3 | dreamingfishcore:opening/liaison/founding_meeting | “加入”回复写入成员身份；独立协作保持非成员；约章副本允许读完后再决定 |
| R4 | dreamingfishcore:opening/liang/first_signal | dreamingfishcore:opening/liang/power_diversion；dreamingfishcore:opening/liang/hold_signal |
| E1 | dreamingfishcore:opening/yuchi/weak_signal | 玩家回复自动获得相应说明 |
| R5 | dreamingfishcore:opening/baizhi/observation_room；dreamingfishcore:opening/jiang/initial_protocol | 根据玩家实际行为记录世界事实 |
| R6 | 使用广播和世界历史文本结算 | 可再次发送 dreamingfishcore:opening/liang/hold_signal 给本轮新参与者 |

熟悉与信任消息只在玩家达到对应好感度、且发生了匹配的真实行为后投放：

- 白芷：familiar_note、trusted_note；
- 江晚：familiar_uncertainty、trusted_personal；
- 梁朔：familiar_record、trusted_fear；
- 尉迟南：familiar_anomaly、trusted_reason；
- 梦屿应急联络员：familiar_concern。

这些消息不是主线必需情报，不需要为了投放而人工刷高好感度。

## 成员身份操作

- 玩家默认是“独立协作者”，逐光会身份与幸存者/感染者、NPC 好感度、Rank 和称号彼此独立。
- 成立消息、`dreamingfishcore:opening/liaison/join_later` 的加入回复会把身份改为逐光会成员。
- `dreamingfishcore:opening/liaison/leave_request` 只可发给成员；确认退出后恢复为独立协作者。
- 服主可用 `/zhuiguang status <玩家>` 查询，用 `/zhuiguang membership <玩家> member|independent` 纠正或恢复身份。
- 独立协作者仍可收到公共求援、参与主线、建设和监督组织；身份条件只用于内部短信和后续组织职责。

## 正式服上线前要替换的内容

1. 为联络站、接应点、中继、观察区和公共天线确定实际位置。
2. 在对应 guidance 中填写维度与坐标，并把 hasLocation 改为 true；如果地点由玩家自由选择，保持 false，待剧情场所登记后由服务端生成定位。
3. 确认 NPC 数字 ID 没有与正式内容冲突。100～104 目前是本内容包的临时保留段。
4. 若生产服已有 npc_data.json，合并 100～104 条目，不要直接覆盖其他 NPC。
5. 将两个配置文件放入生产实例的 config/dreamingfishcore 目录，再执行 /npc reload。
6. 广播、世界历史和环境文本目前是文案资产，尚未自动绑定广播系统或世界事件，需要服主按轮次发布。

## 内容投放边界

- 普通消息回复记录个人关系；带 membershipAction 的回复还会由服务端写入逐光会成员身份。两者都不代替建筑、救援、档案保存或患者处理等世界事实。
- guidance 只负责记住 NPC 已经告诉玩家的行动信息，不允许客户端手动声明完成。
- 已经结算的唯一事件不为晚加入玩家重置；新人收到公共摘要，并从现有建筑、档案和下一轮行动进入故事。
- 大阶段仍由服主发布，前置篇完成不会自动从余梦期切换到管制期。
