package com.hhy.dreamingfishcore.gameplay.archive_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资料库登记表（世界存档数据）。
 *
 * <p>按 {@code 维度|X|Y|Z} 索引，每个资料库方块里存了哪些配方都在这里；<b>方块本身不带方块实体</b>。
 * 理由与刷怪箱、聚居地过滤装置一致：本模组所有需要持久化的方块状态都放服务端注册表——区块里的
 * 方块实体只能靠扫描已加载区块枚举，服务端做不了「一次遍历」；而且这样也白拿了原子写、
 * schemaVersion 校验与只读保护。</p>
 *
 * <p>读档失败时进入只读保护，绝不用空表覆盖存档；写盘是原子写。</p>
 */
public final class ArchiveRegistry {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String DATA_FILE = "libraries.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Map<String, LibraryEntry> LIBRARIES = new LinkedHashMap<>();

    private static boolean loaded;
    private static boolean dirty;
    private static boolean persistenceUnsafe;

    private ArchiveRegistry() {
    }

    /** 存档文档包装，带 schemaVersion 便于将来升级。 */
    private static final class Document {
        private int schemaVersion = CURRENT_SCHEMA_VERSION;
        private List<LibraryEntry> libraries = new ArrayList<>();
    }

    /** 一个资料库方块的内容。字段名即 JSON 键名。 */
    static final class LibraryEntry {
        String dimension = "";
        int x;
        int y;
        int z;
        List<String> recipes = new ArrayList<>();

        String key() {
            return keyOf(dimension, x, y, z);
        }

        boolean valid() {
            return dimension != null && !dimension.isEmpty();
        }

        /** 修复历史/手改数据里的坏条目；返回是否真的改过。 */
        boolean normalize() {
            List<String> clean = ArchiveRules.normalize(recipes);
            if (clean.equals(recipes)) {
                return false;
            }
            recipes = clean;
            return true;
        }
    }

    public static String keyOf(String dimension, int x, int y, int z) {
        return (dimension == null ? "" : dimension) + "|" + x + "|" + y + "|" + z;
    }

    public static String keyOf(String dimension, BlockPos pos) {
        return keyOf(dimension, pos.getX(), pos.getY(), pos.getZ());
    }

    // ==================== 读写 ====================

    /** 该资料库里已存的内容（不可变快照）。 */
    public static synchronized List<String> recipesAt(String dimension, BlockPos pos) {
        LibraryEntry entry = LIBRARIES.get(keyOf(dimension, pos));
        return entry == null ? List.of() : List.copyOf(entry.recipes);
    }

    public static synchronized int countAt(String dimension, BlockPos pos) {
        LibraryEntry entry = LIBRARIES.get(keyOf(dimension, pos));
        return entry == null ? 0 : entry.recipes.size();
    }

    /**
     * 把一批配方并入这个资料库。
     *
     * @return 真正新增的条数（已经存过的不会重复计）
     */
    public static synchronized int mergeRecipes(String dimension, BlockPos pos,
                                                Collection<String> incoming) {
        List<String> added = ArchiveRules.newIds(
                recipesAt(dimension, pos), incoming);
        if (added.isEmpty()) {
            return 0;
        }
        String key = keyOf(dimension, pos);
        LibraryEntry entry = LIBRARIES.get(key);
        if (entry == null) {
            entry = new LibraryEntry();
            entry.dimension = dimension;
            entry.x = pos.getX();
            entry.y = pos.getY();
            entry.z = pos.getZ();
            LIBRARIES.put(key, entry);
        }
        entry.recipes.addAll(added);
        markDirty();
        return added.size();
    }

    // ==================== 生命周期 ====================

    public static synchronized void loadWorldData(MinecraftServer server) {
        LIBRARIES.clear();
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
                enterReadOnlyProtection("资料库数据解析为空");
                return;
            }
            if (document.schemaVersion != CURRENT_SCHEMA_VERSION) {
                enterReadOnlyProtection("资料库数据版本不支持（文件 " + document.schemaVersion
                        + "，本版本 " + CURRENT_SCHEMA_VERSION + "）");
                return;
            }
            int repaired = 0;
            int skipped = 0;
            for (LibraryEntry entry : document.libraries == null
                    ? List.<LibraryEntry>of() : document.libraries) {
                if (entry == null || !entry.valid()) {
                    skipped++;
                    continue;
                }
                if (entry.normalize()) {
                    repaired++;
                    dirty = true;
                }
                LIBRARIES.put(entry.key(), entry);
            }
            loaded = true;
            DreamingFishCore.LOGGER.info("资料库加载完成：{} 座，修正 {} 条，跳过 {} 条",
                    LIBRARIES.size(), repaired, skipped);
        } catch (IOException | RuntimeException exception) {
            enterReadOnlyProtection("资料库数据加载失败", exception);
        }
    }

    public static synchronized boolean saveIfDirty(MinecraftServer server) {
        if (persistenceUnsafe) {
            if (dirty) {
                DreamingFishCore.LOGGER.error("资料库处于只读保护，拒绝写盘（避免用空表覆盖存档）");
            }
            return false;
        }
        if (!loaded || !dirty || server == null) {
            return true;
        }
        Document document = new Document();
        document.schemaVersion = CURRENT_SCHEMA_VERSION;
        document.libraries = new ArrayList<>(LIBRARIES.values());
        try {
            JsonDataStore.writeAtomic(WorldDataPaths.resolve(server, DATA_FILE), GSON, document);
            dirty = false;
            return true;
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("资料库数据写盘失败", exception);
            return false;
        }
    }

    public static synchronized void clearWorldCache() {
        LIBRARIES.clear();
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
}
