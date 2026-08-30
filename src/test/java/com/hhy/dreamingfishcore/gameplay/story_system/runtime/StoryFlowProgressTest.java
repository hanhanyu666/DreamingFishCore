package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryFlowProgressTest {
    @Test
    void cursorAndEffectJournalAreIdempotent() {
        StoryFlowProgress progress = new StoryFlowProgress("read_abydos_notice");

        assertEquals("read_abydos_notice", progress.getCursor());
        assertFalse(progress.hasAppliedEffect("read_abydos_notice/create_guidance"));

        progress.markEffectApplied("read_abydos_notice/create_guidance", 1L);
        progress.setCursor("enter_abydos", 2L);
        progress.markNodeCompleted("read_abydos_notice", 2L);

        assertTrue(progress.hasAppliedEffect("read_abydos_notice/create_guidance"));
        assertTrue(progress.hasCompletedNode("read_abydos_notice"));
        assertEquals("enter_abydos", progress.getCursor());
    }

    @Test
    void stateDoesNotExposeMutableCollections() {
        StoryFlowProgress progress = new StoryFlowProgress("start");
        assertTrue(progress.getCompletedNodes().isEmpty());
        assertTrue(progress.getAppliedEffects().isEmpty());
        assertTrue(progress.getFlags().isEmpty());
    }
}
