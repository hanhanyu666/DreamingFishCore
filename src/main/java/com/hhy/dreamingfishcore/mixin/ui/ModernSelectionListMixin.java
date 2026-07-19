package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.util.ModernSelectionScreenUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public abstract class ModernSelectionListMixin {

    @Inject(method = "renderSelection", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$renderModernSelection(GuiGraphics guiGraphics, int top, int width, int height,
                                                       int outerColor, int innerColor, CallbackInfo ci) {
        if (!ModernSelectionScreenUi.isModernSelectionScreen()) {
            return;
        }

        ci.cancel();
        AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
        ModernSelectionScreenUi.drawModernSelection(guiGraphics, top, list.getLeft(), list.getWidth(), height);
    }
}
