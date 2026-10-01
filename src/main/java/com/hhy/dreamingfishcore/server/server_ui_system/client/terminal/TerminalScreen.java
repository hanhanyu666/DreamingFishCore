package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Easing;
import com.hhy.dreamingfishcore.client.ui.framework.core.UiClock;
import com.hhy.dreamingfishcore.client.ui.framework.nav.Navigator;
import com.hhy.dreamingfishcore.client.ui.framework.nav.Page;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.Justify;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.screen.UiScreen;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Button;
import com.hhy.dreamingfishcore.client.ui.framework.widget.CustomPaint;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.PlayerHead;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Responsive;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.guidance_system.network.Packet_GuidanceSnapshotRequest;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.client.cache.NpcMessageClientCache;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.network.Packet_NpcMessageSnapshotRequest;
import com.hhy.dreamingfishcore.gameplay.story_system.network.Packet_WorldHistoryRequest;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.economy_bridge.network.Packet_EconomyTerminalRequest;
import com.hhy.dreamingfishcore.server.notice_system.network.Packet_NoticeListRequest;
import com.hhy.dreamingfishcore.server.playerdata_system.network.Packet_RequestPlayerStats;
import com.hhy.dreamingfishcore.server.server_ui_system.client.ServerInformationDisplay;
import com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen.ServerScreenUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

/**
 * 梦屿终端。外壳包含背景、设备面板、顶栏、页面导航与底部 Dock；各模块内容由 {@link TerminalPage} 提供。
 *
 * <p>Esc 依次执行：页面内返回 → 返回上一页 → 回到主页 → 关闭终端（带收起动画）。</p>
 */
public class TerminalScreen extends UiScreen {
    public enum Tab {
        HOME,
        PROFILE,
        NOTICES,
        MESSAGES,
        ORGANIZATION,
        STORY,
        MORE
    }

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM.dd");
    private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static Tab lastTab = Tab.HOME;

    private final boolean skipIntro;
    private final Navigator navigator = new Navigator();
    private final Map<Tab, TerminalPage> roots = new EnumMap<>(Tab.class);
    private final AnimatedFloat reveal;
    private final TerminalDock dock = new TerminalDock(this::switchTab);
    private Tab tab;
    private Box device;
    private boolean closing;

    public TerminalScreen() {
        this(false, Tab.HOME);
    }

    public TerminalScreen(boolean skipIntro, Tab initialTab) {
        super(Component.literal("梦屿终端"));
        this.skipIntro = skipIntro;
        this.tab = initialTab == null ? Tab.HOME : initialTab;
        this.reveal = AnimatedFloat.tween(skipIntro ? 1.0F : 0.0F, 460.0F, Easing.EMPHASIZED);
        setBackground(Background.NONE);
    }

    /** 从子界面返回时重新打开，停留在上次的模块且不播放开场动画。 */
    public static TerminalScreen reopen() {
        return new TerminalScreen(true, lastTab);
    }

    @Override
    protected void init() {
        if (ServerScreenUI.isSubScreenActive()) {
            ServerScreenUI.onSubScreenClosed();
        }
        super.init();
        DreamingFishCore_NetworkManager.sendToServer(new Packet_EconomyTerminalRequest());
        DreamingFishCore_NetworkManager.sendToServer(new Packet_RequestPlayerStats());
        DreamingFishCore_NetworkManager.sendToServer(new Packet_NoticeListRequest());
        DreamingFishCore_NetworkManager.sendToServer(new Packet_WorldHistoryRequest());
        DreamingFishCore_NetworkManager.sendToServer(new Packet_NpcMessageSnapshotRequest());
        DreamingFishCore_NetworkManager.sendToServer(new Packet_GuidanceSnapshotRequest());
        // 组织名录是公共信息，打开终端时拉一次，保证角标与成员数和服务端一致
        OrganizationPage.requestSnapshot();
    }

    @Override
    protected UiNode<?> build() {
        dock.item(Tab.HOME, Icons.HOME, "主页", null)
                .item(Tab.PROFILE, Icons.USER, "档案", null)
                .item(Tab.NOTICES, Icons.MEGAPHONE, "广播", () -> TerminalData.unreadNotices() > 0)
                .item(Tab.MESSAGES, Icons.MAIL, "私信", () -> NpcMessageClientCache.getUnreadCount() > 0)
                .item(Tab.ORGANIZATION, Icons.USERS, "组织", OrganizationPage::needsAttention)
                .item(Tab.STORY, Icons.BOOK, "故事", TerminalData::hasOpenStoryProgress)
                .item(Tab.MORE, Icons.GRID, "更多", null);
        navigator.grow(1.0F).basis(0.0F).minHeight(0.0F);
        navigator.onChange(page -> dock.select(tab));

        device = new DevicePanel();
        device.add(buildHeader(), TerminalWidgets.relayLine(), navigator,
                Ui.row(dock).justify(Justify.CENTER).padding(0.0F, 6.0F, 0.0F, 8.0F));
        dock.select(tab);
        navigator.reset(rootFor(tab), false);

        Box shell = new DeviceShell(device);
        shell.onUpdate(() -> {
            if (reveal.target() < 1.0F && !closing) {
                reveal.set(1.0F);
            }
        });
        return Ui.stack(TerminalChrome.backdrop(reveal::get), shell).alignItems(Align.STRETCH);
    }

    // ==================== 外壳 ====================

    private UiNode<?> buildHeader() {
        Dynamic<Object> left = Dynamic.of(() -> navigator.canPop() || tab != Tab.HOME ? navigator.current() : null,
                page -> page == null ? brand() : pageTitle((Page) page));
        left.grow(1.0F).basis(0.0F);

        Box clock = Ui.row(
                Text.of(() -> Component.literal(LocalDateTime.now().format(CLOCK))).style(TextStyle.TITLE).singleLine(),
                Ui.column(
                        Text.of(() -> {
                            LocalDateTime now = LocalDateTime.now();
                            return Component.literal(now.format(DATE) + " " + WEEKDAYS[now.getDayOfWeek().getValue() - 1]);
                        }).style(TextStyle.CAPTION).singleLine(),
                        Text.of(() -> Component.literal("梦屿 · 第 " + worldDay() + " 日")).style(TextStyle.CAPTION)
                                .color(UiColor.withAlpha(TerminalUi.GOLD, 0.85F)).singleLine()
                ).gap(1.0F)
        ).gap(6.0F).alignItems(Align.CENTER);

        PlayerHead avatar = PlayerHead.local().headSize(12.0F).cornerRadius(3.0F);
        avatar.ring(1.0F, UiColor.withAlpha(TerminalUi.CYAN, 0.6F));
        Box status = Ui.row(
                new PulseDot(),
                Text.of(() -> Component.literal("在线 " + onlinePlayers())).style(TextStyle.LABEL).singleLine(),
                Ui.space(5.0F),
                TerminalWidgets.signalBars(() -> ServerInformationDisplay.getClientTps(Minecraft.getInstance())),
                Text.of(() -> Component.literal("中继 " + ServerInformationDisplay.getServerTpsText(Minecraft.getInstance())))
                        .style(TextStyle.LABEL).singleLine(),
                Ui.space(5.0F),
                avatar
        ).gap(4.0F).alignItems(Align.CENTER);
        Box right = Ui.row(status).justify(Justify.END).grow(1.0F).basis(0.0F);

        Responsive middle = Responsive.of(size -> size == Responsive.Size.COMPACT ? Ui.space(0) : clock);
        return Ui.row(left, middle, right).alignItems(Align.CENTER).height(32.0F).padding(14.0F, 0.0F);
    }

    private UiNode<?> brand() {
        return TerminalChrome.brand();
    }

    private UiNode<?> pageTitle(Page page) {
        Button back = Button.icon(Icons.CHEVRON_LEFT).ghost().small();
        back.onClick(this::handleBack);
        return Ui.row(back, Text.of(page.title()).style(TextStyle.SUBTITLE).singleLine().shrink(1.0F))
                .gap(6.0F).alignItems(Align.CENTER);
    }

    /** 梦屿上的第几日（按世界时间）。 */
    private static long worldDay() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 1L : minecraft.level.getDayTime() / 24000L + 1L;
    }

    private static int onlinePlayers() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.connection != null
                ? minecraft.player.connection.getOnlinePlayers().size() : 0;
    }

    // ==================== 导航 ====================

    public Navigator navigator() {
        return navigator;
    }

    public Tab tab() {
        return tab;
    }

    public void switchTab(Tab next) {
        if (next == tab && !navigator.canPop()) {
            return;
        }
        tab = next;
        lastTab = next;
        dock.select(next);
        navigator.reset(rootFor(next), true);
    }

    public void push(TerminalPage page) {
        navigator.push(page);
    }

    private TerminalPage rootFor(Tab target) {
        return roots.computeIfAbsent(target, key -> switch (key) {
            case HOME -> new HomePage(this);
            case PROFILE -> new ProfilePage(this);
            case NOTICES -> new NoticesPage(this);
            case MESSAGES -> new MessagesPage(this);
            case ORGANIZATION -> new OrganizationPage(this);
            case STORY -> new StoryPage(this);
            case MORE -> new MorePage(this);
        });
    }

    /**
     * 直接打开某个页面（调试与截图工具使用）：{@code help}、{@code history}、{@code market}、{@code rank}、
     * {@code notice}（第一条公告）、{@code task}（当前阶段第一项任务）。
     */
    public void openRoute(String route) {
        switch (route) {
            case "help" -> push(new HelpPage(this));
            case "history" -> push(new HistoryPage(this));
            case "market" -> push(new MarketPage(this));
            case "rank" -> {
                switchTab(Tab.PROFILE);
                push(new RankPage(this));
            }
            case "notice" -> {
                switchTab(Tab.NOTICES);
                var notices = TerminalData.notices();
                if (!notices.isEmpty()) {
                    push(new NoticeDetailPage(this, notices.get(notices.size() - 1)));
                }
            }
            case "task" -> {
                switchTab(Tab.STORY);
                var stage = TerminalData.currentStage();
                if (stage != null && stage.getTasks() != null && !stage.getTasks().isEmpty()) {
                    push(new StoryTaskPage(this, stage.getStageId(), stage.getTasks().get(0).getTaskKey()));
                }
            }
            default -> {
            }
        }
    }

    /** 记住当前模块，外部子界面关闭后回到这里。 */
    public void rememberTab() {
        lastTab = tab;
    }

    /** 打开外部子界面（经济系统、原版设置等），关闭后自动回到终端。 */
    public void openSubScreen(Screen screen) {
        lastTab = tab;
        ServerScreenUI.openSubScreen(screen);
    }

    private void handleBack() {
        if (navigator.back()) {
            return;
        }
        if (tab != Tab.HOME) {
            switchTab(Tab.HOME);
            return;
        }
        requestClose();
    }

    @Override
    protected boolean onEscape() {
        handleBack();
        return true;
    }

    // ==================== 开合 ====================

    public void requestClose() {
        if (closing) {
            return;
        }
        closing = true;
        reveal.duration(170.0F).set(0.0F);
    }

    @Override
    public void onClose() {
        requestClose();
    }

    @Override
    public void tick() {
        super.tick();
        if (closing && reveal.get() <= 0.01F) {
            finishClose();
        }
    }

    private void finishClose() {
        lastTab = Tab.HOME;
        if (ServerScreenUI.isOpeningSubScreen()) {
            return;
        }
        if (ServerScreenUI.isShowUI()) {
            ServerScreenUI.toggleUI();
        } else if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    // ==================== 视觉部件 ====================

    /** 设备外框：按窗口尺寸留边，驱动开合动画。 */
    private final class DeviceShell extends Box {
        private float lastWidth = -1.0F;
        private float lastHeight = -1.0F;

        DeviceShell(Box content) {
            column().alignItems(Align.STRETCH);
            add(content.grow(1.0F));
        }

        @Override
        protected void update() {
            float w = ui.width() > 0 ? ui.width() : width();
            float h = ui.height() > 0 ? ui.height() : height();
            if ((w != lastWidth || h != lastHeight) && w > 0.0F) {
                lastWidth = w;
                lastHeight = h;
                float marginX;
                float marginY;
                if (w < Responsive.REGULAR_MIN) {
                    marginX = 6.0F;
                    marginY = 6.0F;
                } else {
                    marginX = Math.max(Math.max(16.0F, (w - 960.0F) * 0.5F), w * 0.035F);
                    // 设备最大 960 × 600，超出部分留给背景，避免卡片被拉得过高
                    marginY = Math.max(Math.max(10.0F, h * 0.05F), (h - 600.0F) * 0.5F);
                }
                padding(marginX, marginY);
            }
            float p = reveal.get();
            device.opacity(Math.min(1.0F, p * 1.2F));
            device.scale(0.965F + 0.035F * p);
            device.translate(0.0F, (1.0F - p) * 8.0F);
        }
    }

    /** 设备面板：玻璃质感的主体、顶部高光与开场扫描光带。 */
    private final class DevicePanel extends Box {
        DevicePanel() {
            column().alignItems(Align.STRETCH);
            radius(Theme.Radius.XL);
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            TerminalChrome.paintDevice(canvas, width(), height(), Theme.Radius.XL, 1.0F);
        }

        @Override
        protected void paintOverlay(UiCanvas canvas) {
            float p = reveal.get();
            if (p < 0.999F && !closing && !skipIntro) {
                float w = width();
                float h = height();
                float bandX = w * p;
                canvas.shape(bandX - 40.0F, 2.0F, 40.0F, h - 4.0F)
                        .horizontalGradient(UiColor.withAlpha(TerminalUi.CYAN, 0.0F), UiColor.withAlpha(TerminalUi.CYAN, 0.16F)).draw();
                canvas.fill(bandX, 2.0F, 1.0F, h - 4.0F, UiColor.withAlpha(TerminalUi.CYAN, 0.6F * (1.0F - p)));
            }
        }
    }

    /** 在线状态的呼吸圆点。 */
    private static final class PulseDot extends UiNode<PulseDot> {
        PulseDot() {
            size(8.0F, 8.0F);
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            double t = (UiClock.now() % 2000.0) / 2000.0;
            float c = 4.0F;
            canvas.circle(c, c, 2.0F + (float) t * 3.5F, UiColor.withAlpha(TerminalUi.GREEN, (float) (1.0 - t) * 0.4F));
            canvas.circle(c, c, 2.0F, TerminalUi.GREEN);
        }
    }
}
