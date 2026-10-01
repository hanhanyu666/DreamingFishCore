package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.vanilla.SelectionEntryAccess;
import com.hhy.dreamingfishcore.client.ui.vanilla.SelectionEntryPainter;
import com.hhy.dreamingfishcore.client.ui.vanilla.SelectionScreenUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.WorldSelectionList$WorldListEntry")
public abstract class WorldListEntryMixin implements SelectionEntryAccess.World {

    @Shadow
    @Final
    LevelSummary summary;

    @Shadow
    @Final
    private FaviconTexture icon;

    @Override
    public LevelSummary dreamingFishCore$summary() {
        return summary;
    }

    @Override
    public ResourceLocation dreamingFishCore$icon() {
        return icon.textureLocation();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$renderModernWorldEntry(GuiGraphics guiGraphics, int index, int top, int left,
                                                        int width, int height, int mouseX, int mouseY,
                                                        boolean hovering, float partialTick, CallbackInfo ci) {
        if (!SelectionScreenUi.isActive()) {
            return;
        }
        ci.cancel();
        SelectionEntryPainter.paintWorld(guiGraphics, this, summary, icon.textureLocation(), index, top, left, width, height, hovering);
    }
}
