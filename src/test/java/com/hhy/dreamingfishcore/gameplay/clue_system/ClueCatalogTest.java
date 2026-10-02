package com.hhy.dreamingfishcore.gameplay.clue_system;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 线索目录（里程碑 2）的行为与内置内容保真度。
 *
 * <p>这里锁两件事：一是稳定 ID / 旧编号 / 私密定义三者的关系与校验失败语义；
 * 二是**迁移保真**——内置 {@code clues.json} 与旧的 {@code fragment_data.json}
 * 必须逐条对得上（标题一致、旧编号 1..12 连续），否则玩家手里的旧残页会指向不存在的线索。</p>
 */
class ClueCatalogTest {

    private static final String VISIBLE_RESOURCE = "/dreamingfishcore/defaults/clues.json";
    private static final String LEGACY_RESOURCE = "/dreamingfishcore/defaults/fragment_data.json";
    private static final Gson GSON = new Gson();

    @AfterEach
    void tearDown() {
        ClueCatalog.resetForTest();
    }

    // ==================== 目录行为 ====================

    @Test
    void catalogResolvesStableIdAndLegacyIdBothWays() {
        ClueDefinition definition = clue("dreamingfishcore:clue/observation_ward_log", 1,
                "观察区值班记录（第 2 天）");
        ClueCatalog.installForTest(List.of(definition), List.of());

        assertTrue(ClueCatalog.isLoaded());
        assertFalse(ClueCatalog.isReadOnly());
        assertEquals(1, ClueCatalog.count());
        assertEquals("dreamingfishcore:clue/observation_ward_log",
                ClueCatalog.idForLegacy(1));
        assertNotNull(ClueCatalog.byId("dreamingfishcore:clue/observation_ward_log"));
        assertNotNull(ClueCatalog.byLegacyId(1));
        assertEquals("观察区值班记录（第 2 天）",
                ClueCatalog.byLegacyId(1).title());
        assertNull(ClueCatalog.idForLegacy(99), "未知旧编号必须返回 null 而不是抛异常");
        assertNull(ClueCatalog.byId("dreamingfishcore:clue/not_there"));
        assertFalse(ClueCatalog.has(null));
    }

    @Test
    void secretsStayInTheirOwnLookupAndNeverMixWithVisibleFields() {
        ClueDefinition definition = clue("dreamingfishcore:clue/medical_review_opinion", 10,
                "感染医学评审意见（摘录）");
        ClueSecrets secrets = new ClueSecrets();
        secretsForTest(secrets, "dreamingfishcore:clue/medical_review_opinion",
                "评审员倾向于把个例当作可复制的证据", "pack.reading_gap", "claim.reading_gap",
                "support", "个例不能代表整体", List.of("dreamingfishcore:clue/recovered_voice"),
                List.of());
        ClueCatalog.installForTest(List.of(definition), List.of(secrets));

        ClueSecrets loaded = ClueCatalog.secretsOf("dreamingfishcore:clue/medical_review_opinion");
        assertNotNull(loaded);
        assertEquals("pack.reading_gap", loaded.evidencePackId());
        assertEquals(List.of("dreamingfishcore:clue/medical_review_opinion"),
                ClueCatalog.cluesInEvidencePack("pack.reading_gap"));

        // 可见定义只带玩家能看的东西（来源/跨度/样本），私密内容不在其中。
        ClueDefinition visible = ClueCatalog.byId("dreamingfishcore:clue/medical_review_opinion");
        assertNotNull(visible);
        assertEquals("测试来源", visible.source());
        assertEquals("1 天", visible.observationSpan());
        assertNull(ClueCatalog.secretsOf("dreamingfishcore:clue/observation_ward_log"),
                "没有私密定义的线索应当返回 null");
        assertTrue(ClueCatalog.cluesInEvidencePack("").isEmpty());
        assertTrue(ClueCatalog.cluesInEvidencePack("no.such.pack").isEmpty());
    }

    @Test
    void brokenDefinitionsAreRejectedWithReadableMessages() {
        assertThrows(IllegalStateException.class, () -> ClueCatalog.installForTest(
                List.of(clue("Bad Id", 1, "标题")), List.of()), "ID 形状非法必须被拒");
        assertThrows(IllegalStateException.class, () -> ClueCatalog.installForTest(
                List.of(clue("dreamingfishcore:clue/x", -1, "标题")), List.of()),
                "旧编号为负必须被拒");
        assertThrows(IllegalStateException.class, () -> ClueCatalog.installForTest(
                List.of(clue("dreamingfishcore:clue/x", 1, "  ")), List.of()),
                "空标题必须被拒");
        assertThrows(IllegalStateException.class, () -> ClueCatalog.installForTest(
                List.of(clue("dreamingfishcore:clue/x", 1, "标题"),
                        clue("dreamingfishcore:clue/x", 2, "另一条")), List.of()),
                "重复稳定 ID 必须被拒");
        assertThrows(IllegalStateException.class, () -> ClueCatalog.installForTest(
                List.of(clue("dreamingfishcore:clue/x", 7, "标题"),
                        clue("dreamingfishcore:clue/y", 7, "另一条")), List.of()),
                "重复旧编号必须被拒（否则旧残页会指向两条线索）");
    }

    @Test
    void contentLengthLimitsAreEnforced() {
        ClueDefinition tooLong = clue("dreamingfishcore:clue/long", 0, "标题");
        setField(tooLong, "content", "x".repeat(ClueDefinition.MAX_CONTENT + 1));
        assertThrows(IllegalStateException.class,
                () -> ClueCatalog.installForTest(List.of(tooLong), List.of()));

        ClueDefinition tooLongTitle = clue("dreamingfishcore:clue/long_title", 0, "y".repeat(
                ClueDefinition.MAX_TITLE + 1));
        assertThrows(IllegalStateException.class,
                () -> ClueCatalog.installForTest(List.of(tooLongTitle), List.of()));
    }

    // ==================== 内置内容与迁移保真 ====================

    @Test
    void builtInClueContentParsesAndKeepsEveryLegacyNumber() throws Exception {
        List<ClueDefinition> clues = readResource(VISIBLE_RESOURCE, ClueDefinition.class);
        List<Map<String, Object>> legacy = readRawList(LEGACY_RESOURCE);

        assertEquals(legacy.size(), clues.size(),
                "迁移后的线索条数必须与旧内容一致");
        ClueCatalog.installForTest(clues, List.of());

        Map<Integer, String> legacyTitles = new LinkedHashMap<>();
        for (Map<String, Object> entry : legacy) {
            legacyTitles.put(((Number) entry.get("id")).intValue(), (String) entry.get("title"));
        }
        for (int legacyId = 1; legacyId <= legacy.size(); legacyId++) {
            ClueDefinition definition = ClueCatalog.byLegacyId(legacyId);
            assertNotNull(definition, "旧编号 " + legacyId + " 必须有对应的稳定 ID");
            assertEquals(legacyTitles.get(legacyId), definition.title(),
                    "旧编号 " + legacyId + " 的标题必须逐字保留");
            assertTrue(definition.id().startsWith("dreamingfishcore:clue/"),
                    "稳定 ID 必须带命名空间：" + definition.id());
            assertFalse(definition.content().isBlank(), "正文不能为空：" + definition.id());
            assertFalse(definition.source().isBlank(), "里程碑 2 要求每条线索都写来源：" + definition.id());
            assertFalse(definition.observationSpan().isBlank(),
                    "里程碑 2 要求每条线索都写观察跨度：" + definition.id());
            assertFalse(definition.sample().isBlank(),
                    "里程碑 2 要求每条线索都写样本：" + definition.id());
        }
    }

    // ==================== 工具 ====================

    private static ClueDefinition clue(String id, int legacyId, String title) {
        ClueDefinition definition = new ClueDefinition();
        setField(definition, "id", id);
        setField(definition, "legacyId", legacyId);
        setField(definition, "title", title);
        setField(definition, "content", "正文内容");
        setField(definition, "source", "测试来源");
        setField(definition, "observationSpan", "1 天");
        setField(definition, "sample", "1 例");
        return definition;
    }

    private static void secretsForTest(ClueSecrets secrets, String id, String stance, String pack,
                                       String claim, String relation, String note,
                                       List<String> related, List<String> alternatives) {
        setField(secrets, "id", id);
        setField(secrets, "authorStance", stance);
        setField(secrets, "evidencePackId", pack);
        setField(secrets, "claimId", claim);
        setField(secrets, "relation", relation);
        setField(secrets, "truthNote", note);
        setField(secrets, "relatedClueIds", new ArrayList<>(related));
        setField(secrets, "alternativeClueIds", new ArrayList<>(alternatives));
    }

    /** 测试里用反射写私有字段，避免为测试给生产类开一堆 setter。 */
    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("测试无法写入字段 " + name, exception);
        }
    }

    private static <T> List<T> readResource(String path, Class<T> elementType) throws Exception {
        try (InputStream stream = ClueCatalogTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "缺少内置资源：" + path);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                List<T> parsed = GSON.fromJson(reader,
                        TypeToken.getParameterized(List.class, elementType).getType());
                return parsed == null ? List.of() : parsed;
            }
        }
    }

    /** 旧内容读成"原始键值表"，只用来核对标题与编号，不需要映射到具体的类。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> readRawList(String path) throws Exception {
        try (InputStream stream = ClueCatalogTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "缺少内置资源：" + path);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                List<Map<String, Object>> parsed = GSON.fromJson(reader, List.class);
                return parsed == null ? List.of() : parsed;
            }
        }
    }
}
