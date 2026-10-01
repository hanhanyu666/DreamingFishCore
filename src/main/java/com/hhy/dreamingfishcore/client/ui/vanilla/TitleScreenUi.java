package com.hhy.dreamingfishcore.client.ui.vanilla;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.framework.core.UiClock;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.Cursor;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.Justify;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.screen.ScreenHost;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextFit;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.InteractiveNode;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Modal;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Responsive;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.client.ui.util.UiBackgroundRenderer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.fml.ModList;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.hhy.dreamingfishcore.client.ui.vanilla.VanillaChrome.BLUE;
import static com.hhy.dreamingfishcore.client.ui.vanilla.VanillaChrome.CREAM;
import static com.hhy.dreamingfishcore.client.ui.vanilla.VanillaChrome.CREAM_SOFT;
import static com.hhy.dreamingfishcore.client.ui.vanilla.VanillaChrome.GOLD;
import static com.hhy.dreamingfishcore.client.ui.vanilla.VanillaChrome.GREEN;
import static com.hhy.dreamingfishcore.client.ui.vanilla.VanillaChrome.MUTED;
import static com.hhy.dreamingfishcore.client.ui.vanilla.VanillaChrome.RED;

/**
 * 标题界面：轮播背景 + 左侧标语 + 右下“航线”菜单。
 *
 * <p>原版 {@link TitleScreen} 仍负责创建按钮，其他模组照常往里加按钮；这里把主按钮换成航线菜单，
 * 语言、辅助功能、模组列表等辅助按钮收拢到右上角的玻璃小按钮里，点击时转调原控件。</p>
 */
public final class TitleScreenUi {
    private static final String UPDATE_LOG_URL = "https://github.com/QingMo-A/DreamingFishCore/releases";
    private static final String UPDATE_LOG_API_URL = "https://api.github.com/repos/QingMo-A/DreamingFishCore/releases/latest";
    private static final Set<String> PRIMARY_KEYS = Set.of("menu.singleplayer", "menu.multiplayer", "menu.online",
            "menu.options", "menu.quit");

    private static volatile String latestRelease = "";
    private static volatile boolean fetchStarted;

    private final TitleScreen screen;
    private final ScreenHost host;

    public TitleScreenUi(TitleScreen screen) {
        this.screen = screen;
        this.host = new ScreenHost(this::build);
        startUpdateFetch();
    }

    public ScreenHost host() {
        return host;
    }

    /**
     * 每帧绘制。{@code fade} 为原版首次进入时的淡入进度：淡入期间只画背景，结束后再建立界面，
     * 让入场动画在画面完全亮起后才播放。
     */
    public void render(GuiGraphics graphics, float fade) {
        UiBackgroundRenderer.renderCyclingBackgroundCrossfade(graphics, screen.width, screen.height, fade);
        if (fade >= 1.0F) {
            host.render(screen, graphics);
        }
    }

    // ==================== 构建 ====================

    private UiNode<?> build() {
        return Ui.stack(
                new Backdrop().enter(EnterEffect.FADE.withDuration(600.0F)),
                Responsive.of(this::layout),
                footer()
        ).alignItems(Align.STRETCH);
    }

    private UiNode<?> layout(Responsive.Size size) {
        boolean compact = size == Responsive.Size.COMPACT;
        float menuWidth = switch (size) {
            case COMPACT -> 196.0F;
            case REGULAR -> 226.0F;
            case WIDE -> 252.0F;
        };
        Box left = Ui.column(Ui.spacer(), hero(compact), Ui.spacer(),
                        VanillaChrome.chip("公益资助说明", Icons.HEART, GOLD, this::openDonate)
                                .enter(EnterEffect.FADE_UP.delayed(420.0F)))
                .alignItems(Align.START).grow(1.0F).basis(0.0F);
        Box right = Ui.column(
                Dynamic.of(this::auxWidgets, this::auxRow),
                Ui.spacer(),
                routeMenu(compact)
        ).alignItems(Align.STRETCH).width(menuWidth);
        float pad = compact ? 16.0F : 26.0F;
        return Ui.row(left, right).alignItems(Align.STRETCH).gap(Theme.Space.XL)
                .padding(pad, 12.0F, compact ? 12.0F : 20.0F, compact ? 22.0F : 28.0F);
    }

    private UiNode<?> hero(boolean compact) {
        Box kicker = Ui.row(Ui.stack().size(22.0F, 2.0F).radius(1.0F).background(BLUE),
                        Text.of("灾变之后，仍有人在这里守望。").style(TextStyle.LABEL).color(0xFFBFEFEA).singleLine())
                .gap(Theme.Space.MD).alignItems(Align.CENTER);
        Text welcome = Text.of("欢迎来到").style(TextStyle.DISPLAY).color(CREAM).singleLine();
        Box name = Ui.stack(new NameGlow(), Text.of("梦屿").style(TextStyle.DISPLAY.withScale(compact ? 3.0F : 3.6F))
                .color(0xFFFFF4D6).singleLine()).alignItems(Align.START);
        Box description = Ui.column(
                Text.of("一片曾让人慢下来生活、重新开始做梦的大陆。").style(TextStyle.BODY).color(CREAM_SOFT),
                Text.of("如今，它正在风暴与沉默之间，等待新的幸存者。").style(TextStyle.BODY).color(CREAM_SOFT)
        ).gap(3.0F);
        Box chips = Ui.row(
                infoTag("CORE", version(), BLUE),
                infoTag("最新发布", () -> latestRelease.isEmpty() ? "获取中…" : latestRelease, GREEN)
        ).gap(Theme.Space.SM);

        Box hero = Ui.column().alignItems(Align.START).gap(Theme.Space.SM).maxWidth(300.0F);
        hero.add(kicker.enter(EnterEffect.FADE_RIGHT),
                welcome.margin(0.0F, 6.0F, 0.0F, 0.0F).enter(EnterEffect.FADE_RIGHT.delayed(60.0F)),
                name.enter(EnterEffect.FADE_RIGHT.delayed(120.0F)),
                new GoldRule().size(112.0F, 1.5F).margin(0.0F, 2.0F, 0.0F, 4.0F).enter(EnterEffect.FADE.delayed(200.0F)),
                description.enter(EnterEffect.FADE_RIGHT.delayed(240.0F)),
                chips.margin(0.0F, 6.0F, 0.0F, 0.0F).enter(EnterEffect.FADE_UP.delayed(320.0F)));
        return hero;
    }

    private static UiNode<?> infoTag(String label, String value, int accent) {
        return infoTag(label, () -> value, accent);
    }

    private static UiNode<?> infoTag(String label, java.util.function.Supplier<String> value, int accent) {
        Box tag = Ui.row(
                Ui.stack().size(2.0F, 12.0F).radius(1.0F).background(UiColor.withAlpha(accent, 0.8F)),
                Ui.column(Text.of(label).style(TextStyle.CAPTION).color(0xFF8C969F).singleLine(),
                        Text.of(() -> Component.literal(value.get())).style(TextStyle.LABEL_STRONG).color(0xFFF1F4F6).singleLine())
                        .gap(1.0F)
        ).gap(6.0F).alignItems(Align.CENTER).padding(7.0F, 4.0F).radius(Theme.Radius.MD)
                .background(0x8A070B10).border(1.0F, 0x1CFFFFFF);
        return tag;
    }

    private UiNode<?> routeMenu(boolean compact) {
        Box header = Ui.column(
                Text.of("ROUTE SELECT").style(TextStyle.CAPTION_STRONG).color(BLUE).singleLine(),
                Text.of("选择你的下一段航线").style(compact ? TextStyle.LABEL : TextStyle.SUBTITLE).color(CREAM).singleLine()
        ).gap(2.0F).padding(2.0F, 0.0F, 0.0F, compact ? 2.0F : 6.0F);

        AbstractWidget multiplayerWidget = findPrimary("menu.multiplayer");
        RouteButton multiplayer = new RouteButton("01", Icons.USERS, "多人游戏", "选择服务器，加入梦屿与其他玩家共同冒险",
                GREEN, true, compact, this::openMultiplayer);
        if (multiplayerWidget != null && !multiplayerWidget.active) {
            multiplayer.disabled(true).tooltip(Component.literal("多人游戏当前不可用"));
        }
        Box footer = Ui.row(
                Text.of("海图 · 梦屿外海").style(TextStyle.CAPTION).color(0xFF7D8890).singleLine(),
                Ui.spacer(),
                Text.of("N 27°41′  E 121°09′").style(TextStyle.CAPTION).color(0xFF7D8890).singleLine()
        ).alignItems(Align.CENTER).padding(2.0F, compact ? 2.0F : 6.0F, 0.0F, 0.0F);

        Box chart = new RouteChart().column().alignItems(Align.STRETCH).gap(compact ? 1.0F : 3.0F)
                .padding(compact ? 4.0F : 6.0F);
        chart.add(header.enter(EnterEffect.FADE_LEFT.delayed(100.0F)),
                multiplayer.enter(EnterEffect.FADE_LEFT.delayed(160.0F)),
                new RouteButton("02", Icons.MAP, "单人游戏", "选择或创建你的单人世界", GOLD, false, compact,
                        () -> minecraft().setScreen(new SelectWorldScreen(screen))).enter(EnterEffect.FADE_LEFT.delayed(210.0F)),
                new RouteButton("03", Icons.SETTINGS, "设置", "调整游戏选项，自定义你的体验", BLUE, false, compact,
                        () -> minecraft().setScreen(new OptionsScreen(screen, minecraft().options)))
                        .enter(EnterEffect.FADE_LEFT.delayed(260.0F)),
                new RouteButton("04", Icons.POWER, "退出游戏", "离开梦屿，返回现实世界", RED, false, compact,
                        () -> minecraft().stop()).enter(EnterEffect.FADE_LEFT.delayed(310.0F)),
                footer.enter(EnterEffect.FADE.delayed(380.0F)));
        return chart;
    }

    private UiNode<?> footer() {
        Box row = Ui.row().alignItems(Align.END).gap(Theme.Space.MD);
        PlainTextButton credits = findCredits();
        if (credits != null) {
            Text link = Text.of(credits.getMessage().getString()).style(TextStyle.CAPTION).color(0xFF7F8A93).singleLine();
            link.cursor(Cursor.POINTER).onClick(credits::onPress);
            link.onHover(hovered -> link.color(hovered ? 0xFFE9EEF2 : 0xFF7F8A93));
            row.add(link);
        }
        row.add(Ui.spacer(), Text.of("© 2026 DreamingFish · DreamingFishCore").style(TextStyle.CAPTION).color(0xFF8E969E)
                .singleLine());
        return row.absolute(10.0F, Float.NaN, 10.0F, 5.0F).enter(EnterEffect.FADE.delayed(450.0F));
    }

    // ==================== 辅助按钮 ====================

    /** 当前需要收拢的辅助控件（原版与其他模组在 init 之后加入的按钮也会出现在这里）。 */
    private List<AbstractWidget> auxWidgets() {
        List<AbstractWidget> result = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible && !(widget instanceof PlainTextButton)
                    && !isPrimaryMessage(widget.getMessage())) {
                result.add(widget);
            }
        }
        return result;
    }

    private UiNode<?> auxRow(List<AbstractWidget> widgets) {
        Box row = Ui.row().gap(Theme.Space.XS).justify(Justify.END)
                .wrap(true);
        int index = 0;
        for (AbstractWidget widget : widgets) {
            String label = auxLabel(widget);
            String shown = TextFit.trim(label, minecraft().font, 72);
            UiNode<?> chip = VanillaChrome.chip(shown, auxIcon(widget), BLUE, () -> activate(widget))
                    .enter(EnterEffect.FADE_DOWN.delayed(200.0F + index * 40.0F));
            List<Component> tooltip = auxTooltip(widget, label, shown);
            if (!tooltip.isEmpty()) {
                chip.tooltip(() -> tooltip);
            }
            row.add(chip);
            index++;
        }
        UiNode<?> changelog = VanillaChrome.chip("更新日志", Icons.HISTORY, GREEN, TitleScreenUi::openUpdateLog)
                .tooltip(() -> List.of(Component.literal("在浏览器中打开发布页"),
                        Component.literal(latestRelease.isEmpty() ? "最新版本获取中…" : "最新：" + latestRelease)))
                .enter(EnterEffect.FADE_DOWN.delayed(200.0F + index * 40.0F));
        row.add(changelog);
        return row;
    }

    private static void activate(AbstractWidget widget) {
        if (widget instanceof AbstractButton button) {
            button.onPress();
        } else {
            widget.onClick(widget.getX() + widget.getWidth() * 0.5, widget.getY() + widget.getHeight() * 0.5);
        }
    }

    /**
     * 辅助按钮的名称。其他模组常用只有图标、标题为空的按钮，依次退到：按钮自带的提示文字、
     * 按钮所属模组的名称，都取不到时才显示“更多”。
     */
    private static String auxLabel(AbstractWidget widget) {
        String key = translationKey(widget.getMessage());
        if ("options.language".equals(key) || "narrator.button.language".equals(key)) {
            return "语言";
        }
        if ("options.accessibility".equals(key) || "narrator.button.accessibility".equals(key)) {
            return "辅助功能";
        }
        if ("fml.menu.mods".equals(key)) {
            return "模组";
        }
        String text = clean(widget.getMessage().getString());
        if (!text.isEmpty()) {
            return text;
        }
        String tooltip = clean(tooltipLine(widget));
        if (!tooltip.isEmpty()) {
            return tooltip;
        }
        String mod = WidgetOwner.modName(widget);
        return mod == null || mod.isBlank() ? "更多" : mod;
    }

    /** 悬停提示：名称被截短时给出全名，并注明来自哪个模组。 */
    private static List<Component> auxTooltip(AbstractWidget widget, String label, String shown) {
        List<Component> lines = new ArrayList<>();
        String mod = WidgetOwner.modName(widget);
        boolean fromMod = mod != null && !mod.isBlank() && !mod.equals(label);
        if (!shown.equals(label) || fromMod) {
            lines.add(Component.literal(label));
        }
        if (fromMod) {
            lines.add(Component.literal("§7来自 " + mod));
        }
        return lines;
    }

    private static String tooltipLine(AbstractWidget widget) {
        Tooltip tooltip = widget.getTooltip();
        if (tooltip == null) {
            return "";
        }
        List<FormattedCharSequence> lines = tooltip.toCharSequence(minecraft());
        if (lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        lines.get(0).accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return true;
        });
        return builder.toString();
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace("...", "").replace("…", "").trim();
    }

    private static Icons auxIcon(AbstractWidget widget) {
        String key = translationKey(widget.getMessage());
        if ("options.language".equals(key) || "narrator.button.language".equals(key)) {
            return Icons.GLOBE;
        }
        if ("options.accessibility".equals(key) || "narrator.button.accessibility".equals(key)) {
            return Icons.ACCESSIBILITY;
        }
        if ("fml.menu.mods".equals(key)) {
            return Icons.GRID;
        }
        return Icons.SPARKLE;
    }

    private AbstractWidget findPrimary(String key) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && key.equals(translationKey(widget.getMessage()))) {
                return widget;
            }
        }
        return null;
    }

    private PlainTextButton findCredits() {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof PlainTextButton button) {
                return button;
            }
        }
        return null;
    }

    static boolean isPrimaryMessage(Component component) {
        String key = translationKey(component);
        // 其他模组的按钮可以使用纯文本或空标题；Set.of 创建的集合不接受 null 查询。
        return key != null && PRIMARY_KEYS.contains(key);
    }

    private static String translationKey(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : null;
    }

    // ==================== 动作 ====================

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private void openMultiplayer() {
        Minecraft minecraft = minecraft();
        Screen next = minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(screen) : new SafetyScreen(screen);
        minecraft.setScreen(next);
    }

    private void openDonate() {
        Box close = Ui.stack(Icon.of(Icons.CLOSE, 10.0F).color(0xFFB9C0C8)).alignItems(Align.CENTER).size(18.0F, 18.0F)
                .radius(Theme.Radius.SM).cursor(Cursor.POINTER);
        Box panel = VanillaChrome.dialogPanel(GOLD).column().alignItems(Align.STRETCH).gap(Theme.Space.SM)
                .padding(Theme.Space.XL, Theme.Space.LG).width(300.0F);
        panel.add(
                Ui.row(Icon.of(Icons.HEART, 14.0F).color(GOLD),
                        Text.of("公益资助说明").style(TextStyle.TITLE).color(0xFFFFF1DA).singleLine().grow(1.0F),
                        close).gap(Theme.Space.SM).alignItems(Align.CENTER),
                Text.of("§e§l欢§6§l迎§a§l来§b§l到 §d§l守§9§l望§c§l梦§6§l屿 §8— §7梦鱼服").style(TextStyle.BODY),
                Ui.stack().height(1.0F).background(0x22FFFFFF).margin(0.0F, 2.0F),
                donateLine("本服为§e非营利公益服§7，§e公益服维持不易，感谢所有资助者§7。"),
                donateLine("无偿资助§c无法获得§7游戏内权益和物资，请您资助前三思。"),
                donateLine("资助者可自定义设计武器 / 装备 / 物品等，且可以自定义属性、外观（数值保证合理）。"),
                donateLine("开发完成后可以让所有人§a获取§7。"));
        Modal modal = Modal.show(host.ui(), panel);
        close.onClick(modal::dismiss);
        close.onHover(hovered -> close.background(hovered ? 0x55AA3333 : 0));
    }

    private static UiNode<?> donateLine(String text) {
        return Ui.row(Ui.stack().size(3.0F, 3.0F).radius(1.5F).background(GOLD).margin(0.0F, 3.5F, 0.0F, 0.0F),
                        Text.of("§7" + text).style(TextStyle.LABEL.withLineGap(2.0F)).color(0xFFC9CED3).grow(1.0F).shrink(1.0F))
                .gap(Theme.Space.SM).alignItems(Align.START);
    }

    private static void openUpdateLog() {
        try {
            Util.getPlatform().openUri(new URI(UPDATE_LOG_URL));
        } catch (Exception ignored) {
            // 地址异常时忽略，避免影响标题界面
        }
    }

    private static String version() {
        return ModList.get().getModContainerById(DreamingFishCore.MODID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("dev");
    }

    private static void startUpdateFetch() {
        if (fetchStarted) {
            return;
        }
        fetchStarted = true;
        Thread thread = new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(UPDATE_LOG_API_URL).openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Minecraft-Mod");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                if (connection.getResponseCode() != 200) {
                    latestRelease = "暂无";
                    return;
                }
                try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    String name = json.has("name") ? json.get("name").getAsString() : "";
                    String tag = json.has("tag_name") ? json.get("tag_name").getAsString() : "";
                    String latest = !name.isBlank() ? name : tag;
                    latestRelease = latest.isBlank() ? "暂无" : latest;
                }
            } catch (Exception ignored) {
                latestRelease = "离线";
            }
        }, "dreamingFishCore-update-log-fetch");
        thread.setDaemon(true);
        thread.start();
    }

    // ==================== 部件 ====================

    /** 背景之上的电影感遮罩：左侧与底部压暗保证文字可读，顶部轻压托住辅助按钮。 */
    private static final class Backdrop extends UiNode<Backdrop> {
        Backdrop() {
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float w = width();
            float h = height();
            canvas.fill(0.0F, 0.0F, w, h, 0x2205070A);
            canvas.shape(0.0F, 0.0F, w * 0.62F, h).horizontalGradient(0xD603060A, 0x0003060A).draw();
            canvas.shape(0.0F, h * 0.5F, w, h * 0.5F).verticalGradient(0x0003060A, 0x9903060A).draw();
            canvas.shape(0.0F, 0.0F, w, 56.0F).verticalGradient(0x66000000, 0x00000000).draw();
            canvas.shape(0.0F, 0.0F, w, h).radial(0x00000000, 0x70000000, w * 0.5F, h * 0.5F,
                    (float) Math.hypot(w, h) * 0.6F).draw();
        }
    }

    /** “梦屿”背后的暖色柔光，缓慢呼吸。 */
    private static final class NameGlow extends UiNode<NameGlow> {
        NameGlow() {
            pointerEvents(false);
            absolute(-40.0F, -40.0F, -60.0F, -40.0F);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float breath = (float) (0.75 + 0.25 * Math.sin(UiClock.now() / 1400.0));
            float w = width();
            float h = height();
            // 半径不超过节点范围，光晕在边界内完全淡出，不留矩形边
            float radius = Math.min(w * 0.4F, h * 0.5F);
            canvas.shape(0.0F, 0.0F, w, h).radial(UiColor.withAlpha(0xFFFFC27A, 0.18F * breath), 0x00FFC27A,
                    w * 0.4F, h * 0.5F, radius).draw();
        }
    }

    /** 金色细线，向右淡出，亮度随时间轻微起伏。 */
    private static final class GoldRule extends UiNode<GoldRule> {
        GoldRule() {
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float pulse = (float) (0.7 + 0.3 * Math.sin(UiClock.now() / 760.0));
            canvas.shape(0.0F, 0.0F, width(), height()).radius(height() * 0.5F)
                    .horizontalGradient(UiColor.withAlpha(GOLD, 0.95F * pulse), UiColor.withAlpha(GOLD, 0.0F)).draw();
        }
    }

    /**
     * 航线图：菜单背后一圈压暗与淡淡的罗盘，四个选项是同一条虚线航路上的航点，虚线缓慢向下流动。
     */
    private static final class RouteChart extends Box {
        @Override
        protected void paintBackground(UiCanvas canvas) {
            float w = width();
            float h = height();
            // 不画实底面板，只压暗托住文字，让菜单像标在海图上；光晕在正方形范围内完全淡出，不留矩形边
            float shadeX = w * 0.5F + 10.0F;
            float shadeY = h * 0.55F;
            float shadeR = Math.max(w, h) * 0.72F;
            canvas.shape(shadeX - shadeR, shadeY - shadeR, shadeR * 2.0F, shadeR * 2.0F)
                    .radial(0xA0030608, 0x00030608, shadeR, shadeR, shadeR).draw();
            // 罗盘随菜单宽度缩放，压在标题右侧，尽量不越过第一条航线
            float compass = Math.max(24.0F, Math.min(38.0F, w * 0.15F));
            paintCompass(canvas, w - compass + 4.0F, compass - 10.0F, compass);

            float top = Float.NaN;
            float bottom = Float.NaN;
            float lineX = 0.0F;
            for (UiNode<?> child : children()) {
                if (child instanceof RouteButton button) {
                    float cy = button.y() + button.height() * 0.5F;
                    lineX = button.x() + button.nodeCenter();
                    top = Float.isNaN(top) ? cy : top;
                    bottom = cy;
                }
            }
            if (Float.isNaN(top)) {
                return;
            }
            float flow = (float) ((UiClock.now() / 90.0) % 7.0);
            for (float y = top - 7.0F + flow; y < bottom; y += 7.0F) {
                float from = Math.max(top, y);
                float to = Math.min(bottom, y + 3.5F);
                if (to > from) {
                    canvas.fill(lineX - 0.5F, from, 1.0F, to - from, 0x46D7E3EA);
                }
            }
        }

        /** 罗盘玫瑰：外圈刻度缓慢转动，四个主方位指针，北向标红。 */
        private static void paintCompass(UiCanvas canvas, float cx, float cy, float r) {
            int ring = 0x1CFFFFFF;
            canvas.arc(cx, cy, r, 1.0F, 0.0F, (float) (Math.PI * 2.0), ring, ring);
            canvas.arc(cx, cy, r * 0.62F, 1.0F, 0.0F, (float) (Math.PI * 2.0), 0x14FFFFFF, 0x14FFFFFF);
            float spin = (float) (UiClock.now() / 9000.0 % (Math.PI * 2.0));
            for (int i = 0; i < 36; i++) {
                double a = spin + i * Math.PI / 18.0;
                float inner = i % 9 == 0 ? r - 6.0F : r - 3.0F;
                canvas.line(cx + (float) Math.cos(a) * inner, cy + (float) Math.sin(a) * inner,
                        cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, 0.8F, 0x22FFFFFF, false);
            }
            for (int i = 0; i < 4; i++) {
                double a = -Math.PI / 2.0 + i * Math.PI / 2.0;
                float length = i == 0 ? r * 0.82F : r * 0.6F;
                int color = i == 0 ? UiColor.withAlpha(RED, 0.5F) : 0x30FFFFFF;
                canvas.line(cx, cy, cx + (float) Math.cos(a) * length, cy + (float) Math.sin(a) * length, 1.2F, color, true);
            }
            for (int i = 0; i < 4; i++) {
                double a = -Math.PI / 4.0 + i * Math.PI / 2.0;
                canvas.line(cx, cy, cx + (float) Math.cos(a) * r * 0.36F, cy + (float) Math.sin(a) * r * 0.36F, 0.8F,
                        0x1CFFFFFF, true);
            }
            canvas.circle(cx, cy, 1.6F, 0x55FFFFFF);
        }
    }

    /** 航点按钮：航路上的圆形航点 + 序号、标题与说明；悬停时航点点亮，一束光从航点向右展开。 */
    private static final class RouteButton extends InteractiveNode<RouteButton> {
        private final int accent;
        private final boolean primary;
        private final Waypoint waypoint;
        private final Icon icon;
        private final Text kicker;
        private final Text title;
        private final Icon chevron;

        RouteButton(String number, Icons iconType, String label, String description, int accent, boolean primary,
                    boolean compact, Runnable action) {
            this.accent = accent;
            this.primary = primary;
            float nodeSize = primary ? (compact ? 24.0F : 28.0F) : (compact ? 20.0F : 24.0F);
            icon = Icon.of(iconType, nodeSize * 0.46F);
            waypoint = new Waypoint(this);
            waypoint.stack().alignItems(Align.CENTER).size(nodeSize, nodeSize).shrink(0.0F);
            waypoint.add(icon);
            kicker = Text.of("ROUTE " + number).style(TextStyle.CAPTION_STRONG.withScale(0.62F)).singleLine();
            title = Text.of(label).style(primary ? TextStyle.TITLE : TextStyle.SUBTITLE).singleLine();
            chevron = Icon.of(Icons.ARROW_RIGHT, 9.0F);

            Box texts = Ui.column(kicker, title).gap(1.0F).grow(1.0F).shrink(1.0F);
            if (!compact) {
                texts.add(Text.of(description).style(TextStyle.CAPTION).color(0xFF8E99A3).singleLine());
            }
            row().alignItems(Align.CENTER).gap(Theme.Space.MD).radius(Theme.Radius.MD);
            float vertical = compact ? 3.0F : (primary ? 7.0F : 5.0F);
            padding(4.0F, vertical, 8.0F, vertical);
            add(waypoint, texts, chevron);
            cursor(Cursor.POINTER);
            onClick(action);
        }

        /** 航点圆心相对按钮左缘的横坐标，航路虚线穿过这里。 */
        float nodeCenter() {
            return padLeft() + waypoint.width() * 0.5F;
        }

        @Override
        protected void update() {
            float h = isEffectivelyDisabled() ? 0.0F : hover();
            icon.color(UiColor.lerp(UiColor.lerp(0xFFC4CCD3, accent, primary ? 0.8F : 0.5F), 0xFFFFFFFF, h * 0.5F));
            kicker.color(UiColor.withAlpha(accent, 0.55F + 0.45F * h));
            title.color(UiColor.lerp(primary ? 0xFFF4F7F9 : 0xFFDDE3E8, 0xFFFFFFFF, h));
            title.translate(h * 3.0F, 0.0F);
            kicker.translate(h * 3.0F, 0.0F);
            chevron.color(UiColor.withAlpha(UiColor.lerp(0xFF8E99A3, accent, h), 0.25F + 0.75F * h));
            chevron.translate(h * 4.0F - 2.0F, 0.0F);
            opacity(isEffectivelyDisabled() ? 0.45F : 1.0F);
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            float h = isEffectivelyDisabled() ? 0.0F : hover();
            float w = width();
            float ht = height();
            float cx = nodeCenter();
            float band = primary ? Math.max(h, 0.45F) : h;
            if (band > 0.01F) {
                canvas.shape(cx, 0.0F, w - cx, ht).radius(0.0F, radiusValue(), radiusValue(), 0.0F)
                        .horizontalGradient(UiColor.withAlpha(accent, 0.16F * band), UiColor.withAlpha(accent, 0.0F)).draw();
                canvas.shape(cx, ht - 1.0F, (w - cx) * (0.4F + 0.6F * band), 1.0F)
                        .horizontalGradient(UiColor.withAlpha(accent, 0.5F * band), UiColor.withAlpha(accent, 0.0F)).draw();
            }
            float press = press();
            if (press > 0.01F) {
                canvas.shape(cx, 0.0F, w - cx, ht).radius(radiusValue()).fill(UiColor.withAlpha(accent, 0.1F * press)).draw();
            }
        }

        float glow() {
            return isEffectivelyDisabled() ? 0.0F : hover();
        }
    }

    /** 航点：深色圆底遮住航路虚线，外圈随悬停点亮；主航线带一圈缓慢扩散的光环。 */
    private static final class Waypoint extends Box {
        private final RouteButton owner;

        Waypoint(RouteButton owner) {
            this.owner = owner;
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            float h = owner.glow();
            float w = width();
            float r = w * 0.5F;
            int accent = owner.accent;
            if (owner.primary) {
                float t = (float) ((UiClock.now() % 2400.0) / 2400.0);
                canvas.circle(r, r, r + 2.0F + t * 6.0F, UiColor.withAlpha(accent, 0.22F * (1.0F - t)));
            }
            canvas.circle(r, r, r + 3.0F * h, UiColor.withAlpha(accent, 0.18F * h));
            canvas.shape(0.0F, 0.0F, w, w).radius(r).fill(UiColor.lerp(0xF00A1117, UiColor.withAlpha(accent, 0.95F), 0.18F + 0.12F * h))
                    .border(1.2F, UiColor.withAlpha(accent, (owner.primary ? 0.7F : 0.45F) + 0.3F * h)).draw();
        }
    }
}
