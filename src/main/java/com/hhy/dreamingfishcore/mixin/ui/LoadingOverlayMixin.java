package com.hhy.dreamingfishcore.mixin.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.util.UiBackgroundRenderer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.resources.ResourceLocation;
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
 * LoadingOverlay Mixin
 * Blue rounded progress bar like world generation screen
 * Injects at RETURN to draw custom UI after original rendering
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin extends Overlay {

    @Unique private static final int ACCENT_BLUE = 0xFF0088FF;
    @Unique private static final int BAR_BACKGROUND = 0x66000000;

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private Consumer<Optional<Throwable>> onFinish;
    @Shadow @Final private boolean fadeIn;
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dreamingFishCore$init(CallbackInfo ci) {
        DreamingFishCore.LOGGER.info("LoadingOverlayMixin initialized!");
    }

    @Inject(
        method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        at = @At("HEAD"),
        cancellable = true
    )
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
            return;
        }

        int alpha = fadeOutProgress > -1.0F
                ? Mth.ceil((1.0F - Mth.clamp(fadeOutProgress, 0.0F, 1.0F)) * 255.0F)
                : 255;
        float alphaF = alpha / 255.0F;

        if (this.minecraft.screen != null && fadeOutProgress > -1.0F) {
            this.minecraft.screen.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        RenderSystem.enableBlend();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, alphaF);
        UiBackgroundRenderer.renderLoadingBackground(guiGraphics, width, height);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.fillGradient(0, 0, width, height,
                (Mth.ceil(0x88 * alphaF) << 24),
                (Mth.ceil(0xCC * alphaF) << 24));

        // Update progress
        float actualProgress = this.reload.getActualProgress();
        this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + actualProgress * 0.05F, 0.0F, 1.0F);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Progress bar at bottom
        int barMargin = 40;
        int progressBarHeight = 8;
        int progressBarX = barMargin;
        int progressBarWidth = width - barMargin * 2;
        int progressBarY = height - 35;

        int barBg = (BAR_BACKGROUND & 0x00FFFFFF) | (Mth.ceil(((BAR_BACKGROUND >>> 24) & 255) * alphaF) << 24);
        int barAccent = (ACCENT_BLUE & 0x00FFFFFF) | (Mth.ceil(((ACCENT_BLUE >>> 24) & 255) * alphaF) << 24);

        dreamingFishCore$renderRoundedBar(guiGraphics, progressBarX, progressBarY, progressBarWidth, progressBarHeight, barBg);

        // Progress bar (blue)
        int progressWidth = (int) (this.currentProgress * progressBarWidth);
        if (progressWidth > 0) {
            dreamingFishCore$renderRoundedBar(guiGraphics, progressBarX, progressBarY, progressWidth, progressBarHeight, barAccent);

            // Top highlight line
            if (progressWidth > 2) {
                guiGraphics.fill(progressBarX + 2, progressBarY, progressBarX + progressWidth - 2, progressBarY + 1,
                        (Mth.ceil(255 * alphaF) << 24) | 0x55AAFF);
            }

            // Pulsing glow effect
            if ((now / 500) % 2 == 0) {
                dreamingFishCore$renderRoundedBar(guiGraphics, progressBarX, progressBarY, progressWidth, progressBarHeight,
                        (Mth.ceil(0x33 * alphaF) << 24) | 0x0055FF);
            }

            // Star-like sparkles
            if (progressWidth > 2) {
                int sparkleCount = Math.min(8, Math.max(4, progressWidth / 40));
                int sparkleY1 = progressBarY + 1;
                int sparkleY2 = progressBarY + progressBarHeight - 1;
                for (int i = 0; i < sparkleCount; i++) {
                    int offset = (int) ((now / 220 + i * 13) % 1000);
                    int sx = progressBarX + (offset * 37 + i * 53) % Math.max(1, progressWidth);
                    int sy = sparkleY1 + (i * 3 + (int) (now / 350)) % Math.max(1, (sparkleY2 - sparkleY1));
                    guiGraphics.fill(sx, sy, sx + 1, sy + 1, (Mth.ceil(0x33 * alphaF) << 24) | 0xFFFFFF);
                    if ((now / 700 + i) % 2 == 0) {
                        guiGraphics.fill(sx - 1, sy, sx, sy + 1, (Mth.ceil(0x22 * alphaF) << 24) | 0x00FFFF);
                    }
                }
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    @Unique
    private void dreamingFishCore$renderRoundedBar(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int radius = height >= 6 ? height / 3 : 1;
        int innerHeight = Math.max(1, height - 2);
        int left = x + radius;
        int right = x + width - radius;
        if (right > left) {
            guiGraphics.fill(left, y, right, y + height, color);
        }
        guiGraphics.fill(x, y + 1, x + radius, y + 1 + innerHeight, color);
        guiGraphics.fill(x + width - radius, y + 1, x + width, y + 1 + innerHeight, color);
    }
}
