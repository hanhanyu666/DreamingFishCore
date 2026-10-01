package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.vanilla.SelectionScreenUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 服务器列表交给 {@link SelectionScreenUi}；延迟检测、局域网扫描与按钮逻辑保持原版。 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    @Shadow
    protected ServerSelectionList serverSelectionList;

    @Unique
    private SelectionScreenUi dreamingFishCore$ui;

    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private SelectionScreenUi dreamingFishCore$ui() {
        if (dreamingFishCore$ui == null) {
            dreamingFishCore$ui = new SelectionScreenUi(this, SelectionScreenUi.Kind.SERVERS, () -> this.serverSelectionList, () -> null);
        }
        return dreamingFishCore$ui;
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void dreamingFishCore$afterInit(CallbackInfo ci) {
        dreamingFishCore$ui().hideVanillaWidgets();
    }

    @ModifyArg(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/multiplayer/ServerSelectionList;<init>(Lnet/minecraft/client/gui/screens/multiplayer/JoinMultiplayerScreen;Lnet/minecraft/client/Minecraft;IIII)V"
            ),
            index = 5,
            require = 0
    )
    private int dreamingFishCore$modernServerRowHeight(int original) {
        return 50;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();
        dreamingFishCore$ui().render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (dreamingFishCore$ui().host().keyPressed(keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return dreamingFishCore$ui().host().mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = dreamingFishCore$ui().host().mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button) || handled;
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void dreamingFishCore$removed(CallbackInfo ci) {
        if (dreamingFishCore$ui != null) {
            dreamingFishCore$ui.host().close();
        }
    }
}
