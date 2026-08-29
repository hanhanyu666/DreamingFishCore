package com.hhy.dreamingfishcore.gameplay.story_system;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryWorldStateTest {
    private static final Gson GSON = new Gson();
    private static final UUID PLAYER_ID = UUID.fromString("2f680db9-4fb7-4f71-9c04-466737b75ad1");

    @Test
    void defaultStateIsValid() {
        StoryWorldState state = new StoryWorldState();

        state.validateLoadedState();

        assertEquals("dreamingfishcore:dream_beginning", StoryWorldState.DEFAULT_STAGE_ID);
        assertEquals(StoryWorldState.CURRENT_SCHEMA_VERSION, state.getSchemaVersion());
        assertEquals(StoryWorldState.DEFAULT_STAGE_ID, state.getCurrentStageId());
        assertEquals(0L, state.getActiveTicks());
        assertTrue(state.getWorldFlags().isEmpty());
        assertEquals(StoryWorldState.OperationRoundStatus.IDLE, state.getOperationRound().getStatus());
        assertEquals("", state.getEndingId());
    }

    @Test
    void stageEntryUsesActiveWorldTime() {
        StoryWorldState state = new StoryWorldState();
        for (int index = 0; index < 40; index++) {
            assertTrue(state.incrementActiveTicks());
        }

        assertTrue(state.changeStage("dreamingfishcore:control"));

        assertEquals("dreamingfishcore:control", state.getCurrentStageId());
        assertEquals(40L, state.getStageEnteredAtActiveTick());
        assertFalse(state.changeStage("dreamingfishcore:control"));
    }

    @Test
    void flagsAreValidatedAndIdempotent() {
        StoryWorldState state = new StoryWorldState();

        assertTrue(state.setWorldFlag("dreamingfishcore:first_infected", true));
        assertFalse(state.setWorldFlag("dreamingfishcore:first_infected", true));
        assertTrue(state.hasWorldFlag("dreamingfishcore:first_infected"));
        assertThrows(IllegalArgumentException.class, () -> state.setWorldFlag("Invalid Flag", true));
        assertTrue(state.setWorldFlag("dreamingfishcore:first_infected", false));
    }

    @Test
    void operationRoundTracksSourceAndPublishedContent() {
        StoryWorldState state = new StoryWorldState();
        state.incrementActiveTicks();

        assertTrue(state.beginOperationRound("discussion:first_infected"));
        assertEquals(1L, state.getOperationRound().getNumber());
        assertEquals(StoryWorldState.OperationRoundStatus.AWAITING_RESPONSE,
                state.getOperationRound().getStatus());
        assertFalse(state.beginOperationRound("discussion:first_infected"));
        assertTrue(state.publishOperationRound("dreamingfishcore:control_response_01"));
        assertEquals(StoryWorldState.OperationRoundStatus.PUBLISHED,
                state.getOperationRound().getStatus());
        assertEquals("dreamingfishcore:control_response_01", state.getOperationRound().getContentId());
        assertFalse(state.publishOperationRound("dreamingfishcore:duplicate"));
    }

    @Test
    void taskCanResolveSuccessfullyOnlyOnce() {
        StoryWorldState state = new StoryWorldState();
        String taskKey = "dreamingfishcore:medical_station_sample";
        StoryWorldState.TaskParticipant participant =
                new StoryWorldState.TaskParticipant(PLAYER_ID, "Tester");

        assertTrue(state.activateTask(taskKey));
        assertFalse(state.activateTask(taskKey));
        assertTrue(state.resolveTask(taskKey, StoryTaskOutcome.SUCCEEDED, List.of(participant)));
        assertFalse(state.resolveTask(taskKey, StoryTaskOutcome.FAILED, List.of(participant)));

        StoryWorldState.TaskProgress progress = state.getTaskProgress(taskKey);
        assertEquals(StoryTaskOutcome.SUCCEEDED, progress.getOutcome());
        assertEquals(1, progress.getParticipantCount());
        assertTrue(progress.hasParticipant(PLAYER_ID));
    }

    @Test
    void failedTaskIsResolvedAndCannotReopen() {
        StoryWorldState state = new StoryWorldState();
        String taskKey = "dreamingfishcore:medical_station_defense";

        state.activateTask(taskKey);
        assertTrue(state.resolveTask(taskKey, StoryTaskOutcome.FAILED, List.of()));

        StoryWorldState.TaskProgress progress = state.getTaskProgress(taskKey);
        assertEquals(StoryTaskOutcome.FAILED, progress.getOutcome());
        assertTrue(progress.getOutcome().isResolved());
        assertFalse(state.resolveTask(taskKey, StoryTaskOutcome.SUCCEEDED, List.of()));
    }

    @Test
    void taskResolutionIsAtomicWhenParticipantCollectionFails() {
        StoryWorldState state = new StoryWorldState();
        String taskKey = "dreamingfishcore:unstable_participant_source";
        state.activateTask(taskKey);

        Iterable<StoryWorldState.TaskParticipant> brokenParticipants = () -> List.of(
                new StoryWorldState.TaskParticipant(PLAYER_ID, "Tester"),
                new StoryWorldState.TaskParticipant(UUID.randomUUID(), "Other"))
                .stream()
                .map(participant -> {
                    if ("Other".equals(participant.playerName())) {
                        throw new IllegalStateException("participant source failed");
                    }
                    return participant;
                })
                .iterator();

        assertThrows(IllegalStateException.class,
                () -> state.resolveTask(taskKey, StoryTaskOutcome.FAILED, brokenParticipants));
        StoryWorldState.TaskProgress progress = state.getTaskProgress(taskKey);
        assertEquals(StoryTaskOutcome.ACTIVE, progress.getOutcome());
        assertEquals(0, progress.getParticipantCount());
    }

    @Test
    void legacyClientCompletionDoesNotResolveSharedTask() {
        StoryWorldState state = new StoryWorldState();
        String taskKey = "dreamingfishcore:legacy_test";

        state.activateTask(taskKey);
        assertTrue(state.recordLegacyPlayerCompletion(
                taskKey, new StoryWorldState.TaskParticipant(PLAYER_ID, "Tester")));

        StoryWorldState.TaskProgress progress = state.getTaskProgress(taskKey);
        assertEquals(StoryTaskOutcome.ACTIVE, progress.getOutcome());
        assertTrue(progress.hasParticipant(PLAYER_ID));
        assertFalse(state.recordLegacyPlayerCompletion(
                taskKey, new StoryWorldState.TaskParticipant(PLAYER_ID, "Tester")));
    }

    @Test
    void personalCompletionWaitsForEveryExpectedPlayerWithoutChangingWorldState() {
        StoryWorldState state = new StoryWorldState();
        String taskKey = "dreamingfishcore:personal_story_test";
        UUID secondPlayer = UUID.fromString("4a1cfe0a-c7c1-4b9d-8d43-4f73db5cb6dc");
        Set<UUID> expectedPlayers = Set.of(PLAYER_ID, secondPlayer);

        StoryWorldState.PersonalCompletionResult first = state.recordPersonalCompletion(
                taskKey,
                new StoryWorldState.TaskParticipant(PLAYER_ID, "Tester"),
                expectedPlayers);

        assertTrue(first.changed());
        assertFalse(first.allPlayersCompleted());
        assertFalse(first.resolvedNow());
        assertEquals(1, state.getPersonalTaskCompletionCount(taskKey));
        assertTrue(state.getTaskProgressView().isEmpty());

        StoryWorldState.PersonalCompletionResult second = state.recordPersonalCompletion(
                taskKey,
                new StoryWorldState.TaskParticipant(secondPlayer, "Second"),
                expectedPlayers);
        assertTrue(second.changed());
        assertTrue(second.allPlayersCompleted());
        assertTrue(second.resolvedNow());
        assertEquals(2, state.getPersonalTaskCompletionCount(taskKey));
        // 故事层负责在门槛达到后发布/结算世界任务；数据层不越权写入共享结果。
        assertTrue(state.getTaskProgressView().isEmpty());

        assertFalse(state.recordPersonalCompletion(
                taskKey,
                new StoryWorldState.TaskParticipant(secondPlayer, "Second"),
                expectedPlayers).changed());
    }

    @Test
    void personalCompletionRemainsIndependentWhenWorldTaskIsAlreadyFailed() {
        StoryWorldState state = new StoryWorldState();
        String taskKey = "dreamingfishcore:personal_failed_test";

        state.activateTask(taskKey);
        assertTrue(state.resolveTask(taskKey, StoryTaskOutcome.FAILED, List.of()));
        StoryWorldState.PersonalCompletionResult result = state.recordPersonalCompletion(
                taskKey,
                new StoryWorldState.TaskParticipant(PLAYER_ID, "Tester"),
                Set.of(PLAYER_ID));
        assertTrue(result.changed());
        assertTrue(result.allPlayersCompleted());
        assertEquals(StoryTaskOutcome.FAILED, state.getTaskProgress(taskKey).getOutcome());
        assertEquals(0, state.getTaskProgress(taskKey).getParticipantCount());
        assertEquals(1, state.getPersonalTaskCompletionCount(taskKey));
    }

    @Test
    void personalTaskViewIsVisibleBeforeWorldTaskIsPublished() {
        StoryTaskData task = new StoryTaskData(
                "dreamingfishcore:personal_view_test",
                1201,
                "个人任务",
                "完成你的部分",
                0L,
                0L);

        task.applyRuntimeView(null, false, true, 2, 5);

        assertTrue(task.isPersonalTask());
        assertFalse(task.isTaskState());
        assertFalse(task.isCompleted());
        assertFalse(task.isClientPlayerFinished());
        assertEquals(2, task.getFinishedPlayerCount());
        assertEquals(5, task.getPersonalExpectedPlayerCount());
    }

    @Test
    void jsonRoundTripPreservesSharedWorldState() {
        StoryWorldState original = new StoryWorldState();
        original.incrementActiveTicks();
        original.changeStage("dreamingfishcore:control");
        original.setWorldFlag("dreamingfishcore:first_infected", true);
        original.beginOperationRound("discussion:first_infected");
        original.activateTask("dreamingfishcore:sample");
        original.resolveTask(
                "dreamingfishcore:sample",
                StoryTaskOutcome.FAILED,
                List.of(new StoryWorldState.TaskParticipant(PLAYER_ID, "Tester")));
        original.recordPersonalCompletion(
                "dreamingfishcore:personal_sample",
                new StoryWorldState.TaskParticipant(PLAYER_ID, "Tester"),
                Set.of(PLAYER_ID));

        StoryWorldState restored = GSON.fromJson(GSON.toJson(original), StoryWorldState.class);
        restored.validateLoadedState();

        assertEquals("dreamingfishcore:control", restored.getCurrentStageId());
        assertEquals(1L, restored.getActiveTicks());
        assertTrue(restored.hasWorldFlag("dreamingfishcore:first_infected"));
        assertEquals(StoryWorldState.OperationRoundStatus.AWAITING_RESPONSE,
                restored.getOperationRound().getStatus());
        assertEquals(StoryTaskOutcome.FAILED,
                restored.getTaskProgress("dreamingfishcore:sample").getOutcome());
        assertEquals(1, restored.getPersonalTaskCompletionCount("dreamingfishcore:personal_sample"));
        assertTrue(restored.hasPersonalTaskCompletion("dreamingfishcore:personal_sample", PLAYER_ID));
    }

    @Test
    void legacyAfterdreamKeepsItsStableMeaningWhenANewFirstStageIsInserted() {
        StoryWorldState oldState = GSON.fromJson(
                "{\"schemaVersion\":1,\"currentStageId\":\"afterdream\"}",
                StoryWorldState.class);

        assertTrue(oldState.validateAndMigrateLoadedState());
        assertEquals(StoryWorldState.CURRENT_SCHEMA_VERSION, oldState.getSchemaVersion());
        assertEquals("dreamingfishcore:afterdream", oldState.getCurrentStageId());
    }

    @Test
    void rejectsUnsupportedOrInconsistentSavedState() {
        StoryWorldState futureVersion = GSON.fromJson("{\"schemaVersion\":99}", StoryWorldState.class);
        assertThrows(IllegalStateException.class, futureVersion::validateLoadedState);

        StoryWorldState invalidStageTime = GSON.fromJson(
                "{\"schemaVersion\":2,\"currentStageId\":\"dreamingfishcore:afterdream\","
                        + "\"activeTicks\":10,\"stageEnteredAtActiveTick\":11}",
                StoryWorldState.class);
        assertThrows(IllegalStateException.class, invalidStageTime::validateLoadedState);
    }
}
