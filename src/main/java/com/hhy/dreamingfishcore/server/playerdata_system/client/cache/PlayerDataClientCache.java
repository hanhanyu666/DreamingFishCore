package com.hhy.dreamingfishcore.server.playerdata_system.client.cache;

import com.hhy.dreamingfishcore.server.playerdata_system.PlayerData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端玩家资料缓存，只保存服务端同步下来的只读快照。
 */
@OnlyIn(Dist.CLIENT)
public final class PlayerDataClientCache {
    private static final Map<UUID, PlayerData> PLAYER_DATA = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> EXPLORED_BIOMES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> UNLOCKED_RECIPES = new ConcurrentHashMap<>();

    private PlayerDataClientCache() {
    }

    public static PlayerData get(UUID uuid) {
        return PLAYER_DATA.get(uuid);
    }

    public static PlayerData getOrCreate(UUID uuid) {
        return PLAYER_DATA.computeIfAbsent(uuid, ignored -> new PlayerData());
    }

    public static void put(UUID uuid, PlayerData data) {
        if (uuid != null && data != null) {
            PLAYER_DATA.put(uuid, data);
        }
    }

    public static int getExploredBiomesCount(UUID uuid) {
        return EXPLORED_BIOMES.getOrDefault(uuid, 0);
    }

    public static void setExploredBiomesCount(UUID uuid, int count) {
        EXPLORED_BIOMES.put(uuid, count);
    }

    public static int getUnlockedRecipesCount(UUID uuid) {
        return UNLOCKED_RECIPES.getOrDefault(uuid, 0);
    }

    public static void setUnlockedRecipesCount(UUID uuid, int count) {
        UNLOCKED_RECIPES.put(uuid, count);
    }

    public static void remove(UUID uuid) {
        PLAYER_DATA.remove(uuid);
        EXPLORED_BIOMES.remove(uuid);
        UNLOCKED_RECIPES.remove(uuid);
    }

    public static void clear() {
        PLAYER_DATA.clear();
        EXPLORED_BIOMES.clear();
        UNLOCKED_RECIPES.clear();
    }
}
