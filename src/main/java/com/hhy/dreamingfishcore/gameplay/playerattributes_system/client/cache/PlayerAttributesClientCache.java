package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.cache;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端玩家属性缓存，归属属性系统而不是全局客户端缓存。
 */
@OnlyIn(Dist.CLIENT)
public final class PlayerAttributesClientCache {
    private static final Map<UUID, PlayerAttributesData> ATTRIBUTES = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> RESPAWN_POINTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> INFECTED = new ConcurrentHashMap<>();

    private PlayerAttributesClientCache() {
    }

    public static PlayerAttributesData get(UUID uuid) {
        return ATTRIBUTES.get(uuid);
    }

    public static PlayerAttributesData getOrCreate(UUID uuid) {
        return ATTRIBUTES.computeIfAbsent(uuid, ignored -> new PlayerAttributesData());
    }

    public static void put(UUID uuid, PlayerAttributesData data) {
        if (uuid != null && data != null) {
            ATTRIBUTES.put(uuid, data);
        }
    }

    public static float getRespawnPoint(UUID uuid) {
        return RESPAWN_POINTS.getOrDefault(uuid, 100.0F);
    }

    public static void setRespawnPoint(UUID uuid, float respawnPoint) {
        RESPAWN_POINTS.put(uuid, respawnPoint);
    }

    public static boolean isInfected(UUID uuid) {
        return INFECTED.getOrDefault(uuid, false);
    }

    public static void setInfected(UUID uuid, boolean infected) {
        INFECTED.put(uuid, infected);
    }

    public static float getNormalRespawnCost(UUID uuid) {
        return isInfected(uuid) ? 20.0F : 5.0F;
    }

    public static float getKeepInventoryCost(UUID uuid) {
        return getNormalRespawnCost(uuid) + 30.0F;
    }

    public static int getRespawnTimes(UUID uuid) {
        float cost = getNormalRespawnCost(uuid);
        return cost > 0.0F ? (int) (getRespawnPoint(uuid) / cost) : 0;
    }

    public static void remove(UUID uuid) {
        ATTRIBUTES.remove(uuid);
        RESPAWN_POINTS.remove(uuid);
        INFECTED.remove(uuid);
    }

    public static void clear() {
        ATTRIBUTES.clear();
        RESPAWN_POINTS.clear();
        INFECTED.clear();
    }
}
