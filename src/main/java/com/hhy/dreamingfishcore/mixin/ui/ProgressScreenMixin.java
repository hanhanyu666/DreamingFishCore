package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.loading.LoadingScreenUi;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingTransitionController;
import com.hhy.dreamingfishcore.client.ui.util.LoadingTips;
import com.hhy.dreamingfishcore.client.ui.util.VirtualCoordinateHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/** Keeps the short world-start preparation phase on the same immersive loading surface. */
@Mixin(ProgressScreen.class)
public abstract class ProgressScreenMixin extends Screen {
    @Shadow @Nullable private Component header;
    @Shadow @Nullable private Component stage;
    @Shadow private boolean stop;
    @Shadow @Final private boolean clearScreenAfterStop;

    @Unique private final VirtualCoordinateHelper.VirtualSizeResult dreamingFishCore$virtualSize =
            new VirtualCoordinateHelper.VirtualSizeResult();
    @Unique private String dreamingFishCore$tip = "";

    protected ProgressScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$render(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                         float partialTick, CallbackInfo ci) {
        ci.cancel();
        if (this.stop) {
            if (this.clearScreenAfterStop) {
                this.minecraft.setScreen(null);
            }
            return;
        }

        VirtualCoordinateHelper.calculateVirtualSize(this, dreamingFishCore$virtualSize);
        if (dreamingFishCore$tip.isEmpty()) {
            dreamingFishCore$tip = LoadingTips.getRandomTip();
        }
        String status = dreamingFishCore$statusText();

        LoadingScreenUi.renderBackground(guiGraphics, this.width, this.height);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(dreamingFishCore$virtualSize.uiScale,
                dreamingFishCore$virtualSize.uiScale, 1.0F);
        LoadingScreenUi.renderTip(guiGraphics, this.font, dreamingFishCore$tip,
                Math.min(250, dreamingFishCore$virtualSize.virtualWidth - 52));
        LoadingScreenUi.renderBottomStatusText(guiGraphics, this.font,
                dreamingFishCore$virtualSize.virtualWidth,
                dreamingFishCore$virtualSize.virtualHeight,
                status);
        guiGraphics.pose().popPose();
        LoadingTransitionController.rememberTextFrame(dreamingFishCore$tip, status);
        LoadingTransitionController.renderLoadingEntry(guiGraphics, this.width, this.height);
    }

    @Unique
    private String dreamingFishCore$statusText() {
        if (this.stage != null && !this.stage.getString().isBlank()) {
            return this.stage.getString();
        }
        if (this.header != null && !this.header.getString().isBlank()) {
            return this.header.getString();
        }
        return "正在准备梦屿";
    }
}
