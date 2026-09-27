package com.hhy.dreamingfishcore.gameplay.playerattributes_system;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAttributesDataTest {
    private static final Gson GSON = new Gson();

    @Test
    void oldInfectedRecordMigratesToLevelOne() {
        PlayerAttributesData data = GSON.fromJson(
                "{\"currentInfection\":100,\"isInfected\":true}",
                PlayerAttributesData.class);

        assertTrue(data.normalizeInfectionState());
        assertTrue(data.isInfected());
        assertTrue(data.isLevelOneInfected());
        assertEquals(PlayerAttributesData.INFECTION_LEVEL_ONE, data.getInfectionLevel());
    }

    @Test
    void levelTwoRemainsInfectedAndIsNotDowngradedByLegacyBooleanSetter() {
        PlayerAttributesData data = new PlayerAttributesData();
        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);

        data.setInfected(true);

        assertTrue(data.isInfected());
        assertTrue(data.isLevelTwoInfected());
        assertFalse(data.normalizeInfectionState());
    }

    @Test
    void clearingInfectedStateAlsoClearsTheLevel() {
        PlayerAttributesData data = new PlayerAttributesData();
        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);

        data.setInfected(false);

        assertFalse(data.isInfected());
        assertEquals(PlayerAttributesData.INFECTION_LEVEL_NONE, data.getInfectionLevel());
    }

    @Test
    void rawInfectionValueDoesNotSelectLevelWithoutStageContext() {
        PlayerAttributesData data = GSON.fromJson(
                "{\"currentInfection\":100,\"isInfected\":false}",
                PlayerAttributesData.class);

        assertFalse(data.normalizeInfectionState());
        assertFalse(data.isInfected());
        assertEquals(PlayerAttributesData.INFECTION_LEVEL_NONE, data.getInfectionLevel());
    }

    @Test
    void protectiveMaskReceiptKeepsExistingLevelOneAndRawValue() {
        PlayerAttributesData data = new PlayerAttributesData();
        data.setCurrentInfection(100.0F);
        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);

        assertTrue(data.recordProtectiveMaskReceipt());
        assertTrue(data.hasReceivedProtectiveMask());
        assertTrue(data.isLevelOneInfected());
        assertFalse(data.isLevelTwoInfected());
        assertEquals(100.0F, data.getCurrentInfection());
    }

    @Test
    void protectiveMaskReceiptDoesNotApplyAnInfectionThreshold() {
        PlayerAttributesData data = new PlayerAttributesData();
        data.setCurrentInfection(100.0F);

        assertTrue(data.recordProtectiveMaskReceipt());
        assertFalse(data.isInfected());
        assertEquals(100.0F, data.getCurrentInfection());
    }

    @Test
    void infectionStorageAllowsTwoHundredWithoutInferringAnInfectionLevel() {
        PlayerAttributesData data = new PlayerAttributesData();
        data.setCurrentInfection(250.0F);

        assertEquals(200.0F, data.getCurrentInfection());
        assertFalse(data.normalizeInfectionState());
        assertFalse(data.isInfected());
    }

    @Test
    void treatmentDeadlinePersistsOnlyForLevelOneAndClearsWhenTreated() {
        PlayerAttributesData data = new PlayerAttributesData();
        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);
        data.startInfectionTreatmentWindow(24_000L);

        assertTrue(data.hasPendingInfectionTreatmentWindow());
        assertEquals(24_000L, data.getInfectionTreatmentDeadlineActiveTick());

        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);
        assertFalse(data.hasPendingInfectionTreatmentWindow());
        assertEquals(-1L, data.getInfectionTreatmentDeadlineActiveTick());

        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);
        data.startInfectionTreatmentWindow(48_000L);
        data.clearInfectionTreatmentDeadline();
        assertFalse(data.hasPendingInfectionTreatmentWindow());
    }

    @Test
    void serializedTreatmentDeadlineRoundTrips() {
        PlayerAttributesData original = new PlayerAttributesData();
        original.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);
        original.startInfectionTreatmentWindow(99L);

        PlayerAttributesData restored = GSON.fromJson(
                GSON.toJson(original), PlayerAttributesData.class);

        assertEquals(99L, restored.getInfectionTreatmentDeadlineActiveTick());
        assertTrue(restored.hasPendingInfectionTreatmentWindow());
    }
}
