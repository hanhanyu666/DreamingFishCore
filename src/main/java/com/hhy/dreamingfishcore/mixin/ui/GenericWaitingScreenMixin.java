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

    @Unique private static final int ACCENT_BLUE = 0xFF0088FF;
    @Unique private static final int BAR_BG = 0x66000000;
    @Unique private static final int BAR_HIGHLIGHT = 0xFF55AAFF;

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

        long now = System.currentTimeMillis();

        // 底部进度条（循环动画）
        int barMargin = 32;
        int barHeight = 6;
        int barX = barMargin;
        int barW = vw - barMargin * 2;
        int barY = vh - 28;

        int fakeProgress = (int) ((now % 6000) * 100 / 6000);

        String statusText = this.title == null ? "请稍候" : this.title.getString();
        if (statusText == null || statusText.isBlank()) {
            statusText = "请稍候";
        }
        String progressText = fakeProgress + "%";
        guiGraphics.drawString(this.font, LoadingScreenUi.trimToWidth(statusText, this.font,
                        Math.max(20, barW - this.font.width(progressText) - 18)),
                barX, barY - 12, 0xFFFFFFFF, true);
        guiGraphics.drawString(this.font, progressText, barX + barW - this.font.width(progressText), barY - 12,
                0xFFFFFFFF, true);

        LoadingScreenUi.renderProgressBar(guiGraphics, barX, barY, barW, barHeight, fakeProgress,
                BAR_BG, ACCENT_BLUE, BAR_HIGHLIGHT);

        guiGraphics.pose().popPose();
    }

}
