package com.hhy.dreamingfishcore.gameplay.disaster_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.DayPlan;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.EventType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 天灾反应器的纯规则。
 *
 * <p>要钉住的是"25% 损坏"这条边界的**准确含义**：0~24 算损坏、25~99 算成功，
 * 差一位就会变成 26% 或 24%，而这种偏差在游戏里没人能立刻发现。</p>
 */
class DisasterReactorRulesTest {

    @Test
    void corruptionIsExactlyTheFirstQuarter() {
        assertEquals(25, DisasterReactorRules.CORRUPTION_PERCENT);
        for (int roll = 0; roll < 25; roll++) {
            assertTrue(DisasterReactorRules.isCorrupted(roll), "掷出 " + roll + " 应当损坏");
        }
        for (int roll = 25; roll < 100; roll++) {
            assertFalse(DisasterReactorRules.isCorrupted(roll), "掷出 " + roll + " 不应损坏");
        }
    }

    @Test
    void illegalRollsAreTreatedAsSuccess() {
        assertFalse(DisasterReactorRules.isCorrupted(-1), "越界负数不该算损坏");
        assertFalse(DisasterReactorRules.isCorrupted(100), "越界 100 不该算损坏");
    }

    @Test
    void clearsOnlyWhenScheduledAndNotCorrupted() {
        assertTrue(DisasterReactorRules.clears(true, false));
        assertFalse(DisasterReactorRules.clears(true, true));
        assertFalse(DisasterReactorRules.clears(false, false));
        assertFalse(DisasterReactorRules.clears(false, true));
    }

    @Test
    void onlyValidDataIsConsumed() {
        assertTrue(DisasterReactorRules.consumes(true));
        assertFalse(DisasterReactorRules.consumes(false));
    }

    @Test
    void scheduledTodayHandlesNullPlanAndMultiEventDays() {
        DayPlan plan = new DayPlan(12L, List.of(EventType.STORM_DAY, EventType.SPORE_PLAGUE));
        assertTrue(DisasterReactorRules.scheduledToday(plan, EventType.STORM_DAY));
        assertTrue(DisasterReactorRules.scheduledToday(plan, EventType.SPORE_PLAGUE));
        assertFalse(DisasterReactorRules.scheduledToday(plan, EventType.FROST_NIGHT),
                "同一天叠加多场时，只对真的安排了的那一场生效");
        assertFalse(DisasterReactorRules.scheduledToday(null, EventType.FROST_NIGHT));
        assertFalse(DisasterReactorRules.scheduledToday(plan, null));
    }
}
