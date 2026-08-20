package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.loading.LoadingScreenUi;
import com.hhy.dreamingfishcore.client.ui.util.LoadingTips;
import com.hhy.dreamingfishcore.client.ui.util.VirtualCoordinateHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReceivingLevelScreen.class)
public abstract class ReceivingLevelScreenMixin extends Screen {

    @Unique private static final String SERVER_STATUS = "正在搜寻梦屿信号";
    @Unique private final VirtualCoordinateHelper.VirtualSizeResult vs = new VirtualCoordinateHelper.VirtualSizeResult();
    @Unique private String tip = "";
    @Unique private long dreamingFishCore$receivingStartedAt = -1L;

    protected ReceivingLevelScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                         CallbackInfo ci) {
        ci.cancel();

        VirtualCoordinateHelper.calculateVirtualSize(this, vs);
        if (tip.isEmpty()) {
            tip = LoadingTips.getRandomTip();
        }
        if (dreamingFishCore$receivingStartedAt < 0L) {
            dreamingFishCore$receivingStartedAt = System.currentTimeMillis();
        }

        long now = System.currentTimeMillis();
        int progress = LoadingScreenUi.estimateProgress(
                dreamingFishCore$receivingStartedAt, now, 90, 99, 3_600L);

        LoadingScreenUi.renderBackground(guiGraphics, this.width, this.height);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(vs.uiScale, vs.uiScale, 1.0f);
        LoadingScreenUi.renderTip(guiGraphics, this.font, tip, Math.min(250, vs.virtualWidth - 52));
        LoadingScreenUi.renderStatusWaveform(guiGraphics, this.font, vs.virtualWidth, vs.virtualHeight,
                SERVER_STATUS, progress, now);
        guiGraphics.pose().popPose();
    }
}
