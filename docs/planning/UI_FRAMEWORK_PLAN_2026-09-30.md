# 梦屿 UI 框架：设计与迁移计划

状态：2026-09-30 计划稿，待服主确认。确认后按阶段实施，每个阶段结束时都可以编译、运行和发版。

## 1. 目标

为本模组建立一套统一的客户端 UI 框架，并把现有全部界面、HUD 和原版界面改造迁移到框架上。

**高性能**（阶段 0 先测出现状基线，再定最终数值）：

- 界面静止时（无动画、无输入、数据未变）不重新布局、不重建节点，每帧只执行绘制。
- 同一层内的形状合并为一次提交；迁移后每个界面每帧的 GPU 提交次数不高于迁移前。
- 渲染热路径每帧不分配新对象（不在绘制中创建 lambda、临时列表或拼接字符串）。
- 长列表只构建和绘制可见项。

**易用性**：

- 页面代码中不出现手算坐标和手动登记的点击区域。
- 颜色、间距、圆角、字号、动画时长只从主题取值，不写字面量。
- 新增一个常规页面只需要写一个 Page 类；列表页迁移后代码量预计降到原来的三分之一左右。
- 修改主题 JSON 后在游戏内热重载即可看到效果。

**现代化观感**：抗锯齿圆角、柔和阴影、毛玻璃、弹簧动效、页面转场、惯性滚动；能支撑“手机 App 式”的复杂界面。

## 2. 已确认的决策

| 决策 | 结论 | 原因 |
| --- | --- | --- |
| 渲染方式 | 原生渲染（GuiGraphics / RenderSystem / 自定义 core shader），不嵌入浏览器或 HTML 引擎 | 大量 UI 是 HUD，且需要原生物品、模型和 shader；CEF 离屏上传开销、Chromium 分发体积、中文输入法问题都不适合 |
| 文字 | 统一走原版 Font 接口，由 Modern UI 负责实际渲染；框架不自研字体 | 客户端整合包内置 Modern UI 3.13.0.1，它已接管字体渲染、测宽和换行，并开启了 2D SDF 文字 |
| 是否基于 Modern UI 的 View 体系 | 否。只借用它的文字引擎，不依赖它的 API | 它的 View 体系运行在独立的 UI 线程（Looper 模型），与主线程上的客户端缓存、物品渲染和 HUD 集成成本高；它的 API 不是稳定的公开接口；框架保持零依赖，没装 Modern UI 时只是退化为原版位图字 |
| 框架形态 | 放在本模组内的独立包 `client.ui.framework`，不引用任何玩法代码 | 起步最快；以后需要时可以原样抽成独立库 |
| 两种用法 | 界面用保留式组件树；HUD 用即时绘制画布；两者共用渲染核心、主题和动画 | HUD 每帧变化且对性能敏感，不强行套组件树 |
| 开发方式 | Java 流式 DSL 描述界面，主题用 JSON 并支持热重载；暂不做 XML 模板 | 类型安全、便于重构；模板层以后按需补 |
| 设计流程 | 用 HTML/CSS 在 `design_previews/` 做视觉原型，调好的参数原样搬进主题 | 两边优势都拿到 |

## 3. 现状盘点

| 类别 | 规模 | 主要文件 |
| --- | ---: | --- |
| 终端 ServerScreenUI | 6753 行 | `ServerScreenUI_Screen`（4779 行，含全部页面、坐标和点击处理）、`_PageRenderer`、`_RendererUtils`、`_RoundedRenderer` |
| 独立界面 | 约 3800 行 | 登录、NPC 对话、故事书目录、故事碎片、复活符、公告详情、`ModernSelectionScreenUi`、加载界面与加载过渡 |
| HUD 与覆盖层 | 约 8200 行 | 快捷栏、生命体征与肢体线框、任务地点 HUD、服务器信息与效果栏、通知、沉浸式聊天（1635 行）、标记、终端透镜 |
| 原版界面 mixin | 约 3850 行 | 标题（1094）、死亡（880）、断线（427）、连接/进度/等待/加载、多人与单人列表及条目、聊天与命令补全、加载遮罩 |
| 共享 UI 基础设施 | 约 4000 行 | `client/ui/components`（含 `newUI`）、`client/ui/render`、`client/ui/util` |

主要问题：

- 全部代码中有 1057 处颜色字面量、550 种不同颜色；`ServerScreenUI_Screen` 一个文件就有 239 处。
- 动画有 4 套独立实现（`UiAnimation`、`newUI/AnimationController`、`HudPalette` 中的缓动函数、`TextAnimation`），34 个 UI 文件直接读取系统时间。
- 按钮至少有 3 套实现；`newUI` 的 VBox/HBox 除 `Screen_Test` 外没有被使用。
- 终端页面在 render 时登记 7 种 `XxxClickArea` 记录，再由约 300 行的 `mouseClicked` 逐个判断；卡片尺寸等大量数值写死。
- 已有值得保留的性能经验：`drawManaged` 合批、`GuiQuadBatchRenderer`、`RetainedGuiBuffers`、`GameplayHudBatchRenderer` 的单次 HUD 提交。

## 4. 框架架构

### 4.1 分层

| 层 | 内容 | 使用者 |
| --- | --- | --- |
| 页面与组件 | Page、Navigator、Button、Card、VirtualList 等 | 界面 |
| 节点树 | UiNode、Flex 布局、事件分发、焦点、数据绑定 | 界面 |
| HUD 画布 | HudCanvas（即时绘制）、HudLayers | HUD 与覆盖层 |
| 渲染核心 | UiCanvas、SDF 形状批、分层合批、文字、物品与头像、裁剪栈、离屏目标、模糊 | 以上全部 |
| 基础 | 主题 Theme、帧时钟 UiClock、动画 Tween / Spring | 以上全部 |

### 4.2 包结构

```
client/ui/framework/
  core/     UiClock、UiContext（当前缩放、主题、字体）、资源生命周期
  theme/    Theme、颜色角色、间距/圆角/阴影/字号/动效档位、JSON 加载与热重载
  anim/     Easing（含 cubic-bezier）、Tween、Spring、AnimatedFloat/AnimatedColor、Stagger
  render/   UiCanvas、ShapeBatch（SDF）、LayerBatcher、TextPainter、ItemPainter、
            PlayerFacePainter、ClipStack、RenderTargetPool、BackdropBlur
  layout/   FlexLayout、尺寸测量、布局缓存
  node/     UiNode、NodeStyle、脏标记、Binding
  input/    命中测试、指针事件、指针捕获、焦点、键盘、手势（拖拽/甩动）
  widget/   Box/Row/Column/Stack、Text、Icon、Image、Button、Card、ScrollView、VirtualList、
            Tabs、Segmented、Badge、Progress、Toggle、Slider、TextField、Tooltip、
            Modal、BottomSheet、Toast、ItemSlot、PlayerModel
  nav/      Navigator（页面栈、转场、深链接）、Page
  screen/   FrameworkScreen（Screen 基类）、FrameworkHost（挂到原版 Screen 上）
  hud/      HudCanvas、HudLayers（统一注册与单次提交）
  debug/    检查器叠加层、性能计数
```

框架节点统一叫 `UiNode`，避免与 `net.minecraft.network.chat.Component` 冲突。

### 4.3 渲染核心

**SDF 形状着色器**：一个 core shader 负责所有矩形类图形。每个元素一个四边形，顶点携带尺寸、四角独立圆角、描边宽度与颜色、填充（纯色、线性或径向渐变）、阴影的模糊半径与偏移。片元中计算圆角矩形距离场，任意缩放下都抗锯齿；外阴影用误差函数近似高斯模糊，内阴影和发光同理。通过 `RegisterShadersEvent` 注册，与现有的 `hud_body_outline`、`black_hole_lens` 方式相同。

**分层合批**：绘制命令按层收集。同一层内先画全部形状，再画图片与物品，最后画文字，每类一次提交。绝对定位元素、弹窗、浮层和拖拽中的元素自动开新层。这把现有 `drawManaged` 与 `GameplayHudBatchRenderer` 中手工维护的合批经验变成框架的默认行为。

**文字 TextPainter**：

- 只通过 `Font` / `StringSplitter` 测量、换行和绘制，实际渲染由 Modern UI 完成。
- 支持 `Component`（含翻译键与样式）、最大行数、省略号、对齐和字号层级。字号通过 PoseStack 缩放实现，Modern UI 的 SDF 文字在非整数缩放下保持清晰。
- 换行结果按（文本、样式、宽度）缓存；资源重载和切换语言时清空，因为 Modern UI 的字体可能随之变化。
- 宽度一律实际测量，不假设字宽。

**原生内容**：物品（沿用 `HotbarItemRenderCache` 的经验）、玩家头像（`PlayerFaceBatchRenderer`）、实体与玩家模型、自定义 shader 绘制（如肢体线框描边）都作为可以参与布局的节点。

**裁剪**：矩形裁剪用 scissor 栈；圆角裁剪对形状在 shader 中按裁剪区距离场处理，对文字和物品退化为矩形裁剪。

**离屏与效果**（阶段 3）：渲染目标池，支持整组半透明、页面转场快照、局部毛玻璃（复制当前画面、降采样、Kawase 模糊、按面板形状采样）。不依赖原版或 Modern UI 的全屏模糊。

**像素对齐**：布局用浮点 dp 计算，绘制时对齐到物理像素，保证 1px 描边清晰。

### 4.4 节点树与布局

- `UiNode` 保存样式、子节点、布局结果和交互状态（hover、pressed、focused、disabled）。
- Flex 布局支持：方向、gap、padding、margin、grow/shrink/basis、主轴与交叉轴对齐、换行、绝对定位、最小/最大尺寸、宽高比。
- 只有被标脏的子树才重新布局；不影响尺寸的样式变化（颜色、透明度）只重绘、不重排。
- 单位 dp = GUI 缩放后的像素 × 框架 UI 缩放系数。不再使用固定的 640×360 虚拟画布，改为响应式布局，按可用宽度分为紧凑、常规、宽屏三档。
- 兼容 Modern UI 的 `useNewGuiScale`，不假设 GUI 缩放只有 1 到 4 这几个整数档。

### 4.5 输入与事件

- `FrameworkScreen` 把鼠标按下、抬起、拖拽、滚轮，以及键盘和字符输入转交给节点树。
- 命中测试按绘制顺序的逆序在布局结果上进行；事件从目标节点向上冒泡，可被拦截。
- 指针捕获：拖动滑块或滚动条时，鼠标移出元素仍持续接收事件。
- hover 状态每帧统一计算一次。
- 焦点管理与 Tab 切换；Esc 交给 Navigator 执行返回。
- 滚动：滚轮平滑滚动，拖拽甩动带惯性，越界时弹簧回弹。

### 4.6 状态与数据绑定

- 采用拉取式绑定：`Binding.of(() -> 客户端缓存中的值)`。已挂载的节点每帧（或每 tick）读取一次，值变化（equals 比较）时才标脏。这契合现有“网络包 → 客户端缓存”的数据流，不需要改动服务端和网络代码。
- 列表按 key 比对：数据变化时只增删改对应的项，保留未变项的状态与动画。
- 组件就是返回 `UiNode` 的普通 Java 方法，靠组合复用。

### 4.7 动画

- `UiClock`：每帧只取一次时间，所有动画基于同一时间戳；支持全局慢放（调试用）。窗口失焦时 Modern UI 会限到 30 帧，动画按时间计算，不受影响。
- Tween：时长加缓动曲线，曲线参数与 CSS 的 cubic-bezier 相同。
- Spring：刚度与阻尼，用于 hover、按下、拖拽释放等交互反馈。
- 样式过渡：为节点声明 `transition(属性, 动效档位)`，属性变化时自动平滑。
- 挂载与卸载动画：节点移除时先播放退出动画再真正移除；列表支持依次入场。
- 合并现有的 `UiAnimation`、`AnimationController`、`HudPalette.easeOutCubic/approach`、`TextAnimation`。

### 4.8 主题

- 颜色按用途命名：`surface`、`surfaceRaised`、`surfaceOverlay`、`border`、`text`、`textMuted`、`accent`、`success`、`warn`、`danger` 等；HUD 另有一组（骨白、琥珀、感染绿等，来自 `HudPalette`），与终端共用基础色阶。
- 档位：间距 4/8/12/16/24/32，以及圆角、阴影高度、字号层级、动效时长与曲线。
- 默认值在 Java 中定义，`config/dreamingfishcore/ui_theme.json` 可以覆盖；开发时按键热重载。
- 合并 `ServerScreenUI_Screen` 中的颜色常量、`HudPalette`、`NotificationTheme`、`UiButtonStyle`。

### 4.9 界面与导航

- `FrameworkScreen`：Screen 基类，负责生命周期、输入转发和背景。背景可以选择沿用 Modern UI 的模糊背景（与客户端其他界面一致），也可以完全自定义（如终端），避免与 Modern UI 的淡入和模糊效果叠加。
- `Navigator`：页面栈、推入与返回转场（滑动、淡入、共享轴）、深链接（例如点击通知直接打开终端里的某条公告）。
- `FrameworkHost`：挂到原版 Screen 上，保留原版逻辑，只替换绘制和交互，用于迁移原版界面 mixin。
- Tooltip 默认走原版 `renderTooltip`，由 Modern UI 统一样式，与整个客户端保持一致。

### 4.10 HUD 画布

- `HudCanvas` 提供与 `UiCanvas` 相同的形状、文字、图标、物品绘制接口，即时模式，不建节点树。
- `HudLayers` 在一次 `RenderGuiEvent.Post` 中按优先级绘制所有 HUD 层，一次托管提交，取代 `GameplayHudBatchRenderer`。
- 通知栈这类需要进出场动画和自动排版的覆盖层，可以挂一棵小型节点树。
- 自定义 shader 内容（肢体线框描边等）通过画布回调接入，不改其实现。

### 4.11 调试工具

- 按键打开检查器：显示布局边框和 padding、悬停节点的尺寸与样式、节点树。
- 性能计数：每帧 GPU 提交次数、层数、布局耗时、绘制耗时。
- 主题热重载、动画慢放。

### 4.12 API 示意

以下仅为示意，类名和方法名以阶段 2 试点结果为准。

```java
public final class HistoryPage extends Page {
    @Override
    protected UiNode build() {
        return Column.of(
                Text.of(Component.translatable("ui.dreamingfishcore.terminal.history"))
                        .variant(TextVariant.TITLE),
                VirtualList.of(WorldHistoryCache::entries, HistoryEntry::id, this::historyCard)
                        .gap(Space.SM)
                        .empty(() -> EmptyState.of("暂无世界历史"))
                        .grow(1)
        ).gap(Space.MD).padding(Space.LG);
    }

    private UiNode historyCard(HistoryEntry entry) {
        HistoryPresentation p = HistoryPresentation.of(entry);
        return Card.of(
                Row.of(
                        Icon.of(p.icon()).color(p.color()),
                        Column.of(
                                Text.of(p.title()).maxLines(1),
                                Text.of(p.subtitle()).variant(TextVariant.CAPTION).maxLines(2)
                        ).gap(Space.XS).grow(1)
                ).gap(Space.MD).alignItems(Align.CENTER)
        ).onClick(() -> navigator().push(new HistoryDetailPage(entry)))
         .hoverLift();
    }
}
```

HUD 示意：

```java
HudLayers.register(HudLayerId.VITALS, HudPriority.STATUS, (canvas, frame) -> {
    canvas.panel(x, y, w, h, Surface.HUD);
    canvas.bar(x + 8, y + 8, w - 16, 4, vitals.health(), HudColor.BONE);
    canvas.text(vitals.label(), x + 8, y + 16, TextVariant.CAPTION);
});
```

## 5. 兼容性

| 对象 | 影响 | 处理 |
| --- | --- | --- |
| Modern UI 文字引擎 | 接管字体渲染与测宽（`MixinFontRenderer`、`MixinStringSplitter`、`MixinFontManager`） | 文字只走 Font 接口；宽度一律实测；资源重载时清缓存 |
| Modern UI 界面增强 | 为所有 Screen 加背景模糊与 200ms 淡入（`MixinScreen`） | `FrameworkScreen` 统一接管背景，需要时显式沿用 |
| Modern UI 输入框 | mixin 了 `EditBox` | `TextField` 内嵌无边框的原版 `EditBox` 负责编辑、选区和输入法，外框由框架绘制 |
| Modern UI 缩放 | `useNewGuiScale` | 布局不假设缩放档位 |
| Modern UI 渲染钩子 | mixin 了 `RenderSystem`、`GameRenderer` | GL 状态只通过 RenderSystem 修改并及时还原 |
| ImmediatelyFast | 改变 GUI 批处理方式 | 自定义 RenderType 的提交顺序在整合包环境专项测试 |
| Sodium / Iris | 光影包可能影响 core shader 与 GL 状态 | 开启光影包测试 GUI 与 HUD |
| JEI / Xaero 小地图 | 屏幕边缘与 HUD 位置可能重叠 | HUD 布局预留安全区，并允许配置 |
| 首次资源加载 | 加载遮罩渲染时自定义 shader 可能尚未加载 | 渲染核心在 shader 不可用时自动退回原版 `fill` 绘制 |

测试环境分两套：

- 开发环境：`run/mods` 中的 Modern UI。
- 本地客户端整合包 `D:/Desktop/mc/.minecraft/versions/DreamingFish-DreamingHaven-1.21.1`：Modern UI、ImmediatelyFast、Sodium、Iris、JEI、Xaero 等共 116 个文件。

## 6. 分阶段计划

每个阶段结束时都可以编译、运行和发版。工作量用 S/M/L/XL 相对表示。

### 阶段 0：准备与基线（S）

- 提交当前工作区中未提交的 HUD 改动，从 `1.21.1` 开出 `ui-framework` 分支。
- 开发环境性能计数工具：统计每帧 GPU 提交次数与 UI 耗时，记录终端各页、HUD、通知在迁移前的基线。
- 颜色审计：把 550 种颜色归并到主题角色，产出映射表。
- 视觉规范：在 `design_previews/` 用 HTML 做一版主题样张（色板、字号、间距、圆角、阴影、动效曲线），确认后作为主题默认值。

交付：基线数据、颜色映射表、主题样张。游戏内无可见变化。

### 阶段 1：基础与渲染核心（M）

- `theme`、`core/UiClock`、`anim`；把现有 4 套动画实现改接到新动画库，行为保持不变。
- SDF 形状着色器、`ShapeBatch`、`LayerBatcher`、`UiCanvas`、`TextPainter`、`ClipStack`、物品与头像绘制。
- 保持 `UiPanelRenderer` 的接口不变，内部换成 SDF 实现，15 个调用它的文件自动受益。
- 调试叠加层中的性能计数部分。
- 单元测试：缓动与弹簧数学、颜色插值、文字换行（使用假测量器）。

交付：现有界面的圆角和阴影观感提升，GPU 提交次数不高于基线。

### 阶段 2：节点树、布局、输入与试点（L）

- `node`、`layout/FlexLayout`、`input`（命中测试、冒泡、捕获、焦点）、`ScrollView`（惯性与回弹）、`VirtualList`。
- `FrameworkScreen` 与基础组件：Box/Row/Column/Stack、Text、Icon、Image、Button、Card、Badge、Progress、Divider。
- 检查器的布局边框与节点信息。
- 单元测试：Flex 布局规则、命中测试、滚动物理。
- **试点**：用框架重写 `Screen_NoticeDetail`（369 行，包含滚动、长文本和按钮），根据试点结果修订 API，然后冻结第一版。

交付：用新框架写成的公告详情页上线，API 定型。

### 阶段 3：交互组件、导航与动效（L）

- TextField、Toggle、Slider、Tabs、Segmented、Tooltip、Modal、BottomSheet、Toast、ItemSlot、PlayerModel。
- `Navigator`（页面栈、转场、深链接）；挂载与卸载动画、依次入场、样式过渡、hover 弹簧。
- 渲染目标池、整组半透明、局部毛玻璃。
- 主题 JSON 热重载。
- 使用指南 `docs/UI_FRAMEWORK_GUIDE.md` 初版。

交付：组件库完整，可以编写“手机 App 式”界面。

### 阶段 4：终端迁移（XL）

- 用 `Navigator` 重建终端外壳（平板外框、顶栏、状态栏、展开动画），每个模块一个 Page 类。
- 迁移顺序：历史 → 市场 → 公告 → 帮助 → 个人资料（含 Rank 管理）→ NPC 短信 → 剧情任务 → 仪表盘。
- 过渡期新旧终端并存：新终端先由开发按键或配置开关打开，全部页面完成并验收后切为默认，再删除旧实现。
- 经济系统入口：`ServerScreenUiEconomyBridgeMixin` 依赖旧类的 `handleLeftButtonClick` 与 `selectedLeftButtonIndex`。迁移后改为在新终端的导航中直接调用 `EconomySystemUiBridge`，并删除该 mixin。
- 迁移时保留原有的数据请求（`init` 中发送的各类网络包）与业务调用，只替换展示层。
- 完成后删除 `ServerScreenUI_Screen`、`_PageRenderer`、`_RendererUtils`、`_RoundedRenderer`。

交付：新终端上线，6753 行旧实现移除。

### 阶段 5：其他独立界面（L）

- 登录（密码输入与输入法）、NPC 对话、故事书目录、故事碎片、复活符、`ModernSelectionScreenUi`、加载界面与加载过渡。

### 阶段 6：HUD 与覆盖层（L）

- 由 `HudLayers` 取代 `GameplayHudBatchRenderer`，逐个改用 `HudCanvas`：快捷栏、生命体征与肢体线框、任务地点 HUD、服务器信息与效果栏、系统消息、终端透镜、群系发现提示、标记的 2D 标签部分。
- 通知改为小型节点树（进出场动画、自动排版）。
- 沉浸式聊天：文字排版改用 `TextPainter`，保持现有行为，`ImmersiveChatManagerTest` 继续通过。
- 自定义 shader 部分（`HudModelOutline` 等）保持原实现，通过画布回调接入。
- 每一项都对照基线，确认性能不退化。

### 阶段 7：原版界面 mixin（M）

- 用 `FrameworkHost` 或 `UiCanvas` 改写：标题、死亡、断线、连接、进度、各类等待与加载界面、多人与单人列表及条目、聊天与命令补全、加载遮罩。
- 加载遮罩必须在自定义 shader 尚未就绪时也能正常显示。

### 阶段 8：清理与文档（S）

- 删除 `newUI/*`、`AnimatedButton`、`UiButtonRenderer`、`UiButtonStyle`、`VirtualCoordinateHelper` 和旧的调色常量；`UiPanelRenderer` 若已无调用者也一并删除。
- 更新 `docs/ARCHITECTURE.md`，完善 `docs/UI_FRAMEWORK_GUIDE.md`。

## 7. 测试与验证

- **单元测试**（JUnit 5，项目已配置）：布局、命中测试、动画数学、滚动物理、文字换行、列表 key 比对、主题 JSON 解析。框架核心逻辑与 MC 渲染分离，便于测试。
- **游戏内验证**：每个阶段都在两套环境中实际打开界面检查。GUI 缩放覆盖 1 到 6 以及 Modern UI 的新缩放档位；窗口尺寸覆盖 1280×720、1920×1080、2560×1440 和超宽屏。
- **性能**：每迁移一个界面，对照阶段 0 的基线记录 GPU 提交次数和耗时。
- **构建**：每次改动至少执行 `gradlew compileJava` 和相关测试；涉及资源时执行完整的 `gradlew build`。

## 8. 风险

| 风险 | 应对 |
| --- | --- |
| 工作量大，迁移期间界面风格不一致 | 阶段 1 的 SDF 替换先统一基础观感；终端迁移期间新旧并存，整体完成后再切换 |
| Modern UI 升级后字体测量或 mixin 行为变化 | 文字只经 Font 接口；升级 Modern UI 时回归检查文字密集的界面 |
| ImmediatelyFast、Iris 与自定义 RenderType 的交互问题 | 阶段 1 结束时就在整合包环境验证，让问题尽早暴露 |
| 首次资源加载时 shader 不可用 | 渲染核心内置退回路径 |
| API 设计过早定型 | 阶段 2 用试点页面驱动 API 设计，试点完成后才冻结 |
| 迁移引入行为回归（点击逻辑、网络请求时机） | 只替换展示层，保留原有数据请求与业务调用；逐页对照验收 |

## 9. 不在本计划内

- HTML/CSS/JS 运行时、XML 界面模板（以后需要时可以在节点树之上补充）。
- 非输入框文字的选中与复制、富文本编辑。
- 从右到左书写的语言布局。
- 改造原版容器界面（背包、合成台等）和 Modern UI 自身的界面。

## 10. 待确认

1. **视觉方向**：终端（深蓝灰科技风）与 HUD（骨白与琥珀的生存风）统一成一种风格，还是保留两种风格、共用基础色阶和规范？
2. **正式服过渡**：终端迁移期间，是否给玩家提供“新旧界面切换”开关？还是只在开发环境切换，完成后整体上线？
3. **界面缩放**：是否给玩家提供独立于 MC GUI 缩放的“界面缩放”选项？

计划确认后，新增 ADR 记录“UI 使用自研原生框架”这一决策。
