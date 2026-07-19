package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.util.ModernSelectionScreenUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen {

    @Shadow
    protected EditBox searchBox;

    @Shadow
    private WorldSelectionList list;

    protected SelectWorldScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void dreamingFishCore$layoutAfterInit(CallbackInfo ci) {
        ModernSelectionScreenUi.resetAnimation(this);
        ModernSelectionScreenUi.Layout layout = ModernSelectionScreenUi.calculateLayout(this, true);
        ModernSelectionScreenUi.applyLayout(this, layout, this.searchBox, this.list, true);
    }

    @ModifyArg(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/worldselection/WorldSelectionList;<init>(Lnet/minecraft/client/gui/screens/worldselection/SelectWorldScreen;Lnet/minecraft/client/Minecraft;IIIIILjava/lang/String;Lnet/minecraft/client/gui/screens/worldselection/WorldSelectionList;)V"
            ),
            index = 6,
            require = 0
    )
    private int dreamingFishCore$modernWorldRowHeight(int original) {
        return 56;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$customBackground(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                   float partialTick, CallbackInfo ci) {
        ci.cancel();

        ModernSelectionScreenUi.Layout layout = ModernSelectionScreenUi.calculateLayout(this, true);
        ModernSelectionScreenUi.applyLayout(this, layout, this.searchBox, this.list, true);
        ModernSelectionScreenUi.renderBase(guiGraphics, this, layout, ModernSelectionScreenUi.Kind.WORLDS);
        ModernSelectionScreenUi.prepareTransparentButtons(this);

        ModernSelectionScreenUi.setButtonsVisible(this, false);
        try {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        } finally {
            ModernSelectionScreenUi.setButtonsVisible(this, true);
        }
        if (this.searchBox != null && this.searchBox.visible) {
            this.searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        int itemCount = this.list == null ? 0 : this.list.children().size();
        boolean hasSelection = this.list != null && this.list.getSelectedOpt().isPresent();
        ModernSelectionScreenUi.renderForeground(guiGraphics, this, layout,
                ModernSelectionScreenUi.Kind.WORLDS, itemCount, hasSelection, mouseX, mouseY);
    }
}
