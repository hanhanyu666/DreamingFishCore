package com.hhy.dreamingfishcore.client.ui.loading;

import com.hhy.dreamingfishcore.client.ui.util.UiBackgroundRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Shared drawing helpers for the immersive loading screens. */
public final class LoadingScreenUi {
    private static final int TIP_COLOR = 0xFFD8BE82;
    private static final int STATUS_COLOR = 0xFFE4B95F;
    private static final int WAVE_ACTIVE = 0xFFE0B457;
    private static final int WAVE_ACTIVE_HIGHLIGHT = 0xFFFFE0A0;
    private static final int WAVE_INACTIVE = 0x5C9C7840;
    private static final int WAVE_BASELINE = 0x7A80622F;
    private static final int CANCEL_COLOR = 0xC5C4A777;
    private static final int CANCEL_KEY_COLOR = 0xD05A5138;
    private static final float CANCEL_TEXT_SCALE = 0.8F;

    private LoadingScreenUi() {
    }

    public static void renderBackground(GuiGraphics guiGraphics, int width, int height) {
        renderBackground(guiGraphics, width, height, 1.0F);
    }

    public static void renderBackground(GuiGraphics guiGraphics, int width, int height, float opacity) {
        UiBackgroundRenderer.renderLoadingBackground(guiGraphics, width, height, opacity);
        guiGraphics.fillGradient(0, 0, width, height,
                withOpacity(0x10000000, opacity), withOpacity(0x78000000, opacity));
        int leftShadeWidth = Math.min(width, Math.max(160, width / 2));
        guiGraphics.fill(0, 0, leftShadeWidth, height, withOpacity(0x12000000, opacity));
    }

    /** Draws the existing random loading tip without a visible "tip" heading. */
    public static void renderTip(GuiGraphics guiGraphics, Font font, String tip) {
        renderTip(guiGraphics, font, tip, 250, 1.0F);
    }

    public static void renderTip(GuiGraphics guiGraphics, Font font, String tip, int maxTextWidth) {
        renderTip(guiGraphics, font, tip, maxTextWidth, 1.0F);
    }

    public static void renderTip(GuiGraphics guiGraphics, Font font, String tip, int maxTextWidth,
                                 float opacity) {
        int iconX = 20;
        int iconY = 16;
        int iconSize = 14;
        UiBackgroundRenderer.renderRadioIcon(guiGraphics, iconX, iconY, iconSize, opacity);

        String plainTip = ChatFormatting.stripFormatting(tip);
        if (plainTip == null || plainTip.isBlank()) {
            return;
        }

        int textX = iconX + iconSize + 8;
        int availableWidth = Math.max(40, maxTextWidth);
        List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(Component.literal(plainTip), availableWidth);
        int lineCount = Math.min(2, lines.size());
        for (int i = 0; i < lineCount; i++) {
            guiGraphics.drawString(font, lines.get(i), textX, iconY + i * (font.lineHeight + 2),
                    withOpacity(TIP_COLOR, opacity), true);
        }
    }

    /** Renders the lower-left status, an animated waveform, and a compact progress number. */
    public static void renderStatusWaveform(GuiGraphics guiGraphics, Font font, int virtualWidth,
                                             int virtualHeight, String statusText, int progress, long now) {
        renderStatusWaveform(guiGraphics, font, virtualWidth, virtualHeight,
                statusText, progress, now, 1.0F);
    }

    public static void renderStatusWaveform(GuiGraphics guiGraphics, Font font, int virtualWidth,
                                             int virtualHeight, String statusText, int progress, long now,
                                             float opacity) {
        int margin = 24;
        int statusY = virtualHeight - 84;
        int waveY = virtualHeight - 54;
        int percentWidth = font.width("100%");
        int waveWidth = Math.min(190, Math.max(100, virtualWidth - margin * 2 - percentWidth - 18));

        guiGraphics.drawString(font, trimToWidth(statusText, font, waveWidth + 30), margin, statusY,
                withOpacity(STATUS_COLOR, opacity), true);
        renderWaveform(guiGraphics, margin, waveY, waveWidth, 18, progress, now, opacity);

        int clampedProgress = clampProgress(progress);
        String progressText = clampedProgress + "%";
        guiGraphics.drawString(font, progressText, margin + waveWidth + 10, waveY + 4,
                withOpacity(WAVE_ACTIVE_HIGHLIGHT, opacity), true);
    }

    /** Draws a compact status for waiting screens that do not expose meaningful progress. */
    public static void renderBottomStatusText(GuiGraphics guiGraphics, Font font, int virtualWidth,
                                              int virtualHeight, String statusText) {
        renderBottomStatusText(guiGraphics, font, virtualWidth, virtualHeight, statusText, 1.0F);
    }

    public static void renderBottomStatusText(GuiGraphics guiGraphics, Font font, int virtualWidth,
                                               int virtualHeight, String statusText, float opacity) {
        int margin = 24;
        int maxWidth = Math.max(40, virtualWidth - margin * 2);
        int y = Math.max(8, virtualHeight - margin - font.lineHeight);
        guiGraphics.drawString(font, trimToWidth(statusText, font, maxWidth), margin, y,
                withOpacity(STATUS_COLOR, opacity), true);
    }

    private static void renderWaveform(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                        int progress, long now, float opacity) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int clampedProgress = clampProgress(progress);
        int centerY = y + height / 2;
        int segmentCount = Math.max(32, width / 2);
        int activeSegments = segmentCount * clampedProgress / 100;
        guiGraphics.fill(x, centerY, x + width, centerY + 1,
                withOpacity(WAVE_BASELINE, opacity));

        for (int i = 0; i < segmentCount; i++) {
            int segmentX = x + i * width / segmentCount;
            int nextX = x + (i + 1) * width / segmentCount;
            int segmentWidth = Math.max(1, nextX - segmentX);
            double pattern = Math.abs(Math.sin(i * 0.73D) * Math.cos(i * 0.19D + 0.8D));
            double breathing = 0.78D + Math.sin(now / 220.0D + i * 0.47D) * 0.22D;
            int amplitude = 2 + (int) Math.round((height * 0.42D) * pattern * breathing);
            int color = i < activeSegments ? WAVE_ACTIVE : WAVE_INACTIVE;
            guiGraphics.fill(segmentX, centerY - amplitude, segmentX + segmentWidth,
                    centerY + amplitude + 1, withOpacity(color, opacity));
        }

        int cursorX = x + Math.min(width - 1, width * clampedProgress / 100);
        guiGraphics.fill(cursorX, y - 2, cursorX + 1, y + height + 2,
                withOpacity(WAVE_ACTIVE_HIGHLIGHT, opacity));
    }

    public static int estimateProgress(long startedAt, long now, int start, int end, long durationMillis) {
        if (end <= start) {
            return clampProgress(start);
        }
        if (startedAt < 0L) {
            return clampProgress(start);
        }

        long elapsed = Math.max(0L, now - startedAt);
        double normalized = 1.0D - Math.exp(-elapsed / (double) Math.max(1L, durationMillis));
        return Math.min(end, start + (int) Math.round((end - start) * normalized));
    }

    public static int getCancelHintWidth(Font font) {
        return getActionHintWidth(font, " 中断连接");
    }

    public static void renderCancelHint(GuiGraphics guiGraphics, Font font, int virtualWidth, int virtualHeight) {
        renderActionHint(guiGraphics, font, virtualWidth, virtualHeight, " 中断连接", 1.0F);
    }

    /** Returns the visual width of the lower-right Esc action, after its compact scale is applied. */
    public static int getActionHintWidth(Font font, String suffix) {
        int rawWidth = getRawActionHintWidth(font, suffix);
        return (int) Math.ceil(rawWidth * CANCEL_TEXT_SCALE);
    }

    /** Draws an Esc action using the exact styling shared by the connection screen. */
    public static void renderActionHint(GuiGraphics guiGraphics, Font font, int virtualWidth,
                                        int virtualHeight, String suffix) {
        renderActionHint(guiGraphics, font, virtualWidth, virtualHeight, suffix, 1.0F);
    }

    public static void renderActionHint(GuiGraphics guiGraphics, Font font, int virtualWidth,
                                        int virtualHeight, String suffix, float opacity) {
        String prefix = "按 ";
        String key = "Esc";
        String safeSuffix = suffix == null ? "" : suffix;
        int keyWidth = font.width(key);
        int totalWidth = getActionHintWidth(font, safeSuffix);
        int x = virtualWidth - 24 - totalWidth;
        int y = virtualHeight - 24;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0.0F);
        guiGraphics.pose().scale(CANCEL_TEXT_SCALE, CANCEL_TEXT_SCALE, 1.0F);

        guiGraphics.drawString(font, prefix, 0, 0, withOpacity(CANCEL_COLOR, opacity), true);
        int keyX = font.width(prefix) + 2;
        int keyY = -2;
        guiGraphics.fill(keyX - 2, keyY, keyX + keyWidth + 2, keyY + font.lineHeight + 2,
                withOpacity(CANCEL_KEY_COLOR, opacity));
        guiGraphics.fill(keyX - 2, keyY, keyX + keyWidth + 2, keyY + 1,
                withOpacity(CANCEL_COLOR, opacity));
        guiGraphics.fill(keyX - 2, keyY + font.lineHeight + 1, keyX + keyWidth + 2,
                keyY + font.lineHeight + 2, withOpacity(CANCEL_COLOR, opacity));
        guiGraphics.drawString(font, key, keyX, 0, withOpacity(CANCEL_COLOR, opacity), true);
        guiGraphics.drawString(font, safeSuffix, keyX + keyWidth + 4, 0,
                withOpacity(CANCEL_COLOR, opacity), true);
        guiGraphics.pose().popPose();
    }

    private static int getRawActionHintWidth(Font font, String suffix) {
        return font.width("按 ") + font.width("Esc") + font.width(suffix == null ? "" : suffix) + 8;
    }

    private static int withOpacity(int color, float opacity) {
        float clamped = Math.max(0.0F, Math.min(1.0F, opacity));
        int alpha = Math.round(((color >>> 24) & 0xFF) * clamped);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static int clampProgress(int progress) {
        return Math.max(0, Math.min(100, progress));
    }

    public static String trimToWidth(String text, Font font, int maxWidth) {
        if (text == null || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }

        String ellipsis = "...";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }

}
