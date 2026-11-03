package com.hhy.dreamingfishcore.gameplay.disaster_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 天灾的时间与规模计算（纯函数）。
 *
 * <p>钉住两件事：傍晚提醒的窗口不能漏也不能重复触发；尸潮规模**随天数单调上升、随人数次线性放大、
 * 并且有硬上限**——否则长期存档里会出现"几百只一起刷"的灭世场面。</p>
 */
class DisasterTimingTest {

    @Test
    void eveningWindowCoversSunsetAndStopsBeforeMidnight() {
        assertFalse(DisasterTiming.isEveningWindow(0L), "清晨不该触发");
        assertFalse(DisasterTiming.isEveningWindow(6_000L), "正午不该触发");
        assertTrue(DisasterTiming.isEveningWindow(11_000L), "傍晚窗口起点应包含");
        assertTrue(DisasterTiming.isEveningWindow(12_000L), "日落时刻应在窗口内");
        assertTrue(DisasterTiming.isEveningWindow(12_999L), "窗口末尾前一 tick 仍算");
        assertFalse(DisasterTiming.isEveningWindow(13_000L), "过了窗口就不提醒了");
        assertFalse(DisasterTiming.isEveningWindow(18_000L), "午夜不该提醒");
    }

    @Test
    void eveningWindowWorksAcrossMultipleDays() {
        // 第二天、第三天的同一时刻也应该命中（用 floorMod，不看总时间）
        assertTrue(DisasterTiming.isEveningWindow(24_000L + 11_500L));
        assertTrue(DisasterTiming.isEveningWindow(3L * 24_000L + 12_500L));
        assertFalse(DisasterTiming.isEveningWindow(3L * 24_000L + 2_000L));
    }

    @Test
    void dayFactorStartsAtOneAndGrowsTenPercentPerDay() {
        assertEquals(1.0D, DisasterTiming.dayFactor(0L), "第 0 天按 1.0 起算");
        assertEquals(1.0D, DisasterTiming.dayFactor(1L), "第 1 天 1.0");
        assertEquals(1.1D, DisasterTiming.dayFactor(2L), 1e-9, "第 2 天 +10%");
        assertEquals(1.2D, DisasterTiming.dayFactor(3L), 1e-9, "第 3 天 +20%");
        assertEquals(2.0D, DisasterTiming.dayFactor(11L), 1e-9, "第 11 天 2.0");
    }

    @Test
    void dayFactorIsCappedSoLongRunningWorldsStayPlayable() {
        assertEquals(DisasterTiming.MAX_DAY_FACTOR, DisasterTiming.dayFactor(50L), 1e-9);
        assertEquals(DisasterTiming.MAX_DAY_FACTOR, DisasterTiming.dayFactor(10_000L), 1e-9);
        // 单调不减（在封顶之前）
        double previous = 0.0D;
        for (long day = 1L; day <= 40L; day++) {
            double factor = DisasterTiming.dayFactor(day);
            assertTrue(factor >= previous, "天数系数不应下降（day=" + day + "）");
            previous = factor;
        }
    }

    @Test
    void playerFactorIsSublinear() {
        assertEquals(1.0D, DisasterTiming.playerFactor(0), 1e-9);
        assertEquals(1.0D, DisasterTiming.playerFactor(1), 1e-9);
        assertEquals(1.2D, DisasterTiming.playerFactor(2), 1e-9);
        assertEquals(1.35D, DisasterTiming.playerFactor(3), 1e-9);
        assertEquals(1.5D, DisasterTiming.playerFactor(4), 1e-9);
        assertEquals(1.5D, DisasterTiming.playerFactor(20), 1e-9, "超过 4 人不再叠加");
        assertTrue(DisasterTiming.playerFactor(10) < 10.0D, "绝不能是线性放大");
    }

    @Test
    void hordePerPlayerIsAtLeastOneAndCappedAtTripleBase() {
        int base = 12;
        for (long day = 0L; day < 200L; day += 7L) {
            for (int players = 1; players <= 10; players++) {
                int count = DisasterTiming.hordePerPlayer(day, players, base);
                assertTrue(count >= 1, "至少 1 只");
                assertTrue(count <= base * 3, "不超过基础值 3 倍：" + count);
            }
        }
        // 第 1 天单人 = 基础值
        assertEquals(base, DisasterTiming.hordePerPlayer(1L, 1, base));
        // 后期明显更强，但仍在上限内
        assertTrue(DisasterTiming.hordePerPlayer(11L, 1, base) > base);
        assertEquals(base * 3, DisasterTiming.hordePerPlayer(50L, 4, base));
    }

    @Test
    void zeroBaseMeansNoHorde() {
        assertEquals(0, DisasterTiming.hordePerPlayer(5L, 3, 0));
        assertEquals(0, DisasterTiming.hordePerBatch(5L, 3, 0, 4));
    }

    @Test
    void hordeTotalHasAHardCap() {
        assertEquals(12, DisasterTiming.hordeTotalCap(0), "没人时也给个下限，避免除零");
        assertEquals(40, DisasterTiming.hordeTotalCap(1));
        assertEquals(120, DisasterTiming.hordeTotalCap(3));
        assertEquals(240, DisasterTiming.hordeTotalCap(100), "人数再多也不超过 240");
    }

    @Test
    void perBatchSplitsTheTotalAndNeverExceedsTheCap() {
        int base = 12;
        for (int players = 1; players <= 6; players++) {
            int perBatch = DisasterTiming.hordePerBatch(5L, players, base, 4);
            int total = DisasterTiming.hordePerPlayer(5L, players, base) * players;
            assertTrue(perBatch * 4 <= Math.max(total, 0) + 4 * players,
                    "每批×批次数不应明显超过总量（players=" + players + "）");
        }
        assertEquals(0, DisasterTiming.hordePerBatch(5L, 0, base, 4), "没人在线就不刷");
        assertEquals(0, DisasterTiming.hordePerBatch(5L, 3, base, 0), "批次数为 0 就不刷");
    }
}
