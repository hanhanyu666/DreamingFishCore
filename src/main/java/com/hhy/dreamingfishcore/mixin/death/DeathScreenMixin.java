package com.hhy.dreamingfishcore.mixin.death;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.client.ui.DeathScreenUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 死亡界面改由 {@link DeathScreenUi} 绘制：保留游戏画面作为背景，原版按钮不再创建。
 */
@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin extends Screen {
    @Unique
    private DeathScreenUi dreamingFishCore$ui;

    protected DeathScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private DeathScreenUi dreamingFishCore$ui() {
        if (dreamingFishCore$ui == null) {
            dreamingFishCore$ui = new DeathScreenUi((DeathScreen) (Object) this);
        }
        return dreamingFishCore$ui;
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$init(CallbackInfo ci) {
        ci.cancel();
        dreamingFishCore$ui();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();
        dreamingFishCore$ui().render(guiGraphics);
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                                  CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(dreamingFishCore$ui().host().mouseClicked(mouseX, mouseY, button));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        dreamingFishCore$ui().host().mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return dreamingFishCore$ui().host().mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return dreamingFishCore$ui().host().mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return dreamingFishCore$ui().host().keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        if (dreamingFishCore$ui != null) {
            dreamingFishCore$ui.host().close();
        }
        super.removed();
    }
}
