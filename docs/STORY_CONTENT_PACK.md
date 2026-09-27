# 当前开服剧情内容包

新增 `story_operations.json` 管理手写总结草稿和固定人数门槛的世界任务；发布和切章仍由服主操作，详见 [剧情运营指南](story/STORY_OPERATIONS_GUIDE.md)。

医院小章随余梦期自动开放：模板说明 → 共同建设 → 人工验收 → 正式复查，并提供每日模板维护。已处于余梦期的存档更新重启后自动接入。配置、命令与验收步骤见 [医院发布指南](story/HOSPITAL_ROLLOUT.md)。

本文只记录当前简易版。`PROJECT-M.D.G.A` 和其他设计稿仅供未来讨论，不是当前流程的
实现依据。

## 当前边界

- 第一阶段：玩家完成“加入逐光会 / 保持独立”的个人选择。
- 第二阶段：按《余梦期-逐光会公开救治.md》执行公开救治、江晚复核、药剂、面具。
- 阶段由服主手动切换；第一阶段个人选择不会自动切换全服阶段。
- 随机本、线索、调查板、投票和第三阶段以后暂不实现。

## 代码和文案的分工

| 内容 | 唯一流程代码 | 可编辑文案 |
| --- | --- | --- |
| 梦的开始 | `gameplay/opening_story_system/OpeningStory.java` | `story_text.json`、NPC/私信配置 |
| 余梦期 | `gameplay/afterdream_story_system/AfterdreamStory.java` | `story_text.json`、白芷私信配置 |
| 医院小章与每日维护 | `gameplay/hospital_system/` | `hospital.json`、`story_text.json` 的 `hospital.*` |
| 感染规则 | `playerattributes_system/infection/PlayerInfectionManager.java` | 无 |
| 全服入口和存档 | `gameplay/story_system/StoryManager.java` | 无 |

story_operations.json 仅支持已注册接入点和固定人数门槛；不定义任意节点、奖励或状态跳转。新增具体玩法仍修改对应阶段 Java 文件。

## 第一阶段：梦的开始

```text
读阿拜多斯公告
  → 进入阿拜多斯地点
  → 与白芷交谈
  → 查看并回复周岑联络消息
  → 阅读周岑介绍
       ├─ 加入逐光会 → 写入身份、发一次补给、建立基地引导
       └─ 保持独立   → 写入选择，不发成员补给
```

状态定义在 `OpeningStoryStep`，处理方法集中在 `OpeningStory`。离开第一阶段后旧入口
停止；进入第二阶段时“建设逐光会基地”归档并保留实际结果，不再成为 HUD 当前目标。

## 第二阶段：余梦期

```text
阶段切换
  → 公开救治公告
  → 白芷医疗说明私信
  → 玩家读信
  → 进入医疗接待点
  → 第一次江晚交互：共同开场
  → 第二次江晚交互：终端复核并发一瓶药剂
  → 0/1 级实际服药清零后完成医疗
  → 首次接待启动全服 48,000 在线活动 tick 倒计时
  → 到期发布面具公告
  → 玩家重新打开江晚并成功领取面具
```

0、1、2 级首次复核都发一瓶基因复苏试剂；二级感染者不能使用当前药剂。倒计时到期
不等于面具已经领取，首次接待所在的同一对话会话不会直接发面具。NPC 界面刷新也不
算新会话，必须关闭后重新打开江晚。

面具实际放入背包后才开启全局感染规则：幸存者上限改为 200、停止每日自然回落、面具
只阻断一级感染者传播；新达到阈值的一级感染者拥有 24,000 tick 治疗窗口，逾期只将
该玩家转为二级并触发一次病毒进化公告。

## 唯一剧情存档

```text
<世界>/data/dreamingfishcore/story/story_state.json
```

文件中的 `StoryWorldState` 同时保存当前阶段、活动 tick、两阶段个人事实和余梦期全服
倒计时。公告已读、私信记录、引导和普通任务仍由各自系统保存，但只是显示/交互投影，
不拥有剧情步骤。

当前代码不会读取或迁移旧的：

```text
world_state.json
opening_player_progress.json
afterdream_player_progress.json
flow_player_progress.json
story_flows.json
story_stage_data.json
```

旧版本切换由运营者停服后手工写新的 `story_state.json`；本模组不会在线猜测旧游标。

## 配置职责

| 文件 | 允许内容 |
| --- | --- |
| `config/dreamingfishcore/story_text.json` | 阶段名、任务文字、公告正文、对白、引导和通知 |
| `config/dreamingfishcore/npc_messages.json` | 私信正文、预设回复、关系条件 |
| `config/dreamingfishcore/npc_data.json` | NPC 身份、外观、普通非主线对白 |
| `config/dreamingfishcore/notices.json` | 已创建公告的存档和展示文字 |
| `config/dreamingfishcore/task_locations.json` | 地点边界、维度和名称 |

改一句话只改 `story_text.json` 或对应私信配置；改顺序、奖励或感染条件只改 Java。不要
在配置里新增自动触发器，也不要让客户端完成包直接修改故事状态。

## 修改时只看这条链

游戏事件/网络包 → `StoryManager` → 当前阶段 `*Story.java` → 公告、私信、NPC、引导、
任务投影。新入口先加到 `StoryManager`，不要在 NPC、公告或 HUD 中复制状态判断。

流程代码改完后，当前开发阶段只做一次本地 `compileJava`；远程服务器保持不动，直到
本地流程确认完成。
