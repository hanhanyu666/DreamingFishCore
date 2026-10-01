package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.vanilla.SelectionEntryPainter;
import com.hhy.dreamingfishcore.client.ui.vanilla.SelectionScreenUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.server.LanServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$NetworkServerEntry")
public abstract class NetworkServerEntryMixin {

    @Shadow
    @Final
    protected LanServer serverData;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$renderModernNetworkServerEntry(GuiGraphics guiGraphics, int index, int top, int left,
                                                                int width, int height, int mouseX, int mouseY,
                                                                boolean hovering, float partialTick, CallbackInfo ci) {
        if (!SelectionScreenUi.isActive()) {
            return;
        }
        ci.cancel();
        SelectionEntryPainter.paintLan(guiGraphics, this, serverData, index, top, left, width, height, hovering);
    }
}
