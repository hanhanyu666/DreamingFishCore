package com.hhy.dreamingfishcore.gameplay.raid_system.loot;

import com.hhy.dreamingfishcore.gameplay.raid_system.RaidRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 区域规划器：一次把"激活哪些点"和"每个点放什么"串起来（设计稿 §11 与 §13 的第 8~14 步）。
 *
 * <p>顺序严格按设计稿：先算区域预算 → 激活资源点 → 按质量倍率切分预算 → 分配物品 →
 * 记账稀有物品。每一步都是纯函数，所以整条链路可以单测。</p>
 *
 * <p>区域之间要**共享** {@code globalRare} 配额：调用方把上一个区域返回的 {@code rareUsed}
 * 累计起来传给下一个区域，全局上限才真正是"单局上限"而不是"每区域上限"。</p>
 */
public final class RaidLootPlanner {

    /** 一个候选资源点（锚点 + 它的附加信息）。 */
    public record Point(String anchorId, String group, int weight, double x, double z,
                        double qualityMultiplier, String containerType) {

        public AnchorActivator.Candidate toCandidate() {
            return new AnchorActivator.Candidate(anchorId, group, weight, x, z);
        }
    }

    /**
     * 一个区域的规划结果。
     *
     * @param zoneId      区域 id
     * @param zoneBudget  本局该区域的总预算（落在区域配置的范围内）
     * @param activation  激活结果（哪些点出现、分组统计、被间距/上限挡掉的原因）
     * @param allocation  分配结果（每个点放了什么、花了多少）
     * @param rareUsed    本区域真正用掉的稀有物品数量
     */
    public record ZonePlan(String zoneId, int zoneBudget, AnchorActivator.Result activation,
                           LootAllocator.ZoneAllocation allocation, Map<String, Integer> rareUsed) {

        public ZonePlan {
            rareUsed = Map.copyOf(rareUsed);
        }

        public int itemCount() {
            return allocation.itemCount();
        }

        public int activePointCount() {
            return activation.selected().size();
        }

        /** 供命令与 manifest 备注使用的单行摘要。 */
        public String summary() {
            StringBuilder builder = new StringBuilder();
            builder.append("区域 ").append(zoneId)
                    .append("：预算 ").append(zoneBudget)
                    .append("，实际 ").append(allocation.spentTotal())
                    .append("；激活 ").append(activePointCount()).append(" 点（")
                    .append(AnchorActivator.describeGroups(activation.perGroup())).append('）')
                    .append("；共 ").append(itemCount()).append(" 件");
            if (!rareUsed.isEmpty()) {
                builder.append("；稀有 ").append(rareUsed);
            }
            if (!activation.problems().isEmpty()) {
                builder.append("；提示 ").append(activation.problems().get(0).message());
            }
            return builder.toString();
        }
    }

    private RaidLootPlanner() {
    }

    /**
     * 规划一个区域。
     *
     * @param zone        区域模板
     * @param points      该区域的候选点（调用方已按锚点类型筛过，例如只传容器点）
     * @param countRange  本类点位要激活几个（容器点用区域配置的容器范围，露天物品用露天范围）
     * @param pool        物品池
     * @param random      本局 loot 子系统随机源
     * @param globalRare  本局稀有物品剩余配额（跨区域累计后的值）
     * @param maxPerGroup 每个子区域最多激活几个（0 表示不限）
     * @param minDistance 激活点之间的最小水平间距（0 表示不限）
     */
    public static ZonePlan planZone(LootConfig.Zone zone,
                                    List<Point> points,
                                    LootConfig.IntRange countRange,
                                    List<LootConfig.Item> pool,
                                    RaidRandom random,
                                    Map<String, Integer> globalRare,
                                    int maxPerGroup,
                                    double minDistance) {
        int zoneBudget = zone.budget().pick(random);

        List<AnchorActivator.Candidate> candidates = new ArrayList<>();
        if (points != null) {
            points.forEach(point -> candidates.add(point.toCandidate()));
        }
        AnchorActivator.Rules rules = new AnchorActivator.Rules(
                countRange == null ? 0 : countRange.min(),
                countRange == null ? 0 : countRange.max(),
                maxPerGroup, minDistance);
        AnchorActivator.Result activation = AnchorActivator.activate(candidates, rules, random);

        // 只给激活的点分预算；质量倍率取自候选点定义，缺失时按 1.0
        Map<String, Double> quality = new LinkedHashMap<>();
        Map<String, String> containerTypes = new LinkedHashMap<>();
        Map<String, Point> byId = new HashMap<>();
        if (points != null) {
            points.forEach(point -> {
                byId.put(point.anchorId(), point);
                containerTypes.put(point.anchorId(), point.containerType());
            });
        }
        for (String anchorId : activation.selected()) {
            Point point = byId.get(anchorId);
            quality.put(anchorId, point == null ? 1.0D : Math.max(0.01D, point.qualityMultiplier()));
        }

        Map<String, Integer> budgets = LootAllocator.splitZoneBudget(zoneBudget, quality, random);
        Map<String, Integer> rareLeft = new TreeMap<>(globalRare == null ? Map.of() : globalRare);

        List<LootAllocator.Problem> problems = new ArrayList<>();
        LootAllocator.ZoneAllocation allocation = LootAllocator.allocateZone(zone, budgets, pool, random,
                rareLeft, containerTypes, problems);

        return new ZonePlan(zone.zoneId(), zoneBudget, activation, allocation, allocation.rareAllocated());
    }

    /**
     * 连续规划多个区域，并把稀有物品配额在区域之间传递。
     *
     * <p>这是"单局全局上限"真正生效的地方：第一个区域用掉的配额，第二个区域拿不到。</p>
     *
     * @param zoneInputs 区域 → (候选点, 数量范围)；顺序按区域 id 排序处理，保证确定性
     */
    public static Map<String, ZonePlan> planRaid(Map<String, ZoneInput> zoneInputs,
                                                 List<LootConfig.Item> pool,
                                                 RaidRandom random,
                                                 Map<String, Integer> globalRare,
                                                 int maxPerGroup,
                                                 double minDistance) {
        Map<String, ZonePlan> plans = new TreeMap<>();
        Map<String, Integer> remaining = new TreeMap<>(globalRare == null ? Map.of() : globalRare);
        if (zoneInputs == null) {
            return plans;
        }
        for (Map.Entry<String, ZoneInput> entry : new TreeMap<>(zoneInputs).entrySet()) {
            ZoneInput input = entry.getValue();
            if (input == null || input.zone() == null) {
                continue;
            }
            ZonePlan plan = planZone(input.zone(), input.points(), input.countRange(), pool, random,
                    remaining, maxPerGroup, minDistance);
            plans.put(entry.getKey(), plan);
            plan.rareUsed().forEach((itemId, used) -> remaining.merge(itemId, -used, Integer::sum));
        }
        return plans;
    }

    /** 一个区域的输入：模板 + 候选点 + 数量范围。 */
    public record ZoneInput(LootConfig.Zone zone, List<Point> points, LootConfig.IntRange countRange) {

        public ZoneInput {
            points = points == null ? List.of() : List.copyOf(points);
        }
    }
}
