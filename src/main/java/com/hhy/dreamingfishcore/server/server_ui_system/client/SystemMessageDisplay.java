package com.hhy.dreamingfishcore.server.server_ui_system.client;

import com.hhy.dreamingfishcore.client.ui.notification.Notification;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationManager;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationPosition;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationQueuePolicy;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationRenderer;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationTheme;
import com.hhy.dreamingfishcore.server.rank_system.PlayerRankManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Compatibility entry point for the server information message area. */
public final class SystemMessageDisplay {
    private static final long MESSAGE_DURATION_MS = 8000L;

    private SystemMessageDisplay() {
    }

    public static void addMessage(Component text, int borderColor) {
        int accentColor = borderColor >= 0 ? borderColor : getPlayerRankBorderColor();
        NotificationManager.show(Notification.builder()
                .message(text)
                .position(NotificationPosition.TOP_RIGHT)
                .theme(NotificationTheme.SYSTEM)
                .queuePolicy(NotificationQueuePolicy.STACK)
                .accentColor(accentColor)
                .durationMs(MESSAGE_DURATION_MS)
                .build());
    }

    public static void addMessage(Component text) {
        addMessage(text, -1);
    }

    public static void clearMessages() {
        NotificationManager.clear(NotificationPosition.TOP_RIGHT);
    }

    public static void renderSystemMessages(GuiGraphics guiGraphics, Font font, int screenWidth,
                                            int playerInfoBoxY, int playerInfoBoxHeight) {
        NotificationRenderer.renderTopRight(
                guiGraphics, font, screenWidth, playerInfoBoxY, playerInfoBoxHeight);
    }

    private static int getPlayerRankBorderColor() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0xFFFFFF : PlayerRankManager.getPlayerRankClient(mc.player).getRankColor();
    }
}
