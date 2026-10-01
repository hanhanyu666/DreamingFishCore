package com.hhy.dreamingfishcore.gameplay.playerattributes_system;

import com.google.gson.Gson;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity;
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

    @Test
    void relapseWindowOnlyExistsForStableInfectedAndSurvivesSerialization() {
        PlayerAttributesData original = new PlayerAttributesData();
        original.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);
        assertTrue(original.beginRelapse(4_800L));
        assertTrue(original.hasActiveRelapseWindow());
        assertTrue(original.isRelapsing());
        assertFalse(original.isStableInfected());

        PlayerAttributesData restored = GSON.fromJson(
                GSON.toJson(original), PlayerAttributesData.class);

        assertEquals(4_800L, restored.getRelapseUntilActiveTick());
        assertTrue(restored.hasActiveRelapseWindow());
        assertEquals(InfectionIdentity.RELAPSE, restored.getInfectionIdentity());
    }

    @Test
    void leavingStableIdentityClearsRelapseWindowAndCooldown() {
        PlayerAttributesData data = new PlayerAttributesData();
        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);
        data.beginRelapse(1_000L);
        data.setRelapseCooldownUntilActiveTick(2_000L);

        data.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);

        assertFalse(data.hasActiveRelapseWindow());
        assertFalse(data.isRelapseCoolingDown());
        assertEquals(-1L, data.getRelapseUntilActiveTick());
        assertEquals(-1L, data.getRelapseCooldownUntilActiveTick());
    }

    @Test
    void normalizationDropsStaleRelapseStateFromLegacyOrBrokenSaves() {
        // 非稳定身份却带着复发窗口：读档时必须被清掉，否则幸存者会被判定成传播复发。
        PlayerAttributesData data = GSON.fromJson(
                "{\"infectionLevel\":1,\"relapseUntilActiveTick\":500}",
                PlayerAttributesData.class);

        assertTrue(data.normalizeInfectionState());
        assertEquals(-1L, data.getRelapseUntilActiveTick());
        assertEquals(InfectionIdentity.UNSTABLE, data.getInfectionIdentity());

        // 哨兵值比 -1 更小同样要归一到 -1。
        PlayerAttributesData broken = GSON.fromJson(
                "{\"infectionLevel\":2,\"relapseUntilActiveTick\":-50,\"relapseCooldownUntilActiveTick\":-9}",
                PlayerAttributesData.class);
        assertTrue(broken.normalizeInfectionState());
        assertEquals(-1L, broken.getRelapseUntilActiveTick());
        assertEquals(-1L, broken.getRelapseCooldownUntilActiveTick());
    }
}
