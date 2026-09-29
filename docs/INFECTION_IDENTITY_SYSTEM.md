# 感染身份系统（里程碑 1）

> 相关决策：[ADR 0003](adr/0003-contagion-depends-on-infection-stability.md)、
> [ADR 0005](adr/0005-identity-transition-is-reversible-before-finale.md)、
> [ADR 0016](adr/0016-survivor-and-infected-use-soft-asymmetry.md)、
> [ADR 0017](adr/0017-settlements-can-suppress-passive-contagion.md)、
> [ADR 0040](adr/0040-infection-identity-levels-plus-relapse-window.md)
>
> 术语以 `CONTEXT.md`「感染身份 / 接触暴露 / 软身份差异 / 分层治疗」为准，
> 阵容与剧情设定见 `PROJECT-M.D.G.A/docs/story/02-感染与重生规则.md`。

## 一、四种身份与存档表示

| 身份 | 存档表示 | 会传播 | 重生消耗 |
| --- | --- | --- | --- |
| 幸存者 | `infectionLevel = 0` | 否 | 5 |
| 不稳定感染者 | `infectionLevel = 1` | **是** | 20 |
| 稳定感染者 | `infectionLevel = 2`，无复发窗口 | 否 | 10 |
| 传播复发 | `infectionLevel = 2` + `relapseUntilActiveTick >= 0` | **是** | 10 |

- 身份解析的唯一入口是 `PlayerAttributesData.getInfectionIdentity()` / `InfectionIdentity.of(data)`。
  客户端对应 `PlayerAttributesClientCache.getInfectionIdentity(uuid)`。
- 传播复发**不是等级 3**（见 ADR 0040）：用正交的到期 tick 表达，因此所有既有的 `>= 2`
  判定（复核话术、重构疗程准入、感染上限 200）保持正确，旧存档（0/1/2）无需迁移脚本。
- 读档时 `normalizeInfectionState()` 会清掉「身份不是稳定感染者却带着复发窗口」的损坏数据。

## 二、身份推进

```
幸存者 ──感染值达到当前上限──▶ 不稳定感染者 ──治疗窗口关闭／稳定治疗──▶ 稳定感染者
   ▲                                    │                                   │
   └────────── 早期逆转（基因复苏试剂）───┘                                   │
   └────────────────────── 重构疗程（三次疗程 + 终检）────────────────────────┘
                                       稳定感染者 ──重伤──▶ 传播复发 ──5 分钟／稳定治疗──▶ 稳定感染者
```

- 面具阶段新产生的**不稳定**感染者拥有 `NEW_LEVEL_ONE_TREATMENT_WINDOW_TICKS`（24,000 tick，
  一个剧情活动日）的治疗窗口；窗口关闭即稳定化。活动时钟不可用时**不排期**，
  以免写入一个立刻到期的期限。
- 传播复发由**单次实际损失 ≥ 6 点生命**触发，持续 5 分钟，结束后冷却 5 分钟。
  复发与冷却都**不随复活护符传递**：被复活者的身体按模板重建，要自己再次受重伤才会复发。

## 三、接触暴露

- 只有**幸存者**会累积暴露；感染身份已适应异常因子，其暴露量按「离开范围」自然衰减。
- 每 20 秒一次判定，半径 32 格：在范围内 `+1`（上限 6），离开范围 `-1`。
- 警告按 1 / 2 / 3 三档升级，同一档不重复提示；攒满 6 点转化为一次 **+8 点感染值**，随后清零重攒。
- 传播来源只包括**不稳定感染者**与**传播复发者**。稳定感染者在正常状态下不产生暴露，
  这是「稳定感染者不会持续感染队友」的落点。
- 暴露量**不写入存档**（`ContactExposureTracker` 是会话内状态）：登出、死亡、重生、
  换维度都会清空。持久化会让"离开后仍无限累积"以另一种形式复活。
- 受伤累积（`生命损失 / 5`）仍然保留：接触暴露不替代其它感染来源。
  单次伤害的折算输入上限是玩家当前最大生命——`/kill` 一类来源会用 `Float.MAX_VALUE`
  结算伤害，不截断就会让一次自杀直接涨满感染值（实机日志出现过 `3.4e38 → 0.00->100.00`）。
- **待接入**：ADR 0017 的聚居地过滤/异常因子抑制设备属于领地建设内容，尚未实装；
  落地位置是 `InfectionEventHandler.hasSpreadingSourceNearby` 的来源过滤。

## 四、软身份差异

| 差异 | 落点 | 现状 |
| --- | --- | --- |
| 重生代价分档 | `InfectionRules.respawnCost` → `DeathEventHandler.getNormalCost` | 已实装（5 / 10 / 20，保留物品栏 +30） |
| 丧尸仇恨偏好 | `AggroPreferenceRules` + `InfectionAggroHandler`（`LivingChangeTargetEvent`） | 已实装：普通丧尸对稳定感染者会转向 24 格内更近的幸存者，否则 60% 概率放弃目标；尸潮丧尸的正在执行的声音/广播目标不参与 |
| 污染适应 | `PlayerInfectionManager.applyInfectionDebuff` | v1：稳定感染者不再承受「感染」效果的移速/攻击力惩罚；污染区域实装后应在此追加环境伤害减免 |
| 关键互动资格 | — | **未实装**：校准基准、精密设施、特殊样本都还没有对应内容（疑光期/破晓期尚未注册到运行时）。现有唯一的身份门槛是基因复苏试剂拒绝稳定感染者 |

数值全部集中在 `InfectionRules`：内容设计（`PROJECT-M.D.G.A/docs/story/待决策事项.md`）
定稿后只改这一个类，不必翻调用点。该里程碑刻意不做配置化平衡。

## 五、服务端动作接口

`InfectionTreatmentService` 是身份变化的唯一入口，所有动作都会校验会话、写入玩家档案并同步客户端：

| 动作 | 方法 | 适用身份 |
| --- | --- | --- |
| 抑制剂（降低感染值） | `applySuppressant` | 幸存者 |
| 足量抑制剂（清零感染值） | `applyFullSuppressant` | 幸存者（基因复苏试剂走这一档） |
| 早期逆转 | `applyEarlyReversal` | 不稳定感染者 |
| 稳定治疗 | `applyStabilization` | 不稳定感染者（→ 稳定）；传播复发（结束复发） |
| 高成本重构 | `applyReconstruction` | 稳定感染者 / 传播复发 |

代价（设施、资源、时间、冷却）由内容层提供，本里程碑只提供接口。
按 ADR 0005，现有「三次疗程 + 终检」在代码里归为**重构疗程**；剧情文案里的
「二级感染 / 三次早期逆转疗程」等叫法尚未统一，属于剧情侧术语对齐。

## 六、网络与调试

- `Packet_SyncInfectionData` 载荷为 `感染值 + 是否感染 + 感染等级 + 感染上限 + 是否复发`，
  协议版本 **0.26.0**（改线格式，旧客户端会被拒绝连接）。
- 调试命令（3 级权限）：
  - `/dreamingfish debug infection set <survivor|unstable|stable|relapse|0|1|2> [感染值]`
  - `/dreamingfish debug infection stabilize | relapse | cure`
  - `/dreamingfish debug infection exposure step [次数] | clear`
    ——按真实代码路径推进暴露判定，单人也能验证警告阶梯、转化与清零，不绕过任何规则。

## 七、测试覆盖

- `InfectionRulesTest`：暴露累积/衰减/转化、警告档位、复发触发条件、重生代价分档。
- `ContactExposureTrackerTest`：警告只升级一次、转化后清零、衰减后重新警告、清除。
- `InfectionIdentityTest`：身份解析（含复发只对稳定感染者成立）与术语表文案。
- `PlayerAttributesDataTest`：复发窗口的序列化往返、离开稳定身份时清空、损坏数据归一。
- `PlayerInfectionManagerTest`：复活不继承复发窗口与冷却。
- `InfectionEventHandlerTest`：面具只拦「具有传播能力」的来源（含传播复发）。
- `AggroPreferenceRulesTest`：仇恨转移/放弃/保持的边界与 NaN 防御。
- `InfectionIdentityPersistenceTest`：走真实的原子写 + 读盘通道，锁定复发字段的磁盘契约，
  并验证四态改造之前的旧存档（没有复发字段）读回来就是合法的稳定感染者。
- `InfectionEventHandlerTest`：`/kill` 一类的 `Float.MAX_VALUE` 伤害折算被血条上限截断。
- `InfectionStateMachineGameTest`（**无头 gametest**，`gradlew runGameTestServer`）：用
  `GameTestHelper.makeMockServerPlayerInLevel()` 造出真实服务端玩家，覆盖单测够不到的部分——
  两名玩家同场的传播规则、暴露转化为感染、未认证会话被拒、身份落盘后按真实加载路径重载、
  重伤触发复发并可由稳定治疗结束。

### 无头 gametest 说明

- 结构模板：`src/main/resources/data/dreamingfishcore/structure/empty.nbt`（3×3×3 石台 + 空气），
  由 `tools/generate_empty_gametest_structure.py` 生成；原版与 NeoForge 都没有现成的"空模板"。
- 模拟玩家的两个坑（都已写进测试注释）：`makeMockServerPlayerInLevel` 的匿名子类固定
  `isCreative() == true`，且刚加入时带着服务端的出生无敌时间（字段是 private，测试改不了），
  因此伤害相关的验证要用 `damageSources().genericKill()` 绕过无敌——它绕过的只是无敌判定，
  伤害事件本身照常派发。
- 网络层的配套改动：`DreamingFishCore_NetworkManager.sendToClient` 现在会兜住载荷下发失败并记
  警告。自定义载荷只有在客户端协商过通道后才允许下发，模拟连接没有协商通道；在服务端崩溃
  与"发不出去就留痕"之间，后者才是正确行为。

## 八、实机验证记录（2026-09-30，玩家 Dev，单机存档）

`run/logs/latest.log` 逐条证据：

| 时间 | 日志 | 验证点 |
| --- | --- | --- |
| 00:09:32 | `已将 Dev 的感染身份设为：不稳定感染者，感染值 50.0` | `set unstable`：服务端写入 + 回执 |
| 00:09:51 | `稳定治疗完成：玩家 Dev 成为稳定感染者` + 动作栏「突变已经稳定下来：你现在是稳定感染者。」 | 稳定治疗（不稳定 → 稳定） |
| 00:10:0x | `玩家 Dev 进入传播复发（6000 tick 后结束）` + 「已强制进入传播复发。」 | 传播复发可进入、有明确时长 |
| 00:10:38 | `重构疗程完成：玩家 Dev 恢复为幸存者` + 聊天「重构完成，你已经恢复为幸存者。」 | 重构疗程（稳定 → 幸存者），四态链路闭合 |
| 00:11:14–00:12:14 | 暴露量 1.0 → 1.0 → 2.0 → 4.0 → 5.0 | 暴露累积与**离开范围后的衰减**同时生效（两次 `step 1` 之间被自然衰减回 0） |
| 00:12:29 | `已清空接触暴露量。` | 暴露清零 |
| 00:13:24 | 死亡界面收到 `正常=20.0, 保留=50.0` | 重生代价按身份分档（不稳定 20 + 保留物品栏 30） |
| 00:13:34 | `玩家 Dev 正常复活，消耗 20.0 复活点（剩余: 45.0）` | 扣费与服务端计算一致 |
| 存档核对 | 治疗后 `infectionLevel=2`、治疗窗口 `-1`、复发字段 `-1`；复活后 `infectionLevel=1`、`respawnPoint=45.0` | 身份变化真实落盘（`player_attributes.json`） |

同一次实测还暴露并修掉了一个既有问题：`/kill` 用 `Float.MAX_VALUE` 结算伤害时，
旧折算逻辑让幸存者从 `0.00` 直接涨到 `100.00` 感染值（日志原文
`实际生命损失:3.4e38, 增加感染:6.8e37, 0.00->100.00`）。现在单次伤害折算被血条上限截断。

**尚未在真人多人环境下跑过**（但已由无头 gametest 覆盖，见第七节）：

1. 接触暴露攒满 6 点转化为 +8 感染值——已由 gametest `exposureConvertsIntoInfectionAndResets` 覆盖；
2. 重伤触发传播复发——已由 gametest `heavyDamageTriggersRelapseAndStabilizationEndsIt` 覆盖
   （真人实测那次复发是用调试命令进入的）；
3. 重启后身份保留——已由 gametest `treatmentIsServerVerifiedAndSurvivesReload` 走真实的
   落盘 + 重载路径覆盖，另有数据层单测与存档核对；
4. 稳定感染者与幸存者同场不持续感染队友、丧尸仇恨转移——前者由 gametest
   `stableInfectedDoesNotExposeNearbySurvivor` / `unstableInfectedExposesNearbySurvivor` 用
   两名真实服务端玩家覆盖；**丧尸仇恨转移仍然只有单测证据**（需要真人玩家与丧尸同场才能看到行为）。
