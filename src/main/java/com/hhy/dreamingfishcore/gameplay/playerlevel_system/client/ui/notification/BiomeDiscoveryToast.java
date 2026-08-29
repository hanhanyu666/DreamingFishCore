package com.hhy.dreamingfishcore.gameplay.playerlevel_system.client.ui.notification;

import com.hhy.dreamingfishcore.client.ui.notification.Notification;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationManager;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationPosition;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationQueuePolicy;
import com.hhy.dreamingfishcore.client.ui.notification.NotificationTheme;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public final class BiomeDiscoveryToast {
    private static final long DISPLAY_DURATION_MS = 4680L;

    private BiomeDiscoveryToast() {
    }

    public static void show(String biomeId, String biomeName, int totalExplored,
                            long experienceReward, boolean newlyDiscovered) {
        Component displayName = resolveBiomeName(biomeId, biomeName);
        Component detail = newlyDiscovered
                ? Component.literal("首次发现  ·  + " + experienceReward + " 经验  ·  已探索 " + totalExplored)
                : Component.empty();
        NotificationManager.show(Notification.builder()
                .title(displayName)
                .message(detail)
                .position(NotificationPosition.CENTER_TOP)
                .theme(NotificationTheme.GOLD)
                .queuePolicy(NotificationQueuePolicy.REPLACE)
                .durationMs(DISPLAY_DURATION_MS)
                .build());
    }

    private static Component resolveBiomeName(String biomeId, String fallbackName) {
        String translationKey = biomeTranslationKey(biomeId);
        if (translationKey != null && I18n.exists(translationKey)) {
            return Component.translatable(translationKey);
        }

        String fallback = fallbackName == null || fallbackName.isBlank() ? biomeId : fallbackName;
        return Component.literal(fallback == null ? "" : fallback);
    }

    @Nullable
    static String biomeTranslationKey(String biomeId) {
        ResourceLocation id = biomeId == null ? null : ResourceLocation.tryParse(biomeId);
        return id == null ? null : id.toLanguageKey("biome");
    }
}
