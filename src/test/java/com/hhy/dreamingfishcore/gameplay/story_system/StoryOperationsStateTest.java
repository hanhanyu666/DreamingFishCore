package com.hhy.dreamingfishcore.gameplay.story_system;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StoryOperationsStateTest {
    private static final UUID PLAYER = UUID.fromString("2f680db9-4fb7-4f71-9c04-466737b75ad1");
    private static final String RECAP = "dreamingfishcore:recap/test";
    private static final StoryCheckpoint TARGET = StoryCheckpoint.require(StoryCheckpoint.AFTERDREAM_RECEPTION);

    @Test
    void publicationIsManualImmutableAndDoesNotDeliverByItself() {
        StoryOperationsState state = new StoryOperationsState();
        assertTrue(state.publish(RECAP, "总结", "已发布的正文", TARGET.id(), 1));
        assertFalse(state.hasReceived(PLAYER, RECAP));
        assertFalse(state.publish(RECAP, "改写", "新的正文", TARGET.id(), 2));
        assertEquals("已发布的正文", state.recaps().get(RECAP).content());
    }

    @Test
    void onlyPlayersBeforeTheCheckpointReceiveItAndEachReceivesOnce() {
        StoryOperationsState state = published();
        assertFalse(state.deliver(RECAP, PLAYER, 2020, TARGET, Set.of()));
        assertFalse(state.deliver(RECAP, PLAYER, 2030, TARGET, Set.of()));
        assertTrue(state.deliver(RECAP, PLAYER, 2010, TARGET, Set.of()));
        assertFalse(state.deliver(RECAP, PLAYER, 0, TARGET, Set.of()));
        assertEquals(2020, state.reachedOrder(PLAYER));
        assertTrue(state.isWaived(PLAYER, TARGET.skippedTasks().getFirst()));
        assertTrue(state.markRead(PLAYER, RECAP));
        assertFalse(state.markRead(PLAYER, RECAP));
        assertFalse(state.markRead(UUID.randomUUID(), RECAP));
    }

    @Test
    void restartPreservesUnreadDeliveryWaiversAndCanDeliverToLaterJoiners() {
        StoryOperationsState state = published();
        String completedTask = TARGET.skippedTasks().getFirst();
        state.deliver(RECAP, PLAYER, 2010, TARGET, Set.of(completedTask));
        Gson gson = new Gson();
        StoryOperationsState restored = gson.fromJson(gson.toJson(state), StoryOperationsState.class);
        restored.validate();
        assertTrue(restored.hasReceived(PLAYER, RECAP));
        assertFalse(restored.hasRead(PLAYER, RECAP));
        assertFalse(restored.isWaived(PLAYER, completedTask));
        assertFalse(restored.deliver(RECAP, PLAYER, 0, TARGET, Set.of()));
        assertTrue(restored.deliver(RECAP, UUID.randomUUID(), 0, TARGET, Set.of()));
        assertTrue(restored.markRead(PLAYER, RECAP));
        StoryOperationsState readAgain = gson.fromJson(gson.toJson(restored), StoryOperationsState.class);
        assertTrue(readAgain.hasRead(PLAYER, RECAP));
    }

    @Test
    void versionThreeUpgradePreservesExistingFactsAndAddsNoFakeCompletions() {
        StoryWorldState original = new StoryWorldState();
        original.recordPersonalCompletion("test:done", new StoryWorldState.TaskParticipant(PLAYER, "Tester"), Set.of());
        original.activateTask("test:failed");
        original.resolveTask("test:failed", StoryTaskOutcome.FAILED, java.util.List.of());
        Gson gson = new Gson();
        var json = gson.toJsonTree(original).getAsJsonObject();
        json.addProperty("schemaVersion", 3);
        json.remove("operations");
        StoryWorldState restored = gson.fromJson(json, StoryWorldState.class);
        restored.validateLoadedState();
        assertEquals(StoryWorldState.CURRENT_SCHEMA_VERSION, restored.getSchemaVersion());
        assertTrue(restored.hasPersonalTaskCompletion("test:done", PLAYER));
        assertFalse(restored.hasPersonalTaskCompletion("test:done", UUID.randomUUID()));
        assertEquals(StoryTaskOutcome.FAILED, restored.getTaskProgress("test:failed").getOutcome());
        assertTrue(restored.getOperations().recaps().isEmpty());
    }

    @Test
    void archiveDoesNotCompleteWorldOrPersonalFacts() {
        StoryWorldState state = new StoryWorldState();
        state.activateTask("test:active");
        state.getOperations().archive("test:active");
        assertEquals(StoryTaskOutcome.ACTIVE, state.getTaskProgress("test:active").getOutcome());
        assertEquals(0, state.getPersonalTaskCompletionCount("test:active"));
        StoryTaskData task = new StoryTaskData("test:active", 1, "任务", "说明", 0, 0);
        task.applyRuntimeView(state.getTaskProgress("test:active"), false);
        task.setArchived(true);
        assertFalse(task.isActionRequired());
        assertFalse(task.isCompleted());
        assertFalse(task.isClientPlayerFinished());
        task.setArchived(false);
        assertTrue(task.isActionRequired());
    }

    private static StoryOperationsState published() {
        StoryOperationsState state = new StoryOperationsState();
        state.publish(RECAP, "总结", "正文", TARGET.id(), 1);
        return state;
    }
}
