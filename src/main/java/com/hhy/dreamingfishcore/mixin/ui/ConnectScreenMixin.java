package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.loading.LoadingSurface;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingTransitionController;
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

/** 连接服务器：统一加载画面，进度按时间估算，Esc 或点击右下角中断连接。 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {
    @Unique private static final String SERVER_STATUS = "正在搜寻梦屿信号";
    @Unique private final LoadingSurface dreamingFishCore$surface = new LoadingSurface();
    @Unique private Button dreamingFishCore$cancelBtn;
    @Unique private long dreamingFishCore$connectionStartedAt = -1L;

    protected ConnectScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void dreamingFishCore$init(CallbackInfo ci) {
        if (dreamingFishCore$connectionStartedAt < 0L) {
            dreamingFishCore$connectionStartedAt = System.currentTimeMillis();
        }
        for (var child : this.children()) {
            if (child instanceof Button button) {
                // 原版取消按钮保留逻辑但隐藏，由右下角的 Esc 操作代替
                dreamingFishCore$cancelBtn = button;
                button.visible = false;
                break;
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick,
                                         CallbackInfo ci) {
        ci.cancel();
        int progress = LoadingSurface.estimateProgress(dreamingFishCore$connectionStartedAt,
                System.currentTimeMillis(), 6, 90, 5_200L);
        dreamingFishCore$surface.status(SERVER_STATUS).progress(progress)
                .action("中断连接", this::dreamingFishCore$cancel)
                .render(guiGraphics, this.width, this.height);
        LoadingTransitionController.rememberFrame(dreamingFishCore$surface);
        LoadingTransitionController.renderLoadingEntry(guiGraphics, this.width, this.height);
    }

    @Unique
    private void dreamingFishCore$cancel() {
        if (dreamingFishCore$cancelBtn != null) {
            dreamingFishCore$cancelBtn.onPress();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return dreamingFishCore$surface.host().mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return dreamingFishCore$surface.host().mouseReleased(mouseX, mouseY, button);
    }

    /** Screen.keyPressed is inherited by ConnectScreen, so the mixin supplies the Esc action directly. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && dreamingFishCore$cancelBtn != null) {
            dreamingFishCore$cancel();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        dreamingFishCore$surface.host().close();
        super.removed();
    }
}
