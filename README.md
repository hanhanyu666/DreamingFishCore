# DreamingFishCore

DreamingFishCore 是为梦鱼服制作的 Minecraft Forge 1.20.1 核心模组，提供综合性的服务器管理功能、服务器玩法、角色成长、终端界面、剧情任务、蓝图合成限制，且制作了一套全新的沉浸式 UI。本分支是 1.21.1 版功能的 1.20.1 移植版。

为了方便模组的维护，本项目从原 [QingMo-A/EconomySystem](https://github.com/QingMo-A/EconomySystem) 中分离出专属于 DreamingFish 服务器的核心功能，并围绕 DreamingFishCore 的定位进行独立维护，原 EconomySystem 继续维护商店、领地等内容，该模组将继续为梦鱼服的后续玩法进行开发。

### 作者 (Author):

- [QingMo](https://github.com/QingMo-A)
- [HanHanYu](https://github.com/hanhanyu666)

## 核心特性

- 梦屿终端：按键打开服务器终端，集中展示个人档案、公告、故事进展、排行、成就、背包与设置入口。
- 玩家成长：提供综合等级、经验、排行、称号、探索统计等长期成长数据。
- RPG 属性：包含勇气、体力、感染、复活点数、肢体伤害等扩展状态。
- 蓝图系统：玩家需要通过蓝图学习物品配方，未解锁的配方会被合成限制拦截。
- 剧情与任务：支持故事阶段、任务数据、故事碎片和随记本等内容承载。
- NPC 系统：提供 NPC 数据、关系、对话、想法与警告规则。
- 公告系统：服务器可发布公告，客户端终端内可查看公告列表与详情。
- 自定义内容：包含药品、复活护符、启程锦鲤、故事碎片、随记本等服务器道具。

## 玩法系统

### 梦屿终端

梦屿终端是玩家进入服务器后最常用的界面入口。终端使用独立的平板式 UI，展示玩家等级、称号、排行、身体状态、探索数量、蓝图数量和服务器公告。

### 蓝图合成

蓝图系统会收集工作台配方，并根据玩家已学习的蓝图决定是否允许合成。玩家可以通过蓝图物品解锁指定物品的制作权限，死亡后会遗忘已学习蓝图，需要重新探索和获取。

### 角色属性

模组扩展了玩家状态，包含感染、勇气、体力、肢体健康和复活相关数据。这些系统用于支撑更偏生存与角色扮演的服务器规则。

### 剧情任务

故事阶段、任务和碎片物品用于承载服务器剧情内容。随记本和故事碎片可作为玩家推进剧情、收集线索和记录世界信息的道具基础。

### 公告与管理

服务器公告会同步到客户端终端，玩家可以查看公告列表、未读状态和公告详情。模组也提供多组服务端命令，用于检查数据、管理公告、NPC、任务、等级、排行和称号。

## 物品与内容

- 启程锦鲤
- 蓝图
- 故事碎片
- 随记本
- 简易急救包 / 高级急救包 / 专业急救包
- 复活护符
- 基因复苏药剂

## 技术信息

- Minecraft: 1.20.1
- Mod Loader: Forge 47
- Java: 17
- Mod ID: `dreamingfishcore`
- License: GNU Affero General Public License v3.0

## 构建与运行

```bash
./gradlew build
./gradlew runClient
./gradlew runServer
```

Windows PowerShell 下也可以使用：

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

## 配置与数据

模组会在 Forge 配置目录下创建 `dreamingfishcore` 配置文件夹，用于保存服务器侧配置和运行数据。部分系统会使用玩家持久化数据、服务端保存数据或配置文件来同步和记录状态。

## 开发说明

DreamingfishCore 的包名为 `com.hhy.dreamingfishcore`。主要模块位于：

- `core/`：玩法核心数据与规则
- `screen/`：客户端 UI 与终端界面
- `network/`：客户端与服务端同步包
- `commands/`：服务器命令
- `item/`：自定义物品与创造栏
- `events/`：Forge 事件处理
- `mixin/`：原版行为注入
- `loot/`：全局掉落修改器
- `server/`：服务器侧数据管理
