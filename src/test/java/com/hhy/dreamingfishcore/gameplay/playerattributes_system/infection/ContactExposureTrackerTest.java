package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactExposureTrackerTest {

    private final UUID playerId = UUID.randomUUID();

    @AfterEach
    void clearState() {
        ContactExposureTracker.clearAll();
    }

    @Test
    void exposureBuildsUpOnlyWhileExposed() {
        ContactExposureTracker.advance(playerId, true);
        assertEquals(1.0F, ContactExposureTracker.chargeOf(playerId));

        ContactExposureTracker.Step decayed = ContactExposureTracker.advance(playerId, false);
        assertEquals(0.0F, decayed.charge());
        assertEquals(0.0F, ContactExposureTracker.chargeOf(playerId));
    }

    @Test
    void warningsEscalateOncePerStep() {
        ContactExposureTracker.Step first = ContactExposureTracker.advance(playerId, true);
        assertTrue(first.escalated());
        assertEquals(1, first.warningStep());

        ContactExposureTracker.Step second = ContactExposureTracker.advance(playerId, true);
        assertTrue(second.escalated());
        assertEquals(2, second.warningStep());

        // 档位没有变化时不重复提示，避免每 20 秒刷同一句话。
        ContactExposureTracker.Step third = ContactExposureTracker.advance(playerId, true);
        assertFalse(third.escalated());
        assertEquals(2, third.warningStep());

        ContactExposureTracker.Step fourth = ContactExposureTracker.advance(playerId, true);
        assertTrue(fourth.escalated());
        assertEquals(3, fourth.warningStep());
    }

    @Test
    void reachingTheThresholdConvertsAndResetsTheCharge() {
        ContactExposureTracker.Step step = null;
        for (int i = 0; i < (int) InfectionRules.EXPOSURE_THRESHOLD; i++) {
            step = ContactExposureTracker.advance(playerId, true);
        }

        assertTrue(step != null && step.converts());
        assertEquals(0.0F, step.charge());
        assertEquals(0.0F, ContactExposureTracker.chargeOf(playerId));

        // 清零后重新累积，连续暴露会再次攒满并再次转化。
        ContactExposureTracker.Step after = ContactExposureTracker.advance(playerId, true);
        assertEquals(1.0F, after.charge());
        assertFalse(after.converts());
    }

    @Test
    void decayResetsTheWarningSoTheNextExposureWarnsAgain() {
        for (int i = 0; i < 4; i++) {
            ContactExposureTracker.advance(playerId, true);
        }
        for (int i = 0; i < 4; i++) {
            ContactExposureTracker.advance(playerId, false);
        }
        assertEquals(0.0F, ContactExposureTracker.chargeOf(playerId));

        ContactExposureTracker.Step again = ContactExposureTracker.advance(playerId, true);
        assertTrue(again.escalated());
        assertEquals(1, again.warningStep());
    }

    @Test
    void clearForgetsTheChargeForOnePlayer() {
        ContactExposureTracker.advance(playerId, true);
        ContactExposureTracker.clear(playerId);

        assertEquals(0.0F, ContactExposureTracker.chargeOf(playerId));
        assertEquals(0, ContactExposureTracker.advance(playerId, false).warningStep());
    }

    @Test
    void nullPlayerIsIgnored() {
        ContactExposureTracker.Step step = ContactExposureTracker.advance(null, true);
        assertEquals(0.0F, step.charge());
        assertEquals(0.0F, ContactExposureTracker.chargeOf(null));
    }
}
