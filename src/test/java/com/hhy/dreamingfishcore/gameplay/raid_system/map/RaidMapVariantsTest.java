package com.hhy.dreamingfishcore.gameplay.raid_system.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hhy.dreamingfishcore.gameplay.raid_system.RaidRandom;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 部分随机地图的不变量测试（设计稿 §6）。
 *
 * <p>最要紧的一条：**宁可这局地图没有变化，也不能给玩家一张走不通的图**——
 * 所以"抽不到合格组合就退回无变体"必须要能测出来。</p>
 */
class RaidMapVariantsTest {

    /** 经典布局：residential/road/factory/forest/warehouse/laboratory，road-factory 是必经之路。 */
    private static RaidMapVariants.Graph factoryGraph() {
        return RaidMapVariants.Graph.of(
                "residential-road", "residential-forest", "road-factory",
                "forest-warehouse", "warehouse-factory", "factory-laboratory");
    }

    private static final Set<String> SPAWNS = Set.of("residential", "forest");
    private static final Set<String> EXTRACTIONS = Set.of("laboratory");

    private static RaidMapVariants.Group northEntrance() {
        RaidMapVariants.Variant open = RaidMapVariants.Variant.of("open", 40);
        RaidMapVariants.Variant blocked = new RaidMapVariants.Variant("blocked", 30,
                Set.of("road-factory"), Set.of());
        RaidMapVariants.Variant breached = new RaidMapVariants.Variant("breached", 10,
                Set.of(), Set.of("forest-warehouse"));
        return new RaidMapVariants.Group("factory_north_entrance", 1, List.of(open, blocked, breached));
    }

    @Test
    void selectionIsDeterministicAndWeightedByGroup() {
        List<RaidMapVariants.Group> groups = List.of(northEntrance());
        RaidMapVariants.Selection first = RaidMapVariants.select(groups, new RaidRandom(1L));
        RaidMapVariants.Selection second = RaidMapVariants.select(groups, new RaidRandom(1L));
        assertEquals(first.chosenByGroup(), second.chosenByGroup(), "同种子必须选出同一套变体");
        assertTrue(first.chosenByGroup().containsKey("factory_north_entrance"));
    }

    @Test
    void chooseCountAndZeroWeightAreRespected() {
        RaidMapVariants.Group group = new RaidMapVariants.Group("doors", 2, List.of(
                RaidMapVariants.Variant.of("a", 100),
                RaidMapVariants.Variant.of("b", 100),
                new RaidMapVariants.Variant("never", 0, Set.of("road-factory"), Set.of())));

        RaidMapVariants.Selection selection = RaidMapVariants.select(List.of(group), new RaidRandom(2L));
        assertEquals(1, selection.chosenByGroup().size(), "一组的多个状态只记第一个（choose>1 时仍是一组一个 id）");
        assertFalse(selection.chosenByGroup().containsValue("never"), "权重 0 的状态不该被选中");
        assertTrue(selection.disabledEdges().isEmpty(), "没选到 blocked 就不该关掉任何边");
    }

    @Test
    void disabledEdgeClosesThePassage() {
        RaidMapVariants.Graph graph = factoryGraph();
        RaidMapVariants.Selection onlyRoad = new RaidMapVariants.Selection(
                java.util.Map.of("factory_north_entrance", "blocked"),
                Set.of("road-factory"), Set.of(), List.of());

        // 只关掉 road-factory 还切不断：居民区仍可绕 forest → warehouse → factory
        assertTrue(graph.canReach("residential", Set.of("factory"), onlyRoad),
                "这张图有绕行路线，只关一条边不该切断交通");
        assertTrue(graph.canReach("residential", EXTRACTIONS, onlyRoad));

        // 连 warehouse-factory 一起关掉，才真的到不了工厂与实验室
        RaidMapVariants.Selection bothClosed = new RaidMapVariants.Selection(
                java.util.Map.of("factory_north_entrance", "blocked"),
                Set.of("road-factory", "warehouse-factory"), Set.of(), List.of());
        assertFalse(graph.canReach("residential", Set.of("factory"), bothClosed),
                "两条通往工厂的通道都关掉后，居民区到不了工厂");
        assertFalse(graph.canReach("residential", EXTRACTIONS, bothClosed),
                "也就到不了实验室");
    }

    @Test
    void validationCatchesSpawnWithoutRoute() {
        RaidMapVariants.Graph graph = factoryGraph();
        // 把居民区到公路、居民区到森林都关掉：居民区被封死
        RaidMapVariants.Selection selection = new RaidMapVariants.Selection(
                java.util.Map.of("g", "v"), Set.of("residential-road", "residential-forest"), Set.of(),
                List.of());

        RaidMapVariants.Validation validation = RaidMapVariants.validate(graph, selection, SPAWNS,
                EXTRACTIONS, Set.of());
        assertFalse(validation.valid());
        assertTrue(validation.problems().stream().anyMatch(text -> text.startsWith("SPAWN_NO_ROUTE")),
                validation.problems().toString());
        assertTrue(validation.problems().stream().anyMatch(text -> text.startsWith("ISOLATED_ZONE")),
                "居民区一条通道都不剩，也应被指出：" + validation.problems());
    }

    @Test
    void validationCatchesSealedOffValuableZone() {
        RaidMapVariants.Graph graph = factoryGraph();
        RaidMapVariants.Selection selection = new RaidMapVariants.Selection(
                java.util.Map.of("g", "v"), Set.of("warehouse-factory", "road-factory"), Set.of(),
                List.of());

        RaidMapVariants.Validation validation = RaidMapVariants.validate(graph, selection, SPAWNS,
                Set.of("forest"), Set.of("laboratory"));
        assertFalse(validation.valid());
        assertTrue(validation.problems().stream().anyMatch(text -> text.startsWith("SEALED_OFF")),
                "实验室从任何出生区都到不了：" + validation.problems());
    }

    @Test
    void validationPassesOnAHealthySelection() {
        RaidMapVariants.Validation validation = RaidMapVariants.validate(factoryGraph(),
                RaidMapVariants.Selection.empty(), SPAWNS, EXTRACTIONS, Set.of("factory"));
        assertTrue(validation.valid(), validation.problems().toString());
    }

    @Test
    void selectValidRetriesUntilAValidCombinationIsFound() {
        // 两个组：随便怎么选都不会把图弄断
        List<RaidMapVariants.Group> groups = List.of(
                new RaidMapVariants.Group("power", 1, List.of(
                        RaidMapVariants.Variant.of("on", 50), RaidMapVariants.Variant.of("off", 50))),
                new RaidMapVariants.Group("weather", 1, List.of(
                        RaidMapVariants.Variant.of("fog", 50), RaidMapVariants.Variant.of("clear", 50))));

        RaidMapVariants.Outcome outcome = RaidMapVariants.selectValid(groups, factoryGraph(), SPAWNS,
                EXTRACTIONS, Set.of(), new RaidRandom(3L), 20);
        assertTrue(outcome.validation().valid(), outcome.validation().problems().toString());
        assertFalse(outcome.usedFallback());
        assertEquals(2, outcome.selection().chosenByGroup().size());
    }

    @Test
    void alwaysBlockingVariantFallsBackToNoVariants() {
        // 这个组无论选哪个都会把通往工厂的两条路都关掉 → 校验必然失败 → 退回无变体
        List<RaidMapVariants.Group> groups = List.of(
                new RaidMapVariants.Group("factory_north_entrance", 1, List.of(
                        new RaidMapVariants.Variant("blocked_a", 50,
                                Set.of("road-factory", "warehouse-factory"), Set.of()),
                        new RaidMapVariants.Variant("blocked_b", 50,
                                Set.of("road-factory", "warehouse-factory"), Set.of()))));

        RaidMapVariants.Outcome outcome = RaidMapVariants.selectValid(groups, factoryGraph(), SPAWNS,
                EXTRACTIONS, Set.of(), new RaidRandom(4L), 5);

        assertTrue(outcome.usedFallback(), "抽不到合格组合就该退回无变体");
        assertTrue(outcome.selection().isEmpty(), "退回后不该留下任何变体");
        assertTrue(outcome.validation().valid(), "基础图本身应当是通的");
        assertTrue(outcome.selection().notes().stream().anyMatch(note -> note.contains("退回无变体")),
                outcome.selection().notes().toString());
    }

    @Test
    void emptyOrBrokenInputsDoNotCrash() {
        assertTrue(RaidMapVariants.select(null, new RaidRandom(1L)).isEmpty());
        assertTrue(RaidMapVariants.select(List.of(), new RaidRandom(1L)).isEmpty());

        RaidMapVariants.Graph emptyGraph = RaidMapVariants.Graph.of();
        RaidMapVariants.Validation validation = RaidMapVariants.validate(emptyGraph,
                RaidMapVariants.Selection.empty(), SPAWNS, EXTRACTIONS, Set.of());
        assertFalse(validation.valid(), "空图不可能通过校验");
        assertTrue(RaidMapVariants.validate(null, null, SPAWNS, EXTRACTIONS, Set.of())
                .problems().contains("没有连通图"));
    }

    @Test
    void edgeKeysAreNormalizedAndOrderIndependent() {
        assertEquals("a|b", RaidMapVariants.edgeKey("a-b"));
        assertEquals("a|b", RaidMapVariants.edgeKey("b-a"), "正反写应归一成同一个键");
        assertEquals("dash-format-problem|no", RaidMapVariants.edgeKey("no-dash-format-problem"),
                "从第一个连字符拆——所以区域名里不要用连字符（这条规则写在类注释里）");
        assertEquals(null, RaidMapVariants.edgeKey("nodash"), "没有连字符就不是合法边");
        assertEquals(null, RaidMapVariants.edgeKey("-broken"));
        assertEquals(null, RaidMapVariants.edgeKey(null));

        RaidMapVariants.Variant variant = new RaidMapVariants.Variant("v", 1,
                Set.of("road-factory", "factory-road", "bad"), Set.of());
        assertEquals(Set.of("factory|road"), variant.disabledEdges(),
                "正反写法应去重成一个键，格式不对的丢掉");
    }

    @Test
    void describeListsChoiceAndProblems() {
        RaidMapVariants.Outcome outcome = RaidMapVariants.selectValid(List.of(northEntrance()),
                factoryGraph(), SPAWNS, EXTRACTIONS, Set.of(), new RaidRandom(5L), 3);
        List<String> lines = RaidMapVariants.describe(outcome);
        assertTrue(lines.get(0).contains("地图变体"), lines.get(0));
        assertTrue(lines.size() >= 1);
    }
}
