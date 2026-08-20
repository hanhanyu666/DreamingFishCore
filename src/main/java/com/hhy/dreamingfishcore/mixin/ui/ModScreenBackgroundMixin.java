package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.util.LoadingTips;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingScreenUi;
import com.hhy.dreamingfishcore.client.ui.util.VirtualCoordinateHelper;
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

        LoadingScreenUi.renderBackground(guiGraphics, screen.width, screen.height);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(DREAMINGFISHCORE_VIRTUAL_SIZE.uiScale, DREAMINGFISHCORE_VIRTUAL_SIZE.uiScale, 1.0f);

        int vw = DREAMINGFISHCORE_VIRTUAL_SIZE.virtualWidth;
        int vh = DREAMINGFISHCORE_VIRTUAL_SIZE.virtualHeight;
        var font = Minecraft.getInstance().font;
        LoadingScreenUi.renderTip(guiGraphics, font, dreamingFishCore$genericTip);

        String statusText = screen.getTitle() == null ? "处理中" : screen.getTitle().getString();
        if (statusText == null || statusText.isBlank()) {
            statusText = "处理中";
        }
        LoadingScreenUi.renderBottomStatusText(guiGraphics, font, vw, vh, statusText);

        guiGraphics.pose().popPose();
    }

}
