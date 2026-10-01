package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.loading.LoadingSurface;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingTransitionController;
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

/** 单人世界生成区块：统一加载画面，进度取自区块监听器。 */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {
    @Unique private static final String SINGLE_PLAYER_STATUS = "正在唤醒梦屿";
    @Shadow @Final private StoringChunkProgressListener progressListener;
    @Unique private final LoadingSurface dreamingFishCore$surface = new LoadingSurface();

    protected LevelLoadingScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                         CallbackInfo ci) {
        ci.cancel();
        dreamingFishCore$surface.status(SINGLE_PLAYER_STATUS).progress(Mth.clamp(progressListener.getProgress(), 0, 100))
                .render(guiGraphics, this.width, this.height);
        LoadingTransitionController.rememberFrame(dreamingFishCore$surface);
        LoadingTransitionController.renderLoadingEntry(guiGraphics, this.width, this.height);
    }

    @Override
    public void removed() {
        dreamingFishCore$surface.host().close();
        super.removed();
    }
}
