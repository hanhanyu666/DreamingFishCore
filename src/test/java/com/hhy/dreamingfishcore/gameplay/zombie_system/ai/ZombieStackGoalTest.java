package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombieStackGoalTest {
    @Test
    void threeAdultZombiesUseVanillaPassengerOverlap() {
        assertEquals(4.575D, ZombieStackGoal.estimatedAdultStackHeight(3), 1.0E-9D);
    }

    @Test
    void defaultRuleRequiresAFullFourBlockVerticalDifference() {
        assertFalse(ZombieStackGoal.isTargetHighEnough(64.0D, 67.99D, 4.0D));
        assertTrue(ZombieStackGoal.isTargetHighEnough(64.0D, 68.0D, 4.0D));
    }
}
