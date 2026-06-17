package com.hhy.dreamingfishcore.mixin.ui;

import com.hhy.dreamingfishcore.client.util.ModernSelectionScreenUi;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mixin(targets = "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$OnlineServerEntry")
public abstract class OnlineServerEntryMixin {

    @Unique
    private static final ExecutorService DREAMINGFISHCORE_SERVER_PING_EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "DreamingFishCore Server Pinger");
        thread.setDaemon(true);
        return thread;
    });

    @Shadow
    @Final
    private JoinMultiplayerScreen screen;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ServerData serverData;

    @Shadow
    @Final
    private FaviconTexture icon;

    @Shadow
    @Nullable
    private byte[] lastIconBytes;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dreamingFishCore$renderModernOnlineServerEntry(GuiGraphics guiGraphics, int index, int top, int left,
                                                               int width, int height, int mouseX, int mouseY,
                                                               boolean hovering, float partialTick, CallbackInfo ci) {
        if (!ModernSelectionScreenUi.isModernSelectionScreen()) {
            return;
        }

        ci.cancel();
        dreamingFishCore$ensureServerStatus();
        dreamingFishCore$updateServerIconTexture();
        ResourceLocation texture = this.icon.textureLocation();
        ModernSelectionScreenUi.renderOnlineServerEntry(guiGraphics, this.serverData, texture, index, top, left, width, height, hovering);
    }

    @Unique
    private void dreamingFishCore$ensureServerStatus() {
        if (this.serverData.state() != ServerData.State.INITIAL) {
            return;
        }

        this.serverData.setState(ServerData.State.PINGING);
        this.serverData.motd = CommonComponents.EMPTY;
        this.serverData.status = CommonComponents.EMPTY;
        DREAMINGFISHCORE_SERVER_PING_EXECUTOR.submit(() -> {
            try {
                this.screen.getPinger().pingServer(
                        this.serverData,
                        () -> this.minecraft.execute(this::dreamingFishCore$updateServerList),
                        () -> this.serverData.setState(
                                this.serverData.protocol == SharedConstants.getCurrentVersion().getProtocolVersion()
                                        ? ServerData.State.SUCCESSFUL
                                        : ServerData.State.INCOMPATIBLE
                        )
                );
            } catch (UnknownHostException unknownHostException) {
                this.serverData.setState(ServerData.State.UNREACHABLE);
                this.serverData.motd = Component.translatable("multiplayer.status.cannot_resolve").withColor(-65536);
            } catch (Exception exception) {
                this.serverData.setState(ServerData.State.UNREACHABLE);
                this.serverData.motd = Component.translatable("multiplayer.status.cannot_connect").withColor(-65536);
            }
        });
    }

    @Unique
    private void dreamingFishCore$updateServerIconTexture() {
        byte[] iconBytes = this.serverData.getIconBytes();
        if (Arrays.equals(iconBytes, this.lastIconBytes)) {
            return;
        }

        if (dreamingFishCore$uploadServerIcon(iconBytes)) {
            this.lastIconBytes = iconBytes;
            return;
        }

        this.serverData.setIconBytes(null);
        dreamingFishCore$updateServerList();
    }

    @Unique
    private boolean dreamingFishCore$uploadServerIcon(@Nullable byte[] iconBytes) {
        if (iconBytes == null) {
            this.icon.clear();
            return true;
        }

        try {
            this.icon.upload(NativeImage.read(iconBytes));
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    @Unique
    private void dreamingFishCore$updateServerList() {
        this.screen.getServers().save();
    }
}
