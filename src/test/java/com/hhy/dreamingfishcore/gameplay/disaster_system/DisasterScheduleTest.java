package com.hhy.dreamingfishcore.gameplay.disaster_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.DayPlan;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.EventType;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.Schedule;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 天灾日程的纯逻辑测试。
 *
 * <p>要钉住的是"玩家能不能提前准备"这件事所依赖的三条性质：确定性、14 天里至少一次大尸潮、
 * 不连续同类型；外加"后 3 日"的查询口径（不含今天）。</p>
 */
class DisasterScheduleTest {

    @Test
    void scheduleCoversExactlyFourteenConsecutiveDays() {
        Schedule schedule = DisasterSchedule.generate(42L, 100L);
        assertEquals(14, schedule.days().size());
        assertEquals(100L, schedule.firstDay());
        assertEquals(113L, schedule.lastDay());
        for (int index = 0; index < 14; index++) {
            assertEquals(100L + index, schedule.days().get(index).day(), "日期必须连续");
        }
        assertTrue(schedule.covers(100L));
        assertTrue(schedule.covers(113L));
        assertFalse(schedule.covers(99L));
        assertFalse(schedule.covers(114L));
    }

    @Test
    void sameSeedAlwaysProducesTheSameSchedule() {
        Schedule first = DisasterSchedule.generate(7L, 50L);
        Schedule second = DisasterSchedule.generate(7L, 50L);
        assertEquals(first.days(), second.days(), "同一个种子排出来的日程必须完全一致");
    }

    @Test
    void differentSeedsCanProduceDifferentSchedules() {
        // 不保证一定不同（小概率撞车），但连续 20 个种子全一样的概率低到可以当成实现错误。
        Schedule base = DisasterSchedule.generate(1L, 0L);
        boolean anyDifferent = false;
        for (long seed = 2L; seed < 22L && !anyDifferent; seed++) {
            anyDifferent = !DisasterSchedule.generate(seed, 0L).days().equals(base.days());
        }
        assertTrue(anyDifferent, "不同种子应该能排出不同的日程");
    }

    @Test
    void everyScheduleContainsAtLeastOneZombieHorde() {
        for (long seed = 0L; seed < 60L; seed++) {
            Schedule schedule = DisasterSchedule.generate(seed, 0L);
            boolean hasHorde = schedule.days().stream()
                    .anyMatch(day -> day.contains(EventType.ZOMBIE_HORDE));
            assertTrue(hasHorde, "14 天里至少要有一次大尸潮（seed=" + seed + "）");
        }
    }

    @Test
    void theSameDisasterNeverHappensTwoDaysInARow() {
        for (long seed = 0L; seed < 60L; seed++) {
            Schedule schedule = DisasterSchedule.generate(seed, 0L);
            List<DayPlan> days = schedule.days();
            for (int index = 1; index < days.size(); index++) {
                for (EventType type : days.get(index).events()) {
                    assertFalse(days.get(index - 1).contains(type),
                            "同一种天灾不该连着两天来：" + type + "（seed=" + seed
                                    + "，第 " + days.get(index).day() + " 天）");
                }
            }
        }
    }

    @Test
    void frostAndStormAreMoreCommonThanTheHorde() {
        Map<EventType, Integer> counts = new EnumMap<>(EventType.class);
        for (EventType type : EventType.values()) {
            counts.put(type, 0);
        }
        int total = 0;
        for (long seed = 0L; seed < 200L; seed++) {
            for (DayPlan day : DisasterSchedule.generate(seed, 0L).days()) {
                for (EventType type : day.events()) {
                    counts.merge(type, 1, Integer::sum);
                    total++;
                }
            }
        }
        assertTrue(total > 0, "200 个种子里居然一条天灾都没有");
        assertTrue(counts.get(EventType.FROST_NIGHT) > counts.get(EventType.ZOMBIE_HORDE),
                "寒霜之夜应该比大尸潮常见：" + counts);
        assertTrue(counts.get(EventType.STORM_DAY) > counts.get(EventType.ZOMBIE_HORDE),
                "风暴天应该比大尸潮常见：" + counts);
    }

    @Test
    void upcomingReturnsTheNextThreeDaysWithoutToday() {
        Schedule schedule = DisasterSchedule.generate(3L, 10L);
        List<DayPlan> upcoming = schedule.upcoming(10L, 3);
        assertEquals(3, upcoming.size());
        assertEquals(11L, upcoming.get(0).day(), "「后 3 日」不含今天");
        assertEquals(13L, upcoming.get(2).day());

        // 日程末尾之后会被截断，而不是抛异常或给出 null
        List<DayPlan> tail = schedule.upcoming(22L, 3);
        assertEquals(1, tail.size(), "只剩第 23 天（日程覆盖到第 23 天）");
        assertEquals(23L, tail.get(0).day());
        assertTrue(schedule.upcoming(30L, 3).isEmpty(), "日程结束之后没有可预告的日子");
    }

    @Test
    void forDayHandlesOutOfRangeGracefully() {
        Schedule schedule = DisasterSchedule.generate(5L, 20L);
        assertNotNull(schedule.forDay(20L));
        assertNotNull(schedule.forDay(33L));
        assertNull(schedule.forDay(19L));
        assertNull(schedule.forDay(34L));
    }

    @Test
    void jsonRoundTripKeepsSeedDaysAndEvents() {
        Schedule schedule = DisasterSchedule.generate(11L, 1L);
        JsonObject json = schedule.toJson();
        Schedule reparsed = Schedule.fromJson(json);

        assertNotNull(reparsed);
        assertEquals(schedule.seed(), reparsed.seed());
        assertEquals(schedule.firstDay(), reparsed.firstDay());
        assertEquals(schedule.days(), reparsed.days(), "存档往返后日程必须一字不差");
    }

    @Test
    void brokenEntriesAreSkippedInsteadOfBreakingTheWholeSchedule() {
        JsonObject json = new JsonObject();
        json.addProperty("seed", 9L);
        json.addProperty("first_day", 5L);
        var days = new com.google.gson.JsonArray();
        var good = new JsonObject();
        good.addProperty("day", 5L);
        var events = new com.google.gson.JsonArray();
        events.add(EventType.STORM_DAY.name());
        events.add("NOT_A_DISASTER");      // 坏类型：只丢这一条
        good.add("events", events);
        days.add(good);
        days.add(new JsonObject());        // 坏条目：没有 day
        json.add("days", days);

        Schedule reparsed = Schedule.fromJson(json);
        assertNotNull(reparsed);
        assertEquals(1, reparsed.days().size());
        assertEquals(List.of(EventType.STORM_DAY), reparsed.days().get(0).events(),
                "坏类型应被跳过，好类型保留");
    }

    @Test
    void describeMakesADayReadable() {
        assertEquals("第 7 天：平安", new DayPlan(7L, List.of()).describe());
        assertEquals("第 8 天：大尸潮", new DayPlan(8L, List.of(EventType.ZOMBIE_HORDE)).describe());
        assertEquals("第 9 天：寒霜之夜、风暴天",
                new DayPlan(9L, List.of(EventType.FROST_NIGHT, EventType.STORM_DAY)).describe());
    }
}
