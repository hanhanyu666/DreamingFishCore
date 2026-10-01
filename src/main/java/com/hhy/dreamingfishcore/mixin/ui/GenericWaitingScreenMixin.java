package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.loading.LoadingSurface;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingTransitionController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.GenericWaitingScreen;
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

/** 通用等待界面：标题与提示文字作为状态，原版按钮变成右下角的 Esc 操作。 */
@Mixin(GenericWaitingScreen.class)
public abstract class GenericWaitingScreenMixin extends Screen {
    @Shadow @Final @Nullable private Component messageText;
    @Shadow @Final private Component buttonLabel;
    @Shadow private Button button;

    @Unique private final LoadingSurface dreamingFishCore$surface = new LoadingSurface();

    protected GenericWaitingScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void dreamingFishCore$init(CallbackInfo ci) {
        if (button != null) {
            button.visible = false;
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();
        String status = this.title == null ? "" : this.title.getString();
        if (messageText != null && !messageText.getString().isBlank()) {
            status = status.isBlank() ? messageText.getString() : status + " · " + messageText.getString();
        }
        if (status.isBlank()) {
            status = "请稍候";
        }
        boolean actionReady = button != null && button.active;
        dreamingFishCore$surface.status(status).progress(-1)
                .action(actionReady ? buttonLabel.getString() : null, this::onClose)
                .render(guiGraphics, this.width, this.height);
        LoadingTransitionController.rememberFrame(dreamingFishCore$surface);
        LoadingTransitionController.renderLoadingEntry(guiGraphics, this.width, this.height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        return dreamingFishCore$surface.host().mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        return dreamingFishCore$surface.host().mouseReleased(mouseX, mouseY, mouseButton);
    }

    /** 右下角提示“Esc + 按钮文字”，所以按钮可用时 Esc 直接执行它。 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && button != null && button.active) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        dreamingFishCore$surface.host().close();
        super.removed();
    }
}
