package com.hhy.dreamingfishcore.gameplay.afterdream_story_system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证“领取、实际使用、再次领取”三个事实不会混为一个游标。 */
class AfterdreamPlayerProgressTest {
    @Test
    void potionGrantIsNotTreatmentCompletion() {
        AfterdreamPlayerProgress progress = readyForResult();

        progress.markPotionGranted(12L);
        progress.setStep(AfterdreamMedicalStep.RESULT_LEVEL_ONE, 12L);

        assertTrue(progress.isPotionGranted());
        assertTrue(progress.isFirstReceptionCompleted());
        assertFalse(progress.isMedicalTreatmentCompleted());
        assertEquals(AfterdreamMedicalStep.RESULT_LEVEL_ONE, progress.getStep());
    }

    @Test
    void actualTreatmentCompletesTheMedicalFact() {
        AfterdreamPlayerProgress progress = readyForResult();
        progress.markPotionGranted(12L);
        progress.setStep(AfterdreamMedicalStep.RESULT_LEVEL_ONE, 12L);
        progress.setStep(AfterdreamMedicalStep.AWAITING_TREATMENT, 12L);

        progress.markMedicalTreatmentCompleted(20L);

        assertTrue(progress.isMedicalTreatmentCompleted());
        assertEquals(AfterdreamMedicalStep.COMPLETED, progress.getStep());
    }

    @Test
    void maskReceiptIsIndependentFromTreatmentCompletion() {
        AfterdreamPlayerProgress progress = readyForResult();
        progress.markFirstReceptionCompleted(12L);
        progress.setStep(AfterdreamMedicalStep.RESULT_LEVEL_ONE, 12L);
        progress.setStep(AfterdreamMedicalStep.AWAITING_TREATMENT, 12L);

        progress.markMaskReceived(30L);
        progress.setStep(AfterdreamMedicalStep.MASK_RECEIVED, 30L);

        assertTrue(progress.isFirstReceptionCompleted());
        assertTrue(progress.isMaskReceived());
        assertFalse(progress.isMedicalTreatmentCompleted());
        assertEquals(AfterdreamMedicalStep.MASK_RECEIVED, progress.getStep());
    }

    @Test
    void repairKeepsFactsButFixesOnlySafeConsistency() {
        AfterdreamPlayerProgress progress = new AfterdreamPlayerProgress();
        progress.markPotionGranted(1L);

        // A potion grant always implies that a first reception happened, but
        // it must not imply that the potion was actually consumed.
        assertFalse(progress.repair());
        assertTrue(progress.isFirstReceptionCompleted());
        assertFalse(progress.isMedicalTreatmentCompleted());
    }

    @Test
    void readingAgainAfterArrivalKeepsTheCurrentTarget() {
        AfterdreamPlayerProgress progress = new AfterdreamPlayerProgress();
        assertTrue(progress.recordMessageRead(1));
        assertEquals(java.util.Set.of(AfterdreamPlayerProgress.GuidanceTarget.ENTER_RECEPTION), progress.pendingGuidance(false));
        progress.advanceTo(AfterdreamMedicalStep.RECEPTION_READY, 2);
        assertFalse(progress.recordMessageRead(3));
        assertEquals(AfterdreamMedicalStep.RECEPTION_READY, progress.getStep());
        assertEquals(java.util.Set.of(AfterdreamPlayerProgress.GuidanceTarget.MEDICAL_REVIEW), progress.pendingGuidance(false));
    }

    @Test
    void readingAgainAfterTreatmentAndMaskDoesNotRecreateAnyTarget() {
        AfterdreamPlayerProgress progress = readyForResult();
        progress.markPotionGranted(5);
        progress.advanceTo(AfterdreamMedicalStep.RESULT_LEVEL_ONE, 5);
        progress.markMedicalTreatmentCompleted(6);
        progress.markMaskReceived(7);
        assertFalse(progress.recordMessageRead(8));
        assertTrue(progress.pendingGuidance(true).isEmpty());
        assertTrue(progress.isMedicalTreatmentCompleted());
    }

    @Test
    void treatmentAndMaskCanRemainPendingTogether() {
        AfterdreamPlayerProgress progress = readyForResult();
        progress.markPotionGranted(5);
        progress.advanceTo(AfterdreamMedicalStep.RESULT_LEVEL_ONE, 5);
        assertEquals(java.util.Set.of(AfterdreamPlayerProgress.GuidanceTarget.MEDICAL_REVIEW,
                AfterdreamPlayerProgress.GuidanceTarget.MASK), progress.pendingGuidance(true));
        progress.markMaskReceived(6);
        assertEquals(java.util.Set.of(AfterdreamPlayerProgress.GuidanceTarget.MEDICAL_REVIEW), progress.pendingGuidance(true));
    }

    @Test
    void recapEntryDoesNotInventTreatmentOrRewardsAndCannotRewind() {
        AfterdreamPlayerProgress progress = new AfterdreamPlayerProgress();
        assertTrue(progress.enterReceptionFromRecap());
        assertFalse(progress.enterReceptionFromRecap());
        assertFalse(progress.isFirstReceptionCompleted());
        assertFalse(progress.isPotionGranted());
        assertFalse(progress.isMaskReceived());
        assertFalse(progress.isMedicalTreatmentCompleted());
        progress.advanceTo(AfterdreamMedicalStep.INTRODUCTION, 1);
        assertFalse(progress.enterReceptionFromRecap());
        assertEquals(AfterdreamMedicalStep.INTRODUCTION, progress.getStep());
        progress.validateState(1);
    }

    @Test
    void levelTwoReviewStopsMedicationGuidanceWithoutInventingACure() {
        var progress = readyForResult();
        assertTrue(progress.pendingGuidance(false, false).contains(AfterdreamPlayerProgress.GuidanceTarget.MEDICAL_REVIEW));
        progress.markPotionGranted(5);
        progress.advanceTo(AfterdreamMedicalStep.RESULT_LEVEL_TWO, 5);
        assertFalse(progress.pendingGuidance(false, false).contains(AfterdreamPlayerProgress.GuidanceTarget.MEDICAL_REVIEW));
        assertEquals(java.util.Set.of(AfterdreamPlayerProgress.GuidanceTarget.MASK), progress.pendingGuidance(true, false));
        assertFalse(progress.isMedicalTreatmentCompleted());
        progress.markMaskReceived(6);
        assertTrue(progress.pendingGuidance(true, false).isEmpty());
        assertFalse(progress.isMedicalTreatmentCompleted());
    }

    private static AfterdreamPlayerProgress readyForResult() {
        AfterdreamPlayerProgress progress = new AfterdreamPlayerProgress();
        progress.recordMessageRead(1);
        progress.advanceTo(AfterdreamMedicalStep.RECEPTION_READY, 2);
        progress.advanceTo(AfterdreamMedicalStep.INTRODUCTION, 3);
        return progress;
    }
}
