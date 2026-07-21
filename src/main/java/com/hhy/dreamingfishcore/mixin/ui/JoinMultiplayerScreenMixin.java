package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.util.ModernSelectionScreenUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {

    @Shadow
    protected ServerSelectionList serverSelectionList;

    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void dreamingFishCore$layoutAfterInit(CallbackInfo ci) {
        ModernSelectionScreenUi.resetAnimation(this);
        ModernSelectionScreenUi.Layout layout = ModernSelectionScreenUi.calculateLayout(this, false);
        ModernSelectionScreenUi.applyLayout(this, layout, null, this.serverSelectionList, false);
    }

    @ModifyArg(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/multiplayer/ServerSelectionList;<init>(Lnet/minecraft/client/gui/screens/multiplayer/JoinMultiplayerScreen;Lnet/minecraft/client/Minecraft;IIIII)V"
            ),
            index = 6,
            require = 0
    )
    private int dreamingFishCore$modernServerRowHeight(int original) {
        return 56;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$customBackground(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                   float partialTick, CallbackInfo ci) {
        ci.cancel();

        ModernSelectionScreenUi.Layout layout = ModernSelectionScreenUi.calculateLayout(this, false);
        ModernSelectionScreenUi.applyLayout(this, layout, null, this.serverSelectionList, false);
        ModernSelectionScreenUi.renderBase(guiGraphics, this, layout, ModernSelectionScreenUi.Kind.MULTIPLAYER);
        ModernSelectionScreenUi.prepareTransparentButtons(this);

        // In 1.20.1 this list is registered with addWidget(), not
        // addRenderableWidget(), so the cancelled vanilla render method must be
        // replaced with an explicit list render. ModernSelectionScreenUi has
        // already disabled the list's dirt background.
        if (this.serverSelectionList != null) {
            this.serverSelectionList.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        ModernSelectionScreenUi.setButtonsVisible(this, false);
        try {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        } finally {
            ModernSelectionScreenUi.setButtonsVisible(this, true);
        }

        int itemCount = this.serverSelectionList == null ? 0 : this.serverSelectionList.children().size();
        boolean hasSelection = this.serverSelectionList != null && this.serverSelectionList.getSelected() != null;
        ModernSelectionScreenUi.renderForeground(guiGraphics, this, layout,
                ModernSelectionScreenUi.Kind.MULTIPLAYER, itemCount, hasSelection, mouseX, mouseY);
    }
}
