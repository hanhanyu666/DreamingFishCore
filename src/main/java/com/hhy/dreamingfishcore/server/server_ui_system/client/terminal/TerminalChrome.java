package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.core.UiClock;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.CustomPaint;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;

import java.util.function.Supplier;

/**
 * 梦屿终端的外观：一台幸存者随身的野外终端。
 *
 * <p>枪灰色外壳包着一块玻璃屏；屏幕底层极淡地铺着梦屿的等高线地图，灯塔的光束缓慢扫过，
 * 几粒微光向上漂浮（逐光）。登录、对话等界面共用这些部件，保持同一视觉语言。</p>
 */
public final class TerminalChrome {
    /** 灯塔所在的岛屿轮廓参数：固定的谐波组合，形状每次都一样。 */
    private static final float[][] COAST_HARMONICS = {
            {3.0F, 0.16F, 0.7F}, {5.0F, 0.09F, 2.1F}, {8.0F, 0.05F, 4.0F}, {13.0F, 0.025F, 1.3F}
    };
    private static final int CONTOUR_RINGS = 6;
    private static final int CONTOUR_SEGMENTS = 72;
    private static final int MOTES = 18;

    private TerminalChrome() {
    }

    /** 全屏背景：遮罩与暗角。{@code alpha} 用于开合动画。 */
    public static UiNode<?> backdrop(Supplier<Float> alpha) {
        return new Backdrop(alpha);
    }

    // ==================== 品牌 ====================

    /** 品牌标识：灯塔信标——旋转的光弧与中心光点。 */
    public static void paintLogo(UiCanvas canvas, float w, float h) {
        float cx = w * 0.5F;
        float cy = h * 0.5F;
        float r = Math.min(w, h) * 0.5F - 1.5F;
        double t = UiClock.now() / 1000.0;
        canvas.arc(cx, cy, r, 1.6F, (float) (t * 0.6), (float) (Math.PI * 1.55), TerminalUi.CYAN,
                UiColor.withAlpha(TerminalUi.CYAN, 0.15F));
        canvas.arc(cx, cy, r * 0.55F, 1.4F, (float) (-t * 0.9 + Math.PI), (float) (Math.PI * 1.2), TerminalUi.VIOLET,
                UiColor.withAlpha(TerminalUi.VIOLET, 0.2F));
        float glow = (float) (0.75 + 0.25 * Math.sin(t * 2.2));
        canvas.circle(cx, cy, r * 0.42F, UiColor.withAlpha(TerminalUi.GOLD, 0.18F * glow));
        canvas.circle(cx, cy, r * 0.22F, TerminalUi.GOLD);
    }

    public static CustomPaint logo(float size) {
        return (CustomPaint) CustomPaint.of(TerminalChrome::paintLogo).size(size, size);
    }

    /** 品牌名与标语。 */
    public static Box brand() {
        return Ui.row(
                logo(18.0F),
                Ui.column(
                        Text.of("梦屿终端").style(TextStyle.SUBTITLE).singleLine(),
                        Text.of("守望梦屿 · FIELD TERMINAL").style(TextStyle.CAPTION).singleLine()
                                .color(UiColor.withAlpha(TerminalUi.CYAN, 0.75F))
                ).gap(0.0F)
        ).gap(8.0F).alignItems(Align.CENTER);
    }

    // ==================== 设备外壳 ====================

    /**
     * 完整的设备外壳：外圈枪灰色壳体、内嵌玻璃屏、等高线地图水印、灯塔光束、漂浮微光与四角括号。
     * {@code ambient} 为水印与动效的强度（0 关闭）。
     */
    public static void paintDevice(UiCanvas canvas, float w, float h, float radius, float ambient) {
        // 壳体
        canvas.shape(0.0F, 0.0F, w, h).radius(radius)
                .verticalGradient(0xFF1B232B, 0xFF0D1217)
                .border(1.0F, 0xFF2C3946)
                .shadow(new Theme.Shadow(0.0F, 12.0F, 36.0F, 0.0F, 0xA0000000)).draw();
        // 玻璃屏
        float inset = 3.0F;
        float gr = Math.max(2.0F, radius - inset);
        float gw = w - inset * 2.0F;
        float gh = h - inset * 2.0F;
        canvas.shape(inset, inset, gw, gh).radius(gr)
                .verticalGradient(0xFA0F171E, 0xFA090E13)
                .border(1.0F, 0x16FFFFFF).draw();
        if (ambient > 0.01F) {
            canvas.pushClip(inset, inset, gw, gh, gr);
            canvas.pushAlpha(ambient);
            paintIsleMap(canvas, inset, inset, gw, gh);
            paintMotes(canvas, inset, inset, gw, gh);
            canvas.popAlpha();
            canvas.popClip();
        }
        // 顶边高光：两端淡出，中间最亮
        float edge = Math.min(18.0F, w * 0.1F);
        float half = (w - edge * 2.0F) * 0.5F;
        int glow = UiColor.withAlpha(TerminalUi.CYAN, 0.45F);
        int clear = UiColor.withAlpha(TerminalUi.CYAN, 0.0F);
        canvas.shape(edge, inset, half, 1.0F).horizontalGradient(clear, glow).draw();
        canvas.shape(edge + half, inset, half, 1.0F).horizontalGradient(glow, clear).draw();
        paintCornerTicks(canvas, w, h, 9.0F, inset + 4.0F, UiColor.withAlpha(TerminalUi.CYAN, 0.4F));
    }

    /** 旧接口：玻璃面板（带水印）。 */
    public static void paintPanel(UiCanvas canvas, float w, float h, float radius) {
        paintDevice(canvas, w, h, radius, 0.7F);
    }

    /** 四角的 L 形括号。 */
    public static void paintCornerTicks(UiCanvas canvas, float w, float h, float length, float inset, int color) {
        float t = 1.0F;
        float x0 = inset;
        float y0 = inset;
        float x1 = w - inset;
        float y1 = h - inset;
        canvas.fill(x0, y0, length, t, color);
        canvas.fill(x0, y0, t, length, color);
        canvas.fill(x1 - length, y0, length, t, color);
        canvas.fill(x1 - t, y0, t, length, color);
        canvas.fill(x0, y1 - t, length, t, color);
        canvas.fill(x0, y1 - length, t, length, color);
        canvas.fill(x1 - length, y1 - t, length, t, color);
        canvas.fill(x1 - t, y1 - length, t, length, color);
    }

    /** 梦屿等高线：岛心偏右下，内圈暖色，外圈渐隐；海岸上的灯塔光束缓慢扫过。 */
    private static void paintIsleMap(UiCanvas canvas, float ox, float oy, float w, float h) {
        float cx = ox + w * 0.74F;
        float cy = oy + h * 0.6F;
        float base = Math.min(w, h) * 0.46F;
        for (int ring = 0; ring < CONTOUR_RINGS; ring++) {
            float scale = 1.0F - ring * 0.15F;
            float wobble = ring * 0.6F;
            int color = ring >= CONTOUR_RINGS - 2
                    ? UiColor.withAlpha(TerminalUi.GOLD, 0.09F + ring * 0.008F)
                    : UiColor.withAlpha(TerminalUi.CYAN, 0.07F + ring * 0.008F);
            float prevX = 0.0F;
            float prevY = 0.0F;
            for (int i = 0; i <= CONTOUR_SEGMENTS; i++) {
                float angle = (float) (Math.PI * 2.0 * i / CONTOUR_SEGMENTS);
                float r = base * scale * coast(angle, wobble);
                float x = cx + (float) Math.cos(angle) * r * 1.25F;
                float y = cy + (float) Math.sin(angle) * r * 0.8F;
                if (i > 0) {
                    canvas.line(prevX, prevY, x, y, 0.8F, color, false);
                }
                prevX = x;
                prevY = y;
            }
        }
        // 灯塔：外圈海岸线上的一点，光束绕它旋转
        float lighthouseAngle = 3.55F;
        float lr = base * coast(lighthouseAngle, 0.0F);
        float lx = cx + (float) Math.cos(lighthouseAngle) * lr * 1.25F;
        float ly = cy + (float) Math.sin(lighthouseAngle) * lr * 0.8F;
        double t = UiClock.now() / 1000.0;
        float beam = (float) (t * 0.35 % (Math.PI * 2.0));
        float length = Math.max(w, h) * 0.9F;
        for (int i = -3; i <= 3; i++) {
            float a = beam + i * 0.035F;
            float alpha = 0.085F * (1.0F - Math.abs(i) / 4.0F);
            canvas.line(lx, ly, lx + (float) Math.cos(a) * length, ly + (float) Math.sin(a) * length, 2.2F,
                    UiColor.withAlpha(0xFFFFE6A8, alpha), false);
        }
        float blink = (float) (0.6 + 0.4 * Math.sin(t * 3.0));
        canvas.circle(lx, ly, 5.0F, UiColor.withAlpha(TerminalUi.GOLD, 0.12F * blink));
        canvas.circle(lx, ly, 1.6F, UiColor.withAlpha(TerminalUi.GOLD, 0.65F));
        // 岛心标记
        canvas.circle(cx, cy, 1.2F, UiColor.withAlpha(TerminalUi.GOLD, 0.25F));
    }

    private static float coast(float angle, float wobble) {
        float value = 1.0F;
        for (float[] harmonic : COAST_HARMONICS) {
            value += harmonic[1] * (float) Math.sin(harmonic[0] * angle + harmonic[2] + wobble);
        }
        return value;
    }

    /** 逐光：几粒微光从屏幕底部缓慢上升、明灭。位置由序号决定，不随机抖动。 */
    private static void paintMotes(UiCanvas canvas, float ox, float oy, float w, float h) {
        double t = UiClock.now() / 1000.0;
        for (int i = 0; i < MOTES; i++) {
            float seedX = hash(i * 12.9898F);
            float seedY = hash(i * 78.233F);
            float speed = 5.0F + hash(i * 3.17F) * 9.0F;
            float x = ox + seedX * w + (float) Math.sin(t * 0.4 + i) * 6.0F;
            float travel = h + 20.0F;
            float y = oy + h + 10.0F - (float) ((t * speed + seedY * travel) % travel);
            float flicker = (float) (0.5 + 0.5 * Math.sin(t * (1.2 + seedX) + i * 1.7));
            // 只在下半部分可见，升到中段前逐渐熄灭，避免像灰尘一样落在正文上
            float rise = (oy + h - y) / h;
            float fade = Math.max(0.0F, Math.min(1.0F, (0.55F - rise) / 0.35F));
            if (fade <= 0.0F) {
                continue;
            }
            float size = 0.7F + hash(i * 5.31F) * 0.9F;
            int color = i % 3 == 0 ? 0xFFFFD99A : TerminalUi.CYAN;
            canvas.circle(x, y, size * 2.6F, UiColor.withAlpha(color, 0.05F * flicker * fade));
            canvas.circle(x, y, size, UiColor.withAlpha(color, (0.18F + 0.32F * flicker) * fade));
        }
    }

    private static float hash(float value) {
        float s = (float) Math.sin(value) * 43758.5453F;
        return s - (float) Math.floor(s);
    }

    /** 面板容器：纵向排列，背景为设备外壳。 */
    public static Box panel() {
        return new Panel();
    }

    private static final class Panel extends Box {
        Panel() {
            column().alignItems(Align.STRETCH).radius(Theme.Radius.XL);
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            paintPanel(canvas, width(), height(), Theme.Radius.XL);
        }
    }

    private static final class Backdrop extends UiNode<Backdrop> {
        private final Supplier<Float> alpha;

        Backdrop(Supplier<Float> alpha) {
            this.alpha = alpha;
            pointerEvents(false);
        }

        @Override
        protected void paintContent(UiCanvas canvas) {
            float p = alpha.get();
            float w = width();
            float h = height();
            canvas.pushAlpha(Math.min(1.0F, p * 1.4F));
            canvas.fill(0.0F, 0.0F, w, h, 0x9604080C);
            canvas.shape(0.0F, 0.0F, w, h)
                    .radial(0x00000000, 0xA0000000, w * 0.5F, h * 0.5F, (float) Math.hypot(w, h) * 0.55F).draw();
            canvas.popAlpha();
        }
    }
}
