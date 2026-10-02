package com.hhy.dreamingfishcore.gameplay.clue_system;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发放入口的声明解析与索引（里程碑 2 的六种入口）。
 *
 * <p>这里锁三件事：六种前缀都能解析、写错的声明只丢掉自己（不拖垮别的入口）、
 * 索引能按"入口 + 键"反查出该发哪几条线索。</p>
 */
class ClueGrantSourceTest {

    private static final String CLUE_A = "dreamingfishcore:clue/alpha";
    private static final String CLUE_B = "dreamingfishcore:clue/beta";

    @AfterEach
    void tearDown() {
        ClueCatalog.resetForTest();
    }

    // ==================== 前缀 ====================

    @Test
    void allSixTokensResolveToTheirType() {
        assertEquals(ClueSourceType.BLOCK, ClueSourceType.fromToken("block"));
        assertEquals(ClueSourceType.CONTAINER, ClueSourceType.fromToken("container"));
        assertEquals(ClueSourceType.NPC, ClueSourceType.fromToken("npc"));
        assertEquals(ClueSourceType.AREA, ClueSourceType.fromToken("area"));
        assertEquals(ClueSourceType.BROADCAST, ClueSourceType.fromToken("broadcast"));
        assertEquals(ClueSourceType.EVENT, ClueSourceType.fromToken("event"));

        // 服主手写时大小写和空格都不该成为坑。
        assertEquals(ClueSourceType.BLOCK, ClueSourceType.fromToken(" BLOCK "));
        assertNull(ClueSourceType.fromToken("chest"), "没登记的前缀应当解析不出来");
        assertNull(ClueSourceType.fromToken(null));
    }

    // ==================== 声明解析 ====================

    @Test
    void declarationsParseForEveryType() {
        assertEquals("minecraft:chest", ClueGrantSource.parse("container=minecraft:chest").key());
        assertEquals(ClueSourceType.NPC, ClueGrantSource.parse("npc=106").type());
        assertEquals("逐光会医疗接待点", ClueGrantSource.parse("area=逐光会医疗接待点").key());
        assertEquals("hospital.opened", ClueGrantSource.parse("broadcast=hospital.opened").key());
        assertEquals("hospital_review", ClueGrantSource.parse("event=hospital_review").key());

        // 方块 / 容器 的键归一成小写，避免 Minecraft:Chest 这种写法永远匹配不上。
        assertEquals("minecraft:chest", ClueGrantSource.parse("block=Minecraft:Chest").key());
    }

    @Test
    void malformedDeclarationsAreRejected() {
        assertNull(ClueGrantSource.parse(null));
        assertNull(ClueGrantSource.parse(""), "空字符串");
        assertNull(ClueGrantSource.parse("container"), "缺少等号");
        assertNull(ClueGrantSource.parse("=minecraft:chest"), "缺少前缀");
        assertNull(ClueGrantSource.parse("chest=minecraft:chest"), "未知前缀");
        assertNull(ClueGrantSource.parse("container="), "键为空");
        assertNull(ClueGrantSource.parse("container=   "), "键只有空白");
        assertNull(ClueGrantSource.parse("container=NotNamespaced"), "方块键必须是命名空间 ID");
        assertNull(ClueGrantSource.parse("npc=jiangwan"), "NPC 键必须是编号");
        assertNull(ClueGrantSource.parse("container=" + "a".repeat(200)), "键过长");
    }

    // ==================== 索引 ====================

    @Test
    void indexFindsCluesByDeclaredEntry() {
        installOne(CLUE_A, List.of("container=minecraft:chest"));

        assertEquals(List.of(CLUE_A),
                ClueCatalog.cluesForGrantSource(ClueSourceType.CONTAINER, "minecraft:chest"));
        assertEquals(List.of(),
                ClueCatalog.cluesForGrantSource(ClueSourceType.CONTAINER, "minecraft:barrel"),
                "没声明过的键不该反查得到东西");
        assertEquals(List.of(), ClueCatalog.cluesForGrantSource(null, "minecraft:chest"));
        assertEquals(List.of(), ClueCatalog.cluesForGrantSource(ClueSourceType.CONTAINER, "  "));
    }

    @Test
    void oneClueCanDeclareSeveralEntries() {
        installOne(CLUE_A, List.of("container=minecraft:chest", "npc=106", "event=hospital_review"));

        assertEquals(List.of(CLUE_A), ClueCatalog.cluesForGrantSource(ClueSourceType.CONTAINER, "minecraft:chest"));
        assertEquals(List.of(CLUE_A), ClueCatalog.cluesForGrantSource(ClueSourceType.NPC, "106"));
        assertEquals(List.of(CLUE_A), ClueCatalog.cluesForGrantSource(ClueSourceType.EVENT, "hospital_review"));
        assertEquals(3, ClueCatalog.grantSourceCount());
    }

    @Test
    void severalCluesCanShareOneEntryInDeclarationOrder() {
        ClueCatalog.installForTest(
                List.of(clue(CLUE_A, 1), clue(CLUE_B, 2)),
                List.of(secrets(CLUE_A, List.of("area=阿拜多斯区域")),
                        secrets(CLUE_B, List.of("area=阿拜多斯区域"))));

        assertEquals(List.of(CLUE_A, CLUE_B),
                ClueCatalog.cluesForGrantSource(ClueSourceType.AREA, "阿拜多斯区域"),
                "同一入口上的多条线索要保持声明顺序");
    }

    @Test
    void brokenDeclarationOnlyDropsItself() {
        installOne(CLUE_A, List.of("chest=minecraft:chest", "container=minecraft:chest", "npc=106"));

        assertEquals(List.of(CLUE_A),
                ClueCatalog.cluesForGrantSource(ClueSourceType.CONTAINER, "minecraft:chest"),
                "坏声明不该影响同一条线索的其它入口");
        assertEquals(List.of(CLUE_A), ClueCatalog.cluesForGrantSource(ClueSourceType.NPC, "106"));
    }

    @Test
    void repeatedDeclarationCountsOnce() {
        installOne(CLUE_A, List.of("container=minecraft:chest", "container=minecraft:chest"));

        assertEquals(List.of(CLUE_A),
                ClueCatalog.cluesForGrantSource(ClueSourceType.CONTAINER, "minecraft:chest"));
        assertEquals(1, ClueCatalog.grantSourceCount(), "重复声明只该占一个索引位");
    }

    @Test
    void secretsWithoutVisibleClueNeverReachTheIndex() {
        // 内容被删了、私密定义还留着：validateSecrets 记警告，这里也不该建出索引。
        ClueCatalog.installForTest(
                List.of(clue(CLUE_A, 1)),
                List.of(secrets(CLUE_B, List.of("container=minecraft:chest"))));

        assertEquals(List.of(), ClueCatalog.cluesForGrantSource(ClueSourceType.CONTAINER, "minecraft:chest"));
        assertEquals(0, ClueCatalog.grantSourceCount());
    }

    @Test
    void reloadDropsThePreviousIndex() {
        installOne(CLUE_A, List.of("container=minecraft:chest"));
        assertEquals(1, ClueCatalog.grantSourceCount());

        ClueCatalog.installForTest(List.of(clue(CLUE_B, 2)), List.of());
        assertEquals(0, ClueCatalog.grantSourceCount(),
                "换成没有声明的内容后，旧索引不该残留");
        assertTrue(ClueCatalog.cluesForGrantSource(ClueSourceType.CONTAINER, "minecraft:chest").isEmpty());
    }

    // ==================== 可达性判据 ====================

    @Test
    void validDeclarationDetectionMatchesIndexing() {
        assertTrue(ClueGrantSource.hasValidDeclaration(List.of("container=minecraft:chest")));
        assertTrue(ClueGrantSource.hasValidDeclaration(List.of("chest=x", "npc=106")),
                "一组里只要有一条能解析通过，就算有确定入口");
        assertFalse(ClueGrantSource.hasValidDeclaration(List.of()),
                "没有任何声明 = 只能靠掉落");
        assertFalse(ClueGrantSource.hasValidDeclaration(null));
        assertFalse(ClueGrantSource.hasValidDeclaration(List.of("chest=x", "npc=jiangwan")),
                "全部写错时不该被当成有入口，否则审计会漏报");
    }

    @Test
    void grantSourcesOfFallsBackToEmpty() {
        ClueCatalog.installForTest(List.of(clue(CLUE_A, 1)), List.of());
        assertEquals(List.of(), ClueCatalog.grantSourcesOf(CLUE_A), "没有私密定义就是空表");
        assertEquals(List.of(), ClueCatalog.grantSourcesOf("dreamingfishcore:clue/not_there"));
        assertEquals(List.of(), ClueCatalog.grantSourcesOf(null));
    }

    // ==================== 辅助 ====================

    private static void installOne(String clueId, List<String> grantSources) {
        ClueCatalog.installForTest(
                List.of(clue(clueId, 1)),
                List.of(secrets(clueId, grantSources)));
    }

    private static ClueDefinition clue(String id, int legacyId) {
        ClueDefinition definition = new ClueDefinition();
        setField(definition, "id", id);
        setField(definition, "legacyId", legacyId);
        setField(definition, "title", "测试线索");
        setField(definition, "content", "正文内容");
        setField(definition, "source", "测试来源");
        setField(definition, "observationSpan", "1 天");
        setField(definition, "sample", "1 例");
        return definition;
    }

    private static ClueSecrets secrets(String id, List<String> grantSources) {
        ClueSecrets secrets = new ClueSecrets();
        setField(secrets, "id", id);
        setField(secrets, "authorStance", "测试立场");
        setField(secrets, "evidencePackId", "pack.test");
        setField(secrets, "claimId", "claim.test");
        setField(secrets, "relation", "support");
        setField(secrets, "truthNote", "测试备注");
        setField(secrets, "grantSources", new java.util.ArrayList<>(grantSources));
        return secrets;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("测试字段注入失败：" + fieldName, exception);
        }
    }
}
