package com.hhy.dreamingfishcore.gameplay.research_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.init.CommonInit;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 研究桌的服务器配置。
 *
 * <p>文件位置：{@code config/dreamingfishcore/research_table.json}。</p>
 *
 * <p>研究桌是**蓝图的另一条获取途径**：花经验一次性学会一批配方，省掉靠掉落慢慢攒。
 * 能被研究出来的物品就是「需要蓝图、且落在下面命名空间白名单里、并且玩家还没学过」的那批
 * —— 白名单为空表示不限命名空间。</p>
 *
 * <p>与项目其他配置一致：文件缺失时写入默认值；文件损坏时只使用内存默认值，
 * 不覆盖服主原有的文件。</p>
 */
public final class ResearchTableConfig {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static final boolean DEFAULT_ENABLED = true;
    /** 一次研究消耗的经验点数（原版经验点，不是等级）。 */
    public static final int DEFAULT_COST_EXPERIENCE_POINTS = 100;
    public static final int DEFAULT_MIN_RECIPES = 10;
    public static final int DEFAULT_MAX_RECIPES = 15;
    /** 默认只研究原版配方；服主可以加模组命名空间，或清空表示不限。 */
    public static final List<String> DEFAULT_NAMESPACES = List.of("minecraft");
    public static final boolean DEFAULT_SKIP_LEARNED = true;

    /** 一次研究最多给多少个：防止配置写成 99999 把整本配方书一次倒给玩家。 */
    static final int MAX_RECIPES_LIMIT = 64;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static volatile ResearchTableConfig current = defaults();

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private boolean enabled = DEFAULT_ENABLED;
    private int costExperiencePoints = DEFAULT_COST_EXPERIENCE_POINTS;
    private int minRecipes = DEFAULT_MIN_RECIPES;
    private int maxRecipes = DEFAULT_MAX_RECIPES;
    private List<String> namespaces = new ArrayList<>(DEFAULT_NAMESPACES);
    private boolean skipLearned = DEFAULT_SKIP_LEARNED;

    public ResearchTableConfig() {
    }

    public static synchronized ResearchTableConfig init() {
        current = load();
        return current;
    }

    public static synchronized ResearchTableConfig reload() {
        current = load();
        return current;
    }

    /** 运行期查询使用的快照；不会读取磁盘。 */
    public static ResearchTableConfig current() {
        return current;
    }

    public static ResearchTableConfig load() {
        return load(getConfigPath());
    }

    /** 供测试与工具使用的路径重载版本。 */
    public static synchronized ResearchTableConfig load(Path path) {
        ResearchTableConfig defaults = defaults();

        if (Files.notExists(path)) {
            try {
                JsonDataStore.writeAtomic(path, GSON, defaults);
            } catch (IOException exception) {
                DreamingFishCore.LOGGER.warn("研究桌配置默认文件创建失败：{}", path, exception);
            }
            return defaults;
        }

        try {
            if (Files.size(path) == 0L) {
                throw new IOException("配置文件为空");
            }

            ResearchTableConfig loaded = JsonDataStore.read(
                    path,
                    GSON,
                    ResearchTableConfig.class,
                    ResearchTableConfig::new);
            if (loaded.validateAndNormalize()) {
                try {
                    JsonDataStore.writeAtomic(path, GSON, loaded);
                } catch (IOException exception) {
                    DreamingFishCore.LOGGER.warn("研究桌配置修正结果写回失败，将继续使用内存中的配置：{}", path, exception);
                }
            }
            return loaded;
        } catch (Exception exception) {
            // 损坏文件保持原样，避免把服主的内容覆盖成默认值。
            DreamingFishCore.LOGGER.warn("研究桌配置损坏，本次仅使用内存默认值：{}", path, exception);
            return defaults;
        }
    }

    public static Path getConfigPath() {
        return CommonInit.CONFIG_DIRECTORY.resolve("research_table.json");
    }

    private static ResearchTableConfig defaults() {
        return new ResearchTableConfig();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getCostExperiencePoints() {
        return costExperiencePoints;
    }

    /** 一次研究给出的最少数量（已保证 ≤ {@link #getMaxRecipes()}）。 */
    public int getMinRecipes() {
        return minRecipes;
    }

    public int getMaxRecipes() {
        return maxRecipes;
    }

    /** 允许被研究出来的命名空间；为空表示不限制。 */
    public List<String> getNamespaces() {
        return List.copyOf(namespaces);
    }

    public boolean isSkipLearned() {
        return skipLearned;
    }

    /** 该物品所属命名空间是否允许被研究。命名空间列表为空时不限制。 */
    public boolean allowsNamespace(String itemId) {
        if (namespaces.isEmpty()) {
            return true;
        }
        if (itemId == null) {
            return false;
        }
        int separator = itemId.indexOf(':');
        String namespace = (separator < 0 ? itemId : itemId.substring(0, separator))
                .trim()
                .toLowerCase(Locale.ROOT);
        if (namespace.isEmpty()) {
            return false;
        }
        for (String allowed : namespaces) {
            if (allowed != null && allowed.trim().toLowerCase(Locale.ROOT).equals(namespace)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验并就地修正配置。
     *
     * @return 是否发生了修正（需要写回文件）
     */
    private boolean validateAndNormalize() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的研究桌配置版本：" + schemaVersion);
        }

        boolean changed = false;
        if (costExperiencePoints < 0) {
            costExperiencePoints = 0;
            changed = true;
        }
        if (maxRecipes > MAX_RECIPES_LIMIT) {
            maxRecipes = MAX_RECIPES_LIMIT;
            changed = true;
        }
        if (maxRecipes < 1) {
            maxRecipes = 1;
            changed = true;
        }
        if (minRecipes < 1) {
            minRecipes = 1;
            changed = true;
        }
        if (minRecipes > maxRecipes) {
            minRecipes = maxRecipes;
            changed = true;
        }
        if (namespaces == null) {
            namespaces = new ArrayList<>(DEFAULT_NAMESPACES);
            changed = true;
        }
        changed |= stripBlanks(namespaces);
        return changed;
    }

    /** 去掉空白项与首尾空格；返回是否改动了列表。 */
    private static boolean stripBlanks(List<String> list) {
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            String value = list.get(i);
            if (value == null || value.isBlank()) {
                list.remove(i);
                changed = true;
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.equals(value)) {
                list.set(i, trimmed);
                changed = true;
            }
        }
        return changed;
    }
}
