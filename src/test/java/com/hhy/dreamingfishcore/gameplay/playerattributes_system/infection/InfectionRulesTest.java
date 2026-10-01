package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfectionRulesTest {

    @Test
    void exposureAccruesWhileInsideTheRangeAndDecaysOutside() {
        assertEquals(1.0F, InfectionRules.advanceExposure(0.0F, true));
        assertEquals(2.0F, InfectionRules.advanceExposure(1.0F, true));
        assertEquals(1.0F, InfectionRules.advanceExposure(2.0F, false));
        assertEquals(0.0F, InfectionRules.advanceExposure(0.0F, false));
    }

    @Test
    void exposureNeverExceedsTheThresholdAndConvertsThere() {
        float charge = InfectionRules.advanceExposure(InfectionRules.EXPOSURE_THRESHOLD, true);
        assertEquals(InfectionRules.EXPOSURE_THRESHOLD, charge);
        assertTrue(InfectionRules.convertsOnThreshold(charge));
        assertFalse(InfectionRules.convertsOnThreshold(InfectionRules.EXPOSURE_THRESHOLD - 0.5F));
    }

    @Test
    void warningStepsEscalateWithTheChargeAndFallBackWhenItDecays() {
        assertEquals(0, InfectionRules.exposureWarningStep(0.0F));
        assertEquals(1, InfectionRules.exposureWarningStep(1.0F));
        assertEquals(2, InfectionRules.exposureWarningStep(2.0F));
        assertEquals(3, InfectionRules.exposureWarningStep(4.0F));
        assertEquals(3, InfectionRules.exposureWarningStep(InfectionRules.EXPOSURE_THRESHOLD));
        assertEquals(1, InfectionRules.exposureWarningStep(1.0F));
    }

    @Test
    void invalidChargeIsTreatedAsNoExposure() {
        assertEquals(0.0F, InfectionRules.boundExposure(Float.NaN));
        assertEquals(0.0F, InfectionRules.boundExposure(-5.0F));
        assertEquals(0.0F, InfectionRules.boundExposure(Float.POSITIVE_INFINITY));
    }

    @Test
    void relapseRequiresHeavyDamageAndNoActiveWindowOrCooldown() {
        assertTrue(InfectionRules.shouldTriggerRelapse(InfectionRules.RELAPSE_DAMAGE_THRESHOLD, false, false));
        assertTrue(InfectionRules.shouldTriggerRelapse(20.0F, false, false));
        assertFalse(InfectionRules.shouldTriggerRelapse(InfectionRules.RELAPSE_DAMAGE_THRESHOLD - 0.1F, false, false));
        // 已经在复发中，或处于复发冷却期，都不再重复触发。
        assertFalse(InfectionRules.shouldTriggerRelapse(20.0F, true, false));
        assertFalse(InfectionRules.shouldTriggerRelapse(20.0F, false, true));
        assertFalse(InfectionRules.shouldTriggerRelapse(Float.NaN, false, false));
    }

    @Test
    void respawnCostFollowsIdentityTiers() {
        assertEquals(5.0F, InfectionRules.respawnCost(InfectionIdentity.SURVIVOR));
        assertEquals(10.0F, InfectionRules.respawnCost(InfectionIdentity.STABLE));
        assertEquals(20.0F, InfectionRules.respawnCost(InfectionIdentity.UNSTABLE));
        // 传播复发是稳定感染者的临时状态，沿用稳定感染者的基础消耗。
        assertEquals(10.0F, InfectionRules.respawnCost(InfectionIdentity.RELAPSE));
        assertEquals(5.0F, InfectionRules.respawnCost(null));
    }

    @Test
    void keepInventoryCostAddsTheFixedExtra() {
        for (InfectionIdentity identity : InfectionIdentity.values()) {
            assertEquals(InfectionRules.respawnCost(identity) + InfectionRules.KEEP_INVENTORY_COST,
                    InfectionRules.keepInventoryCost(identity));
        }
    }

    @Test
    void onlyUnstableAndRelapseIdentitiesCanSpread() {
        assertFalse(InfectionIdentity.SURVIVOR.canSpread());
        assertTrue(InfectionIdentity.UNSTABLE.canSpread());
        assertFalse(InfectionIdentity.STABLE.canSpread());
        assertTrue(InfectionIdentity.RELAPSE.canSpread());
    }
}
