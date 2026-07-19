package com.hhy.dreamingfishcore.screen.playerlevel_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public class BiomeDiscoveryToastRenderer {
    private static final long INTRO_MS = 620L;
    private static final long HOLD_MS = 3300L;
    private static final long OUTRO_MS = 760L;
    private static final long TOTAL_MS = INTRO_MS + HOLD_MS + OUTRO_MS;
    private static final int MIN_WIDTH = 190;
    private static final int MAX_WIDTH_MARGIN = 58;
    private static final int PANEL_HEIGHT = 29;
    private static final int NEW_BIOME_PANEL_HEIGHT = 38;
    private static final int TOP_Y = 16;
    private static final int PANEL_RADIUS = 4;
    private static final int BG_COLOR = 0xD011141D;
    private static final int BG_INNER_COLOR = 0x5E202634;
    private static final int BORDER_COLOR = 0xE0CFA766;
    private static final int BORDER_DARK_COLOR = 0x965A4328;
    private static final int GLOW_COLOR = 0x3ACFA766;
    private static final int TITLE_COLOR = 0xFFD8B66E;
    private static final int NAME_COLOR = 0xFFF2E7CF;
    private static BiomeNotice currentNotice;
    private static long currentNoticeStartMs;

    public static void show(String biomeId, String biomeName, int totalExplored,
                            long experienceReward, boolean newlyDiscovered) {
        currentNotice = new BiomeNotice(biomeId, biomeName, totalExplored, experienceReward, newlyDiscovered);
        currentNoticeStartMs = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.options.renderDebug) {
            return;
        }

        long now = System.currentTimeMillis();
        if (currentNotice == null) {
            return;
        }

        long elapsed = now - currentNoticeStartMs;
        if (elapsed >= TOTAL_MS) {
            currentNotice = null;
            return;
        }

        drawToast(event.getGuiGraphics(), mc, currentNotice, elapsed);
    }

    private static void drawToast(GuiGraphics guiGraphics, Minecraft mc, BiomeNotice notice, long elapsed) {
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        float intro = elapsed < INTRO_MS ? easeOutCubic(elapsed / (float) INTRO_MS) : 1.0f;
        float outro = elapsed > INTRO_MS + HOLD_MS
                ? easeInCubic((elapsed - INTRO_MS - HOLD_MS) / (float) OUTRO_MS)
                : 0.0f;
        float alpha = clamp(intro * (1.0f - outro));
        if (alpha <= 0.01f) {
            return;
        }

        int maxPanelWidth = Math.max(MIN_WIDTH, screenWidth - MAX_WIDTH_MARGIN * 2);
        int panelHeight = notice.newlyDiscovered ? NEW_BIOME_PANEL_HEIGHT : PANEL_HEIGHT;
        String biomeName = trimToWidth(font, notice.biomeName, maxPanelWidth - 52);
        String detail = notice.newlyDiscovered
                ? "首次发现  ·  + " + notice.experienceReward + " 经验  ·  已探索 " + notice.totalExplored
                : "";
        int detailWidth = notice.newlyDiscovered ? font.width(detail) : 0;
        int contentWidth = Math.max(font.width(biomeName), detailWidth) + 52;
        int panelWidth = Math.max(MIN_WIDTH, Math.min(maxPanelWidth, contentWidth));
        float widthEase = 0.86f + intro * 0.14f;
        int animatedWidth = Math.max(24, Math.round(panelWidth * widthEase));
        int x = (screenWidth - animatedWidth) / 2;
        int y = TOP_Y - Math.round((1.0f - intro) * 26.0f) - Math.round(outro * 10.0f);
        int centerX = screenWidth / 2;
        int centerY = y + panelHeight / 2;
        int alpha255 = Math.round(alpha * 255.0f);

        drawEpicPanel(guiGraphics, x, y, animatedWidth, panelHeight, alpha255, intro, elapsed);
        drawTextBlock(guiGraphics, font, centerX, y, biomeName, detail, alpha255, notice.newlyDiscovered);
        drawSideGlyph(guiGraphics, x + 12, centerY, alpha255, intro);
        drawSideGlyph(guiGraphics, x + animatedWidth - 12, centerY, alpha255, intro);
    }

    private static void drawEpicPanel(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                      int alpha, float intro, long elapsed) {
        int right = x + width;
        int bottom = y + height;
        int shadowAlpha = Math.min(90, alpha / 3);
        int glowAlpha = Math.min(72, alpha / 3);
        int streakWidth = Math.max(0, Math.round((width - 28) * intro));
        int streakX = x + (width - streakWidth) / 2;
        float shimmer = 0.5f + 0.5f * (float) Math.sin(elapsed / 360.0f);

        drawRoundedRect(guiGraphics, x - 2, y + 3, width + 4, height + 3,
                PANEL_RADIUS + 1, withAlpha(0xFF000000, shadowAlpha));
        drawRoundedRect(guiGraphics, x - 1, y - 1, width + 2, height + 2,
                PANEL_RADIUS + 1, withAlpha(GLOW_COLOR, glowAlpha));
        drawRoundedRect(guiGraphics, x, y, width, height, PANEL_RADIUS, withAlpha(BG_COLOR, alpha));
        drawRoundedRect(guiGraphics, x + 4, y + 4, width - 8, height - 8, PANEL_RADIUS - 2,
                withAlpha(BG_INNER_COLOR, Math.round(alpha * 0.56f)));

        drawRoundedBorder(guiGraphics, x, y, width, height, PANEL_RADIUS, withAlpha(BORDER_COLOR, alpha));
        guiGraphics.fill(streakX, y + 2, streakX + streakWidth, y + 3,
                withAlpha(blendColor(BORDER_COLOR, 0xFFFFFFFF, 0.28f), Math.round(alpha * (0.28f + shimmer * 0.18f))));
        guiGraphics.fill(x + 12, bottom - 4, right - 12, bottom - 3,
                withAlpha(BORDER_DARK_COLOR, Math.round(alpha * 0.55f)));
    }

    private static void drawTextBlock(GuiGraphics guiGraphics, Font font, int centerX, int y,
                                      String biomeName, String detail, int alpha, boolean newlyDiscovered) {
        int nameY = newlyDiscovered ? y + 7 : y + 9;
        drawCenteredScaledString(guiGraphics, font, biomeName, centerX, nameY, 1.16f,
                withAlpha(NAME_COLOR, alpha));
        if (newlyDiscovered) {
            drawCenteredScaledString(guiGraphics, font, detail, centerX, y + 26, 0.76f,
                    withAlpha(TITLE_COLOR, Math.round(alpha * 0.82f)));
        }
    }

    private static void drawSideGlyph(GuiGraphics guiGraphics, int centerX, int centerY, int alpha, float intro) {
        int size = Math.max(2, Math.round(4.0f + intro * 2.0f));
        int color = withAlpha(BORDER_COLOR, Math.round(alpha * 0.76f));
        guiGraphics.fill(centerX - size, centerY, centerX, centerY + 1, color);
        guiGraphics.fill(centerX, centerY - size, centerX + 1, centerY, color);
        guiGraphics.fill(centerX, centerY + 1, centerX + 1, centerY + size + 1, color);
        guiGraphics.fill(centerX + 1, centerY, centerX + size + 1, centerY + 1, color);
    }

    private static void drawRoundedRect(GuiGraphics guiGraphics, int x, int y,
                                        int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int r = Math.max(0, Math.min(radius, Math.min(width / 2, height / 2)));
        int right = x + width;
        int bottom = y + height;
        guiGraphics.fill(x + r, y, right - r, bottom, color);
        guiGraphics.fill(x, y + r, right, bottom - r, color);
        if (r >= 2) {
            guiGraphics.fill(x + 1, y + 1, x + r, y + r, color);
            guiGraphics.fill(right - r, y + 1, right - 1, y + r, color);
            guiGraphics.fill(x + 1, bottom - r, x + r, bottom - 1, color);
            guiGraphics.fill(right - r, bottom - r, right - 1, bottom - 1, color);
        }
    }

    private static void drawRoundedBorder(GuiGraphics guiGraphics, int x, int y,
                                          int width, int height, int radius, int color) {
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

    private static void drawCenteredScaledString(GuiGraphics guiGraphics, Font font, String text, int centerX, int y,
                                                 float scale, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }

        int textWidth = font.width(text);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        int scaledX = Math.round((centerX - textWidth * scale / 2.0f) / scale);
        int scaledY = Math.round(y / scale);
        guiGraphics.drawString(font, text, scaledX + 1, scaledY + 1, withAlpha(0xFF000000, (color >>> 24) / 2), false);
        guiGraphics.drawString(font, text, scaledX, scaledY, color, false);
        guiGraphics.pose().popPose();
    }

    private static String trimToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }

        int ellipsisWidth = font.width("...");
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - ellipsisWidth)) + "...";
    }

    private static float easeOutCubic(float t) {
        float value = clamp(t);
        float inv = 1.0f - value;
        return 1.0f - inv * inv * inv;
    }

    private static float easeInCubic(float t) {
        float value = clamp(t);
        return value * value * value;
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static int blendColor(int colorA, int colorB, float t) {
        float mix = clamp(t);
        int ar = (colorA >> 16) & 0xFF;
        int ag = (colorA >> 8) & 0xFF;
        int ab = colorA & 0xFF;
        int br = (colorB >> 16) & 0xFF;
        int bg = (colorB >> 8) & 0xFF;
        int bb = colorB & 0xFF;
        int r = Math.round(ar + (br - ar) * mix);
        int g = Math.round(ag + (bg - ag) * mix);
        int b = Math.round(ab + (bb - ab) * mix);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static class BiomeNotice {
        private final String biomeId;
        private final String biomeName;
        private final int totalExplored;
        private final long experienceReward;
        private final boolean newlyDiscovered;

        private BiomeNotice(String biomeId, String biomeName, int totalExplored,
                            long experienceReward, boolean newlyDiscovered) {
            this.biomeId = biomeId == null ? "" : biomeId;
            this.biomeName = biomeName == null || biomeName.isBlank() ? this.biomeId : biomeName;
            this.totalExplored = totalExplored;
            this.experienceReward = experienceReward;
            this.newlyDiscovered = newlyDiscovered;
        }
    }
}
