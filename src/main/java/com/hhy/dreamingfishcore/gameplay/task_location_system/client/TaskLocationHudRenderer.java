package com.hhy.dreamingfishcore.gameplay.task_location_system.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.components.UiPanelRenderer;
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
    private static final int RIGHT_MARGIN = 4;
    private static final int BOTTOM_MARGIN = 4;
    private static final int PANEL_RADIUS = 3;
    private static final int LEFT_PADDING = 5;
    private static final int RIGHT_PADDING = 5;
    private static final int VERTICAL_PADDING = 3;
    private static final int ACCENT_WIDTH = 2;
    private static final int ACCENT_GAP = 4;
    private static final int BUILDABLE_ACCENT = 0xFF68D9AE;
    private static final int PROTECTED_ACCENT = 0xFFE2B06C;
    private static final int PANEL_BACKGROUND = 0xC4141D28;
    private static final int TEXT_COLOR = 0xFFF0F4F8;

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
        graphics.drawManaged(() -> render(graphics, minecraft.font, snapshot));
    }

    private static void render(GuiGraphics graphics, Font font,
                               TaskLocationClientState.Snapshot snapshot) {
        String label = labelFor(snapshot.mode());
        int textWidth = font.width(label);
        int panelWidth = LEFT_PADDING + ACCENT_WIDTH + ACCENT_GAP
                + textWidth + RIGHT_PADDING;
        int panelHeight = font.lineHeight + VERTICAL_PADDING * 2;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int x = screenWidth - panelWidth - RIGHT_MARGIN;
        int y = screenHeight - panelHeight - BOTTOM_MARGIN;
        int accent = accentFor(snapshot.mode());

        UiPanelRenderer.smoothRoundedRectBatched(graphics, x, y,
                panelWidth, panelHeight, PANEL_RADIUS, PANEL_BACKGROUND,
                UiPanelRenderer.withAlpha(accent, 118));
        UiPanelRenderer.roundedRect(graphics, x + LEFT_PADDING, y + VERTICAL_PADDING,
                ACCENT_WIDTH, panelHeight - VERTICAL_PADDING * 2, 1,
                UiPanelRenderer.withAlpha(accent, 224));
        graphics.drawString(font, label,
                x + LEFT_PADDING + ACCENT_WIDTH + ACCENT_GAP,
                y + VERTICAL_PADDING, TEXT_COLOR, false);
    }

    static String labelFor(TaskLocationMode mode) {
        return mode == TaskLocationMode.BUILDABLE
                ? "保护区 - 可建造"
                : "保护区 - 强制保护";
    }

    private static int accentFor(TaskLocationMode mode) {
        return mode == TaskLocationMode.BUILDABLE ? BUILDABLE_ACCENT : PROTECTED_ACCENT;
    }
}
