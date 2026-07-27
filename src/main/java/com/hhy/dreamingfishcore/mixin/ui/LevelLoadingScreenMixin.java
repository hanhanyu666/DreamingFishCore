package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.util.LoadingTips;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingScreenUi;
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

    @Unique private static final int ACCENT_BLUE = 0xFF0088FF;
    @Unique private static final int BAR_BG = 0x66000000;
    @Unique private static final int BAR_HIGHLIGHT = 0xFF55AAFF;

    @Shadow @Final private StoringChunkProgressListener progressListener;

    @Unique private final VirtualCoordinateHelper.VirtualSizeResult vs = new VirtualCoordinateHelper.VirtualSizeResult();
    @Unique private String tip = "";

    protected LevelLoadingScreenMixin(Component title) {
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

        // 左上角提示
        LoadingScreenUi.renderTip(guiGraphics, this.font, tip);

        // 底部进度条
        int barMargin = 32;
        int barHeight = 6;
        int barX = barMargin;
        int barW = vw - barMargin * 2;
        int barY = vh - 28;

        int progress = Mth.clamp(progressListener.getProgress(), 0, 100);

        // 进度条上方右侧文字
        String label = "正在加载世界... " + progress + "%";
        int labelW = this.font.width(label);
        guiGraphics.drawString(this.font, label, barX + barW - labelW, barY - 12, 0xFFFFFFFF, true);

        LoadingScreenUi.renderProgressBar(guiGraphics, barX, barY, barW, barHeight, progress,
                BAR_BG, ACCENT_BLUE, BAR_HIGHLIGHT);

        guiGraphics.pose().popPose();
    }

}
