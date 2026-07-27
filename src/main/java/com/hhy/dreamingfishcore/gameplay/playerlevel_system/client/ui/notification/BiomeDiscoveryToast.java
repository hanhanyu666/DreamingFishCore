package com.hhy.dreamingfishcore.gameplay.playerlevel_system.client.ui.notification;

import com.hhy.dreamingfishcore.client.ui.notification.Notification;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationManager;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationPosition;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationQueuePolicy;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationTheme;
import net.minecraft.network.chat.Component;

public final class BiomeDiscoveryToast {
    private static final long DISPLAY_DURATION_MS = 4680L;

    private BiomeDiscoveryToast() {
    }

    public static void show(String biomeId, String biomeName, int totalExplored,
                            long experienceReward, boolean newlyDiscovered) {
        String displayName = biomeName == null || biomeName.isBlank() ? biomeId : biomeName;
        Component detail = newlyDiscovered
                ? Component.literal("首次发现  ·  + " + experienceReward + " 经验  ·  已探索 " + totalExplored)
                : Component.empty();
        NotificationManager.show(Notification.builder()
                .title(Component.literal(displayName == null ? "" : displayName))
                .message(detail)
                .position(NotificationPosition.CENTER_TOP)
                .theme(NotificationTheme.GOLD)
                .queuePolicy(NotificationQueuePolicy.REPLACE)
                .durationMs(DISPLAY_DURATION_MS)
                .build());
    }
}
