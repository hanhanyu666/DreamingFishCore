package com.hhy.dreamingfishcore.gameplay.afterdream_story_system;

/** 余梦期单个玩家的持久化事实。 */
public final class AfterdreamPlayerProgress {
    public enum GuidanceTarget { READ_MESSAGE, ENTER_RECEPTION, MEDICAL_REVIEW, MASK }
    private AfterdreamMedicalStep step = AfterdreamMedicalStep.NOT_STARTED;
    private boolean potionGranted;
    private boolean firstReceptionCompleted;
    private boolean medicalTreatmentCompleted;
    private boolean maskReceived;
    private int jiangwanInteractionCount;
    private long firstReceptionCompletedAtActiveTick = -1L;
    private long startedAtEpochMillis;
    private long updatedAtEpochMillis;

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
        if (!medicalTreatmentCompleted && (medicationApplicable || !firstReceptionCompleted)) {
            targets.add(GuidanceTarget.MEDICAL_REVIEW);
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
                            || next == AfterdreamMedicalStep.MASK_RECEIVED
                            || next == AfterdreamMedicalStep.COMPLETED;
            case AWAITING_TREATMENT -> next == AfterdreamMedicalStep.MASK_RECEIVED
                    || next == AfterdreamMedicalStep.COMPLETED;
            case MASK_RECEIVED -> next == AfterdreamMedicalStep.AWAITING_TREATMENT
                    || next == AfterdreamMedicalStep.COMPLETED;
            case COMPLETED -> next == AfterdreamMedicalStep.MASK_RECEIVED;
        };
    }
}
