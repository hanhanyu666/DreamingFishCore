# 故事系统代码阅读指南

这份文档面向刚掌握 Java 基础、准备阅读当前故事系统实现的开发者。它解释代码之间的关系，不替代源码中的具体注释。

## 一、先记住两类数据

故事系统最重要的设计，是把“设计内容”和“世界历史”分开。

### 1. 故事定义

故事定义来自：

```text
config/dreamingfishcore/story_stage_data.json
```

它回答：

- 有哪些阶段？
- 阶段叫什么？
- 有哪些任务？
- 任务在终端显示什么文字？
- 进入阶段时是否默认发布任务？

对应 Java 类：

- `StoryStageData`
- `StoryTaskData`

修改任务名称属于修改定义，不代表世界里的任务重新开始。

### 2. 世界运行状态

世界状态保存在当前世界目录下：

```text
data/dreamingfishcore/story/world_state.json
```

它回答：

- 当前处于哪个阶段？
- 哪些任务已经发布？
- 任务成功还是失败？
- 任务什么时候结算？
- 哪些玩家在结算时取得了个人记录？
- 哪些世界旗标已经成立？
- 当前运营轮次和结局是什么？

对应 Java 类：

- `StoryWorldState`
- `StoryWorldState.TaskProgress`
- `StoryWorldState.OperationRound`

这个文件属于世界历史。即使后来修改了配置文案，已经发生的成功或失败仍保留在这里。

## 二、建议阅读顺序

不要一开始就从 700 多行的 `StoryManager` 第一行硬读到底。推荐顺序：

1. `StoryTaskOutcome.java`
2. `StoryTaskData.java`
3. `StoryStageData.java`
4. `StoryWorldState.java`
5. `StoryManager.java`
6. `Command_Story.java`
7. `Packet_SyncFullTaskData.java`
8. `WorldDataLifecycleEvents.java`
9. `StoryWorldStateTest.java`

先理解数据长什么样，再看管理器如何组合数据，会容易很多。

## 三、每个核心类负责什么

### StoryTaskOutcome

这是一个枚举：

```java
ACTIVE
SUCCEEDED
FAILED
```

枚举适合表示有限且互斥的状态。一个任务不能同时成功和失败。

`isResolved()` 把状态分成两组：

- `ACTIVE`：尚未结算。
- `SUCCEEDED`、`FAILED`：已经结算。

因此失败任务也会进入全服进度，而且不能正常重开。

### StoryTaskData

这个类同时承担两种用途：

1. 从配置读取任务名称、ID、内容。
2. 生成发给某个客户端的显示副本。

其中 `transient` 字段非常重要：

```java
private transient boolean completed;
private transient boolean failed;
```

这些字段不属于配置定义，而是查询时从 `TaskProgress` 临时计算出来的。它们不会被 Gson 写回配置 JSON。

`completed` 的含义是“已结算”，成功和失败都为 `true`；`failed` 用来进一步区分失败并显示红色。

### StoryStageData

阶段包含自己的 ID、编号、名称、描述、任务列表和可选怪物倍率。

字符串 ID 用于稳定引用：

```text
dreamingfishcore:afterdream
```

数字编号主要用于排序和兼容旧界面。显示名称可以随文案修改，但字符串 ID 一旦进入正式世界就不应随意修改。

阶段对象发送给客户端前会复制一次，而且任务列表只放入已发布任务。这样客户端看不到尚未发布的任务内容。

### StoryWorldState

这是纯数据和规则类。它不知道文件路径，也不知道 Minecraft 玩家实体。

大部分修改方法没有写 `public`，例如：

```java
boolean activateTask(String taskKey)
```

这种写法叫包级可见性。只有同一个 `story_system` 包里的代码可以直接调用，外部玩法模块应通过 `StoryManager` 调用。

这样可以避免 NPC 系统绕开管理器，直接修改世界存档对象。

### StoryManager

它是整个系统的门面和唯一运行时所有者。

它负责：

- 读取配置。
- 校验阶段与任务 ID。
- 读取世界状态。
- 将配置定义和世界状态组合成客户端视图。
- 提供发布任务、结算任务、切换阶段等 API。
- 标记数据需要保存。
- 在加载失败时进入只读保护。

它使用大量 `static` 字段和方法，表示服务器进程中不需要创建多个 `StoryManager` 对象。

修改方法使用：

```java
public static synchronized boolean resolveTask(...)
```

`synchronized` 表示同一时刻只有一个线程可以进入这个静态修改方法，避免两个事件同时结算同一个任务时互相覆盖。

## 四、服务器启动时发生什么

入口位于 `WorldDataLifecycleEvents`。

启动顺序如下：

```text
ServerStartingEvent
    -> StoryManager.loadWorldData(server)
        -> 清理旧静态缓存
        -> 读取 story_stage_data.json
        -> 校验定义并建立索引
        -> 读取当前世界 world_state.json
        -> 执行旧 schema 迁移
        -> 检查当前阶段仍存在于定义中
        -> 发布当前阶段的默认任务
```

为什么先读定义，再读状态？

世界状态只保存当前阶段 ID。读完状态以后，系统必须用定义索引确认这个阶段确实存在。

如果配置或存档损坏，`writesEnabled` 会变成 `false`。这叫只读保护：允许管理员查看错误，但不允许默认空状态覆盖损坏文件。

## 五、一个任务从发布到结算的完整流程

假设配置里存在任务：

```text
dreamingfishcore:station_defense
```

### 第一步：发布

未来任务脚本或运营工具调用：

```java
StoryManager.activateTask("dreamingfishcore:station_defense");
```

管理器先检查配置中确实存在这个任务，然后在 `StoryWorldState.taskProgress` 中创建：

```text
station_defense -> ACTIVE
```

任务从这一刻开始：

- 出现在客户端任务列表。
- 进入全服和个人进度的分母。
- 可以被成功或失败结算。

### 第二步：任务执行器运行

未来的任务执行器负责 Minecraft 玩法，例如：

- 检查玩家是否进入任务区域。
- 生成尸潮。
- 计算防守时间。
- 判断目标 NPC 是否存活。
- 判断成功或失败。
- 收集结算瞬间区域内的生存/冒险玩家。

这些功能目前还没有实现，它们不应该写进 `StoryWorldState`。

### 第三步：结算

执行器收集参与者以后调用：

```java
StoryManager.resolveTask(
        "dreamingfishcore:station_defense",
        StoryTaskOutcome.FAILED,
        participants
);
```

状态类会依次检查：

1. outcome 只能是成功或失败。
2. 任务必须已经发布。
3. 任务不能已经结算。
4. 先完整收集参与者。
5. 再一次性写入结果、时间和参与者。

步骤 4 在步骤 5 前面，是为了避免参与者收集器中途报错后留下半结算状态。

### 第四步：进度变化

失败后：

- 全服已结算任务数增加 1。
- 在场玩家的个人记录增加 1。
- 客户端显示红色 `FAILED`。
- 再次结算返回 `false`。
- 系统本身不会自动施加尸潮进化或线索损失，具体代价由未来正式任务决定。

## 六、客户端视图是怎么生成的

服务器调用：

```java
StoryManager.getStagesForPlayer(player.getUUID())
```

对于每一个配置任务：

1. 在 `StoryWorldState` 中查找同 ID 的 `TaskProgress`。
2. 没找到，说明没发布，不加入阶段视图。
3. 找到后复制 `StoryTaskData`。
4. 把成功/失败、在场人数和当前玩家个人记录填入副本。
5. 通过 `Packet_SyncFullTaskData` 发给对应玩家。

服务器不会把所有参与者姓名和 UUID 发给客户端，只发送人数和当前玩家自己的状态。

## 七、双进度如何计算

假设某阶段已经发布 5 个任务：

- 2 个成功。
- 1 个失败。
- 2 个仍在进行。

全服进度为：

```text
(2 + 1) / 5 = 60%
```

某玩家只在其中 2 个已结算任务的区域内，则个人进度为：

```text
2 / 5 = 40%
```

如果服主又发布 1 个任务，分母变成 6，所以进度可以下降。这不是回档，而是世界出现了新的调查内容。

## 八、世界旗标是什么

世界旗标是全服共享的布尔事实，例如：

```text
dreamingfishcore:baizhi_missing
```

存在代表 `true`，不存在代表 `false`。它适合保存不需要完整任务结果的数据：

- 某个 NPC 是否已经失踪。
- 某扇剧情门是否永久开启。
- 玩家是否把资料交给逐光会。
- 某个隐藏区域是否被发现。

世界旗标不保存详细结果和参与者；需要成功、失败和参与名单时应该使用任务状态。

## 九、为什么很多查询都返回副本

例如 `getTaskProgress()` 不直接返回内部对象，而是调用 `copy()`。

如果直接返回内部对象，外部代码可能这样做：

```java
manager.getProgress().participantNames.clear();
```

这会绕开所有校验并直接破坏存档状态。返回副本或不可修改集合叫防御性复制，是保护状态所有权的常见做法。

## 十、当前仍未实现的部分

故事底层已经能保存和同步结果，但下面这些属于下一层：

- 正式任务 JSON。
- 任务区域和刷怪点定义。
- 尸潮、防守、搜索、撤离等执行器。
- 自动收集结算区域内玩家。
- NPC 对话生成引导项。
- 客户端在任务发布、结算和阶段切换后的实时刷新事件。
- 运营内容包热重载。
- 世界历史日志和调查板。

因此，阅读代码时可以把当前系统理解成“故事状态数据库 + 规则入口”。具体玩法脚本会作为调用方接在它上面，而不应该重新创建另一套故事进度。
