# DreamingFishCore 代码架构约定

本文档描述 1.20.1 分支的包结构和新功能放置规则。重构的目标是让一个功能的业务、事件、命令、数据包和客户端实现尽量聚合，同时保留网络与命令的统一注册入口。

## 顶层目录

```text
com.hhy.dreamingfishcore/
├─ DreamingFishCore.java      # 模组入口，只负责注册与启动
├─ init/                      # 公共启动编排，不承载业务逻辑
├─ server/                    # 服务器服务类功能，如登录、公告、权限、检查
├─ gameplay/                  # 玩家玩法类功能，如属性、NPC、任务、剧情
├─ content/                   # Forge 内容注册与物品实现
├─ client/                    # 跨功能复用的纯客户端代码与客户端缓存
├─ commands/                  # 仅保留统一命令注册入口
├─ network/                   # 仅保留统一网络频道与数据包注册入口
├─ common/                    # 跨系统配置和通用工具
├─ datagen/                   # 数据生成
├─ mixin/                     # Mixin 集中入口
└─ jei/                       # JEI 集成
```

`server` 表示“服务器服务领域”，不是 Java 物理侧限制。服务器服务如果有界面，应放在对应系统的 `client` 子包中。真正只能在客户端加载的类必须位于带有 `client` 含义的包，并继续使用 Forge 的 `Dist.CLIENT` 限制。

## 功能模块结构

功能根目录保留现有 Manager 和核心数据类，按需创建子包，不要求为了整齐创建空目录。

```text
gameplay/npc_system/
├─ NpcManager.java            # 核心管理类，暂不拆 Service/Repository
├─ NpcData.java               # 系统核心数据
├─ command/                   # 该功能的命令实现
├─ network/                   # 该功能的数据包实现
├─ event/                     # 该功能的 Forge 事件处理器
└─ client/                    # 该功能的界面、渲染和纯客户端状态
```

服务器服务功能采用相同规则：

```text
server/notice_system/
├─ NoticeManager.java
├─ PlayerNoticeDataManager.java
├─ command/
├─ network/
├─ event/
└─ client/
```

故事系统是全服共享剧情的唯一所有者，不再拆出平行的世界故事模块：

```text
gameplay/story_system/
├─ StoryManager.java          # 定义加载、世界状态、任务结果与兼容入口
├─ StoryWorldState.java       # 随世界保存的服务端权威运行状态
├─ StoryStageData.java        # 阶段定义与客户端阶段视图
├─ StoryTaskData.java         # 任务定义与客户端任务视图
├─ StoryTaskOutcome.java      # 进行中、成功、失败
└─ command/                   # 故事状态与服主阶段发布命令
```

`story_stage_data.json` 只描述阶段和任务定义，世界存档中的 `story/world_state.json` 只记录已经发生的状态。客户端任务包只包含当前玩家所需视图，不同步其他玩家UUID；旧客户端完成入口只能写兼容个人记录，不能结束全服任务或切换故事阶段。

## Manager 与缓存

- 现有 Manager 保留，不为了形式拆成 Service、Repository、Provider 等多层。
- 服务端缓存继续由所属系统的 Manager 管理，避免出现无归属的全局缓存。
- 跨功能客户端缓存统一放在 `client/cache/ClientCacheManager`。
- 仅供单个功能使用的纯客户端临时状态，可以放在该功能的 `client` 子包。
- ViewData 只在客户端展示字段与服务端完整数据确实不同时创建，不要求每个 Data 都配套 View。

## 命令

- 命令实现放在所属功能的 `command` 子包。
- 命令类只提供 `register(CommandDispatcher<CommandSourceStack>)`，不自行订阅 `RegisterCommandsEvent`。
- 所有命令由 `commands/DreamingFishCore_CommandManager` 统一注册。
- 新增命令时，需要同时在统一 Manager 中增加一次注册调用。

## 网络

- 数据包实现放在所属功能的 `network` 子包。
- 网络频道、协议版本和数据包 ID 顺序由 `network/DreamingFishCore_NetworkManager` 统一管理。
- 不要在功能模块中创建第二个 `SimpleChannel`。
- 已发布版本不要随意调整现有数据包注册顺序；新增包优先追加，协议不兼容时应明确升级协议版本。
- 所有客户端发往服务端的数据都视为不可信；处理器必须从网络上下文获取发送者并进行权限、状态、长度和频率校验。

## 内容注册

物品、创造栏、LootModifier 等保持独立注册，不创建包含所有内容的 `ContentInit`：

```text
content/
├─ item/DreamingFishCore_Items.java
├─ creative/DreamingFishCore_CreativeTabs.java
└─ loot/DreamingFishCore_LootModifiers.java
```

未来加入声音时，创建独立的 `content/sound/DreamingFishCore_Sounds.java`。

## 新功能检查清单

1. 先判断它属于 `server` 服务还是 `gameplay` 玩法。
2. 在功能根目录放 Manager 和核心数据，不提前制造无用抽象。
3. 事件、命令、数据包、客户端实现分别放入功能子包。
4. 在统一 CommandManager 或 NetworkManager 中完成注册。
5. 服务端数据包处理器完成权限与输入校验。
6. 至少执行一次 `gradlew compileJava`；涉及资源或注册内容时执行完整 `gradlew build`。
