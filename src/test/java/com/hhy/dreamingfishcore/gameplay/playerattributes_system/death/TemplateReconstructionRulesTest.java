package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplateReconstructionRulesTest {
    @Test
    void lastFractionAllowsOneStandardReconstructionThenStopsAtZero() {
        assertTrue(TemplateReconstructionRules.canReconstruct(0.5f));
        assertEquals(0.5f, TemplateReconstructionRules.standardCharge(0.5f, 20));
        assertEquals(1, TemplateReconstructionRules.remainingReconstructions(0.5f, 20));
        assertFalse(TemplateReconstructionRules.canReconstruct(0));
        assertEquals(0, TemplateReconstructionRules.remainingReconstructions(0, 20));
    }

    @Test
    void survivalAndInfectionKeepTheirBaseCostsWithCeilingCount() {
        assertEquals(5, TemplateReconstructionRules.standardCharge(100, 5));
        assertEquals(20, TemplateReconstructionRules.standardCharge(100, 20));
        assertEquals(20, TemplateReconstructionRules.remainingReconstructions(100, 5));
        assertEquals(5, TemplateReconstructionRules.remainingReconstructions(100, 20));
        assertEquals(2, TemplateReconstructionRules.remainingReconstructions(21, 20));
        assertEquals(1, TemplateReconstructionRules.remainingReconstructions(20, 20));
    }

    @Test
    void invalidBalancesCannotAuthorizeReconstruction() {
        assertFalse(TemplateReconstructionRules.canReconstruct(Float.NaN));
        assertFalse(TemplateReconstructionRules.canReconstruct(Float.POSITIVE_INFINITY));
        assertFalse(TemplateReconstructionRules.canReconstruct(-1));
        assertEquals(0, TemplateReconstructionRules.remainingReconstructions(5, 0));
    }
}
