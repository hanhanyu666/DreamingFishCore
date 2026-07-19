package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.util.ModernSelectionScreenUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.server.LanServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerSelectionList.NetworkServerEntry.class)
public abstract class NetworkServerEntryMixin {

    @Shadow
    @Final
    protected LanServer serverData;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$renderModernNetworkServerEntry(GuiGraphics guiGraphics, int index, int top, int left,
                                                                int width, int height, int mouseX, int mouseY,
                                                                boolean hovering, float partialTick, CallbackInfo ci) {
        if (!ModernSelectionScreenUi.isModernSelectionScreen()) {
            return;
        }

        ci.cancel();
        boolean hideAddress = Minecraft.getInstance().options.hideServerAddress;
        ModernSelectionScreenUi.renderLanServerEntry(guiGraphics, this.serverData, index, top, left, width, height, hovering, hideAddress);
    }
}
