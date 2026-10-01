package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection;

import org.junit.jupiter.api.Test;

import static com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.AggroPreferenceRules.Decision.DROP;
import static com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.AggroPreferenceRules.Decision.KEEP;
import static com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.AggroPreferenceRules.Decision.REDIRECT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 丧尸仇恨软规则的单测。
 *
 * <p>这里只覆盖纯决策函数：玩家筛选、距离计算和事件落地依赖服务端实体，属于集成范围的验证，
 * 不能靠单测假装覆盖。随机判定以 {@code dropRoll} 参数直接注入，测试因此没有随机性。</p>
 */
class AggroPreferenceRulesTest {

    private static final double TARGET_DISTANCE = 30.0;

    @Test
    void survivorAlwaysKeepsTargetEvenWithCloserSurvivor() {
        // 幸存者自己就是被仇恨的一方，不存在"转移给更近的幸存者"这回事。
        assertEquals(KEEP, AggroPreferenceRules.decide(
                InfectionIdentity.SURVIVOR, TARGET_DISTANCE, 5.0, true));
        assertEquals(KEEP, AggroPreferenceRules.decide(
                InfectionIdentity.SURVIVOR, TARGET_DISTANCE, 5.0, false));
    }

    @Test
    void unstableInfectedAlwaysKeepsTarget() {
        assertEquals(KEEP, AggroPreferenceRules.decide(
                InfectionIdentity.UNSTABLE, TARGET_DISTANCE, 5.0, true));
    }

    @Test
    void relapseAlwaysKeepsTarget() {
        // 传播复发是稳定感染者的临时状态，不能顺带享受稳定期的仇恨减免。
        assertEquals(KEEP, AggroPreferenceRules.decide(
                InfectionIdentity.RELAPSE, TARGET_DISTANCE, 5.0, true));
    }

    @Test
    void stableRedirectsToCloserSurvivorInsideRange() {
        assertEquals(REDIRECT, AggroPreferenceRules.decide(
                InfectionIdentity.STABLE, TARGET_DISTANCE, 20.0, false));
        assertEquals(REDIRECT, AggroPreferenceRules.decide(
                InfectionIdentity.STABLE, TARGET_DISTANCE, 20.0, true));
    }

    @Test
    void stableRedirectsAtRangeAndDistanceBoundaries() {
        // 边界取闭区间：幸存者正好 24 格、且正好和目标同距时仍然可以转移。
        assertEquals(REDIRECT, AggroPreferenceRules.decide(
                InfectionIdentity.STABLE, AggroPreferenceRules.REDIRECT_RANGE,
                AggroPreferenceRules.REDIRECT_RANGE, false));
    }

    @Test
    void survivorBeyondRedirectRangeFallsBackToDropRoll() {
        assertEquals(DROP, AggroPreferenceRules.decide(
                InfectionIdentity.STABLE, 40.0, 30.0, true));
        assertEquals(KEEP, AggroPreferenceRules.decide(
                InfectionIdentity.STABLE, 40.0, 30.0, false));
    }

    @Test
    void survivorFartherThanTargetFallsBackToDropRoll() {
        // 20 格仍在转移范围内，但比 10 格的目标远；此时拉走丧尸反而违反"更近"的意图。
        assertEquals(DROP, AggroPreferenceRules.decide(
                InfectionIdentity.STABLE, 10.0, 20.0, true));
        assertEquals(KEEP, AggroPreferenceRules.decide(
                InfectionIdentity.STABLE, 10.0, 20.0, false));
    }

    @Test
    void missingSurvivorUsesDropChanceOnly() {
        assertEquals(DROP, AggroPreferenceRules.decide(
                InfectionIdentity.STABLE, TARGET_DISTANCE, AggroPreferenceRules.NO_SURVIVOR_DISTANCE, true));
        assertEquals(KEEP, AggroPreferenceRules.decide(
                InfectionIdentity.STABLE, TARGET_DISTANCE, AggroPreferenceRules.NO_SURVIVOR_DISTANCE, false));
    }

    @Test
    void nanAndNegativeDistancesNeverRedirect() {
        assertDoesNotThrow(() -> AggroPreferenceRules.decide(
                InfectionIdentity.STABLE, Double.NaN, Double.NaN, false));
        assertEquals(KEEP, AggroPreferenceRules.decide(
                InfectionIdentity.STABLE, Double.NaN, Double.NaN, false));
        assertEquals(DROP, AggroPreferenceRules.decide(
                InfectionIdentity.STABLE, Double.NaN, 3.0, true));
        // 目标距离不可用时同样按"没有幸存者"处理，不能让负数距离满足"更近"的比较。
        assertEquals(DROP, AggroPreferenceRules.decide(
                InfectionIdentity.STABLE, -5.0, 3.0, true));
    }

    @Test
    void onlySurvivorIdentityIsEligibleForRedirect() {
        assertTrue(AggroPreferenceRules.isEligibleSurvivor(InfectionIdentity.SURVIVOR));
        assertFalse(AggroPreferenceRules.isEligibleSurvivor(InfectionIdentity.UNSTABLE));
        assertFalse(AggroPreferenceRules.isEligibleSurvivor(InfectionIdentity.STABLE));
        assertFalse(AggroPreferenceRules.isEligibleSurvivor(InfectionIdentity.RELAPSE));
        assertFalse(AggroPreferenceRules.isEligibleSurvivor(null));
    }
}
