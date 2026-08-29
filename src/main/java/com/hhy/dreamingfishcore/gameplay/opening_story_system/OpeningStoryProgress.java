package com.hhy.dreamingfishcore.gameplay.opening_story_system;

/** 可持久化的单人开场任务状态。 */
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

    public long getStartedAtEpochMillis() {
        return startedAtEpochMillis;
    }

    public long getUpdatedAtEpochMillis() {
        return updatedAtEpochMillis;
    }

    /** 只允许沿作者规定的任务链前进，避免网络包跳过中间步骤。 */
    boolean advanceTo(OpeningStoryStep next, long now) {
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

    boolean markStarterSupplyGranted(long now) {
        if (starterSupplyGranted || getStep() != OpeningStoryStep.BUILD_ZHUIGUANG_BASE) {
            return false;
        }
        starterSupplyGranted = true;
        updatedAtEpochMillis = Math.max(0L, now);
        return true;
    }

    /** Gson 读取旧文件后补齐空枚举与非法时间。 */
    boolean repair() {
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
