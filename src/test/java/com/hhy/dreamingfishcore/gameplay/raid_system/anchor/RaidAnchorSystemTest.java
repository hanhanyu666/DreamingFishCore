package com.hhy.dreamingfishcore.gameplay.raid_system.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 锚点系统纯逻辑测试：解析、默认值、两层合并、软失败、确定性排序。
 *
 * <p>这一层刻意不碰 Minecraft，所以能飞快地跑；游戏侧只负责把任务地点包成
 * {@link RaidAnchorCatalog.ZoneLookup} 并把定义/覆盖层喂进来。</p>
 */
class RaidAnchorSystemTest {

    /** 测试用区域：一个简单的盒子，可切换"存在与否"。 */
    private record BoxZone(String id, double minX, double minY, double minZ,
                           double maxX, double maxY, double maxZ, boolean exists)
            implements RaidAnchorCatalog.ZoneLookup {

        @Override
        public boolean exists(String zoneId) {
            return exists && id.equals(zoneId);
        }

        @Override
        public boolean contains(String zoneId, double x, double y, double z) {
            return exists(zoneId)
                    && x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
    }

    private static final RaidAnchorCatalog.ZoneLookup FACTORY =
            new BoxZone("factory", 100, 60, -100, 200, 80, 0, true);

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    // ------------------------------------------------------------------ 解析

    @Test
    void parsesAllFieldsFromFullJson() {
        RaidAnchor.ParseResult result = RaidAnchor.fromJson(json("""
                {
                  "id": "factory_office_desk_01",
                  "type": "LOOSE_LOOT",
                  "zone": "factory",
                  "position": [124.5, 67.02, -83.4],
                  "rotation": [0, 135, 0],
                  "group": "office_tables",
                  "tags": ["electronic", "civilian"],
                  "weight": 80,
                  "enabled": false,
                  "quality_multiplier": 1.2
                }
                """), RaidAnchor.Source.DEFINITION);

        assertTrue(result.ok(), () -> "应解析成功，问题：" + result.problems());
        RaidAnchor anchor = result.anchor();
        assertEquals("factory_office_desk_01", anchor.id());
        assertEquals(RaidAnchorType.LOOSE_LOOT, anchor.type());
        assertEquals("factory", anchor.zone());
        assertEquals(124.5, anchor.x());
        assertEquals(67.02, anchor.y(), 1.0E-6);
        assertEquals(-83.4, anchor.z(), 1.0E-6);
        assertEquals(135.0F, anchor.rotation().y());
        assertEquals("office_tables", anchor.group());
        assertEquals(List.of("electronic", "civilian"), anchor.tags());
        assertEquals(80, anchor.weight());
        assertFalse(anchor.enabled());
        assertEquals(1.2, anchor.qualityMultiplier(), 1.0E-9);
        assertEquals(RaidAnchor.Source.DEFINITION, anchor.source());
    }

    @Test
    void minimalJsonFallsBackToDefaults() {
        RaidAnchor.ParseResult result = RaidAnchor.fromJson(json("""
                {"id": "a1", "type": "extraction", "zone": "factory", "position": [1, 2, 3]}
                """), RaidAnchor.Source.DEFINITION);

        assertTrue(result.ok(), () -> "应解析成功，问题：" + result.problems());
        RaidAnchor anchor = result.anchor();
        assertEquals(RaidAnchorType.EXTRACTION, anchor.type(), "type 应大小写不敏感");
        assertEquals(RaidAnchor.DEFAULT_WEIGHT, anchor.weight());
        assertEquals(RaidAnchor.DEFAULT_QUALITY_MULTIPLIER, anchor.qualityMultiplier(), 1.0E-9);
        assertTrue(anchor.enabled());
        assertEquals(RaidAnchor.Rotation.NONE, anchor.rotation());
        assertTrue(anchor.tags().isEmpty());
        assertEquals("", anchor.group());
        assertTrue(result.problems().isEmpty(), "默认值不应产生问题");
    }

    @Test
    void lowercaseAndTrimId() {
        RaidAnchor.ParseResult result = RaidAnchor.fromJson(json("""
                {"id": "  Factory_Door_01  ", "type": "DOOR", "zone": "factory", "position": [1, 2, 3]}
                """), RaidAnchor.Source.DEFINITION);
        assertTrue(result.ok());
        assertEquals("factory_door_01", result.anchor().id(), "id 应统一成小写并去空白");
    }

    @Test
    void unknownTypeIsASoftFailureForThatAnchorOnly() {
        RaidAnchor.ParseResult result = RaidAnchor.fromJson(json("""
                {"id": "a1", "type": "NOT_A_TYPE", "zone": "factory", "position": [1, 2, 3]}
                """), RaidAnchor.Source.DEFINITION);
        assertFalse(result.ok());
        assertNull(result.anchor());
        assertTrue(result.problems().stream().anyMatch(text -> text.contains("type")),
                () -> "问题里应说明类型非法：" + result.problems());
    }

    @Test
    void missingRequiredFieldsFail() {
        assertFalse(RaidAnchor.fromJson(json("""
                {"type": "DOOR", "zone": "factory", "position": [1, 2, 3]}
                """), RaidAnchor.Source.DEFINITION).ok(), "缺 id 应失败");
        assertFalse(RaidAnchor.fromJson(json("""
                {"id": "a1", "zone": "factory", "position": [1, 2, 3]}
                """), RaidAnchor.Source.DEFINITION).ok(), "缺 type 应失败");
        assertFalse(RaidAnchor.fromJson(json("""
                {"id": "a1", "type": "DOOR", "position": [1, 2, 3]}
                """), RaidAnchor.Source.DEFINITION).ok(), "缺 zone 应失败");
        assertFalse(RaidAnchor.fromJson(json("""
                {"id": "a1", "type": "DOOR", "zone": "factory"}
                """), RaidAnchor.Source.DEFINITION).ok(), "缺 position 应失败");
    }

    @Test
    void illegalIdIsRejected() {
        RaidAnchor.ParseResult result = RaidAnchor.fromJson(json("""
                {"id": "bad id!", "type": "DOOR", "zone": "factory", "position": [1, 2, 3]}
                """), RaidAnchor.Source.DEFINITION);
        assertFalse(result.ok());
        assertTrue(result.problems().get(0).contains("id"), () -> "应报 id 不合法：" + result.problems());
    }

    @Test
    void nonFinitePositionIsRejected() {
        // JSON 里放不下 NaN 字面量，用超大指数模拟无穷大
        RaidAnchor.ParseResult result = RaidAnchor.fromJson(json("""
                {"id": "a1", "type": "DOOR", "zone": "factory", "position": [1e400, 2, 3]}
                """), RaidAnchor.Source.DEFINITION);
        assertFalse(result.ok(), "无穷大坐标应被拒绝");
    }

    @Test
    void wrongArityPositionIsRejected() {
        assertFalse(RaidAnchor.fromJson(json("""
                {"id": "a1", "type": "DOOR", "zone": "factory", "position": [1, 2]}
                """), RaidAnchor.Source.DEFINITION).ok(), "position 少于 3 项应失败");
    }

    @Test
    void badOptionalFieldsFallBackWithProblems() {
        RaidAnchor.ParseResult result = RaidAnchor.fromJson(json("""
                {
                  "id": "a1", "type": "DOOR", "zone": "factory", "position": [1, 2, 3],
                  "weight": 0, "enabled": "yes", "quality_multiplier": -1, "tags": "electronic"
                }
                """), RaidAnchor.Source.DEFINITION);

        assertTrue(result.ok(), "可选字段坏掉不应让整个锚点失败");
        RaidAnchor anchor = result.anchor();
        assertEquals(RaidAnchor.DEFAULT_WEIGHT, anchor.weight());
        assertTrue(anchor.enabled());
        assertEquals(RaidAnchor.DEFAULT_QUALITY_MULTIPLIER, anchor.qualityMultiplier(), 1.0E-9);
        assertTrue(anchor.tags().isEmpty());
        assertEquals(4, result.problems().size(), () -> "应有三条问题：" + result.problems());
    }

    @Test
    void duplicateTagsAreDeduplicated() {
        RaidAnchor.ParseResult result = RaidAnchor.fromJson(json("""
                {"id": "a1", "type": "DOOR", "zone": "factory", "position": [1, 2, 3],
                 "tags": ["metal", "metal", " ", "door"]}
                """), RaidAnchor.Source.DEFINITION);
        assertTrue(result.ok());
        assertEquals(List.of("metal", "door"), result.anchor().tags());
    }

    @Test
    void jsonRoundTripKeepsEveryField() {
        RaidAnchor original = RaidAnchor.fromJson(json("""
                {
                  "id": "a1", "type": "CONTAINER_LOOT", "zone": "factory", "position": [1.5, 2.25, -3.75],
                  "rotation": [10, 20, 30], "group": "g1", "tags": ["t1", "t2"],
                  "weight": 7, "enabled": false, "quality_multiplier": 2.5
                }
                """), RaidAnchor.Source.OVERLAY).anchor();
        assertNotNull(original);

        RaidAnchor reparsed = RaidAnchor.fromJson(original.toJson(), RaidAnchor.Source.DEFINITION).anchor();
        assertNotNull(reparsed);
        assertEquals(original.id(), reparsed.id());
        assertEquals(original.type(), reparsed.type());
        assertEquals(original.zone(), reparsed.zone());
        assertEquals(original.x(), reparsed.x());
        assertEquals(original.y(), reparsed.y());
        assertEquals(original.z(), reparsed.z());
        assertEquals(original.rotation(), reparsed.rotation());
        assertEquals(original.group(), reparsed.group());
        assertEquals(original.tags(), reparsed.tags());
        assertEquals(original.weight(), reparsed.weight());
        assertEquals(original.enabled(), reparsed.enabled());
        assertEquals(original.qualityMultiplier(), reparsed.qualityMultiplier(), 1.0E-9);
    }

    @Test
    void blockCoordinatesFloorTowardsNegativeInfinity() {
        RaidAnchor anchor = RaidAnchor.fromJson(json("""
                {"id": "a1", "type": "LOOSE_LOOT", "zone": "factory", "position": [-83.4, 67.99, 12.0]}
                """), RaidAnchor.Source.DEFINITION).anchor();
        assertNotNull(anchor);
        assertEquals(-84, anchor.blockX(), "负坐标应向下取整，避免露天物品偏一格");
        assertEquals(67, anchor.blockY());
        assertEquals(12, anchor.blockZ());
    }

    // ------------------------------------------------------------------ 覆盖层

    @Test
    void overlayJsonRoundTrip() {
        RaidAnchorOverlay overlay = RaidAnchorOverlay.empty()
                .withPatch(RaidAnchor.fromJson(json("""
                        {"id": "a1", "type": "DOOR", "zone": "factory", "position": [1, 2, 3]}
                        """), RaidAnchor.Source.OVERLAY).anchor())
                .withRemoved("a2")
                .withDisabled("a3", true);

        List<String> problems = new ArrayList<>();
        RaidAnchorOverlay reparsed = RaidAnchorOverlay.fromJson(overlay.toJson(), problems);
        assertTrue(problems.isEmpty(), () -> "不应有问题：" + problems);
        assertEquals(overlay.patches().keySet(), reparsed.patches().keySet());
        assertEquals(overlay.removed(), reparsed.removed());
        assertEquals(overlay.disabled(), reparsed.disabled());
    }

    @Test
    void removingAnAnchorAlsoDropsItsPatchAndDisabledFlag() {
        RaidAnchorOverlay overlay = RaidAnchorOverlay.empty()
                .withPatch(RaidAnchor.fromJson(json("""
                        {"id": "a1", "type": "DOOR", "zone": "factory", "position": [1, 2, 3]}
                        """), RaidAnchor.Source.OVERLAY).anchor())
                .withDisabled("a1", true)
                .withRemoved("a1");

        assertTrue(overlay.patches().isEmpty(), "删除后不应残留 patch");
        assertFalse(overlay.disabled().contains("a1"), "删除后不应残留禁用标记");
        assertTrue(overlay.removed().contains("a1"));
    }

    // ------------------------------------------------------------------ 合并与校验

    private static RaidAnchor anchor(String id, RaidAnchorType type, String zone,
                                     double x, double y, double z) {
        return new RaidAnchor(id, type, zone, x, y, z, RaidAnchor.Rotation.NONE, "", List.of(),
                RaidAnchor.DEFAULT_WEIGHT, true, RaidAnchor.DEFAULT_QUALITY_MULTIPLIER,
                RaidAnchor.Source.DEFINITION);
    }

    @Test
    void overlayWinsOverDefinitionLayer() {
        RaidAnchorCatalog catalog = RaidAnchorCatalog.build(
                List.of(anchor("a1", RaidAnchorType.DOOR, "factory", 110, 65, -50)),
                RaidAnchorOverlay.empty().withPatch(anchor("a1", RaidAnchorType.EXTRACTION, "factory", 150, 70, -10)),
                FACTORY);

        assertEquals(1, catalog.anchors().size());
        RaidAnchor merged = catalog.anchors().get(0);
        assertEquals(RaidAnchorType.EXTRACTION, merged.type(), "世界层应覆盖定义层");
        assertEquals(150.0, merged.x());
        assertEquals(RaidAnchor.Source.OVERLAY, merged.source());
    }

    @Test
    void overlayCanAddAnAnchorThatIsNotInTheDefinitionLayer() {
        RaidAnchorCatalog catalog = RaidAnchorCatalog.build(
                List.of(),
                RaidAnchorOverlay.empty().withPatch(anchor("added", RaidAnchorType.EVENT, "factory", 120, 65, -20)),
                FACTORY);

        assertEquals(1, catalog.anchors().size());
        assertEquals("added", catalog.anchors().get(0).id());
        assertEquals(RaidAnchor.Source.OVERLAY, catalog.anchors().get(0).source());
        assertTrue(catalog.problems().isEmpty());
    }

    @Test
    void removedAndDisabledAreApplied() {
        RaidAnchorCatalog catalog = RaidAnchorCatalog.build(
                List.of(anchor("keep", RaidAnchorType.DOOR, "factory", 110, 65, -50),
                        anchor("gone", RaidAnchorType.DOOR, "factory", 111, 65, -50),
                        anchor("off", RaidAnchorType.DOOR, "factory", 112, 65, -50)),
                RaidAnchorOverlay.empty().withRemoved("gone").withDisabled("off", true),
                FACTORY);

        assertEquals(2, catalog.anchors().size());
        assertEquals(List.of("keep", "off"), catalog.anchors().stream().map(RaidAnchor::id).toList());
        assertFalse(catalog.byId("off").orElseThrow().enabled(), "被禁用的锚点应保留但标记为禁用");
        assertTrue(catalog.byId("gone").isEmpty());
        assertTrue(catalog.problems().isEmpty(), "删除与禁用是正常操作，不该报问题");
    }

    @Test
    void unknownIdsInOverlayAreReportedButNotFatal() {
        RaidAnchorCatalog catalog = RaidAnchorCatalog.build(
                List.of(anchor("a1", RaidAnchorType.DOOR, "factory", 110, 65, -50)),
                RaidAnchorOverlay.empty().withRemoved("nope").withDisabled("also_nope", true),
                FACTORY);

        assertEquals(1, catalog.anchors().size());
        assertEquals(2, catalog.problems().size(), () -> "应各报一条：" + catalog.problems());
        assertTrue(catalog.problems().stream().anyMatch(problem -> problem.code().equals("REMOVE_UNKNOWN_ID")));
        assertTrue(catalog.problems().stream().anyMatch(problem -> problem.code().equals("DISABLE_UNKNOWN_ID")));
    }

    @Test
    void duplicateIdKeepsOnlyOneAndReportsIt() {
        RaidAnchorCatalog catalog = RaidAnchorCatalog.build(
                List.of(anchor("dup", RaidAnchorType.DOOR, "factory", 110, 65, -50),
                        anchor("dup", RaidAnchorType.EXTRACTION, "factory", 120, 65, -50)),
                RaidAnchorOverlay.empty(),
                FACTORY);

        assertEquals(1, catalog.anchors().size());
        assertEquals(1, catalog.problems().size());
        assertEquals("DUPLICATE_ID", catalog.problems().get(0).code());
    }

    @Test
    void unknownZoneIsSkippedInsteadOfCrashing() {
        RaidAnchorCatalog catalog = RaidAnchorCatalog.build(
                List.of(anchor("a1", RaidAnchorType.DOOR, "factory", 110, 65, -50),
                        anchor("a2", RaidAnchorType.DOOR, "ghost_zone", 110, 65, -50)),
                RaidAnchorOverlay.empty(),
                FACTORY);

        assertEquals(1, catalog.anchors().size(), "区域不存在的锚点应被跳过");
        assertEquals("a1", catalog.anchors().get(0).id());
        assertEquals("UNKNOWN_ZONE", catalog.problems().get(0).code());
    }

    @Test
    void outOfZonePositionIsSkipped() {
        RaidAnchorCatalog catalog = RaidAnchorCatalog.build(
                List.of(anchor("inside", RaidAnchorType.DOOR, "factory", 110, 65, -50),
                        anchor("outside", RaidAnchorType.DOOR, "factory", 999, 65, -50)),
                RaidAnchorOverlay.empty(),
                FACTORY);

        assertEquals(1, catalog.anchors().size());
        assertEquals("OUTSIDE_ZONE", catalog.problems().get(0).code());
        assertTrue(catalog.problems().get(0).message().contains("999"),
                () -> "问题信息应带上具体坐标：" + catalog.problems().get(0).message());
    }

    @Test
    void badWeightAndQualityMultiplierAreRejected() {
        RaidAnchor badWeight = new RaidAnchor("bad_weight", RaidAnchorType.DOOR, "factory",
                110, 65, -50, RaidAnchor.Rotation.NONE, "", List.of(), 0, true, 1.0,
                RaidAnchor.Source.DEFINITION);
        RaidAnchor badQuality = new RaidAnchor("bad_quality", RaidAnchorType.DOOR, "factory",
                110, 65, -50, RaidAnchor.Rotation.NONE, "", List.of(), 10, true, 0.0,
                RaidAnchor.Source.DEFINITION);
        RaidAnchor fine = anchor("fine", RaidAnchorType.DOOR, "factory", 110, 65, -50);

        RaidAnchorCatalog catalog = RaidAnchorCatalog.build(List.of(badWeight, badQuality, fine),
                RaidAnchorOverlay.empty(), FACTORY);

        assertEquals(List.of("fine"), catalog.anchors().stream().map(RaidAnchor::id).toList());
        assertTrue(catalog.problems().stream().anyMatch(problem -> problem.code().equals("BAD_WEIGHT")));
        assertTrue(catalog.problems().stream()
                .anyMatch(problem -> problem.code().equals("BAD_QUALITY_MULTIPLIER")));
    }

    @Test
    void catalogHasNoZoneChecksWhenLookupIsNull() {
        RaidAnchorCatalog catalog = RaidAnchorCatalog.build(
                List.of(anchor("a1", RaidAnchorType.DOOR, "whatever", 110, 65, -50)),
                RaidAnchorOverlay.empty(), null);
        assertEquals(1, catalog.anchors().size());
        assertTrue(catalog.problems().isEmpty());
    }

    @Test
    void orderIsDeterministicRegardlessOfInputOrder() {
        List<RaidAnchor> anchors = new ArrayList<>(List.of(
                anchor("z_last", RaidAnchorType.DOOR, "factory", 110, 65, -50),
                anchor("a_first", RaidAnchorType.DOOR, "factory", 111, 65, -50),
                anchor("m_middle", RaidAnchorType.EVENT, "factory", 112, 65, -50)));

        List<String> expected = List.of("a_first", "m_middle", "z_last");
        assertEquals(expected, RaidAnchorCatalog.build(anchors, RaidAnchorOverlay.empty(), FACTORY)
                .anchors().stream().map(RaidAnchor::id).toList());

        Collections.reverse(anchors);
        assertEquals(expected, RaidAnchorCatalog.build(anchors, RaidAnchorOverlay.empty(), FACTORY)
                .anchors().stream().map(RaidAnchor::id).toList(), "颠倒输入顺序后结果必须完全相同");
    }

    @Test
    void queriesAndCountsWork() {
        RaidAnchorCatalog catalog = RaidAnchorCatalog.build(List.of(
                anchor("a1", RaidAnchorType.CONTAINER_LOOT, "factory", 110, 65, -50),
                anchor("a2", RaidAnchorType.CONTAINER_LOOT, "factory", 111, 65, -50),
                anchor("a3", RaidAnchorType.LOOSE_LOOT, "factory", 112, 65, -50)),
                RaidAnchorOverlay.empty(), FACTORY);

        assertEquals(2, catalog.byType(RaidAnchorType.CONTAINER_LOOT).size());
        assertEquals(3, catalog.byZone("factory").size());
        assertEquals(2, catalog.countByType().get("CONTAINER_LOOT"));
        assertEquals(1, catalog.countByType().get("LOOSE_LOOT"));
        assertEquals(Optional.of("a1"), catalog.byId("a1").map(RaidAnchor::id));
        assertTrue(catalog.byId("missing").isEmpty());
        assertTrue(catalog.ok(), () -> "不应有问题：" + catalog.problems());
    }

    @Test
    void typeParseIsLenientAndNamesAreStable() {
        assertEquals(Optional.of(RaidAnchorType.PLAYER_SPAWN), RaidAnchorType.parse(" player_spawn "));
        assertTrue(RaidAnchorType.parse("nope").isEmpty());
        assertTrue(RaidAnchorType.parse(null).isEmpty());
        assertEquals(RaidAnchorType.values().length, RaidAnchorType.names().length);
        assertEquals("PLAYER_SPAWN", RaidAnchorType.names()[0]);
    }
}
