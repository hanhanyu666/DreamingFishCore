package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.ui.loading.DisconnectReason;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 断线界面：沿用加载画面，左下状态换成“信号中断”的失败变体。 */
@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen {
    @Shadow @Final private DisconnectionDetails details;
    @Shadow @Final private Screen parent;

    @Unique private LoadingSurface dreamingFishCore$surface;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$init(CallbackInfo ci) {
        ci.cancel();
        if (dreamingFishCore$surface == null) {
            DisconnectReason reason = new DisconnectReason(details == null ? null : details.reason());
            dreamingFishCore$surface = new LoadingSurface()
                    .failure(reason.title(), reason.detail(), reason.state(), reason.accent())
                    .action(dreamingFishCore$returnLabel(), this::dreamingFishCore$returnFromDisconnect);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (dreamingFishCore$surface != null) {
            dreamingFishCore$surface.render(guiGraphics, width, height);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return dreamingFishCore$surface != null && dreamingFishCore$surface.host().mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return dreamingFishCore$surface != null && dreamingFishCore$surface.host().mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            dreamingFishCore$returnFromDisconnect();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        if (dreamingFishCore$surface != null) {
            dreamingFishCore$surface.host().close();
        }
        super.removed();
    }

    @Unique
    private void dreamingFishCore$returnFromDisconnect() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen target = parent == null || parent instanceof ConnectScreen
                ? new JoinMultiplayerScreen(new TitleScreen())
                : parent;
        // A local death-ban can show this screen while the integrated server is still saving.
        // Wait for Minecraft's normal shutdown path before the player can open another save;
        // otherwise NeoForge server configs from both instances can overlap and crash PermissionAPI.
        if (minecraft.hasSingleplayerServer()) {
            minecraft.disconnect();
        }
        minecraft.setScreen(target);
    }

    @Unique
    private String dreamingFishCore$returnLabel() {
        return parent instanceof TitleScreen ? "返回标题界面" : "返回服务器列表";
    }
}
