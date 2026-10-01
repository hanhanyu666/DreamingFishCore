package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.vanilla.TitleScreenUi;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 标题界面改由 {@link TitleScreenUi} 绘制与处理输入；原版仍创建按钮（含其他模组添加的），
 * 由新界面转调。原版控件本身不再接收鼠标与键盘事件，避免看不见的按钮被误触。
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Shadow
    private boolean fading;

    @Shadow
    private long fadeInStart;

    @Unique
    private TitleScreenUi dreamingFishCore$ui;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private TitleScreenUi dreamingFishCore$ui() {
        if (dreamingFishCore$ui == null) {
            dreamingFishCore$ui = new TitleScreenUi((TitleScreen) (Object) this);
        }
        return dreamingFishCore$ui;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();
        float fade = 1.0F;
        if (fading) {
            if (fadeInStart == 0L) {
                fadeInStart = Util.getMillis();
            }
            fade = Math.min(1.0F, (Util.getMillis() - fadeInStart) / 1000.0F);
            if (fade >= 1.0F) {
                fading = false;
            }
        }
        dreamingFishCore$ui().render(guiGraphics, fade);
        net.neoforged.neoforge.client.ClientHooks.renderMainMenu((TitleScreen) (Object) this, guiGraphics, this.font,
                this.width, this.height, 0xFF000000);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(dreamingFishCore$ui().host().mouseClicked(mouseX, mouseY, button));
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void dreamingFishCore$removed(CallbackInfo ci) {
        if (dreamingFishCore$ui != null) {
            dreamingFishCore$ui.host().close();
        }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return dreamingFishCore$ui().host().mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return dreamingFishCore$ui().host().keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return dreamingFishCore$ui().host().charTyped(codePoint, modifiers);
    }
}
