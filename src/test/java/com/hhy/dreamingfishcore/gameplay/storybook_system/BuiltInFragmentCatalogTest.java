package com.hhy.dreamingfishcore.gameplay.storybook_system;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 校验随模组分发的内置线索文案本身是否合法，避免坏 JSON 在开服时才暴露。 */
class BuiltInFragmentCatalogTest {

    private static final String RESOURCE = "/dreamingfishcore/defaults/fragment_data.json";

    private static String bundledJson() throws Exception {
        try (InputStream stream = BuiltInFragmentCatalogTest.class.getResourceAsStream(RESOURCE)) {
            assertTrue(stream != null, "内置线索资源缺失：" + RESOURCE);
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
            }
            return builder.toString();
        }
    }

    @Test
    void bundledFileContainsTheTwelvePlannedClues() throws Exception {
        List<FragmentData> fragments = BuiltInFragmentCatalog.parseForTest(bundledJson());

        assertEquals(12, fragments.size(), "线索池应为 12 条");

        Set<Integer> ids = new HashSet<>();
        for (FragmentData fragment : fragments) {
            assertTrue(fragment.getId() > 0, "线索 ID 必须为正数");
            assertTrue(ids.add(fragment.getId()), "线索 ID 不能重复：" + fragment.getId());
            assertEquals(1, fragment.getStageId(), "线索都属于第一阶段「梦的开始」");
            assertTrue(fragment.getChapterId() >= 1 && fragment.getChapterId() <= 3,
                    "章节只能是 1/2/3：" + fragment.getId());
            assertFalse(fragment.getTitle().isEmpty(), "标题不能为空");
            assertFalse(fragment.getContent().isEmpty(), "正文不能为空");
            assertTrue(fragment.getContent().contains("\n"), "正文应包含分段换行：" + fragment.getId());
        }
        assertEquals(12, ids.size());
    }

    @Test
    void bundledCluesKeepTheirAuthoredAttribution() throws Exception {
        List<FragmentData> fragments = BuiltInFragmentCatalog.parseForTest(bundledJson());

        FragmentData observation = fragments.stream()
                .filter(fragment -> fragment.getId() == 1)
                .findFirst()
                .orElseThrow();
        assertEquals("观察区值班记录（第 2 天）", observation.getTitle());
        assertEquals("阿拜多斯医院 · 观察区值班记录", observation.getAuthorName());

        FragmentData outerRelay = fragments.stream()
                .filter(fragment -> fragment.getId() == 8)
                .findFirst()
                .orElseThrow();
        assertEquals("外缘中继站", outerRelay.getAuthorName());
        assertEquals(3, outerRelay.getChapterId(), "外缘信号属于「远处与未来」章节");
    }

    @Test
    void rejectsDuplicateOrInvalidIds() {
        String duplicated = """
                [
                  {"id": 1, "stageId": 1, "chapterId": 1, "authorName": "a", "title": "t", "time": "x", "content": "c"},
                  {"id": 1, "stageId": 1, "chapterId": 1, "authorName": "a", "title": "t", "time": "x", "content": "c"}
                ]
                """;
        assertThrows(IllegalStateException.class, () -> BuiltInFragmentCatalog.parseForTest(duplicated));

        String negativeStage = """
                [
                  {"id": 3, "stageId": 0, "chapterId": 1, "authorName": "a", "title": "t", "time": "x", "content": "c"}
                ]
                """;
        assertThrows(IllegalStateException.class, () -> BuiltInFragmentCatalog.parseForTest(negativeStage));

        String emptyContent = """
                [
                  {"id": 4, "stageId": 1, "chapterId": 1, "authorName": "a", "title": "t", "time": "x", "content": ""}
                ]
                """;
        assertThrows(IllegalStateException.class, () -> BuiltInFragmentCatalog.parseForTest(emptyContent));
    }
}
