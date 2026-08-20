package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.loading.LoadingScreenUi;
import com.hhy.dreamingfishcore.client.ui.util.LoadingTips;
import com.hhy.dreamingfishcore.client.ui.util.VirtualCoordinateHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {

    @Unique private static final String SINGLE_PLAYER_STATUS = "正在唤醒梦屿";
    @Shadow @Final private StoringChunkProgressListener progressListener;
    @Unique private final VirtualCoordinateHelper.VirtualSizeResult vs = new VirtualCoordinateHelper.VirtualSizeResult();
    @Unique private String tip = "";

    protected LevelLoadingScreenMixin(Component title) {
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

        long now = System.currentTimeMillis();
        int progress = Mth.clamp(progressListener.getProgress(), 0, 100);

        LoadingScreenUi.renderBackground(guiGraphics, this.width, this.height);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(vs.uiScale, vs.uiScale, 1.0f);
        LoadingScreenUi.renderTip(guiGraphics, this.font, tip, Math.min(250, vs.virtualWidth - 52));
        LoadingScreenUi.renderStatusWaveform(guiGraphics, this.font, vs.virtualWidth, vs.virtualHeight,
                SINGLE_PLAYER_STATUS, progress, now);
        guiGraphics.pose().popPose();
    }
}
