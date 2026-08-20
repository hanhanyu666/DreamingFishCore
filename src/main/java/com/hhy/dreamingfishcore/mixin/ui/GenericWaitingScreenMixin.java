package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.util.LoadingTips;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingScreenUi;
import com.hhy.dreamingfishcore.client.ui.util.VirtualCoordinateHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericWaitingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenericWaitingScreen.class)
public abstract class GenericWaitingScreenMixin extends Screen {

    @Unique private final VirtualCoordinateHelper.VirtualSizeResult vs = new VirtualCoordinateHelper.VirtualSizeResult();
    @Unique private String tip = "";

    protected GenericWaitingScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();

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

        String statusText = this.title == null ? "请稍候" : this.title.getString();
        if (statusText == null || statusText.isBlank()) {
            statusText = "请稍候";
        }
        LoadingScreenUi.renderBottomStatusText(guiGraphics, this.font, vw, vh, statusText);

        guiGraphics.pose().popPose();
    }

}
