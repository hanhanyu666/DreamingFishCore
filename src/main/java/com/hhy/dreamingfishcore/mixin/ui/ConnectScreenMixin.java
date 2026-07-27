package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.util.LoadingTips;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingScreenUi;
import com.hhy.dreamingfishcore.client.ui.util.VirtualCoordinateHelper;
import net.minecraft.client.Minecraft;
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

    @Unique private static final int ACCENT_GREEN = 0xFF3FBF7F;
    @Unique private static final int BAR_BG = 0x66000000;
    @Unique private static final int BAR_HIGHLIGHT = 0xFF8EF0B8;

    @Unique private final VirtualCoordinateHelper.VirtualSizeResult vs = new VirtualCoordinateHelper.VirtualSizeResult();
    @Unique private String tip = "";
    @Unique private Button dreamingFishCore$cancelBtn;

    protected ConnectScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$init(CallbackInfo ci) {
        ci.cancel();
        VirtualCoordinateHelper.calculateVirtualSize(this, vs);

        String cancelText = "← 取消连接";
        int textW = this.font.width(cancelText);
        int btnX = (int) (8 * vs.uiScale);
        int btnY = this.height - (int) (50 * vs.uiScale);

        dreamingFishCore$cancelBtn = new TextCancelButton(btnX, btnY, textW + 8, 12,
            Component.literal(cancelText), btn -> dreamingFishCore$disconnect());
        this.addRenderableWidget(dreamingFishCore$cancelBtn);

        if (tip.isEmpty()) {
            tip = LoadingTips.getRandomTip();
        }
    }

    @Unique
    private void dreamingFishCore$disconnect() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().close();
        if (mc.level != null) mc.level.disconnect();
        mc.disconnect();
        mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();

        VirtualCoordinateHelper.calculateVirtualSize(this, vs);
        float scale = vs.uiScale;
        int vw = vs.virtualWidth;
        int vh = vs.virtualHeight;
        dreamingFishCore$updateCancelButtonPosition();

        LoadingScreenUi.renderBackground(guiGraphics, this.width, this.height);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);

        // 左上角提示
        LoadingScreenUi.renderTip(guiGraphics, this.font, tip);

        // 底部进度条（循环动画）
        int barMargin = 32;
        int barHeight = 6;
        int barX = barMargin;
        int barW = vw - barMargin * 2;
        int barY = vh - 28;

        long now = System.currentTimeMillis();
        int fakeProgress = (int) ((now % 6000) * 100 / 6000);

        String label = "正在连接到服务器... " + fakeProgress + "%";
        int labelW = this.font.width(label);
        guiGraphics.drawString(this.font, label, barX + barW - labelW, barY - 12, 0xFFFFFFFF, true);

        LoadingScreenUi.renderProgressBar(guiGraphics, barX, barY, barW, barHeight, fakeProgress,
                BAR_BG, ACCENT_GREEN, BAR_HIGHLIGHT);

        guiGraphics.pose().popPose();

        // 按钮在屏幕坐标渲染
        if (dreamingFishCore$cancelBtn != null) {
            dreamingFishCore$cancelBtn.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Unique
    private void dreamingFishCore$updateCancelButtonPosition() {
        if (dreamingFishCore$cancelBtn == null) {
            return;
        }
        dreamingFishCore$cancelBtn.setX((int) (8 * vs.uiScale));
        dreamingFishCore$cancelBtn.setY(this.height - (int) (50 * vs.uiScale));
    }

    @Unique
    private static class TextCancelButton extends Button {
        TextCancelButton(int x, int y, int w, int h, Component msg, OnPress onPress) {
            super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hov = isHovered();
            String text = getMessage().getString();
            int color = hov ? 0xFF88FFAA : 0xFFAAAAAA;
            g.drawString(Minecraft.getInstance().font, text, getX(), getY(), color, true);
            if (hov) {
                int tw = Minecraft.getInstance().font.width(text);
                g.fill(getX(), getY() + 10, getX() + tw, getY() + 11, 0xFF3FBF7F);
            }
        }
    }
}
