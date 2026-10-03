package com.hhy.dreamingfishcore.gameplay.zombie_system.charred;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 焦尸伤害闸门的纯逻辑：判定表与倍率。
 *
 * <p>这些用例覆盖的是「哪一类伤害进不来、进来之后打多少」，不依赖服务端与任何注册表，
 * 所以能把整张表枚举干净——包括容易被忽略的两条：兜底通道必须放行，以及
 * {@code amplifier = 0} 要按「I 级 = +10%」而不是「+0%」算。</p>
 */
class CharredZombieRulesTest {
    private static final double FIRE_MULTIPLIER = 2.0D;
    private static final double BONUS_PER_LEVEL = 0.10D;

    // ------------------------------------------------------------------
    // 判定表
    // ------------------------------------------------------------------

    @Test
    void nonFireDamageIsImmuneOnlyWhenNoVulnerableAndNoFallback() {
        assertTrue(CharredZombieRules.isImmune(false, false, false),
                "非火 + 无易损 + 非兜底：应当归零（这就是「只受火焰伤害」）");
    }

    @Test
    void fireDamageAlwaysGoesThrough() {
        assertFalse(CharredZombieRules.isImmune(true, false, false));
        assertFalse(CharredZombieRules.isImmune(true, true, false));
    }

    @Test
    void vulnerableOpensTheGateForNonFireDamage() {
        assertFalse(CharredZombieRules.isImmune(false, true, false),
                "身上有「易损」时非火伤害必须放行，否则「被火打裂才吃得进别的伤害」这条机制不成立");
    }

    @Test
    void fallbackDamageTypesIgnoreTheImmuneGate() {
        assertFalse(CharredZombieRules.isImmune(false, false, true),
                "兜底通道（默认虚空/溺水/摔落）必须始终放行，否则会出现清不掉的怪");
    }

    // ------------------------------------------------------------------
    // 倍率
    // ------------------------------------------------------------------

    @Test
    void fireDamageIsDoubledByFlammableBody() {
        assertEquals(2.0F, CharredZombieRules.damageMultiplier(
                true, false, -1, FIRE_MULTIPLIER, BONUS_PER_LEVEL), 1.0E-6F);
    }

    @Test
    void plainDamageKeepsMultiplierOne() {
        assertEquals(1.0F, CharredZombieRules.damageMultiplier(
                false, false, -1, FIRE_MULTIPLIER, BONUS_PER_LEVEL), 1.0E-6F,
                "兜底伤害不吃任何倍率");
    }

    @Test
    void levelOneVulnerableAddsTenPercentNotZero() {
        // 原版 amplifier 0 就是 I 级，所以默认配置必须给出 1.10 而不是 1.00。
        assertEquals(1.10F, CharredZombieRules.damageMultiplier(
                false, true, 0, FIRE_MULTIPLIER, BONUS_PER_LEVEL), 1.0E-6F);
        assertEquals(1.20F, CharredZombieRules.damageMultiplier(
                false, true, 1, FIRE_MULTIPLIER, BONUS_PER_LEVEL), 1.0E-6F);
        assertEquals(1.30F, CharredZombieRules.damageMultiplier(
                false, true, 2, FIRE_MULTIPLIER, BONUS_PER_LEVEL), 1.0E-6F);
    }

    @Test
    void fireAndVulnerableStackMultiplicatively() {
        // 4 点火焰伤害打在挂着「易损 I」的焦尸上：4 × 2.0 × 1.10 = 8.8
        assertEquals(2.2F, CharredZombieRules.damageMultiplier(
                true, true, 0, FIRE_MULTIPLIER, BONUS_PER_LEVEL), 1.0E-6F);
        assertEquals(4.0F * 2.2F, 4.0F * CharredZombieRules.damageMultiplier(
                true, true, 0, FIRE_MULTIPLIER, BONUS_PER_LEVEL), 1.0E-5F);
    }

    @Test
    void multipliersAreMonotonicInVulnerableLevel() {
        float previous = CharredZombieRules.damageMultiplier(
                false, true, 0, FIRE_MULTIPLIER, BONUS_PER_LEVEL);
        for (int amplifier = 1; amplifier <= 9; amplifier++) {
            float current = CharredZombieRules.damageMultiplier(
                    false, true, amplifier, FIRE_MULTIPLIER, BONUS_PER_LEVEL);
            assertTrue(current > previous,
                    "「易损」等级越高受伤倍率必须越大，等级 " + amplifier
                            + " 得到 " + current + "，上一级是 " + previous);
            previous = current;
        }
    }

    @Test
    void multipliersNeverGoNegativeForOutOfRangeInputs() {
        // 负的等级与负的加成都不该把伤害缩放成负数。
        assertEquals(1.0F, CharredZombieRules.damageMultiplier(
                false, false, -5, -1.0D, -1.0D), 1.0E-6F);
        // 易燃体质倍率配成 0 是合法但极端的配置：火伤被完全免掉（倍率 0），而不是变成负数。
        assertEquals(0.0F, CharredZombieRules.damageMultiplier(
                true, false, -1, 0.0D, 0.0D), 1.0E-6F);
    }

    // ------------------------------------------------------------------
    // 辅助规则
    // ------------------------------------------------------------------

    @Test
    void burningBoostFollowsBothTheToggleAndTheFireState() {
        assertTrue(CharredZombieRules.shouldBoostWhileBurning(true, true));
        assertFalse(CharredZombieRules.shouldBoostWhileBurning(true, false));
        assertFalse(CharredZombieRules.shouldBoostWhileBurning(false, true));
    }

    @Test
    void vulnerableRefreshNeverDowngradesTheLevel() {
        assertEquals(0, CharredZombieRules.refreshedAmplifier(-1, 0), "没有效果时按配置等级挂上");
        assertEquals(3, CharredZombieRules.refreshedAmplifier(3, 0), "已有更高等级时保持不降");
        assertEquals(2, CharredZombieRules.refreshedAmplifier(1, 2));
        assertEquals(0, CharredZombieRules.refreshedAmplifier(-1, -4), "配置成负数时按 0 兜底");
    }
}
