package com.hhy.dreamingfishcore.gameplay.opening_story_system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第一阶段状态机的纯规则测试；不启动 Minecraft 也能捕获越级推进。 */
class OpeningStoryProgressTest {
    @Test
    void onlyTheAuthorDefinedTransitionsAreAccepted() {
        OpeningStoryProgress progress = new OpeningStoryProgress();

        assertFalse(progress.advanceTo(OpeningStoryStep.TALK_TO_BAIZHI, 1L));
        assertTrue(progress.advanceTo(OpeningStoryStep.TRAVEL_TO_ABYDOS, 2L));
        assertTrue(progress.advanceTo(OpeningStoryStep.TALK_TO_BAIZHI, 3L));
        assertTrue(progress.advanceTo(OpeningStoryStep.CONTACT_ZHOUCEN, 4L));
        assertTrue(progress.advanceTo(OpeningStoryStep.CHOOSE_MEMBERSHIP, 5L));
        assertTrue(progress.advanceTo(OpeningStoryStep.BUILD_ZHUIGUANG_BASE, 6L));
        assertFalse(progress.advanceTo(OpeningStoryStep.DECLINED_ZHUIGUANG, 7L));
        assertEquals(OpeningStoryStep.BUILD_ZHUIGUANG_BASE, progress.getStep());
    }

    @Test
    void membershipChoiceHasAnIndependentDeclinedBranch() {
        OpeningStoryProgress progress = new OpeningStoryProgress();
        progress.advanceTo(OpeningStoryStep.TRAVEL_TO_ABYDOS, 1L);
        progress.advanceTo(OpeningStoryStep.TALK_TO_BAIZHI, 2L);
        progress.advanceTo(OpeningStoryStep.CONTACT_ZHOUCEN, 3L);
        progress.advanceTo(OpeningStoryStep.CHOOSE_MEMBERSHIP, 4L);

        assertTrue(progress.advanceTo(OpeningStoryStep.DECLINED_ZHUIGUANG, 5L));
        assertFalse(progress.markStarterSupplyGranted(6L));
    }

    @Test
    void starterSupplyFactIsIdempotentAndOnlyValidAfterJoining() {
        OpeningStoryProgress progress = new OpeningStoryProgress();
        assertFalse(progress.markStarterSupplyGranted(1L));

        progress.advanceTo(OpeningStoryStep.TRAVEL_TO_ABYDOS, 1L);
        progress.advanceTo(OpeningStoryStep.TALK_TO_BAIZHI, 2L);
        progress.advanceTo(OpeningStoryStep.CONTACT_ZHOUCEN, 3L);
        progress.advanceTo(OpeningStoryStep.CHOOSE_MEMBERSHIP, 4L);
        progress.advanceTo(OpeningStoryStep.BUILD_ZHUIGUANG_BASE, 5L);

        assertTrue(progress.markStarterSupplyGranted(6L));
        assertFalse(progress.markStarterSupplyGranted(7L));
        assertTrue(progress.isStarterSupplyGranted());
    }
}
