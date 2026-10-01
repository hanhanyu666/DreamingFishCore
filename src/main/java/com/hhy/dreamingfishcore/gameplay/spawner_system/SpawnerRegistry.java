package com.hhy.dreamingfishcore.gameplay.spawner_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 刷怪箱登记表（世界存档数据）。
 *
 * <p>按 {@code 维度|X|Y|Z} 索引，每台刷怪箱的配置与运行状态都在这里；方块本身不存配置，
 * 只保留外观这类纯展示的方块状态。理由与聚居地过滤装置一致：服务端要一次性遍历全部刷怪箱
 * 做周期判定，而区块里的方块实体只能靠扫描已加载区块枚举。</p>
 *
 * <p>读档失败时进入只读保护，绝不用空表覆盖存档；写盘也是原子写。</p>
 */
public final class SpawnerRegistry {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String DATA_FILE = "spawners.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Map<String, SpawnerEntry> SPAWNERS = new LinkedHashMap<>();

    private static boolean loaded;
    private static boolean dirty;
    private static boolean persistenceUnsafe;

    private SpawnerRegistry() {
    }

    /** 存档文档包装，带 schemaVersion 便于将来升级。 */
    private static final class Document {
        private int schemaVersion = CURRENT_SCHEMA_VERSION;
        private List<SpawnerEntry> spawners = new ArrayList<>();
    }

    public static String keyOf(String dimensionId, int x, int y, int z) {
        return (dimensionId == null ? "" : dimensionId) + "|" + x + "|" + y + "|" + z;
    }

    // ==================== 生命周期 ====================

    public static synchronized void loadWorldData(MinecraftServer server) {
        SPAWNERS.clear();
        loaded = false;
        dirty = false;
        persistenceUnsafe = false;
        if (server == null) {
            return;
        }
        try {
            Document document = JsonDataStore.read(
                    WorldDataPaths.resolve(server, DATA_FILE),
                    GSON,
                    Document.class,
                    Document::new);
            if (document == null) {
                enterReadOnlyProtection("刷怪箱数据解析为空");
                return;
            }
            if (document.schemaVersion != CURRENT_SCHEMA_VERSION) {
                enterReadOnlyProtection("刷怪箱数据版本不支持（文件 " + document.schemaVersion
                        + "，本版本 " + CURRENT_SCHEMA_VERSION + "）");
                return;
            }
            int repaired = 0;
            int skipped = 0;
            for (SpawnerEntry entry : document.spawners == null
                    ? List.<SpawnerEntry>of() : document.spawners) {
                if (entry == null || !entry.valid()) {
                    skipped++;
                    continue;
                }
                if (entry.normalize()) {
                    repaired++;
                    dirty = true;
                }
                SPAWNERS.put(entry.key(), entry);
            }
            loaded = true;
            DreamingFishCore.LOGGER.info("刷怪箱加载完成：{} 台，修正 {} 条，跳过 {} 条",
                    SPAWNERS.size(), repaired, skipped);
        } catch (IOException | RuntimeException exception) {
            enterReadOnlyProtection("刷怪箱数据加载失败", exception);
        }
    }

    public static synchronized boolean saveIfDirty(MinecraftServer server) {
        if (persistenceUnsafe) {
            if (dirty) {
                DreamingFishCore.LOGGER.error("刷怪箱处于只读保护，拒绝写盘（避免用空表覆盖存档）");
            }
            return false;
        }
        if (!loaded || !dirty || server == null) {
            return true;
        }
        Document document = new Document();
        document.schemaVersion = CURRENT_SCHEMA_VERSION;
        document.spawners = new ArrayList<>(SPAWNERS.values());
        try {
            JsonDataStore.writeAtomic(WorldDataPaths.resolve(server, DATA_FILE), GSON, document);
            dirty = false;
            return true;
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("刷怪箱数据写盘失败", exception);
            return false;
        }
    }

    public static synchronized void clearWorldCache() {
        SPAWNERS.clear();
        loaded = false;
        dirty = false;
        persistenceUnsafe = false;
    }

    public static synchronized boolean isLoaded() {
        return loaded;
    }

    private static void enterReadOnlyProtection(String reason) {
        enterReadOnlyProtection(reason, null);
    }

    private static void enterReadOnlyProtection(String reason, Throwable cause) {
        persistenceUnsafe = true;
        loaded = true;
        if (cause == null) {
            DreamingFishCore.LOGGER.error("{}，本次启动进入只读保护（不会覆盖存档，重启后自动重试）", reason);
        } else {
            DreamingFishCore.LOGGER.error("{}，本次启动进入只读保护（不会覆盖存档，重启后自动重试）", reason, cause);
        }
    }

    private static void markDirty() {
        dirty = true;
    }

    private static boolean writable() {
        return loaded && !persistenceUnsafe;
    }

    // ==================== 查询与变更 ====================

    public static synchronized Optional<SpawnerEntry> find(String dimensionId, int x, int y, int z) {
        return Optional.ofNullable(SPAWNERS.get(keyOf(dimensionId, x, y, z)));
    }

    public static synchronized List<SpawnerEntry> all() {
        return List.copyOf(SPAWNERS.values());
    }

    public static synchronized int count() {
        return SPAWNERS.size();
    }

    public static synchronized Map<String, SpawnerEntry> snapshot() {
        return Map.copyOf(SPAWNERS);
    }

    /** 放置时登记一台刷怪箱（已存在则保留原配置）。 */
    public static synchronized boolean register(String dimensionId, int x, int y, int z) {
        if (!writable()) {
            return false;
        }
        String key = keyOf(dimensionId, x, y, z);
        if (SPAWNERS.containsKey(key)) {
            return true;
        }
        SPAWNERS.put(key, new SpawnerEntry(dimensionId, x, y, z));
        markDirty();
        return true;
    }

    public static synchronized boolean unregister(String dimensionId, int x, int y, int z) {
        if (!writable()) {
            return false;
        }
        if (SPAWNERS.remove(keyOf(dimensionId, x, y, z)) != null) {
            markDirty();
            return true;
        }
        return false;
    }

    /** 取配置用于修改；调用方改完必须调 {@link #markChanged()} 让改动落盘。 */
    public static synchronized SpawnerEntry entryFor(String dimensionId, int x, int y, int z) {
        return SPAWNERS.get(keyOf(dimensionId, x, y, z));
    }

    /** 配置被外部改过之后标脏。 */
    public static synchronized void markChanged() {
        markDirty();
    }

    /** 清空所有刷怪箱（调试命令用）。 */
    public static synchronized int clearAll() {
        if (!writable()) {
            return 0;
        }
        int count = SPAWNERS.size();
        if (count > 0) {
            SPAWNERS.clear();
            markDirty();
        }
        return count;
    }
}
