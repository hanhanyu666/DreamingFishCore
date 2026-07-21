package com.hhy.dreamingfishcore.server.playerdata_system;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import com.hhy.dreamingfishcore.server.playerdata_system.event.LoginSync;
import com.hhy.dreamingfishcore.server.rank_system.Rank;
import com.hhy.dreamingfishcore.server.rank_system.RankRegistry;
import com.hhy.dreamingfishcore.server.title_system.Title;
import com.hhy.dreamingfishcore.server.title_system.TitleRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家基础数据管理器。数据在服务器启动时一次性加载，在世界生命周期内以内存缓存为准。
 */
@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerDataManager {
    private static final Map<UUID, PlayerData> PLAYER_DATA_CACHE = new ConcurrentHashMap<>();
    private static final Type PLAYER_DATA_TYPE = new TypeToken<Map<UUID, PlayerData>>() {}.getType();

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private static boolean dirty;
    private static boolean loaded;

    public static void loadWorldData(MinecraftServer server) {
        PLAYER_DATA_CACHE.clear();
        try {
            Map<UUID, PlayerData> loadedData = JsonDataStore.read(
                    WorldDataPaths.resolve(server, "playerdata", "player_data.json"),
                    GSON,
                    PLAYER_DATA_TYPE,
                    ConcurrentHashMap::new);
            PLAYER_DATA_CACHE.putAll(loadedData);
            dirty = repairStoredTitles(PLAYER_DATA_CACHE);
            if (dirty) {
                DreamingFishCore.LOGGER.info("已在内存中修复玩家数据的旧版称号，等待下次世界保存");
            }
            DreamingFishCore.LOGGER.info("玩家数据加载完成，共 {} 条", PLAYER_DATA_CACHE.size());
        } catch (Exception exception) {
            dirty = false;
            DreamingFishCore.LOGGER.error("读取世界玩家数据失败，本次会话不会覆盖损坏文件", exception);
        } finally {
            loaded = true;
        }
    }

    public static boolean hasPlayerData(ServerPlayer player) {
        ensureLoaded();
        return PLAYER_DATA_CACHE.containsKey(player.getUUID());
    }

    public static void initPlayerData(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        if (hasPlayerData(player)) {
            PlayerData existingData = getPlayerData(playerUUID);
            PlayerAttributesDataManager.initPlayerAttributesData(player, existingData.getLevel());
            return;
        }

        PlayerData newPlayerData = new PlayerData(player);
        PLAYER_DATA_CACHE.put(playerUUID, newPlayerData);
        markDirty();

        DreamingFishCore.LOGGER.info("新玩家 {} 数据初始化完成（默认Rank={}, Title={}, Level={}）",
                player.getScoreboardName(),
                newPlayerData.getRank().getRankName(),
                newPlayerData.getTitle().getTitleName(),
                newPlayerData.getLevel());

        PlayerAttributesDataManager.initPlayerAttributesData(player, newPlayerData.getLevel());
    }

    public static PlayerData getPlayerData(UUID playerUUID) {
        ensureLoaded();
        PlayerData playerData = PLAYER_DATA_CACHE.get(playerUUID);
        if (playerData == null) {
            DreamingFishCore.LOGGER.warn("玩家 {} 无数据，返回默认数据", playerUUID);
            return new PlayerData();
        }
        return playerData;
    }

    public static void updatePlayerData(ServerPlayer serverPlayer, Rank rank, Title title, int level, long experience) {
        ensureLoaded();
        UUID playerUUID = serverPlayer.getUUID();

        if (rank == null) {
            rank = getPlayerData(playerUUID).getRank();
            if (rank == null) {
                rank = RankRegistry.NO_RANK;
            }
        }
        if (title == null) {
            title = getPlayerData(playerUUID).getTitle();
            if (title == null) {
                title = TitleRegistry.getDefaultTitle();
            }
        }

        PlayerData playerData = PLAYER_DATA_CACHE.get(playerUUID);
        if (playerData == null) {
            playerData = new PlayerData(serverPlayer);
            DreamingFishCore.LOGGER.warn("玩家 {} 无原有数据，创建新数据并更新", serverPlayer.getScoreboardName());
        }
        playerData.setRank(rank);
        playerData.setTitle(title);
        playerData.setLevel(level);
        playerData.setCurrentExperience(experience);

        PLAYER_DATA_CACHE.put(playerUUID, playerData);
        markDirty();
        PlayerAttributesDataManager.initPlayerAttributesData(serverPlayer, level);

        DreamingFishCore.LOGGER.info("玩家 {} 数据更新成功（Rank={}, Title={}, Level={}, Exp={}）",
                serverPlayer.getScoreboardName(),
                rank.getRankName(),
                title.getTitleName(),
                level,
                experience);

        LoginSync.sendSyncPacketToPlayer(serverPlayer, serverPlayer);
        LoginSync.broadcastPlayerDataToAllOnlinePlayers(serverPlayer);
    }

    /**
     * 保留旧方法签名供网络层使用；数据现在来自内存缓存，不再读取文件。
     */
    public static Map<UUID, PlayerData> loadAllPlayerDataFromFile() {
        ensureLoaded();
        return new HashMap<>(PLAYER_DATA_CACHE);
    }

    private static boolean repairStoredTitles(Map<UUID, PlayerData> allPlayerData) {
        boolean repaired = false;
        for (PlayerData playerData : allPlayerData.values()) {
            if (playerData == null) {
                continue;
            }

            Title storedTitle = playerData.getTitle();
            int titleId = storedTitle == null ? 0 : storedTitle.getTitleID();
            Title configuredTitle = TitleRegistry.getTitleById(titleId);
            if (storedTitle != null
                    && (configuredTitle == null || configuredTitle.getTitleID() != titleId)) {
                continue;
            }
            if (configuredTitle == null) {
                configuredTitle = TitleRegistry.getDefaultTitle();
            }
            if (configuredTitle == null) {
                continue;
            }

            if (storedTitle == null
                    || storedTitle.getTitleID() != configuredTitle.getTitleID()
                    || storedTitle.getColor() != configuredTitle.getColor()
                    || !Objects.equals(storedTitle.getTitleName(), configuredTitle.getTitleName())) {
                playerData.setTitle(configuredTitle);
                repaired = true;
            }
        }
        return repaired;
    }

    public static void saveIfDirty(MinecraftServer server) {
        if (!loaded || !dirty) {
            return;
        }
        try {
            JsonDataStore.writeAtomic(
                    WorldDataPaths.resolve(server, "playerdata", "player_data.json"),
                    GSON,
                    PLAYER_DATA_CACHE);
            dirty = false;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("写入世界玩家数据失败，保留 dirty 状态等待下次保存", exception);
        }
    }

    public static void markDirty() {
        ensureLoaded();
        dirty = true;
    }

    public static void clearWorldCache() {
        PLAYER_DATA_CACHE.clear();
        dirty = false;
        loaded = false;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("玩家数据尚未随世界加载");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        initPlayerData(player);

        PlayerData data = getPlayerData(player.getUUID());
        data.setLastLoginTime(System.currentTimeMillis());
        markDirty();
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerData data = getPlayerData(player.getUUID());
            long onlineTime = System.currentTimeMillis() - data.getLastLoginTime();
            data.addPlayTime(onlineTime);
            markDirty();

            DreamingFishCore.LOGGER.info("玩家 {} 登出，本次在线: {}秒，总时长: {}秒",
                    player.getScoreboardName(),
                    onlineTime / 1000,
                    data.getTotalPlayTime() / 1000);
        }
    }
}
