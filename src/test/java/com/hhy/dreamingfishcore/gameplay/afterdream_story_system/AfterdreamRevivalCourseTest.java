package com.hhy.dreamingfishcore.gameplay.afterdream_story_system;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 三次早期逆转疗程与第 3 / 7 天随访的状态机测试。 */
class AfterdreamRevivalCourseTest {

    private static final long DAY = AfterdreamPlayerProgress.ACTIVE_TICKS_PER_DAY;

    /** 走到“首次接待出结果 = 二级感染”这一步。 */
    private static AfterdreamPlayerProgress atLevelTwoResult() {
        AfterdreamPlayerProgress progress = new AfterdreamPlayerProgress();
        progress.setStep(AfterdreamMedicalStep.MESSAGE_RECEIVED, 0L);
        progress.setStep(AfterdreamMedicalStep.MESSAGE_READ, 0L);
        progress.setStep(AfterdreamMedicalStep.RECEPTION_READY, 0L);
        progress.setStep(AfterdreamMedicalStep.INTRODUCTION, 0L);
        progress.setStep(AfterdreamMedicalStep.RESULT_LEVEL_TWO, 0L);
        return progress;
    }

    private static AfterdreamPlayerProgress courseAtDose(int doses, long tick) {
        AfterdreamPlayerProgress progress = atLevelTwoResult();
        progress.startCourse(tick);
        for (int i = 0; i < doses; i++) {
            assertTrue(progress.recordCourseDose(tick + i * DAY, DAY), "第 " + (i + 1) + " 次用药应被接受");
        }
        return progress;
    }

    @Test
    void startingCourseMakesFirstDoseAvailableImmediately() {
        AfterdreamPlayerProgress progress = atLevelTwoResult();

        assertTrue(progress.startCourse(1_000L));

        assertEquals(AfterdreamMedicalStep.COURSE_IN_PROGRESS, progress.getStep());
        assertEquals(0, progress.getCourseDoses());
        assertTrue(progress.isNextDoseAvailable(1_000L), "开始当次即可完成第一次治疗");
        assertFalse(progress.isFinalCheckReady());
    }

    @Test
    void doseRequiresAFullActiveDayBetweenTreatments() {
        AfterdreamPlayerProgress progress = courseAtDose(1, 1_000L);

        assertEquals(1, progress.getCourseDoses());
        assertFalse(progress.isNextDoseAvailable(1_000L + DAY - 1), "不到间隔不能治疗");
        assertTrue(progress.isNextDoseAvailable(1_000L + DAY), "满一个活动日即可治疗");
        assertFalse(progress.recordCourseDose(1_000L + DAY - 1, DAY), "未到间隔的用药必须被拒绝");
        assertTrue(progress.recordCourseDose(1_000L + DAY, DAY));
        assertEquals(2, progress.getCourseDoses());
    }

    @Test
    void thirdDoseOpensFinalCheck() {
        AfterdreamPlayerProgress progress = courseAtDose(3, 500L);

        assertEquals(3, progress.getCourseDoses());
        assertTrue(progress.isFinalCheckReady());
        assertFalse(progress.isNextDoseAvailable(500L + 10 * DAY), "三次之后不再有新的用药间隔");
        assertFalse(progress.recordCourseDose(500L + 10 * DAY, DAY), "次数已满不能再用药");
    }

    @Test
    void finalCheckRequiresAllThreeDoses() {
        AfterdreamPlayerProgress progress = courseAtDose(2, 100L);

        assertFalse(progress.completeCourse(100L + 5 * DAY), "不足三次不能终检");

        assertTrue(progress.recordCourseDose(100L + 2 * DAY, DAY), "第三次用药要等到第 2 天之后");
        assertTrue(progress.completeCourse(100L + 5 * DAY));
        assertEquals(AfterdreamMedicalStep.COMPLETED, progress.getStep());
        assertTrue(progress.isMedicalTreatmentCompleted());
        assertEquals(100L + 5 * DAY, progress.getCourseCompletedAtActiveTick());
    }

    @Test
    void illegalTransitionsAreRejected() {
        AfterdreamPlayerProgress progress = new AfterdreamPlayerProgress();

        assertThrows(IllegalStateException.class,
                () -> progress.setStep(AfterdreamMedicalStep.COURSE_IN_PROGRESS, 0L),
                "未完成首次接待不能直接进入疗程");
    }

    @Test
    void followUpFallsDueAfterThreeAndSevenActiveDays() {
        AfterdreamPlayerProgress progress = courseAtDose(3, 0L);
        assertTrue(progress.completeCourse(3 * DAY));
        long completedAt = progress.getCourseCompletedAtActiveTick();

        assertEquals(0, progress.pendingFollowUpDay(completedAt + 3 * DAY - 1), "第 3 天之前不应提醒");
        assertEquals(AfterdreamPlayerProgress.FOLLOW_UP_THIRD_DAY,
                progress.pendingFollowUpDay(completedAt + 3 * DAY));
        assertTrue(progress.markFollowUpNotified(AfterdreamPlayerProgress.FOLLOW_UP_THIRD_DAY,
                completedAt + 3 * DAY));
        assertEquals(0, progress.pendingFollowUpDay(completedAt + 3 * DAY), "已提醒过就不再重复");

        assertEquals(AfterdreamPlayerProgress.FOLLOW_UP_SEVENTH_DAY,
                progress.pendingFollowUpDay(completedAt + 7 * DAY));
        assertTrue(progress.markFollowUpNotified(AfterdreamPlayerProgress.FOLLOW_UP_SEVENTH_DAY,
                completedAt + 7 * DAY));
        assertEquals(0, progress.pendingFollowUpDay(completedAt + 30 * DAY));
    }

    @Test
    void seventhDayReminderRequiresTheThirdDayOne() {
        AfterdreamPlayerProgress progress = courseAtDose(3, 0L);
        progress.completeCourse(3 * DAY);

        assertFalse(progress.markFollowUpNotified(AfterdreamPlayerProgress.FOLLOW_UP_SEVENTH_DAY, 10 * DAY),
                "第 7 天提醒不能早于第 3 天");
        assertFalse(progress.markFollowUpNotified(7, 10 * DAY), "非法天数应被拒绝");
    }

    @Test
    void followUpReviewOnlyRecordsWhatActuallyHappened() {
        AfterdreamPlayerProgress progress = courseAtDose(3, 0L);
        progress.completeCourse(3 * DAY);

        assertFalse(progress.recordFollowUpCompletion(AfterdreamPlayerProgress.FOLLOW_UP_THIRD_DAY, 4 * DAY),
                "还没有提醒过就不能记为已复核");

        progress.markFollowUpNotified(AfterdreamPlayerProgress.FOLLOW_UP_THIRD_DAY, 3 * DAY);
        assertTrue(progress.isFollowUpAwaitingReview());
        assertTrue(progress.recordFollowUpCompletion(AfterdreamPlayerProgress.FOLLOW_UP_THIRD_DAY, 8 * DAY),
                "逾期复核同样记录，且不带惩罚");
        assertFalse(progress.isFollowUpAwaitingReview());
        assertFalse(progress.recordFollowUpCompletion(AfterdreamPlayerProgress.FOLLOW_UP_THIRD_DAY, 9 * DAY),
                "同一次随访不能重复记账");
    }

    @Test
    void guidanceTargetsFollowTheCourseAndFollowUp() {
        AfterdreamPlayerProgress progress = courseAtDose(1, 0L);
        Set<AfterdreamPlayerProgress.GuidanceTarget> courseTargets =
                progress.pendingGuidance(false, false);
        assertTrue(courseTargets.contains(AfterdreamPlayerProgress.GuidanceTarget.COURSE));
        assertFalse(courseTargets.contains(AfterdreamPlayerProgress.GuidanceTarget.MEDICAL_REVIEW),
                "疗程进行中不再显示普通复核引导");

        progress.recordCourseDose(DAY, DAY);
        progress.recordCourseDose(2 * DAY, DAY);
        progress.completeCourse(3 * DAY);
        progress.markFollowUpNotified(AfterdreamPlayerProgress.FOLLOW_UP_THIRD_DAY, 4 * DAY);

        assertTrue(progress.pendingGuidance(false, false)
                .contains(AfterdreamPlayerProgress.GuidanceTarget.FOLLOW_UP), "待办随访应建立引导");
    }

    @Test
    void testingDoseBypassesTheIntervalButKeepsCountsConsistent() {
        AfterdreamPlayerProgress progress = atLevelTwoResult();
        progress.startCourse(1_000L);

        assertTrue(progress.recordCourseDoseForTesting(1_000L, DAY));
        assertEquals(1, progress.getCourseDoses());
        assertEquals(1_000L + DAY, progress.getCourseNextDoseAtActiveTick());
        assertTrue(progress.recordCourseDoseForTesting(1_000L, DAY));
        assertTrue(progress.recordCourseDoseForTesting(1_000L, DAY));
        assertEquals(3, progress.getCourseDoses());
        assertEquals(-1L, progress.getCourseNextDoseAtActiveTick());
        assertFalse(progress.recordCourseDoseForTesting(1_000L, DAY), "次数已满不能再记");
        assertTrue(progress.isFinalCheckReady());

        // 快进产生的存档同样必须通过一致性校验。
        progress.validateState(20_000L);
    }

    @Test
    void validationRejectsTimestampsFromTheFuture() {
        AfterdreamPlayerProgress progress = courseAtDose(1, 1_000L);

        progress.validateState(1_000L);
        assertThrows(IllegalStateException.class, () -> progress.validateState(500L),
                "记录时间晚于当前活动时间应视为损坏存档");
    }

    @Test
    void oneDoseTreatmentPathIsStillValidWithoutAnyCourseData() {
        AfterdreamPlayerProgress progress = new AfterdreamPlayerProgress();
        progress.setStep(AfterdreamMedicalStep.MESSAGE_RECEIVED, 0L);
        progress.setStep(AfterdreamMedicalStep.MESSAGE_READ, 0L);
        progress.setStep(AfterdreamMedicalStep.RECEPTION_READY, 0L);
        progress.setStep(AfterdreamMedicalStep.INTRODUCTION, 0L);
        progress.setStep(AfterdreamMedicalStep.RESULT_LEVEL_ONE, 0L);
        progress.markPotionGranted(0L);
        progress.markMedicalTreatmentCompleted(100L);

        // 一级感染仍走“一瓶药剂清零”的老路径：没有疗程数据也必须完全合法。
        progress.validateState(100L);
        assertEquals(0, progress.getCourseDoses());
        assertEquals(-1L, progress.getCourseCompletedAtActiveTick());
        assertFalse(progress.isFollowUpAwaitingReview());
    }
}
