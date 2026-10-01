package com.hhy.dreamingfishcore.client.ui.loading;

import com.hhy.dreamingfishcore.client.ui.framework.core.UiClock;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.Cursor;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.screen.ScreenHost;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.InteractiveNode;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.client.ui.util.LoadingTips;
import com.hhy.dreamingfishcore.client.ui.util.UiBackgroundRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 统一的加载画面：背景图 + 左上“梦屿电台”提示 + 左下信号波形与进度 + 右下 Esc 操作。
 *
 * <p>连接服务器、接收世界、生成区块、准备世界、等待与资源重载都用同一个外观，
 * 屏幕切换时看起来是同一个画面在推进。每帧由调用方写入状态后调用 {@link #render}。</p>
 *
 * <p>首次启动的资源加载阶段字体和着色器都还没就绪，{@link #startup(boolean)} 模式只画背景与波形条，
 * 框架在着色器缺失时会自动退回原版矩形绘制。</p>
 */
public final class LoadingSurface {
    static final int AMBER = 0xFFE0B457;
    static final int AMBER_BRIGHT = 0xFFFFE0A0;
    static final int AMBER_DIM = 0xFFC4A777;
    static final int TIP = 0xFFEADCB8;

    private final ScreenHost host = new ScreenHost(this::build);
    private String tip;
    private String status = "";
    private int progress = -1;
    private String action;
    private Runnable onAction;
    private float opacity = 1.0F;
    private boolean startup;
    private int failureAccent;
    private String failureDetail = "";
    private String failureState = "";

    public LoadingSurface() {
        this(LoadingTips.getRandomTip());
    }

    public LoadingSurface(String tip) {
        this.tip = tip == null ? "" : tip;
    }

    public ScreenHost host() {
        return host;
    }

    public String tip() {
        return tip;
    }

    public String status() {
        return status;
    }

    public int progress() {
        return progress;
    }

    public String action() {
        return action;
    }

    public LoadingSurface status(String value) {
        status = value == null ? "" : value;
        return this;
    }

    /** 进度 0~100；小于 0 表示没有可用进度，波形改为来回扫描。 */
    public LoadingSurface progress(int value) {
        progress = value < 0 ? -1 : Math.min(100, value);
        return this;
    }

    /** 右下角 Esc 操作；{@code label} 为 null 时不显示。 */
    public LoadingSurface action(String label, Runnable handler) {
        action = label;
        onAction = handler;
        return this;
    }

    public LoadingSurface opacity(float value) {
        opacity = Math.max(0.0F, Math.min(1.0F, value));
        return this;
    }

    /**
     * 失败模式（断线等）：状态换成强调色标题与原因，波形中途断开，画面带一层暗红。
     */
    public LoadingSurface failure(String title, String detail, String state, int accent) {
        status(title);
        failureDetail = detail == null ? "" : detail;
        failureState = state == null ? "" : state;
        if (failureAccent != accent) {
            failureAccent = accent;
            host.rebuild();
        }
        return this;
    }

    /** 启动阶段：固定首张背景、不画文字和图标。 */
    public LoadingSurface startup(boolean value) {
        if (startup != value) {
            startup = value;
            host.rebuild();
        }
        return this;
    }

    /** 没有真实进度时按指数逼近估算：从 {@code start} 起，越接近 {@code end} 越慢。 */
    public static int estimateProgress(long startedAt, long now, int start, int end, long durationMillis) {
        if (end <= start || startedAt < 0L) {
            return Math.max(0, Math.min(100, start));
        }
        long elapsed = Math.max(0L, now - startedAt);
        double normalized = 1.0 - Math.exp(-elapsed / (double) Math.max(1L, durationMillis));
        return Math.min(end, start + (int) Math.round((end - start) * normalized));
    }

    public void render(GuiGraphics graphics, int width, int height) {
        if (startup) {
            UiBackgroundRenderer.renderStartupBackground(graphics, width, height, opacity);
        } else {
            UiBackgroundRenderer.renderLoadingBackground(graphics, width, height, opacity);
        }
        host.render(graphics, width, height);
    }

    // ==================== 构建 ====================

    private UiNode<?> build() {
        Box root = Ui.stack(new Shade()).alignItems(Align.STRETCH);
        if (!startup) {
            root.add(tipCard().absolute(18.0F, 14.0F, Float.NaN, Float.NaN));
        }
        root.add(statusBlock().absolute(24.0F, Float.NaN, Float.NaN, 22.0F));
        if (!startup) {
            root.add(new ActionChip().absolute(Float.NaN, Float.NaN, 24.0F, 22.0F));
        }
        root.onUpdate(() -> root.opacity(opacity));
        return root;
    }

    private UiNode<?> tipCard() {
        String shown = tip.replace("§7", "§r");
        Box card = Ui.row(
                new RadioIcon().size(16.0F, 16.0F),
                Ui.column(
                        Text.of("梦屿电台 · 随机播报").style(TextStyle.CAPTION).color(UiColor.withAlpha(AMBER_DIM, 0.85F)).singleLine(),
                        Text.of(shown).style(TextStyle.LABEL.withLineGap(2.0F)).color(TIP).maxLines(2).shadow(true)
                ).gap(3.0F).shrink(1.0F)
        ).gap(Theme.Space.MD).alignItems(Align.START).maxWidth(300.0F);
        return card;
    }

    private UiNode<?> statusBlock() {
        if (failureAccent != 0) {
            return failureBlock();
        }
        Box block = Ui.column().alignItems(Align.START).gap(5.0F);
        if (!startup) {
            block.add(Ui.row(new SignalDot().size(5.0F, 5.0F),
                            Text.of("RELAY · 梦屿中继").style(TextStyle.CAPTION).color(UiColor.withAlpha(AMBER_DIM, 0.8F)).singleLine())
                            .gap(5.0F).alignItems(Align.CENTER),
                    Text.of(() -> Component.literal(status)).style(TextStyle.SUBTITLE).color(AMBER).singleLine().shadow(true)
                            .maxWidth(320.0F));
        }
        Box wave = Ui.row(new Waveform().size(startup ? 240.0F : 196.0F, 20.0F)).gap(Theme.Space.MD).alignItems(Align.CENTER);
        if (!startup) {
            Text percent = Text.of(() -> Component.literal(progress < 0 ? "" : progress + "%")).style(TextStyle.LABEL_STRONG)
                    .color(AMBER_BRIGHT).singleLine().shadow(true);
            wave.add(percent);
        }
        block.add(wave);
        return block;
    }

    private UiNode<?> failureBlock() {
        int accent = failureAccent;
        Box block = Ui.column().alignItems(Align.START).gap(5.0F);
        block.add(Ui.row(new SignalDot().size(5.0F, 5.0F),
                                Text.of("RELAY · 信号中断").style(TextStyle.CAPTION).color(UiColor.withAlpha(accent, 0.8F)).singleLine())
                        .gap(5.0F).alignItems(Align.CENTER),
                Text.of(() -> Component.literal(status)).style(TextStyle.TITLE).color(accent).singleLine().shadow(true),
                Text.of(() -> Component.literal(failureDetail)).style(TextStyle.LABEL.withLineGap(2.0F)).color(0xE0D2BFB8)
                        .maxLines(3).shadow(true).maxWidth(380.0F),
                Ui.row(new Waveform().size(196.0F, 20.0F),
                                Text.of(() -> Component.literal(failureState)).style(TextStyle.LABEL_STRONG)
                                        .color(0xC8B7A39D).singleLine().shadow(true))
                        .gap(Theme.Space.MD).alignItems(Align.CENTER).margin(0.0F, 4.0F, 0.0F, 0.0F));
        return block;
    }

    // ==================== 部件 ====================

    /** 读字用的压暗：整体自上而下加深，左下角再压一层托住状态文字；失败时叠一层暗红。 */
    private final class Shade extends UiNode<Shade> {
        private final double createdAt = UiClock.now();

        Shade() {
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float w = width();
            float h = height();
            canvas.shape(0.0F, 0.0F, w, h).verticalGradient(0x10000000, 0x78000000).draw();
            if (failureAccent != 0) {
                float intro = Math.min(1.0F, (float) ((UiClock.now() - createdAt) / 360.0));
                intro = 1.0F - (1.0F - intro) * (1.0F - intro) * (1.0F - intro);
                canvas.shape(0.0F, 0.0F, w, h).verticalGradient(UiColor.withAlpha(0xFF35070B, 0.27F * intro),
                        UiColor.withAlpha(0xFF180205, 0.5F * intro)).draw();
            }
            canvas.shape(0.0F, 0.0F, w, 70.0F).verticalGradient(0x55000000, 0x00000000).draw();
            canvas.shape(0.0F, h * 0.6F, w * 0.7F, h * 0.4F).radial(0x66000000, 0x00000000, 0.0F, h * 0.4F, w * 0.6F).draw();
        }
    }

    /** 电台图标，信号弧随时间闪烁。 */
    private static final class RadioIcon extends UiNode<RadioIcon> {
        RadioIcon() {
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            int size = Math.round(Math.min(width(), height()));
            canvas.custom(0.0F, 0.0F, size, size, g -> UiBackgroundRenderer.renderRadioIcon(g, 0, 0, size, 1.0F));
        }
    }

    /** 呼吸的信号灯。 */
    private static final class SignalDot extends UiNode<SignalDot> {
        SignalDot() {
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(UiClock.now() / 260.0));
            float r = width() * 0.5F;
            canvas.circle(r, r, r + pulse * 1.5F, UiColor.withAlpha(AMBER, 0.18F * pulse));
            canvas.circle(r, r, r * 0.8F, UiColor.withAlpha(AMBER_BRIGHT, 0.55F + 0.45F * pulse));
        }
    }

    /** 信号波形：已完成的部分为琥珀色；无进度时一段亮带来回扫描。 */
    private final class Waveform extends UiNode<Waveform> {
        Waveform() {
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float w = width();
            float h = height();
            float cy = h * 0.5F;
            double now = UiClock.now();
            int bars = Math.max(24, (int) (w / 3.0F));
            float barW = w / bars;
            canvas.fill(0.0F, cy - 0.25F, w, 0.5F, 0x7A80622F);
            if (failureAccent != 0) {
                paintInterrupted(canvas, w, h, cy, now);
                return;
            }
            float done = progress < 0 ? -1.0F : progress / 100.0F;
            float scan = (float) ((Math.sin(now / 700.0) * 0.5 + 0.5));
            for (int i = 0; i < bars; i++) {
                float t = (i + 0.5F) / bars;
                double pattern = Math.abs(Math.sin(i * 0.73) * Math.cos(i * 0.19 + 0.8));
                double breathing = 0.78 + Math.sin(now / 220.0 + i * 0.47) * 0.22;
                float amp = 1.5F + (float) ((h * 0.42) * pattern * breathing);
                int color;
                if (done >= 0.0F) {
                    color = t <= done ? AMBER : 0x5C9C7840;
                } else {
                    float band = Math.max(0.0F, 1.0F - Math.abs(t - scan) * 6.0F);
                    color = UiColor.lerp(0x5C9C7840, AMBER, band);
                }
                canvas.shape(i * barW + barW * 0.18F, cy - amp, barW * 0.64F, amp * 2.0F).radius(barW * 0.3F)
                        .fill(color).draw();
            }
            float cursor = done >= 0.0F ? done : scan;
            float cx = Math.min(w - 1.0F, w * cursor);
            canvas.shape(cx - 8.0F, -4.0F, 16.0F, h + 8.0F).radial(0x50FFE0A0, 0x00FFE0A0, 8.0F, (h + 8.0F) * 0.5F, 9.0F).draw();
            canvas.shape(cx - 0.5F, -2.0F, 1.0F, h + 4.0F).radius(0.5F).fill(AMBER_BRIGHT).draw();
            canvas.circle(cx, -2.0F, 1.5F, AMBER_BRIGHT);
        }
    }

    /** 断开的信号：前段正常起伏，断点之后只剩零星的残余脉冲。 */
    private void paintInterrupted(UiCanvas canvas, float w, float h, float cy, double now) {
        int accent = failureAccent;
        canvas.fill(0.0F, cy - 0.25F, w, 0.5F, UiColor.withAlpha(accent, 0.2F));
        int bars = Math.max(24, (int) (w / 4.0F));
        float barW = w / bars;
        int visibleUntil = Math.max(5, bars * 2 / 5);
        for (int i = 0; i < bars; i++) {
            boolean alive = i <= visibleUntil;
            if (!alive && (i % 5 != 0 || i > visibleUntil + 10)) {
                continue;
            }
            float amp = alive ? 1.0F + (float) (Math.abs(Math.sin(i * 0.83)) * (h * 0.3)) : 1.0F;
            float flicker = alive ? 1.0F : (float) (0.25 + 0.2 * Math.sin(now / 180.0 + i));
            canvas.shape(i * barW + barW * 0.2F, cy - amp, barW * 0.6F, amp * 2.0F).radius(barW * 0.3F)
                    .fill(UiColor.withAlpha(accent, flicker)).draw();
        }
        float breakX = w * 0.47F;
        float blink = (float) (0.55 + 0.45 * Math.sin(now / 240.0));
        canvas.shape(breakX - 1.0F, cy - 7.0F, 2.0F, 15.0F).radius(1.0F).fill(UiColor.withAlpha(accent, 0.7F * blink)).draw();
    }

    /** 右下角的 Esc 键帽与说明，点击等同按下 Esc。 */
    private final class ActionChip extends InteractiveNode<ActionChip> {
        private final Text label = Text.of(() -> Component.literal(action == null ? "" : action)).style(TextStyle.LABEL)
                .singleLine().shadow(true);

        ActionChip() {
            row().alignItems(Align.CENTER).gap(6.0F).padding(6.0F, 4.0F).radius(Theme.Radius.MD);
            add(new KeyCap(), label);
            cursor(Cursor.POINTER);
            onClick(() -> {
                if (onAction != null) {
                    onAction.run();
                }
            });
        }

        @Override
        protected void update() {
            visible(action != null);
            label.color(UiColor.lerp(AMBER_DIM, AMBER_BRIGHT, hover()));
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            float h = hover();
            if (h > 0.01F) {
                canvas.shape(0.0F, 0.0F, width(), height()).radius(radiusValue())
                        .fill(UiColor.withAlpha(0xFF1A140A, 0.6F * h)).border(1.0F, UiColor.withAlpha(AMBER, 0.4F * h)).draw();
            }
        }
    }

    private static final class KeyCap extends Box {
        KeyCap() {
            stack().alignItems(Align.CENTER).padding(5.0F, 2.0F).radius(Theme.Radius.SM);
            add(Text.of("Esc").style(TextStyle.CAPTION_STRONG).color(AMBER_DIM).singleLine());
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            canvas.shape(0.0F, 1.5F, width(), height()).radius(radiusValue()).fill(0x80000000).draw();
            canvas.shape(0.0F, 0.0F, width(), height()).radius(radiusValue()).verticalGradient(0xE03A3122, 0xE0282015)
                    .border(1.0F, UiColor.withAlpha(AMBER_DIM, 0.55F)).draw();
        }
    }
}
