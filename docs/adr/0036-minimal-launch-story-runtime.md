---
status: superseded
superseded-by: 0037-java-authored-story-transitions.md
---

# 开服版剧情采用单一权威的最小流程（已被 0037 取代）

这份记录保留历史背景，不再描述当前运行架构。它曾经假定开服剧情由多个
`story_flows.json` 流程共同驱动；实际维护中发现通用 Flow 的游标、条件、效果和
迁移分支会让短流程产生重复推进、重复发奖和文案/状态错位。

当前实现请阅读 [0037：开服剧情使用 Java 流程与数据文案分离](0037-java-authored-story-transitions.md)。
现在的规则是：流程顺序、条件、奖励和感染规则写在 Java；JSON 只保存可编辑文案或
展示定义；运行时不自动迁移旧玩家剧情游标，版本切换由服主停服备份后手工处理。
