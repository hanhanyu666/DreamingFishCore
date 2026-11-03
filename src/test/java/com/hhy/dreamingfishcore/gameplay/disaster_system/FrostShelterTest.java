package com.hhy.dreamingfishcore.gameplay.disaster_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 寒霜庇护判定的阈值。
 *
 * <p>这条规则被服主调过两次，而且出过 bug，所以单独钉住：</p>
 * <ul>
 *   <li>最早是"周围 16 格内有发光等级 &gt; 14 的光源"——因为按 2 格步长采样，
 *       玩家旁边第 1 格的海晶灯永远采样不到，站在灯边照样冻伤；</li>
 *   <li>之后改成直接读**玩家所在位置的方块光照**，先定 &gt; 10，最终定为 **≥ 7**。</li>
 * </ul>
 */
class FrostShelterTest {

    @Test
    void thresholdIsSevenOrAbove() {
        assertEquals(7, DisasterTiming.SHELTER_BLOCK_LIGHT,
                "阈值是 7 以上：光照 6 不算安全，7 就算");
    }

    @Test
    void darkerThanTheThresholdIsNotSheltered() {
        for (int light = 0; light < DisasterTiming.SHELTER_BLOCK_LIGHT; light++) {
            assertFalse(DisasterTiming.isSheltered(light), "光照 " + light + " 不该算庇护");
        }
    }

    @Test
    void thresholdAndAboveAreSheltered() {
        for (int light = DisasterTiming.SHELTER_BLOCK_LIGHT; light <= 15; light++) {
            assertTrue(DisasterTiming.isSheltered(light), "光照 " + light + " 应该算庇护");
        }
    }

    @Test
    void boundaryIsExactlySeven() {
        assertFalse(DisasterTiming.isSheltered(6), "6 差一格，不安全");
        assertTrue(DisasterTiming.isSheltered(7), "正好 7 就安全（\"7 以上\"）");
    }

    @Test
    void torchRangeCountsAsShelter() {
        // 火把照到的地方方块光是 14 —— 远高于 7
        assertTrue(DisasterTiming.isSheltered(14), "火把照到的地方当然算庇护");
        assertTrue(DisasterTiming.isSheltered(15));
    }

    @Test
    void moonlitShadowIsNotShelter() {
        // 夜里露天、没有方块光源时方块光是 0（月光不算方块光），应该被冻
        assertFalse(DisasterTiming.isSheltered(0));
    }
}