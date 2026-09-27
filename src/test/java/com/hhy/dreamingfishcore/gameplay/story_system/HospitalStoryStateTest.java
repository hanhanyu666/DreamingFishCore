package com.hhy.dreamingfishcore.gameplay.story_system;

import com.google.gson.Gson;
import com.hhy.dreamingfishcore.gameplay.hospital_system.*;
import com.hhy.dreamingfishcore.server.notice_system.NoticeCategory;
import com.hhy.dreamingfishcore.server.notice_system.NoticeData;
import com.hhy.dreamingfishcore.server.notice_system.NoticeManager;
import net.neoforged.fml.loading.FMLPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HospitalStoryStateTest {
    @TempDir Path directory;

    @Test
    void existingAfterdreamWorldStartsOnServicesReadyAndKeepsProgressOnRepeat() throws Exception {
        StoryWorldState state = new StoryWorldState();
        state.changeStage(StoryStageCatalog.AFTERDREAM_ID);
        UUID player = UUID.randomUUID();
        state.recordPersonalCompletion(HospitalStory.READ_TASK,
                new StoryWorldState.TaskParticipant(player, "Tester"), Set.of());
        state.getHospital().setLocationId("test:existing_hospital");
        // Use an already published notice to avoid writing the real configuration in this runtime test.
        var previousPaths = new EnumMap<FMLPaths, Path>(FMLPaths.class);
        for (FMLPaths path : FMLPaths.values()) previousPaths.put(path, path.get());
        List<NoticeData> notices = null;
        List<NoticeData> previousNotices = List.of();
        NoticeData published = new NoticeData(901, "已有模板说明", "保留正文", 123L,
                NoticeCategory.GAME, StoryStageCatalog.AFTERDREAM_ID, "余梦期", HospitalStory.INFO_NOTICE);
        try {
            FMLPaths.loadAbsolutePaths(directory);
            notices = notices();
            previousNotices = List.copyOf(notices);
            StoryManager.clearWorldCache();
            notices.clear();
            notices.add(published);
            setRuntime("state", state);
            setRuntime("loaded", true);
            setRuntime("writesEnabled", true);
            StoryManager.onServicesReady(null);
            assertTrue(state.getHospital().isStarted());
            assertNull(state.getTaskProgress(HospitalStory.BUILD_TASK));
            StoryManager.onServicesReady(null);
            assertEquals("test:existing_hospital", state.getHospital().getLocationId());
            assertTrue(state.hasPersonalTaskCompletion(HospitalStory.READ_TASK, player));
            assertEquals(0, state.getPersonalTaskCompletionCount(HospitalStory.REVIEW_TASK));
            assertSame(published, NoticeManager.getNoticeByKey(HospitalStory.INFO_NOTICE));
            assertEquals(1, notices.stream().filter(notice -> HospitalStory.INFO_NOTICE.equals(notice.getNoticeKey())).count());
            assertEquals(StoryStageCatalog.AFTERDREAM_ID, state.getCurrentStageId());
        } finally {
            if (notices != null) {
                notices.clear();
                notices.addAll(previousNotices);
            }
            StoryManager.clearWorldCache();
            Field absolutePath = FMLPaths.class.getDeclaredField("absolutePath");
            absolutePath.setAccessible(true);
            for (var entry : previousPaths.entrySet()) absolutePath.set(entry.getKey(), entry.getValue());
        }
    }

    @Test
    void automaticStartDoesNotPublishInOpeningOrInReadOnlyWorld() throws Exception {
        StoryWorldState state = new StoryWorldState();
        try {
            StoryManager.clearWorldCache();
            setRuntime("state", state);
            setRuntime("loaded", true);
            setRuntime("writesEnabled", true);
            StoryManager.onServicesReady(null);
            HospitalStory.reconcile();
            assertFalse(state.getHospital().isStarted());
            state.changeStage(StoryStageCatalog.AFTERDREAM_ID);
            setRuntime("writesEnabled", false);
            StoryManager.onServicesReady(null);
            assertFalse(state.getHospital().isStarted());
        } finally {
            StoryManager.clearWorldCache();
        }
    }

    private static void setRuntime(String name, Object value) throws Exception {
        Field field = StoryManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    @SuppressWarnings("unchecked")
    private static List<NoticeData> notices() throws Exception {
        Field field = NoticeManager.class.getDeclaredField("NOTICES");
        field.setAccessible(true);
        return (List<NoticeData>) field.get(null);
    }

    @Test
    void publicationAndLocationPersistWithoutCompletingConstructionOrTreatment() {
        StoryWorldState state = new StoryWorldState();
        assertFalse(state.getHospital().isStarted());
        assertTrue(state.getHospital().start());
        assertFalse(state.getHospital().start());
        state.getHospital().setLocationId("test:hospital");
        Gson gson = new Gson();
        StoryWorldState restored = gson.fromJson(gson.toJson(state), StoryWorldState.class);
        restored.validateLoadedState();
        assertTrue(restored.getHospital().isStarted());
        assertEquals("test:hospital", restored.getHospital().getLocationId());
        assertNull(restored.getTaskProgress(HospitalStory.BUILD_TASK));
        assertEquals(0, restored.getPersonalTaskCompletionCount(HospitalStory.REVIEW_TASK));
    }

    @Test
    void versionFourAddsHospitalWithoutChangingExistingProgressOrPublishing() {
        Gson gson = new Gson();
        StoryWorldState state = new StoryWorldState();
        state.changeStage(StoryStageCatalog.AFTERDREAM_ID);
        UUID player = UUID.randomUUID();
        state.recordPersonalCompletion("test:done", new StoryWorldState.TaskParticipant(player, "Tester"), Set.of());
        var json = gson.toJsonTree(state).getAsJsonObject();
        json.addProperty("schemaVersion", 4);
        json.remove("hospital");
        StoryWorldState restored = gson.fromJson(json, StoryWorldState.class);
        restored.validateLoadedState();
        assertEquals(5, restored.getSchemaVersion());
        assertEquals(StoryStageCatalog.AFTERDREAM_ID, restored.getCurrentStageId());
        assertTrue(restored.hasPersonalTaskCompletion("test:done", player));
        assertFalse(restored.getHospital().isStarted());
    }

    @Test
    void manualConstructionAcceptanceDoesNotInventPersonalParticipation() {
        StoryWorldState state = new StoryWorldState();
        state.getHospital().start();
        assertThrows(IllegalStateException.class, () -> state.resolveTask(HospitalStory.BUILD_TASK, StoryTaskOutcome.SUCCEEDED, List.of()));
        state.activateTask(HospitalStory.BUILD_TASK);
        state.resolveTask(HospitalStory.BUILD_TASK, StoryTaskOutcome.SUCCEEDED, List.of());
        assertTrue(state.getTaskProgress(HospitalStory.BUILD_TASK).getOutcome().isResolved());
        assertFalse(state.getTaskProgress(HospitalStory.BUILD_TASK).hasParticipant(UUID.randomUUID()));
        assertEquals(0, state.getPersonalTaskCompletionCount(HospitalStory.REVIEW_TASK));
        assertEquals(StoryStageCatalog.DREAM_BEGINNING_ID, state.getCurrentStageId());
    }
}
