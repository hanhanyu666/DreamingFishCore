package com.hhy.dreamingfishcore.gameplay.guidance_system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidanceEntryTest {
    @Test
    void aGuideCopiesAuthoredContextAndCannotBeResolvedTwice() {
        GuidanceSeed seed = new GuidanceSeed(
                "dreamingfishcore:guidance/test",
                "前往旧车站",
                "在旧车站寻找记录")
                .withStoryStage("dreamingfishcore:test_stage")
                .withLocation("旧车站", "minecraft:overworld", 10, 64, -20);

        GuidanceEntry entry = GuidanceEntry.fromMessage(
                seed,
                "message-record",
                1,
                "记录员",
                "去旧车站看看。",
                100L);

        assertEquals(GuidanceEntry.Status.ACTIVE, entry.getStatus());
        assertEquals("旧车站", entry.getLocationLabel());
        assertEquals(-20, entry.getZ());
        assertTrue(entry.resolve(200L));
        assertEquals(GuidanceEntry.Status.RESOLVED, entry.getStatus());
        assertFalse(entry.resolve(300L));
        assertEquals(200L, entry.getResolvedAtEpochMillis());
    }
}
