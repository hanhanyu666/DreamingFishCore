package com.hhy.dreamingfishcore.gameplay.task_system;

import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDataManagerTest {
    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void resetBeforeTest() {
        TaskDataManager.clearWorldCache();
        JsonDataStore.resetSession();
    }

    @AfterEach
    void resetAfterTest() {
        TaskDataManager.clearWorldCache();
        JsonDataStore.resetSession();
    }

    @Test
    void missingFileInitializesWritableStoreAndSavesAtomically() throws Exception {
        Path dataFile = dataFile();

        TaskDataManager.loadWorldData(dataFile);

        assertTrue(TaskDataManager.areWritesEnabled());
        assertTrue(TaskDataManager.TASK_PLAYER_DATA_CACHE.isEmpty());

        TaskDataManager.createPlayerTask("任务", "内容", 100L);
        assertTrue(TaskDataManager.saveIfDirty(dataFile));
        assertTrue(Files.exists(dataFile));

        String persisted = Files.readString(dataFile, StandardCharsets.UTF_8);
        assertTrue(persisted.contains("\"taskId\": 1"));
        assertTrue(persisted.contains("\"taskName\": \"任务\""));
    }

    @Test
    void emptyFileInitializesWritableStore() throws Exception {
        Path dataFile = dataFile();
        Files.createDirectories(dataFile.getParent());
        Files.createFile(dataFile);

        TaskDataManager.loadWorldData(dataFile);

        assertTrue(TaskDataManager.areWritesEnabled());
        TaskDataManager.createPlayerTask("任务", "内容", 100L);
        assertTrue(TaskDataManager.saveIfDirty(dataFile));
        assertTrue(Files.size(dataFile) > 0L);
    }

    @Test
    void malformedJsonStaysUntouchedAndMakesManagerReadOnly() throws Exception {
        Path dataFile = dataFile();
        Files.createDirectories(dataFile.getParent());
        String malformed = "{broken-json";
        Files.writeString(dataFile, malformed, StandardCharsets.UTF_8);

        TaskDataManager.loadWorldData(dataFile);

        assertFalse(TaskDataManager.areWritesEnabled());
        assertTrue(TaskDataManager.TASK_PLAYER_DATA_CACHE.isEmpty());
        assertThrows(IllegalStateException.class,
                () -> TaskDataManager.createPlayerTask("不能写入", "内容", 100L));
        assertTrue(TaskDataManager.saveIfDirty(dataFile));
        assertEquals(malformed, Files.readString(dataFile, StandardCharsets.UTF_8));
    }

    @Test
    void malformedPrimaryStaysReadOnlyEvenWhenAValidBackupExists() throws Exception {
        Path dataFile = dataFile();
        TaskPlayerData first = new TaskPlayerData(1, "备份任务", "内容", 1L, 2L);
        TaskPlayerData second = new TaskPlayerData(2, "主文件任务", "内容", 1L, 2L);
        JsonDataStore.writeAtomic(dataFile, TaskDataManager.GSON, Map.of(1, first));
        JsonDataStore.writeAtomic(dataFile, TaskDataManager.GSON, Map.of(2, second));
        String malformed = "{broken-primary";
        Files.writeString(dataFile, malformed, StandardCharsets.UTF_8);

        TaskDataManager.loadWorldData(dataFile);

        assertFalse(TaskDataManager.areWritesEnabled());
        assertTrue(TaskDataManager.TASK_PLAYER_DATA_CACHE.isEmpty());
        assertThrows(IllegalStateException.class,
                () -> TaskDataManager.createPlayerTask("不能写入", "内容", 100L));
        assertTrue(TaskDataManager.saveIfDirty(dataFile));
        assertEquals(malformed, Files.readString(dataFile, StandardCharsets.UTF_8));
        assertTrue(Files.exists(dataFile.resolveSibling("player_tasks.json.bak")));
    }

    @Test
    void nonEmptyJsonNullFileStaysReadOnlyAndUntouched() throws Exception {
        Path dataFile = dataFile();
        Files.createDirectories(dataFile.getParent());
        String jsonNull = "null";
        Files.writeString(dataFile, jsonNull, StandardCharsets.UTF_8);

        TaskDataManager.loadWorldData(dataFile);

        assertFalse(TaskDataManager.areWritesEnabled());
        assertTrue(TaskDataManager.TASK_PLAYER_DATA_CACHE.isEmpty());
        assertThrows(IllegalStateException.class,
                () -> TaskDataManager.createPlayerTask("不能写入", "内容", 100L));
        assertEquals(jsonNull, Files.readString(dataFile, StandardCharsets.UTF_8));
    }

    @Test
    void invalidFinishedPlayerMakesWholeFileReadOnlyWithoutRepairingIt() throws Exception {
        Path dataFile = dataFile();
        Files.createDirectories(dataFile.getParent());
        String invalidData = """
                {
                  "1": {
                    "taskId": 1,
                    "taskName": "任务",
                    "taskContent": "内容",
                    "taskState": false,
                    "isCompleted": false,
                    "startTime": 1,
                    "endTime": 2,
                    "finishedPlayers": [
                      {
                        "playerName": "",
                        "playerUUID": "00000000-0000-0000-0000-000000000001"
                      }
                    ]
                  }
                }
                """;
        Files.writeString(dataFile, invalidData, StandardCharsets.UTF_8);

        TaskDataManager.loadWorldData(dataFile);

        assertFalse(TaskDataManager.areWritesEnabled());
        assertTrue(TaskDataManager.TASK_PLAYER_DATA_CACHE.isEmpty());
        assertThrows(IllegalStateException.class,
                () -> TaskDataManager.createPlayerTask("不能写入", "内容", 100L));
        assertTrue(TaskDataManager.saveIfDirty(dataFile));
        assertEquals(invalidData, Files.readString(dataFile, StandardCharsets.UTF_8));
    }

    @Test
    void mapKeyAndEmbeddedTaskIdMustMatch() throws Exception {
        Path dataFile = dataFile();
        TaskPlayerData mismatchedTask = new TaskPlayerData(2, "任务", "内容", 1L, 2L);
        Files.createDirectories(dataFile.getParent());
        String invalidData = TaskDataManager.GSON.toJson(Map.of(1, mismatchedTask));
        Files.writeString(dataFile, invalidData, StandardCharsets.UTF_8);

        TaskDataManager.loadWorldData(dataFile);

        assertFalse(TaskDataManager.areWritesEnabled());
        assertTrue(TaskDataManager.TASK_PLAYER_DATA_CACHE.isEmpty());
        assertEquals(invalidData, Files.readString(dataFile, StandardCharsets.UTF_8));
    }

    @Test
    void nullFinishedPlayerUuidMakesWholeFileReadOnly() throws Exception {
        Path dataFile = dataFile();
        Files.createDirectories(dataFile.getParent());
        String invalidData = """
                {
                  "1": {
                    "taskId": 1,
                    "taskName": "任务",
                    "taskContent": "内容",
                    "taskState": false,
                    "isCompleted": false,
                    "startTime": 1,
                    "endTime": 2,
                    "finishedPlayers": [
                      {
                        "playerName": "player",
                        "playerUUID": null
                      }
                    ]
                  }
                }
                """;
        Files.writeString(dataFile, invalidData, StandardCharsets.UTF_8);

        TaskDataManager.loadWorldData(dataFile);

        assertFalse(TaskDataManager.areWritesEnabled());
        assertTrue(TaskDataManager.TASK_PLAYER_DATA_CACHE.isEmpty());
        assertEquals(invalidData, Files.readString(dataFile, StandardCharsets.UTF_8));
    }

    @Test
    void oversizedFinishedPlayerSetMakesWholeFileReadOnly() throws Exception {
        Path dataFile = dataFile();
        TaskPlayerData task = new TaskPlayerData(1, "任务", "内容", 1L, 2L);
        for (int index = 0; index <= TaskDataManager.MAX_FINISHED_PLAYERS_PER_TASK; index++) {
            task.addFinishedPlayer("player-" + index, new UUID(0L, index + 1L));
        }
        Files.createDirectories(dataFile.getParent());
        String oversizedData = TaskDataManager.GSON.toJson(Map.of(1, task));
        Files.writeString(dataFile, oversizedData, StandardCharsets.UTF_8);

        TaskDataManager.loadWorldData(dataFile);

        assertFalse(TaskDataManager.areWritesEnabled());
        assertTrue(TaskDataManager.TASK_PLAYER_DATA_CACHE.isEmpty());
        assertEquals(oversizedData, Files.readString(dataFile, StandardCharsets.UTF_8));
    }

    @Test
    void oversizedTaskMapMakesWholeFileReadOnly() throws Exception {
        Path dataFile = dataFile();
        Map<Integer, TaskPlayerData> tasks = new LinkedHashMap<>();
        for (int taskId = 1; taskId <= TaskDataManager.MAX_TASK_COUNT + 1; taskId++) {
            tasks.put(taskId, new TaskPlayerData(taskId, "任务", "内容", 1L, 2L));
        }
        Files.createDirectories(dataFile.getParent());
        String oversizedData = TaskDataManager.GSON.toJson(tasks);
        Files.writeString(dataFile, oversizedData, StandardCharsets.UTF_8);

        TaskDataManager.loadWorldData(dataFile);

        assertFalse(TaskDataManager.areWritesEnabled());
        assertTrue(TaskDataManager.TASK_PLAYER_DATA_CACHE.isEmpty());
        assertEquals(oversizedData, Files.readString(dataFile, StandardCharsets.UTF_8));
    }

    @Test
    void concurrentTaskCreationAllocatesEveryIdExactlyOnce() throws Exception {
        TaskDataManager.loadWorldData(dataFile());
        int taskCount = 200;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> calls = new ArrayList<>();
            for (int index = 0; index < taskCount; index++) {
                calls.add(() -> {
                    TaskDataManager.createPlayerTask("任务", "内容", 100L);
                    return null;
                });
            }
            List<Future<Void>> results = executor.invokeAll(calls);
            for (Future<Void> result : results) {
                result.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(taskCount, TaskDataManager.TASK_PLAYER_DATA_CACHE.size());
        assertEquals(
                IntStream.rangeClosed(1, taskCount).boxed().toList(),
                TaskDataManager.TASK_PLAYER_DATA_CACHE.keySet().stream().sorted().toList());
    }

    @Test
    void completionRejectsMissingIdentityAndPersistsValidIdentity() throws Exception {
        Path dataFile = dataFile();
        TaskDataManager.loadWorldData(dataFile);
        TaskDataManager.createPlayerTask("任务", "内容", 100L);

        TaskDataManager.playerCompleteOwnTask(1, "", UUID.randomUUID());
        TaskDataManager.playerCompleteOwnTask(1, "player", null);
        assertTrue(TaskDataManager.TASK_PLAYER_DATA_CACHE.get(1).getFinishedPlayers().isEmpty());

        UUID playerId = UUID.randomUUID();
        TaskDataManager.playerCompleteOwnTask(1, "player", playerId);
        assertEquals(1, TaskDataManager.TASK_PLAYER_DATA_CACHE.get(1).getFinishedPlayers().size());
        assertTrue(TaskDataManager.saveIfDirty(dataFile));

        TaskDataManager.clearWorldCache();
        TaskDataManager.loadWorldData(dataFile);
        assertTrue(TaskDataManager.areWritesEnabled());
        assertTrue(TaskDataManager.TASK_PLAYER_DATA_CACHE.get(1).isPlayerFinished(playerId));
    }

    @Test
    void taskCountLimitPreventsAdditionalCreation() {
        TaskDataManager.loadWorldData(dataFile());
        TaskPlayerData task = new TaskPlayerData(1, "任务", "内容", 1L, 2L);
        for (int taskId = 1; taskId <= TaskDataManager.MAX_TASK_COUNT; taskId++) {
            TaskDataManager.TASK_PLAYER_DATA_CACHE.put(taskId, task);
        }

        assertThrows(IllegalStateException.class,
                () -> TaskDataManager.createPlayerTask("超限任务", "内容", 100L));
    }

    @Test
    void failedReloadClearsPreviousCacheAndDisablesWritesFirst() {
        TaskDataManager.loadWorldData(dataFile());
        TaskDataManager.createPlayerTask("旧任务", "内容", 100L);
        assertFalse(TaskDataManager.TASK_PLAYER_DATA_CACHE.isEmpty());

        TaskDataManager.loadWorldData((Path) null);

        assertTrue(TaskDataManager.TASK_PLAYER_DATA_CACHE.isEmpty());
        assertFalse(TaskDataManager.areWritesEnabled());
    }

    private Path dataFile() {
        return temporaryDirectory.resolve("task").resolve("player_tasks.json");
    }
}
