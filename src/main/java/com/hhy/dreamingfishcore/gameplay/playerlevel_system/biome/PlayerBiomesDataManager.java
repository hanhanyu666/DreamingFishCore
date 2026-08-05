package com.hhy.dreamingfishcore.gameplay.playerlevel_system.biome;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家生物群系探索数据管理器。
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class PlayerBiomesDataManager {
    private static final Map<UUID, Set<String>> BIOMES_CACHE = new ConcurrentHashMap<>();
    private static final Type BIOMES_TYPE = new TypeToken<Map<UUID, Set<String>>>() {}.getType();

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private static boolean dirty;
    private static boolean loaded;

    public static void loadWorldData(MinecraftServer server) {
        BIOMES_CACHE.clear();
        try {
            Map<UUID, Set<String>> loadedData = JsonDataStore.read(
                    WorldDataPaths.resolve(server, "playerlevel", "player_biomes.json"),
                    GSON,
                    BIOMES_TYPE,
                    ConcurrentHashMap::new);
            loadedData.forEach((uuid, biomes) -> {
                Set<String> concurrentSet = ConcurrentHashMap.newKeySet();
                if (biomes != null) {
                    concurrentSet.addAll(biomes);
                }
                BIOMES_CACHE.put(uuid, concurrentSet);
            });
            dirty = false;
            DreamingFishCore.LOGGER.info("群系探索数据加载完成，共 {} 个玩家", BIOMES_CACHE.size());
        } catch (Exception exception) {
            dirty = false;
            DreamingFishCore.LOGGER.error("读取世界群系探索数据失败，本次会话不会覆盖损坏文件", exception);
        } finally {
            loaded = true;
        }
    }

    public static Set<String> getExploredBiomes(UUID playerUUID) {
        ensureLoaded();
        return BIOMES_CACHE.computeIfAbsent(playerUUID, ignored -> ConcurrentHashMap.newKeySet());
    }

    public static boolean addExploredBiome(UUID playerUUID, String biomeKey) {
        Set<String> exploredBiomes = getExploredBiomes(playerUUID);
        if (!exploredBiomes.add(biomeKey)) {
            return false;
        }
        dirty = true;
        return true;
    }

    public static boolean hasExploredBiome(UUID playerUUID, String biomeKey) {
        return getExploredBiomes(playerUUID).contains(biomeKey);
    }

    public static int getExploredBiomeCount(UUID playerUUID) {
        return getExploredBiomes(playerUUID).size();
    }

    public static boolean saveIfDirty(MinecraftServer server) {
        if (!loaded || !dirty) {
            return true;
        }
        try {
            JsonDataStore.writeAtomic(
                    WorldDataPaths.resolve(server, "playerlevel", "player_biomes.json"),
                    GSON,
                    BIOMES_CACHE);
            dirty = false;
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("写入世界群系探索数据失败，保留 dirty 状态等待下次保存", exception);
            return false;
        }
    }

    public static void clearWorldCache() {
        BIOMES_CACHE.clear();
        dirty = false;
        loaded = false;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("群系探索数据尚未随世界加载");
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Set<String> biomes = getExploredBiomes(player.getUUID());
            DreamingFishCore.LOGGER.info("玩家 {} 已探索 {} 个生物群系",
                    player.getScoreboardName(), biomes.size());
        }
    }
}
