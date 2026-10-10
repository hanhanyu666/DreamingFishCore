package com.hhy.dreamingfishcore.gameplay.zombie_system.boss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 阶段阈值、召唤配额和难度倍率的纯函数测试，不使用游戏注册表或配置目录。 */
class ZombieCommanderRulesTest {
    @Test
    void enrageStartsAtOrBelowTheHealthFractionThreshold() {
        assertFalse(ZombieCommanderRules.shouldEnrage(240.0D, 240.0D, 0.5D));
        assertFalse(ZombieCommanderRules.shouldEnrage(Math.nextUp(120.0D), 240.0D, 0.5D));
        assertTrue(ZombieCommanderRules.shouldEnrage(120.0D, 240.0D, 0.5D));
        assertTrue(ZombieCommanderRules.shouldEnrage(Math.nextDown(120.0D), 240.0D, 0.5D));
        assertTrue(ZombieCommanderRules.shouldEnrage(1.0D, 240.0D, 0.5D));
        assertTrue(ZombieCommanderRules.shouldEnrage(0.0D, 240.0D, 0.5D));
        assertTrue(ZombieCommanderRules.shouldEnrage(60.0D, 240.0D, 0.25D));
        assertFalse(ZombieCommanderRules.shouldEnrage(61.0D, 240.0D, 0.25D));
    }

    @Test
    void zeroAndOneFractionsHaveInclusiveBoundarySemantics() {
        assertTrue(ZombieCommanderRules.shouldEnrage(0.0D, 240.0D, 0.0D));
        assertFalse(ZombieCommanderRules.shouldEnrage(1.0D, 240.0D, 0.0D));
        assertTrue(ZombieCommanderRules.shouldEnrage(240.0D, 240.0D, 1.0D));
        assertTrue(ZombieCommanderRules.shouldEnrage(1.0D, 240.0D, 1.0D));
        assertTrue(ZombieCommanderRules.shouldEnrage(Double.MAX_VALUE / 2.0D, Double.MAX_VALUE, 0.5D));
        assertTrue(ZombieCommanderRules.shouldEnrage(Double.MIN_VALUE, Double.MIN_VALUE, 1.0D));
    }

    @Test
    void invalidHealthMaxHealthAndFractionNeverTriggerEnrage() {
        assertFalse(ZombieCommanderRules.shouldEnrage(-1.0D, 240.0D, 0.5D));
        assertFalse(ZombieCommanderRules.shouldEnrage(241.0D, 240.0D, 1.0D));
        assertFalse(ZombieCommanderRules.shouldEnrage(0.0D, 0.0D, 0.5D));
        assertFalse(ZombieCommanderRules.shouldEnrage(0.0D, -240.0D, 0.5D));
        assertFalse(ZombieCommanderRules.shouldEnrage(120.0D, 240.0D, -0.01D));
        assertFalse(ZombieCommanderRules.shouldEnrage(120.0D, 240.0D, 1.01D));
        for (double nonFinite : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertFalse(ZombieCommanderRules.shouldEnrage(nonFinite, 240.0D, 0.5D));
            assertFalse(ZombieCommanderRules.shouldEnrage(120.0D, nonFinite, 0.5D));
            assertFalse(ZombieCommanderRules.shouldEnrage(120.0D, 240.0D, nonFinite));
        }
    }

    @Test
    void summonsAreLimitedByBothRequestedCountAndAvailableCapacity() {
        assertEquals(2, ZombieCommanderRules.availableSummons(0, 6, 2));
        assertEquals(3, ZombieCommanderRules.availableSummons(0, 6, 3));
        assertEquals(2, ZombieCommanderRules.availableSummons(4, 6, 3));
        assertEquals(1, ZombieCommanderRules.availableSummons(5, 6, 3));
        assertEquals(0, ZombieCommanderRules.availableSummons(6, 6, 3));
        assertEquals(0, ZombieCommanderRules.availableSummons(7, 6, 3));
        assertEquals(6, ZombieCommanderRules.availableSummons(0, 6, 12));
        assertEquals(1, ZombieCommanderRules.availableSummons(0, 6, 1));
    }

    @Test
    void negativeCountsAreClampedAndIntegerExtremesDoNotOverflow() {
        assertEquals(2, ZombieCommanderRules.availableSummons(-1, 6, 2));
        assertEquals(6, ZombieCommanderRules.availableSummons(Integer.MIN_VALUE, 6, 12));
        assertEquals(0, ZombieCommanderRules.availableSummons(0, 0, 2));
        assertEquals(0, ZombieCommanderRules.availableSummons(0, -1, 2));
        assertEquals(0, ZombieCommanderRules.availableSummons(0, Integer.MIN_VALUE, 2));
        assertEquals(0, ZombieCommanderRules.availableSummons(0, 6, 0));
        assertEquals(0, ZombieCommanderRules.availableSummons(0, 6, -1));
        assertEquals(0, ZombieCommanderRules.availableSummons(0, 6, Integer.MIN_VALUE));
        assertEquals(0, ZombieCommanderRules.availableSummons(Integer.MAX_VALUE, 6, 2));
        assertEquals(0, ZombieCommanderRules.availableSummons(Integer.MAX_VALUE, Integer.MAX_VALUE, 2));
        assertEquals(1, ZombieCommanderRules.availableSummons(Integer.MAX_VALUE - 1, Integer.MAX_VALUE, 2));
        assertEquals(Integer.MAX_VALUE,
                ZombieCommanderRules.availableSummons(Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(0, ZombieCommanderRules.availableSummons(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void vanillaDifficultyIdsHaveExactlyTheConfirmedProjectileMultipliers() {
        assertEquals(0.0D, ZombieCommanderRules.projectileDamageMultiplier(0));
        assertEquals(0.75D, ZombieCommanderRules.projectileDamageMultiplier(1));
        assertEquals(1.0D, ZombieCommanderRules.projectileDamageMultiplier(2));
        assertEquals(1.5D, ZombieCommanderRules.projectileDamageMultiplier(3));
    }

    @Test
    void unknownDifficultyIdsUseNormalDamage() {
        for (int difficultyId : new int[] {-1, 4, 99, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertEquals(1.0D, ZombieCommanderRules.projectileDamageMultiplier(difficultyId));
        }
    }

    // ------------------------------------------------------------------
    // 近战三段连招
    // ------------------------------------------------------------------

    @Test
    void comboDamageEscalatesAndTheFinisherHitsHardest() {
        assertEquals(1.0D, ZombieCommanderRules.meleeDamageMultiplier(1));
        assertEquals(1.15D, ZombieCommanderRules.meleeDamageMultiplier(2));
        assertEquals(1.65D, ZombieCommanderRules.meleeDamageMultiplier(3));
        assertTrue(ZombieCommanderRules.meleeDamageMultiplier(2)
                        > ZombieCommanderRules.meleeDamageMultiplier(1));
        assertTrue(ZombieCommanderRules.meleeDamageMultiplier(3)
                        > ZombieCommanderRules.meleeDamageMultiplier(2));
        assertEquals(3.8D, ZombieCommanderRules.meleeComboTotalMultiplier(), 1.0E-9D,
                "三段全中约等于基础伤害的 3.8 倍");
    }

    @Test
    void outOfRangeStepsAreClampedInsteadOfThrowing() {
        for (int step : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertEquals(1, ZombieCommanderRules.clampMeleeStep(step));
            assertEquals(1.0D, ZombieCommanderRules.meleeDamageMultiplier(step));
            assertEquals(ZombieCommanderRules.meleeImpactTick(1),
                    ZombieCommanderRules.meleeImpactTick(step));
        }
        for (int step : new int[] {4, 99, Integer.MAX_VALUE}) {
            assertEquals(3, ZombieCommanderRules.clampMeleeStep(step));
            assertEquals(1.65D, ZombieCommanderRules.meleeDamageMultiplier(step));
        }
    }

    @Test
    void comboChainsThroughAllThreeStepsThenStops() {
        assertEquals(2, ZombieCommanderRules.meleeChainStep(1, 2.0D, 3.6D));
        assertEquals(3, ZombieCommanderRules.meleeChainStep(2, 3.5D, 3.6D));
        assertEquals(0, ZombieCommanderRules.meleeChainStep(3, 1.0D, 3.6D),
                "第三段是收尾，打完必须停下");
    }

    @Test
    void comboStopsWhenTheTargetIsPushedOutOfRangeOrInputIsBroken() {
        assertEquals(0, ZombieCommanderRules.meleeChainStep(1, 3.61D, 3.6D));
        assertEquals(2, ZombieCommanderRules.meleeChainStep(1, 3.6D, 3.6D), "边界值算够得着");
        assertEquals(0, ZombieCommanderRules.meleeChainStep(1, Double.NaN, 3.6D));
        assertEquals(0, ZombieCommanderRules.meleeChainStep(1, 1.0D, Double.NaN));
        assertEquals(0, ZombieCommanderRules.meleeChainStep(1, Double.POSITIVE_INFINITY, 3.6D));
    }

    @Test
    void onlyTheFinisherKnocksTheTargetBack() {
        assertEquals(0.0D, ZombieCommanderRules.meleeKnockback(1));
        assertTrue(ZombieCommanderRules.meleeKnockback(2) > 0.0D);
        assertTrue(ZombieCommanderRules.meleeKnockback(3)
                        > ZombieCommanderRules.meleeKnockback(2));
    }

    @Test
    void impactTicksAreStrictlyInsideTheirOwnStep() {
        int previousLength = 0;
        for (int step = 1; step <= ZombieCommanderRules.MELEE_STEPS; step++) {
            int impact = ZombieCommanderRules.meleeImpactTick(step);
            int length = ZombieCommanderRules.meleeLengthTick(step);
            assertTrue(impact > 0 && impact < length,
                    "第 " + step + " 段的命中 tick 必须在 (0, 本段时长) 之间");
            assertTrue(length >= previousLength, "越靠后的段动作不会更短");
            assertTrue(length >= 10, "每段动作至少要有 0.5 秒，否则来不及看清");
            previousLength = length;
        }
    }
}
