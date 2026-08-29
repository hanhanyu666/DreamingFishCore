package com.hhy.dreamingfishcore.gameplay.opening_story_system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningStoryProgressTest {
    @Test
    void memberPathCannotSkipRequiredSteps() {
        OpeningStoryProgress progress = new OpeningStoryProgress();

        assertFalse(progress.advanceTo(OpeningStoryStep.TALK_TO_BAIZHI, 1L));
        assertTrue(progress.advanceTo(OpeningStoryStep.TRAVEL_TO_ABYDOS, 2L));
        assertTrue(progress.advanceTo(OpeningStoryStep.TALK_TO_BAIZHI, 3L));
        assertTrue(progress.advanceTo(OpeningStoryStep.CONTACT_ZHOUCEN, 4L));
        assertTrue(progress.advanceTo(OpeningStoryStep.CHOOSE_MEMBERSHIP, 5L));
        assertTrue(progress.advanceTo(OpeningStoryStep.BUILD_ZHUIGUANG_BASE, 6L));
        assertFalse(progress.advanceTo(OpeningStoryStep.DECLINED_ZHUIGUANG, 7L));
        assertEquals(OpeningStoryStep.BUILD_ZHUIGUANG_BASE, progress.getStep());
        assertTrue(progress.markStarterSupplyGranted(8L));
        assertFalse(progress.markStarterSupplyGranted(9L));
    }

    @Test
    void decliningIsTerminalAndSupplyFlagIsIdempotent() {
        OpeningStoryProgress progress = new OpeningStoryProgress();
        assertTrue(progress.advanceTo(OpeningStoryStep.TRAVEL_TO_ABYDOS, 1L));
        assertTrue(progress.advanceTo(OpeningStoryStep.TALK_TO_BAIZHI, 2L));
        assertTrue(progress.advanceTo(OpeningStoryStep.CONTACT_ZHOUCEN, 3L));
        assertTrue(progress.advanceTo(OpeningStoryStep.CHOOSE_MEMBERSHIP, 4L));
        assertTrue(progress.advanceTo(OpeningStoryStep.DECLINED_ZHUIGUANG, 5L));
        assertFalse(progress.advanceTo(OpeningStoryStep.BUILD_ZHUIGUANG_BASE, 6L));

        assertFalse(progress.markStarterSupplyGranted(7L));
    }
}
