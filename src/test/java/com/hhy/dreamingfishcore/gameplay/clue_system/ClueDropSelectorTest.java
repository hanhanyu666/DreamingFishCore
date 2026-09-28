package com.hhy.dreamingfishcore.gameplay.clue_system;

import com.hhy.dreamingfishcore.gameplay.storybook_system.FragmentData;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 线索掉落选择的纯逻辑测试：去重、空池、概率边界。 */
class ClueDropSelectorTest {

    private static FragmentData fragment(int id) {
        return new FragmentData(id, 1, 1, "作者", "标题" + id, "时间", "内容");
    }

    private static List<FragmentData> pool(int... ids) {
        List<FragmentData> fragments = new ArrayList<>();
        for (int id : ids) {
            fragments.add(fragment(id));
        }
        return fragments;
    }

    @Test
    void excludesAlreadyUnlockedClues() {
        List<Integer> candidates = ClueDropSelector.candidates(pool(1, 2, 3, 4), Set.of(1, 3));

        assertEquals(List.of(2, 4), candidates);
    }

    @Test
    void returnsEmptyWhenEverythingIsUnlocked() {
        List<Integer> candidates = ClueDropSelector.candidates(pool(1, 2), Set.of(1, 2));

        assertTrue(candidates.isEmpty());
        assertNull(ClueDropSelector.pick(candidates, RandomSource.create(1L)),
                "候选为空时不能掉落任何残页");
    }

    @Test
    void handlesMissingPoolAndUnlockedSet() {
        assertTrue(ClueDropSelector.candidates(null, Set.of()).isEmpty());
        assertTrue(ClueDropSelector.candidates(List.of(), null).isEmpty());
        assertEquals(List.of(5), ClueDropSelector.candidates(pool(5), null));
    }

    @Test
    void skipsMalformedFragmentEntries() {
        List<FragmentData> fragments = new ArrayList<>();
        fragments.add(null);
        fragments.add(fragment(0));
        fragments.add(fragment(7));

        assertEquals(List.of(7), ClueDropSelector.candidates(fragments, Set.of()));
    }

    @Test
    void pickStaysInsideCandidateRange() {
        List<Integer> candidates = List.of(3, 6, 9);
        RandomSource random = RandomSource.create(42L);

        for (int i = 0; i < 200; i++) {
            Integer picked = ClueDropSelector.pick(candidates, random);
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
