package com.hhy.dreamingfishcore.gameplay.zombie_system.adamant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 金刚僵尸「生锈」的判定表与倍率。
 *
 * <p>这里给出的都是精确值，实机侧的 GameTest 只验证接线（未生锈=0 伤害、锈后掉血、
 * 受伤倍率随锈级递增）——把精确数学与集成验证分开，避免断言依赖护甲公式的浮点尾数。</p>
 */
class AdamantZombieRulesTest {
    private static final double BONUS_PER_STAGE = 0.20D;

    @Test
    void unrustedIsImmuneUnlessTheDamageTypeIsAFallback() {
        assertTrue(AdamantZombieRules.isImmune(0, false), "未生锈：换不了血");
        assertFalse(AdamantZombieRules.isImmune(0, true), "兜底通道（默认只有虚空）必须始终有效");
    }

    @Test
    void anyRustStageBreaksTheImmunity() {
        assertFalse(AdamantZombieRules.isImmune(1, false));
        assertFalse(AdamantZombieRules.isImmune(2, false));
        assertFalse(AdamantZombieRules.isImmune(3, false));
    }

    @Test
    void rustAdvancesOneStageAtATimeAndStopsAtTheCeiling() {
        assertEquals(1, AdamantZombieRules.advancedStage(0, 3));
        assertEquals(2, AdamantZombieRules.advancedStage(1, 3));
        assertEquals(3, AdamantZombieRules.advancedStage(2, 3));
        assertEquals(3, AdamantZombieRules.advancedStage(3, 3), "锈透之后不再涨");
        assertEquals(3, AdamantZombieRules.advancedStage(2, 5, 3), "一次涨多层也要封顶");
    }

    @Test
    void rustCanAdvanceMultipleStagesWhenConfiguredSo() {
        assertEquals(3, AdamantZombieRules.advancedStage(0, 3, 9));
        assertEquals(0, AdamantZombieRules.advancedStage(0, 0, 9), "涨 0 层等于没浇，锈级不该动");
        assertEquals(2, AdamantZombieRules.advancedStage(2, 0, 9), "已经在锈的也不该被「涨 0 层」改回去");
    }

    @Test
    void damageMultiplierMakesStageOneExactlyNeutral() {
        assertEquals(0.0F, AdamantZombieRules.damageMultiplier(0, BONUS_PER_STAGE), 1.0E-6F,
                "未生锈：伤害倍率 0（免疫）");
        assertEquals(1.0F, AdamantZombieRules.damageMultiplier(1, BONUS_PER_STAGE), 1.0E-6F,
                "第 1 层就是正常伤害——刚生锈就该打得动，不该再打折");
        assertEquals(1.20F, AdamantZombieRules.damageMultiplier(2, BONUS_PER_STAGE), 1.0E-6F);
        assertEquals(1.40F, AdamantZombieRules.damageMultiplier(3, BONUS_PER_STAGE), 1.0E-6F);
    }

    @Test
    void damageMultiplierIsMonotonicInRustStage() {
        float previous = AdamantZombieRules.damageMultiplier(1, BONUS_PER_STAGE);
        for (int stage = 2; stage <= 9; stage++) {
            float current = AdamantZombieRules.damageMultiplier(stage, BONUS_PER_STAGE);
            assertTrue(current > previous,
                    "锈级越高受伤倍率必须越大，锈级 " + stage + " 得到 " + current + "，上一层是 " + previous);
            previous = current;
        }
    }

    @Test
    void negativeBonusNeverLowersDamageBelowNormal() {
        assertEquals(1.0F, AdamantZombieRules.damageMultiplier(3, -1.0D), 1.0E-6F);
    }

    @Test
    void stageAndAmplifierRoundTrip() {
        // 锈级 1 对应效果等级 0（也就是 HUD 上显示的「生锈 I」）。
        assertEquals(0, AdamantZombieRules.amplifierForStage(1));
        assertEquals(2, AdamantZombieRules.amplifierForStage(3));
        assertEquals(0, AdamantZombieRules.amplifierForStage(0), "没锈时按 0 级兜底");
        assertEquals(1, AdamantZombieRules.stageForAmplifier(0));
        assertEquals(3, AdamantZombieRules.stageForAmplifier(2));
        assertEquals(0, AdamantZombieRules.stageForAmplifier(-1), "身上没这个效果 = 未生锈");
    }

    @Test
    void wetnessOnlyRustsWhenTheFeatureIsOn() {
        assertTrue(AdamantZombieRules.accumulatesRustFromWetness(true, true));
        assertFalse(AdamantZombieRules.accumulatesRustFromWetness(true, false));
        assertFalse(AdamantZombieRules.accumulatesRustFromWetness(false, true));
    }
}
