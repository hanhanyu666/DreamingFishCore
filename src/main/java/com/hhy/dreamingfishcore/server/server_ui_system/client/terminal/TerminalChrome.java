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
 * 梦屿终端的外观部件：背景网格与暗角、品牌标识、玻璃面板。登录、对话等界面共用，保持同一视觉语言。
 */
public final class TerminalChrome {
    private TerminalChrome() {
    }

    /** 全屏背景：遮罩、暗角与细网格。{@code alpha} 用于开合动画。 */
    public static UiNode<?> backdrop(Supplier<Float> alpha) {
        return new Backdrop(alpha);
    }

    /** 品牌标识：旋转的双环与中心光点。 */
    public static void paintLogo(UiCanvas canvas, float w, float h) {
        float cx = w * 0.5F;
        float cy = h * 0.5F;
        float r = Math.min(w, h) * 0.5F - 1.5F;
        double t = UiClock.now() / 1000.0;
        canvas.arc(cx, cy, r, 1.6F, (float) (t * 0.6), (float) (Math.PI * 1.55), TerminalUi.CYAN,
                UiColor.withAlpha(TerminalUi.CYAN, 0.15F));
        canvas.arc(cx, cy, r * 0.55F, 1.4F, (float) (-t * 0.9 + Math.PI), (float) (Math.PI * 1.2), TerminalUi.VIOLET,
                UiColor.withAlpha(TerminalUi.VIOLET, 0.2F));
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
                        Text.of("DREAMING FISH · WATCH OVER THE ISLE").style(TextStyle.CAPTION).singleLine()
                                .color(UiColor.withAlpha(TerminalUi.CYAN, 0.7F))
                ).gap(0.0F)
        ).gap(8.0F).alignItems(Align.CENTER);
    }

    /** 玻璃质感的面板背景：纵向渐变、描边、阴影与顶部高光。 */
    public static void paintPanel(UiCanvas canvas, float w, float h, float radius) {
        canvas.shape(0.0F, 0.0F, w, h).radius(radius)
                .verticalGradient(0xF6121B23, 0xF60A1016)
                .border(1.0F, 0xFF223140)
                .shadow(new Theme.Shadow(0.0F, 10.0F, 32.0F, 0.0F, 0x99000000)).draw();
        // 顶边高光：两端淡出，中间最亮
        float inset = Math.min(14.0F, w * 0.1F);
        float half = (w - inset * 2.0F) * 0.5F;
        int glow = UiColor.withAlpha(TerminalUi.CYAN, 0.4F);
        int clear = UiColor.withAlpha(TerminalUi.CYAN, 0.0F);
        canvas.shape(inset, 0.0F, half, 1.0F).horizontalGradient(clear, glow).draw();
        canvas.shape(inset + half, 0.0F, half, 1.0F).horizontalGradient(glow, clear).draw();
    }

    /** 面板容器：纵向排列，背景为玻璃面板。 */
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
            canvas.fill(0.0F, 0.0F, w, h, 0x8C04080C);
            canvas.shape(0.0F, 0.0F, w, h)
                    .radial(0x00000000, 0x99000000, w * 0.5F, h * 0.5F, (float) Math.hypot(w, h) * 0.55F).draw();
            int grid = UiColor.withAlpha(TerminalUi.CYAN, 0.035F);
            for (float gx = 24.0F; gx < w; gx += 24.0F) {
                canvas.fill(gx, 0.0F, 0.5F, h, grid);
            }
            for (float gy = 24.0F; gy < h; gy += 24.0F) {
                canvas.fill(0.0F, gy, w, 0.5F, grid);
            }
            canvas.popAlpha();
        }
    }
}
