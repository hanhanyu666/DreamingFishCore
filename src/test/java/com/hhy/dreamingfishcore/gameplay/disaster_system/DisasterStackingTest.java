package com.hhy.dreamingfishcore.gameplay.disaster_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.DayPlan;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.EventType;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 天灾"稀一点、但一有就叠加"的分布。
 *
 * <p>这两件事是配套的：只把平安日权重调高会变成"天灾变少、每次只来一场"，
 * 玩家反而觉得没劲；所以同时让"已经出事的那天"有机会再叠一场。</p>
 *
 * <p>下面用统计区间而不是精确值——权重是设计参数，区间只用来保证"方向没调反"。</p>
 */
class DisasterStackingTest {

    private static final int SEEDS = 300;

    @Test
    void mostDaysAreQuiet() {
        int quiet = 0;
        int total = 0;
        for (long seed = 0L; seed < SEEDS; seed++) {
            for (DayPlan plan : DisasterSchedule.generate(seed, 0L).days()) {
                total++;
                if (!plan.hasEvents()) {
                    quiet++;
                }
            }
        }
        double ratio = (double) quiet / total;
        assertTrue(ratio > 0.50D, "平安日应占多数，实际 " + ratio);
        assertTrue(ratio < 0.75D, "也不能安静到几乎没有天灾，实际 " + ratio);
    }

    @Test
    void disasterDaysStackOften() {
        int disasterDays = 0;
        int stackedDays = 0;
        for (long seed = 0L; seed < SEEDS; seed++) {
            for (DayPlan plan : DisasterSchedule.generate(seed, 0L).days()) {
                if (!plan.hasEvents()) {
                    continue;
                }
                disasterDays++;
                if (plan.events().size() > 1) {
                    stackedDays++;
                }
            }
        }
        double stackedRatio = (double) stackedDays / Math.max(1, disasterDays);
        assertTrue(stackedRatio > 0.20D, "有天灾的日子里应有相当比例是叠加的，实际 " + stackedRatio);
        assertTrue(stackedRatio < 0.55D, "叠加也不能变成常态，实际 " + stackedRatio);
        assertTrue(stackedDays > 0, "300 份日程里一天叠加都没有，说明叠加逻辑没生效");
    }

    @Test
    void aDayNeverRepeatsTheSameDisaster() {
        for (long seed = 0L; seed < SEEDS; seed++) {
            for (DayPlan plan : DisasterSchedule.generate(seed, 0L).days()) {
                List<EventType> events = plan.events();
                assertEquals(events.size(), new HashSet<>(events).size(),
                        "第 " + plan.day() + " 天重复了同一种天灾：" + events);
            }
        }
    }

    @Test
    void stackingStillRespectsTheNoRepeatRuleAcrossDays() {
        for (long seed = 0L; seed < SEEDS; seed++) {
            List<DayPlan> days = DisasterSchedule.generate(seed, 0L).days();
            for (int index = 1; index < days.size(); index++) {
                Set<EventType> yesterday = new HashSet<>(days.get(index - 1).events());
                for (EventType type : days.get(index).events()) {
                    assertTrue(!yesterday.contains(type),
                            "第 " + days.get(index).day() + " 天与前一天重复了 " + type);
                }
            }
        }
    }

    @Test
    void hordeGuaranteeSurvivesStacking() {
        for (long seed = 0L; seed < SEEDS; seed++) {
            boolean hasHorde = DisasterSchedule.generate(seed, 0L).days().stream()
                    .anyMatch(plan -> plan.contains(EventType.ZOMBIE_HORDE));
            assertTrue(hasHorde, "14 天内仍应保证一次大尸潮（seed=" + seed + "）");
        }
    }

    @Test
    void frequencyIsLowerThanBefore() {
        // 旧的平安权重是 45，天灾几乎天天有；现在至少要有明显更多的平安日
        int quiet = 0;
        int total = 0;
        Map<EventType, Integer> counts = new EnumMap<>(EventType.class);
        for (long seed = 0L; seed < 50L; seed++) {
            for (DayPlan plan : DisasterSchedule.generate(seed, 0L).days()) {
                total++;
                if (!plan.hasEvents()) {
                    quiet++;
                }
                plan.events().forEach(type -> counts.merge(type, 1, Integer::sum));
            }
        }
        assertTrue((double) quiet / total > 0.45D, "天灾自然频率应明显降低");
        assertTrue(counts.values().stream().mapToInt(Integer::intValue).sum() > 0,
                "仍要能排出天灾");
    }
}
