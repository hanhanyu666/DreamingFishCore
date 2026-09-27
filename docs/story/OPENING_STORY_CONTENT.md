# 《灯还亮着》当前开场内容

这是第一阶段“梦的开始”的内容摘要。真实顺序只看
`OpeningStory.java`，真实状态只看 `StoryManager` 的统一故事存档。

## 保留内容

| 类型 | 稳定 ID/编号 | 作用 |
| --- | --- | --- |
| 故事阶段 | `dreamingfishcore:dream_beginning` | 服主手动发布的第一阶段 |
| NPC | `101` 白芷 | 阿拜多斯医疗志愿者 |
| NPC | `105` 周岑 | 逐光会筹备负责人 |
| 公告 | `opening.desert_town` | 阿拜多斯临时安置公告 |
| 地点 | `dreamingfishcore:location_d105866ccdc84c4da7b017a7f13ec7d3` | 阿拜多斯稳定地点 |

## 唯一链路

```text
阅读阿拜多斯公告
  → 进入阿拜多斯
  → 与白芷交谈
  → 查看并回复周岑联络消息
  → 阅读周岑介绍
       ├─ 加入逐光会 → 写入成员身份、发一次补给、创建建设引导
       └─ 保持独立   → 写入个人选择
```

选择不会自动改变全服阶段，也不会因为客户端任务按钮或 NPC 普通闲聊跳步。

## 运行事实

```text
<世界>/data/dreamingfishcore/story/story_state.json
```

`openingPlayerProgress` 中每个 UUID 对应一个 `OpeningStoryProgress`。补给只有在实际
加入并成功发放后才设置 `starterSupplyGranted`，登录重试不会重复发放。

## 可编辑文案

```text
config/dreamingfishcore/story_text.json
config/dreamingfishcore/npc_messages.json
config/dreamingfishcore/npc_data.json
```

这些文件提供句子、私信和 NPC 身份；它们不能定义流程节点、奖励或状态跳转。

## 不属于当前开场

基地贡献统计、随机本线索、调查板、社区投票、复杂阶段任务和自动阶段切换都属于未来
设计。旧 Flow 文件和旧进度文件不会被当前代码读取，也没有在线迁移器。
