package com.hhy.dreamingfishcore.server.notice_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Type;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final Map<UUID, Set<Integer>> DELIVERED_NOTICES_CACHE = new ConcurrentHashMap<>();

    private static boolean readDirty;
    private static boolean deliveryDirty;
    private static boolean readWritesEnabled = true;
    private static boolean deliveryWritesEnabled = true;
    private static boolean loaded;

    public static synchronized void loadWorldData(MinecraftServer server) {
        READ_NOTICES_CACHE.clear();
        DELIVERED_NOTICES_CACHE.clear();
        readDirty = false;
        deliveryDirty = false;
        readWritesEnabled = true;
        deliveryWritesEnabled = true;

        Path readPath = WorldDataPaths.resolve(server, "notice", "player_read_state.json");
        try {
            PlayerNoticeStatePersistence.ReadResult readState =
                    PlayerNoticeStatePersistence.readWithWriteProtection(
                            readPath, GSON, NOTICE_DATA_TYPE);
            READ_NOTICES_CACHE.putAll(readState.values());
            readWritesEnabled = readState.writesEnabled();
            DreamingFishCore.LOGGER.info("已加载 {} 个玩家的公告阅读记录", READ_NOTICES_CACHE.size());
        } catch (Exception exception) {
            readWritesEnabled = false;
            DreamingFishCore.LOGGER.error("读取世界公告已读数据失败，本次会话不会覆盖损坏文件", exception);
        }

        Path deliveryPath = WorldDataPaths.resolve(server, "notice", "player_delivery_state.json");
        try {
            PlayerNoticeStatePersistence.LoadResult deliveryState =
                    PlayerNoticeStatePersistence.readOrMigrate(
                            deliveryPath, GSON, NOTICE_DATA_TYPE, READ_NOTICES_CACHE);
            DELIVERED_NOTICES_CACHE.putAll(deliveryState.values());
            deliveryDirty = deliveryState.dirty();
            deliveryWritesEnabled = deliveryState.writesEnabled();
            if (deliveryState.migrated()) {
                DreamingFishCore.LOGGER.info(
                        "公告投递记录文件不存在，已从已读记录迁移 {} 个玩家的记录；等待保存",
                        DELIVERED_NOTICES_CACHE.size());
            } else {
                DreamingFishCore.LOGGER.info(
                        "已加载 {} 个玩家的公告投递记录", DELIVERED_NOTICES_CACHE.size());
            }
        } catch (Exception exception) {
            // JsonDataStore marks only this path as write-blocked when its
            // primary and backup are both unreadable.  Keep the read state
            // usable and never turn this empty cache into a replacement file.
            deliveryWritesEnabled = false;
            deliveryDirty = false;
            DreamingFishCore.LOGGER.error("读取世界公告投递数据失败，本次会话不会覆盖损坏文件", exception);
        }
        loaded = true;
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
            readDirty = true;
        }
    }

    public static void markMultipleAsRead(UUID playerUUID, Set<Integer> noticeIds) {
        if (noticeIds != null && getReadNoticeIds(playerUUID).addAll(noticeIds)) {
            readDirty = true;
        }
    }

    /**
     * Returns the IDs whose top-left delivery has already been sent to this player.
     * Delivery is deliberately independent from opening an announcement.
     */
    public static Set<Integer> getDeliveredNoticeIds(UUID playerUUID) {
        ensureLoaded();
        return DELIVERED_NOTICES_CACHE.computeIfAbsent(
                playerUUID, ignored -> ConcurrentHashMap.newKeySet());
    }

    public static boolean hasDeliveredNotice(UUID playerUUID, int noticeId) {
        return getDeliveredNoticeIds(playerUUID).contains(noticeId);
    }

    public static void markAsDelivered(UUID playerUUID, int noticeId) {
        if (getDeliveredNoticeIds(playerUUID).add(noticeId)) {
            deliveryDirty = true;
        }
    }

    public static void markMultipleAsDelivered(UUID playerUUID, Set<Integer> noticeIds) {
        if (noticeIds != null && getDeliveredNoticeIds(playerUUID).addAll(noticeIds)) {
            deliveryDirty = true;
        }
    }

    public static synchronized boolean saveIfDirty(MinecraftServer server) {
        if (!loaded) {
            return true;
        }

        boolean success = true;
        if (readDirty) {
            if (!readWritesEnabled) {
                success = false;
                DreamingFishCore.LOGGER.error("公告已读数据文件已被写保护，保留 readDirty 状态等待人工处理");
            } else {
                try {
                    JsonDataStore.writeAtomic(
                            WorldDataPaths.resolve(server, "notice", "player_read_state.json"),
                            GSON,
                            READ_NOTICES_CACHE);
                    readDirty = false;
                } catch (Exception exception) {
                    success = false;
                    DreamingFishCore.LOGGER.error("写入世界公告已读数据失败，保留 readDirty 状态等待下次保存", exception);
                }
            }
        }
        if (deliveryDirty) {
            if (!deliveryWritesEnabled) {
                success = false;
                DreamingFishCore.LOGGER.error("公告投递数据文件已被写保护，保留 deliveryDirty 状态等待人工处理");
            } else {
                try {
                    JsonDataStore.writeAtomic(
                            WorldDataPaths.resolve(server, "notice", "player_delivery_state.json"),
                            GSON,
                            DELIVERED_NOTICES_CACHE);
                    deliveryDirty = false;
                } catch (Exception exception) {
                    success = false;
                    DreamingFishCore.LOGGER.error("写入世界公告投递数据失败，保留 deliveryDirty 状态等待下次保存", exception);
                }
            }
        }
        return success;
    }

    public static synchronized void clearWorldCache() {
        READ_NOTICES_CACHE.clear();
        DELIVERED_NOTICES_CACHE.clear();
        readDirty = false;
        deliveryDirty = false;
        readWritesEnabled = true;
        deliveryWritesEnabled = true;
        loaded = false;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("公告已读数据尚未随世界加载");
        }
    }
}

/**
 * Pure-Java persistence rules shared by the manager and its unit tests.
 * Package-private on purpose: Minecraft lifecycle code should remain the only
 * public entry point for world-scoped state.
 */
final class PlayerNoticeStatePersistence {
    private PlayerNoticeStatePersistence() {
    }

    static Map<UUID, Set<Integer>> read(Path path, Gson gson, Type type) throws IOException {
        return copyState(JsonDataStore.read(path, gson, type, ConcurrentHashMap::new));
    }

    static ReadResult readWithWriteProtection(Path path, Gson gson, Type type) throws IOException {
        if (Files.exists(path) && Files.size(path) == 0L) {
            return new ReadResult(new ConcurrentHashMap<>(), false);
        }
        return new ReadResult(read(path, gson, type), true);
    }

    static LoadResult readOrMigrate(Path deliveryPath, Gson gson, Type type,
                                    Map<UUID, Set<Integer>> readState) throws IOException {
        if (Files.exists(deliveryPath) && Files.size(deliveryPath) == 0L) {
            return new LoadResult(new ConcurrentHashMap<>(), false, false, false);
        }
        if (Files.notExists(deliveryPath)) {
            Map<UUID, Set<Integer>> migrated = copyState(readState);
            boolean hasRecords = containsAnyNoticeId(migrated);
            return new LoadResult(migrated, hasRecords, hasRecords, true);
        }
        return new LoadResult(read(deliveryPath, gson, type), false, false, true);
    }

    private static Map<UUID, Set<Integer>> copyState(Map<UUID, Set<Integer>> source) {
        Map<UUID, Set<Integer>> result = new ConcurrentHashMap<>();
        if (source == null) {
            return result;
        }
        source.forEach((uuid, noticeIds) -> {
            if (uuid != null) {
                Set<Integer> copied = ConcurrentHashMap.newKeySet();
                if (noticeIds != null) {
                    copied.addAll(noticeIds);
                }
                result.put(uuid, copied);
            }
        });
        return result;
    }

    private static boolean containsAnyNoticeId(Map<UUID, Set<Integer>> state) {
        return state.values().stream().anyMatch(ids -> ids != null && !ids.isEmpty());
    }

    record ReadResult(Map<UUID, Set<Integer>> values, boolean writesEnabled) {
    }

    record LoadResult(Map<UUID, Set<Integer>> values, boolean dirty, boolean migrated,
                      boolean writesEnabled) {
    }
}
