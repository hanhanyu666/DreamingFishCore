package com.hhy.dreamingfishcore.gameplay.raid_system.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hhy.dreamingfishcore.gameplay.raid_system.RaidRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 露天物品静态节点的不变量测试。
 *
 * <p>重点：点位与内容分离、每个点的成本上限（露天物品是随手能捡的，不是保险柜）、
 * 稀有物与容器共用全局配额、同种子同结果，以及拾取判定的幂等与拒绝理由。</p>
 */
class LooseLootPlannerTest {

    private static LootConfig.Item item(String id, int cost, String category, int weight) {
        return new LootConfig.Item(id, category, LootConfig.Rarity.COMMON, cost, 0, weight,
                Set.of(), Set.of(), Set.of(), 0);
    }

    private static LooseLootPlanner.Spot spot(String id, double x, double y, double z, Set<String> tags) {
        return new LooseLootPlanner.Spot(id, "desk", 100, x, y, z, 90.0F, 1.0D, tags);
    }

    private static final List<LootConfig.Item> POOL = List.of(
            item("minecraft:paper", 40, "document", 150),
            item("minecraft:redstone", 90, "electronic", 120),
            item("minecraft:gold_ingot", 400, "valuable", 40),
            item("minecraft:diamond", 1500, "valuable", 5));

    @Test
    void plansOneNodePerSpotWithinTheCap() {
        List<LooseLootPlanner.Spot> spots = List.of(
                spot("a1", 10, 64, 10, Set.of()),
                spot("a2", 12, 64, 10, Set.of()),
                spot("a3", 14, 64, 10, Set.of()));

        LooseLootPlanner.Plan plan = LooseLootPlanner.plan(spots, POOL, new RaidRandom(1L), Map.of(), 2);

        assertEquals(2, plan.itemCount(), "maxNodes 限制应生效");
        assertTrue(plan.nodes().stream().allMatch(node -> POOL.stream()
                        .anyMatch(item -> item.itemId().equals(node.itemId()))),
                "物品必须来自池子");
    }

    @Test
    void defaultValueCapKeepsLooseLootCheap() {
        // 默认上限 600：1500 的钻石不该出现在露天物品点上
        List<LooseLootPlanner.Spot> spots = List.of(spot("a1", 0, 64, 0, Set.of()));
        for (long seed = 0; seed < 50; seed++) {
            LooseLootPlanner.Plan plan = LooseLootPlanner.plan(spots, POOL, new RaidRandom(seed), Map.of(), 1);
            plan.nodes().forEach(node -> assertTrue(node.value() <= 600,
                    "露天物品成本不该超过默认上限，实际 " + node.itemId() + "=" + node.value()));
        }
    }

    @Test
    void valueTagOverridesTheCap() {
        LooseLootPlanner.Spot rich = spot("a1", 0, 64, 0, Set.of("value:2000"));
        assertEquals(2000, rich.valueCap(), "value: 标签应覆盖默认上限");

        LooseLootPlanner.Spot bad = spot("a2", 0, 64, 0, Set.of("value:abc"));
        assertEquals(LooseLootPlanner.DEFAULT_VALUE_CAP, bad.valueCap(), "标签写坏就用默认值，而不是让点不可用");
    }

    @Test
    void categoryTagsRestrictWhatCanAppear() {
        LooseLootPlanner.Spot documentsOnly = spot("a1", 0, 64, 0, Set.of("loot:document"));
        assertEquals(Set.of("document"), documentsOnly.categories());
        assertTrue(documentsOnly.allows("document"));
        assertFalse(documentsOnly.allows("valuable"));

        LooseLootPlanner.Plan plan = LooseLootPlanner.plan(List.of(documentsOnly), POOL,
                new RaidRandom(2L), Map.of(), 1);
        assertEquals(1, plan.itemCount());
        assertEquals("minecraft:paper", plan.nodes().get(0).itemId(), "只允许 document 类别时只能出纸");
    }

    @Test
    void multipleCategoryTagsAreUnioned() {
        LooseLootPlanner.Spot mixed = spot("a1", 0, 64, 0,
                Set.of("loot:document", "loot:electronic", "value:150"));
        assertEquals(Set.of("document", "electronic"), mixed.categories());
        for (long seed = 0; seed < 30; seed++) {
            LooseLootPlanner.Plan plan = LooseLootPlanner.plan(List.of(mixed), POOL,
                    new RaidRandom(seed), Map.of(), 1);
            plan.nodes().forEach(node -> assertTrue(
                    node.itemId().equals("minecraft:paper") || node.itemId().equals("minecraft:redstone"),
                    "只该出这两个类别的物品，实际 " + node.itemId()));
        }
    }

    @Test
    void globalRareQuotaIsSharedWithContainers() {
        LootConfig.Item diamond = new LootConfig.Item("minecraft:diamond", "valuable",
                LootConfig.Rarity.RARE, 500, 0, 100, Set.of(), Set.of(), Set.of(), 1);
        List<LootConfig.Item> pool = List.of(item("minecraft:paper", 40, "document", 100), diamond);
        List<LooseLootPlanner.Spot> spots = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            spots.add(spot("a" + index, index, 64, 0, Set.of("value:1000")));
        }

        LooseLootPlanner.Plan plan = LooseLootPlanner.plan(spots, pool, new RaidRandom(3L),
                Map.of("minecraft:diamond", 1), 10);

        long diamonds = plan.nodes().stream().filter(node -> node.itemId().equals("minecraft:diamond")).count();
        assertTrue(diamonds <= 1, "全局上限 1 不该放出 " + diamonds + " 颗钻石");
        assertEquals((int) diamonds, plan.rareUsed().getOrDefault("minecraft:diamond", 0),
                "rareUsed 记账应与实际放入一致");

        // 配额已经用尽（传 0）时一颗都不该出
        LooseLootPlanner.Plan none = LooseLootPlanner.plan(spots, pool, new RaidRandom(3L),
                Map.of("minecraft:diamond", 0), 10);
        assertEquals(0, none.nodes().stream().filter(node -> node.itemId().equals("minecraft:diamond")).count());
    }

    @Test
    void planIsDeterministicForTheSameSeed() {
        List<LooseLootPlanner.Spot> spots = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            spots.add(spot("a" + index, index, 64, index, Set.of()));
        }
        List<String> first = LooseLootPlanner.plan(spots, POOL, new RaidRandom(7L), Map.of(), 5)
                .nodes().stream().map(LooseLootPlanner.Node::itemId).toList();
        List<String> second = LooseLootPlanner.plan(spots, POOL, new RaidRandom(7L), Map.of(), 5)
                .nodes().stream().map(LooseLootPlanner.Node::itemId).toList();
        assertEquals(first, second, "同种子必须得到完全相同的节点清单");
    }

    @Test
    void emptyInputsProduceAnEmptyPlanInsteadOfCrashing() {
        assertTrue(LooseLootPlanner.plan(List.of(), POOL, new RaidRandom(1L), Map.of(), 5).nodes().isEmpty());
        assertTrue(LooseLootPlanner.plan(null, POOL, new RaidRandom(1L), Map.of(), 5).nodes().isEmpty());
        assertTrue(LooseLootPlanner.plan(List.of(spot("a1", 0, 64, 0, Set.of())), List.of(),
                new RaidRandom(1L), Map.of(), 5).nodes().isEmpty());
        assertTrue(LooseLootPlanner.plan(List.of(spot("a1", 0, 64, 0, Set.of())), POOL,
                new RaidRandom(1L), Map.of(), 0).nodes().isEmpty(), "maxNodes=0 时应什么也不生成");
    }

    @Test
    void zeroWeightSpotsAreSkipped() {
        LooseLootPlanner.Spot zero = new LooseLootPlanner.Spot("a1", "g", 0, 0, 64, 0, 0.0F, 1.0D, Set.of());
        assertTrue(LooseLootPlanner.plan(List.of(zero), POOL, new RaidRandom(1L), Map.of(), 1).nodes().isEmpty());
    }

    @Test
    void pickupIsDeniedWithAReasonAndIsIdempotent() {
        assertTrue(LooseLootPlanner.canPickup(false, 1.0D, false, false).allowed());

        LooseLootPlanner.PickupCheck picked = LooseLootPlanner.canPickup(true, 1.0D, false, false);
        assertFalse(picked.allowed(), "已经被拿走的物品不该再能拿");
        assertTrue(picked.reason().contains("已经被拿走"), picked.reason());

        assertFalse(LooseLootPlanner.canPickup(false, 10.0D, false, false).allowed(), "太远应拒绝");
        assertTrue(LooseLootPlanner.canPickup(false, 10.0D, false, false).reason().contains("距离"));
        assertFalse(LooseLootPlanner.canPickup(false, 1.0D, true, false).allowed(), "别人正在拾取应拒绝");
        assertFalse(LooseLootPlanner.canPickup(false, 1.0D, false, true).allowed(), "背包满应拒绝");
        assertTrue(LooseLootPlanner.canPickup(false, LooseLootPlanner.MAX_PICKUP_DISTANCE, false, false).allowed(),
                "正好在最大距离上应允许（边界包含）");
    }

    @Test
    void describeListsNodesWithPositions() {
        LooseLootPlanner.Plan plan = LooseLootPlanner.plan(
                List.of(spot("desk_01", 10.5D, 64.0D, -3.5D, Set.of())),
                List.of(item("minecraft:paper", 40, "document", 100)),
                new RaidRandom(1L), Map.of(), 1);
        List<String> lines = LooseLootPlanner.describe(plan);
        assertTrue(lines.get(0).contains("1 个"), lines.get(0));
        assertTrue(lines.get(1).contains("desk_01"), lines.get(1));
        assertTrue(lines.get(1).contains("minecraft:paper"), lines.get(1));
    }
}
