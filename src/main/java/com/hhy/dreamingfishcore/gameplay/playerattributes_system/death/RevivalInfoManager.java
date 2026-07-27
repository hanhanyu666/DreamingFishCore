package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保存等待玩家下次登录时显示的复活信息。
 */
public class RevivalInfoManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type REVIVAL_INFO_TYPE = new TypeToken<Map<UUID, RevivalInfo>>() {}.getType();
    private static final Map<UUID, RevivalInfo> REVIVAL_INFO_CACHE = new ConcurrentHashMap<>();

    private static boolean dirty;
    private static boolean loaded;

    public static class RevivalInfo {
        private final String reviverName;
        private final boolean reviverIsInfected;

        public RevivalInfo(String reviverName, boolean reviverIsInfected) {
            this.reviverName = reviverName;
            this.reviverIsInfected = reviverIsInfected;
        }

        public String getReviverName() {
            return reviverName;
        }

        public boolean isReviverInfected() {
            return reviverIsInfected;
        }
    }

    public static void loadWorldData(MinecraftServer server) {
        REVIVAL_INFO_CACHE.clear();
        try {
            Map<UUID, RevivalInfo> loadedData = JsonDataStore.read(
                    WorldDataPaths.resolve(server, "death", "revival_info.json"),
                    GSON,
                    REVIVAL_INFO_TYPE,
                    ConcurrentHashMap::new);
            REVIVAL_INFO_CACHE.putAll(loadedData);
            dirty = false;
            DreamingFishCore.LOGGER.info("复活信息加载完成，共 {} 条", REVIVAL_INFO_CACHE.size());
        } catch (Exception exception) {
            dirty = false;
            DreamingFishCore.LOGGER.error("读取世界复活信息失败，本次会话不会覆盖损坏文件", exception);
        } finally {
            loaded = true;
        }
    }

    public static void setRevivalInfo(UUID playerUUID, String reviverName, boolean reviverIsInfected) {
        ensureLoaded();
        REVIVAL_INFO_CACHE.put(playerUUID, new RevivalInfo(reviverName, reviverIsInfected));
        dirty = true;
        DreamingFishCore.LOGGER.info("记录复活信息: 玩家 {} 被 {} ({}) 复活",
                playerUUID, reviverName, reviverIsInfected ? "感染者" : "幸存者");
    }

    public static RevivalInfo getRevivalInfo(UUID playerUUID) {
        ensureLoaded();
        return REVIVAL_INFO_CACHE.get(playerUUID);
    }

    public static void removeRevivalInfo(UUID playerUUID) {
        ensureLoaded();
        if (REVIVAL_INFO_CACHE.remove(playerUUID) != null) {
            dirty = true;
            DreamingFishCore.LOGGER.info("清除复活信息: 玩家 {}", playerUUID);
        }
    }

    public static boolean hasRevivalInfo(UUID playerUUID) {
        ensureLoaded();
        return REVIVAL_INFO_CACHE.containsKey(playerUUID);
    }

    public static boolean saveIfDirty(MinecraftServer server) {
        if (!loaded || !dirty) {
            return true;
        }
        try {
            JsonDataStore.writeAtomic(
                    WorldDataPaths.resolve(server, "death", "revival_info.json"),
                    GSON,
                    REVIVAL_INFO_CACHE);
            dirty = false;
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("写入世界复活信息失败，保留 dirty 状态等待下次保存", exception);
            return false;
        }
    }

    public static void clearAll() {
        ensureLoaded();
        if (!REVIVAL_INFO_CACHE.isEmpty()) {
            REVIVAL_INFO_CACHE.clear();
            dirty = true;
        }
    }

    public static void clearWorldCache() {
        REVIVAL_INFO_CACHE.clear();
        dirty = false;
        loaded = false;
    }

    public static void checkAndSendRevivalTip(ServerPlayer player) {
        RevivalInfo info = getRevivalInfo(player.getUUID());
        if (info == null) {
            return;
        }

        String reviverIdentity = info.isReviverInfected() ? "§c感染者" : "§a幸存者";
        String message = String.format("§d§l✦ 复活通知 ✦\n§f%s §e牺牲了自己一半的重生点数复活了您\n§7您现在的身份为：%s",
                info.getReviverName(), reviverIdentity);
        NotificationPushHelper.sendTopLeftNotification(player, message, 15000);
        removeRevivalInfo(player.getUUID());
        DreamingFishCore.LOGGER.info("已向玩家 {} 发送复活提示", player.getScoreboardName());
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("复活信息尚未随世界加载");
        }
    }
}
