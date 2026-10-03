package com.hhy.dreamingfishcore.gameplay.raid_system.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 战利品分配的不变量测试。
 *
 * <p>这里断言的都是"贵得离谱但一旦破了很难查"的性质：预算不能超、稀有物不能超上限、
 * 买不起时要能收尾而不是死循环、同种子必须同结果。数值手感（哪件物品更常见）不在这里断言。</p>
 */
class LootAllocatorTest {

    private static LootConfig.Item item(String id, int cost) {
        return item(id, cost, "misc", 100);
    }

    private static LootConfig.Item item(String id, int cost, String category, int weight) {
        return new LootConfig.Item(id, category, LootConfig.Rarity.COMMON, cost, 0, weight,
                Set.of(), Set.of(), Set.of(), 0);
    }

    private static LootConfig.Zone zone(int budgetMin, int budgetMax, List<LootConfig.Guarantee> guarantees,
                                        List<LootConfig.RareRule> rareRules) {
        return new LootConfig.Zone("factory", 2, LootConfig.IntRange.of(2, 4), LootConfig.IntRange.of(1, 2),
                LootConfig.IntRange.of(budgetMin, budgetMax), Set.of(), guarantees, rareRules, Set.of());
    }

    // ---------------------------------------------------------------- 预算切分

    @Test
    void splitBudgetAlwaysSumsToTotal() {
        int[][] weightCases = {
                {100, 100, 100},
                {1, 2, 3, 4},
                {7},
                {0, 0, 0},
                {5, 0, 5},
                {1, 1, 1, 1, 1, 1, 1},
        };
        int[] totals = {0, 1, 7, 100, 9_999, 123_456};
        for (int[] weights : weightCases) {
            for (int total : totals) {
                int[] parts = LootAllocator.splitBudget(total, weights);
                assertEquals(weights.length, parts.length);
                int sum = 0;
                for (int part : parts) {
                    assertTrue(part >= 0, "不允许负预算");
                    sum += part;
                }
                assertEquals(total, sum, "切分必须精确等于总预算（权重 " + java.util.Arrays.toString(weights)
                        + "，总预算 " + total + "）");
            }
        }
    }

    @Test
    void splitBudgetHandlesDegenerateInputs() {
        assertEquals(0, LootAllocator.splitBudget(100, new int[0]).length);
        assertEquals(0, LootAllocator.splitBudget(100, null).length);
        int[] negative = LootAllocator.splitBudget(-5, new int[]{1, 1});
        assertEquals(0, negative[0] + negative[1], "负总预算应全为 0 而不是负数");
    }

    @Test
    void splitZoneBudgetSumsToZoneBudgetAndIsDeterministic() {
        Map<String, Double> multipliers = new java.util.LinkedHashMap<>();
        multipliers.put("a", 0.5D);
        multipliers.put("b", 1.0D);
        multipliers.put("c", 2.0D);
        multipliers.put("d", 3.0D);

        Map<String, Integer> first = LootAllocator.splitZoneBudget(15_000, multipliers, new RaidRandom(7L));
        Map<String, Integer> second = LootAllocator.splitZoneBudget(15_000, multipliers, new RaidRandom(7L));
        assertEquals(first, second, "同种子必须得到完全相同的分配");
        assertEquals(15_000, first.values().stream().mapToInt(Integer::intValue).sum(),
                "各点预算之和必须精确等于区域预算");
        assertTrue(first.get("d") > first.get("a"), "质量倍率高的点应拿到更多预算");
    }

    // ---------------------------------------------------------------- 区域分配

    @Test
    void neverSpendsMoreThanBudgetOnAnyPoint() {
        List<LootConfig.Item> pool = List.of(item("cheap", 50), item("mid", 300), item("pricey", 1500));
        Map<String, Integer> budgets = new java.util.LinkedHashMap<>();
        budgets.put("p1", 1000);
        budgets.put("p2", 3);
        budgets.put("p3", 0);

        LootAllocator.ZoneAllocation allocation = LootAllocator.allocateZone(
                zone(1000, 1000, List.of(), List.of()), budgets, pool, new RaidRandom(11L), Map.of(), Map.of());

        for (LootAllocator.PointAllocation point : allocation.points()) {
            assertTrue(point.spent() <= point.budget(),
                    "点 " + point.anchorId() + " 花超了：" + point.spent() + " > " + point.budget());
            assertTrue(point.remaining() >= 0);
        }
        assertTrue(allocation.spentTotal() <= allocation.zoneBudget());
        // 预算 3 连最便宜的都买不起：必须安静地什么都不放，而不是死循环
        assertEquals(0, allocation.points().stream().filter(point -> point.anchorId().equals("p2"))
                .findFirst().orElseThrow().spent());
    }

    @Test
    void terminatesWhenNothingIsAffordable() {
        List<LootConfig.Item> pool = List.of(item("expensive_a", 500), item("expensive_b", 900));
        LootAllocator.ZoneAllocation allocation = LootAllocator.allocateZone(
                zone(10, 10, List.of(), List.of()), Map.of("p1", 10), pool, new RaidRandom(3L), Map.of(), Map.of());
        assertEquals(0, allocation.itemCount(), "买不起时不该产出任何物品");
        assertEquals(0, allocation.spentTotal());
    }

    @Test
    void allocationIsDeterministicForTheSameSeed() {
        List<LootConfig.Item> pool = List.of(item("a", 100, "misc", 100), item("b", 250, "misc", 50),
                item("c", 700, "misc", 10), item("d", 40, "filler", 200));
        Map<String, Integer> budgets = Map.of("p1", 2_000, "p2", 1_500);

        List<String> first = LootAllocator.allocateZone(zone(3_500, 3_500, List.of(), List.of()), budgets,
                pool, new RaidRandom(42L), Map.of(), Map.of()).allItemIds();
        List<String> second = LootAllocator.allocateZone(zone(3_500, 3_500, List.of(), List.of()), budgets,
                pool, new RaidRandom(42L), Map.of(), Map.of()).allItemIds();
        assertEquals(first, second, "同种子同输入必须得到完全相同的清单");

        List<String> different = LootAllocator.allocateZone(zone(3_500, 3_500, List.of(), List.of()), budgets,
                pool, new RaidRandom(43L), Map.of(), Map.of()).allItemIds();
        assertFalse(first.isEmpty());
        assertFalse(first.equals(different) && first.size() > 5, "换种子不该连一件都不变");
    }

    @Test
    void guaranteesAreHonouredWhenBudgetAllows() {
        List<LootConfig.Item> pool = List.of(item("bandage", 100, "medical", 100),
                item("screw", 50, "mechanical", 100));
        LootConfig.Zone zone = zone(1_000, 1_000,
                List.of(new LootConfig.Guarantee("medical", 2, 4)), List.of());

        LootAllocator.ZoneAllocation allocation = LootAllocator.allocateZone(zone, Map.of("p1", 1_000),
                pool, new RaidRandom(5L), Map.of(), Map.of());
        long medical = allocation.allItemIds().stream().filter("bandage"::equals).count();
        assertTrue(medical >= 2, "必出类别应至少出 2 件，实际 " + medical);
    }

    @Test
    void restrictionsOnTierContainerAndCategoryAreRespected() {
        LootConfig.Item tierThreeOnly = new LootConfig.Item("lab_key", "electronic",
                LootConfig.Rarity.RARE, 300, 0, 10, Set.of(3), Set.of(), Set.of(), 0);
        LootConfig.Item safeOnly = new LootConfig.Item("gold_bar", "valuable",
                LootConfig.Rarity.RARE, 400, 0, 10, Set.of(), Set.of("safe"), Set.of(), 0);
        LootConfig.Item civilianOnly = new LootConfig.Item("watch", "civilian",
                LootConfig.Rarity.COMMON, 200, 0, 50, Set.of(), Set.of(), Set.of("residential"), 0);
        LootConfig.Item anything = item("screw", 50, "mechanical", 100);
        List<LootConfig.Item> pool = List.of(tierThreeOnly, safeOnly, civilianOnly, anything);

        // tier=2、容器 crate、无区域标签
        LootConfig.Zone zone = new LootConfig.Zone("factory", 2, LootConfig.IntRange.of(1, 1),
                LootConfig.IntRange.of(0, 0), LootConfig.IntRange.of(1_000, 1_000), Set.of(),
                List.of(), List.of(), Set.of("factory"));
        LootAllocator.ZoneAllocation allocation = LootAllocator.allocateZone(zone, Map.of("p1", 1_000),
                pool, new RaidRandom(9L), Map.of(), Map.of("p1", "crate"));

        List<String> ids = allocation.allItemIds();
        assertFalse(ids.contains("lab_key"), "等级不符的物品不该出现");
        assertFalse(ids.contains("gold_bar"), "容器类型不符的物品不该出现");
        assertFalse(ids.contains("watch"), "区域标签不符的物品不该出现");
        assertTrue(ids.contains("screw"), "无限制的物品应能出现");
    }

    @Test
    void categoryAllowListIsApplied() {
        List<LootConfig.Item> pool = List.of(item("bandage", 100, "medical", 100),
                item("screw", 100, "mechanical", 100));
        LootConfig.Zone zone = new LootConfig.Zone("factory", 2, LootConfig.IntRange.of(1, 1),
                LootConfig.IntRange.of(0, 0), LootConfig.IntRange.of(1_000, 1_000), Set.of("mechanical"),
                List.of(), List.of(), Set.of());
        LootAllocator.ZoneAllocation allocation = LootAllocator.allocateZone(zone, Map.of("p1", 1_000),
                pool, new RaidRandom(13L), Map.of(), Map.of());
        assertTrue(allocation.allItemIds().stream().allMatch("screw"::equals),
                "区域允许列表之外的类别不该出现：" + allocation.allItemIds());
    }

    // ---------------------------------------------------------------- 稀有物全局分配

    @Test
    void globalRareNeverExceedsRaidLimit() {
        LootConfig.Item keycard = new LootConfig.Item("lab_keycard", "electronic", LootConfig.Rarity.LEGENDARY,
                2_000, 0, 1, Set.of(), Set.of(), Set.of(), 2);
        List<LootConfig.Item> pool = List.of(keycard);
        LootConfig.Zone zone = zone(10_000, 10_000, List.of(),
                List.of(new LootConfig.RareRule("lab_keycard", 1.0D, 5)));   // 想一次要 5 张

        Map<String, Integer> allocated = LootAllocator.allocateGlobalRare(zone, new RaidRandom(1L), pool, Map.of());
        assertEquals(2, allocated.get("lab_keycard").intValue(), "必须被单局全局上限压到 2");

        // 跨区域累计：已经分过 2 张之后，第二个区域一件都拿不到
        Map<String, Integer> second = LootAllocator.allocateGlobalRare(zone, new RaidRandom(2L), pool, allocated);
        assertTrue(second.isEmpty(), "全局上限用尽后不该再分配：" + second);
    }

    @Test
    void globalRareRespectsChance() {
        LootConfig.Item keycard = new LootConfig.Item("lab_keycard", "electronic", LootConfig.Rarity.LEGENDARY,
                2_000, 0, 1, Set.of(), Set.of(), Set.of(), 0);
        LootConfig.Zone never = zone(1_000, 1_000, List.of(),
                List.of(new LootConfig.RareRule("lab_keycard", 0.0D, 1)));
        assertTrue(LootAllocator.allocateGlobalRare(never, new RaidRandom(4L), List.of(keycard), Map.of()).isEmpty(),
                "概率 0 时永远不出");

        LootConfig.Zone always = zone(1_000, 1_000, List.of(),
                List.of(new LootConfig.RareRule("lab_keycard", 1.0D, 1)));
        assertEquals(1, LootAllocator.allocateGlobalRare(always, new RaidRandom(4L), List.of(keycard), Map.of())
                .get("lab_keycard").intValue(), "概率 1 时必出");
    }

    @Test
    void rareQuotaIsConsumedWhereItIsPlaced() {
        LootConfig.Item keycard = new LootConfig.Item("lab_keycard", "electronic", LootConfig.Rarity.LEGENDARY,
                500, 0, 1, Set.of(), Set.of(), Set.of(), 1);
        List<LootConfig.Item> pool = List.of(keycard, item("screw", 50));
        Map<String, Integer> budgets = new java.util.LinkedHashMap<>();
        budgets.put("p1", 1_000);
        budgets.put("p2", 1_000);

        LootAllocator.ZoneAllocation allocation = LootAllocator.allocateZone(zone(2_000, 2_000, List.of(), List.of()),
                budgets, pool, new RaidRandom(6L), Map.of("lab_keycard", 1), Map.of());

        long placed = allocation.allItemIds().stream().filter("lab_keycard"::equals).count();
        assertTrue(placed <= 1, "配额 1 不该放出 2 张：" + placed);
        assertEquals((int) placed, allocation.rareAllocated().getOrDefault("lab_keycard", 0).intValue(),
                "分配结果里记录的稀有物数量应与实际放入的一致");
    }

    // ---------------------------------------------------------------- 配置解析

    @Test
    void itemJsonIsParsedWithDefaultsAndSoftFailures() {
        LootConfig.ParseResult<LootConfig.Item> result = LootConfig.Item.fromJson(JsonParser.parseString("""
                {
                  "item": "qingmo:Encrypted_Hard_Drive",
                  "category": "electronic",
                  "rarity": "rare",
                  "spawn_cost": 1800,
                  "rarity_weight": 8,
                  "allowed_tiers": [2, 3],
                  "allowed_container_types": ["Safe", "computer"],
                  "raid_global_limit": 2
                }
                """).getAsJsonObject());

        assertTrue(result.ok(), () -> "应解析成功：" + result.problems());
        LootConfig.Item parsed = result.value();
        assertEquals("qingmo:encrypted_hard_drive", parsed.itemId(), "id 应统一小写");
        assertEquals(LootConfig.Rarity.RARE, parsed.rarity());
        assertEquals(1800, parsed.spawnCost());
        assertEquals(0, parsed.combatScore(), "未写 combat_score 时默认 0");
        assertEquals(2, parsed.raidGlobalLimit());
        assertTrue(parsed.allowedIn(2, Set.of(), "safe"), "容器类型应大小写不敏感");
        assertFalse(parsed.allowedIn(4, Set.of(), "safe"), "等级不在允许列表内应被拒");
        assertFalse(parsed.allowedIn(2, Set.of(), "crate"), "容器类型不符应被拒");

        assertFalse(LootConfig.Item.fromJson(JsonParser.parseString("{\"category\":\"x\"}").getAsJsonObject()).ok(),
                "缺 item 应失败");
        assertFalse(LootConfig.Item.fromJson(JsonParser.parseString(
                "{\"item\":\"a\",\"spawn_cost\":0}").getAsJsonObject()).ok(),
                "spawn_cost 为 0 应失败（否则会无限填充预算）");
    }

    @Test
    void zoneJsonIsParsedAndValidated() {
        LootConfig.ParseResult<LootConfig.Zone> result = LootConfig.Zone.fromJson(JsonParser.parseString("""
                {
                  "zone": "factory_zone",
                  "tier": 3,
                  "active_containers": {"minimum": 12, "maximum": 18},
                  "active_loose_loot": {"minimum": 8, "maximum": 14},
                  "loot_budget": {"minimum": 13000, "maximum": 17000},
                  "allowed_categories": ["Mechanical", "medical"],
                  "guarantees": [{"category": "medical", "minimum_count": 2}],
                  "rare_items": [{"item": "lab_keycard", "chance": 0.35, "count": 1}]
                }
                """).getAsJsonObject());

        assertTrue(result.ok(), () -> "应解析成功：" + result.problems());
        LootConfig.Zone parsed = result.value();
        assertEquals("factory_zone", parsed.zoneId());
        assertEquals(3, parsed.tier());
        assertEquals(12, parsed.containerCount().min());
        assertEquals(18, parsed.containerCount().max());
        assertEquals(13_000, parsed.budget().min());
        assertEquals(17_000, parsed.budget().max());
        assertEquals(Set.of("mechanical", "medical"), parsed.allowedCategories(), "类别应统一成小写");
        assertEquals(1, parsed.guarantees().size());
        assertTrue(parsed.guarantees().get(0).maxCount() >= parsed.guarantees().get(0).minCount(),
                "最大件数不应小于最小件数");
        assertEquals(1, parsed.rareRules().size());
        assertEquals(0.35D, parsed.rareRules().get(0).chance(), 1.0E-9);

        assertFalse(LootConfig.Zone.fromJson(JsonParser.parseString(
                "{\"zone\":\"x\"}").getAsJsonObject()).ok(), "缺 loot_budget 应失败");
        assertNull(LootConfig.Zone.fromJson(JsonParser.parseString(
                        "{\"zone\":\"x\",\"loot_budget\":{\"minimum\":1,\"maximum\":2}}").getAsJsonObject())
                .value().guarantees().stream().findAny().orElse(null), "没有必出规则时列表应为空");
    }

    @Test
    void rangePicksWithinBoundsAndClamps() {
        LootConfig.IntRange range = LootConfig.IntRange.of(5, 9);
        RaidRandom random = new RaidRandom(1L);
        for (int index = 0; index < 200; index++) {
            int value = range.pick(random);
            assertTrue(value >= 5 && value <= 9, "应落在闭区间内：" + value);
        }
        assertEquals(5, range.clamp(3));
        assertEquals(9, range.clamp(100));
        assertEquals(5, LootConfig.IntRange.of(9, 5).min(), "min/max 写反了应自动交换");
        assertEquals(9, LootConfig.IntRange.of(9, 5).max());
    }

    @Test
    void eligibleFilterKeepsPoolOrder() {
        List<LootConfig.Item> pool = new ArrayList<>(List.of(item("z_last", 10), item("a_first", 10)));
        List<LootConfig.Item> eligible = LootAllocator.filterEligible(pool, null, null);
        assertEquals(List.of("z_last", "a_first"), eligible.stream().map(LootConfig.Item::itemId).toList(),
                "过滤不该改变顺序（顺序影响抽取确定性）");
        assertNotNull(LootAllocator.filterEligible(null, null, null));
        assertTrue(LootAllocator.filterEligible(null, null, null).isEmpty());
    }
}
