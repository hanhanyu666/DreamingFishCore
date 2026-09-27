package com.hhy.dreamingfishcore.gameplay.story_system;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 验证实际查询门面，避免只测试数据对象而漏掉历史视图再次强制完成。 */
class StoryManagerProgressTest {
    private static final UUID PLAYER = UUID.fromString("2f680db9-4fb7-4f71-9c04-466737b75ad1");
    private StoryWorldState state;
    private StoryTaskData personal;
    private StoryTaskData world;

    @BeforeEach
    void initialize() throws Exception {
        StoryManager.clearWorldCache();
        state = new StoryWorldState();
        state.changeStage(StoryStageCatalog.AFTERDREAM_ID);
        personal = new StoryTaskData("test:personal", 9101, "个人", "说明", 0, 0);
        personal.setScope(StoryTaskData.Scope.PERSONAL);
        world = new StoryTaskData("test:world", 9102, "世界", "说明", 0, 0);
        world.setScope(StoryTaskData.Scope.WORLD);
        StoryStageData oldStage = new StoryStageData(StoryStageCatalog.DREAM_BEGINNING_ID, 1, "旧阶段", "介绍");
        oldStage.addTask(personal);
        oldStage.addTask(world);
        StoryStageData current = new StoryStageData(StoryStageCatalog.AFTERDREAM_ID, 2, "当前阶段", "介绍");
        put("STAGES_BY_ID", Map.of(oldStage.getStageId(), oldStage, current.getStageId(), current));
        put("STAGES_BY_NUMBER", Map.of(1, oldStage, 2, current));
        put("TASKS_BY_KEY", Map.of(personal.getTaskKey(), personal, world.getTaskKey(), world));
        put("TASKS_BY_NUMBER", Map.of(9101, personal, 9102, world));
        put("TASK_STAGE_IDS", Map.of(personal.getTaskKey(), oldStage.getStageId(), world.getTaskKey(), oldStage.getStageId()));
        set("state", state);
        set("loaded", true);
        set("writesEnabled", true);
    }

    @AfterEach
    void clear() { StoryManager.clearWorldCache(); }

    @Test
    void historicalQueriesKeepActualPersonalRecordsForBothIds() {
        state.recordPersonalCompletion(personal.getTaskKey(), new StoryWorldState.TaskParticipant(PLAYER, "Tester"), Set.of());
        UUID newcomer = UUID.randomUUID();
        assertTrue(StoryManager.isPlayerFinishedTask(personal.getTaskKey(), PLAYER));
        assertTrue(StoryManager.isPlayerFinishedTask(9101, PLAYER));
        assertFalse(StoryManager.isPlayerFinishedTask(personal.getTaskKey(), newcomer));
        assertFalse(StoryManager.isPlayerFinishedTask(9101, newcomer));
        assertFalse(StoryManager.isPlayerFinishedStage(1, newcomer));
        assertEquals(0, StoryManager.getPlayerCompletedTaskCount(1, newcomer));
        StoryTaskData view = StoryManager.getStagesForPlayer(newcomer).get(1).getTasks().getFirst();
        assertTrue(view.isArchived());
        assertFalse(view.isClientPlayerFinished());
        assertFalse(view.isActionRequired());
    }

    @Test
    void historicalWorldFailureSurvivesStageAndSingleTaskViews() {
        state.activateTask(world.getTaskKey());
        state.resolveTask(world.getTaskKey(), StoryTaskOutcome.FAILED, List.of());
        StoryTaskData view = StoryManager.getTask(world.getTaskKey());
        assertTrue(view.isArchived());
        assertTrue(view.isFailed());
        assertTrue(view.isCompleted());
        assertFalse(view.isClientPlayerFinished());
        var stageView = StoryManager.getStagesForPlayer(PLAYER).get(1).getTasks().get(1);
        assertTrue(stageView.isFailed());
        assertFalse(stageView.isClientPlayerFinished());
        assertThrows(IllegalArgumentException.class, () -> StoryManager.activateTask(world.getTaskKey()));
    }

    @Test
    void fixedCountUnlocksOnceWithoutChangingStageOrRequiringLateJoiners() throws Exception {
        var definition = new StoryOperationsCatalog.WorldTask("test:gate", 9200,
                StoryStageCatalog.AFTERDREAM_ID, "共同任务", "说明", "", List.of(personal.getTaskKey()), 2, "");
        put("TASKS_BY_KEY", Map.of(definition.id(), definition.definition()));
        put("TASK_STAGE_IDS", Map.of(definition.id(), definition.stageId()));
        set("operationsDocument", new StoryOperationsCatalog.Document(1, List.of(), List.of(definition)));
        var evaluate = StoryManager.class.getDeclaredMethod("evaluateWorldTaskGates");
        evaluate.setAccessible(true);
        state.recordPersonalCompletion(personal.getTaskKey(), new StoryWorldState.TaskParticipant(PLAYER, "First"), Set.of());
        evaluate.invoke(null);
        assertNull(state.getTaskProgress(definition.id()));
        state.recordPersonalCompletion(personal.getTaskKey(), new StoryWorldState.TaskParticipant(UUID.randomUUID(), "Second"), Set.of());
        evaluate.invoke(null);
        assertEquals(StoryTaskOutcome.ACTIVE, state.getTaskProgress(definition.id()).getOutcome());
        state.getOperations().reach(UUID.randomUUID(), 2000);
        evaluate.invoke(null);
        assertEquals(1, state.getTaskProgressView().size());
        assertEquals(StoryStageCatalog.AFTERDREAM_ID, state.getCurrentStageId());
    }

    private static void set(String name, Object value) throws Exception {
        Field field = StoryManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    @SuppressWarnings("unchecked")
    private static void put(String name, Map<?, ?> values) throws Exception {
        Field field = StoryManager.class.getDeclaredField(name);
        field.setAccessible(true);
        ((Map<Object, Object>) field.get(null)).putAll(values);
    }
}
