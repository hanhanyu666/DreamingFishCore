package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.vanilla.SelectionEntryPainter;
import com.hhy.dreamingfishcore.client.ui.vanilla.SelectionScreenUi;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 世界/服务器列表：去掉原版底纹与分隔线，选中态交给条目自己画，滚动条换成细胶囊。 */
@Mixin(AbstractSelectionList.class)
public abstract class ModernSelectionListMixin {

    @Inject(method = "renderListBackground", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$cancelVanillaSelectionBackground(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (SelectionScreenUi.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderListSeparators", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$cancelVanillaSelectionSeparators(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (SelectionScreenUi.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSelection", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$renderModernSelection(GuiGraphics guiGraphics, int top, int width, int height,
                                                       int outerColor, int innerColor, CallbackInfo ci) {
        if (SelectionScreenUi.isActive()) {
            ci.cancel();
            SelectionEntryPainter.markNextSelected();
        }
    }

    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void dreamingFishCore$modernScrollbar(GuiGraphics guiGraphics, ResourceLocation sprite, int x, int y, int width,
                                                 int height, Operation<Void> original) {
        if (!SelectionScreenUi.isActive()) {
            original.call(guiGraphics, sprite, x, y, width, height);
            return;
        }
        SelectionEntryPainter.paintScrollbar(guiGraphics, sprite.getPath().equals("widget/scroller"), x, y, width, height);
    }
}
