package com.hhy.dreamingfishcore.gameplay.story_system;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningStoryDefinitionCatalogTest {
    @Test
    void catalogContainsFourStableAndPublishedTasks() {
        var tasks = OpeningStoryDefinitionCatalog.createTasks();

        assertEquals(4, tasks.size());
        assertEquals(4, new HashSet<>(tasks.stream()
                .map(StoryTaskData::getTaskKey)
                .toList()).size());
        assertEquals(4, new HashSet<>(tasks.stream()
                .map(StoryTaskData::getTaskId)
                .toList()).size());
        assertTrue(tasks.stream().allMatch(StoryTaskData::isPublishedByDefault));
        assertEquals(OpeningStoryDefinitionCatalog.ABYDOS_LOCATION_ID,
                tasks.get(0).getLocationId());
        assertTrue(tasks.stream().skip(1).allMatch(task -> task.getLocationId().isBlank()));
        assertTrue(OpeningStoryDefinitionCatalog.isMemberOnlyTask(
                OpeningStoryDefinitionCatalog.BUILD_ZHUIGUANG_BASE_TASK_ID));
        assertFalse(OpeningStoryDefinitionCatalog.isMemberOnlyTask(
                OpeningStoryDefinitionCatalog.CHOOSE_ZHUIGUANG_PATH_TASK_ID));
    }

    @Test
    void backfillDoesNotDuplicateExistingTasks() {
        StoryStageData stage = new StoryStageData(
                OpeningStoryDefinitionCatalog.STAGE_ID, 1, "梦的开始", "test");

        assertTrue(OpeningStoryDefinitionCatalog.ensureTasks(stage));
        assertEquals(4, stage.getTasks().size());
        assertFalse(OpeningStoryDefinitionCatalog.ensureTasks(stage));
        assertEquals(4, stage.getTasks().size());
    }

    @Test
    void doesNotRewriteAnExistingTaskLocation() {
        StoryStageData stage = new StoryStageData(
                OpeningStoryDefinitionCatalog.STAGE_ID, 1, "梦的开始", "test");
        StoryTaskData custom = new StoryTaskData(
                OpeningStoryDefinitionCatalog.SETTLE_IN_ABYDOS_TASK_ID,
                OpeningStoryDefinitionCatalog.SETTLE_IN_ABYDOS_TASK_NUMBER,
                "抵达阿拜多斯",
                "test",
                0L,
                0L);
        custom.setLocationId("dreamingfishcore:custom_abydos");
        stage.addTask(custom);

        assertTrue(OpeningStoryDefinitionCatalog.ensureTasks(stage));
        assertEquals("dreamingfishcore:custom_abydos",
                stage.getTasks().get(0).getLocationId());
        assertEquals(4, stage.getTasks().size());
    }
}
