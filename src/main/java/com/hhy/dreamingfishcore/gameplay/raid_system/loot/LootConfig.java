package com.hhy.dreamingfishcore.gameplay.raid_system.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * 战利品配置：物品价值表与区域模板。
 *
 * <p>设计稿 §10 的核心是"不要只给物品设一个价值"，这里落地三个字段：</p>
 * <ul>
 *   <li>{@code spawnCost} —— 生成成本，用来消耗区域预算（相对稳定）；</li>
 *   <li>{@code combatScore} —— 战斗强度，用来限制一局顶级装备数量、和"值多少钱"分开；</li>
 *   <li>{@code rarityWeight} —— 抽取**权重**（不是百分比）。</li>
 * </ul>
 *
 * <p><b>刻意不定义 sell_value</b>：出售价格归 EconomySystem 唯一真源，两边都定义必然打架
 * （这也是设计稿 §10.2 自己强调的"生成成本 ≠ 商店出售价格"）。</p>
 *
 * <p>解析一律软失败：单个条目坏了只跳过它并记问题，不让整张表加载失败。</p>
 */
public final class LootConfig {

    private LootConfig() {
    }

    /** 稀有度档位，只用于展示与排序；真正的概率由 {@code rarityWeight} 决定。 */
    public enum Rarity {
        COMMON, UNCOMMON, RARE, EPIC, LEGENDARY;

        public static Rarity parse(String raw, Rarity fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return fallback;
            }
        }
    }

    /** 解析结果：要么得到数据，要么得到一组可读问题。 */
    public record ParseResult<T>(T value, List<String> problems) {

        public boolean ok() {
            return value != null;
        }

        public static <T> ParseResult<T> failure(List<String> problems) {
            return new ParseResult<>(null, List.copyOf(problems));
        }

        public static <T> ParseResult<T> success(T value, List<String> problems) {
            return new ParseResult<>(value, List.copyOf(problems));
        }
    }

    /**
     * 一个物品的价值配置。
     *
     * @param itemId                物品 id
     * @param category              类别（电子、医疗、机械…），供区域允许列表与必出规则使用
     * @param rarity                稀有度档（展示/排序用）
     * @param spawnCost             生成成本（≥1）
     * @param combatScore           战斗强度（≥0）
     * @param rarityWeight          抽取权重（≥1；0 或负数表示不参与普通抽取）
     * @param allowedTiers          允许生成的区域等级；空表示不限
     * @param allowedContainerTypes 允许的容器类型；空表示不限
     * @param allowedZoneTags       允许的区域标签；空表示不限
     * @param raidGlobalLimit       单局全局上限（0 表示不限）
     */
    public record Item(String itemId,
                       String category,
                       Rarity rarity,
                       int spawnCost,
                       int combatScore,
                       int rarityWeight,
                       Set<Integer> allowedTiers,
                       Set<String> allowedContainerTypes,
                       Set<String> allowedZoneTags,
                       int raidGlobalLimit) {

        public Item {
            category = category == null ? "" : category;
            rarity = rarity == null ? Rarity.COMMON : rarity;
            allowedTiers = allowedTiers == null ? Set.of() : Set.copyOf(new TreeSet<>(allowedTiers));
            allowedContainerTypes = ordered(allowedContainerTypes);
            allowedZoneTags = ordered(allowedZoneTags);
        }

        /**
         * 本物品是否允许出现在这个区域/容器里。
         *
         * <p>空集合一律表示"不限"，所以服主少写一个字段不会让物品变成永远抽不到。</p>
         */
        public boolean allowedIn(Integer tier, Set<String> zoneTags, String containerType) {
            if (!allowedTiers.isEmpty() && (tier == null || !allowedTiers.contains(tier))) {
                return false;
            }
            if (!allowedContainerTypes.isEmpty()) {
                if (containerType == null || !allowedContainerTypes.contains(containerType)) {
                    return false;
                }
            }
            if (!allowedZoneTags.isEmpty()) {
                if (zoneTags == null || zoneTags.stream().noneMatch(allowedZoneTags::contains)) {
                    return false;
                }
            }
            return true;
        }

        public boolean hasGlobalLimit() {
            return raidGlobalLimit > 0;
        }

        public static ParseResult<Item> fromJson(JsonObject json) {
            List<String> problems = new ArrayList<>();
            if (json == null) {
                return ParseResult.failure(List.of("物品条目不是 JSON 对象"));
            }
            String itemId = string(json, "item");
            if (itemId == null || itemId.isBlank()) {
                return ParseResult.failure(List.of("缺少 item"));
            }
            int spawnCost = number(json, "spawn_cost", 0, problems);
            if (spawnCost < 1) {
                return ParseResult.failure(List.of("spawn_cost 必须 ≥1（否则会无限填充预算）"));
            }
            Item item = new Item(itemId.trim().toLowerCase(Locale.ROOT),
                    string(json, "category"),
                    Rarity.parse(string(json, "rarity"), Rarity.COMMON),
                    spawnCost,
                    Math.max(0, number(json, "combat_score", 0, problems)),
                    Math.max(0, number(json, "rarity_weight", 1, problems)),
                    intSet(json, "allowed_tiers", problems),
                    stringSet(json, "allowed_container_types", problems),
                    stringSet(json, "allowed_zone_tags", problems),
                    Math.max(0, number(json, "raid_global_limit", 0, problems)));
            return ParseResult.success(item, problems);
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("item", itemId);
            if (!category.isEmpty()) {
                json.addProperty("category", category);
            }
            json.addProperty("rarity", rarity.name());
            json.addProperty("spawn_cost", spawnCost);
            if (combatScore > 0) {
                json.addProperty("combat_score", combatScore);
            }
            json.addProperty("rarity_weight", rarityWeight);
            if (!allowedTiers.isEmpty()) {
                JsonArray array = new JsonArray();
                allowedTiers.forEach(array::add);
                json.add("allowed_tiers", array);
            }
            if (!allowedContainerTypes.isEmpty()) {
                json.add("allowed_container_types", toArray(allowedContainerTypes));
            }
            if (!allowedZoneTags.isEmpty()) {
                json.add("allowed_zone_tags", toArray(allowedZoneTags));
            }
            if (raidGlobalLimit > 0) {
                json.addProperty("raid_global_limit", raidGlobalLimit);
            }
            return json;
        }
    }

    /** 闭区间（含两端），用于"每局激活数量""区域预算"这类范围。 */
    public record IntRange(int min, int max) {

        public IntRange {
            if (max < min) {
                int swap = min;
                min = max;
                max = swap;
            }
        }

        public int pick(com.hhy.dreamingfishcore.gameplay.raid_system.RaidRandom random) {
            return random.nextInt(min, max);
        }

        public int clamp(int value) {
            return Math.max(min, Math.min(max, value));
        }

        public static IntRange of(int min, int max) {
            return new IntRange(min, max);
        }
    }

    /** 必出规则：某类别至少/最多出几件（设计稿 §11.2 第一步）。 */
    public record Guarantee(String category, int minCount, int maxCount) {

        public Guarantee {
            minCount = Math.max(0, minCount);
            maxCount = Math.max(minCount, maxCount);
        }

        public static Guarantee fromJson(JsonObject json) {
            String category = string(json, "category");
            if (category == null || category.isBlank()) {
                return null;
            }
            return new Guarantee(category, number(json, "minimum_count", 0, new ArrayList<>()),
                    number(json, "maximum_count", 0, new ArrayList<>()));
        }
    }

    /** 稀有物品规则：本局以多大把握出几件（设计稿 §12.2）。 */
    public record RareRule(String itemId, double chance, int count) {

        public RareRule {
            chance = Math.max(0.0D, Math.min(1.0D, chance));
            count = Math.max(1, count);
        }

        public static RareRule fromJson(JsonObject json) {
            String itemId = string(json, "item");
            if (itemId == null || itemId.isBlank()) {
                return null;
            }
            double chance = 1.0D;
            JsonElement chanceJson = json.get("chance");
            if (chanceJson != null && chanceJson.isJsonPrimitive() && chanceJson.getAsJsonPrimitive().isNumber()) {
                chance = chanceJson.getAsDouble();
            }
            int count = number(json, "count", 1, new ArrayList<>());
            return new RareRule(itemId.trim().toLowerCase(Locale.ROOT), chance, count);
        }
    }

    /**
     * 区域模板（设计稿 §7.3、§15.2）。
     *
     * <p>资源点数量与区域预算**是两个独立概念**：前者决定玩家能搜多少位置，后者决定总价值。
     * 所以这里刻意不把它们合成一个字段。</p>
     *
     * @param zoneId           任务地点 id（复用现有区域体系）
     * @param tier             区域等级
     * @param containerCount   每局激活的容器点数量范围
     * @param looseLootCount   每局激活的露天物品点数量范围
     * @param budget           区域总预算范围（用 spawn_cost 计价）
     * @param allowedCategories 允许的类别；空表示不限
     * @param guarantees       必出规则
     * @param rareRules        稀有物品规则
     * @param zoneTags         区域标签，供物品的 allowed_zone_tags 匹配
     */
    public record Zone(String zoneId,
                       int tier,
                       IntRange containerCount,
                       IntRange looseLootCount,
                       IntRange budget,
                       Set<String> allowedCategories,
                       List<Guarantee> guarantees,
                       List<RareRule> rareRules,
                       Set<String> zoneTags) {

        public Zone {
            allowedCategories = ordered(allowedCategories);
            guarantees = guarantees == null ? List.of() : List.copyOf(guarantees);
            rareRules = rareRules == null ? List.of() : List.copyOf(rareRules);
            zoneTags = ordered(zoneTags);
        }

        public static ParseResult<Zone> fromJson(JsonObject json) {
            List<String> problems = new ArrayList<>();
            if (json == null) {
                return ParseResult.failure(List.of("区域条目不是 JSON 对象"));
            }
            String zoneId = string(json, "zone");
            if (zoneId == null || zoneId.isBlank()) {
                zoneId = string(json, "id");
            }
            if (zoneId == null || zoneId.isBlank()) {
                return ParseResult.failure(List.of("缺少 zone（任务地点 id）"));
            }

            IntRange budget = readRange(json, "loot_budget", problems, "loot_budget");
            if (budget == null) {
                return ParseResult.failure(List.of("缺少 loot_budget（区域总预算，形如 {minimum, maximum}）"));
            }

            List<Guarantee> guarantees = new ArrayList<>();
            JsonElement guaranteesJson = json.get("guarantees");
            if (guaranteesJson != null && guaranteesJson.isJsonArray()) {
                for (JsonElement element : guaranteesJson.getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        problems.add("guarantees 里有非对象项，已忽略");
                        continue;
                    }
                    Guarantee guarantee = Guarantee.fromJson(element.getAsJsonObject());
                    if (guarantee == null) {
                        problems.add("guarantees 里有缺少 category 的条目，已忽略");
                    } else {
                        guarantees.add(guarantee);
                    }
                }
            }

            List<RareRule> rareRules = new ArrayList<>();
            JsonElement rareJson = json.get("rare_items");
            if (rareJson != null && rareJson.isJsonArray()) {
                for (JsonElement element : rareJson.getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        problems.add("rare_items 里有非对象项，已忽略");
                        continue;
                    }
                    RareRule rule = RareRule.fromJson(element.getAsJsonObject());
                    if (rule == null) {
                        problems.add("rare_items 里有缺少 item 的条目，已忽略");
                    } else {
                        rareRules.add(rule);
                    }
                }
            }

            IntRange containerRange = readRange(json, "active_containers", problems, "active_containers");
            IntRange looseRange = readRange(json, "active_loose_loot", problems, "active_loose_loot");
            Zone zone = new Zone(zoneId.trim(),
                    number(json, "tier", 1, problems),
                    containerRange == null ? IntRange.of(0, 0) : containerRange,
                    looseRange == null ? IntRange.of(0, 0) : looseRange,
                    budget,
                    stringSet(json, "allowed_categories", problems),
                    guarantees,
                    rareRules,
                    stringSet(json, "zone_tags", problems));
            return ParseResult.success(zone, problems);
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("zone", zoneId);
            json.addProperty("tier", tier);
            json.add("active_containers", rangeToJson(containerCount));
            json.add("active_loose_loot", rangeToJson(looseLootCount));
            json.add("loot_budget", rangeToJson(budget));
            if (!allowedCategories.isEmpty()) {
                json.add("allowed_categories", toArray(allowedCategories));
            }
            if (!zoneTags.isEmpty()) {
                json.add("zone_tags", toArray(zoneTags));
            }
            return json;
        }
    }

    // ---------------------------------------------------------------- 工具

    private static JsonObject rangeToJson(IntRange range) {
        JsonObject json = new JsonObject();
        json.addProperty("minimum", range.min());
        json.addProperty("maximum", range.max());
        return json;
    }

    private static JsonArray toArray(Set<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static Set<String> ordered(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new TreeSet<>();
        values.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT)).forEach(result::add);
        return java.util.Collections.unmodifiableSet(result);
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        return element.getAsString();
    }

    private static int number(JsonObject json, String key, int fallback, List<String> problems) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            problems.add(key + " 不是数字，已用默认值 " + fallback);
            return fallback;
        }
        return element.getAsInt();
    }

    private static Set<Integer> intSet(JsonObject json, String key, List<String> problems) {
        Set<Integer> values = new TreeSet<>();
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (!element.isJsonArray()) {
            problems.add(key + " 必须是数组，已忽略");
            return values;
        }
        for (JsonElement item : element.getAsJsonArray()) {
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isNumber()) {
                values.add(item.getAsInt());
            } else {
                problems.add(key + " 里有非数字项，已忽略");
            }
        }
        return java.util.Collections.unmodifiableSet(values);
    }

    private static Set<String> stringSet(JsonObject json, String key, List<String> problems) {
        Set<String> values = new LinkedHashSet<>();
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return Set.of();
        }
        if (!element.isJsonArray()) {
            problems.add(key + " 必须是数组，已忽略");
            return Set.of();
        }
        for (JsonElement item : element.getAsJsonArray()) {
            if (item.isJsonPrimitive()) {
                String value = item.getAsString().trim().toLowerCase(Locale.ROOT);
                if (!value.isEmpty()) {
                    values.add(value);
                }
            } else {
                problems.add(key + " 里有非字符串项，已忽略");
            }
        }
        return Set.copyOf(values);
    }

    private static IntRange readRange(JsonObject json, String key, List<String> problems, String label) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonObject()) {
            problems.add(label + " 必须是 {minimum, maximum} 对象，已忽略");
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement minJson = object.get("minimum");
        JsonElement maxJson = object.get("maximum");
        if (minJson == null || maxJson == null
                || !minJson.isJsonPrimitive() || !maxJson.isJsonPrimitive()) {
            problems.add(label + " 缺少 minimum 或 maximum，已忽略");
            return null;
        }
        int min = minJson.getAsInt();
        int max = maxJson.getAsInt();
        if (min < 0 || max < 0) {
            problems.add(label + " 不应为负数，已忽略");
            return null;
        }
        return new IntRange(min, max);
    }
}
