package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.loading.LoadingSurface;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingTransitionController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 接收服务器世界：接着连接阶段的进度从 90% 推进到 99%。 */
@Mixin(ReceivingLevelScreen.class)
public abstract class ReceivingLevelScreenMixin extends Screen {
    @Unique private static final String SERVER_STATUS = "正在搜寻梦屿信号";
    @Unique private final LoadingSurface dreamingFishCore$surface = new LoadingSurface();
    @Unique private long dreamingFishCore$receivingStartedAt = -1L;

    protected ReceivingLevelScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                         CallbackInfo ci) {
        ci.cancel();
        if (dreamingFishCore$receivingStartedAt < 0L) {
            dreamingFishCore$receivingStartedAt = System.currentTimeMillis();
        }
        int progress = LoadingSurface.estimateProgress(dreamingFishCore$receivingStartedAt,
                System.currentTimeMillis(), 90, 99, 3_600L);
        dreamingFishCore$surface.status(SERVER_STATUS).progress(progress).render(guiGraphics, this.width, this.height);
        LoadingTransitionController.rememberFrame(dreamingFishCore$surface);
        LoadingTransitionController.renderLoadingEntry(guiGraphics, this.width, this.height);
    }

    @Override
    public void removed() {
        dreamingFishCore$surface.host().close();
        super.removed();
    }
}
