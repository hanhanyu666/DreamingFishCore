package com.hhy.dreamingfishcore.gameplay.playerattributes_system;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.strength.StrengthSyncManager;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家属性数据管理器。世界启动时加载一次，运行期间仅更新内存并标记 dirty。
 */
@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerAttributesDataManager {
    private static final Map<UUID, PlayerAttributesData> ATTRIBUTES_CACHE = new ConcurrentHashMap<>();
    private static final Type ATTRIBUTES_TYPE = new TypeToken<Map<UUID, PlayerAttributesData>>() {}.getType();

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private static boolean dirty;
    private static boolean loaded;

    public static void loadWorldData(MinecraftServer server) {
        ATTRIBUTES_CACHE.clear();
        try {
            Map<UUID, PlayerAttributesData> loadedData = JsonDataStore.read(
                    WorldDataPaths.resolve(server, "playerattributes", "player_attributes.json"),
                    GSON,
                    ATTRIBUTES_TYPE,
                    ConcurrentHashMap::new);
            ATTRIBUTES_CACHE.putAll(loadedData);
            dirty = false;
            DreamingFishCore.LOGGER.info("玩家属性数据加载完成，共 {} 条", ATTRIBUTES_CACHE.size());
        } catch (Exception exception) {
            dirty = false;
            DreamingFishCore.LOGGER.error("读取世界玩家属性数据失败，本次会话不会覆盖损坏文件", exception);
        } finally {
            loaded = true;
        }
    }

    public static boolean hasPlayerAttributesData(ServerPlayer player) {
        ensureLoaded();
        return ATTRIBUTES_CACHE.containsKey(player.getUUID());
    }

    public static void initPlayerAttributesData(ServerPlayer player, int realLevel) {
        UUID playerUUID = player.getUUID();
        if (hasPlayerAttributesData(player)) {
            PlayerAttributesData existingData = getPlayerAttributesData(playerUUID);
            existingData.setLevel(realLevel, player);
            existingData.setPlayerName(player.getScoreboardName());
            ATTRIBUTES_CACHE.put(playerUUID, existingData);
            markDirty();
            return;
        }

        PlayerAttributesData newAttributesData = new PlayerAttributesData(playerUUID, player.getScoreboardName(), realLevel);
        ATTRIBUTES_CACHE.put(playerUUID, newAttributesData);
        markDirty();
        StrengthSyncManager.syncStrengthToClient(player);
    }

    public static PlayerAttributesData getPlayerAttributesData(UUID playerUUID) {
        ensureLoaded();
        PlayerAttributesData attributesData = ATTRIBUTES_CACHE.get(playerUUID);
        if (attributesData == null) {
            DreamingFishCore.LOGGER.warn("玩家 {} 无属性数据，返回默认数据", playerUUID);
            return new PlayerAttributesData();
        }
        return attributesData;
    }

    public static void updatePlayerLevel(ServerPlayer player, int newLevel) {
        PlayerAttributesData attributesData = getPlayerAttributesData(player.getUUID());
        attributesData.setLevel(newLevel, player);
        attributesData.setPlayerName(player.getScoreboardName());
        ATTRIBUTES_CACHE.put(player.getUUID(), attributesData);
        markDirty();
        DreamingFishCore.LOGGER.info("玩家 {} 等级更新为{}，属性数据已标记保存", player.getScoreboardName(), newLevel);
    }

    public static void updatePlayerAttributesData(ServerPlayer player, PlayerAttributesData newData) {
        ATTRIBUTES_CACHE.put(player.getUUID(), newData);
        markDirty();
    }

    /**
     * 保留原有外部接口；现在只更新缓存并标脏，由世界保存流程统一落盘。
     */
    public static void saveSinglePlayerData(UUID playerUUID, PlayerAttributesData data) {
        ensureLoaded();
        ATTRIBUTES_CACHE.put(playerUUID, data);
        markDirty();
    }

    public static void saveIfDirty(MinecraftServer server) {
        if (!loaded || !dirty) {
            return;
        }
        try {
            JsonDataStore.writeAtomic(
                    WorldDataPaths.resolve(server, "playerattributes", "player_attributes.json"),
                    GSON,
                    ATTRIBUTES_CACHE);
            dirty = false;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("写入世界玩家属性数据失败，保留 dirty 状态等待下次保存", exception);
        }
    }

    public static void markDirty() {
        ensureLoaded();
        dirty = true;
    }

    public static void clearWorldCache() {
        ATTRIBUTES_CACHE.clear();
        dirty = false;
        loaded = false;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("玩家属性数据尚未随世界加载");
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            // 捕获通过可变 PlayerAttributesData 直接发生的修改。
            markDirty();
        }
    }
}
