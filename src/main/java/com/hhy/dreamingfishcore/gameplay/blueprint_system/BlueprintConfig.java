package com.hhy.dreamingfishcore.gameplay.blueprint_system;

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
 * 合成蓝图的服务器配置。
 *
 * <p>文件位置：{@code config/dreamingfishcore/blueprint.json}。</p>
 *
 * <p><b>默认关闭</b>：{@code enabled} 为 {@code false} 时整套蓝图限制都不生效
 * （所有配方都能正常合成、也不会掉落蓝图），服主确认内容齐了再打开。</p>
 *
 * <p><b>拦不拦一件物品，只看两件事</b>：它是不是「免蓝图」（默认放行列表 /
 * 赦免命名空间），以及玩家有没有学过它的蓝图。白名单与黑名单**只决定什么能进抽取池**，
 * 所以被黑名单排除、又不在默认放行列表里的物品，玩家将永远无法通过工作台合成它
 * —— 这是刻意为之（这类物品应当有别的获取途径），加载时会点名提醒服主。</p>
 *
 * <p>与项目其他配置一致：文件缺失时写入默认值；文件损坏时只使用内存默认值，
 * 不覆盖服主原有的文件。</p>
 */
public final class BlueprintConfig {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** 默认关闭：这是会显著改变合成体验的系统，由服主显式开启。 */
    public static final boolean DEFAULT_ENABLED = false;

    /** 模组自定义丧尸（攻城丧尸）掉落蓝图的概率百分比。 */
    public static final double DEFAULT_SIEGE_ZOMBIE_DROP_PERCENT = 5.0D;

    /** 宝箱掉落蓝图的概率百分比，与改动前的战利品修饰器取值一致。 */
    public static final double DEFAULT_CHEST_DROP_PERCENT = 25.0D;

    /**
     * 默认放行的基础物品：开局就能造，不需要蓝图。
     *
     * <p>取材标准是「不靠它们就没法开始玩」：木板、木棍、火把、工作台、熔炉、篝火、箱子，
     * 以及木制与石制工具。其余物品（含熔炉以上的所有装备、建材与机器）都需要蓝图。</p>
     */
    public static final List<String> DEFAULT_UNLOCKED_ITEMS = List.of(
            "minecraft:*_planks",
            "minecraft:stick",
            "minecraft:torch",
            "minecraft:crafting_table",
            "minecraft:furnace",
            "minecraft:campfire",
            "minecraft:chest",
            "minecraft:wooden_pickaxe",
            "minecraft:wooden_axe",
            "minecraft:wooden_shovel",
            "minecraft:wooden_hoe",
            "minecraft:wooden_sword",
            "minecraft:stone_pickaxe",
            "minecraft:stone_axe",
            "minecraft:stone_shovel",
            "minecraft:stone_hoe",
            "minecraft:stone_sword",
            // 刷怪箱是创造模式物品，玩家平时拿不到；放进放行列表只是避免它变成「造不了也拿不到」的死物。
            "dreamingfishcore:spawner",
            // 研究桌是「获得蓝图」的工具本身，不能让造它的前提是自己已被研究出来（循环依赖）。
            "dreamingfishcore:research_table"
    );

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static volatile BlueprintConfig current = defaults();

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private boolean enabled = DEFAULT_ENABLED;
    private double siegeZombieDropPercent = DEFAULT_SIEGE_ZOMBIE_DROP_PERCENT;
    private double chestDropPercent = DEFAULT_CHEST_DROP_PERCENT;
    private List<String> defaultUnlockedItems = new ArrayList<>(DEFAULT_UNLOCKED_ITEMS);
    private List<String> exemptNamespaces = new ArrayList<>();
    private List<String> blueprintWhitelist = new ArrayList<>();
    private List<String> blueprintBlacklist = new ArrayList<>();

    public BlueprintConfig() {
    }

    public static synchronized BlueprintConfig init() {
        current = load();
        return current;
    }

    public static synchronized BlueprintConfig reload() {
        current = load();
        return current;
    }

    /** 运行期查询使用的快照；不会读取磁盘。 */
    public static BlueprintConfig current() {
        return current;
    }

    public static BlueprintConfig load() {
        return load(getConfigPath());
    }

    /** 供测试与工具使用的路径重载版本。 */
    public static synchronized BlueprintConfig load(Path path) {
        BlueprintConfig defaults = defaults();

        if (Files.notExists(path)) {
            try {
                JsonDataStore.writeAtomic(path, GSON, defaults);
            } catch (IOException exception) {
                DreamingFishCore.LOGGER.warn("蓝图配置默认文件创建失败：{}", path, exception);
            }
            return defaults;
        }

        try {
            if (Files.size(path) == 0L) {
                throw new IOException("配置文件为空");
            }

            BlueprintConfig loaded = JsonDataStore.read(
                    path,
                    GSON,
                    BlueprintConfig.class,
                    BlueprintConfig::new);
            if (loaded.validateAndNormalize()) {
                try {
                    JsonDataStore.writeAtomic(path, GSON, loaded);
                } catch (IOException exception) {
                    DreamingFishCore.LOGGER.warn("蓝图配置修正结果写回失败，将继续使用内存中的配置：{}", path, exception);
                }
            }
            return loaded;
        } catch (Exception exception) {
            // 损坏文件保持原样，避免把服主的内容覆盖成默认值。
            DreamingFishCore.LOGGER.warn("蓝图配置损坏，本次仅使用内存默认值：{}", path, exception);
            return defaults;
        }
    }

    public static Path getConfigPath() {
        return CommonInit.CONFIG_DIRECTORY.resolve("blueprint.json");
    }

    private static BlueprintConfig defaults() {
        return new BlueprintConfig();
    }

    /** 蓝图限制总开关。关闭时任何物品都不被拦截，也不会掉落蓝图。 */
    public boolean isEnabled() {
        return enabled;
    }

    public double getSiegeZombieDropPercent() {
        return enabled ? siegeZombieDropPercent : 0.0D;
    }

    public double getChestDropPercent() {
        return enabled ? chestDropPercent : 0.0D;
    }

    public List<String> getDefaultUnlockedItems() {
        return List.copyOf(defaultUnlockedItems);
    }

    public List<String> getExemptNamespaces() {
        return List.copyOf(exemptNamespaces);
    }

    public List<String> getBlueprintWhitelist() {
        return List.copyOf(blueprintWhitelist);
    }

    public List<String> getBlueprintBlacklist() {
        return List.copyOf(blueprintBlacklist);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    /**
     * 这件物品是否「免蓝图」（默认放行，或来自被赦免的模组命名空间）。
     *
     * <p>总开关关闭时一律返回 {@code true}。</p>
     */
    public boolean isExemptFromBlueprint(String itemId) {
        if (!enabled) {
            return true;
        }
        if (ItemIdPattern.matchesAny(itemId, defaultUnlockedItems)) {
            return true;
        }
        return isExemptNamespace(itemId);
    }

    /** 物品所属命名空间是否在赦免列表里。 */
    public boolean isExemptNamespace(String itemId) {
        if (itemId == null || exemptNamespaces.isEmpty()) {
            return false;
        }
        int separator = itemId.indexOf(':');
        String namespace = (separator < 0 ? itemId : itemId.substring(0, separator))
                .trim()
                .toLowerCase(Locale.ROOT);
        if (namespace.isEmpty()) {
            return false;
        }
        for (String exempt : exemptNamespaces) {
            if (exempt != null && exempt.trim().toLowerCase(Locale.ROOT).equals(namespace)) {
                return true;
            }
        }
        return false;
    }

    /** 物品是否允许出现在蓝图的随机抽取池里。 */
    public boolean isInBlueprintPool(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        if (isExemptFromBlueprint(itemId)) {
            return false;
        }
        if (ItemIdPattern.matchesAny(itemId, blueprintBlacklist)) {
            return false;
        }
        // 白名单为空 = 不额外限制（抽取池默认就是「所有需要蓝图的工作台配方」）。
        return blueprintWhitelist.isEmpty() || ItemIdPattern.matchesAny(itemId, blueprintWhitelist);
    }

    /**
     * 校验并就地修正配置。
     *
     * @return 是否发生了修正（需要写回文件）
     */
    private boolean validateAndNormalize() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的蓝图配置版本：" + schemaVersion);
        }

        boolean changed = false;
        double normalizedSiege = clampPercent(siegeZombieDropPercent);
        if (normalizedSiege != siegeZombieDropPercent) {
            siegeZombieDropPercent = normalizedSiege;
            changed = true;
        }
        double normalizedChest = clampPercent(chestDropPercent);
        if (normalizedChest != chestDropPercent) {
            chestDropPercent = normalizedChest;
            changed = true;
        }

        if (defaultUnlockedItems == null) {
            defaultUnlockedItems = new ArrayList<>(DEFAULT_UNLOCKED_ITEMS);
            changed = true;
        }
        if (exemptNamespaces == null) {
            exemptNamespaces = new ArrayList<>();
            changed = true;
        }
        if (blueprintWhitelist == null) {
            blueprintWhitelist = new ArrayList<>();
            changed = true;
        }
        if (blueprintBlacklist == null) {
            blueprintBlacklist = new ArrayList<>();
            changed = true;
        }

        changed |= stripBlanks(defaultUnlockedItems);
        changed |= stripBlanks(exemptNamespaces);
        changed |= stripBlanks(blueprintWhitelist);
        changed |= stripBlanks(blueprintBlacklist);
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

    /** 概率钳制在 {@code [0, 100]}；NaN 与无穷大回落到 0（手改坏的文件按「不掉落」处理更安全）。 */
    static double clampPercent(double percent) {
        if (Double.isNaN(percent) || Double.isInfinite(percent)) {
            return 0.0D;
        }
        if (percent < 0.0D) {
            return 0.0D;
        }
        return Math.min(percent, 100.0D);
    }
}
