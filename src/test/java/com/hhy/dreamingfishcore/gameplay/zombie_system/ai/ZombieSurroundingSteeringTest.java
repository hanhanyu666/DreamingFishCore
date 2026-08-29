package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombieSurroundingSteeringTest {
    @Test
    void stableSlotsCoverAllFourSidesOfAHiddenTarget() {
        Set<Integer> claimedSlots = new HashSet<>();
        for (int index = 0; index < ZombieSurroundingSteering.SLOT_COUNT; index++) {
            claimedSlots.add(ZombieSurroundingSteering.slotIndex(new UUID(0L, index)));
        }

        assertEquals(ZombieSurroundingSteering.SLOT_COUNT, claimedSlots.size());
        assertEquals(new Vec3(1.0D, 0.0D, 0.0D),
                ZombieSurroundingSteering.slotDirection(new UUID(0L, 0L)));
        assertEquals(1.0D,
                ZombieSurroundingSteering.slotDirection(new UUID(0L, 6L)).z,
                1.0E-9D);
        assertEquals(-1.0D,
                ZombieSurroundingSteering.slotDirection(new UUID(0L, 12L)).x,
                1.0E-9D);
        assertEquals(-1.0D,
                ZombieSurroundingSteering.slotDirection(new UUID(0L, 18L)).z,
                1.0E-9D);
    }

    @Test
    void oppositeSlotsProduceOppositeLateralApproaches() {
        Vec3 zombie = new Vec3(0.0D, 0.0D, -5.0D);
        Vec3 directWaypoint = Vec3.ZERO;
        Vec3 target = Vec3.ZERO;

        Vec3 eastSlotDirection = ZombieSurroundingSteering.calculateDirection(
                new UUID(0L, 0L),
                zombie,
                directWaypoint,
                target,
                Vec3.ZERO,
                2.4D,
                12.0D,
                0.55D);
        Vec3 westSlotDirection = ZombieSurroundingSteering.calculateDirection(
                new UUID(0L, 12L),
                zombie,
                directWaypoint,
                target,
                Vec3.ZERO,
                2.4D,
                12.0D,
                0.55D);

        assertTrue(eastSlotDirection.x > 0.05D);
        assertTrue(westSlotDirection.x < -0.05D);
        assertTrue(eastSlotDirection.z > 0.0D);
        assertTrue(westSlotDirection.z > 0.0D);
        assertEquals(1.0D, eastSlotDirection.length(), 1.0E-9D);
        assertEquals(1.0D, westSlotDirection.length(), 1.0E-9D);
    }

    @Test
    void nearbyCrowdBiasesTheResultAwayWithoutChangingSpeed() {
        Vec3 baseline = ZombieSurroundingSteering.calculateDirection(
                new UUID(0L, 0L),
                new Vec3(0.0D, 0.0D, -4.0D),
                Vec3.ZERO,
                Vec3.ZERO,
                Vec3.ZERO,
                2.4D,
                12.0D,
                0.55D);
        Vec3 avoidingEastCrowd = ZombieSurroundingSteering.calculateDirection(
                new UUID(0L, 0L),
                new Vec3(0.0D, 0.0D, -4.0D),
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(-1.0D, 0.0D, 0.0D),
                2.4D,
                12.0D,
                0.55D);

        assertTrue(avoidingEastCrowd.x < baseline.x);
        assertEquals(1.0D, avoidingEastCrowd.length(), 1.0E-9D);
    }
}
