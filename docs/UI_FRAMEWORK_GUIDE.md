# 梦屿 UI 框架使用指南

本模组的界面、HUD 与原版界面改造都建立在 `client/ui/framework` 上。设计动机与迁移计划见
[`planning/UI_FRAMEWORK_PLAN_2026-09-30.md`](planning/UI_FRAMEWORK_PLAN_2026-09-30.md)，本文只讲怎么用。

## 1. 基本约定

- **缩放跟随 MC**：所有坐标都是 GUI 像素，不设独立的 UI 缩放。尺寸档位由 `Responsive` 按 GUI 宽度划分：
  `COMPACT` < 560 ≤ `REGULAR` < 860 ≤ `WIDE`。
- **文字走原版 Font**：Modern UI 随整合包下发，它会接管 Font 渲染，所以直接用 `Text` / `canvas.text` 即可，
  不要自己引 Modern UI 的 API。
- **形状一律走 `UiCanvas`**：圆角、描边、渐变、阴影、圆弧都由 SDF 着色器按层合批绘制。
  不要在框架界面里直接调用 `GuiGraphics.fill/blit`，需要原版绘制时用 `canvas.custom(...)`。
- **时间用 `UiClock.now()` / `Util.getMillis()`**，不要用 `System.currentTimeMillis()`（游戏内的计时器与它不同源）。

## 2. 包结构

| 包 | 内容 |
| --- | --- |
| `render` | `UiCanvas`（每帧的绘制入口）、`SdfRenderer`、`ShapeBuffer` |
| `node` | `UiNode` 节点基类、`Box` 容器、`UiRoot`、flex 布局、`EnterEffect` 入场动画 |
| `widget` | `Ui` 工厂、`Text`、`Button`、`Card`、`TextField`、`ScrollView`、`Dynamic`、`ForEach`、`Responsive`、`Icons` 等 |
| `anim` | `AnimatedFloat`（弹簧 / 补间）、`AnimatedColor`、`Spring`、`Easing` |
| `theme` | `Theme`（间距、圆角、阴影、时长常量）、`ColorRole`、`UiColor` 颜色工具 |
| `text` | `TextStyle`、`TextLayout`、`TextFit`（按宽度截断加 “…”） |
| `screen` | `UiScreen`（独立界面基类）、`ScreenHost`（把节点树挂到原版界面上） |
| `hud` | `HudLayer`、`HudCanvas`、`HudFrame`：游戏内 HUD 共用一遍画布 |
| `nav` | `Navigator`、`Page`：界面内的分页与返回栈 |

## 3. 写一个独立界面

继承 `UiScreen`，在 `build()` 里返回节点树。`build()` 只在首次打开时调用，窗口尺寸变化只会重新布局。

```java
public class Screen_Example extends UiScreen {
    public Screen_Example() {
        super(Component.literal("示例"));
        setBackground(Background.NONE);   // 或 SCRIM / VANILLA
    }

    @Override
    protected UiNode<?> build() {
        Box panel = Ui.column(
                Text.of("标题").style(TextStyle.HEADLINE).singleLine(),
                Text.of("正文会按宽度自动换行。").style(TextStyle.BODY),
                Button.of("确定").leadingIcon(Icons.CHECK).onClick(this::onClose)
        ).gap(Theme.Space.MD).padding(Theme.Space.XL).radius(Theme.Radius.XL)
                .background(0xF0101820).border(1.0F, 0x30FFFFFF)
                .width(320.0F).enter(EnterEffect.POP);
        return Ui.column(panel).alignItems(Align.CENTER).justify(Justify.CENTER);
    }
}
```

要点：

- 布局是 flex：`row` / `column` / `stack`，配合 `gap`、`padding`、`grow`、`shrink`、`basis`、`alignItems`、`justify`。
- 行内的固定尺寸小元素（圆点、图标底）记得 `.shrink(0)`，否则空间不足时会被压扁。
- 要让子节点在交叉轴撑满，父容器用 `alignItems(Align.STRETCH)`。
- 随数据变化的内容：简单文本用 `Text.of(() -> Component...)`；结构会变的用 `Dynamic.of(key, builder)`，
  key 变化时才重建；列表用 `ForEach.of(source, keyOf, builder)`。
- 每帧更新颜色、可见性等属性用 `node.onUpdate(() -> ...)`。
- 自定义绘制：继承 `UiNode` 并重写 `paintBackground` / `paintContent` / `paintOverlay`，坐标相对节点左上角。
- Esc 默认关闭界面；分页内返回请重写 `onEscape()`。

## 4. 改造原版界面（mixin）

原版界面不能换成 `UiScreen` 时，用 `ScreenHost` 把节点树叠在它上面（参考 `client/ui/vanilla/TitleScreenUi`）：

```java
private final ScreenHost host = new ScreenHost(this::build);

// render 注入点
host.render(screen, graphics);
// 输入注入点：返回 true 表示已处理，原版不再处理
if (host.mouseClicked(mouseX, mouseY, button)) { cir.setReturnValue(true); }
```

`ScreenHost` 默认放行没有被节点处理的点击，原版按钮与列表仍然可用。
原版列表的条目绘制通过 `SelectionEntryPainter` 一类的辅助类直接使用 `UiCanvas`。

## 5. HUD

实现 `HudLayer` 并在 `ClientSetup.onClientSetup` 里 `HudCanvas.register(...)`：

```java
public static final HudLayer LAYER = new HudLayer() {
    @Override
    public boolean visible(Minecraft minecraft) {
        return minecraft.player != null && !minecraft.options.hideGui;
    }

    @Override
    public void paint(HudFrame frame) {
        UiCanvas canvas = frame.canvas();
        canvas.shape(8, 8, 120, 20).radius(6).fill(0xB0101820).draw();
        canvas.text("示例", 14, 14, 0xFFFFFFFF, 1.0F, false);
    }
};
```

- 所有可见层每帧共用一遍画布：形状合批，文字与物品按层穿插。
- `pass()` 选择 `MAIN`（原版 HUD 之后）或 `OVERLAY`（最后，盖在其他模组 HUD 之上）；`order()` 小的先画。
- 某一层抛异常只会跳过该层，不影响其他层。
- 聊天栏等不在 HUD 事件里的临时绘制用 `HudCanvas.paintNow(graphics, canvas -> ...)`。

## 6. 动画

```java
AnimatedFloat hover = AnimatedFloat.spring(0.0F, Spring.SNAPPY);   // 每帧 set 目标，get 读当前值
AnimatedFloat reveal = AnimatedFloat.tween(0.0F, 380.0F, Easing.EMPHASIZED);
```

节点入场用 `.enter(EnterEffect.FADE_UP)` 等预设，`replayEnter()` 可以重播。持续动效（呼吸、扫描）直接用
`UiClock.now()` 取相位计算即可，不需要存状态。

## 7. 视觉风格

| 风格 | 位置 | 说明 |
| --- | --- | --- |
| 梦屿终端 | `server_ui_system/client/terminal`：`TerminalUi`、`TerminalChrome`、`TerminalWidgets` | 深色玻璃、青色主色，等高线地图背景、灯塔光束、四角取景括号 |
| 原版界面 | `client/ui/vanilla/VanillaChrome` | 标题、世界选择、服务器列表、加载与断开界面共用的玻璃面板与徽章 |
| HUD | `gameplay/playerattributes_system/client/ui/hud/HudDraw` 等 | 半透明面板与数值条，尽量少遮挡画面 |
| 区域明信片 | `client/ui/notification/RegionPostcard` | 上方居中横幅：按群系、时间、天气变化的分层风景剪影，入场像立体书依次弹起 |

新界面优先复用对应风格里的组件（如 `TerminalUi.card()`、`TerminalUi.header(...)`、`VanillaChrome.glassPanel()`），
颜色取各自的常量，不要散落新的色值。

## 8. 截图验证（UI Harness）

`./gradlew runUiHarness` 以 `run-harness` 为游戏目录进入名为 `harness` 的存档，按
`run-harness/harness_steps.txt` 逐行打开界面并截图到 `run-harness/screenshots/harness/`，完成后自动退出。

步骤格式：`场景名 [gui=缩放] [wait=tick] [mouse=x,y] [click=x,y] [as=文件名]`，例如：

```
terminal:home gui=3 wait=60 as=term_home
terminal:story gui=4 wait=50 as=term_story_compact
death gui=2 wait=80 as=death
```

注意 `wait` 的单位是 tick（20 tick = 1 秒）。场景在 `client/debug/UiHarnessScenarios` 中登记；
加新界面时顺手登记一个场景，便于在不同 GUI 缩放下检查布局。harness 窗口运行期间不要点击或缩放它。

几个特殊场景：

- `loading_startup` / `loading_startup_fallback`：预览首次启动的加载画面；后者用
  `SdfRenderer.forceFallback(true)` 模拟界面着色器尚未加载的阶段（只剩原版矩形与线性渐变）。
  首次启动画面里需要在这一阶段也好看的图形（如灯塔）请画成贴图，见 `client/ui/loading/StartupArt`
  与生成脚本 `tools/generate_startup_lighthouse.py`。
- `title_mod_buttons`：标题界面加入几个模拟的其他模组按钮（带提示的图标按钮、无文字按钮、纯文本按钮），
  检查辅助按钮的命名。
- `postcard:<场景>:<day|dusk|night>[:rain]`：上方居中的区域明信片（场景名见 `RegionPostcard.Scene`，如
  `postcard:cherry:dusk`）；`postcard_plain:` 为再次进入（只有地名）；`postcard_anim:` 只显示 3.4 秒，
  用不同的 `wait` 连续截图可以拼出入场与退场动画；`postcard_biome:minecraft:dark_forest` 按真实群系走一遍
  判定（群系标签 + 群系颜色 + 世界当前时间）。风景剪影由 `tools/generate_region_scenes.py` 生成，
  中景里风车、烟囱、灯塔的位置写在 `tools/region_scene_anchors.json`，改图后要同步到 `RegionPostcard`。
