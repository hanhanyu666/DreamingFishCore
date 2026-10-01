package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.loading.LoadingSurface;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingTransitionController;
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
    @Shadow private int progress;
    @Shadow private boolean stop;
    @Shadow @Final private boolean clearScreenAfterStop;

    @Unique private final LoadingSurface dreamingFishCore$surface = new LoadingSurface();

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
        dreamingFishCore$surface.status(dreamingFishCore$statusText()).progress(this.progress > 0 ? this.progress : -1)
                .render(guiGraphics, this.width, this.height);
        LoadingTransitionController.rememberFrame(dreamingFishCore$surface);
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

    @Override
    public void removed() {
        dreamingFishCore$surface.host().close();
        super.removed();
    }
}
