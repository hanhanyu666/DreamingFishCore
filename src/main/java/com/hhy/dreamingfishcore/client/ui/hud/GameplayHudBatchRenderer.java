package com.hhy.dreamingfishcore.client.ui.hud;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud.CustomHotbarGUI;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud.CustomStatueGUI;
import com.hhy.dreamingfishcore.gameplay.task_location_system.client.TaskLocationHudRenderer;
import com.hhy.dreamingfishcore.gameplay.task_location_system.client.TaskLocationReminderHudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Owns one managed-buffer pass for the stable gameplay HUD sections.
 *
 * <p>Previously the hotbar, status HUD and two bottom-right task cards each
 * opened and submitted an independent GUI pass. Keeping their regular panel
 * and font geometry together avoids redundant render-buffer flushes.</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class GameplayHudBatchRenderer {
    private GameplayHudBatchRenderer() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean showHotbar = CustomHotbarGUI.shouldRenderHud(minecraft);
        boolean showLocation = TaskLocationHudRenderer.shouldRenderHud(minecraft);
        boolean showReminder = TaskLocationReminderHudRenderer.shouldRenderHud(minecraft);
        boolean showStatus = CustomStatueGUI.shouldRenderHud(minecraft);
        if (!showHotbar && !showLocation && !showReminder && !showStatus) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        graphics.drawManaged(() -> {
            // Render the hotbar first: its retained item-model layer has an
            // explicit GUI boundary. The regular geometry produced afterward
            // can then remain queued until the status icon-atlas boundary.
            if (showHotbar) {
                CustomHotbarGUI.renderBatched(graphics, minecraft);
            }
            if (showLocation) {
                TaskLocationHudRenderer.renderBatched(graphics, minecraft);
            }
            if (showReminder) {
                TaskLocationReminderHudRenderer.renderBatched(graphics, minecraft);
            }
            if (showStatus) {
                CustomStatueGUI.renderBatched(graphics, minecraft);
            }
        });
    }
}
