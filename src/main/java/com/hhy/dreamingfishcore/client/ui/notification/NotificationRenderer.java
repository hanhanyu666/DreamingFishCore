package com.hhy.dreamingfishcore.client.ui.notification;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.components.UiPanelRenderer;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingScreenUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class NotificationRenderer {
    private static final int LEFT_MARGIN = 5;
    private static final int TOP_MARGIN = 5;
    private static final int INNER_PADDING = 7;
    private static final int ACCENT_WIDTH = 2;
    private static final long SIDE_NOTIFICATION_ANIMATION_MS = 220L;
    private static final float TOP_RIGHT_TEXT_SCALE = 0.82f;
    private static final int TOP_RIGHT_MAX_WIDTH = 196;
    private static final int TOP_RIGHT_BOX_HEIGHT = 15;
    private static final int TOP_RIGHT_RADIUS = 4;
    private static final int TOP_RIGHT_STACK_GAP = 3;
    private static final int TOP_RIGHT_LEFT_PADDING = 5;
    private static final int TOP_RIGHT_ACCENT_GAP = 4;
    private static final int TOP_RIGHT_RIGHT_PADDING = 6;
    private static final int TOP_RIGHT_VERTICAL_PADDING = 3;
    private static final int TOP_RIGHT_LINE_GAP = 1;
    private static final int MAX_LEFT_WIDTH = 300;
    private static final int CENTER_MIN_WIDTH = 190;
    private static final int CENTER_SIDE_MARGIN = 58;
    private static final int PANEL_RADIUS = 4;
    private static final int CENTER_INNER_COLOR = 0x5E202634;
    private static final int CENTER_DARK_BORDER_COLOR = 0x965A4328;
    // Notification text is immutable for its lifetime.  Keep the expensive
    // Font.split result around instead of rebuilding it on every HUD frame;
    // weak keys let completed notifications disappear from the cache naturally.
    private static final Map<Notification, CachedLines> LEFT_LINES_CACHE = new WeakHashMap<>();
    private static final Map<Notification, CachedLines> TOP_RIGHT_LINES_CACHE = new WeakHashMap<>();
    private static Font lineCacheFont;

    private NotificationRenderer() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || mc.screen != null) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        // Notification panels are part of the HUD and can contain many translucent
        // primitives. Batch them for one buffer submission per notification pass.
        guiGraphics.drawManaged(() -> {
            renderTopLeft(guiGraphics, mc);
            renderCenterTop(guiGraphics, mc);
        });
    }

    public static void renderTopRight(GuiGraphics guiGraphics, Font font, int rightEdge,
                                      int anchorY, int anchorHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || mc.screen != null) {
            return;
        }

        List<NotificationManager.ActiveNotification> entries =
                NotificationManager.getActive(NotificationPosition.TOP_RIGHT);
        int currentY = anchorY + anchorHeight + 2;
        long now = System.currentTimeMillis();
        for (int index = entries.size() - 1; index >= 0; index--) {
            NotificationManager.ActiveNotification entry = entries.get(index);
            Notification notification = entry.notification();
            long animationMs = Math.min(SIDE_NOTIFICATION_ANIMATION_MS, notification.durationMs() / 3L);
            long outroStart = Math.max(animationMs, notification.durationMs() - animationMs);
            long age = entry.ageMs(now);
            float intro = easeOutCubic(clamp01(age / (float) Math.max(1L, animationMs)));
            float outro = age > outroStart
                    ? easeInCubic(clamp01((age - outroStart) / (float) Math.max(1L, animationMs)))
                    : 0.0f;
            float visibility = intro * (1.0f - outro);
            int alpha = Math.round(visibility * 255.0f);
            if (alpha <= 0) {
                continue;
            }

            Component message = notification.message();
            int chromeWidth = TOP_RIGHT_LEFT_PADDING + ACCENT_WIDTH
                    + TOP_RIGHT_ACCENT_GAP + TOP_RIGHT_RIGHT_PADDING;
            int maxTextWidth = Math.max(24,
                    (int) ((TOP_RIGHT_MAX_WIDTH - chromeWidth) / TOP_RIGHT_TEXT_SCALE));
            CachedLines cachedLines = splitTopRightLines(font, notification, maxTextWidth);
            List<FormattedCharSequence> displayLines = cachedLines.lines();
            int rawTextWidth = cachedLines.maxWidth();
            int textWidth = Math.round(rawTextWidth * TOP_RIGHT_TEXT_SCALE);
            int boxWidth = Math.min(TOP_RIGHT_MAX_WIDTH, chromeWidth + textWidth);
            int rawLineStep = font.lineHeight + TOP_RIGHT_LINE_GAP;
            int rawTextHeight = font.lineHeight + Math.max(0, displayLines.size() - 1) * rawLineStep;
            int scaledTextHeight = Math.max(1, Math.round(rawTextHeight * TOP_RIGHT_TEXT_SCALE));
            int boxHeight = Math.max(TOP_RIGHT_BOX_HEIGHT,
                    TOP_RIGHT_VERTICAL_PADDING * 2 + scaledTextHeight);
            int boxX = rightEdge - boxWidth - 2;
            int boxRight = boxX + boxWidth;
            int animatedWidth = Math.max(1, Math.round(boxWidth * visibility));
            guiGraphics.enableScissor(boxRight - animatedWidth, currentY, boxRight, currentY + boxHeight);
            drawTopRightPanel(guiGraphics, boxX, currentY, boxWidth, boxHeight, notification, alpha);

            int textX = boxX + TOP_RIGHT_LEFT_PADDING + ACCENT_WIDTH + TOP_RIGHT_ACCENT_GAP;
            int textY = currentY + (boxHeight - scaledTextHeight) / 2;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(textX, textY, 0.0f);
            guiGraphics.pose().scale(TOP_RIGHT_TEXT_SCALE, TOP_RIGHT_TEXT_SCALE, 1.0f);
            int lineY = 0;
            for (FormattedCharSequence line : displayLines) {
                guiGraphics.drawString(font, line, 0, lineY,
                        scaledColor(notification.theme().textColor(), alpha), false);
                lineY += rawLineStep;
            }
            guiGraphics.pose().popPose();
            guiGraphics.disableScissor();
            currentY += boxHeight + TOP_RIGHT_STACK_GAP;
        }
    }

    private static void drawTopRightPanel(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                          Notification notification, int alpha) {
        NotificationTheme theme = notification.theme();
        int accent = notification.effectiveAccentColor();
        int border = notification.accentColor() >= 0 ? accent : theme.borderColor();
        UiPanelRenderer.roundedRect(guiGraphics, x, y, width, height, TOP_RIGHT_RADIUS,
                scaleAlpha(theme.backgroundColor(), Math.round(alpha * 0.78f)));
        UiPanelRenderer.roundedBorder(guiGraphics, x, y, width, height, TOP_RIGHT_RADIUS,
                scaleAlpha(border, Math.round(alpha * 0.62f)));

        int accentX = x + TOP_RIGHT_LEFT_PADDING;
        int accentY = y + 3;
        int accentHeight = Math.max(2, height - 6);
        UiPanelRenderer.roundedRect(guiGraphics, accentX, accentY, ACCENT_WIDTH, accentHeight, 1,
                scaleAlpha(accent, Math.round(alpha * 0.88f)));
    }

    private static void renderTopLeft(GuiGraphics guiGraphics, Minecraft mc) {
        List<NotificationManager.ActiveNotification> entries =
                NotificationManager.getActive(NotificationPosition.TOP_LEFT);
        int currentY = TOP_MARGIN;
        long now = System.currentTimeMillis();
        for (NotificationManager.ActiveNotification entry : entries) {
            Notification notification = entry.notification();
            CachedLines cachedLines = splitLines(mc.font, notification);
            List<FormattedCharSequence> lines = cachedLines.lines();
            if (lines.isEmpty()) {
                continue;
            }

            int maxWidth = cachedLines.maxWidth();
            int boxWidth = maxWidth + INNER_PADDING * 2 + ACCENT_WIDTH + 4;
            int boxHeight = INNER_PADDING * 2 + lines.size() * (mc.font.lineHeight + 3) - 3;
            long age = entry.ageMs(now);
            long animationMs = Math.min(SIDE_NOTIFICATION_ANIMATION_MS, notification.durationMs() / 3L);
            long outroStart = Math.max(animationMs, notification.durationMs() - animationMs);
            float intro = easeOutCubic(clamp01(age / (float) Math.max(1L, animationMs)));
            float outro = age > outroStart
                    ? easeInCubic(clamp01((age - outroStart) / (float) Math.max(1L, animationMs)))
                    : 0.0f;
            float visibility = intro * (1.0f - outro);
            int alpha = Math.round(visibility * 255.0f);
            int animatedWidth = Math.max(1, Math.round(boxWidth * visibility));
            guiGraphics.enableScissor(LEFT_MARGIN, currentY,
                    LEFT_MARGIN + animatedWidth, currentY + boxHeight);
            drawPanel(guiGraphics, LEFT_MARGIN, currentY, boxWidth, boxHeight, notification, alpha, 3);

            int textX = LEFT_MARGIN + INNER_PADDING + ACCENT_WIDTH + 5;
            int textY = currentY + INNER_PADDING;
            for (FormattedCharSequence line : lines) {
                guiGraphics.drawString(mc.font, line, textX, textY,
                        scaledColor(notification.theme().textColor(), alpha), true);
                textY += mc.font.lineHeight + 3;
            }
            guiGraphics.disableScissor();
            currentY += boxHeight + 4;
        }
    }

    private static void renderCenterTop(GuiGraphics guiGraphics, Minecraft mc) {
        List<NotificationManager.ActiveNotification> entries =
                NotificationManager.getActive(NotificationPosition.CENTER_TOP);
        if (entries.isEmpty()) {
            return;
        }

        NotificationManager.ActiveNotification entry = entries.get(0);
        Notification notification = entry.notification();
        long elapsed = entry.ageMs(System.currentTimeMillis());
        long introMs = Math.min(620L, notification.durationMs() / 3L);
        long outroMs = Math.min(760L, notification.durationMs() / 3L);
        long outroStart = Math.max(introMs, notification.durationMs() - outroMs);
        float intro = easeOutCubic(clamp01(elapsed / (float) Math.max(1L, introMs)));
        float outro = elapsed > outroStart
                ? easeInCubic(clamp01((elapsed - outroStart) / (float) Math.max(1L, outroMs)))
                : 0.0f;
        float alpha = intro * (1.0f - outro);
        if (alpha <= 0.01f) {
            return;
        }

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int maxPanelWidth = Math.max(CENTER_MIN_WIDTH, screenWidth - CENTER_SIDE_MARGIN * 2);
        String title = notification.title().getString();
        String detail = notification.message().getString();
        title = LoadingScreenUi.trimToWidth(title, font, maxPanelWidth - 52);
        detail = LoadingScreenUi.trimToWidth(detail, font, maxPanelWidth - 52);

        int contentWidth = Math.max(font.width(title), detail.isEmpty() ? 0 : font.width(detail)) + 52;
        int panelWidth = Math.max(CENTER_MIN_WIDTH, Math.min(maxPanelWidth, contentWidth));
        boolean hasDetail = !detail.isEmpty();
        int panelHeight = hasDetail ? 38 : 29;
        int animatedWidth = Math.max(24, Math.round(panelWidth * (0.86f + intro * 0.14f)));
        int y = 16 - Math.round((1.0f - intro) * 26.0f) - Math.round(outro * 10.0f);
        int x = (screenWidth - animatedWidth) / 2;
        int alpha255 = Math.round(alpha * 255.0f);

        drawCenterPanel(guiGraphics, x, y, animatedWidth, panelHeight,
                notification, alpha255, intro, elapsed);
        int centerX = screenWidth / 2;
        drawCenteredScaledString(guiGraphics, font, title, centerX,
                y + (hasDetail ? 7 : 9), 1.16f,
                UiPanelRenderer.withAlpha(notification.theme().textColor(), alpha255));
        if (hasDetail) {
            drawCenteredScaledString(guiGraphics, font, detail, centerX, y + 26, 0.76f,
                    UiPanelRenderer.withAlpha(notification.theme().secondaryTextColor(),
                            Math.round(alpha255 * 0.82f)));
        }
        int glyphColor = notification.theme().borderColor();
        drawSideGlyph(guiGraphics, x + 12, y + panelHeight / 2, glyphColor, alpha255, intro);
        drawSideGlyph(guiGraphics, x + animatedWidth - 12, y + panelHeight / 2,
                glyphColor, alpha255, intro);
    }

    private static CachedLines splitLines(Font font, Notification notification) {
        ensureLineCacheFont(font);
        return LEFT_LINES_CACHE.computeIfAbsent(notification, ignored -> {
            List<FormattedCharSequence> result = new ArrayList<>();
            if (!notification.title().getString().isBlank()) {
                result.addAll(font.split(notification.title(), MAX_LEFT_WIDTH));
            }

            String message = notification.message().getString();
            for (String manualLine : message.split("\\R")) {
                if (!manualLine.isEmpty()) {
                    result.addAll(font.split(Component.literal(manualLine), MAX_LEFT_WIDTH));
                }
            }
            List<FormattedCharSequence> lines = List.copyOf(result);
            return new CachedLines(lines, maxWidth(font, lines));
        });
    }

    private static CachedLines splitTopRightLines(Font font, Notification notification,
                                                   int maxTextWidth) {
        ensureLineCacheFont(font);
        // The top-right width is fixed by the HUD constants, so the first
        // computed split remains valid for the notification's lifetime.
        return TOP_RIGHT_LINES_CACHE.computeIfAbsent(notification, ignored -> {
            List<FormattedCharSequence> lines = font.split(notification.message(), maxTextWidth);
            List<FormattedCharSequence> safeLines = lines.isEmpty()
                    ? List.of(notification.message().getVisualOrderText())
                    : List.copyOf(lines);
            return new CachedLines(safeLines, maxWidth(font, safeLines));
        });
    }

    private static int maxWidth(Font font, List<FormattedCharSequence> lines) {
        int width = 0;
        for (FormattedCharSequence line : lines) {
            width = Math.max(width, font.width(line));
        }
        return width;
    }

    private static void ensureLineCacheFont(Font font) {
        if (lineCacheFont == font) {
            return;
        }
        LEFT_LINES_CACHE.clear();
        TOP_RIGHT_LINES_CACHE.clear();
        lineCacheFont = font;
    }

    private static void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                  Notification notification, int alpha, int radius) {
        NotificationTheme theme = notification.theme();
        int accent = notification.effectiveAccentColor();
        UiPanelRenderer.smoothRoundedRectBatched(guiGraphics, x - 1, y - 1, width + 2, height + 2,
                radius + 1, scaleAlpha(theme.glowColor(), alpha), 0);
        UiPanelRenderer.smoothRoundedRectBatched(guiGraphics, x, y, width, height, radius,
                scaleAlpha(theme.backgroundColor(), alpha),
                scaleAlpha(notification.accentColor() >= 0 ? accent : theme.borderColor(), alpha));

        int accentAlpha = scaleAlpha(accent, alpha);
        int accentX = x + INNER_PADDING - 2;
        int accentY = y + INNER_PADDING;
        int accentHeight = Math.max(1, height - INNER_PADDING * 2);
        guiGraphics.fill(accentX, accentY, accentX + ACCENT_WIDTH, accentY + accentHeight, accentAlpha);
    }

    private static void drawCenterPanel(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                        Notification notification, int alpha, float intro, long elapsed) {
        NotificationTheme theme = notification.theme();
        int right = x + width;
        int bottom = y + height;
        int shadowAlpha = Math.min(90, alpha / 3);
        int glowAlpha = Math.min(72, alpha / 3);
        int streakWidth = Math.max(0, Math.round((width - 28) * intro));
        int streakX = x + (width - streakWidth) / 2;
        float shimmer = 0.5f + 0.5f * (float) Math.sin(elapsed / 360.0f);

        UiPanelRenderer.smoothRoundedRectBatched(guiGraphics, x - 2, y + 3, width + 4, height + 3,
                PANEL_RADIUS + 1, UiPanelRenderer.withAlpha(0xFF000000, shadowAlpha), 0);
        UiPanelRenderer.smoothRoundedRectBatched(guiGraphics, x - 1, y - 1, width + 2, height + 2,
                PANEL_RADIUS + 1, UiPanelRenderer.withAlpha(theme.glowColor(), glowAlpha), 0);
        UiPanelRenderer.smoothRoundedRectBatched(guiGraphics, x, y, width, height, PANEL_RADIUS,
                UiPanelRenderer.withAlpha(theme.backgroundColor(), alpha),
                UiPanelRenderer.withAlpha(theme.borderColor(), alpha));
        UiPanelRenderer.smoothRoundedRectBatched(guiGraphics, x + 4, y + 4, width - 8, height - 8,
                PANEL_RADIUS - 2, UiPanelRenderer.withAlpha(CENTER_INNER_COLOR, Math.round(alpha * 0.56f)), 0);
        guiGraphics.fill(streakX, y + 2, streakX + streakWidth, y + 3,
                UiPanelRenderer.withAlpha(blendColor(theme.borderColor(), 0xFFFFFFFF, 0.28f),
                        Math.round(alpha * (0.28f + shimmer * 0.18f))));
        guiGraphics.fill(x + 12, bottom - 4, right - 12, bottom - 3,
                UiPanelRenderer.withAlpha(CENTER_DARK_BORDER_COLOR, Math.round(alpha * 0.55f)));
    }

    private static void drawCenteredScaledString(GuiGraphics guiGraphics, Font font, String text,
                                                 int centerX, int y, float scale, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }

        int textWidth = font.width(text);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        int scaledX = Math.round((centerX - textWidth * scale / 2.0f) / scale);
        int scaledY = Math.round(y / scale);
        guiGraphics.drawString(font, text, scaledX + 1, scaledY + 1,
                UiPanelRenderer.withAlpha(0xFF000000, (color >>> 24) / 2), false);
        guiGraphics.drawString(font, text, scaledX, scaledY, color, false);
        guiGraphics.pose().popPose();
    }

    private static void drawSideGlyph(GuiGraphics guiGraphics, int centerX, int centerY,
                                      int accentColor, int alpha, float intro) {
        int size = Math.max(2, Math.round(4.0f + intro * 2.0f));
        int color = UiPanelRenderer.withAlpha(accentColor, Math.round(alpha * 0.76f));
        guiGraphics.fill(centerX - size, centerY, centerX, centerY + 1, color);
        guiGraphics.fill(centerX, centerY - size, centerX + 1, centerY, color);
        guiGraphics.fill(centerX, centerY + 1, centerX + 1, centerY + size + 1, color);
        guiGraphics.fill(centerX + 1, centerY, centerX + size + 1, centerY + 1, color);
    }

    private static int scaledColor(int color, int alpha) {
        return UiPanelRenderer.withAlpha(color, Math.round((color >>> 24) * (alpha / 255.0f)));
    }

    private static int scaleAlpha(int color, int alpha) {
        return UiPanelRenderer.withAlpha(color, Math.round((color >>> 24) * (alpha / 255.0f)));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float easeOutCubic(float value) {
        float inverse = 1.0f - value;
        return 1.0f - inverse * inverse * inverse;
    }

    private static float easeInCubic(float value) {
        return value * value * value;
    }

    private static int blendColor(int colorA, int colorB, float value) {
        float mix = clamp01(value);
        int ar = (colorA >> 16) & 0xFF;
        int ag = (colorA >> 8) & 0xFF;
        int ab = colorA & 0xFF;
        int br = (colorB >> 16) & 0xFF;
        int bg = (colorB >> 8) & 0xFF;
        int bb = colorB & 0xFF;
        int red = Math.round(ar + (br - ar) * mix);
        int green = Math.round(ag + (bg - ag) * mix);
        int blue = Math.round(ab + (bb - ab) * mix);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private record CachedLines(List<FormattedCharSequence> lines, int maxWidth) {
    }
}
