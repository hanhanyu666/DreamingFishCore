# 丧尸（`siege_zombie`）

模组注册了一个继承原版 `Zombie` 的新物种。它沿用原版僵尸的寻路、模型骨骼、声音和战斗动画，并提供可按故事阶段开关的能力：

- 挖掘：原版路径持续不可达后，分别沿头部和身体高度寻找朝向目标的第一面真实障碍，优先开头部空间、再开脚部空间，最终形成两格高入口；完整墙体即使与屋顶相连也可以被选中。默认不会破坏方块实体、门、基岩或超过硬度上限的方块。
- 开门：使用原版 `OpenDoorGoal` 打开木门。`breakingDoors` 另外控制原版困难模式破门目标。
- 放置方块：只有目标是玩家、玩家明显高于丧尸且原版路径持续不可达时，才会在真实的墙体/缺口前放置配置的方块；单纯和其他丧尸碰撞不会触发建造。每只丧尸有独立的方块“补给”数量，单次追击最多放置 3 块，放置成功才会消耗。
- 堆人墙：只有玩家脚面至少比未骑乘丧尸高 `4` 格时，才会寻找附近较低的原版 Zombie（包括丧尸自身）并短暂骑乘；同层房屋或只高一两格的玩家不会触发。挂载前会检查完整乘客碰撞箱，跳出前也会检查水平通道，阻止乘客状态绕过方块碰撞而穿墙。
- 移动声追踪：玩家在丧尸视线外移动时，服务器按节流后的移动采样发出“声音”；符合距离和生存状态的丧尸可以直接接收目标。连续脚步会刷新同一个锁定的有效期，但玩家没有离开默认四格的声音锚点时不会反复改变隐藏寻路坐标。玩家在大建筑内移动超过该距离后，最新情报锚点仍会正常更新。
- 目标广播：看见玩家或听见玩家的丧尸会在有限半径内广播目标；接收者可以继续转发，形成有限跳数/有限人数的 BFS 波次。
- 分流包围：原版导航进入玩家附近后，丧尸会认领不同的环形方位，并根据两格左右的局部拥挤情况修正最后一小段移动方向；它不会重算或替换原版路径。玩家藏在密闭建筑内时，同一个方位会投影到对应方向、且原版寻路可到达的外墙位置，因此尸群会绕到建筑四周，再由普通破墙逻辑从各自方向攻入。个体一旦选中可达外墙或开始挖掘，就会暂时承诺该次突破：期间继续接收新脚步情报，但不会放弃已经走到一半或挖到一半的入口。
- 任务地点保护（丧尸 AI）：启用后，位于任意启用的 `task_location`（包括“可建造”和“不可建造/强制保护”）内的丧尸不能挖掘、放置方块或破门；受影响方块/门即使在边界内而丧尸站在外侧也会受到保护。木门开启仍由独立的原版开门目标处理，不受此项禁止影响。
- 保护区恢复：启用后，处于任意启用任务地点的玩家会持续获得生命恢复 I；效果由服务端短时续期，离开地点后自然消退。
- 保护区刷怪：启用后，任意启用任务地点内不会自动生成 `MONSTER` 类怪物（自然生成、刷怪笼/试炼刷怪笼、结构/事件生成、增援等均拦截）；`/summon`、刷怪蛋、管理员脚本和实体转化等明确创建行为仍可用。
- 外观：沿用原版僵尸骨骼和比例，使用十二套 64×64 社区/委托皮肤：`zombiedf1—5`、`qingmozangbi`、`left2mine_zombie`、`zombie_girl`、`hanhanyu_z`、`qingmo_z`、`wither_light_z` 和 `jijituan_z`。十二套皮肤全部共用发光眼睛渲染层，每只丧尸会从蓝、绿、粉、红、白、黄六种眼部贴图中取得一种；眼睛通过与原版蜘蛛眼相同的全亮渲染层显示，在黑暗中不会被环境光压暗。皮肤和眼色都按实体 UUID 稳定选择，重进世界不会随机换样，但发光只影响视觉，不会像火把一样照亮周围方块。

所有能力都遵守“原版寻路优先”：原版近战目标和导航与堆墙/放置/挖掘处于同一 AI 优先级，但原版攻击目标先注册，因此只要原版攻击目标仍在运行，这些后备动作不会抢占它；攻击导航停止后还要连续确认路径不可达约 20 tick（约 1 秒）才会尝试。能绕过柱子、台阶或其他普通障碍时不会主动挖方块。开门只在实际碰到木门时按原版门交互逻辑处理。放置还要求目标至少高出丧尸约一格；刚放置的方块会短暂进入施工保护，半径 2 格内的丧尸会等待路径重新计算，不会互相拆建；同一玩家在这段保护窗口内也只允许一只丧尸放置一次，挖掘也会在这段时间让路。

视觉、声音和广播只是三种“获得目标”的来源，成功后都会写入同一个攻击目标，并共用追踪、原版寻路、包围、开门和障碍处理逻辑。声音/广播目标唯一额外的限制是短时记忆：记忆有效期间等同于已经看见玩家，玩家不再发声且记忆过期后才会丢失目标，因此不会变成永久透视。

隐藏追踪现在区分“最新声音情报”和“当前突破承诺”两层状态。最新情报会随玩家在大建筑内的有效移动继续更新；尚未分配外墙的丧尸会使用新情报。已经分配外墙、正在赶路或正在挖掘的丧尸则冻结自己的有效锚点，直到入口被原版寻路判定为可用、重新看见玩家、目标失效或承诺超时。承诺是逐只丧尸保存的，因此老一批可以继续打开原突破口，后到的一批可以转向玩家的新区域。

## 配置文件

第一次启动后会生成：

`config/dreamingfishcore/zombie_species.json`

根级字段控制默认值，`stageOverrides` 按故事阶段 ID 覆盖字段。覆盖对象只需要写要改变的字段，未写字段继承根级值。例如：

```json
{
  "schemaVersion": 1,
  "enabled": true,
  "speedMultiplier": 1.35,
  "digging": true,
  "openDoors": true,
  "breakingDoors": true,
  "placingBlocks": true,
  "stacking": true,
  "hearing": true,
  "broadcasting": true,
  "surrounding": true,
  "taskLocationProtection": true,
  "taskLocationRegeneration": true,
  "taskLocationSpawnProtection": true,
  "trackingRange": 45.0,
  "hearingRange": 45.0,
  "broadcastRange": 20.0,
  "broadcastMaxHops": 4,
  "broadcastMaxRecipients": 64,
  "hearingCooldownTicks": 6,
  "broadcastCooldownTicks": 10,
  "alertMemoryTicks": 120,
  "alertRetargetDistance": 4.0,
  "breachCommitmentTicks": 160,
  "surroundActivationRange": 12.0,
  "surroundRadius": 2.4,
  "surroundSteeringStrength": 0.55,
  "stackMinimumTargetHeight": 4.0,
  "placementBlock": "minecraft:cobblestone",
  "placementBlocks": 16,
  "naturalSpawn": true,
  "zombieFamilySpawnPercent": 120,
  "vanillaZombieSpawnPercent": 40,
  "customZombieSpawnPercent": 60,
  "otherMonsterSpawnPercent": 80,
  "stageOverrides": {
    "dreamingfishcore:dream_beginning": {
      "digging": false,
      "placingBlocks": false,
      "stacking": false,
      "taskLocationProtection": false,
      "taskLocationRegeneration": false,
      "taskLocationSpawnProtection": false
    },
    "dreamingfishcore:afterdream": {
      "digging": true,
      "placingBlocks": true,
      "stacking": true,
      "taskLocationProtection": true,
      "taskLocationRegeneration": true,
      "taskLocationSpawnProtection": true
    }
  }
}
```

常用参数：

| 字段 | 默认值 | 说明 |
| --- | ---: | --- |
| `speedMultiplier` | `1.35` | 移动速度倍率；`1.0` 为原版速度 |
| `digRange` | `8` | 搜索目标方向障碍物的最大距离 |
| `maxDigHardness` | `3.0` | 可挖方块硬度上限；不可破坏方块始终跳过 |
| `digCooldownTicks` | `0` | 挖完一个方块后再处理下一个方块前的额外等待；默认 `0`，与玩家连续挖掘一致 |
| `placementBlock` | `minecraft:cobblestone` | 放置方块 ID |
| `placementBlocks` | `16` | 每只新生成丧尸的放置补给 |
| `maxStackHeight` | `3` | 堆叠链允许的最大高度 |
| `hearing` | `true` | 是否接收视线外移动玩家的声音 |
| `broadcasting` | `true` | 是否把已发现的玩家广播给附近丧尸 |
| `surrounding` | `true` | 是否对已锁定玩家（视觉、听觉或广播）启用环形分流、外墙方位分配和局部防拥挤 |
| `trackingRange` | `45.0` | 可见目标的直接索敌距离（格） |
| `hearingRange` | `45.0` | 移动声音的基础听觉距离（格）；疾跑约增加 15%，潜行约降低为 55% |
| `broadcastRange` | `20.0` | 一次广播从一个丧尸传播到另一个丧尸的最大距离（格） |
| `broadcastMaxHops` | `4` | 单次广播最多转发跳数；设为 `0` 时仍可接收直接目标，但不再转发 |
| `broadcastMaxRecipients` | `64` | 单次波次最多新增接收者；代码另有硬上限 256 |
| `hearingCooldownTicks` | `6` | 同一玩家移动声音采样的最短间隔（tick） |
| `broadcastCooldownTicks` | `10` | 同一玩家广播波次的最短间隔（tick） |
| `alertMemoryTicks` | `120` | 声音/广播目标的短时记忆（tick） |
| `alertRetargetDistance` | `4.0` | 隐藏玩家至少离开上一次有效声音位置多少格，才更新最新声音情报；范围内脚步只刷新记忆时间 |
| `breachCommitmentTicks` | `160` | 个体选中外墙或开始挖掘后，至少坚持原突破锚点多久；`160 tick` 为 8 秒，赶路和挖较慢方块时会自动延长到足以完成当前动作 |
| `surroundActivationRange` | `12.0` | 距离玩家多少格以内开始修正原版路径的最后移动方向 |
| `surroundRadius` | `2.4` | 围绕玩家认领方位的目标半径；必须小于触发距离 |
| `surroundSteeringStrength` | `0.55` | 环形方位对原版路径方向的混合强度，范围 `0.05—0.9` |
| `stackMinimumTargetHeight` | `4.0` | 玩家脚面至少比发起堆叠的丧尸高多少格，才允许使用人墙 |
| `taskLocationProtection` | `true` | 任务地点内禁止丧尸挖掘、放置方块和破门；可建造与强制保护模式都适用，开木门不受影响 |
| `taskLocationRegeneration` | `true` | 任务地点内玩家持续获得生命恢复 I |
| `taskLocationSpawnProtection` | `true` | 任务地点内禁止自动生成敌对 `MONSTER`；刷怪蛋、`/summon` 等明确创建行为不受影响 |
| `naturalSpawn` | `true` | 是否启用自然怪物刷怪池中的僵尸条目拆分和其他怪物权重调整 |
| `zombieFamilySpawnPercent` | `120` | 僵尸家族总权重相对原版的百分比；`120` 表示提高 20% |
| `vanillaZombieSpawnPercent` | `40` | 调整后僵尸家族权重分给原版僵尸/尸壳的份额 |
| `customZombieSpawnPercent` | `60` | 调整后僵尸家族权重分给逐光丧尸的份额 |
| `otherMonsterSpawnPercent` | `80` | 其他 `MONSTER` 条目相对原版的权重；`80` 表示降低 20% |

刷怪总量遵守原版的 `MONSTER` 上限。对每个原版僵尸家族条目，代码先按 `zombieFamilySpawnPercent` 调整总权重，再按 `vanillaZombieSpawnPercent` 与 `customZombieSpawnPercent` 拆分。例如原权重 `100`、僵尸家族总权重 `120`、内部比例 `40/60` 时，结果就是原版僵尸 `48`、逐光丧尸 `72`。蜘蛛、骷髅、苦力怕等其他条目则各自保留 `80%` 权重。权重会由游戏在抽取时自动归一化；实际实体总数仍由原版/ServerCore 的 `MONSTER` 上限决定。整数权重无法精确表示的小条目会采用最近整数；没有原版僵尸或尸壳刷怪条目的维度或生物群系不会凭空增加新丧尸。

拆分后的条目会按有效类型、权重和数量复用同一个 `SpawnerData` 对象。NeoForge 的自然刷怪器会在抽取后再次验证条目，这个对象稳定性是让逐光丧尸能够通过第二次验证的必要条件。

`diggableBlocks` 非空时会变成严格白名单；`protectedBlocks` 始终优先阻止挖掘。所有破坏/放置都会经过 NeoForge 的 `mobGriefing` 和实体破坏事件钩子。

无工具丧尸的单块破坏时间使用原版生存玩家空手公式，而不是自定义倍率：可徒手采集的方块为 `硬度 × 30 tick`，必须使用正确工具的方块在空手状态下为 `硬度 × 100 tick`。例如木板硬度 `2`，需要约 `60 tick`（3 秒）；石头硬度 `1.5`，需要约 `150 tick`（7.5 秒）。旧配置中的 `digTicksPerHardness` 已不再读取，可以安全删除。未来带工具变种会按工具对当前材料的实际破坏速度和“是否为正确工具”计算，因此镐子能加速石头，但对木头仍保持空手速度。

## 管理命令

- `/dreamingfish zombie status`：查看当前故事阶段解析出的能力开关。
- `/dreamingfish zombie set <能力> <true|false>`（权限等级 3）：直接修改当前故事阶段的能力覆盖值并原子写回 JSON；在线实体约半秒内生效。能力名可用 `digging`、`open_doors`、`breaking_doors`、`placing_blocks`、`stacking`、`hearing`、`broadcasting`、`surrounding`、`task_location_protection`、`task_location_regeneration`、`task_location_spawn_protection`。
- `/dreamingfish zombie enable_all`（权限等级 3）：一键开启当前故事阶段的全部丧尸能力及保护区规则，并一次性原子写回配置；不会改动自然生成比例等非能力参数。
- `/dreamingfish zombie reload`（权限等级 3）：热重载 JSON；在线实体会在约半秒内应用新阶段设置。若校验失败，会保留上一份有效配置并返回错误，不会意外开启全部能力。
- 数值参数（距离、跳数、人数上限等）在 JSON 中编辑后执行 `reload`；`status` 会显示当前阶段解析出的追踪和广播参数。
- `/summon dreamingfishcore:siege_zombie` 或创造栏中的“丧尸刷怪蛋”可用于测试。

### 堆人墙测试

1. 先执行 `/dreamingfish zombie set digging false` 和 `/dreamingfish zombie set placing_blocks false`，避免挖掘/建造遮住堆叠动作；测试结束再分别设回 `true`。
2. 用刷怪蛋或 `/summon` 生成至少两只丧尸，让它们在同一条直线上相距约 1—2 格，并清掉附近其他可攻击目标。把离玩家较近的称为“前排”，后面的称为“后排”。
3. 在它们正前方的窄走廊里搭一面至少四格高、至少三格宽、横向封住绕行路线的墙（开放平地上的孤立三格墙可以绕过去，可能不会触发）；把玩家脚面站到比地面丧尸高至少四格的位置，并确保能被看见。
4. 等待约 1—2 秒（路径失败确认需要约 20 tick）。后排丧尸会短暂骑上前排丧尸并向目标方向跳出；打开碰撞箱可直接观察。也可以对前排那只执行 `/data get entity <实体UUID> Passengers` 查看真实乘客关系。
5. 若要放慢动作，把 JSON 中的 `stackDurationTicks` 调大（例如 `40`，会把骑乘观察窗口延长到约 1 秒），然后执行 `/dreamingfish zombie reload`。测试结束执行 `/dreamingfish zombie set digging true` 和 `/dreamingfish zombie set placing_blocks true`。

三只成年原版比例僵尸并不是简单叠成 `1.95 × 3`：骑乘挂载点会让身体互相重叠。按 1.21.1 的僵尸尺寸和挂载点计算，三层从最底部脚面到最顶部约为 `4.575` 格。因此默认四格高度门槛接近三层堆叠的有效高度，同时足以排除普通同层房屋。

### 追踪、听觉和广播测试

1. 用刷怪蛋生成两只丧尸。把第一只放在玩家可见范围内约 44 格处，第二只放在第一只旁边 10—15 格、但距离玩家超过 45 格处，并用墙挡住第二只的视线。
2. 切换生存后持续走动。第一只应在直接索敌距离内获得目标，并把目标广播给第二只；第二只即使初始距离超过 45 格，也应开始朝玩家所在方向移动。广播最多按 `broadcastMaxHops` 跳、最多新增 `broadcastMaxRecipients` 个接收者。
3. 在房间内隔墙走动，确认看不见玩家的丧尸会在声音范围内获得短时目标；在 `3×3` 房间内来回移动时，脚步应持续刷新仇恨，但外部丧尸不会跟着每一步左右换墙。玩家离开上一次声音锚点达到 `alertRetargetDistance` 后才会更新隐藏路径。站着不动时不会每 tick 触发扫描；潜行时听觉距离降低，疾跑时略微增加。
4. 用 `/dreamingfish zombie status` 查看当前阶段参数。若要单独排查机制，可执行 `/dreamingfish zombie set hearing false` 或 `/dreamingfish zombie set broadcasting false`，测试后再设回 `true`。

### 密闭房屋破墙测试

1. 执行 `/gamerule mobGriefing true` 和 `/dreamingfish zombie set digging true`。使用圆石或木板搭建一个完全封闭、至少三格高的房间，不留门窗；默认 `maxDigHardness: 3.0` 可以处理这些方块。
2. 在屋外生成丧尸，玩家进入屋内后切换生存并持续走动，让丧尸通过移动声获得目标。若玩家从未被看见且完全静止，按设计不会凭空暴露位置。
3. 原版寻路会先尝试寻找正常入口；确认不可达约 20 tick 后，丧尸应走到朝向玩家的墙面，先破坏头部高度方块，再破坏同一方向的身体高度方块，形成两格高入口后恢复原版追击。
4. 若没有破坏，依次检查 `/dreamingfish zombie status`、`mobGriefing`、`diggableBlocks` 白名单以及目标墙体硬度是否超过 `maxDigHardness`。门仍交给开门/破门目标，不会被普通挖掘目标抢占。

### 保护区禁止刷怪测试

1. 确认 `taskLocationSpawnProtection: true`，执行 `/dreamingfish zombie reload`；在 `task_locations.json` 中使用一个已启用的 `BUILDABLE` 或 `PROTECTED` 地点。
2. 在地点边界内等待夜间自然刷怪，或观察刷怪笼/试炼刷怪笼：敌对 `MONSTER` 不应在边界内生成，增援、结构和事件产生的敌对怪物也会被拦截。地点外的刷怪不受影响。
3. 用 `/summon minecraft:zombie` 或丧尸刷怪蛋做对照；这些明确创建方式保留，方便管理员测试和剧情脚本。若要让自动刷怪重新出现，可执行 `/dreamingfish zombie set task_location_spawn_protection false`，测试后再设回 `true`。

### 分流包围测试

1. 在平坦、无遮挡且至少 `25×25` 格的场地中心切换生存，执行 `/dreamingfish zombie set surrounding true`。
2. 在距离玩家 12—18 格的东、西、南、北方向各生成 4—6 只丧尸。远处仍按原版路径追击；进入默认 12 格范围后，应逐渐向左右分流。
3. 玩家保持不动约 5 秒并从俯视角观察：丧尸应占据多个方向，接触时会相互错开，而不是全部重合在玩家正前方。最内圈仍会保留正常实体碰撞和近战攻击距离。
4. 执行 `/dreamingfish zombie set surrounding false` 后重复测试，丧尸会恢复完全原版的直追行为。数值参数可在 JSON 中调整后执行 `/dreamingfish zombie reload`。

### 听觉锁定后的四面破墙测试

1. 执行 `/gamerule mobGriefing true`、`/dreamingfish zombie set hearing true`、`/dreamingfish zombie set surrounding true` 和 `/dreamingfish zombie set digging true`。
2. 搭建一个完全密闭、三格高、边长约 `7—11` 格的方形房间；玩家进入房间中央并切换生存，屋外同一侧生成至少 12 只丧尸。
3. 玩家在屋内持续正常走动数秒。丧尸听见后会把玩家写入与视觉发现相同的攻击目标；原版路径确认房间不可达后，各只丧尸按稳定方位绕到不同外墙，而不是全部挤在最初那一面墙。
4. 用第二个旁观账号俯视，或让被锁定玩家保持生存并通过玻璃屋顶观察；被锁定玩家本人不要切换旁观/创造，否则会按原版规则失去仇恨。先到达各方位的丧尸会并行出现破坏进度，最终应从至少两个、通常四个方向开出入口。个体抵达时间不同，因此不会要求同一个 tick 同时开始挖掘。
5. 对照测试可把 `surrounding` 设为 `false`：听觉锁定和追踪仍有效，但尸群会更倾向在最近的一侧集中破墙。测试结束后把配置恢复为需要的故事阶段值。

### 大建筑锚点更新与突破口承诺测试

1. 搭建一个完全封闭、至少 `20×20` 格的大建筑，确保四周可通行；开启 `hearing`、`surrounding`、`digging` 和 `mobGriefing`，在同一侧生成一批丧尸。
2. 玩家在建筑内靠近第一片区域持续移动，等部分丧尸已经分流到外墙或出现挖掘裂纹后，再横穿至少 `8` 格到建筑另一片区域继续移动。该距离应明显超过默认 `alertRetargetDistance: 4.0`。
3. 已经承诺旧突破口的丧尸应继续赶路或挖完旧墙，不会全体跟随每次脚步突然调头；尚未选中外墙、承诺已超时或稍后收到目标的丧尸可以按新声音区域重新分配。这样大建筑会保留多个持续推进的入口，而不是整群在外墙来回摆动。
4. 旧入口形成两格高通道、原版路径重新可达后，对应丧尸会提前解除承诺并追向最新位置；若玩家被直接看见，也会立即放弃旧声音锚点。可把 `breachCommitmentTicks` 临时调成 `40` 与 `240` 对照观察承诺过短和更稳定的区别，修改后执行 `/dreamingfish zombie reload`。

广播实现使用空间查询、每玩家声音节流、每服务器 tick 的波次展开预算，以及单波次跳数/人数硬上限。单次声音采样最多检查 512 个最近候选、让 256 个丧尸直接接收，另选最多 8 个作为首轮转发种子。因此密集尸群不会无限递归广播；当预算耗尽时，已经收到目标的丧尸仍会追踪，但不会继续转发本轮广播。

故事阶段仍由故事系统管理（例如 `/dreamingfish story stage set <stageId>`）。丧尸只读取服务器权威阶段，不读取客户端界面状态。
