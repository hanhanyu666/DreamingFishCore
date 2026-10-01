package com.hhy.dreamingfishcore.gameplay.clue_system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 关键线索的保底发放条件与编号。 */
class ClueGuaranteeServiceTest {

    @Test
    void grantsOnlyWhenNothingBlocksIt() {
        assertTrue(ClueGuaranteeService.shouldGrant(true, false, false));
    }

    @Test
    void missingFragmentDefinitionBlocksGrant() {
        assertFalse(ClueGuaranteeService.shouldGrant(false, false, false),
                "编号不在线索池里就不能发，否则玩家背包里会出现读不出来的残页");
    }

    @Test
    void alreadyCollectedBlocksGrant() {
        assertFalse(ClueGuaranteeService.shouldGrant(true, true, false),
                "已收录的线索不再重复发放");
    }

    @Test
    void alreadyCarryingBlocksGrant() {
        assertFalse(ClueGuaranteeService.shouldGrant(true, false, true),
                "背包里已经有一张同编号残页时不再叠加，避免反复触发剧情事件刷出重复残页");
    }

    @Test
    void everyBlockerIndividuallyPreventsGrant() {
        assertFalse(ClueGuaranteeService.shouldGrant(false, true, false));
        assertFalse(ClueGuaranteeService.shouldGrant(false, false, true));
        assertFalse(ClueGuaranteeService.shouldGrant(true, true, true));
    }

    @Test
    void guaranteedClueIdsMatchTheContentPool() {
        // 编号必须与 config/dreamingfishcore/data/fragment_data.json 里的 id 一致；
        // 改这里之前先确认那条线索的正文和 id 没有一起被改掉。
        assertEquals(1, ClueGuaranteeService.CLUE_OBSERVATION_LOG);
        assertEquals(4, ClueGuaranteeService.CLUE_RECOVERED_VOICE);
        assertEquals(8, ClueGuaranteeService.CLUE_OUTER_RELAY);
        assertEquals(11, ClueGuaranteeService.CLUE_RESPAWN_ANOMALY);
    }
}
