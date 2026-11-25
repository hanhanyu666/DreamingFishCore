# 与 Xaero 小地图 / 世界地图的联动设计

> 目标：把本模组的**圈地（组织领地）**与**剧情地区（任务地点）**显示到 Xaero 的地图上。
> 状态：**技术路径已探明，尚未开始实现**。
> 探明日期：2026-11-23（对 Xaero 世界地图 1.40.11 / 小地图 26.6.0 / xaerolib 1.7.3 实测 javap）

---

## 一、先说结论（重要）

**Xaero 没有公开的"区域/领地"绘制 API。** 两个 jar 里 `xaero/**/api/**` 包中的类数量为 **0**，
`*Api*` 命名的类也只有两个无关类（`GuiMinimapInfoSettings`、`MinimapInterface`）。

官方文档（Modrinth 的 For Developers 一节）只提供了 **Maven 仓库与依赖坐标**，没有 API 说明。

所以"在 Xaero 地图上显示区域"只有三条路：

| 路线 | 做法 | 稳定性 | 额外依赖 |
| --- | --- | --- | --- |
| **A. 高亮器（推荐）** | 实现 Xaero 的 `ChunkHighlighter` 并用 `HighlighterRegistry.register(...)` 注册 | 中（内部类，但签名干净；版本升级需跟进） | 无（Xaero 已装即可） |
| B. 走 Open Parties and Claims | 把我们的领地镜像成 PAC 的 claim，Xaero 原生显示 | 高（Xaero 官方支持 PAC） | 需要装 PAC（客户端 + 服务端） |
| C. 路点近似 | 在区域四角/中心放路点 | 低（体验差：只是点，不是面） | 无 |

## 二、路线 A 的可行性证据（javap 实测）

```java
// xaero.map.highlight.HighlighterRegistry —— register 是 public ✓
public class HighlighterRegistry {
    public void register(AbstractHighlighter);       // ← 扩展点
    public List<AbstractHighlighter> getHighlighters();
    public void end();
}

// xaero.map.highlight.ChunkHighlighter —— public 抽象类，只需实现 4 个方法 ✓
public abstract class ChunkHighlighter extends AbstractHighlighter {
    protected abstract int[] getColors(ResourceKey<Level> dim, int chunkX, int chunkZ);   // 颜色（null=不高亮）
    public abstract boolean chunkIsHighlit(ResourceKey<Level> dim, int chunkX, int chunkZ);
    public abstract Component getChunkHighlightSubtleTooltip(ResourceKey<Level>, int, int);
    public abstract Component getChunkHighlightBluntTooltip(ResourceKey<Level>, int, int);
}
```

Xaero 自己给 Open Parties and Claims 做的集成（`xaero.map.mods.pac.highlight.ClaimsHighlighter`）
就是 `extends ChunkHighlighter` ✓ —— 说明这条路是**设计者的本意**，不是硬撬。

## 三、真正的技术缺口（三处）

### 缺口 1：怎么拿到 `HighlighterRegistry` 实例

`xaero.map.WorldMap` 里**没有**该字段（javap 查不到 `highlighter`/`registry`），
八成在 `WorldMapSession` 之类的内部类里。取法二选一：

- **客户端 mixin**：在 `HighlighterRegistry` 的构造器 `RETURN` 处把 `this` 存到我们的静态持有者里（最稳，两行）；
- **反射**：遍历内部类字段找实例（脆，取决于字段名）。

### ⚠️ 缺口 2（关键风险）：mixin 必须"装了 Xaero 才应用"

我们现在的 `dreamingfishcore.mixins.json` 是 `"required": true` 且**没有** `IMixinConfigPlugin`。
如果直接加一个把 `xaero.map.*` 当目标的 mixin，**没装 Xaero 的玩家会启动崩溃**（找不到目标类）。

解决：写一个 `IMixinConfigPlugin`，在 `shouldApplyMixin` 里判断
`net.neoforged.fml.ModList.get().isLoaded("xaeroworldmap")`，并把新增的 mixin 放进**独立的
mixins 配置文件**（例如 `dreamingfishcore.xaero.mixins.json`，只在该插件通过时才应用）。

### 缺口 3：区域数据从哪来、怎么到客户端

地图是**客户端**画的，但圈地与剧情地点是**服务端**数据 ✗。所以必须新增同步：

```java
record RegionSnapshot(String dimension, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                      String name, int argb, Kind kind)   // Kind = CLAIM / STORY / FRONTIER
```

- 服务端：从 `OrganizationManager`（领地）与 `TaskLocationManager`（任务地点/尸潮区域）汇总；
- 网络：新增 `region/snapshot` 包（**协议 0.33.0 → 0.34.0** ✗ 客户端必须一起换）；
- 客户端：缓存 → `ChunkHighlighter.getColors()` 里做**区块内点测试** + 颜色混合。

## 四、建议的实施顺序（每步可独立验收）

```
第 1 步（无风险，不依赖 Xaero）
  服务端汇总 RegionSnapshot + 新增包 + 客户端缓存
  + 一个 /dreamingfish map regions 命令，先用聊天栏打印"识别到几个区域"验证数据对不对

第 2 步（有风险，但可控制）
  mixin 插件 + 独立 mixin 配置 + HighlighterRegistry 捕获
  + ChunkHighlighter 子类（颜色/工具提示）
  + 只在 Xaero 存在时注册；不存在时整条链路不加载

第 3 步（打磨）
  领地/剧情/外缘三种颜色与图例、重叠区域混合色、小地图工具提示显示区域名与归属
```

## 五、依赖现状（已加进 build.gradle）

```gradle
repositories { maven { name = "XaeroMaven"; url = "https://chocolateminecraft.com/maven" } }

dependencies {
    compileOnly "xaero.minimap:xaerominimap-neoforge-1.21.1:26.6.0"
    compileOnly "xaero.map:xaeroworldmap-neoforge-1.21.1:1.40.11"
}
```

> **注意**：这个 Maven 仓库很慢（首次解析约 7~14 分钟 ✗）。jar 已进 Gradle 缓存，后续编译不再等。
> 这两个依赖**必须保持 compileOnly**：它们不随本模组分发，运行时有则由桥接类接管，没有则跳过。

## 六、待确认的设计选择

1. 三种区域是否都上地图？圈地（组织领地）✓、剧情地区 ✓、外缘（尸潮区域）建议也上（颜色区分）；
2. 领地边界是"区块矩形"还是"多边形"？当前数据模型是圈地区块集合 ✓ → 用**区块级矩形集合**最省事且与 Xaero 的 chunk 高亮粒度一致；
3. 是否允许玩家在客户端关掉本模组的区域层？（建议加一个客户端开关，避免和 PAC 等模组的图层打架）

---

## 七、实施记录（已完成）

### 7.1 数据链路

```
任务地点（TaskLocationManager）      → 剧情地区 / 外缘
组织领地（OrganizationManager +      → 领地
  EconomySystemBridge.findTerritory）
            ↓ MapRegionCollector.collect(server)
  Packet_MapRegionSnapshot（S2C，全量）
            ↓ 登录推送（MapRegionSync.onLogin）+ 客户端进服请求（MapRegionClientJoin）
  MapRegionClientCache（区块点测试 + 重叠取平均色 + 工具提示）
            ↓ Xaero 每区块询问
  DreamingFishHighlighter（ChunkHighlighter 子类）
```

### 7.2 文件清单

| 文件 | 作用 |
| --- | --- |
| `gameplay/map_region_system/MapRegion.java` | 区域模型（区块粒度、负坐标、`contains`） |
| `.../MapRegionCollector.java` | 汇总剧情地点 + 外缘 + 领地 |
| `.../MapRegionSync.java` | 登录时下发 / 全服广播 |
| `.../Command_MapRegions.java` | `/dreamingfish map regions`（2 级权限，验证数据） |
| `.../client/MapRegionClientCache.java` | 客户端快照 + 混合色 + 提示 |
| `.../network/Packet_MapRegionSnapshot.java` | 全量快照（S2C） |
| `.../network/Packet_MapRegionRequest.java` | 请求快照（C2S） |
| `integration/xaero/XaeroIntegration.java` | 总开关（**不含 Xaero 类型**，永远可安全加载） |
| `integration/xaero/XaeroInstaller.java` | 唯一接触 Xaero 类型的一层 |
| `integration/xaero/XaeroRegistryCapture.java` | 捕获注册表（用 Object 存，避免类加载） |
| `integration/xaero/DreamingFishHighlighter.java` | `ChunkHighlighter` 实现（四角颜色 + 提示） |
| `mixin/xaero/HighlighterRegistryMixin.java` | 在 `HighlighterRegistry` 构造末尾捕获实例 |
| `resources/dreamingfishcore.xaero.mixins.json` | **独立** mixin 配置，`required: false` |

### 7.3 防崩设计（回应"不装会不会崩"）

| 层 | 做法 |
| --- | --- |
| 依赖 | 只有 `compileOnly`，jar 里 0 个 xaero 类（已验证） |
| 代码 | 主代码 0 处引用 Xaero；`XaeroIntegration` 不含 Xaero 类型 |
| 加载 | 先判 `ModList.isLoaded("xaeroworldmap")`，再调用 `XaeroInstaller`（此时才会加载 Xaero 类） |
| mixin | 独立配置文件且 `required: false`：目标类不在只打警告 |
| 异常 | 安装过程 `catch (Throwable)` → 记一次日志后**本次游戏期间永久放弃** |
| 清单 | `neoforge.mods.toml` 里 Xaero 不作为必需依赖 |

### 7.4 怎么在游戏里验证

1. 客户端装 **Xaero 世界地图**（1.40.x）；
2. 进服，看日志应出现：`[map] 已把区域图层挂到 Xaero 世界地图（小地图会读取同一份数据）`；
3. 打开世界地图（M）→ 领地/剧情地点所在区块应显示**半透明填色**，鼠标悬停显示 `区域名（领地/剧情地区/外缘）`；
4. 数据不对时先用 `/dreamingfish map regions` 检查服务端汇总（区域总数、各类几个、前 10 个范围）。

### 7.5 已知限制

- 区域是**区块矩形**：斜边/多段领地会显示成外接矩形；
- 重叠时取平均色（不叠加 alpha），三块以上重叠会偏灰；
- 小地图的区块高亮依赖 Xaero 自己的"世界地图→小地图"数据流，若某版本不显示，以世界地图为准；
- 目前**没有**客户端关闭开关（后续可加）。

