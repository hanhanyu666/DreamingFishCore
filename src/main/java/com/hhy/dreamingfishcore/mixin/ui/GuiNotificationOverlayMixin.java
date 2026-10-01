package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.framework.hud.HudCanvas;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HUD 的最后一刻：画 OVERLAY 时机的区域（如左上角通知），保证盖在 Xaero 小地图等后画的 HUD 之上。 */
// Xaero's end-of-Gui hook uses the default mixin priority (1000); the higher
// priority makes this RETURN callback be applied after it and therefore run last.
@Mixin(value = Gui.class, priority = 2000)
public abstract class GuiNotificationOverlayMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void dreamingFishCore$renderTopLeftNotificationsLast(
            GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo callbackInfo) {
        HudCanvas.paintOverlay(guiGraphics);
    }
}
