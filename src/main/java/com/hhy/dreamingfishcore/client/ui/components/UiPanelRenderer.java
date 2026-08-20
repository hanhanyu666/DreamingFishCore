package com.hhy.dreamingfishcore.client.ui.components;

import net.minecraft.client.gui.GuiGraphics;

/** Shared primitives for the small panels used by HUD and notification UI. */
public final class UiPanelRenderer {
    private UiPanelRenderer() {
    }

    public static void roundedRect(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                   int radius, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
            return;
        }

        int r = Math.max(0, Math.min(radius, Math.min(width / 2, height / 2)));
        int right = x + width;
        int bottom = y + height;

        if (r == 0) {
            guiGraphics.fill(x, y, right, bottom, color);
            return;
        }

        // Draw the rounded rectangle as non-overlapping regions. This is important for
        // translucent colors: overlapping fills would blend twice and create a darker inner layer.
        guiGraphics.fill(x + r, y, right - r, y + r, color);
        guiGraphics.fill(x, y + r, right, bottom - r, color);
        guiGraphics.fill(x + r, bottom - r, right - r, bottom, color);

        if (r >= 2) {
            guiGraphics.fill(x + 1, y + 1, x + r, y + r, color);
            guiGraphics.fill(right - r, y + 1, right - 1, y + r, color);
            guiGraphics.fill(x + 1, bottom - r, x + r, bottom - 1, color);
            guiGraphics.fill(right - r, bottom - r, right - 1, bottom - 1, color);
        }
    }

    public static void roundedBorder(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                     int radius, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
            return;
        }

        int r = Math.max(0, Math.min(radius, Math.min(width / 2, height / 2)));
        int right = x + width;
        int bottom = y + height;
        guiGraphics.fill(x + r, y, right - r, y + 1, color);
        guiGraphics.fill(x + r, bottom - 1, right - r, bottom, color);
        guiGraphics.fill(x, y + r, x + 1, bottom - r, color);
        guiGraphics.fill(right - 1, y + r, right, bottom - r, color);

        if (r >= 2) {
            guiGraphics.fill(x + 1, y + 1, x + 2, y + 2, color);
            guiGraphics.fill(right - 2, y + 1, right - 1, y + 2, color);
            guiGraphics.fill(x + 1, bottom - 2, x + 2, bottom - 1, color);
            guiGraphics.fill(right - 2, bottom - 2, right - 1, bottom - 1, color);
        }
    }

    public static int withAlpha(int color, int alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (clampedAlpha << 24);
    }
}
