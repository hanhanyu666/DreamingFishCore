package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.integration.EconomySystemUiBridge;
import com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen.ServerScreenUI_Screen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes DreamingFish terminal entries to EconomySystem's native screens when available.
 *
 * <p>The original terminal handlers remain as fallbacks. We only cancel the click after the
 * external screen has been created successfully.</p>
 */
@Mixin(ServerScreenUI_Screen.class)
public abstract class ServerScreenUiEconomyBridgeMixin {

    @Shadow
    private int selectedLeftButtonIndex;

    @Inject(method = "handleLeftButtonClick", at = @At("HEAD"), cancellable = true)
    private void dreamingfish$openEconomyNativeScreen(int index, CallbackInfo ci) {
        Screen parent = (Screen) (Object) this;

        if ((index == 6 || index == 7) && EconomySystemUiBridge.openHome(parent)) {
            selectedLeftButtonIndex = -1;
            ci.cancel();
        }
    }
}
