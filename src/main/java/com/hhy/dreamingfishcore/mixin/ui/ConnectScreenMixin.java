package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.loading.LoadingScreenUi;
import com.hhy.dreamingfishcore.client.ui.util.LoadingTips;
import com.hhy.dreamingfishcore.client.ui.util.VirtualCoordinateHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {

    @Unique private static final String SERVER_STATUS = "正在搜寻梦屿信号";
    @Unique private final VirtualCoordinateHelper.VirtualSizeResult vs = new VirtualCoordinateHelper.VirtualSizeResult();
    @Unique private String tip = "";
    @Unique private Button dreamingFishCore$cancelBtn;
    @Unique private long dreamingFishCore$connectionStartedAt = -1L;

    protected ConnectScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void dreamingFishCore$init(CallbackInfo ci) {
        VirtualCoordinateHelper.calculateVirtualSize(this, vs);
        if (tip.isEmpty()) {
            tip = LoadingTips.getRandomTip();
        }
        if (dreamingFishCore$connectionStartedAt < 0L) {
            dreamingFishCore$connectionStartedAt = System.currentTimeMillis();
        }

        for (var child : this.children()) {
            if (child instanceof Button button) {
                dreamingFishCore$cancelBtn = button;
                button.setMessage(Component.literal("按 Esc 中断连接"));
                break;
            }
        }
        dreamingFishCore$updateCancelButtonPosition();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                         CallbackInfo ci) {
        ci.cancel();

        VirtualCoordinateHelper.calculateVirtualSize(this, vs);
        dreamingFishCore$updateCancelButtonPosition();
        long now = System.currentTimeMillis();
        int progress = LoadingScreenUi.estimateProgress(
                dreamingFishCore$connectionStartedAt, now, 6, 90, 5_200L);

        LoadingScreenUi.renderBackground(guiGraphics, this.width, this.height);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(vs.uiScale, vs.uiScale, 1.0f);
        LoadingScreenUi.renderTip(guiGraphics, this.font, tip, Math.min(250, vs.virtualWidth - 52));
        LoadingScreenUi.renderStatusWaveform(guiGraphics, this.font, vs.virtualWidth, vs.virtualHeight,
                SERVER_STATUS, progress, now);
        LoadingScreenUi.renderCancelHint(guiGraphics, this.font, vs.virtualWidth, vs.virtualHeight);
        guiGraphics.pose().popPose();
    }

    @Unique
    private void dreamingFishCore$updateCancelButtonPosition() {
        if (dreamingFishCore$cancelBtn == null) {
            return;
        }

        int hintWidth = LoadingScreenUi.getCancelHintWidth(this.font);
        int virtualX = vs.virtualWidth - 24 - hintWidth;
        int virtualY = vs.virtualHeight - 28;
        dreamingFishCore$cancelBtn.setX(Math.round(virtualX * vs.uiScale));
        dreamingFishCore$cancelBtn.setY(Math.round(virtualY * vs.uiScale));
        dreamingFishCore$cancelBtn.setWidth(Math.max(1, Math.round((hintWidth + 8) * vs.uiScale)));
        dreamingFishCore$cancelBtn.setHeight(Math.max(12, Math.round(18 * vs.uiScale)));
    }

    /** Screen.keyPressed is inherited by ConnectScreen, so the mixin supplies the Esc action directly. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && dreamingFishCore$cancelBtn != null) {
            dreamingFishCore$cancelBtn.onPress();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
