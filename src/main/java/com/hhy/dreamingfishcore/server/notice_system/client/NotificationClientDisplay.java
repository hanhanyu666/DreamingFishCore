package com.hhy.dreamingfishcore.server.notice_system.client;

import com.hhy.dreamingfishcore.client.ui.notification.Notification;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationManager;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationPosition;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationQueuePolicy;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationTheme;
import net.minecraft.network.chat.Component;

/** 客户端通知数据包的显示适配器。 */
public final class NotificationClientDisplay {
    private static final String WORLD_BOUNDARY_KEYWORD = "世界边界";

    private NotificationClientDisplay() {
    }

    public static void showTopLeft(String message, int displayDuration) {
        showNotification("", message, displayDuration, (byte) 0);
    }

    public static void showCenterTop(String title, String message, int displayDuration) {
        showNotification(title, message, displayDuration, (byte) 1);
    }

    public static void showNotification(
            String title, String message, int displayDuration, byte position) {
        if (position == 1) {
            NotificationManager.show(Notification.builder()
                    .title(Component.literal(title == null ? "" : title))
                    .message(Component.literal(message == null ? "" : message))
                    .position(NotificationPosition.CENTER_TOP)
                    .theme(NotificationTheme.SYSTEM)
                    .queuePolicy(NotificationQueuePolicy.REPLACE)
                    .durationMs(displayDuration)
                    .build());
            return;
        }

        String text = message == null ? "" : message;
        NotificationTheme theme = text.contains(WORLD_BOUNDARY_KEYWORD)
                ? NotificationTheme.WARNING
                : NotificationTheme.DEFAULT;
        NotificationManager.show(Notification.builder()
                .message(Component.literal(text))
                .position(NotificationPosition.TOP_LEFT)
                .theme(theme)
                .queuePolicy(NotificationQueuePolicy.STACK)
                .durationMs(displayDuration)
                .build());
    }
}
