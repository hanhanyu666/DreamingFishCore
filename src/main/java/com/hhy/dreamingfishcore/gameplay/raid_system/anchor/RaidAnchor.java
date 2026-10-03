package com.hhy.dreamingfishcore.gameplay.raid_system.anchor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 一个候选锚点（搜打撤设计稿 §3.2）。
 *
 * <p>坐标用 double 直接存，不落成整数：露天物品要贴在桌面上，取整会偏一格。
 * 需要方块坐标时用 {@link #blockX()} 等方法（向下取整）。</p>
 *
 * <p>本类刻意不引用任何 Minecraft 类型，这样解析、合并、校验都能在普通单测里跑，
 * 不需要启动游戏注册表；与游戏侧（任务地点、世界存档）的对接放在适配层。</p>
 *
 * @param id                全局唯一 id（同一张图内不得重复）
 * @param type              锚点类型
 * @param zone              所属任务地点 id（复用现有任务地点体系）
 * @param x                 世界坐标 X
 * @param y                 世界坐标 Y
 * @param z                 世界坐标 Z
 * @param rotation          朝向（度），无朝向时用 {@link Rotation#NONE}
 * @param group             子区域 / 点位分组，用于"每个子区域至少一个、最多若干"的规则
 * @param tags              分类标签，供预算与允许列表筛选
 * @param weight            被激活的基础权重（≥1）
 * @param enabled           是否参与生成
 * @param qualityMultiplier 质量倍率（设计稿 §11.1，>0）
 * @param source            该锚点来自定义层还是世界层覆盖
 */
public record RaidAnchor(String id,
                         RaidAnchorType type,
                         String zone,
                         double x,
                         double y,
                         double z,
                         Rotation rotation,
                         String group,
                         List<String> tags,
                         int weight,
                         boolean enabled,
                         double qualityMultiplier,
                         Source source) {

    /** 锚点来源：定义层随地图分发，世界层是服主在游戏内的微调（世界层优先）。 */
    public enum Source {
        DEFINITION,
        OVERLAY
    }

    /** 朝向（度）。只用 yaw 的场合也一并存 x/z，避免以后再加字段破坏存档兼容。 */
    public record Rotation(float x, float y, float z) {
        public static final Rotation NONE = new Rotation(0.0F, 0.0F, 0.0F);

        public JsonArray toJson() {
            JsonArray array = new JsonArray();
            array.add(x);
            array.add(y);
            array.add(z);
            return array;
        }
    }

    /** id 允许的字符：小写字母、数字、下划线、点、连字符（便于按地图/区域前缀命名）。 */
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.\\-]{1,96}");

    public static final int DEFAULT_WEIGHT = 100;
    public static final double DEFAULT_QUALITY_MULTIPLIER = 1.0D;

    public RaidAnchor {
        tags = tags == null ? List.of() : List.copyOf(tags);
        rotation = rotation == null ? Rotation.NONE : rotation;
        group = group == null ? "" : group;
    }

    /** 解析结果：要么得到锚点，要么得到一组可读问题（软失败：只跳过这一个锚点）。 */
    public record ParseResult(RaidAnchor anchor, List<String> problems) {

        public boolean ok() {
            return anchor != null;
        }

        public static ParseResult failure(List<String> problems) {
            return new ParseResult(null, List.copyOf(problems));
        }

        public static ParseResult success(RaidAnchor anchor) {
            return new ParseResult(anchor, List.of());
        }
    }

    /**
     * 从 JSON 解析一个锚点。
     *
     * <p>把"能读到的都读出来"和"必需的字段是否合法"分开：可选字段不合法记一条问题并落默认值，
     * 必需字段不合法才整体失败（返回 {@code anchor == null}）。这样服主改坏一个可选字段时，
     * 这个锚点仍然可用，只是会看到一条提示。</p>
     */
    public static ParseResult fromJson(JsonObject json, Source source) {
        List<String> problems = new ArrayList<>();
        if (json == null) {
            return ParseResult.failure(List.of("锚点条目不是 JSON 对象"));
        }

        String id = readString(json, "id");
        if (id == null || id.isBlank()) {
            return ParseResult.failure(List.of("缺少 id"));
        }
        id = id.trim().toLowerCase(Locale.ROOT);
        if (!ID_PATTERN.matcher(id).matches()) {
            return ParseResult.failure(List.of("id 不合法（只允许小写字母、数字、下划线、点、连字符）: " + id));
        }

        RaidAnchorType type = null;
        String rawType = readString(json, "type");
        if (rawType == null || rawType.isBlank()) {
            return ParseResult.failure(List.of("缺少 type"));
        }
        var parsedType = RaidAnchorType.parse(rawType);
        if (parsedType.isEmpty()) {
            return ParseResult.failure(List.of("type 不是合法类型: " + rawType));
        }
        type = parsedType.get();

        String zone = readString(json, "zone");
        if (zone == null || zone.isBlank()) {
            return ParseResult.failure(List.of("缺少 zone（必须引用一个已存在的任务地点）"));
        }
        zone = zone.trim();

        double[] position = readNumbers(json.get("position"), 3, problems, "position");
        if (position == null) {
            return ParseResult.failure(List.of("position 必须是 3 个数字"));
        }
        for (double value : position) {
            if (!Double.isFinite(value)) {
                return ParseResult.failure(List.of("position 含 NaN 或无穷大"));
            }
        }

        Rotation rotation = Rotation.NONE;
        JsonElement rotationJson = json.get("rotation");
        if (rotationJson != null && !rotationJson.isJsonNull()) {
            double[] read = readNumbers(rotationJson, 3, problems, "rotation");
            if (read != null && Double.isFinite(read[0]) && Double.isFinite(read[1]) && Double.isFinite(read[2])) {
                rotation = new Rotation((float) read[0], (float) read[1], (float) read[2]);
            }
        }

        String group = readString(json, "group");
        if (group == null) {
            group = "";
        } else {
            group = group.trim();
        }

        List<String> tags = new ArrayList<>();
        JsonElement tagsJson = json.get("tags");
        if (tagsJson != null && !tagsJson.isJsonNull()) {
            if (tagsJson.isJsonArray()) {
                for (JsonElement element : tagsJson.getAsJsonArray()) {
                    if (element == null || !element.isJsonPrimitive()) {
                        problems.add("tags 里有非字符串项，已忽略");
                        continue;
                    }
                    String tag = element.getAsString().trim();
                    if (tag.isEmpty() || tags.contains(tag)) {
                        continue;
                    }
                    tags.add(tag);
                }
            } else {
                problems.add("tags 必须是字符串数组，已忽略");
            }
        }

        int weight = readInt(json, "weight", DEFAULT_WEIGHT, 1, problems);
        boolean enabled = readBoolean(json, "enabled", true, problems);
        double qualityMultiplier = readDouble(json, "quality_multiplier", DEFAULT_QUALITY_MULTIPLIER,
                Double.MIN_VALUE, problems);

        RaidAnchor anchor = new RaidAnchor(id, type, zone, position[0], position[1], position[2],
                rotation, group, tags, weight, enabled, qualityMultiplier, source);
        return new ParseResult(anchor, List.copyOf(problems));
    }

    /** 写回 JSON（导出用）。字段名与 {@link #fromJson} 完全对应，保证往返一致。 */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("type", type.name());
        json.addProperty("zone", zone);

        JsonArray position = new JsonArray();
        position.add(x);
        position.add(y);
        position.add(z);
        json.add("position", position);

        if (!Rotation.NONE.equals(rotation)) {
            json.add("rotation", rotation.toJson());
        }
        if (!group.isEmpty()) {
            json.addProperty("group", group);
        }
        if (!tags.isEmpty()) {
            JsonArray tagArray = new JsonArray();
            tags.forEach(tagArray::add);
            json.add("tags", tagArray);
        }
        if (weight != DEFAULT_WEIGHT) {
            json.addProperty("weight", weight);
        }
        if (!enabled) {
            json.addProperty("enabled", false);
        }
        if (qualityMultiplier != DEFAULT_QUALITY_MULTIPLIER) {
            json.addProperty("quality_multiplier", qualityMultiplier);
        }
        return json;
    }

    public RaidAnchor withSource(Source newSource) {
        return new RaidAnchor(id, type, zone, x, y, z, rotation, group, tags, weight, enabled,
                qualityMultiplier, newSource);
    }

    public RaidAnchor withEnabled(boolean newEnabled) {
        return new RaidAnchor(id, type, zone, x, y, z, rotation, group, tags, weight, newEnabled,
                qualityMultiplier, source);
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    private static String readString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        return element.getAsString();
    }

    private static double[] readNumbers(JsonElement element, int expected, List<String> problems, String key) {
        if (element == null || !element.isJsonArray()) {
            if (element != null && !element.isJsonNull()) {
                problems.add(key + " 必须是数组");
            }
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() != expected) {
            problems.add(key + " 需要 " + expected + " 个数字，实际 " + array.size());
            return null;
        }
        double[] values = new double[expected];
        for (int i = 0; i < expected; i++) {
            JsonElement item = array.get(i);
            if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber()) {
                problems.add(key + " 的第 " + (i + 1) + " 项不是数字");
                return null;
            }
            values[i] = item.getAsDouble();
        }
        return values;
    }

    private static int readInt(JsonObject json, String key, int fallback, int minimum, List<String> problems) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            problems.add(key + " 不是数字，已用默认值 " + fallback);
            return fallback;
        }
        int value = element.getAsInt();
        if (value < minimum) {
            problems.add(key + " 小于 " + minimum + "，已用默认值 " + fallback);
            return fallback;
        }
        return value;
    }

    private static boolean readBoolean(JsonObject json, String key, boolean fallback, List<String> problems) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            problems.add(key + " 不是布尔值，已用默认值 " + fallback);
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static double readDouble(JsonObject json, String key, double fallback, double minimum,
                                     List<String> problems) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            problems.add(key + " 不是数字，已用默认值 " + fallback);
            return fallback;
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value) || value <= minimum) {
            problems.add(key + " 必须大于 0，已用默认值 " + fallback);
            return fallback;
        }
        return value;
    }

    /** 供导出与日志排序使用：按 id 的字典序，保证同一份数据每次输出一致。 */
    public static int compareById(RaidAnchor left, RaidAnchor right) {
        return left.id().compareTo(right.id());
    }

    public static List<RaidAnchor> sortedById(List<RaidAnchor> anchors) {
        List<RaidAnchor> copy = new ArrayList<>(Objects.requireNonNull(anchors, "anchors"));
        copy.sort(RaidAnchor::compareById);
        return Collections.unmodifiableList(copy);
    }

    /** 聚合标签，供调试命令打印。 */
    public String describe() {
        return id + " [" + type + "] zone=" + zone
                + " pos=(" + trim(x) + ", " + trim(y) + ", " + trim(z) + ")"
                + " weight=" + weight + (enabled ? "" : " (已禁用)")
                + (tags.isEmpty() ? "" : " tags=" + String.join(",", tags));
    }

    private static String trim(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** 便于命令与日志里拼 JSON 数字。 */
    public static JsonPrimitive primitive(double value) {
        return new JsonPrimitive(value);
    }
}
