package com.hhy.dreamingfishcore.gameplay.zombie_system;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombieAlertPositionMemoryTest {
    @Test
    void movementAcrossAThreeByThreeRoomKeepsOnePursuitAnchor() {
        BlockPos firstSound = new BlockPos(10, 64, 10);

        assertFalse(ZombieAlertPositionMemory.shouldRelocate(
                firstSound,
                new BlockPos(12, 64, 12),
                4.0D));
    }

    @Test
    void meaningfulMovementRelocatesTheHiddenTarget() {
        BlockPos firstSound = new BlockPos(10, 64, 10);

        assertTrue(ZombieAlertPositionMemory.shouldRelocate(
                firstSound,
                new BlockPos(14, 64, 10),
                4.0D));
    }
}
