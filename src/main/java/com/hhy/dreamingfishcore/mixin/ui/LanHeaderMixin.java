package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.util.ModernSelectionScreenUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerSelectionList.LANHeader.class)
public abstract class LanHeaderMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$renderModernLanHeader(GuiGraphics guiGraphics, int index, int top, int left,
                                                       int width, int height, int mouseX, int mouseY,
                                                       boolean hovering, float partialTick, CallbackInfo ci) {
        if (!ModernSelectionScreenUi.isModernSelectionScreen()) {
            return;
        }

        ci.cancel();
        ModernSelectionScreenUi.renderLanHeader(guiGraphics, top, left, width, height);
    }
}
