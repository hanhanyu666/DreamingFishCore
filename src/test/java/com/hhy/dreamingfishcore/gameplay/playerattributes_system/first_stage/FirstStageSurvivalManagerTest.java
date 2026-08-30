package com.hhy.dreamingfishcore.gameplay.playerattributes_system.first_stage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FirstStageSurvivalManagerTest {
    @Test
    void foodNaturalHealingStopsExactlyAtSeventyPercentHealth() {
        assertEquals(1.0F,
                FirstStageSurvivalManager.capFoodNaturalHealingAmount(4.0F, 20.0F, 1.0F));
        assertEquals(0.25F,
                FirstStageSurvivalManager.capFoodNaturalHealingAmount(13.75F, 20.0F, 1.0F));
        assertEquals(0.0F,
                FirstStageSurvivalManager.capFoodNaturalHealingAmount(14.0F, 20.0F, 1.0F));
        assertEquals(0.0F,
                FirstStageSurvivalManager.capFoodNaturalHealingAmount(15.0F, 20.0F, 1.0F));
    }

    @Test
    void invalidFoodHealingAmountsAreRejected() {
        assertEquals(0.0F,
                FirstStageSurvivalManager.capFoodNaturalHealingAmount(5.0F, 20.0F, 0.0F));
        assertEquals(0.0F,
                FirstStageSurvivalManager.capFoodNaturalHealingAmount(5.0F, 20.0F, Float.NaN));
        assertEquals(0.0F,
                FirstStageSurvivalManager.capFoodNaturalHealingAmount(5.0F, 0.0F, 1.0F));
    }
}
