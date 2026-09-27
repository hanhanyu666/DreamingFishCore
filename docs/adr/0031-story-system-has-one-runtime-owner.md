---
status: superseded
---

# 故事定义与世界运行状态由统一StoryManager管理

> 本 ADR 保留用于说明上一轮重构的背景；当前实现由 [ADR 0037](0037-java-authored-story-transitions.md) 取代。现在的短主线由 Java 状态机拥有流程，JSON 只承载可编辑文案，且不再执行旧 Flow 游标迁移。

旧 `StoryStageManager` 同时混合配置定义、玩家完成集合和客户端视图，后来新增的世界状态模块又与它平行持有阶段概念。当前实现只保留 `gameplay/story_system` 的统一入口：`StoryManager` 注册 Java 阶段定义并管理 `StoryWorldState`，阶段文件分别维护自己的顺序，文案才从 JSON 读取。旧剧情接口和旧进度文件不参与运行时。

客户端同步不再携带其他玩家姓名和UUID，只发送阶段定义、任务成功或失败、在场人数及当前玩家个人状态。客户端完成包不能推进当前 Java 主线；阶段切换和剧情事实只能通过服务端权威入口发生。
