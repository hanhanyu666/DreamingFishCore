package com.hhy.dreamingfishcore.client.ui.loading;

import com.hhy.dreamingfishcore.client.ui.util.UiBackgroundRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Shared drawing helpers for loading and waiting screens. */
public final class LoadingScreenUi {
    private LoadingScreenUi() {
    }

    public static void renderBackground(GuiGraphics guiGraphics, int width, int height) {
        UiBackgroundRenderer.renderLoadingBackground(guiGraphics, width, height);
        guiGraphics.fillGradient(0, 0, width, height, 0x88000000, 0xCC000000);
    }

    public static void renderTip(GuiGraphics guiGraphics, Font font, String tip) {
        guiGraphics.drawString(font, "§e💡 提示", 8, 8, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, "§7" + (tip == null ? "" : tip), 8, 21, 0xFFAAAAAA, true);
    }

    public static void renderProgressBar(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                         int progress, int backgroundColor, int accentColor, int highlightColor) {
        drawRoundedBar(guiGraphics, x, y, width, height, backgroundColor);
        int clampedProgress = Math.max(0, Math.min(100, progress));
        int fillWidth = width * clampedProgress / 100;
        if (fillWidth <= 0) {
            return;
        }

        drawRoundedBar(guiGraphics, x, y, fillWidth, height, accentColor);
        if (fillWidth > 2) {
            guiGraphics.fill(x + 2, y, x + fillWidth - 2, y + 1, highlightColor);
        }
    }

    public static String trimToWidth(String text, Font font, int maxWidth) {
        if (text == null || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }

        String ellipsis = "...";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    private static void drawRoundedBar(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int radius = height >= 6 ? height / 3 : 1;
        int innerHeight = Math.max(1, height - 2);
        int left = x + radius;
        int right = x + width - radius;
        if (right > left) {
            guiGraphics.fill(left, y, right, y + height, color);
        }
        guiGraphics.fill(x, y + 1, x + radius, y + 1 + innerHeight, color);
        guiGraphics.fill(x + width - radius, y + 1, x + width, y + 1 + innerHeight, color);
    }
}
