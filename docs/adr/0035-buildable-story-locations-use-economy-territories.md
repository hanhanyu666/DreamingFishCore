---
status: accepted
---

# 可建造剧情地点叠加 EconomySystem 私人领地

## 背景

原任务地点只面向作者布置的固定剧情场景，因此所有启用地点都会把生存玩家切成冒险模式。开服前置阶段需要让玩家在逐光会聚居地内共同建设，并可以自愿拥有私人建筑用地。

## 决策

任务地点增加两种模式：

- `PROTECTED`：保持原有强制冒险和场景保护规则；
- `BUILDABLE`：保持生存模式，允许正常建造、破坏、容器/书架使用、活塞和水；拦截 TNT、生物爆炸、岩浆的放置和跨越地点边界流动，以及打火石的普通点火，只允许打火石实际点燃下界传送门。

`BUILDABLE` 地点不复制 EconomySystem 的领地数据。故事区域外完全保留 EconomySystem 原有的自由圈地行为；DreamingFishCore 只在服务端观察触及故事地点的圈地杖选点并给出提醒，同时拦截覆盖 `PROTECTED` 地点的确认命令。领地可以跨出 `BUILDABLE` 边界，但不能进入 `PROTECTED`。通过校验后，所有权、成员、权限、价格、保存和领地保护仍由 EconomySystem 负责。这样既能保留 EconomySystem 的现有 UI/命令，也避免两个 SavedData 相互漂移。

## 后果

旧配置缺少 `mode` 时默认为 `PROTECTED`，不会改变既有剧情场景。服务器安装兼容的 EconomySystem（当前 API 主版本 1）后，故事区域外无需 DreamingFishCore 额外配置即可自由圈地；没有故事地点时，DreamingFishCore 不拦截任何 EconomySystem 圈地。未来如果需要更细的建筑权限或剧情事件白名单，应继续扩展公共策略接口，不要在 DreamingFishCore 再建立第二份私人领地数据库。
