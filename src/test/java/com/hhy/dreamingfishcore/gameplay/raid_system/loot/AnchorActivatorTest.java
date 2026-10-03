package com.hhy.dreamingfishcore.gameplay.raid_system.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hhy.dreamingfishcore.gameplay.raid_system.RaidRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 锚点激活与区域规划的不变量测试。
 *
 * <p>断言的是"规则必须生效"这类硬性质：每个子区域至少一个、单组不超上限、点之间保持间距、
 * 同种子同结果、被挡住时必须收尾而不是死循环。手感（哪个点更常被选中）不在这里断言。</p>
 */
class AnchorActivatorTest {

    private static AnchorActivator.Candidate candidate(String id, String group, int weight, double x, double z) {
        return new AnchorActivator.Candidate(id, group, weight, x, z);
    }

    private static LootConfig.Item item(String id, int cost) {
        return new LootConfig.Item(id, "misc", LootConfig.Rarity.COMMON, cost, 0, 100,
                Set.of(), Set.of(), Set.of(), 0);
    }

    private static LootConfig.Zone zone(int budget, int countMin, int countMax) {
        return new LootConfig.Zone("factory", 2, LootConfig.IntRange.of(countMin, countMax),
                LootConfig.IntRange.of(0, 0), LootConfig.IntRange.of(budget, budget), Set.of(),
                List.of(), List.of(), Set.of());
    }

    // ---------------------------------------------------------------- 激活

    @Test
    void activatesBetweenMinAndMaxAndNeverMore() {
        List<AnchorActivator.Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            candidates.add(candidate(String.format("p%02d", index), "g" + (index % 4), 100,
                    index * 50.0D, 0.0D));
        }
        AnchorActivator.Result result = AnchorActivator.activate(candidates,
                new AnchorActivator.Rules(6, 9, 0, 0.0D), new RaidRandom(1L));

        assertTrue(result.selected().size() >= 6 && result.selected().size() <= 9,
                "激活数量应落在 [6,9]，实际 " + result.selected().size());
        assertEquals(result.selected().stream().sorted().toList(), result.selected(),
                "输出应按 id 排序，保证确定性");
    }

    @Test
    void everyGroupGetsAChanceWhenMinimumAllows() {
        List<AnchorActivator.Candidate> candidates = List.of(
                candidate("a1", "east", 100, 0, 0),
                candidate("a2", "east", 100, 10, 0),
                candidate("b1", "west", 100, 500, 0),
                candidate("b2", "west", 100, 510, 0),
                candidate("c1", "north", 100, 1000, 0));
        AnchorActivator.Result result = AnchorActivator.activate(candidates,
                new AnchorActivator.Rules(3, 3, 1, 0.0D), new RaidRandom(2L));

        assertEquals(3, result.selected().size());
        assertEquals(Set.of("east", "west", "north"), result.perGroup().keySet(),
                "第一轮应让每个子区域各出一个：" + result.perGroup());
        assertTrue(result.perGroup().values().stream().allMatch(count -> count == 1),
                "单组上限为 1 时每组只能一个");
    }

    @Test
    void maxPerGroupIsEnforced() {
        List<AnchorActivator.Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            candidates.add(candidate("p" + index, "only_group", 100, index * 5.0D, 0.0D));
        }
        AnchorActivator.Result result = AnchorActivator.activate(candidates,
                new AnchorActivator.Rules(5, 8, 2, 0.0D), new RaidRandom(3L));

        assertEquals(2, result.selected().size(), "单组上限 2 时应只激活 2 个");
        assertFalse(result.problems().isEmpty(), "达不到最小数量应给出提示");
        assertEquals("MIN_NOT_MET", result.problems().get(0).code());
    }

    @Test
    void minDistanceIsEnforcedAndDoesNotLoopForever() {
        // 所有点都挤在一起：间距要求 10 格，最终只能激活 1 个
        List<AnchorActivator.Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            candidates.add(candidate("p" + index, "g", 100, index * 0.5D, 0.0D));
        }
        AnchorActivator.Result result = AnchorActivator.activate(candidates,
                new AnchorActivator.Rules(4, 8, 0, 10.0D), new RaidRandom(4L));

        assertEquals(1, result.selected().size(), "挤在一起的点最多只能选一个");
        assertFalse(result.skippedByDistance().isEmpty(), "被间距挡掉的点应被记录，便于排查地图问题");
    }

    @Test
    void activationIsDeterministicAndHandlesDegenerateInput() {
        List<AnchorActivator.Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            candidates.add(candidate("p" + index, "g" + (index % 3), 100 + index, index * 20.0D, index * 3.0D));
        }
        AnchorActivator.Rules rules = new AnchorActivator.Rules(5, 12, 4, 15.0D);

        List<String> first = AnchorActivator.activate(candidates, rules, new RaidRandom(5L)).selected();
        List<String> second = AnchorActivator.activate(candidates, rules, new RaidRandom(5L)).selected();
        assertEquals(first, second, "同种子必须选出同一批点");
        assertTrue(first.size() <= 12);

        assertTrue(AnchorActivator.activate(List.of(), rules, new RaidRandom(1L)).isEmpty());
        assertTrue(AnchorActivator.activate(null, rules, new RaidRandom(1L)).isEmpty());
        assertTrue(AnchorActivator.activate(candidates,
                new AnchorActivator.Rules(0, 0, 0, 0), new RaidRandom(1L)).isEmpty());
    }

    // ---------------------------------------------------------------- 规划

    private static RaidLootPlanner.Point point(String id, String group, double x, double quality,
                                               String containerType) {
        return new RaidLootPlanner.Point(id, group, 100, x, 0.0D, quality, containerType);
    }

    @Test
    void zonePlanStaysWithinBudgetAndCountRange() {
        List<RaidLootPlanner.Point> points = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            points.add(point("p" + index, "g" + (index % 2), index * 40.0D, 1.0D, "crate"));
        }
        List<LootConfig.Item> pool = List.of(item("cheap", 50), item("mid", 400), item("pricey", 2_000));

        RaidLootPlanner.ZonePlan plan = RaidLootPlanner.planZone(zone(15_000, 4, 7), points,
                LootConfig.IntRange.of(4, 7), pool, new RaidRandom(6L), Map.of(), 0, 0.0D);

        assertTrue(plan.activePointCount() >= 4 && plan.activePointCount() <= 7,
                "激活点数应落在范围内：" + plan.activePointCount());
        assertEquals(15_000, plan.zoneBudget());
        assertTrue(plan.allocation().spentTotal() <= plan.zoneBudget(), "花超了预算");
        assertEquals(plan.activePointCount(), plan.allocation().points().size(),
                "每个激活点都应有一条分配记录（哪怕什么都没放）");
        assertTrue(plan.summary().contains("factory"), "摘要应带上区域名：" + plan.summary());
    }

    @Test
    void zonePlanIsDeterministicForTheSameSeed() {
        List<RaidLootPlanner.Point> points = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            points.add(point("p" + index, "g" + (index % 3), index * 30.0D, 1.0D + index * 0.05D, "crate"));
        }
        List<LootConfig.Item> pool = List.of(item("a", 100), item("b", 250), item("c", 900));

        RaidLootPlanner.ZonePlan first = RaidLootPlanner.planZone(zone(9_000, 5, 9), points,
                LootConfig.IntRange.of(5, 9), pool, new RaidRandom(7L), Map.of(), 3, 10.0D);
        RaidLootPlanner.ZonePlan second = RaidLootPlanner.planZone(zone(9_000, 5, 9), points,
                LootConfig.IntRange.of(5, 9), pool, new RaidRandom(7L), Map.of(), 3, 10.0D);

        assertEquals(first.activation().selected(), second.activation().selected());
        assertEquals(first.allocation().allItemIds(), second.allocation().allItemIds());
        assertEquals(first.summary(), second.summary());
    }

    @Test
    void containerTypeRestrictionAppliesThroughTheWholeChain() {
        List<RaidLootPlanner.Point> points = List.of(point("crate1", "g", 0, 1.0D, "crate"),
                point("safe1", "g", 200, 1.0D, "safe"));
        LootConfig.Item safeOnly = new LootConfig.Item("gold_bar", "valuable", LootConfig.Rarity.RARE,
                300, 0, 100, Set.of(), Set.of("safe"), Set.of(), 0);
        List<LootConfig.Item> pool = List.of(safeOnly, item("screw", 50));

        RaidLootPlanner.ZonePlan plan = RaidLootPlanner.planZone(zone(1_000, 2, 2), points,
                LootConfig.IntRange.of(2, 2), pool, new RaidRandom(8L), Map.of(), 0, 0.0D);

        for (LootAllocator.PointAllocation allocation : plan.allocation().points()) {
            if ("crate1".equals(allocation.anchorId())) {
                assertFalse(allocation.itemIds().contains("gold_bar"),
                        "保险箱专属物品不该出现在普通箱子里");
            }
        }
    }

    @Test
    void raidWideRareQuotaIsSharedBetweenZones() {
        LootConfig.Item keycard = new LootConfig.Item("keycard", "electronic", LootConfig.Rarity.LEGENDARY,
                500, 0, 100, Set.of(), Set.of(), Set.of(), 1);
        List<LootConfig.Item> pool = List.of(keycard, item("screw", 50));

        LootConfig.Zone first = new LootConfig.Zone("zone_a", 2, LootConfig.IntRange.of(1, 1),
                LootConfig.IntRange.of(0, 0), LootConfig.IntRange.of(5_000, 5_000), Set.of(), List.of(),
                List.of(new LootConfig.RareRule("keycard", 1.0D, 1)), Set.of());
        LootConfig.Zone second = new LootConfig.Zone("zone_b", 2, LootConfig.IntRange.of(1, 1),
                LootConfig.IntRange.of(0, 0), LootConfig.IntRange.of(5_000, 5_000), Set.of(), List.of(),
                List.of(new LootConfig.RareRule("keycard", 1.0D, 1)), Set.of());

        Map<String, RaidLootPlanner.ZoneInput> inputs = new LinkedHashMap<>();
        inputs.put("zone_a", new RaidLootPlanner.ZoneInput(first,
                List.of(point("a1", "g", 0, 1.0D, "safe")), LootConfig.IntRange.of(1, 1)));
        inputs.put("zone_b", new RaidLootPlanner.ZoneInput(second,
                List.of(point("b1", "g", 0, 1.0D, "safe")), LootConfig.IntRange.of(1, 1)));

        // 先各自算出本局的稀有配额：两张卡各区域都想出 1 张，但全局上限是 1
        Map<String, Integer> globalRare = new LinkedHashMap<>();
        globalRare.put("keycard", 1);

        Map<String, RaidLootPlanner.ZonePlan> plans = RaidLootPlanner.planRaid(inputs, pool,
                new RaidRandom(9L), globalRare, 0, 0.0D);

        int totalPlaced = plans.values().stream()
                .mapToInt(plan -> (int) plan.allocation().allItemIds().stream().filter("keycard"::equals).count())
                .sum();
        assertTrue(totalPlaced <= 1, "跨区域合计不该超过全局上限 1，实际 " + totalPlaced);
        assertTrue(plans.get("zone_a").rareUsed().getOrDefault("keycard", 0)
                        + plans.get("zone_b").rareUsed().getOrDefault("keycard", 0) <= 1,
                "稀有物品记账也要跨区域受限");
    }
}
