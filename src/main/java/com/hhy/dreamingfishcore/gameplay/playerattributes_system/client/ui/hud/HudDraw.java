package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import net.minecraft.client.gui.Font;

import java.util.Locale;

/** 体征 HUD 共用的文字、进度条与分段条绘制，全部画在本帧共享的 HUD 画布上。 */
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
    static float text(UiCanvas canvas, Font font, String text, float x, float y,
                      int color, float alpha, float scale) {
        float width = font.width(text) * scale;
        int textAlpha = Math.round((color >>> 24) * HudPalette.clamp01(alpha));
        if (text.isEmpty() || textAlpha < MIN_TEXT_ALPHA) {
            return width;
        }
        int shadowAlpha = Math.round(textAlpha * 0.5F);
        if (shadowAlpha >= MIN_TEXT_ALPHA) {
            canvas.text(text, x + scale, y + scale, shadowAlpha << 24, scale, false);
        }
        canvas.text(text, x, y, HudPalette.withAlpha(color, textAlpha), scale, false);
        return width;
    }

    /** 快捷栏上方的细长进度条。 */
    static void bar(UiCanvas canvas, int x, int y, int width, float ratio, int color,
                    float alpha, boolean warning) {
        if (alpha <= 0.01F || width <= 2) {
            return;
        }
        float progress = HudPalette.clamp01(ratio);
        float innerWidth = width - 2.0F;
        float fillWidth = innerWidth * progress;
        float radius = BAR_HEIGHT * 0.5F;
        canvas.shape(x + 1, y + 1, width, BAR_HEIGHT).radius(radius)
                .fill(HudPalette.withAlpha(0, Math.round(0x26 * alpha))).draw();
        canvas.shape(x, y, width, BAR_HEIGHT).radius(radius)
                .fill(HudPalette.withAlpha(HudPalette.TRACK, Math.round(168 * alpha)))
                .border(1.0F, HudPalette.withAlpha(HudPalette.TRACK, Math.round(88 * alpha))).draw();
        if (fillWidth > 0.5F) {
            int fillAlpha = Math.round((warning ? 228 : 200) * alpha);
            float innerRadius = (BAR_HEIGHT - 2) * 0.5F;
            canvas.shape(x + 1, y + 1, Math.max(BAR_HEIGHT - 2, fillWidth), BAR_HEIGHT - 2).radius(innerRadius)
                    .horizontalGradient(HudPalette.withAlpha(HudPalette.blend(color, 0xFF000000, 0.12F), fillAlpha),
                            HudPalette.withAlpha(color, fillAlpha)).draw();
            if (fillWidth > 3.0F) {
                canvas.fill(x + 2, y + 1, fillWidth - 2.0F, 1.0F,
                        HudPalette.withAlpha(HudPalette.blend(color, 0xFFFFFFFF, 0.2F), Math.round(fillAlpha * 0.85F)));
            }
        }
    }

    /** 十格分段条，用于饱食与勇气；半格按像素宽度截断。返回宽度。 */
    static int segments(UiCanvas canvas, int x, int y, float ratio, int color, float alpha) {
        float filled = HudPalette.clamp01(ratio) * SEGMENT_COUNT;
        int trackColor = HudPalette.withAlpha(HudPalette.TRACK, Math.round(0xA0 * alpha));
        int fillColor = HudPalette.withAlpha(color, Math.round(0xDC * alpha));
        for (int index = 0; index < SEGMENT_COUNT; index++) {
            int segmentX = x + index * (SEGMENT_WIDTH + SEGMENT_GAP);
            canvas.fill(segmentX, y, SEGMENT_WIDTH, SEGMENT_HEIGHT, trackColor);
            float amount = HudPalette.clamp01(filled - index);
            if (amount > 0.0F) {
                int width = Math.max(1, Math.round(SEGMENT_WIDTH * amount));
                canvas.fill(segmentX, y, width, SEGMENT_HEIGHT, fillColor);
            }
        }
        return SEGMENTS_WIDTH;
    }

    static String number(float value) {
        int rounded = Math.round(value);
        if (Math.abs(value - rounded) < 0.05F) {
            return Integer.toString(rounded);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
