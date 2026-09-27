# 剧情总结、个人门槛与手动切章

这次修改落实已确认的运营规则：个人完成保留真实记录；服主发布前情总结让落后玩家接入；指定数量的玩家各自完成前置个人任务后开放世界任务；章节只由服主手动切换。现有两章内容继续使用 Java 状态机。

## 服主如何发布前情总结

配置文件为 `config/dreamingfishcore/story_operations.json`。首次加载创建空文件：

```json
{
  "schemaVersion": 1,
  "recaps": [],
  "worldTasks": []
}
```

在 `recaps` 中增加草稿，例如：

```json
{
  "id": "dreamingfishcore:recap/opening_end_01",
  "title": "开场阶段回顾",
  "content": "由服主在这里填写本服实际发生的事情、行动结果和当前安排。\n这段文字只会在手动发布后发放。",
  "checkpointId": "dreamingfishcore:afterdream/start"
}
```

使用以下命令检查并发布，均要求权限等级 3：

```text
/dreamingfish story recap list
/dreamingfish story recap preview dreamingfishcore:recap/opening_end_01
/dreamingfish story recap publish dreamingfishcore:recap/opening_end_01
```

发布读取磁盘上的草稿，并将当时的标题、正文、接入点保存进本世界的剧情存档。编辑文件、执行内容重载都不会自动发布总结。同一 ID 的发布正文不可改写；再次发布同一 ID 只重试补投。后续需要不同内容时使用新 ID。

目标阶段已开放时，系统向仍落后于接入点的在线玩家发放，并接入对应步骤。离线玩家及后续新人登录认证成功时补投，每人每份只发一次。收到过的总结永久保留在终端“剧情广播”中，可以反复阅读；阅读不再跳转进度。

跨章节总结可提前发布，等你手动开放目标阶段后才发放，不会提前跳进未来章节。建议顺序是：写好并发布总结，然后手动切章。若先切章再发布“新章开始”总结，已经进入该开始节点的玩家不再属于落后玩家，不会补收这份总结。

### 当前可用接入点

| 接入点 | 接入后的行动 | 跳过的当前章任务 |
| --- | --- | --- |
| `dreamingfishcore:opening/start` | 从开场安置通知继续 | 无 |
| `dreamingfishcore:opening/membership` | 了解周岑的介绍，亲自选择是否加入逐光会 | 抵达阿拜多斯、见白芷 |
| `dreamingfishcore:afterdream/start` | 当前余梦期的医疗入口 | 上一章按历史归档处理 |
| `dreamingfishcore:afterdream/reception` | 与江晚开始复核 | 阅读白芷通知、抵达接待点 |

接入不会替玩家选择组织，也不会伪造药剂领取、首次接待、治疗完成或面具领取。已亲自完成的部分保留原记录；被跳过的部分单独标记，不计入人数门槛。接入步骤不会把更靠后的个人进度倒退。

接入点由 `StoryCheckpoint` 注册并由阶段代码定义恢复行为，不能在 JSON 中填写任意状态枚举强行跳转。后续增加剧情节点时，同时增加安全接入点和相应测试。当前没有注册“假定已经治疗”之类的跳转。

## 个人任务线如何开放世界任务

在同一个配置文件的 `worldTasks` 数组中定义世界任务。例如，以下是配置格式示例，不是默认上线内容：

```json
{
  "id": "dreamingfishcore:operator/afterdream_shared_action",
  "number": 2901,
  "stageId": "dreamingfishcore:afterdream",
  "name": "由服主填写共同任务名称",
  "content": "由服主填写具体目标、地点和验收标准。",
  "locationId": "dreamingfishcore:location_d41fd2b0cc77479c9e2017ae727fd117",
  "prerequisiteTasks": [
    "dreamingfishcore:afterdream/receive_baizhi_message"
  ],
  "requiredPlayers": 3,
  "successFlag": "dreamingfishcore:operator/shared_action_finished"
}
```

任务 ID、正整数编号不能与现有任务重复。地点必须已经注册；不采用现场在场名单的任务可以留空。前置任务必须是当前或更早章节的个人任务，不接受世界任务或未来章节任务。固定人数范围为 1–16384。

`prerequisiteTasks` 可列出整条个人任务线需要检查的多个任务。统计的是同时完成这些任务的不同玩家，不会把甲做前半段、乙做后半段拼成一个完成者。重复事件、改名、新人加入和前情跳过均不增加完成人数。

执行：

```text
/dreamingfish story content validate
/dreamingfish story content reload dreamingfishcore:operations_01
/dreamingfish story worldtask list
```

重载安装世界任务定义并检查门槛；之后每次个人真实完成、服务器重启或手动切章都会重新检查。人数达标且任务所属阶段已开放时，世界任务只解锁一次。已解锁的任务不因人数门槛调整或新人加入重新锁定。

所有当前玩家都可以看到已开放的共同任务，并不要求每个人都先完成自己的个人线。首次解锁时不自动结算世界任务，也不替前置任务完成者填写世界任务参与记录。

### 世界任务的结算

具体玩法在服务端验证成功或失败后，调用 `StoryManager.resolveTask`；采用官方任务地点时调用 `resolveTaskAtConfiguredLocation`，系统收集结算当刻区域内的合格玩家。JSON 不提供任意脚本、自动物品收集或建设识别器；新增这些具体玩法仍由对应的 Java 处理器实现。

服主也可以现场验收并手动结算：

```text
/dreamingfish story worldtask unlock dreamingfishcore:operator/afterdream_shared_action
/dreamingfish story worldtask succeed dreamingfishcore:operator/afterdream_shared_action
/dreamingfish story worldtask fail dreamingfishcore:operator/afterdream_shared_action
```

`unlock` 是手动绕过人数门槛，不补写个人完成记录。`succeed` 和 `fail` 只能对当前阶段已解锁、未归档的世界任务生效。配置了地点时按现场名单记录参与；地点为空时不推测参与者。结果只结算一次。

成功时自动写入可选的 `successFlag`，供后续场景、设施或 Java 剧情判断使用；旗标本身不会凭空生成建筑、配方或 NPC 服务。失败不会写入成功旗标。世界成果属于全服，后来加入的人也使用同一个世界结果。

已发布的世界任务不能通过重载删除或更换所属阶段；旧定义应继续保留供历史查询。正文可以更新，活动引导随之刷新。首次解锁后保存的结果不会被定义重载清空。

## 手动切章和历史显示

```text
/dreamingfish story stage set dreamingfishcore:afterdream
```

阶段只能向前发布，达到人数门槛或世界任务完成都不会自动切章。切章时归档旧任务和旧引导，保留实际的世界成功、失败、未结算及个人完成记录；不能把切章当成建设成功、治疗完成或全员参加。

终端分别显示“亲自完成”“已接入后续 · 无需补做”“已归档 · 无需补做”及世界任务的实际结果。没有参与历史的玩家可直接继续当前阶段，不需要补齐旧章节完成率。

## 多条引导与追踪

- 同一任务线只保留当前步骤，不同任务线可同时活动。作者通过 `GuidanceSeed.withStoryLine(...)` 声明任务线；未指定时沿用阶段作为默认任务线，保持旧内容兼容。
- 余梦期医疗处理和面具领取可以同时提示；共同任务各有独立任务线。
- HUD 展开一张当前卡片，最多显示两张简略卡片。默认按住左 Alt 滚轮切换，也可按 `]` 切换下一项；两个按键均可在控制设置中修改。
- 终端任务详情提供“追踪行动”按钮。新增任务不抢走仍有效的当前追踪，普通滚轮仍切换快捷栏；追踪选择不影响任何任务计数。
- 断开连接时清理本次客户端追踪选择；服务器保留全部任务事实，重连后恢复有效行动。

## 存档与验证

统一的 `story_state.json` 从 schema 3/4 升级到 5，补充运营与医院记录，不重新读取已弃用的 Flow 文件，不猜测或回填历史完成事实。旧版本已经写入的结果保持原样。公告用现有网络阅读界面展示前情，前情发布、个人收件及已读仍统一保存在故事状态中。

任务同步包含归档和跳过标志；医院功能加入后网络协议为 `0.23.0`，客户端与服务端需要使用同一版模组。

运营功能测试覆盖旧信重读、医疗和面具并行、总结发放幂等、离线后补发、存档升级、真实完成人数交集、历史查询、任务网络往返及追踪选择。医院增量功能和最新验证记录见 [医院发布指南](HOSPITAL_ROLLOUT.md)。完整 Gradle 流水线仍受依赖下载连接重置影响；未进行游戏内多人和 HUD 视觉验收。
