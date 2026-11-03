package com.hhy.dreamingfishcore.gameplay.disaster_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 风暴天的风向与风力（纯逻辑）。
 *
 * <p>要钉住的是：风向<b>可复现</b>（同一天重启不变）、推力<b>很小</b>（是偏移不是吹飞）、
 * 阵风<b>有界</b>、以及"大体型免疫"的判定边界。</p>
 */
class StormWindTest {

    @Test
    void windDirectionIsAUnitVector() {
        for (long day = 0L; day < 30L; day++) {
            double[] wind = DisasterTiming.windDirection(12345L, day);
            double length = Math.sqrt(wind[0] * wind[0] + wind[1] * wind[1]);
            assertEquals(1.0D, length, 1e-9, "风向必须是单位向量（day=" + day + "）");
        }
    }

    @Test
    void sameDayAlwaysHasTheSameWindButOtherDaysCanDiffer() {
        double[] first = DisasterTiming.windDirection(777L, 5L);
        double[] second = DisasterTiming.windDirection(777L, 5L);
        assertEquals(first[0], second[0], 1e-12, "同一天必须同风向（重启不变）");
        assertEquals(first[1], second[1], 1e-12);

        boolean anyDifferent = false;
        for (long day = 6L; day < 26L && !anyDifferent; day++) {
            double[] other = DisasterTiming.windDirection(777L, day);
            anyDifferent = Math.abs(other[0] - first[0]) > 1e-9
                    || Math.abs(other[1] - first[1]) > 1e-9;
        }
        assertTrue(anyDifferent, "不同天应该能刮不同方向的风");

        // 不同种子也应该能吹出不同方向
        double[] otherSeed = DisasterTiming.windDirection(778L, 5L);
        assertNotEquals(first[0] + first[1], otherSeed[0] + otherSeed[1],
                "换一颗种子风向应有机会不同");
    }

    @Test
    void windPushStaysSmallEvenAtItsStrongest() {
        for (long tick = 0L; tick < 400L; tick += 7L) {
            double factor = DisasterTiming.windGustFactor(tick);
            assertTrue(factor >= 0.6D - 1e-9, "阵风系数不应低于 0.6");
            assertTrue(factor <= 1.0D + 1e-9, "阵风系数不应高于 1.0");
            double push = DisasterTiming.WIND_PUSH * factor;
            assertTrue(push > 0.0D && push <= DisasterTiming.WIND_PUSH,
                    "推力必须很小且有上限：" + push);
        }
        // 基础推力本身就是个小数（每 tick 几百分之一格，几秒才会明显偏移）
        assertTrue(DisasterTiming.WIND_PUSH <= 0.05D, "基础推力不该大到能把人吹跑");
    }

    @Test
    void gustFactorActuallyOscillates() {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (long tick = 0L; tick < 1_000L; tick++) {
            double factor = DisasterTiming.windGustFactor(tick);
            min = Math.min(min, factor);
            max = Math.max(max, factor);
        }
        assertTrue(max - min > 0.3D, "阵风要有明显起伏（实际范围 " + min + "~" + max + "）");
    }

    @Test
    void playersCountAsSmallButRavagersDonNot() {
        assertFalse(DisasterTiming.isLargeEntity(0.6D, 1.8D), "玩家（0.6×1.8）会受风影响");
        assertFalse(DisasterTiming.isLargeEntity(0.6D, 1.95D), "僵尸/金刚僵尸仍受影响");
        assertFalse(DisasterTiming.isLargeEntity(0.9D, 2.0D), "略高的生物仍受影响");

        assertTrue(DisasterTiming.isLargeEntity(1.5D, 1.5D), "宽度达到 1.5 就免疫");
        assertTrue(DisasterTiming.isLargeEntity(1.4D, 2.2D), "高度达到 2.2 就免疫");
        assertTrue(DisasterTiming.isLargeEntity(1.95D, 2.2D), "劫掠兽这类大块头免疫");
        assertTrue(DisasterTiming.isLargeEntity(4.0D, 4.0D), "巨人免疫");
    }

    @Test
    void boundaryValuesAreInclusive() {
        assertTrue(DisasterTiming.isLargeEntity(1.5D, 0.0D), "宽度正好 1.5 算大");
        assertTrue(DisasterTiming.isLargeEntity(0.0D, 2.2D), "高度正好 2.2 算大");
        assertFalse(DisasterTiming.isLargeEntity(1.4999D, 2.1999D), "差一点就不算大");
    }
}
