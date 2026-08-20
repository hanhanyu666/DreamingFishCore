package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.chat.ImmersiveChatManager;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/** Routes vanilla chat storage/events into DreamingFishCore's client-owned chat presentation. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingfish$renderImmersiveChat(GuiGraphics graphics, int tickCount, int mouseX, int mouseY,
                                                  boolean focused, CallbackInfo ci) {
        ImmersiveChatManager.render(graphics, mouseX, mouseY, focused);
        ci.cancel();
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD")
    )
    private void dreamingfish$captureVanillaMessage(Component message, @Nullable MessageSignature signature,
                                                     @Nullable GuiMessageTag tag, CallbackInfo ci) {
        ImmersiveChatManager.captureVanillaMessage(message, tag);
    }

    @Inject(method = "scrollChat", at = @At("HEAD"), cancellable = true)
    private void dreamingfish$scrollImmersiveChat(int amount, CallbackInfo ci) {
        ImmersiveChatManager.scroll(amount);
        ci.cancel();
    }

    @Inject(method = "getLinesPerPage", at = @At("HEAD"), cancellable = true)
    private void dreamingfish$getImmersiveLinesPerPage(CallbackInfoReturnable<Integer> cir) {
        Minecraft mc = Minecraft.getInstance();
        cir.setReturnValue(ImmersiveChatManager.getEstimatedLinesPerPage(
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()));
    }

    @Inject(method = "getClickedComponentStyleAt", at = @At("HEAD"), cancellable = true)
    private void dreamingfish$getImmersiveChatStyle(double mouseX, double mouseY,
                                                     CallbackInfoReturnable<Style> cir) {
        cir.setReturnValue(ImmersiveChatManager.getStyleAt(mouseX, mouseY));
    }

    @Inject(method = "getMessageTagAt", at = @At("HEAD"), cancellable = true)
    private void dreamingfish$disableVanillaChatTagHitTest(double mouseX, double mouseY,
                                                            CallbackInfoReturnable<GuiMessageTag> cir) {
        // The custom layout intentionally does not render vanilla report/system tag icons.
        cir.setReturnValue(null);
    }
}
