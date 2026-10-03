package com.hhy.dreamingfishcore.server.server_ui_system.client;

import com.hhy.dreamingfishcore.client.ui.notification.Notification;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationManager;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationPosition;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationQueuePolicy;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationRenderer;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationTheme;
import com.hhy.dreamingfishcore.client.ui.notification.SystemEvent;
import com.hhy.dreamingfishcore.server.rank_system.PlayerRankManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Compatibility entry point for the server information message area. */
public final class SystemMessageDisplay {
    private static final long MESSAGE_DURATION_MS = 8000L;

    private SystemMessageDisplay() {
    }

    public static void addMessage(Component text, int borderColor) {
        addMessage(text, borderColor, null);
    }

    public static void addMessage(Component text, int borderColor, SystemEvent event) {
        int accentColor = borderColor >= 0 ? borderColor : getPlayerRankBorderColor();
        NotificationManager.show(Notification.builder()
                .message(text)
                .event(event)
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

    public static List<NotificationManager.ActiveNotification> getActiveMessages() {
        return NotificationManager.getActive(NotificationPosition.TOP_RIGHT);
    }

    public static void renderSystemMessages(
            UiCanvas canvas, Font font, int rightEdge,
            int playerInfoBoxY, int playerInfoBoxHeight,
            List<NotificationManager.ActiveNotification> entries) {
        NotificationRenderer.renderTopRight(canvas, font, rightEdge,
                playerInfoBoxY, playerInfoBoxHeight, entries);
    }

    private static int getPlayerRankBorderColor() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0xFFFFFF : PlayerRankManager.getPlayerRankClient(mc.player).getRankColor();
    }
}
