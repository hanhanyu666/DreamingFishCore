package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import com.hhy.dreamingfishcore.client.ui.components.UiPanelRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/** 体征 HUD 共用的文字、进度条与分段条绘制。所有方法都在共享 GUI 批次内追加几何。 */
final class HudDraw {
    static final int BAR_HEIGHT = 5;
    static final int SEGMENT_COUNT = 10;
    static final int SEGMENT_WIDTH = 3;
    static final int SEGMENT_GAP = 1;
    static final int SEGMENT_HEIGHT = 4;
    static final int SEGMENTS_WIDTH = SEGMENT_COUNT * (SEGMENT_WIDTH + SEGMENT_GAP) - SEGMENT_GAP;
    /** Font 会把透明度过低的颜色当作不透明处理，低于此值时直接跳过绘制。 */
    private static final int MIN_TEXT_ALPHA = 8;

    private HudDraw() {
    }

    /** 带柔和投影的缩放文字；返回绘制宽度（GUI 像素）。 */
    static float text(GuiGraphics graphics, Font font, String text, float x, float y,
                      int color, float alpha, float scale) {
        float width = font.width(text) * scale;
        int textAlpha = Math.round((color >>> 24) * HudPalette.clamp01(alpha));
        if (text.isEmpty() || textAlpha < MIN_TEXT_ALPHA) {
            return width;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        int shadowAlpha = Math.round(textAlpha * 0.5F);
        if (shadowAlpha >= MIN_TEXT_ALPHA) {
            graphics.drawString(font, text, 1, 1, shadowAlpha << 24, false);
        }
        graphics.drawString(font, text, 0, 0, HudPalette.withAlpha(color, textAlpha), false);
        graphics.pose().popPose();
        return width;
    }

    /** 快捷栏上方的细长进度条。 */
    static void bar(GuiGraphics graphics, int x, int y, int width, float ratio, int color,
                    float alpha, boolean warning) {
        if (alpha <= 0.01F || width <= 2) {
            return;
        }
        float progress = HudPalette.clamp01(ratio);
        int innerWidth = width - 2;
        int fillWidth = (int) (innerWidth * progress);
        rect(graphics, x + 1, y + 1, width, BAR_HEIGHT, HudPalette.withAlpha(0, Math.round(0x26 * alpha)));
        rect(graphics, x, y, width, BAR_HEIGHT, HudPalette.withAlpha(HudPalette.TRACK, Math.round(168 * alpha)));
        rect(graphics, x + 1, y + 1, width - 2, BAR_HEIGHT - 2,
                HudPalette.withAlpha(HudPalette.TRACK, Math.round(88 * alpha)));
        if (fillWidth > 0) {
            int fillAlpha = Math.round((warning ? 228 : 200) * alpha);
            rect(graphics, x + 1, y + 1, fillWidth, BAR_HEIGHT - 2, HudPalette.withAlpha(color, fillAlpha));
            if (fillWidth > 2) {
                graphics.fill(x + 2, y + 1, x + fillWidth, y + 2,
                        HudPalette.withAlpha(HudPalette.blend(color, 0xFFFFFFFF, 0.2F), Math.round(fillAlpha * 0.85F)));
            }
        }
    }

    /** 十格分段条，用于饱食与勇气；半格按像素宽度截断。返回宽度。 */
    static int segments(GuiGraphics graphics, int x, int y, float ratio, int color, float alpha) {
        float filled = HudPalette.clamp01(ratio) * SEGMENT_COUNT;
        int trackColor = HudPalette.withAlpha(HudPalette.TRACK, Math.round(0xA0 * alpha));
        int fillColor = HudPalette.withAlpha(color, Math.round(0xDC * alpha));
        for (int index = 0; index < SEGMENT_COUNT; index++) {
            int segmentX = x + index * (SEGMENT_WIDTH + SEGMENT_GAP);
            graphics.fill(segmentX, y, segmentX + SEGMENT_WIDTH, y + SEGMENT_HEIGHT, trackColor);
            float amount = HudPalette.clamp01(filled - index);
            if (amount > 0.0F) {
                int width = Math.max(1, Math.round(SEGMENT_WIDTH * amount));
                graphics.fill(segmentX, y, segmentX + width, y + SEGMENT_HEIGHT, fillColor);
            }
        }
        return SEGMENTS_WIDTH;
    }

    static void rect(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
            return;
        }
        int radius = Math.max(1, (Math.min(width, height) + 1) / 2);
        UiPanelRenderer.crispRoundedRectBatched(graphics, x, y, width, height, radius, color);
    }

    static String number(float value) {
        int rounded = Math.round(value);
        if (Math.abs(value - rounded) < 0.05F) {
            return Integer.toString(rounded);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
