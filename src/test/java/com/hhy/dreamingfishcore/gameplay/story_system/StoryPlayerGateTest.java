package com.hhy.dreamingfishcore.gameplay.story_system;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StoryPlayerGateTest {
    @Test
    void countsPeopleWhoEachFinishedTheWholeLine() {
        StoryPlayerGate gate = new StoryPlayerGate(List.of("test:first", "test:last"), 2);
        Map<String, Map<String, String>> progress = Map.of(
                "test:first", Map.of("a", "A", "b", "B"),
                "test:last", Map.of("a", "A", "c", "C"));
        assertEquals(1, gate.completedPlayers(progress));
        assertFalse(gate.isSatisfied(progress));
        assertTrue(gate.isSatisfied(Map.of(
                "test:first", Map.of("a", "A", "b", "B"),
                "test:last", Map.of("a", "A", "b", "B"))));
    }

    @Test
    void duplicateCompletionAndRecapNeverIncreaseTheCount() {
        StoryWorldState state = new StoryWorldState();
        UUID player = UUID.randomUUID();
        StoryWorldState.TaskParticipant person = new StoryWorldState.TaskParticipant(player, "Tester");
        StoryPlayerGate gate = new StoryPlayerGate(List.of("test:line"), 2);
        state.recordPersonalCompletion("test:line", person, Set.of());
        state.recordPersonalCompletion("test:line", person, Set.of());
        var checkpoint = StoryCheckpoint.require(StoryCheckpoint.AFTERDREAM_RECEPTION);
        state.getOperations().publish("test:recap", "总结", "正文", checkpoint.id(), 0);
        state.getOperations().deliver("test:recap", UUID.randomUUID(), 0, checkpoint, Set.of());
        assertEquals(1, gate.completedPlayers(state.getPersonalTaskProgressView()));
        assertFalse(gate.isSatisfied(state.getPersonalTaskProgressView()));
    }

    @Test
    void rejectsImpossibleOrEmptyThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new StoryPlayerGate(List.of(), 2));
        assertThrows(IllegalArgumentException.class, () -> new StoryPlayerGate(List.of("test:line"), 0));
        assertThrows(IllegalArgumentException.class, () -> new StoryPlayerGate(List.of("test:line", "test:line"), 1));
    }
}
