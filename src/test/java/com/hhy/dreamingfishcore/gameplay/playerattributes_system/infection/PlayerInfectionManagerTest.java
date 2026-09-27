package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerInfectionManagerTest {

    @Test
    void revivalUsesLegacyHundredPointScaleBeforeMaskEra() {
        PlayerAttributesData source = new PlayerAttributesData();
        source.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);
        PlayerAttributesData target = new PlayerAttributesData();

        PlayerInfectionManager.applyRevivalInfectionState(source, target, false, 10L);

        assertTrue(target.isLevelOneInfected());
        assertEquals(100.0F, target.getCurrentInfection());
        assertFalse(target.hasPendingInfectionTreatmentWindow());
    }

    @Test
    void revivalDoesNotCreateTreatmentWindowForAnOldLevelOneAfterMaskEra() {
        PlayerAttributesData source = new PlayerAttributesData();
        source.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);
        PlayerAttributesData target = new PlayerAttributesData();

        PlayerInfectionManager.applyRevivalInfectionState(source, target, true, 123L);

        assertTrue(target.isLevelOneInfected());
        assertEquals(200.0F, target.getCurrentInfection());
        assertFalse(target.hasPendingInfectionTreatmentWindow());
    }

    @Test
    void revivalKeepsLevelTwoAtPostMaskMaximumWithoutTreatmentWindow() {
        PlayerAttributesData source = new PlayerAttributesData();
        source.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);
        source.setCurrentInfection(17.0F);
        PlayerAttributesData target = new PlayerAttributesData();

        PlayerInfectionManager.applyRevivalInfectionState(source, target, false, 0L);

        assertTrue(target.isLevelTwoInfected());
        assertEquals(200.0F, target.getCurrentInfection());
        assertFalse(target.hasPendingInfectionTreatmentWindow());
    }

    @Test
    void revivalClearsTargetWhenSourceIsNotInfected() {
        PlayerAttributesData source = new PlayerAttributesData();
        PlayerAttributesData target = new PlayerAttributesData();
        target.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);
        target.setCurrentInfection(200.0F);

        PlayerInfectionManager.applyRevivalInfectionState(source, target, true, 0L);

        assertFalse(target.isInfected());
        assertEquals(0.0F, target.getCurrentInfection());
        assertFalse(target.hasPendingInfectionTreatmentWindow());
    }
}
