package com.hhy.dreamingfishcore.mixin.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 本模组的界面自己绘制背景，跳过原版（及 Modern UI）的模糊背景。 */
@Mixin(Screen.class)
public abstract class ModScreenBackgroundMixin {
    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$skipVanillaBackgroundForModScreens(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                                     float partialTick, CallbackInfo ci) {
        Object self = this;
        if (self.getClass().getName().startsWith("com.hhy.dreamingfishcore.") && !(self instanceof
                com.hhy.dreamingfishcore.client.ui.framework.screen.UiScreen)) {
            ci.cancel();
        }
    }
}
