package com.hhy.dreamingfishcore.gameplay.afterdream_story_system;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 不依赖 Minecraft 的余梦期全服计时回归测试。 */
class AfterdreamWorldProgressTest {
    private static final Gson GSON = new Gson();

    @Test
    void countdownStartsOnceAndUsesTwoFullGameDays() {
        AfterdreamWorldProgress progress = new AfterdreamWorldProgress();

        assertTrue(progress.startMaskCountdown(123L));
        assertEquals(123L, progress.getMaskCountdownStartedAtActiveTick());
        assertEquals(123L + AfterdreamWorldProgress.MASK_COUNTDOWN_TICKS,
                progress.getMaskAvailableAtActiveTick());

        // 任何后续玩家都不能把全服起点重置到自己的交互时间。
        assertFalse(progress.startMaskCountdown(999_999L));
        assertEquals(123L, progress.getMaskCountdownStartedAtActiveTick());
    }

    @Test
    void countdownOnlyBecomesAvailableAtInclusiveDeadlineAndIsIdempotent() {
        AfterdreamWorldProgress progress = new AfterdreamWorldProgress();
        progress.startMaskCountdown(0L);
        long deadline = AfterdreamWorldProgress.MASK_COUNTDOWN_TICKS;

        assertFalse(progress.advanceMaskCountdown(deadline - 1L));
        assertFalse(progress.isMaskDistributionAvailable());
        assertTrue(progress.advanceMaskCountdown(deadline));
        assertTrue(progress.isMaskDistributionAvailable());
        assertFalse(progress.advanceMaskCountdown(deadline + 1L));
    }

    @Test
    void markingAnnouncementAndFirstMaskAreOneShotFacts() {
        AfterdreamWorldProgress progress = new AfterdreamWorldProgress();
        progress.startMaskCountdown(10L);

        assertTrue(progress.markMaskAnnouncementSent());
        assertTrue(progress.isMaskDistributionAvailable());
        assertFalse(progress.markMaskAnnouncementSent());

        assertTrue(progress.markFirstMaskGranted(20L));
        assertEquals(20L, progress.getFirstMaskGrantedAtActiveTick());
        assertFalse(progress.markFirstMaskGranted(30L));
        assertEquals(20L, progress.getFirstMaskGrantedAtActiveTick());
    }

    @Test
    void globalMaskReceiptRepairsChapterAvailabilityWithoutInventingGrantTime() {
        AfterdreamWorldProgress progress = new AfterdreamWorldProgress();

        assertTrue(progress.reconcileMaskDistributionAvailable(true));
        assertTrue(progress.isMaskDistributionAvailable());
        assertEquals(-1L, progress.getFirstMaskGrantedAtActiveTick());
        assertFalse(progress.reconcileMaskDistributionAvailable(true));

        // A missing global receipt must never unlock the chapter by itself.
        AfterdreamWorldProgress untouched = new AfterdreamWorldProgress();
        assertFalse(untouched.reconcileMaskDistributionAvailable(false));
        assertFalse(untouched.isMaskDistributionAvailable());
    }

    @Test
    void repairRejectsAnOrphanDeadlineAndRepairsDerivableFields() {
        AfterdreamWorldProgress invalid = new AfterdreamWorldProgress();
        // The public state starts empty; the invariant is covered through the
        // same JSON shape that the world save actually uses.
        assertFalse(invalid.repair());

        // A countdown start without a separately persisted deadline is safe to
        // reconstruct; this is the only derivation repair the world object does.
        AfterdreamWorldProgress derived = GSON.fromJson(
                "{\"maskCountdownStartedAtActiveTick\":7}", AfterdreamWorldProgress.class);
        assertTrue(derived.repair());
        assertEquals(7L + AfterdreamWorldProgress.MASK_COUNTDOWN_TICKS,
                derived.getMaskAvailableAtActiveTick());

        AfterdreamWorldProgress orphan = GSON.fromJson(
                "{\"maskAvailableAtActiveTick\":4}", AfterdreamWorldProgress.class);
        assertThrows(IllegalStateException.class, orphan::repair);
    }
}
