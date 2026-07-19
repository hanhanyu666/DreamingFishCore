package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.util.LoadingTips;
import com.hhy.dreamingfishcore.client.util.UiBackgroundRenderer;
import com.hhy.dreamingfishcore.client.util.VirtualCoordinateHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenericDirtMessageScreen.class)
public abstract class GenericDirtMessageScreenMixin extends Screen {

    @Unique private static final int ACCENT_BLUE = 0xFF0088FF;
    @Unique private static final int BAR_BG = 0x66000000;
    @Unique private static final int BAR_HIGHLIGHT = 0xFF55AAFF;

    @Unique private final VirtualCoordinateHelper.VirtualSizeResult vs = new VirtualCoordinateHelper.VirtualSizeResult();
    @Unique private String tip = "";

    protected GenericDirtMessageScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();
        dreamingFishCore$renderCleanWaitingScreen(guiGraphics);
    }

    @Unique
    private void dreamingFishCore$renderCleanWaitingScreen(GuiGraphics guiGraphics) {
        VirtualCoordinateHelper.calculateVirtualSize(this, vs);
        if (tip.isEmpty()) {
            tip = LoadingTips.getRandomTip();
        }

        UiBackgroundRenderer.renderLoadingBackground(guiGraphics, this.width, this.height);
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0x88000000, 0xCC000000);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(vs.uiScale, vs.uiScale, 1.0f);

        int vw = vs.virtualWidth;
        int vh = vs.virtualHeight;

        renderTip(guiGraphics);

        // 底部进度条（循环动画）
        int barMargin = 32;
        int barHeight = 6;
        int barX = barMargin;
        int barW = vw - barMargin * 2;
        int barY = vh - 28;

        long now = System.currentTimeMillis();
        int fakeProgress = (int) ((now % 5000) * 100 / 5000);

        String statusText = this.title == null ? "处理中" : this.title.getString();
        if (statusText == null || statusText.isBlank()) {
            statusText = "处理中";
        }
        String progressText = fakeProgress + "%";
        guiGraphics.drawString(this.font, trimToWidth(statusText, Math.max(20, barW - this.font.width(progressText) - 18)),
                barX, barY - 12, 0xFFFFFFFF, true);
        guiGraphics.drawString(this.font, progressText, barX + barW - this.font.width(progressText), barY - 12,
                0xFFFFFFFF, true);

        renderRoundedBar(guiGraphics, barX, barY, barW, barHeight, BAR_BG);
        int fillW = barW * fakeProgress / 100;
        if (fillW > 0) {
            renderRoundedBar(guiGraphics, barX, barY, fillW, barHeight, ACCENT_BLUE);
            if (fillW > 2) {
                guiGraphics.fill(barX + 2, barY, barX + fillW - 2, barY + 1, BAR_HIGHLIGHT);
            }
        }

        guiGraphics.pose().popPose();
    }

    @Unique
    private void renderTip(GuiGraphics guiGraphics) {
        int tipX = 8;
        int tipY = 8;
        guiGraphics.drawString(this.font, "§e💡 提示", tipX, tipY, 0xFFFFFFFF, true);
        guiGraphics.drawString(this.font, "§7" + tip, tipX, tipY + 13, 0xFFAAAAAA, true);
    }

    @Unique
    private static void renderRoundedBar(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int r = h >= 6 ? h / 3 : 1;
        int ih = Math.max(1, h - 2);
        int left = x + r;
        int right = x + w - r;
        if (right > left) g.fill(left, y, right, y + h, color);
        g.fill(x, y + 1, x + r, y + 1 + ih, color);
        g.fill(x + w - r, y + 1, x + w, y + 1 + ih, color);
    }

    @Unique
    private String trimToWidth(String text, int maxWidth) {
        if (text == null || this.font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width(ellipsis))) + ellipsis;
    }
}
