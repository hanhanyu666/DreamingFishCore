package com.hhy.dreamingfishcore.gameplay.afterdream_story_system;

/** 余梦期单个玩家的持久化事实。 */
public final class AfterdreamPlayerProgress {
    public enum GuidanceTarget {
        READ_MESSAGE,
        ENTER_RECEPTION,
        MEDICAL_REVIEW,
        COURSE,
        FOLLOW_UP,
        MASK
    }

    /** 三次早期逆转疗程的用药次数上限。 */
    public static final int COURSE_TOTAL_DOSES = 3;
    /** 随访天数：终检完成后第 3 天与第 7 天各一次。 */
    public static final int FOLLOW_UP_THIRD_DAY = 3;
    public static final int FOLLOW_UP_SEVENTH_DAY = 7;

    private AfterdreamMedicalStep step = AfterdreamMedicalStep.NOT_STARTED;
    private boolean potionGranted;
    private boolean firstReceptionCompleted;
    private boolean medicalTreatmentCompleted;
    private boolean maskReceived;
    private int jiangwanInteractionCount;
    private long firstReceptionCompletedAtActiveTick = -1L;
    private long startedAtEpochMillis;
    private long updatedAtEpochMillis;

    // ===== 二次/重症感染的三次早期逆转疗程 =====
    /** 已完成的用药次数，0..COURSE_TOTAL_DOSES。 */
    private int courseDoses;
    private long courseStartedAtActiveTick = -1L;
    /** 下一次用药可以执行的剧情活动 tick；-1 表示疗程尚未开始。 */
    private long courseNextDoseAtActiveTick = -1L;
    /** 终检完成时间；-1 表示尚未完成。 */
    private long courseCompletedAtActiveTick = -1L;

    // ===== 终检后的第 3 / 7 天随访（白芷负责提醒与复核） =====
    private boolean followUpThirdDayNotified;
    private boolean followUpThirdDayCompleted;
    private boolean followUpSeventhDayNotified;
    private boolean followUpSeventhDayCompleted;
    private long lastFollowUpNotifiedAtActiveTick = -1L;

    public AfterdreamPlayerProgress() {
    }

    public AfterdreamMedicalStep getStep() {
        return step == null ? AfterdreamMedicalStep.NOT_STARTED : step;
    }

    /** 旧会话允许重读；重复事件不能把后续步骤的引导恢复到接待入口。 */
    public boolean recordMessageRead(long activeTick) {
        return switch (getStep()) {
            case NOT_STARTED, MESSAGE_RECEIVED -> advanceTo(AfterdreamMedicalStep.MESSAGE_READ, activeTick);
            default -> false;
        };
    }

    /** 当前仍可执行的行动；同时用于重复事件修复和登录后的投影重建。 */
    public java.util.Set<GuidanceTarget> pendingGuidance(boolean maskAvailable) {
        return pendingGuidance(maskAvailable, true);
    }

    public java.util.Set<GuidanceTarget> pendingGuidance(boolean maskAvailable, boolean medicationApplicable) {
        if (getStep() == AfterdreamMedicalStep.NOT_STARTED) {
            return java.util.Set.of();
        }
        if (getStep() == AfterdreamMedicalStep.MESSAGE_RECEIVED) {
            return java.util.Set.of(GuidanceTarget.READ_MESSAGE);
        }
        if (getStep() == AfterdreamMedicalStep.MESSAGE_READ) {
            return java.util.Set.of(GuidanceTarget.ENTER_RECEPTION);
        }
        java.util.Set<GuidanceTarget> targets = java.util.EnumSet.noneOf(GuidanceTarget.class);
        if (!medicalTreatmentCompleted && !isCourseActive()
                && (medicationApplicable || !firstReceptionCompleted)) {
            targets.add(GuidanceTarget.MEDICAL_REVIEW);
        }
        if (isCourseActive()) {
            targets.add(GuidanceTarget.COURSE);
        }
        if (isFollowUpAwaitingReview()) {
            targets.add(GuidanceTarget.FOLLOW_UP);
        }
        if (firstReceptionCompleted && maskAvailable && !maskReceived) {
            targets.add(GuidanceTarget.MASK);
        }
        return java.util.Set.copyOf(targets);
    }

    /** 前情接入只改变待执行步骤，不伪造接待、用药或物品领取事实。 */
    public boolean enterReceptionFromRecap() {
        if (getStep() != AfterdreamMedicalStep.NOT_STARTED
                && getStep() != AfterdreamMedicalStep.MESSAGE_RECEIVED
                && getStep() != AfterdreamMedicalStep.MESSAGE_READ) {
            return false;
        }
        step = AfterdreamMedicalStep.RECEPTION_READY;
        updatedAtEpochMillis = Math.max(0L, System.currentTimeMillis());
        return true;
    }

    public boolean isPotionGranted() {
        return potionGranted;
    }

    public boolean isFirstReceptionCompleted() {
        return firstReceptionCompleted;
    }

    public boolean isMedicalTreatmentCompleted() {
        return medicalTreatmentCompleted;
    }

    public boolean isMaskReceived() {
        return maskReceived;
    }

    public int getJiangwanInteractionCount() {
        return jiangwanInteractionCount;
    }

    public long getFirstReceptionCompletedAtActiveTick() {
        return firstReceptionCompletedAtActiveTick;
    }

    public long getStartedAtEpochMillis() {
        return startedAtEpochMillis;
    }

    public long getUpdatedAtEpochMillis() {
        return updatedAtEpochMillis;
    }

    /** 沿余梦期作者明确写出的状态边迁移；重复设置同一状态视为无变化。 */
    public boolean advanceTo(AfterdreamMedicalStep next, long activeTick) {
        if (next == null) {
            throw new IllegalArgumentException("余梦期状态不能为空");
        }
        AfterdreamMedicalStep current = getStep();
        if (current == next) {
            return false;
        }
        if (!isAllowedTransition(current, next)) {
            throw new IllegalStateException("余梦期非法状态迁移：" + current + " -> " + next);
        }
        if (current == AfterdreamMedicalStep.NOT_STARTED) {
            startedAtEpochMillis = Math.max(0L, System.currentTimeMillis());
        }
        step = next;
        updatedAtEpochMillis = Math.max(0L, System.currentTimeMillis());
        if (next == AfterdreamMedicalStep.COMPLETED) {
            medicalTreatmentCompleted = true;
        }
        return true;
    }

    /** 阶段脚本内部的简短别名，保留一个唯一迁移实现。 */
    void setStep(AfterdreamMedicalStep next, long activeTick) {
        advanceTo(next, activeTick);
    }

    public void markPotionGranted(long activeTick) {
        potionGranted = true;
        firstReceptionCompleted = true;
        firstReceptionCompletedAtActiveTick = Math.max(0L, activeTick);
        updatedAtEpochMillis = Math.max(0L, System.currentTimeMillis());
    }

    public void markFirstReceptionCompleted(long activeTick) {
        firstReceptionCompleted = true;
        firstReceptionCompletedAtActiveTick = Math.max(0L, activeTick);
        updatedAtEpochMillis = Math.max(0L, System.currentTimeMillis());
    }

    public void markMedicalTreatmentCompleted(long activeTick) {
        medicalTreatmentCompleted = true;
        if (getStep() != AfterdreamMedicalStep.COMPLETED) {
            advanceTo(AfterdreamMedicalStep.COMPLETED, activeTick);
        }
        updatedAtEpochMillis = Math.max(0L, System.currentTimeMillis());
    }

    public void markMaskReceived(long activeTick) {
        maskReceived = true;
        updatedAtEpochMillis = Math.max(0L, System.currentTimeMillis());
    }

    public void incrementJiangwanInteraction(long activeTick) {
        if (jiangwanInteractionCount < Integer.MAX_VALUE) {
            jiangwanInteractionCount++;
        }
        updatedAtEpochMillis = Math.max(0L, System.currentTimeMillis());
    }

    // ==================== 三次早期逆转疗程 ====================

    /**
     * 一个“剧情活动日”的长度（tick）。与面膜倒计时、早期治疗窗口使用的活动时间口径一致：
     * 只有至少一名认证玩家在线时才累计。
     */
    public static final long ACTIVE_TICKS_PER_DAY = 24_000L;

    /** 疗程是否正在进行。 */
    public boolean isCourseActive() {
        return getStep() == AfterdreamMedicalStep.COURSE_IN_PROGRESS;
    }

    public int getCourseDoses() {
        return courseDoses;
    }

    public long getCourseStartedAtActiveTick() {
        return courseStartedAtActiveTick;
    }

    public long getCourseNextDoseAtActiveTick() {
        return courseNextDoseAtActiveTick;
    }

    public long getCourseCompletedAtActiveTick() {
        return courseCompletedAtActiveTick;
    }

    /** 下一次用药是否已经到时间（间隔按剧情活动 tick 计算）。 */
    public boolean isNextDoseAvailable(long activeTick) {
        return isCourseActive()
                && courseDoses < COURSE_TOTAL_DOSES
                && courseNextDoseAtActiveTick >= 0L
                && activeTick >= courseNextDoseAtActiveTick;
    }

    /** 三次用药完成后才可以终检。 */
    public boolean isFinalCheckReady() {
        return isCourseActive() && courseDoses >= COURSE_TOTAL_DOSES;
    }

    /**
     * 进入疗程。进入当次交互即可领取第一剂；之后每次间隔由调用方给出的活动 tick 决定。
     */
    public boolean startCourse(long activeTick) {
        if (!advanceTo(AfterdreamMedicalStep.COURSE_IN_PROGRESS, activeTick)) {
            return false;
        }
        long tick = Math.max(0L, activeTick);
        if (courseStartedAtActiveTick < 0L) {
            courseStartedAtActiveTick = tick;
        }
        courseNextDoseAtActiveTick = tick;
        return true;
    }

    /**
     * 记录一次实际完成的用药。
     *
     * @return false 表示这次交互不应发放药剂：疗程未开始、次数已满，或还没到下一次可用时间。
     */
    public boolean recordCourseDose(long activeTick, long doseIntervalTicks) {
        if (!isNextDoseAvailable(activeTick)) {
            return false;
        }
        courseDoses++;
        if (courseDoses >= COURSE_TOTAL_DOSES) {
            // 三次之后不再有等待间隔：下一次与江晚交互直接进入终检。
            courseNextDoseAtActiveTick = -1L;
        } else {
            courseNextDoseAtActiveTick = Math.max(0L, activeTick) + Math.max(0L, doseIntervalTicks);
        }
        updatedAtEpochMillis = Math.max(0L, System.currentTimeMillis());
        return true;
    }

    /** 终检完成：感染值清零由调用方负责，这里只记录事实并进入完成状态。 */
    public boolean completeCourse(long activeTick) {
        if (!isFinalCheckReady()) {
            return false;
        }
        courseCompletedAtActiveTick = Math.max(0L, activeTick);
        markMedicalTreatmentCompleted(activeTick);
        return true;
    }

    /**
     * 仅测试用：不检查“间隔是否已到”就记入一次用药，用于把玩家快进到疗程中段。
     *
     * <p>正式流程请使用 {@link #recordCourseDose}。这里仍然复用同一份字段与
     * 次数上限规则，只是跳过间隔检查，因此不会产生不可能通过校验的存档。</p>
     */
    boolean recordCourseDoseForTesting(long doseTick, long doseIntervalTicks) {
        if (!isCourseActive() || courseDoses >= COURSE_TOTAL_DOSES) {
            return false;
        }
        courseDoses++;
        if (courseDoses >= COURSE_TOTAL_DOSES) {
            courseNextDoseAtActiveTick = -1L;
        } else {
            courseNextDoseAtActiveTick = Math.max(0L, doseTick) + Math.max(0L, doseIntervalTicks);
        }
        updatedAtEpochMillis = Math.max(0L, System.currentTimeMillis());
        return true;
    }

    // ==================== 终检后的第 3 / 7 天随访 ====================

    /** 当前应当提醒的随访天数；没有待提醒项时返回 0。 */
    public int pendingFollowUpDay(long activeTick) {
        if (courseCompletedAtActiveTick < 0L) {
            return 0;
        }
        if (!followUpThirdDayNotified
                && activeTick >= courseCompletedAtActiveTick + FOLLOW_UP_THIRD_DAY * ACTIVE_TICKS_PER_DAY) {
            return FOLLOW_UP_THIRD_DAY;
        }
        if (!followUpSeventhDayNotified
                && activeTick >= courseCompletedAtActiveTick + FOLLOW_UP_SEVENTH_DAY * ACTIVE_TICKS_PER_DAY) {
            return FOLLOW_UP_SEVENTH_DAY;
        }
        return 0;
    }

    /** 记录“白芷已经提醒过这次随访”。第 7 天必须晚于第 3 天提醒。 */
    public boolean markFollowUpNotified(int day, long activeTick) {
        if (day == FOLLOW_UP_THIRD_DAY) {
            if (followUpThirdDayNotified) {
                return false;
            }
            followUpThirdDayNotified = true;
        } else if (day == FOLLOW_UP_SEVENTH_DAY) {
            if (!followUpThirdDayNotified || followUpSeventhDayNotified) {
                return false;
            }
            followUpSeventhDayNotified = true;
        } else {
            return false;
        }
        lastFollowUpNotifiedAtActiveTick = Math.max(0L, activeTick);
        updatedAtEpochMillis = Math.max(0L, System.currentTimeMillis());
        return true;
    }

    /**
     * 随访复核完成。按照“无随机失败、缺席只记录”的设定，逾期或漏做不会带来任何惩罚，
     * 这里只把实际发生过的事实写下来。
     */
    public boolean recordFollowUpCompletion(int day, long activeTick) {
        if (day == FOLLOW_UP_THIRD_DAY) {
            if (!followUpThirdDayNotified || followUpThirdDayCompleted) {
                return false;
            }
            followUpThirdDayCompleted = true;
        } else if (day == FOLLOW_UP_SEVENTH_DAY) {
            if (!followUpSeventhDayNotified || followUpSeventhDayCompleted) {
                return false;
            }
            followUpSeventhDayCompleted = true;
        } else {
            return false;
        }
        updatedAtEpochMillis = Math.max(0L, System.currentTimeMillis());
        return true;
    }

    /** 是否存在“已经提醒、但还没复核”的随访。 */
    public boolean isFollowUpAwaitingReview() {
        return (followUpThirdDayNotified && !followUpThirdDayCompleted)
                || (followUpSeventhDayNotified && !followUpSeventhDayCompleted);
    }

    public boolean isFollowUpThirdDayNotified() {
        return followUpThirdDayNotified;
    }

    public boolean isFollowUpThirdDayCompleted() {
        return followUpThirdDayCompleted;
    }

    public boolean isFollowUpSeventhDayNotified() {
        return followUpSeventhDayNotified;
    }

    public boolean isFollowUpSeventhDayCompleted() {
        return followUpSeventhDayCompleted;
    }

    public long getLastFollowUpNotifiedAtActiveTick() {
        return lastFollowUpNotifiedAtActiveTick;
    }

    /** 校验当前阶段事实的内部一致性。 */
    public void validateState(long activeTick) {
        if (step == null) {
            throw new IllegalStateException("余梦期状态不能为空");
        }
        if (jiangwanInteractionCount < 0) {
            throw new IllegalStateException("江晚交互次数不能为负数");
        }
        if (firstReceptionCompletedAtActiveTick < -1L
                || firstReceptionCompletedAtActiveTick > activeTick) {
            throw new IllegalStateException("首次医疗接待时间非法");
        }
        if (startedAtEpochMillis < 0L || updatedAtEpochMillis < 0L) {
            throw new IllegalStateException("余梦期时间戳不能为负数");
        }
        if (firstReceptionCompleted && firstReceptionCompletedAtActiveTick < 0L) {
            throw new IllegalStateException("已完成首次接待但缺少完成时间");
        }
        if (potionGranted && !firstReceptionCompleted) {
            throw new IllegalStateException("已发放药剂但首次接待尚未完成");
        }
        if (medicalTreatmentCompleted && !firstReceptionCompleted) {
            throw new IllegalStateException("已完成治疗但首次接待尚未完成");
        }
        if (maskReceived && !firstReceptionCompleted) {
            throw new IllegalStateException("已领取面具但首次接待尚未完成");
        }
        if (courseDoses < 0 || courseDoses > COURSE_TOTAL_DOSES) {
            throw new IllegalStateException("疗程用药次数非法：" + courseDoses);
        }
        if (courseStartedAtActiveTick < -1L || courseStartedAtActiveTick > activeTick) {
            throw new IllegalStateException("疗程开始时间非法");
        }
        if (courseNextDoseAtActiveTick < -1L) {
            throw new IllegalStateException("疗程下次用药时间非法");
        }
        if (courseCompletedAtActiveTick < -1L || courseCompletedAtActiveTick > activeTick) {
            throw new IllegalStateException("疗程终检时间非法");
        }
        if (isCourseActive() && courseStartedAtActiveTick < 0L) {
            throw new IllegalStateException("疗程进行中但缺少开始时间");
        }
        if (courseDoses > 0 && courseStartedAtActiveTick < 0L) {
            throw new IllegalStateException("已用药但缺少疗程开始时间");
        }
        if (courseCompletedAtActiveTick >= 0L && courseDoses < COURSE_TOTAL_DOSES) {
            throw new IllegalStateException("疗程终检完成但用药次数不足");
        }
        if (courseCompletedAtActiveTick >= 0L && !medicalTreatmentCompleted) {
            throw new IllegalStateException("疗程终检完成但医疗处理未完成");
        }
        if (lastFollowUpNotifiedAtActiveTick < -1L || lastFollowUpNotifiedAtActiveTick > activeTick) {
            throw new IllegalStateException("随访提醒时间非法");
        }
        if (followUpSeventhDayNotified && !followUpThirdDayNotified) {
            throw new IllegalStateException("第 7 天随访提醒早于第 3 天");
        }
        if ((followUpThirdDayCompleted && !followUpThirdDayNotified)
                || (followUpSeventhDayCompleted && !followUpSeventhDayNotified)) {
            throw new IllegalStateException("随访复核早于随访提醒");
        }
        if (courseCompletedAtActiveTick < 0L
                && (followUpThirdDayNotified || followUpThirdDayCompleted
                || followUpSeventhDayNotified || followUpSeventhDayCompleted)) {
            throw new IllegalStateException("尚未完成疗程终检却已有随访记录");
        }
    }

    /** 只修复 Gson 容器默认值；不会迁移旧剧情文件或跳过业务状态。 */
    public boolean repair() {
        boolean changed = false;
        if (step == null) {
            step = AfterdreamMedicalStep.NOT_STARTED;
            changed = true;
        }
        if (jiangwanInteractionCount < 0) {
            jiangwanInteractionCount = 0;
            changed = true;
        }
        if (firstReceptionCompletedAtActiveTick < -1L) {
            firstReceptionCompletedAtActiveTick = -1L;
            changed = true;
        }
        if (startedAtEpochMillis < 0L) {
            startedAtEpochMillis = 0L;
            changed = true;
        }
        if (updatedAtEpochMillis < 0L) {
            updatedAtEpochMillis = 0L;
            changed = true;
        }
        if (step == AfterdreamMedicalStep.COMPLETED && !medicalTreatmentCompleted) {
            medicalTreatmentCompleted = true;
            changed = true;
        }
        if (medicalTreatmentCompleted && step != AfterdreamMedicalStep.COMPLETED) {
            step = AfterdreamMedicalStep.COMPLETED;
            changed = true;
        }
        if (potionGranted && !firstReceptionCompleted) {
            firstReceptionCompleted = true;
            changed = true;
        }
        if (courseDoses < 0) {
            courseDoses = 0;
            changed = true;
        }
        if (courseDoses > COURSE_TOTAL_DOSES) {
            courseDoses = COURSE_TOTAL_DOSES;
            changed = true;
        }
        if (courseStartedAtActiveTick < -1L) {
            courseStartedAtActiveTick = -1L;
            changed = true;
        }
        if (courseNextDoseAtActiveTick < -1L) {
            courseNextDoseAtActiveTick = -1L;
            changed = true;
        }
        if (courseCompletedAtActiveTick < -1L) {
            courseCompletedAtActiveTick = -1L;
            changed = true;
        }
        if (lastFollowUpNotifiedAtActiveTick < -1L) {
            lastFollowUpNotifiedAtActiveTick = -1L;
            changed = true;
        }
        // 容器默认值可能留下“还没有终检就有随访记录”这种组合；它无法对应任何真实经历，
        // 只能整体清掉，不能靠补时间戳伪造随访。
        if (courseCompletedAtActiveTick < 0L
                && (followUpThirdDayNotified || followUpThirdDayCompleted
                || followUpSeventhDayNotified || followUpSeventhDayCompleted)) {
            followUpThirdDayNotified = false;
            followUpThirdDayCompleted = false;
            followUpSeventhDayNotified = false;
            followUpSeventhDayCompleted = false;
            changed = true;
        }
        if (followUpSeventhDayNotified && !followUpThirdDayNotified) {
            followUpSeventhDayNotified = false;
            followUpSeventhDayCompleted = false;
            changed = true;
        }
        return changed;
    }

    private static boolean isAllowedTransition(
            AfterdreamMedicalStep current, AfterdreamMedicalStep next) {
        return switch (current) {
            case NOT_STARTED -> next == AfterdreamMedicalStep.MESSAGE_RECEIVED
                    || next == AfterdreamMedicalStep.MESSAGE_READ;
            case MESSAGE_RECEIVED -> next == AfterdreamMedicalStep.MESSAGE_READ;
            case MESSAGE_READ -> next == AfterdreamMedicalStep.RECEPTION_READY;
            case RECEPTION_READY -> next == AfterdreamMedicalStep.INTRODUCTION;
            case INTRODUCTION -> next == AfterdreamMedicalStep.RESULT_LEVEL_ONE
                    || next == AfterdreamMedicalStep.RESULT_NONINFECTED
                    || next == AfterdreamMedicalStep.RESULT_LEVEL_TWO;
            case RESULT_LEVEL_ONE, RESULT_NONINFECTED, RESULT_LEVEL_TWO ->
                    next == AfterdreamMedicalStep.AWAITING_TREATMENT
                            || next == AfterdreamMedicalStep.COURSE_IN_PROGRESS
                            || next == AfterdreamMedicalStep.MASK_RECEIVED
                            || next == AfterdreamMedicalStep.COMPLETED;
            case AWAITING_TREATMENT -> next == AfterdreamMedicalStep.COURSE_IN_PROGRESS
                    || next == AfterdreamMedicalStep.MASK_RECEIVED
                    || next == AfterdreamMedicalStep.COMPLETED;
            case COURSE_IN_PROGRESS -> next == AfterdreamMedicalStep.MASK_RECEIVED
                    || next == AfterdreamMedicalStep.COMPLETED;
            case MASK_RECEIVED -> next == AfterdreamMedicalStep.AWAITING_TREATMENT
                    || next == AfterdreamMedicalStep.COURSE_IN_PROGRESS
                    || next == AfterdreamMedicalStep.COMPLETED;
            case COMPLETED -> next == AfterdreamMedicalStep.MASK_RECEIVED;
        };
    }
}
