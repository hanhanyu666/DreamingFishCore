package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.loading.LoadingTransitionController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/** Observes screen swaps so the multi-screen world loading flow behaves like one transition. */
@Mixin(Minecraft.class)
public abstract class MinecraftScreenTransitionMixin {
    @Unique private Screen dreamingFishCore$screenBeforeChange;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$beforeSetScreen(@Nullable Screen nextScreen, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        dreamingFishCore$screenBeforeChange = minecraft.screen;
        if (LoadingTransitionController.interceptScreenChange(
                minecraft, dreamingFishCore$screenBeforeChange, nextScreen)) {
            ci.cancel();
        }
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void dreamingFishCore$afterSetScreen(@Nullable Screen nextScreen, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        LoadingTransitionController.onScreenChanged(
                dreamingFishCore$screenBeforeChange,
                minecraft.screen);
    }
}
