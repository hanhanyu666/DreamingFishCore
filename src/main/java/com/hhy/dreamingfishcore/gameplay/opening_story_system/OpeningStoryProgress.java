package com.hhy.dreamingfishcore.gameplay.opening_story_system;

/** 可持久化的第一阶段个人状态。 */
public final class OpeningStoryProgress {
    private OpeningStoryStep step = OpeningStoryStep.NOT_STARTED;
    private boolean starterSupplyGranted;
    private long startedAtEpochMillis;
    private long updatedAtEpochMillis;

    public OpeningStoryProgress() {
    }

    public OpeningStoryStep getStep() {
        return step == null ? OpeningStoryStep.NOT_STARTED : step;
    }

    public boolean isStarterSupplyGranted() {
        return starterSupplyGranted;
    }

    /** 服主发布前情后的接入，不代替玩家选择身份或领取物资。 */
    public boolean enterMembershipFromRecap(long now) {
        if (getStep() == OpeningStoryStep.CHOOSE_MEMBERSHIP
                || getStep() == OpeningStoryStep.BUILD_ZHUIGUANG_BASE
                || getStep() == OpeningStoryStep.DECLINED_ZHUIGUANG) {
            return false;
        }
        step = OpeningStoryStep.CHOOSE_MEMBERSHIP;
        updatedAtEpochMillis = Math.max(0L, now);
        return true;
    }

    public long getStartedAtEpochMillis() {
        return startedAtEpochMillis;
    }

    public long getUpdatedAtEpochMillis() {
        return updatedAtEpochMillis;
    }

    /** 只允许沿作者规定的第一阶段链路前进。 */
    public boolean advanceTo(OpeningStoryStep next, long now) {
        if (!isAllowedTransition(getStep(), next)) {
            return false;
        }
        if (getStep() == OpeningStoryStep.NOT_STARTED) {
            startedAtEpochMillis = Math.max(0L, now);
        }
        step = next;
        updatedAtEpochMillis = Math.max(0L, now);
        return true;
    }

    public boolean markStarterSupplyGranted(long now) {
        if (starterSupplyGranted || getStep() != OpeningStoryStep.BUILD_ZHUIGUANG_BASE) {
            return false;
        }
        starterSupplyGranted = true;
        updatedAtEpochMillis = Math.max(0L, now);
        return true;
    }

    /** Gson 读取后修复可以安全修复的容器值；非法状态不会静默跳跃。 */
    public boolean repair() {
        boolean changed = false;
        if (step == null) {
            step = OpeningStoryStep.NOT_STARTED;
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
        return changed;
    }

    public void validateState() {
        if (step == null) {
            throw new IllegalStateException("开场阶段状态不能为空");
        }
        if (startedAtEpochMillis < 0L || updatedAtEpochMillis < 0L) {
            throw new IllegalStateException("开场阶段时间戳不能为负数");
        }
        if (step == OpeningStoryStep.NOT_STARTED && starterSupplyGranted) {
            throw new IllegalStateException("尚未开始开场阶段却已经领取补给");
        }
        if (starterSupplyGranted && step != OpeningStoryStep.BUILD_ZHUIGUANG_BASE) {
            throw new IllegalStateException("补给领取状态与开场阶段不一致");
        }
    }

    private static boolean isAllowedTransition(OpeningStoryStep current, OpeningStoryStep next) {
        if (current == null || next == null) {
            return false;
        }
        return switch (current) {
            case NOT_STARTED -> next == OpeningStoryStep.TRAVEL_TO_ABYDOS;
            case TRAVEL_TO_ABYDOS -> next == OpeningStoryStep.TALK_TO_BAIZHI;
            case TALK_TO_BAIZHI -> next == OpeningStoryStep.CONTACT_ZHOUCEN;
            case CONTACT_ZHOUCEN -> next == OpeningStoryStep.CHOOSE_MEMBERSHIP;
            case CHOOSE_MEMBERSHIP -> next == OpeningStoryStep.BUILD_ZHUIGUANG_BASE
                    || next == OpeningStoryStep.DECLINED_ZHUIGUANG;
            case BUILD_ZHUIGUANG_BASE, DECLINED_ZHUIGUANG -> false;
        };
    }
}
