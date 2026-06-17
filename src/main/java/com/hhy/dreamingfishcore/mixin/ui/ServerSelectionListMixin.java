package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.util.ModernSelectionScreenUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerSelectionList.class)
public abstract class ServerSelectionListMixin {

    @Inject(method = "getRowWidth", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$getModernRowWidth(CallbackInfoReturnable<Integer> cir) {
        Screen screen = Minecraft.getInstance().screen;
        if (!ModernSelectionScreenUi.isModernSelectionScreen() || screen == null) {
            return;
        }

        ModernSelectionScreenUi.Layout layout = ModernSelectionScreenUi.calculateLayout(screen, false);
        int rowWidth = Math.round(layout.listW() * layout.scale());
        cir.setReturnValue(Math.max(270, rowWidth));
    }
}
