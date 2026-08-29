package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.util.LoadingTips;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingScreenUi;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingTransitionController;
import com.hhy.dreamingfishcore.client.ui.util.VirtualCoordinateHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenericMessageScreen.class)
public abstract class GenericDirtMessageScreenMixin extends Screen {

    @Unique private final VirtualCoordinateHelper.VirtualSizeResult vs = new VirtualCoordinateHelper.VirtualSizeResult();
    @Unique private String tip = "";

    protected GenericDirtMessageScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                   float partialTick, CallbackInfo ci) {
        ci.cancel();
        dreamingFishCore$renderCleanWaitingScreen(guiGraphics);
        LoadingTransitionController.renderLoadingEntry(guiGraphics, this.width, this.height);
    }

    @Unique
    private void dreamingFishCore$renderCleanWaitingScreen(GuiGraphics guiGraphics) {
        VirtualCoordinateHelper.calculateVirtualSize(this, vs);
        if (tip.isEmpty()) {
            tip = LoadingTips.getRandomTip();
        }

        LoadingScreenUi.renderBackground(guiGraphics, this.width, this.height);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(vs.uiScale, vs.uiScale, 1.0f);

        int vw = vs.virtualWidth;
        int vh = vs.virtualHeight;

        LoadingScreenUi.renderTip(guiGraphics, this.font, tip);

        String statusText = this.title == null ? "处理中" : this.title.getString();
        if (statusText == null || statusText.isBlank()) {
            statusText = "处理中";
        }
        LoadingScreenUi.renderBottomStatusText(guiGraphics, this.font, vw, vh, statusText);

        guiGraphics.pose().popPose();
        LoadingTransitionController.rememberTextFrame(tip, statusText);
    }

}
