package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.util.ModernSelectionScreenUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class WorldListEntryMixin {

    @Shadow
    @Final
    private LevelSummary summary;

    @Shadow
    @Final
    private FaviconTexture icon;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$renderModernWorldEntry(GuiGraphics guiGraphics, int index, int top, int left,
                                                        int width, int height, int mouseX, int mouseY,
                                                        boolean hovering, float partialTick, CallbackInfo ci) {
        if (!ModernSelectionScreenUi.isModernSelectionScreen()) {
            return;
        }

        ci.cancel();
        ResourceLocation texture = this.icon.textureLocation();
        ModernSelectionScreenUi.renderWorldEntry(guiGraphics, this.summary, texture, index, top, left, width, height, hovering);
    }
}
