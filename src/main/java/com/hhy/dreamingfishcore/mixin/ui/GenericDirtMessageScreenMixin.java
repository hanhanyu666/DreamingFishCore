package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.loading.LoadingSurface;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingTransitionController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 通用消息界面（如“正在保存世界”）：只显示状态文字。 */
@Mixin(GenericMessageScreen.class)
public abstract class GenericDirtMessageScreenMixin extends Screen {
    @Unique private final LoadingSurface dreamingFishCore$surface = new LoadingSurface();

    protected GenericDirtMessageScreenMixin(Component title) {
        super(title);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        String status = this.title == null ? "" : this.title.getString();
        dreamingFishCore$surface.status(status.isBlank() ? "处理中" : status).progress(-1)
                .render(guiGraphics, this.width, this.height);
        LoadingTransitionController.rememberFrame(dreamingFishCore$surface);
        LoadingTransitionController.renderLoadingEntry(guiGraphics, this.width, this.height);
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                   float partialTick, CallbackInfo ci) {
        ci.cancel();
    }

    @Override
    public void removed() {
        dreamingFishCore$surface.host().close();
        super.removed();
    }
}
