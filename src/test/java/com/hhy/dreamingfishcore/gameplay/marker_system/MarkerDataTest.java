package com.hhy.dreamingfishcore.gameplay.marker_system;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerDataTest {
    @Test
    void lifetimeUsesOnlyTheSuppliedLocalTimeBase() {
        long receivedAt = 1_000L;
        MarkerData marker = new MarkerData(UUID.randomUUID(), "Player", Vec3.ZERO, receivedAt);

        assertFalse(marker.isExpired(receivedAt + MarkerData.DEFAULT_LIFETIME_MS - 1L));
        assertTrue(marker.isExpired(receivedAt + MarkerData.DEFAULT_LIFETIME_MS));
    }

    @Test
    void fadeReachesFullOpacityAfterTheLocalIntro() {
        long receivedAt = 5_000L;
        MarkerData marker = new MarkerData(UUID.randomUUID(), "Player", Vec3.ZERO, receivedAt);

        assertEquals(0.0F, marker.getFade(receivedAt), 0.0001F);
        assertEquals(1.0F, marker.getFade(receivedAt + 180L), 0.0001F);
    }
}
