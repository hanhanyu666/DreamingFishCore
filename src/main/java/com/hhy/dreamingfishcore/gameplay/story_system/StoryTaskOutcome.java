package com.hhy.dreamingfishcore.gameplay.story_system;

/**
 * 一个已经发布的故事任务在世界中的运行状态。
 *
 * <p>这是 {@code enum}（枚举），表示一个任务只可能处于下面三种状态之一。
 * 其中成功和失败都是“已经结算”，只有 ACTIVE 仍可继续结算。</p>
 */
public enum StoryTaskOutcome {
    /** 任务已发布，但还没有得到最终结果。 */
    ACTIVE,
    /** 任务成功结束。 */
    SUCCEEDED,
    /** 任务失败结束；失败也算剧情已经发生，不能正常重开。 */
    FAILED;

    /**
     * 判断任务是否已经得到最终结果。
     *
     * @return 成功或失败时返回 true，仍在进行时返回 false
     */
    public boolean isResolved() {
        return this == SUCCEEDED || this == FAILED;
    }
}
