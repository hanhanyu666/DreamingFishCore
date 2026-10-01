package com.hhy.dreamingfishcore.client.ui.notification;

import com.hhy.dreamingfishcore.client.ui.framework.hud.HudFrame;
import com.hhy.dreamingfishcore.client.ui.framework.hud.HudLayer;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextFit;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 通知的三种位置：屏幕上方居中的横幅、左上角的提示卡片与右上角读数下方的系统消息。
 * 全部画在统一 HUD 画布上；出入场时卡片按宽度展开/收起（画布裁剪）。
 */
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
    private static final Map<Notification, CenterLayout> CENTER_LAYOUT_CACHE = new WeakHashMap<>();
    private static Font lineCacheFont;
    private static volatile boolean lineCachesDirty;

    /** 屏幕上方居中的横幅。 */
    public static final HudLayer CENTER_LAYER = new HudLayer() {
        @Override
        public int order() {
            return 60;
        }

        @Override
        public boolean visible(Minecraft minecraft) {
            return hudVisible(minecraft) && !NotificationManager.getActive(NotificationPosition.CENTER_TOP).isEmpty();
        }

        @Override
        public void paint(HudFrame frame) {
            renderCenterTop(frame.canvas(), frame.minecraft(), NotificationManager.getActive(NotificationPosition.CENTER_TOP));
        }
    };

    /**
     * 左上角提示卡片。放在 HUD 的最后一刻绘制：Xaero 小地图在 {@code Gui#render} 返回时才画，
     * 在那之后绘制才不会被小地图盖住。
     */
    public static final HudLayer TOP_LEFT_LAYER = new HudLayer() {
        @Override
        public Pass pass() {
            return Pass.OVERLAY;
        }

        @Override
        public boolean visible(Minecraft minecraft) {
            return hudVisible(minecraft) && !NotificationManager.getActive(NotificationPosition.TOP_LEFT).isEmpty();
        }

        @Override
        public void paint(HudFrame frame) {
            renderTopLeft(frame.canvas(), frame.minecraft(), NotificationManager.getActive(NotificationPosition.TOP_LEFT));
        }
    };

    private NotificationRenderer() {
    }

    private static boolean hudVisible(Minecraft mc) {
        return mc.player != null && !mc.options.hideGui && !mc.getDebugOverlay().showDebugScreen() && mc.screen == null;
    }

    // ==================== 右上角系统消息 ====================

    public static void renderTopRight(UiCanvas canvas, Font font, int rightEdge, int anchorY, int anchorHeight,
                                      List<NotificationManager.ActiveNotification> entries) {
        Minecraft mc = Minecraft.getInstance();
        if (!hudVisible(mc) || entries.isEmpty()) {
            return;
        }

        int currentY = anchorY + anchorHeight + 2;
        long now = System.currentTimeMillis();
        for (int index = entries.size() - 1; index >= 0; index--) {
            NotificationManager.ActiveNotification entry = entries.get(index);
            Notification notification = entry.notification();
            float visibility = visibility(entry, notification, now);
            int alpha = Math.round(visibility * 255.0f);
            if (alpha <= 0) {
                continue;
            }

            int chromeWidth = TOP_RIGHT_LEFT_PADDING + ACCENT_WIDTH
                    + TOP_RIGHT_ACCENT_GAP + TOP_RIGHT_RIGHT_PADDING;
            int maxTextWidth = Math.max(24,
                    (int) ((TOP_RIGHT_MAX_WIDTH - chromeWidth) / TOP_RIGHT_TEXT_SCALE));
            CachedLines cachedLines = splitTopRightLines(font, notification, maxTextWidth);
            List<FormattedCharSequence> displayLines = cachedLines.lines();
            int textWidth = Math.round(cachedLines.maxWidth() * TOP_RIGHT_TEXT_SCALE);
            int boxWidth = Math.min(TOP_RIGHT_MAX_WIDTH, chromeWidth + textWidth);
            int rawLineStep = font.lineHeight + TOP_RIGHT_LINE_GAP;
            int rawTextHeight = font.lineHeight + Math.max(0, displayLines.size() - 1) * rawLineStep;
            int scaledTextHeight = Math.max(1, Math.round(rawTextHeight * TOP_RIGHT_TEXT_SCALE));
            int boxHeight = Math.max(TOP_RIGHT_BOX_HEIGHT,
                    TOP_RIGHT_VERTICAL_PADDING * 2 + scaledTextHeight);
            int boxX = rightEdge - boxWidth - 2;
            int boxRight = boxX + boxWidth;
            boolean clipAnimation = visibility < 0.999f;
            if (clipAnimation) {
                int animatedWidth = Math.max(1, Math.round(boxWidth * visibility));
                canvas.pushClip(boxRight - animatedWidth, currentY, animatedWidth, boxHeight, 0.0F);
            }
            drawTopRightPanel(canvas, boxX, currentY, boxWidth, boxHeight, notification, alpha);

            float textX = boxX + TOP_RIGHT_LEFT_PADDING + ACCENT_WIDTH + TOP_RIGHT_ACCENT_GAP;
            float textY = currentY + (boxHeight - scaledTextHeight) / 2.0F;
            int textColor = scaleAlpha(notification.theme().textColor(), alpha);
            for (FormattedCharSequence line : displayLines) {
                canvas.text(line, textX, textY, textColor, TOP_RIGHT_TEXT_SCALE, false);
                textY += rawLineStep * TOP_RIGHT_TEXT_SCALE;
            }
            if (clipAnimation) {
                canvas.popClip();
            }
            currentY += boxHeight + TOP_RIGHT_STACK_GAP;
        }
    }

    private static void drawTopRightPanel(UiCanvas canvas, int x, int y, int width, int height,
                                          Notification notification, int alpha) {
        NotificationTheme theme = notification.theme();
        int accent = notification.effectiveAccentColor();
        int border = notification.accentColor() >= 0 ? accent : theme.borderColor();
        canvas.shape(x, y, width, height).radius(TOP_RIGHT_RADIUS)
                .fill(scaleAlpha(theme.backgroundColor(), Math.round(alpha * 0.78f)))
                .border(1.0F, scaleAlpha(border, Math.round(alpha * 0.62f))).draw();
        canvas.shape(x + TOP_RIGHT_LEFT_PADDING, y + 3, ACCENT_WIDTH, Math.max(2, height - 6)).radius(1.0F)
                .fill(scaleAlpha(accent, Math.round(alpha * 0.88f))).draw();
    }

    // ==================== 左上角提示 ====================

    private static void renderTopLeft(UiCanvas canvas, Minecraft mc, List<NotificationManager.ActiveNotification> entries) {
        int currentY = TOP_MARGIN;
        long now = System.currentTimeMillis();
        for (NotificationManager.ActiveNotification entry : entries) {
            Notification notification = entry.notification();
            CachedLines cachedLines = splitLines(mc.font, notification);
            List<FormattedCharSequence> lines = cachedLines.lines();
            if (lines.isEmpty()) {
                continue;
            }

            int boxWidth = cachedLines.maxWidth() + INNER_PADDING * 2 + ACCENT_WIDTH + 4;
            int boxHeight = INNER_PADDING * 2 + lines.size() * (mc.font.lineHeight + 3) - 3;
            float visibility = visibility(entry, notification, now);
            int alpha = Math.round(visibility * 255.0f);
            if (alpha <= 0) {
                continue;
            }
            boolean clipAnimation = visibility < 0.999f;
            if (clipAnimation) {
                int animatedWidth = Math.max(1, Math.round((boxWidth + 4) * visibility));
                canvas.pushClip(LEFT_MARGIN - 2, currentY - 2, animatedWidth, boxHeight + 6, 0.0F);
            }
            drawPanel(canvas, LEFT_MARGIN, currentY, boxWidth, boxHeight, notification, alpha);

            float textX = LEFT_MARGIN + INNER_PADDING + ACCENT_WIDTH + 5;
            float textY = currentY + INNER_PADDING;
            int textColor = scaleAlpha(notification.theme().textColor(), alpha);
            for (FormattedCharSequence line : lines) {
                canvas.text(line, textX, textY, textColor, 1.0F, true);
                textY += mc.font.lineHeight + 3;
            }
            if (clipAnimation) {
                canvas.popClip();
            }
            currentY += boxHeight + 4;
        }
    }

    private static void drawPanel(UiCanvas canvas, int x, int y, int width, int height,
                                  Notification notification, int alpha) {
        NotificationTheme theme = notification.theme();
        int accent = notification.effectiveAccentColor();
        canvas.shape(x, y, width, height).radius(3.0F)
                .fill(scaleAlpha(theme.backgroundColor(), alpha))
                .border(1.0F, scaleAlpha(notification.accentColor() >= 0 ? accent : theme.borderColor(), alpha))
                .shadow(new Theme.Shadow(0.0F, 1.0F, 6.0F, 0.0F, scaleAlpha(theme.glowColor(), alpha))).draw();
        canvas.shape(x + INNER_PADDING - 2, y + INNER_PADDING, ACCENT_WIDTH, Math.max(1, height - INNER_PADDING * 2))
                .radius(1.0F).fill(scaleAlpha(accent, alpha)).draw();
    }

    // ==================== 居中横幅 ====================

    private static void renderCenterTop(UiCanvas canvas, Minecraft mc, List<NotificationManager.ActiveNotification> entries) {
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
        CenterLayout layout = getCenterLayout(font, notification, maxPanelWidth);
        int panelWidth = layout.panelWidth();
        int panelHeight = layout.panelHeight();
        int animatedWidth = Math.max(24, Math.round(panelWidth * (0.86f + intro * 0.14f)));
        int y = 16 - Math.round((1.0f - intro) * 26.0f) - Math.round(outro * 10.0f);
        int x = (screenWidth - animatedWidth) / 2;
        int alpha255 = Math.round(alpha * 255.0f);

        drawCenterPanel(canvas, x, y, animatedWidth, panelHeight, notification, alpha255, intro, elapsed);
        float centerX = screenWidth / 2.0F;
        drawCenteredScaledString(canvas, font, layout.title(), centerX, y + (layout.hasDetail() ? 7 : 9), 1.16f,
                UiColor.withAlpha(notification.theme().textColor(), alpha255));
        if (layout.hasDetail()) {
            drawCenteredScaledString(canvas, font, layout.detail(), centerX, y + 26, 0.76f,
                    UiColor.withAlpha(notification.theme().secondaryTextColor(), Math.round(alpha255 * 0.82f)));
        }
        int glyphColor = notification.theme().borderColor();
        drawSideGlyph(canvas, x + 12, y + panelHeight / 2.0F, glyphColor, alpha255, intro);
        drawSideGlyph(canvas, x + animatedWidth - 12, y + panelHeight / 2.0F, glyphColor, alpha255, intro);
    }

    private static void drawCenterPanel(UiCanvas canvas, int x, int y, int width, int height,
                                        Notification notification, int alpha, float intro, long elapsed) {
        NotificationTheme theme = notification.theme();
        int bottom = y + height;
        float streakWidth = Math.max(0, (width - 28) * intro);
        float streakX = x + (width - streakWidth) / 2.0F;
        float shimmer = 0.5f + 0.5f * (float) Math.sin(elapsed / 360.0f);

        canvas.shape(x, y, width, height).radius(PANEL_RADIUS)
                .fill(UiColor.withAlpha(theme.backgroundColor(), alpha))
                .border(1.0F, UiColor.withAlpha(theme.borderColor(), alpha))
                .shadow(new Theme.Shadow(0.0F, 3.0F, 10.0F, 0.0F, UiColor.withAlpha(0xFF000000, Math.min(110, alpha / 2))))
                .draw();
        canvas.shape(x - 1, y - 1, width + 2, height + 2).radius(PANEL_RADIUS + 1).fill(0)
                .shadow(new Theme.Shadow(0.0F, 0.0F, 8.0F, 0.0F, UiColor.withAlpha(theme.glowColor(), Math.min(72, alpha / 3))))
                .draw();
        canvas.shape(x + 4, y + 4, width - 8, height - 8).radius(PANEL_RADIUS - 2)
                .fill(UiColor.withAlpha(CENTER_INNER_COLOR, Math.round(alpha * 0.56f))).draw();
        // 顶边流光：两端淡出
        int streak = UiColor.withAlpha(blendColor(theme.borderColor(), 0xFFFFFFFF, 0.28f),
                Math.round(alpha * (0.28f + shimmer * 0.18f)));
        canvas.shape(streakX, y + 2, streakWidth * 0.5F, 1).horizontalGradient(UiColor.withAlpha(streak, 0), streak).draw();
        canvas.shape(streakX + streakWidth * 0.5F, y + 2, streakWidth * 0.5F, 1)
                .horizontalGradient(streak, UiColor.withAlpha(streak, 0)).draw();
        canvas.fill(x + 12, bottom - 4, width - 24, 1, UiColor.withAlpha(CENTER_DARK_BORDER_COLOR, Math.round(alpha * 0.55f)));
    }

    private static void drawCenteredScaledString(UiCanvas canvas, Font font, String text, float centerX, float y,
                                                 float scale, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float x = centerX - font.width(text) * scale / 2.0F;
        canvas.text(text, x + scale, y + scale, UiColor.withAlpha(0xFF000000, (color >>> 24) / 2), scale, false);
        canvas.text(text, x, y, color, scale, false);
    }

    /** 两侧的十字准星标记，随入场展开。 */
    private static void drawSideGlyph(UiCanvas canvas, float centerX, float centerY, int accentColor, int alpha, float intro) {
        float size = 4.0f + intro * 2.0f;
        int color = UiColor.withAlpha(accentColor, Math.round(alpha * 0.76f));
        canvas.line(centerX - size, centerY + 0.5F, centerX + size + 1.0F, centerY + 0.5F, 1.0F, color, false);
        canvas.line(centerX + 0.5F, centerY - size, centerX + 0.5F, centerY + size + 1.0F, 1.0F, color, false);
    }

    // ==================== 排版缓存 ====================

    private static float visibility(NotificationManager.ActiveNotification entry, Notification notification, long now) {
        long animationMs = Math.min(SIDE_NOTIFICATION_ANIMATION_MS, notification.durationMs() / 3L);
        long outroStart = Math.max(animationMs, notification.durationMs() - animationMs);
        long age = entry.ageMs(now);
        float intro = easeOutCubic(clamp01(age / (float) Math.max(1L, animationMs)));
        float outro = age > outroStart
                ? easeInCubic(clamp01((age - outroStart) / (float) Math.max(1L, animationMs)))
                : 0.0f;
        return intro * (1.0f - outro);
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

    private static CachedLines splitTopRightLines(Font font, Notification notification, int maxTextWidth) {
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

    private static CenterLayout getCenterLayout(Font font, Notification notification, int maxPanelWidth) {
        ensureLineCacheFont(font);
        CenterLayout cached = CENTER_LAYOUT_CACHE.get(notification);
        if (cached != null && cached.maxPanelWidth() == maxPanelWidth) {
            return cached;
        }

        String title = TextFit.trim(notification.title().getString(), font, maxPanelWidth - 52);
        String detail = TextFit.trim(notification.message().getString(), font, maxPanelWidth - 52);
        int contentWidth = Math.max(font.width(title), detail.isEmpty() ? 0 : font.width(detail)) + 52;
        int panelWidth = Math.max(CENTER_MIN_WIDTH, Math.min(maxPanelWidth, contentWidth));
        boolean hasDetail = !detail.isEmpty();
        CenterLayout result = new CenterLayout(title, detail, panelWidth, hasDetail ? 38 : 29, hasDetail, maxPanelWidth);
        CENTER_LAYOUT_CACHE.put(notification, result);
        return result;
    }

    private static int maxWidth(Font font, List<FormattedCharSequence> lines) {
        int width = 0;
        for (FormattedCharSequence line : lines) {
            width = Math.max(width, font.width(line));
        }
        return width;
    }

    private static void ensureLineCacheFont(Font font) {
        if (!lineCachesDirty && lineCacheFont == font) {
            return;
        }
        LEFT_LINES_CACHE.clear();
        TOP_RIGHT_LINES_CACHE.clear();
        CENTER_LAYOUT_CACHE.clear();
        lineCacheFont = font;
        lineCachesDirty = false;
    }

    /** Defers cache disposal to the next render after a resource-pack reload. */
    public static void invalidateLayoutCaches() {
        lineCachesDirty = true;
    }

    private static int scaleAlpha(int color, int alpha) {
        return UiColor.withAlpha(color, Math.round((color >>> 24) * (alpha / 255.0f)));
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
        return UiColor.lerp(colorA | 0xFF000000, colorB | 0xFF000000, clamp01(value));
    }

    private record CachedLines(List<FormattedCharSequence> lines, int maxWidth) {
    }

    private record CenterLayout(String title, String detail, int panelWidth,
                                int panelHeight, boolean hasDetail, int maxPanelWidth) {
    }
}
