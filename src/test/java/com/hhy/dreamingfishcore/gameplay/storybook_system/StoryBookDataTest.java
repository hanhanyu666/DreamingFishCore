package com.hhy.dreamingfishcore.gameplay.storybook_system;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryBookDataTest {
    @Test
    void chapterZeroIsNotARealChapterAndFirstPageCanUnlockItsChapter() {
        StoryBookData data = new StoryBookData();

        assertFalse(data.unlockChapter(0));
        assertTrue(data.unlockChapter(1));
        assertTrue(data.hasUnlockedChapter(1));
    }

    @Test
    void obtainedOrderOnlyContainsOwnedFragmentsAndPreservesOwnership() {
        StoryBookData data = new StoryBookData();
        data.setUnlockedFragmentIds(new LinkedHashSet<>(List.of(1, 2, 3)));

        data.setObtainedOrder(List.of(3, 3, 99, 1));

        assertEquals(List.of(3, 1, 2), data.getSortedFragmentIds());
        assertTrue(data.hasUnlockedFragment(2));
    }

    // ==================== 里程碑 2：稳定 ID 的永久发现记录 ====================

    @Test
    void discoveringAClueIsIdempotentAndKeepsDiscoveryOrder() {
        StoryBookData data = new StoryBookData();
        String first = "dreamingfishcore:clue/observation_ward_log";
        String second = "dreamingfishcore:clue/recovered_voice";

        assertTrue(data.discoverClue(first));
        assertTrue(data.discoverClue(second));
        assertFalse(data.discoverClue(first), "重复发放不应再次登记");
        assertFalse(data.discoverClue(null));
        assertFalse(data.discoverClue("   "), "空白 ID 必须被拒");

        assertEquals(2, data.getDiscoveredClueCount());
        assertEquals(List.of(first, second), data.getSortedClueIds());
        assertTrue(data.hasDiscoveredClue(first));
        assertFalse(data.hasDiscoveredClue("dreamingfishcore:clue/not_there"));
    }

    @Test
    void readMarkOnlyAppliesToDiscoveredClues() {
        StoryBookData data = new StoryBookData();
        String clue = "dreamingfishcore:clue/respawn_node_anomaly";

        data.markClueRead(clue);
        assertFalse(data.hasReadClue(clue), "没发现过的线索不能被标成已读");

        data.discoverClue(clue);
        data.markClueRead(clue);
        assertTrue(data.hasReadClue(clue));
    }

    @Test
    void legacyIntegerIdsMigrateToStableIdsAndKeepTheOldFields() {
        StoryBookData data = new StoryBookData();
        data.setUnlockedFragmentIds(new LinkedHashSet<>(List.of(1, 4)));
        data.markFragmentRead(1);

        boolean changed = data.migrateLegacyClueIds(legacyId -> switch (legacyId) {
            case 1 -> "dreamingfishcore:clue/observation_ward_log";
            case 4 -> "dreamingfishcore:clue/recovered_voice";
            default -> null;
        });

        assertTrue(changed, "首次迁移应当报告发生了变化");
        assertEquals(2, data.getDiscoveredClueCount());
        assertTrue(data.hasDiscoveredClue("dreamingfishcore:clue/observation_ward_log"));
        assertTrue(data.hasReadClue("dreamingfishcore:clue/observation_ward_log"),
                "旧字段里标过已读的线索，迁移后也应当是已读");
        assertTrue(data.hasUnlockedFragment(1), "旧字段必须保留，便于回退与排错");

        assertFalse(data.migrateLegacyClueIds(legacyId -> switch (legacyId) {
            case 1 -> "dreamingfishcore:clue/observation_ward_log";
            case 4 -> "dreamingfishcore:clue/recovered_voice";
            default -> null;
        }), "重复迁移应当是幂等的");
    }

    @Test
    void migrationSkipsLegacyIdsThatNoLongerResolve() {
        StoryBookData data = new StoryBookData();
        data.setUnlockedFragmentIds(new LinkedHashSet<>(List.of(7, 8)));

        boolean changed = data.migrateLegacyClueIds(legacyId ->
                legacyId == 8 ? "dreamingfishcore:clue/outer_relay_signal" : null);

        assertTrue(changed);
        assertEquals(1, data.getDiscoveredClueCount(), "映射不到的旧编号必须跳过而不是报错");
        assertTrue(data.hasDiscoveredClue("dreamingfishcore:clue/outer_relay_signal"));
        assertFalse(data.migrateLegacyClueIds(null), "没有映射函数时不做任何事");
    }

    @Test
    void normalizationDropsBlanksAndUndeclaredOrder() {
        StoryBookData data = new StoryBookData();
        data.setDiscoveredClueIds(new LinkedHashSet<>(List.of(
                "dreamingfishcore:clue/a", "  ", "dreamingfishcore:clue/a")));
        data.setReadClueIds(new LinkedHashSet<>(List.of(
                "dreamingfishcore:clue/a", "dreamingfishcore:clue/not_discovered")));
        data.setClueOrder(List.of("dreamingfishcore:clue/not_discovered",
                "dreamingfishcore:clue/a"));

        assertEquals(1, data.getDiscoveredClueCount(), "空白与重复必须被去掉");
        assertEquals(List.of("dreamingfishcore:clue/a"), data.getSortedClueIds(),
                "顺序里不能留下没发现过的线索");
        assertFalse(data.hasReadClue("dreamingfishcore:clue/not_discovered"),
                "没发现的线索不能出现在已读集合里");
        assertTrue(data.hasReadClue("dreamingfishcore:clue/a"));
    }
}
