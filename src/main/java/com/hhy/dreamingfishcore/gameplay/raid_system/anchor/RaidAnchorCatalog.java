package com.hhy.dreamingfishcore.gameplay.raid_system.anchor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 合并 + 校验后的锚点目录：后续所有随机系统（撤离点、资源点、露天物品、刷怪点…）的**唯一候选来源**。
 *
 * <p>合并规则（设计稿 §4）：</p>
 * <ol>
 *   <li>定义层按 id 排序后取每个 id 的**第一条**，重复 id 记 {@code DUPLICATE_ID} 并丢弃后面的；</li>
 *   <li>世界层优先：{@code removed} 删除、{@code patches} 覆盖或新增（来源标记为 {@code OVERLAY}）、
 *       {@code disabled} 关掉；</li>
 *   <li>逐条校验，**单条失败只跳过这一条**并记问题，绝不让整张图或服务器起不来；</li>
 *   <li>输出按 id 排序，保证顺序确定（"同 seed 同结果"的前提）。</li>
 * </ol>
 *
 * <p>本类与 Minecraft 无关：区域是否存在、坐标是否落在区域内，通过 {@link ZoneLookup} 注入。
 * 游戏侧由适配层把现有任务地点体系（{@code TaskLocationDefinition}）包成这个接口。</p>
 */
public record RaidAnchorCatalog(List<RaidAnchor> anchors, List<Problem> problems) {

    /** 一条校验问题。{@code code} 供命令与日志筛选，{@code message} 给人看。 */
    public record Problem(String anchorId, String code, String message) {

        public static Problem of(String anchorId, String code, String message) {
            return new Problem(anchorId == null ? "" : anchorId, code, message);
        }

        @Override
        public String toString() {
            return (anchorId.isEmpty() ? "(全局)" : anchorId) + " [" + code + "] " + message;
        }
    }

    /** 区域查询：由游戏侧适配现有任务地点体系提供。 */
    public interface ZoneLookup {

        /** 任务地点是否存在（含已禁用但仍在册的地点）。 */
        boolean exists(String zoneId);

        /** 坐标是否落在该区域内（容差由实现方决定，例如外扩 1 格）。 */
        boolean contains(String zoneId, double x, double y, double z);

        /** 只认区域的实现，供不方便接游戏侧时使用（不做位置校验）。 */
        static ZoneLookup acceptingAll() {
            return new ZoneLookup() {
                @Override
                public boolean exists(String zoneId) {
                    return true;
                }

                @Override
                public boolean contains(String zoneId, double x, double y, double z) {
                    return true;
                }
            };
        }
    }

    public RaidAnchorCatalog {
        anchors = anchors == null ? List.of() : List.copyOf(anchors);
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    public static RaidAnchorCatalog empty() {
        return new RaidAnchorCatalog(List.of(), List.of());
    }

    /**
     * 合并定义层与世界层并校验。
     *
     * @param definitions 定义层（可乱序；内部会先按 id 排序再处理，保证与文件顺序无关）
     * @param overlay     世界层覆盖
     * @param zones       区域查询；传 {@code null} 表示不校验区域（仅测试用）
     */
    public static RaidAnchorCatalog build(List<RaidAnchor> definitions,
                                          RaidAnchorOverlay overlay,
                                          ZoneLookup zones) {
        List<Problem> problems = new ArrayList<>();

        // 1) 定义层：按 id 排序后取第一条，重复的丢弃并记录
        Map<String, RaidAnchor> merged = new TreeMap<>();
        for (RaidAnchor anchor : RaidAnchor.sortedById(definitions == null ? List.of() : definitions)) {
            RaidAnchor existing = merged.putIfAbsent(anchor.id(), anchor);
            if (existing != null) {
                problems.add(Problem.of(anchor.id(), "DUPLICATE_ID",
                        "定义层里 id 重复，已保留第一条并忽略后一条"));
            }
        }

        // 2) 世界层覆盖
        RaidAnchorOverlay effectiveOverlay = overlay == null ? RaidAnchorOverlay.empty() : overlay;
        for (String removedId : effectiveOverlay.removed()) {
            if (merged.remove(removedId) == null) {
                problems.add(Problem.of(removedId, "REMOVE_UNKNOWN_ID",
                        "世界层删除的 id 在定义层里不存在（可能地图已更新）"));
            }
        }
        for (Map.Entry<String, RaidAnchor> entry : effectiveOverlay.patches().entrySet()) {
            merged.put(entry.getKey(), entry.getValue().withSource(RaidAnchor.Source.OVERLAY));
        }
        for (String disabledId : effectiveOverlay.disabled()) {
            RaidAnchor anchor = merged.get(disabledId);
            if (anchor == null) {
                problems.add(Problem.of(disabledId, "DISABLE_UNKNOWN_ID",
                        "世界层禁用的 id 不存在（可能地图已更新）"));
                continue;
            }
            merged.put(disabledId, anchor.withEnabled(false));
        }

        // 3) 校验：单条失败只跳过这一条
        List<RaidAnchor> valid = new ArrayList<>();
        for (RaidAnchor anchor : merged.values()) {
            Optional<Problem> problem = validate(anchor, zones);
            if (problem.isPresent()) {
                problems.add(problem.get());
                continue;
            }
            valid.add(anchor);
        }

        // 4) 输出顺序确定
        valid.sort(RaidAnchor::compareById);
        return new RaidAnchorCatalog(valid, problems);
    }

    /** 单条校验。返回空表示可用。 */
    public static Optional<Problem> validate(RaidAnchor anchor, ZoneLookup zones) {
        if (anchor == null) {
            return Optional.of(Problem.of("", "NULL_ANCHOR", "锚点为 null"));
        }
        if (anchor.id() == null || anchor.id().isBlank()) {
            return Optional.of(Problem.of("", "BAD_ID", "id 为空"));
        }
        if (!Double.isFinite(anchor.x()) || !Double.isFinite(anchor.y()) || !Double.isFinite(anchor.z())) {
            return Optional.of(Problem.of(anchor.id(), "BAD_POSITION", "坐标含 NaN 或无穷大"));
        }
        if (anchor.weight() < 1) {
            return Optional.of(Problem.of(anchor.id(), "BAD_WEIGHT",
                    "weight 必须 ≥1，实际 " + anchor.weight()));
        }
        if (!(anchor.qualityMultiplier() > 0.0D) || !Double.isFinite(anchor.qualityMultiplier())) {
            return Optional.of(Problem.of(anchor.id(), "BAD_QUALITY_MULTIPLIER",
                    "quality_multiplier 必须大于 0，实际 " + anchor.qualityMultiplier()));
        }
        if (zones != null) {
            if (anchor.zone() == null || anchor.zone().isBlank()) {
                return Optional.of(Problem.of(anchor.id(), "MISSING_ZONE", "没有所属区域"));
            }
            if (!zones.exists(anchor.zone())) {
                return Optional.of(Problem.of(anchor.id(), "UNKNOWN_ZONE",
                        "区域不存在：" + anchor.zone()));
            }
            if (!zones.contains(anchor.zone(), anchor.x(), anchor.y(), anchor.z())) {
                return Optional.of(Problem.of(anchor.id(), "OUTSIDE_ZONE",
                        "坐标 (" + anchor.blockX() + ", " + anchor.blockY() + ", " + anchor.blockZ()
                                + ") 不在区域 " + anchor.zone() + " 内"));
            }
        }
        return Optional.empty();
    }

    public boolean ok() {
        return problems.isEmpty();
    }

    public Optional<RaidAnchor> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (RaidAnchor anchor : anchors) {
            if (anchor.id().equals(id)) {
                return Optional.of(anchor);
            }
        }
        return Optional.empty();
    }

    public List<RaidAnchor> byType(RaidAnchorType type) {
        List<RaidAnchor> result = new ArrayList<>();
        for (RaidAnchor anchor : anchors) {
            if (anchor.type() == type) {
                result.add(anchor);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<RaidAnchor> byZone(String zone) {
        List<RaidAnchor> result = new ArrayList<>();
        for (RaidAnchor anchor : anchors) {
            if (anchor.zone().equals(zone)) {
                result.add(anchor);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** 各类型的数量统计（按类型名字典序，便于日志对比）。 */
    public Map<String, Integer> countByType() {
        Map<String, Integer> counts = new TreeMap<>();
        for (RaidAnchor anchor : anchors) {
            counts.merge(anchor.type().name(), 1, Integer::sum);
        }
        return new LinkedHashMap<>(counts);
    }

    /** 供命令打印的摘要。 */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        lines.add("可用锚点 " + anchors.size() + " 个，问题 " + problems.size() + " 条");
        countByType().forEach((type, count) -> lines.add("  " + type + " × " + count));
        for (Problem problem : problems) {
            lines.add("  问题 " + problem);
        }
        return lines;
    }
}
