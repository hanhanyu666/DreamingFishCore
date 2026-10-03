package com.hhy.dreamingfishcore.gameplay.water_gun_system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 呲水枪的水量数学与开火判定。
 *
 * <p>纯逻辑测试：水量这种「减成负数」「加到超上限」的小事最容易在实机里变成一把显示 11/10
 * 的水枪，所以边界在这里枚举干净。</p>
 */
class WaterGunRulesTest {

    @Test
    void emptyGunCannotFire() {
        assertFalse(WaterGunRules.canFire(0, true), "没水不能喷");
        assertTrue(WaterGunRules.canFire(1, true), "只剩一发也要能喷");
        assertFalse(WaterGunRules.canFire(5, false), "总开关关掉后即使有水也不能喷");
    }

    @Test
    void firingNeverGoesBelowZero() {
        assertEquals(9, WaterGunRules.afterFire(10));
        assertEquals(0, WaterGunRules.afterFire(1));
        assertEquals(0, WaterGunRules.afterFire(0), "空枪再喷一次不能变成负数");
    }

    @Test
    void refillToFullIgnoresCurrentAmount() {
        assertEquals(10, WaterGunRules.afterRefill(0, 10, 5, true));
        assertEquals(10, WaterGunRules.afterRefill(9, 10, 5, true));
    }

    @Test
    void partialRefillAddsExactlyTheConfiguredAmountAndClamps() {
        assertEquals(5, WaterGunRules.afterRefill(0, 10, 5, false));
        assertEquals(8, WaterGunRules.afterRefill(3, 10, 5, false));
        assertEquals(10, WaterGunRules.afterRefill(8, 10, 5, false), "加到上限就停，不能超");
    }

    @Test
    void refillHandlesDegenerateConfig() {
        // 容量配成 0 时怎么加都只能是 0，而不是负容量或无限水。
        assertEquals(0, WaterGunRules.afterRefill(3, 0, 5, false));
        assertEquals(0, WaterGunRules.afterRefill(3, 0, 5, true));
        assertTrue(WaterGunRules.isFull(0, 0));
    }

    @Test
    void fullCheckDrivesTheWaterSourceInteraction() {
        assertTrue(WaterGunRules.isFull(10, 10));
        assertFalse(WaterGunRules.isFull(9, 10));
    }

    @Test
    void burningFollowsTheVanillaFireTickConvention() {
        assertTrue(WaterGunRules.isBurning(1));
        assertTrue(WaterGunRules.isBurning(200));
        assertFalse(WaterGunRules.isBurning(0));
        assertFalse(WaterGunRules.isBurning(-1), "负数表示「没在烧」，不是没着火");
    }

    @Test
    void waterFractionMatchesTheHotbarBar() {
        assertEquals(1.0F, WaterGunRules.waterFraction(10, 10), 1.0E-6F);
        assertEquals(0.5F, WaterGunRules.waterFraction(5, 10), 1.0E-6F);
        assertEquals(0.0F, WaterGunRules.waterFraction(0, 10), 1.0E-6F);
        assertEquals(0.0F, WaterGunRules.waterFraction(5, 0), 1.0E-6F, "容量为 0 时不能除零");
        assertEquals(1.0F, WaterGunRules.waterFraction(99, 10), 1.0E-6F, "超出容量要夹到 1");
    }
}
