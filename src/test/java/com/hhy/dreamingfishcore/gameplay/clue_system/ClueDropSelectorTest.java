package com.hhy.dreamingfishcore.gameplay.clue_system;

import com.google.gson.Gson;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 线索掉落选择的纯逻辑测试：去重、空池、概率边界。
 *
 * <p>里程碑 2 收尾后线索池与玩家发现状态都用稳定 ID，这里也按稳定 ID 构造。</p>
 */
class ClueDropSelectorTest {

    private static final Gson GSON = new Gson();

    private static ClueDefinition clue(String slug) {
        // 走真实的反序列化路径，保证测试数据和配置文件是同一种结构。
        return GSON.fromJson("""
                {
                  "id": "dreamingfishcore:clue/%s",
                  "stageId": 1,
                  "chapterId": 1,
                  "title": "标题 %s",
                  "content": "正文"
                }
                """.formatted(slug, slug), ClueDefinition.class);
    }

    private static List<ClueDefinition> pool(String... slugs) {
        List<ClueDefinition> definitions = new ArrayList<>();
        for (String slug : slugs) {
            definitions.add(clue(slug));
        }
        return definitions;
    }

    @Test
    void excludesAlreadyDiscoveredClues() {
        List<String> candidates = ClueDropSelector.candidates(
                pool("a", "b", "c", "d"),
                Set.of("dreamingfishcore:clue/a", "dreamingfishcore:clue/c"));

        assertEquals(List.of("dreamingfishcore:clue/b", "dreamingfishcore:clue/d"), candidates);
    }

    @Test
    void returnsEmptyWhenEverythingIsDiscovered() {
        List<String> candidates = ClueDropSelector.candidates(pool("a", "b"),
                Set.of("dreamingfishcore:clue/a", "dreamingfishcore:clue/b"));

        assertTrue(candidates.isEmpty());
        assertNull(ClueDropSelector.pick(candidates, RandomSource.create(1L)),
                "候选为空时不能掉落任何残页");
    }

    @Test
    void handlesMissingPoolAndDiscoveredSet() {
        assertTrue(ClueDropSelector.candidates(null, Set.of()).isEmpty());
        assertTrue(ClueDropSelector.candidates(List.of(), null).isEmpty());
        assertEquals(List.of("dreamingfishcore:clue/e"),
                ClueDropSelector.candidates(pool("e"), null));
    }

    @Test
    void skipsMalformedDefinitions() {
        List<ClueDefinition> definitions = new ArrayList<>();
        definitions.add(null);
        definitions.add(GSON.fromJson("{\"stageId\": 1, \"title\": \"没有 ID\"}", ClueDefinition.class));
        definitions.add(clue("g"));

        assertEquals(List.of("dreamingfishcore:clue/g"),
                ClueDropSelector.candidates(definitions, Set.of()));
    }

    @Test
    void pickStaysInsideCandidateRange() {
        List<String> candidates = List.of("dreamingfishcore:clue/x",
                "dreamingfishcore:clue/y", "dreamingfishcore:clue/z");
        RandomSource random = RandomSource.create(42L);

        for (int i = 0; i < 200; i++) {
            String picked = ClueDropSelector.pick(candidates, random);
            assertTrue(candidates.contains(picked), "拾取结果必须来自候选列表：" + picked);
        }
    }

    @Test
    void rollDropHonoursBoundaries() {
        RandomSource random = RandomSource.create(7L);

        assertFalse(ClueDropSelector.rollDrop(0.0D, random), "0% 不掉落");
        assertTrue(ClueDropSelector.rollDrop(100.0D, random), "100% 必掉");
        assertTrue(ClueDropSelector.rollDrop(150.0D, random), "超过 100% 仍然必掉");
    }

    @Test
    void subOnePercentChanceStillDrops() {
        RandomSource random = RandomSource.create(2026L);
        int attempts = 200_000;
        int drops = 0;

        for (int i = 0; i < attempts; i++) {
            if (ClueDropSelector.rollDrop(0.01D, random)) {
                drops++;
            }
        }

        // 0.01% 的期望值约为 20 次；关键是不能被当成 0 而永远不掉。
        assertTrue(drops > 0, "0.01% 必须真的有可能掉落");
        assertTrue(drops < attempts / 1000, "0.01% 的掉落次数应远低于千分之一，实际：" + drops);
    }

    @Test
    void nanChanceDoesNotDrop() {
        assertFalse(ClueDropSelector.rollDrop(Double.NaN, RandomSource.create(1L)),
                "非法概率按不掉落处理");
    }
}
