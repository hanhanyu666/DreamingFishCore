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

/** Small, persistent bottom-right label for the task location containing the player. */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class TaskLocationHudRenderer {
    private static final int RIGHT_MARGIN = 2;
    private static final int BOTTOM_MARGIN = 4;
    private static final float TEXT_SCALE = 0.82f;
    private static final int MAX_PANEL_WIDTH = 196;
    private static final int MIN_PANEL_HEIGHT = 15;
    private static final int PANEL_RADIUS = 4;
    private static final int LEFT_PADDING = 5;
    private static final int ACCENT_WIDTH = 2;
    private static final int ACCENT_GAP = 4;
    private static final int RIGHT_PADDING = 6;
    private static final int VERTICAL_PADDING = 3;
    private static final int LINE_GAP = 1;
    private static final int BUILDABLE_ACCENT = 0xFF68D9AE;
    private static final int PROTECTED_ACCENT = 0xFFE2B06C;
    private static final int PANEL_BACKGROUND = 0xD4141D28;
    private static final int TITLE_COLOR = 0xFFF0F4F8;
    // The snapshot is immutable until the player enters another task location.
    // Cache its trimmed text and measurements instead of running the font
    // splitter/trimmer on every render frame.
    private static TaskLocationClientState.Snapshot cachedSnapshot;
    private static Font cachedFont;
    private static int cachedPanelMaxWidth = -1;
    private static LocationLayout cachedLayout;

    private TaskLocationHudRenderer() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            TaskLocationClientState.clear();
            return;
        }
        if (minecraft.options.hideGui
                || minecraft.getDebugOverlay().showDebugScreen()
                || minecraft.screen != null) {
            return;
        }

        TaskLocationClientState.Snapshot snapshot = TaskLocationClientState.get();
        if (snapshot == null) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        // This overlay is small, but it is rendered every frame. Keep its rounded
        // panel and text in the same managed batch to avoid a flush per primitive.
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
        graphics.drawString(font, layout.detail(), 0, layout.rawLineStep(),
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
        String detail = detailFor(snapshot.mode());
        String locationName = LoadingScreenUi.trimToWidth(
                snapshot.locationName(), font, maxRawTextWidth);
        int rawTextWidth = Math.max(font.width(locationName), font.width(detail));
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
        cachedLayout = new LocationLayout(locationName, detail, panelWidth, panelHeight,
                rawLineStep, scaledTextHeight);
        return cachedLayout;
    }

    static String detailFor(TaskLocationMode mode) {
        return mode == TaskLocationMode.BUILDABLE
                ? "可建造区 · 仅禁 TNT/岩浆/传送门点火"
                : "冒险保护区 · 仅禁 TNT/岩浆/传送门点火";
    }

    static int accentFor(TaskLocationMode mode) {
        return mode == TaskLocationMode.BUILDABLE ? BUILDABLE_ACCENT : PROTECTED_ACCENT;
    }

    private record LocationLayout(String locationName, String detail, int panelWidth, int panelHeight,
                                  int rawLineStep, int scaledTextHeight) {
    }
}
