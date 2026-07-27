package com.hhy.dreamingfishcore.server.notice_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家公告已读状态管理器。
 */
public class PlayerNoticeDataManager {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();
    private static final Type NOTICE_DATA_TYPE = new TypeToken<Map<UUID, Set<Integer>>>() {}.getType();
    private static final Map<UUID, Set<Integer>> READ_NOTICES_CACHE = new ConcurrentHashMap<>();

    private static boolean dirty;
    private static boolean loaded;

    public static void loadWorldData(MinecraftServer server) {
        READ_NOTICES_CACHE.clear();
        try {
            Map<UUID, Set<Integer>> loadedData = JsonDataStore.read(
                    WorldDataPaths.resolve(server, "notice", "player_read_state.json"),
                    GSON,
                    NOTICE_DATA_TYPE,
                    ConcurrentHashMap::new);
            loadedData.forEach((uuid, noticeIds) -> READ_NOTICES_CACHE.put(
                    uuid,
                    noticeIds == null ? ConcurrentHashMap.newKeySet() : concurrentSet(noticeIds)));
            dirty = false;
            DreamingFishCore.LOGGER.info("已加载 {} 个玩家的公告阅读记录", READ_NOTICES_CACHE.size());
        } catch (Exception exception) {
            dirty = false;
            DreamingFishCore.LOGGER.error("读取世界公告已读数据失败，本次会话不会覆盖损坏文件", exception);
        } finally {
            loaded = true;
        }
    }

    public static Set<Integer> getReadNoticeIds(UUID playerUUID) {
        ensureLoaded();
        return READ_NOTICES_CACHE.computeIfAbsent(playerUUID, ignored -> ConcurrentHashMap.newKeySet());
    }

    public static boolean hasReadNotice(UUID playerUUID, int noticeId) {
        return getReadNoticeIds(playerUUID).contains(noticeId);
    }

    public static void markAsRead(UUID playerUUID, int noticeId) {
        if (getReadNoticeIds(playerUUID).add(noticeId)) {
            dirty = true;
        }
    }

    public static void markMultipleAsRead(UUID playerUUID, Set<Integer> noticeIds) {
        if (noticeIds != null && getReadNoticeIds(playerUUID).addAll(noticeIds)) {
            dirty = true;
        }
    }

    public static boolean saveIfDirty(MinecraftServer server) {
        if (!loaded || !dirty) {
            return true;
        }
        try {
            JsonDataStore.writeAtomic(
                    WorldDataPaths.resolve(server, "notice", "player_read_state.json"),
                    GSON,
                    READ_NOTICES_CACHE);
            dirty = false;
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("写入世界公告已读数据失败，保留 dirty 状态等待下次保存", exception);
            return false;
        }
    }

    public static void clearWorldCache() {
        READ_NOTICES_CACHE.clear();
        dirty = false;
        loaded = false;
    }

    private static Set<Integer> concurrentSet(Set<Integer> values) {
        Set<Integer> result = ConcurrentHashMap.newKeySet();
        result.addAll(values);
        return result;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("公告已读数据尚未随世界加载");
        }
    }
}
