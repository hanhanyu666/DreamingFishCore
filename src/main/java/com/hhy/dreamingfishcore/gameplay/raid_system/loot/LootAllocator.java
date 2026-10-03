package com.hhy.dreamingfishcore.gameplay.raid_system.loot;

import com.hhy.dreamingfishcore.gameplay.raid_system.RaidRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 战利品分配器：把区域预算分到资源点，再在每个点里挑物品。
 *
 * <p>全部是纯函数，方便对"最贵的一类回归"写断言：总价值落在预算内、稀有物不超过全局上限、
 * 买不起时能正常收尾而不是死循环。</p>
 *
 * <p>三条设计稿强调的分离在代码里的落点：</p>
 * <ol>
 *   <li>点位激活（哪个点出现）与点位内容（出现什么）分开：本类只管后者；</li>
 *   <li>资源点数量与区域总价值分开：数量由 {@link LootConfig.Zone#containerCount()} 决定，
 *       价值由 {@link LootConfig.Zone#budget()} 决定；</li>
 *   <li>生成成本与出售价格分开：本类只用 {@code spawnCost}，出售价格归 EconomySystem。</li>
 * </ol>
 */
public final class LootAllocator {

    /** 单个点的分配结果。 */
    public record PointAllocation(String anchorId, List<String> itemIds, int budget, int spent) {

        public PointAllocation {
            itemIds = List.copyOf(itemIds);
        }

        public int remaining() {
            return Math.max(0, budget - spent);
        }
    }

    /** 一个区域的分配结果。 */
    public record ZoneAllocation(String zoneId, int zoneBudget, List<PointAllocation> points,
                                 Map<String, Integer> rareAllocated, int spentTotal) {

        public ZoneAllocation {
            points = List.copyOf(points);
            rareAllocated = Map.copyOf(rareAllocated);
        }

        /** 所有点里的物品 id，按点顺序展开，便于命令打印与测试断言。 */
        public List<String> allItemIds() {
            List<String> ids = new ArrayList<>();
            points.forEach(point -> ids.addAll(point.itemIds()));
            return ids;
        }

        public int itemCount() {
            return allItemIds().size();
        }
    }

    /** 分配过程中的问题（不影响出结果，只是给人看）。 */
    public record Problem(String anchorId, String code, String message) {

        public static Problem of(String anchorId, String code, String message) {
            return new Problem(anchorId == null ? "" : anchorId, code, message);
        }
    }

    private LootAllocator() {
    }

    /**
     * 用**最大余数法**把总预算按权重切成整数份。
     *
     * <p>为什么不用逐个四舍五入：那样总和会飘（10 个点各差 1 就是 10 的误差），
     * 而"区域总价值"是要写进测试与运营预期的数字，必须精确等于预算。</p>
     *
     * <p>权重全为 0（或没有点）时，把预算平均分给各点，剩余零头给前面的点。</p>
     */
    public static int[] splitBudget(int total, int[] weights) {
        if (weights == null || weights.length == 0) {
            return new int[0];
        }
        int[] result = new int[weights.length];
        if (total <= 0) {
            return result;
        }
        long weightSum = 0L;
        for (int weight : weights) {
            if (weight > 0) {
                weightSum += weight;
            }
        }
        if (weightSum <= 0L) {
            int base = total / weights.length;
            int rest = total % weights.length;
            for (int index = 0; index < weights.length; index++) {
                result[index] = base + (index < rest ? 1 : 0);
            }
            return result;
        }

        long assigned = 0L;
        long[] remainders = new long[weights.length];
        for (int index = 0; index < weights.length; index++) {
            int weight = Math.max(0, weights[index]);
            long exact = (long) total * weight;
            result[index] = (int) (exact / weightSum);
            remainders[index] = exact % weightSum;
            assigned += result[index];
        }

        long leftover = total - assigned;
        // 余数大的先补；余数相同按原顺序，保证确定性（不依赖集合遍历顺序）
        while (leftover > 0L) {
            int bestIndex = -1;
            long bestRemainder = -1L;
            for (int index = 0; index < weights.length; index++) {
                if (remainders[index] > bestRemainder) {
                    bestRemainder = remainders[index];
                    bestIndex = index;
                }
            }
            if (bestIndex < 0) {
                break;
            }
            result[bestIndex]++;
            remainders[bestIndex] = -1L;
            leftover--;
        }
        return result;
    }

    /**
     * 本局稀有物品的全局分配（设计稿 §12.2）。
     *
     * <p>先决定"这一局一共出几件"，再往下分配到点——而不是让每个箱子独立抽 1%：
     * 后者在箱子变多之后产出必然失控。</p>
     *
     * @param alreadyAllocated 已经分配掉的（物品 id → 数量），用于跨区域累计全局上限
     */
    public static Map<String, Integer> allocateGlobalRare(LootConfig.Zone zone, RaidRandom random,
                                                          List<LootConfig.Item> pool,
                                                          Map<String, Integer> alreadyAllocated) {
        Map<String, Integer> result = new TreeMap<>();
        if (zone == null || random == null) {
            return result;
        }
        Map<String, Integer> used = alreadyAllocated == null ? Map.of() : alreadyAllocated;
        Map<String, LootConfig.Item> byId = new HashMap<>();
        if (pool != null) {
            pool.forEach(item -> byId.put(item.itemId(), item));
        }

        for (LootConfig.RareRule rule : zone.rareRules()) {
            if (random.nextDouble() >= rule.chance()) {
                continue;
            }
            LootConfig.Item item = byId.get(rule.itemId());
            int limit = item != null && item.hasGlobalLimit() ? item.raidGlobalLimit() : Integer.MAX_VALUE;
            int already = used.getOrDefault(rule.itemId(), 0) + result.getOrDefault(rule.itemId(), 0);
            int allowed = Math.max(0, limit - already);
            int count = Math.min(rule.count(), allowed);
            if (count > 0) {
                result.merge(rule.itemId(), count, Integer::sum);
            }
        }
        return result;
    }

    /**
     * 把一个区域的预算分到各个点并生成清单。
     *
     * @param zone            区域模板
     * @param anchorBudgets   点的 id → 该点的预算（由 {@link #splitZoneBudget} 或上层算好）
     * @param pool            物品池（已按区域/容器过滤也没关系，本类还会再校验一次）
     * @param random          本局 loot 子系统随机源（决定确定性的关键）
     * @param globalRare      本局全局稀有物品配额（物品 id → 数量）
     * @param containerTypes  点的 id → 容器类型（用于 allowed_container_types 过滤），可为空
     */
    public static ZoneAllocation allocateZone(LootConfig.Zone zone,
                                              Map<String, Integer> anchorBudgets,
                                              List<LootConfig.Item> pool,
                                              RaidRandom random,
                                              Map<String, Integer> globalRare,
                                              Map<String, String> containerTypes) {
        List<Problem> ignored = new ArrayList<>();
        return allocateZone(zone, anchorBudgets, pool, random, globalRare, containerTypes, ignored);
    }

    public static ZoneAllocation allocateZone(LootConfig.Zone zone,
                                              Map<String, Integer> anchorBudgets,
                                              List<LootConfig.Item> pool,
                                              RaidRandom random,
                                              Map<String, Integer> globalRare,
                                              Map<String, String> containerTypes,
                                              List<Problem> problems) {
        List<PointAllocation> points = new ArrayList<>();
        Map<String, Integer> rareLeft = new TreeMap<>(globalRare == null ? Map.of() : globalRare);
        int spentTotal = 0;
        int zoneBudget = 0;

        // 顺序确定：按点 id 排序遍历，避免调用方给的 Map 顺序影响结果
        Map<String, Integer> ordered = new TreeMap<>(anchorBudgets == null ? Map.of() : anchorBudgets);
        for (Map.Entry<String, Integer> entry : ordered.entrySet()) {
            String anchorId = entry.getKey();
            int budget = Math.max(0, entry.getValue());
            zoneBudget += budget;
            String containerType = containerTypes == null ? null : containerTypes.get(anchorId);

            List<LootConfig.Item> eligible = filterEligible(pool, zone, containerType);
            if (eligible.isEmpty()) {
                problems.add(Problem.of(anchorId, "EMPTY_POOL", "该点没有可用物品（检查 tier/容器/类别限制）"));
                points.add(new PointAllocation(anchorId, List.of(), budget, 0));
                continue;
            }

            PointAllocation allocation = fillPoint(anchorId, budget, eligible, zone, random, rareLeft, problems);
            points.add(allocation);
            spentTotal += allocation.spent();
        }

        Map<String, Integer> allocatedRare = new TreeMap<>();
        (globalRare == null ? Map.<String, Integer>of() : globalRare).forEach((itemId, count) -> {
            int left = rareLeft.getOrDefault(itemId, 0);
            int used = Math.max(0, count - left);
            if (used > 0) {
                allocatedRare.put(itemId, used);
            }
        });
        return new ZoneAllocation(zone == null ? "" : zone.zoneId(), zoneBudget, points, allocatedRare, spentTotal);
    }

    /** 按区域等级/标签/容器类型过滤出可用物品（顺序保持传入顺序，保证确定性）。 */
    public static List<LootConfig.Item> filterEligible(List<LootConfig.Item> pool, LootConfig.Zone zone,
                                                       String containerType) {
        List<LootConfig.Item> eligible = new ArrayList<>();
        if (pool == null) {
            return eligible;
        }
        Integer tier = zone == null ? null : zone.tier();
        java.util.Set<String> zoneTags = zone == null ? java.util.Set.of() : zone.zoneTags();
        java.util.Set<String> categories = zone == null ? java.util.Set.of() : zone.allowedCategories();
        for (LootConfig.Item item : pool) {
            if (item == null || item.spawnCost() < 1) {
                continue;
            }
            if (!item.allowedIn(tier, zoneTags, containerType)) {
                continue;
            }
            if (!categories.isEmpty() && !categories.contains(item.category())) {
                continue;
            }
            eligible.add(item);
        }
        return eligible;
    }

    /**
     * 往一个点里填物品：先必出、再稀有配额、最后按权重填普通物品并用零头池收尾。
     *
     * <p><b>关键点：只在"买得起"的子集里抽。</b>如果每轮都从全池抽、抽到贵的就跳过，
     * 那么当预算只剩很少而池子里大多是贵物品时，循环会一直空转（设计稿的伪代码就有这个隐患）。
     * 这里的做法是每轮重新算出买得起的子集，抽不到就立刻收尾。</p>
     */
    public static PointAllocation fillPoint(String anchorId, int budget, List<LootConfig.Item> eligible,
                                            LootConfig.Zone zone, RaidRandom random,
                                            Map<String, Integer> rareLeft, List<Problem> problems) {
        List<String> picked = new ArrayList<>();
        int remaining = budget;

        // 第一步：必出物品（品类别至少出 minCount 件，能出就出）
        if (zone != null) {
            for (LootConfig.Guarantee guarantee : zone.guarantees()) {
                int placed = 0;
                for (int attempt = 0; attempt < guarantee.minCount(); attempt++) {
                    List<LootConfig.Item> candidates = affordable(eligible, remaining, guarantee.category());
                    Optional<LootConfig.Item> choice = random.pickWeighted(candidates, LootConfig.Item::rarityWeight);
                    if (choice.isEmpty()) {
                        if (problems != null) {
                            problems.add(Problem.of(anchorId, "GUARANTEE_UNMET",
                                    "预算不足以放下必出类别 " + guarantee.category() + "（已放 " + placed + " 件）"));
                        }
                        break;
                    }
                    LootConfig.Item item = choice.get();
                    picked.add(item.itemId());
                    remaining -= item.spawnCost();
                    placed++;
                }
            }
        }

        // 第二步：本局分配的稀有物品（有配额才放，放完配额减一）
        if (rareLeft != null && !rareLeft.isEmpty()) {
            List<String> rareIds = new ArrayList<>(rareLeft.keySet());
            for (String itemId : rareIds) {
                if (rareLeft.getOrDefault(itemId, 0) <= 0) {
                    continue;
                }
                LootConfig.Item item = findByItemId(eligible, itemId);
                if (item == null || item.spawnCost() > remaining) {
                    continue;
                }
                picked.add(item.itemId());
                remaining -= item.spawnCost();
                rareLeft.merge(itemId, -1, Integer::sum);
            }
        }

        // 第三步：普通填充——每轮只在买得起的子集里抽
        int safety = 0;
        while (true) {
            List<LootConfig.Item> candidates = affordable(eligible, remaining, null);
            if (candidates.isEmpty()) {
                break;
            }
            Optional<LootConfig.Item> choice = random.pickWeighted(candidates, LootConfig.Item::rarityWeight);
            if (choice.isEmpty()) {
                break;
            }
            LootConfig.Item item = choice.get();
            picked.add(item.itemId());
            remaining -= item.spawnCost();
            if (++safety > 10_000) {
                if (problems != null) {
                    problems.add(Problem.of(anchorId, "SAFETY_STOP", "填充次数达到上限，提前收尾"));
                }
                break;
            }
        }

        return new PointAllocation(anchorId, picked, budget, budget - remaining);
    }

    /** 买得起的子集（可选再加类别过滤）；顺序与传入一致，保证抽取确定性。 */
    private static List<LootConfig.Item> affordable(List<LootConfig.Item> eligible, int remaining,
                                                    String category) {
        List<LootConfig.Item> result = new ArrayList<>();
        for (LootConfig.Item item : eligible) {
            if (item.spawnCost() > remaining || item.rarityWeight() <= 0) {
                continue;
            }
            if (category != null && !category.equals(item.category())) {
                continue;
            }
            result.add(item);
        }
        return result;
    }

    private static LootConfig.Item findByItemId(List<LootConfig.Item> items, String itemId) {
        for (LootConfig.Item item : items) {
            if (item.itemId().equals(itemId)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 按点的质量倍率把区域预算分到各点（设计稿 §11.1）。
     *
     * <p>倍率上再乘一个 0.8~1.2 的抖动，避免"同样的点每局拿到的钱完全一样"；
     * 最终仍走最大余数法，保证**各点预算之和精确等于区域预算**。</p>
     */
    public static Map<String, Integer> splitZoneBudget(int zoneBudget, Map<String, Double> qualityMultipliers,
                                                       RaidRandom random) {
        Map<String, Double> ordered = new TreeMap<>(qualityMultipliers == null ? Map.of() : qualityMultipliers);
        int[] weights = new int[ordered.size()];
        int index = 0;
        for (Map.Entry<String, Double> entry : ordered.entrySet()) {
            double quality = entry.getValue() == null ? 1.0D : Math.max(0.01D, entry.getValue());
            double jitter = random == null ? 1.0D : random.nextDouble(0.8D, 1.2D);
            weights[index++] = Math.max(1, (int) Math.round(quality * jitter * 100.0D));
        }
        int[] budgets = splitBudget(zoneBudget, weights);
        Map<String, Integer> result = new LinkedHashMap<>();
        index = 0;
        for (String anchorId : ordered.keySet()) {
            result.put(anchorId, budgets[index++]);
        }
        return result;
    }
}
