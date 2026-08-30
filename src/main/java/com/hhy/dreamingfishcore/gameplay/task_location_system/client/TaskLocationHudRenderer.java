package com.hhy.dreamingfishcore.gameplay.task_location_system.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.components.UiPanelRenderer;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingScreenUi;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Compact bottom-right task-location mode label. */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class TaskLocationHudRenderer {
    private static final int RIGHT_MARGIN = 2;
    private static final int BOTTOM_MARGIN = 4;
    private static final float TEXT_SCALE = 0.82f;
    private static final int MAX_PANEL_WIDTH = 196;
    private static final int MIN_PANEL_HEIGHT = 15;
    private static final int PANEL_RADIUS = 4;
    private static final int LEFT_PADDING = 5;
    private static final int RIGHT_PADDING = 6;
    private static final int VERTICAL_PADDING = 3;
    private static final int ACCENT_WIDTH = 2;
    private static final int ACCENT_GAP = 4;
    private static final int LINE_GAP = 1;
    private static final int BUILDABLE_ACCENT = 0xFF68D9AE;
    private static final int PROTECTED_ACCENT = 0xFFE2B06C;
    private static final int PANEL_BACKGROUND = 0xC4141D28;
    private static final int TITLE_COLOR = 0xFFF0F4F8;

    private static TaskLocationClientState.Snapshot cachedSnapshot;
    private static Font cachedFont;
    private static int cachedPanelMaxWidth = -1;
    private static LocationLayout cachedLayout;

    private TaskLocationHudRenderer() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.options.hideGui
                || minecraft.getDebugOverlay().showDebugScreen()
                || minecraft.screen != null) {
            return;
        }

        TaskLocationClientState.Snapshot snapshot = TaskLocationClientState.get();
        if (snapshot == null) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        graphics.drawManaged(() -> render(graphics, minecraft, snapshot));
    }

    private static void render(GuiGraphics graphics, Minecraft minecraft,
                               TaskLocationClientState.Snapshot snapshot) {
        Font font = minecraft.font;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int availableWidth = Math.max(48, screenWidth - RIGHT_MARGIN * 2);
        int panelMaxWidth = Math.min(MAX_PANEL_WIDTH, availableWidth);
        LocationLayout layout = getLayout(font, snapshot, panelMaxWidth);
        int panelWidth = layout.panelWidth();
        int panelHeight = layout.panelHeight();
        int x = screenWidth - panelWidth - RIGHT_MARGIN;
        int y = screenHeight - panelHeight - BOTTOM_MARGIN;
        int accent = accentFor(snapshot.mode());

        UiPanelRenderer.smoothRoundedRectBatched(graphics, x, y,
                panelWidth, panelHeight, PANEL_RADIUS, PANEL_BACKGROUND,
                UiPanelRenderer.withAlpha(accent, 118));
        UiPanelRenderer.roundedRect(graphics, x + LEFT_PADDING, y + VERTICAL_PADDING,
                ACCENT_WIDTH, panelHeight - VERTICAL_PADDING * 2, 1,
                UiPanelRenderer.withAlpha(accent, 224));
        int textX = x + LEFT_PADDING + ACCENT_WIDTH + ACCENT_GAP;
        int textY = y + (panelHeight - layout.scaledTextHeight()) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(textX, textY, 0.0f);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0f);
        graphics.drawString(font, layout.locationName(), 0, 0, TITLE_COLOR, false);
        graphics.drawString(font, layout.modeLabel(), 0, layout.rawLineStep(),
                UiPanelRenderer.withAlpha(accent, 222), false);
        graphics.pose().popPose();
    }

    private static LocationLayout getLayout(Font font, TaskLocationClientState.Snapshot snapshot,
                                             int panelMaxWidth) {
        if (cachedLayout != null && cachedSnapshot == snapshot
                && cachedFont == font && cachedPanelMaxWidth == panelMaxWidth) {
            return cachedLayout;
        }

        int chromeWidth = LEFT_PADDING + ACCENT_WIDTH + ACCENT_GAP + RIGHT_PADDING;
        int maxRawTextWidth = Math.max(24,
                (int) ((panelMaxWidth - chromeWidth) / TEXT_SCALE));
        String locationName = LoadingScreenUi.trimToWidth(
                snapshot.locationName(), font, maxRawTextWidth);
        String modeLabel = labelFor(snapshot.mode());
        int rawTextWidth = Math.max(font.width(locationName), font.width(modeLabel));
        int panelWidth = Math.min(panelMaxWidth,
                chromeWidth + Math.round(rawTextWidth * TEXT_SCALE));
        int rawLineStep = font.lineHeight + LINE_GAP;
        int rawTextHeight = font.lineHeight + rawLineStep;
        int scaledTextHeight = Math.max(1, Math.round(rawTextHeight * TEXT_SCALE));
        int panelHeight = Math.max(MIN_PANEL_HEIGHT,
                VERTICAL_PADDING * 2 + scaledTextHeight);

        cachedSnapshot = snapshot;
        cachedFont = font;
        cachedPanelMaxWidth = panelMaxWidth;
        cachedLayout = new LocationLayout(locationName, modeLabel, panelWidth, panelHeight,
                rawLineStep, scaledTextHeight);
        return cachedLayout;
    }

    static String labelFor(TaskLocationMode mode) {
        return mode == TaskLocationMode.BUILDABLE
                ? "保护区-可建造"
                : "保护区-强制保护";
    }

    /** Height reserved by the compact two-line region card. */
    static int panelHeight(Font font) {
        int rawLineStep = font.lineHeight + LINE_GAP;
        int rawTextHeight = font.lineHeight + rawLineStep;
        int scaledTextHeight = Math.max(1, Math.round(rawTextHeight * TEXT_SCALE));
        return Math.max(MIN_PANEL_HEIGHT, VERTICAL_PADDING * 2 + scaledTextHeight);
    }

    /** Top edge used by the region card, shared by the reminder card above it. */
    static int panelTop(int screenHeight, Font font) {
        return screenHeight - panelHeight(font) - BOTTOM_MARGIN;
    }

    private static int accentFor(TaskLocationMode mode) {
        return mode == TaskLocationMode.BUILDABLE ? BUILDABLE_ACCENT : PROTECTED_ACCENT;
    }

    private record LocationLayout(String locationName, String modeLabel, int panelWidth,
                                  int panelHeight, int rawLineStep, int scaledTextHeight) {
    }
}
