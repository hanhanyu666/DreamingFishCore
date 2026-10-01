package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.loading.LoadingSurface;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 资源加载遮罩改用统一加载画面。
 *
 * <p>首次启动（{@code fadeIn == false}）时字体和界面着色器都还没加载，只画背景与信号波形；
 * 之后切换资源包等重载可以正常显示文字。</p>
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin extends Overlay {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private Consumer<Optional<Throwable>> onFinish;
    @Shadow @Final private boolean fadeIn;
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;

    @Unique private LoadingSurface dreamingFishCore$surface;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();

        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        long now = Util.getMillis();
        float fadeOutProgress = this.fadeOutStart > -1L ? (now - this.fadeOutStart) / 1000.0F : -1.0F;

        if (this.fadeOutStart == -1L && this.reload.isDone()) {
            this.fadeOutStart = now;
            try {
                this.reload.checkExceptions();
                this.onFinish.accept(Optional.empty());
            } catch (Throwable throwable) {
                this.onFinish.accept(Optional.of(throwable));
            }
            if (this.minecraft.screen != null) {
                this.minecraft.screen.init(this.minecraft, width, height);
            }
            fadeOutProgress = 0.0F;
        }

        if (fadeOutProgress >= 1.0F) {
            this.minecraft.setOverlay(null);
            if (dreamingFishCore$surface != null) {
                dreamingFishCore$surface.host().close();
            }
            return;
        }

        float opacity = fadeOutProgress > -1.0F ? 1.0F - Mth.clamp(fadeOutProgress, 0.0F, 1.0F) : 1.0F;
        if (this.minecraft.screen != null && fadeOutProgress > -1.0F) {
            this.minecraft.screen.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + this.reload.getActualProgress() * 0.05F, 0.0F, 1.0F);

        if (dreamingFishCore$surface == null) {
            dreamingFishCore$surface = new LoadingSurface().startup(!this.fadeIn);
        }
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        dreamingFishCore$surface.status("正在载入梦屿资源").progress(Math.round(this.currentProgress * 100.0F))
                .opacity(opacity).render(guiGraphics, width, height);
        RenderSystem.enableDepthTest();
    }
}
