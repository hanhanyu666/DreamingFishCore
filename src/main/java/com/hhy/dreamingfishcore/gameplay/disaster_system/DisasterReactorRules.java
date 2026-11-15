package com.hhy.dreamingfishcore.gameplay.disaster_system;

import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.EventType;

/**
 * 天灾反应器的规则（纯函数，可单测）。
 *
 * <p>把"掷骰子"与"能不能清"从方块代码里抽出来，一是好测，二是以后想加
 * （比如稳定性更高的数据、或按天灾类型设定不同成功率）只改这里。</p>
 */
public final class DisasterReactorRules {

    /** 插入时数据损坏的概率（百分比）。 */
    public static final int CORRUPTION_PERCENT = 25;

    private DisasterReactorRules() {
    }

    /**
     * 这次插入的数据是否损坏。
     *
     * @param roll 0~99 的掷骰结果
     */
    public static boolean isCorrupted(int roll) {
        return roll >= 0 && roll < CORRUPTION_PERCENT;
    }

    /** 这次插入能否清掉天灾：今天确实安排了这一场，且数据没坏。 */
    public static boolean clears(boolean scheduledToday, boolean corrupted) {
        return scheduledToday && !corrupted;
    }

    /** 数据是否应该被消耗：只要它是合法数据，插进去就会消耗（损坏也消耗）。 */
    public static boolean consumes(boolean validData) {
        return validData;
    }

    /** 今天是否安排了这一场天灾（没有天灾日的计划也要能安全返回 false）。 */
    public static boolean scheduledToday(DisasterSchedule.DayPlan plan, EventType type) {
        return plan != null && type != null && plan.contains(type);
    }
}
