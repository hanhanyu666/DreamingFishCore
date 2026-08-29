package com.hhy.dreamingfishcore.gameplay.zombie_system;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZombieBreachCommitmentMemoryTest {
    @Test
    void newSoundsDoNotMoveAnActiveBreachAnchor() {
        UUID targetId = UUID.randomUUID();
        BlockPos westWallAnchor = new BlockPos(10, 64, 10);
        BlockPos latestSound = new BlockPos(30, 64, 10);
        ZombieBreachCommitmentMemory.Snapshot commitment =
                ZombieBreachCommitmentMemory.beginOrExtend(
                        null,
                        targetId,
                        westWallAnchor,
                        100L,
                        160);

        assertEquals(westWallAnchor, ZombieBreachCommitmentMemory.effectiveAnchor(
                latestSound,
                commitment,
                targetId,
                200L));
    }

    @Test
    void extendingPreservesTheWallAndExtendsTheDeadline() {
        UUID targetId = UUID.randomUUID();
        BlockPos originalAnchor = new BlockPos(10, 64, 10);
        ZombieBreachCommitmentMemory.Snapshot first =
                ZombieBreachCommitmentMemory.beginOrExtend(
                        null,
                        targetId,
                        originalAnchor,
                        100L,
                        80);
        ZombieBreachCommitmentMemory.Snapshot extended =
                ZombieBreachCommitmentMemory.beginOrExtend(
                        first,
                        targetId,
                        new BlockPos(40, 64, 10),
                        150L,
                        200);

        assertEquals(originalAnchor, extended.anchor());
        assertEquals(350L, extended.expiresAt());
    }

    @Test
    void expiredCommitmentHandsControlToTheLatestSound() {
        UUID targetId = UUID.randomUUID();
        BlockPos latestSound = new BlockPos(30, 64, 10);
        ZombieBreachCommitmentMemory.Snapshot commitment =
                ZombieBreachCommitmentMemory.beginOrExtend(
                        null,
                        targetId,
                        new BlockPos(10, 64, 10),
                        100L,
                        80);

        assertEquals(latestSound, ZombieBreachCommitmentMemory.effectiveAnchor(
                latestSound,
                commitment,
                targetId,
                181L));
    }
}
