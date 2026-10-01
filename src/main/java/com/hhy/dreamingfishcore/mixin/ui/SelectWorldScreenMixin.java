package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.vanilla.SelectionScreenUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 世界选择界面交给 {@link SelectionScreenUi}；原版列表与按钮逻辑保持不变。 */
@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen {
    @Shadow
    protected EditBox searchBox;

    @Shadow
    private WorldSelectionList list;

    @Unique
    private SelectionScreenUi dreamingFishCore$ui;

    protected SelectWorldScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private SelectionScreenUi dreamingFishCore$ui() {
        if (dreamingFishCore$ui == null) {
            dreamingFishCore$ui = new SelectionScreenUi(this, SelectionScreenUi.Kind.WORLDS, () -> this.list, () -> this.searchBox);
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
                    target = "Lnet/minecraft/client/gui/screens/worldselection/WorldSelectionList;<init>(Lnet/minecraft/client/gui/screens/worldselection/SelectWorldScreen;Lnet/minecraft/client/Minecraft;IIIILjava/lang/String;Lnet/minecraft/client/gui/screens/worldselection/WorldSelectionList;)V"
            ),
            index = 5,
            require = 0
    )
    private int dreamingFishCore$modernWorldRowHeight(int original) {
        return 50;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();
        dreamingFishCore$ui().render(guiGraphics, mouseX, mouseY, partialTick);
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return dreamingFishCore$ui().host().keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return dreamingFishCore$ui().host().charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void dreamingFishCore$removed(CallbackInfo ci) {
        if (dreamingFishCore$ui != null) {
            dreamingFishCore$ui.host().close();
        }
    }
}
