package com.hhy.dreamingfishcore.gameplay.raid_system.loot;

import com.hhy.dreamingfishcore.gameplay.raid_system.RaidRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 露天物品（静态战利品节点）的规划与拾取规则（设计稿 §8）。
 *
 * <p>设计稿明确不要直接用 {@code ItemEntity}（会被水流推、受爆炸影响、自动堆叠、被漏斗吸、
 * 桌面物品会掉地上、区块重载难管理），而是：**服务器保存静态节点，客户端显示模型，
 * 拾取由服务端验证**。本类负责前半段（每局生成哪些节点、每个节点是什么物品）与拾取判定的纯逻辑，
 * 世界侧（放置显示、发拾取包、写入背包）由后续接线完成。</p>
 *
 * <p>点位仍然只来自锚点系统的 {@code LOOSE_LOOT} 类型；高级字段用标签表达：</p>
 * <pre>
 * loot:electronic / loot:document / loot:valuable    该点只允许这些类别（可多个；不写=不限）
 * value:800                                          该点物品的生成成本上限（默认 {@value #DEFAULT_VALUE_CAP}）
 * </pre>
 *
 * <p>三条不变量：</p>
 * <ol>
 *   <li><b>确定性</b>：点位按 id 排序、权重抽取走 {@link RaidRandom}，同种子同结果；</li>
 *   <li><b>不超预算</b>：每个点的物品生成成本不超过该点的上限（露天物品应该是"随手能捡的"，不是保险柜）；</li>
 *   <li><b>稀有物受全局上限约束</b>：与容器战利品共用同一份 {@code raid_global_limit} 配额。</li>
 * </ol>
 */
public final class LooseLootPlanner {

    /** 单个露天物品点的默认价值上限（生成成本口径）。 */
    public static final int DEFAULT_VALUE_CAP = 600;
    /** 拾取距离（格）。超过这个距离服务端拒绝拾取。 */
    public static final double MAX_PICKUP_DISTANCE = 3.0D;

    /**
     * 一个候选露天物品点。
     *
     * @param anchorId          锚点 id
     * @param group             子区域（用于后续"每片至少一个"之类的规则）
     * @param weight            被选中的权重
     * @param x/y/z             世界坐标（y 很重要：要贴在桌面上）
     * @param yaw               朝向（度），客户端显示模型时用
     * @param qualityMultiplier 质量倍率（影响价值上限）
     * @param tags              锚点标签（loot: / value: 在这里解析）
     */
    public record Spot(String anchorId, String group, int weight,
                       double x, double y, double z, float yaw,
                       double qualityMultiplier, Set<String> tags) {

        public Spot {
            anchorId = anchorId == null ? "" : anchorId;
            group = group == null ? "" : group;
            tags = tags == null ? Set.of() : Set.copyOf(new TreeSet<>(tags));
        }

        /** 该点允许的类别（来自 loot: 标签）；空集表示不限。 */
        public Set<String> categories() {
            Set<String> values = new TreeSet<>();
            for (String tag : tags) {
                String lower = tag.toLowerCase(Locale.ROOT);
                if (lower.startsWith("loot:")) {
                    String value = lower.substring("loot:".length()).trim();
                    if (!value.isEmpty()) {
                        values.add(value);
                    }
                }
            }
            return values;
        }

        /** 该点的价值上限：value: 标签 > 默认值 × 质量倍率。 */
        public int valueCap() {
            for (String tag : tags) {
                String lower = tag.toLowerCase(Locale.ROOT);
                if (lower.startsWith("value:")) {
                    try {
                        return Math.max(1, Integer.parseInt(lower.substring("value:".length()).trim()));
                    } catch (NumberFormatException ignored) {
                        // 标签写坏了就用默认值，不影响这个点可用
                    }
                }
            }
            return Math.max(1, (int) Math.round(DEFAULT_VALUE_CAP * Math.max(0.01D, qualityMultiplier)));
        }

        public boolean allows(String category) {
            Set<String> allowed = categories();
            return allowed.isEmpty() || (category != null && allowed.contains(category));
        }
    }

    /** 一个生成好的静态节点。 */
    public record Node(String anchorId, String itemId, int value,
                       double x, double y, double z, float yaw) {
    }

    /** 规划结果。{@code rareUsed} 是本区域真正用掉的稀有物品数量（供跨区域累计）。 */
    public record Plan(List<Node> nodes, Map<String, Integer> rareUsed) {

        public Plan {
            nodes = List.copyOf(nodes);
            rareUsed = Map.copyOf(rareUsed);
        }

        public int itemCount() {
            return nodes.size();
        }
    }

    /** 拾取判定结果。 */
    public record PickupCheck(boolean allowed, String reason) {

        public static PickupCheck ok() {
            return new PickupCheck(true, "");
        }

        public static PickupCheck denied(String reason) {
            return new PickupCheck(false, reason);
        }
    }

    private LooseLootPlanner() {
    }

    /**
     * 生成露天物品节点。
     *
     * @param spots       候选点（本区域的 LOOSE_LOOT 锚点）
     * @param pool        物品池
     * @param random      本局 loot 子系统随机源
     * @param globalRare  本局稀有物品剩余配额（跨区域累计后的值）
     * @param maxNodes    最多生成几个（来自区域配置的露天物品数量范围）
     */
    public static Plan plan(List<Spot> spots, List<LootConfig.Item> pool, RaidRandom random,
                            Map<String, Integer> globalRare, int maxNodes) {
        List<Node> nodes = new ArrayList<>();
        Map<String, Integer> rareLeft = new TreeMap<>(globalRare == null ? Map.of() : globalRare);
        if (spots == null || spots.isEmpty() || pool == null || pool.isEmpty() || maxNodes <= 0) {
            return new Plan(nodes, Map.of());
        }

        // 确定性：按锚点 id 排序
        List<Spot> ordered = new ArrayList<>(spots);
        ordered.sort(Comparator.comparing(Spot::anchorId));

        for (Spot spot : ordered) {
            if (nodes.size() >= maxNodes) {
                break;
            }
            if (spot.weight() <= 0) {
                continue;
            }
            List<LootConfig.Item> eligible = eligible(pool, spot, rareLeft);
            var picked = random.pickWeighted(eligible, LootConfig.Item::rarityWeight);
            if (picked.isEmpty()) {
                continue;
            }
            LootConfig.Item item = picked.get();
            if (item.hasGlobalLimit()) {
                rareLeft.merge(item.itemId(), -1, Integer::sum);
            }
            nodes.add(new Node(spot.anchorId(), item.itemId(), item.spawnCost(),
                    spot.x(), spot.y(), spot.z(), spot.yaw()));
        }

        Map<String, Integer> used = new TreeMap<>();
        (globalRare == null ? Map.<String, Integer>of() : globalRare).forEach((itemId, count) -> {
            int left = rareLeft.getOrDefault(itemId, 0);
            int spent = Math.max(0, count - left);
            if (spent > 0) {
                used.put(itemId, spent);
            }
        });
        return new Plan(nodes, used);
    }

    /** 某个点能放的物品：类别允许、成本不超该点上限、权重为正、稀有配额还有剩。 */
    public static List<LootConfig.Item> eligible(List<LootConfig.Item> pool, Spot spot,
                                                 Map<String, Integer> rareLeft) {
        List<LootConfig.Item> result = new ArrayList<>();
        int cap = spot.valueCap();
        for (LootConfig.Item item : pool) {
            if (item == null || item.rarityWeight() <= 0 || item.spawnCost() > cap) {
                continue;
            }
            if (!spot.allows(item.category())) {
                continue;
            }
            if (item.hasGlobalLimit()
                    && (rareLeft == null || rareLeft.getOrDefault(item.itemId(), 0) <= 0)) {
                continue;
            }
            result.add(item);
        }
        return result;
    }

    /**
     * 服务端拾取判定（纯函数，单独测）。
     *
     * <p>顺序也重要：先看"是否已被拾取"（幂等），再看拿没拿到，最后才说距离。</p>
     */
    public static PickupCheck canPickup(boolean alreadyPicked, double distance, boolean lockedByOther,
                                        boolean inventoryFull) {
        if (alreadyPicked) {
            return PickupCheck.denied("这个物品已经被拿走了");
        }
        if (lockedByOther) {
            return PickupCheck.denied("别的玩家正在拾取");
        }
        if (distance > MAX_PICKUP_DISTANCE) {
            return PickupCheck.denied("距离太远（" + String.format(Locale.ROOT, "%.1f", distance)
                    + " 格 > " + MAX_PICKUP_DISTANCE + " 格）");
        }
        if (inventoryFull) {
            return PickupCheck.denied("背包放不下");
        }
        return PickupCheck.ok();
    }

    /** 供命令与日志打印。 */
    public static List<String> describe(Plan plan) {
        List<String> lines = new ArrayList<>();
        lines.add("露天物品节点 " + plan.itemCount() + " 个"
                + (plan.rareUsed().isEmpty() ? "" : "，其中稀有 " + plan.rareUsed()));
        plan.nodes().forEach(node -> lines.add("  " + node.anchorId() + " → " + node.itemId()
                + "（成本 " + node.value() + "，位置 " + String.format(Locale.ROOT, "%.1f/%.1f/%.1f",
                node.x(), node.y(), node.z()) + "）"));
        return lines;
    }
}
