package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.util.LoadingTips;
import com.hhy.dreamingfishcore.client.util.UiBackgroundRenderer;
import com.hhy.dreamingfishcore.client.util.VirtualCoordinateHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ModScreenBackgroundMixin {
    @Unique private static final String GENERIC_MESSAGE_SCREEN = "net.minecraft.client.gui.screens.GenericMessageScreen";
    @Unique private static final int DREAMINGFISHCORE_ACCENT_BLUE = 0xFF0088FF;
    @Unique private static final int DREAMINGFISHCORE_BAR_BG = 0x66000000;
    @Unique private static final int DREAMINGFISHCORE_BAR_HIGHLIGHT = 0xFF55AAFF;
    @Unique private static final VirtualCoordinateHelper.VirtualSizeResult DREAMINGFISHCORE_VIRTUAL_SIZE =
            new VirtualCoordinateHelper.VirtualSizeResult();
    @Unique private static String dreamingFishCore$genericTip = "";

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$replaceGenericMessageScreen(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                              float partialTick, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!GENERIC_MESSAGE_SCREEN.equals(screen.getClass().getName())) {
            return;
        }

        ci.cancel();
        dreamingFishCore$renderSilentLoadingScreen(screen, guiGraphics);
    }

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$skipVanillaBackgroundForModScreens(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (((Object) this).getClass().getName().startsWith("com.hhy.dreamingfishcore.")) {
            ci.cancel();
        }
    }

    @Unique
    private static void dreamingFishCore$renderSilentLoadingScreen(Screen screen, GuiGraphics guiGraphics) {
        VirtualCoordinateHelper.calculateVirtualSize(screen, DREAMINGFISHCORE_VIRTUAL_SIZE);
        if (dreamingFishCore$genericTip.isEmpty()) {
            dreamingFishCore$genericTip = LoadingTips.getRandomTip();
        }

        UiBackgroundRenderer.renderLoadingBackground(guiGraphics, screen.width, screen.height);
        guiGraphics.fillGradient(0, 0, screen.width, screen.height, 0x88000000, 0xCC000000);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(DREAMINGFISHCORE_VIRTUAL_SIZE.uiScale, DREAMINGFISHCORE_VIRTUAL_SIZE.uiScale, 1.0f);

        int vw = DREAMINGFISHCORE_VIRTUAL_SIZE.virtualWidth;
        int vh = DREAMINGFISHCORE_VIRTUAL_SIZE.virtualHeight;
        int tipX = 8;
        int tipY = 8;
        var font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, "§e💡 提示", tipX, tipY, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, "§7" + dreamingFishCore$genericTip, tipX, tipY + 13, 0xFFAAAAAA, true);

        int barMargin = 32;
        int barHeight = 6;
        int barX = barMargin;
        int barW = vw - barMargin * 2;
        int barY = vh - 28;
        int fakeProgress = (int) ((System.currentTimeMillis() % 5000) * 100 / 5000);
        String statusText = screen.getTitle() == null ? "处理中" : screen.getTitle().getString();
        if (statusText == null || statusText.isBlank()) {
            statusText = "处理中";
        }
        String progressText = fakeProgress + "%";
        guiGraphics.drawString(font, dreamingFishCore$trimToWidth(statusText, font, Math.max(20, barW - font.width(progressText) - 18)),
                barX, barY - 12, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, progressText, barX + barW - font.width(progressText), barY - 12, 0xFFFFFFFF, true);

        dreamingFishCore$renderRoundedBar(guiGraphics, barX, barY, barW, barHeight, DREAMINGFISHCORE_BAR_BG);
        int fillW = barW * fakeProgress / 100;
        if (fillW > 0) {
            dreamingFishCore$renderRoundedBar(guiGraphics, barX, barY, fillW, barHeight, DREAMINGFISHCORE_ACCENT_BLUE);
            if (fillW > 2) {
                guiGraphics.fill(barX + 2, barY, barX + fillW - 2, barY + 1, DREAMINGFISHCORE_BAR_HIGHLIGHT);
            }
        }

        guiGraphics.pose().popPose();
    }

    @Unique
    private static void dreamingFishCore$renderRoundedBar(GuiGraphics guiGraphics, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int radius = h >= 6 ? h / 3 : 1;
        int innerHeight = Math.max(1, h - 2);
        int left = x + radius;
        int right = x + w - radius;
        if (right > left) {
            guiGraphics.fill(left, y, right, y + h, color);
        }
        guiGraphics.fill(x, y + 1, x + radius, y + 1 + innerHeight, color);
        guiGraphics.fill(x + w - radius, y + 1, x + w, y + 1 + innerHeight, color);
    }

    @Unique
    private static String dreamingFishCore$trimToWidth(String text, net.minecraft.client.gui.Font font, int maxWidth) {
        if (text == null || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }
}
