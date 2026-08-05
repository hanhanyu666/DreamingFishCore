package com.hhy.dreamingfishcore.server.login_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import net.neoforged.fml.loading.FMLPaths;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录数据管理器。
 *
 * <p>登录数据在服务器启动时完整加载一次。服务器运行期间以内存中的完整数据集为准，
 * 修改后立即尝试原子落盘；保存失败时保留 dirty 状态，由统一保存周期继续重试。</p>
 */
public final class PlayerLoginDataManager {
    private static final Path LOGIN_DATA_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(DreamingFishCore.MODID)
            .resolve("data")
            .resolve("login_data.json");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();
    private static final Type LOGIN_DATA_TYPE = new TypeToken<Map<UUID, PlayerLoginData>>() {}.getType();
    private static final Map<UUID, PlayerLoginData> LOGIN_DATA_CACHE = new ConcurrentHashMap<>();

    private static volatile boolean loaded;
    private static boolean dirty;

    private PlayerLoginDataManager() {
    }

    /**
     * 在服务器启动阶段加载完整登录数据集。
     */
    public static synchronized void loadServerData() {
        LOGIN_DATA_CACHE.clear();
        try {
            Map<UUID, PlayerLoginData> loadedData = JsonDataStore.read(
                    LOGIN_DATA_PATH,
                    GSON,
                    LOGIN_DATA_TYPE,
                    ConcurrentHashMap::new);
            loadedData.forEach((uuid, loginData) -> {
                if (uuid != null && loginData != null) {
                    LOGIN_DATA_CACHE.put(uuid, loginData);
                }
            });
            dirty = false;
            DreamingFishCore.LOGGER.info("已加载 {} 条登录数据", LOGIN_DATA_CACHE.size());
        } catch (Exception exception) {
            dirty = false;
            DreamingFishCore.LOGGER.error("读取登录数据失败，本次服务器会话不会覆盖损坏文件", exception);
        } finally {
            loaded = true;
        }
    }

    /**
     * 返回当前服务器会话中的完整数据快照。
     *
     * @return 所有玩家登录数据的独立快照
     * @deprecated 运行期间不应反复读取文件，请使用 {@link #getAllLoginData()}。
     */
    @Deprecated
    public static Map<UUID, PlayerLoginData> loadAllLoginDataFromFile() {
        return getAllLoginData();
    }

    /**
     * 用给定数据替换当前完整数据集并立即尝试保存。
     *
     * @param data 要保存的数据
     */
    public static synchronized void saveAllLoginDataToFile(Map<UUID, PlayerLoginData> data) {
        ensureLoaded();
        LOGIN_DATA_CACHE.clear();
        if (data != null) {
            data.forEach((uuid, loginData) -> {
                if (uuid != null && loginData != null) {
                    LOGIN_DATA_CACHE.put(uuid, loginData);
                }
            });
        }
        dirty = true;
        saveIfDirty();
    }

    /**
     * 检查玩家是否有登录数据。
     */
    public static boolean hasLoginData(UUID playerUUID) {
        ensureLoaded();
        return LOGIN_DATA_CACHE.containsKey(playerUUID);
    }

    /**
     * 获取玩家登录数据，不存在时返回 {@code null}。
     */
    public static PlayerLoginData getLoginData(UUID playerUUID) {
        ensureLoaded();
        return LOGIN_DATA_CACHE.get(playerUUID);
    }

    /**
     * 保存玩家登录数据。更新内存后立即尝试原子落盘。
     */
    public static synchronized void saveLoginData(UUID playerUUID, PlayerLoginData data) {
        ensureLoaded();
        if (playerUUID == null || data == null) {
            throw new IllegalArgumentException("玩家 UUID 和登录数据不能为空");
        }

        LOGIN_DATA_CACHE.put(playerUUID, data);
        dirty = true;
        saveIfDirty();
        DreamingFishCore.LOGGER.debug("已更新玩家 {} 的登录数据", playerUUID);
    }

    /**
     * 删除玩家登录数据。删除内存数据后立即尝试原子落盘。
     */
    public static synchronized void deleteLoginData(UUID playerUUID) {
        ensureLoaded();
        if (LOGIN_DATA_CACHE.remove(playerUUID) != null) {
            dirty = true;
            saveIfDirty();
            DreamingFishCore.LOGGER.info("已删除玩家 {} 的登录数据", playerUUID);
        }
    }

    /**
     * 获取所有登录数据的独立快照。
     */
    public static Map<UUID, PlayerLoginData> getAllLoginData() {
        ensureLoaded();
        return new ConcurrentHashMap<>(LOGIN_DATA_CACHE);
    }

    /**
     * 保存尚未落盘的修改。保存失败时 dirty 状态保持不变，等待下次重试。
     */
    public static synchronized boolean saveIfDirty() {
        if (!loaded || !dirty) {
            return true;
        }
        try {
            JsonDataStore.writeAtomic(LOGIN_DATA_PATH, GSON, LOGIN_DATA_CACHE);
            dirty = false;
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("写入登录数据失败，保留 dirty 状态等待下次保存", exception);
            return false;
        }
    }

    /**
     * 清理服务器级登录数据缓存，只应在服务器停止或显式重载时调用。
     */
    public static synchronized void clearServerCache() {
        LOGIN_DATA_CACHE.clear();
        dirty = false;
        loaded = false;
    }

    /**
     * 兼容旧调用：清空当前服务器的完整登录数据缓存。
     */
    public static void clearCache() {
        clearServerCache();
        DreamingFishCore.LOGGER.info("登录数据缓存已清空");
    }

    /**
     * 登录数据现在使用完整数据集缓存，玩家退出时无需逐条驱逐。
     */
    public static void clearPlayerCache(UUID playerUUID) {
        // 完整数据集是服务器运行期间的唯一数据源，移除单条记录会被误判为未注册。
    }

    /**
     * 从磁盘重新加载完整数据集。未保存的 dirty 修改会先尝试落盘。
     */
    public static synchronized void reloadCache() {
        saveIfDirty();
        if (dirty) {
            DreamingFishCore.LOGGER.error("登录数据仍有未保存修改，已取消重新加载以避免丢失内存数据");
            return;
        }
        loadServerData();
        DreamingFishCore.LOGGER.info("登录数据缓存已重新加载");
    }

    /**
     * 获取当前已加载的登录数据数量。
     */
    public static int getCacheSize() {
        ensureLoaded();
        return LOGIN_DATA_CACHE.size();
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("登录数据尚未随服务器加载");
        }
    }
}
