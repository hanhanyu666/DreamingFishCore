package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.chat.ImmersiveChatManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.glfw.GLFW;

/** Keeps the vanilla text entry/command suggestions while docking them to the custom movable chat window. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow
    protected EditBox input;

    @Inject(method = "init", at = @At("TAIL"))
    private void dreamingfish$positionChatInput(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        ImmersiveChatManager.positionInput(input,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void dreamingfish$clearPendingChatQuote(CallbackInfo ci) {
        ImmersiveChatManager.onChatClosed();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void dreamingfish$sendQuotedMessage(int keyCode, int scanCode, int modifiers,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && ImmersiveChatManager.submitQuotedMessage(input.getValue())) {
            input.setValue("");
            Minecraft.getInstance().setScreen(null);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void dreamingfish$startChatWindowDrag(double mouseX, double mouseY, int button,
                                                   CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (ImmersiveChatManager.handleMouseClicked(mouseX, mouseY, button,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight())) {
            cir.setReturnValue(true);
        }
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0)
    )
    private void dreamingfish$renderDockedInputBackground(GuiGraphics graphics,
                                                           int minX, int minY, int maxX, int maxY, int color) {
        ImmersiveChatManager.drawInputBackground(graphics);
    }
}
