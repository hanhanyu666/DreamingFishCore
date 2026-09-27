package com.hhy.dreamingfishcore.gameplay.task_system;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.common.util.Utf8JsonFileIO;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.gameplay.task_system.network.Packet_SyncFullTaskData;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.hhy.dreamingfishcore.server.server_management_system.GetServerInstance.SERVER_INSTANCE;

public class TaskDataManager {
    public static final Map<Integer, TaskPlayerData> TASK_PLAYER_DATA_CACHE = new ConcurrentHashMap<>();
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    static final int MAX_TASK_COUNT = 10_000;
    static final int MAX_FINISHED_PLAYERS_PER_TASK = 10_000;

    private static final Type PLAYER_MAP_TYPE = new TypeToken<Map<Integer, TaskPlayerData>>() {}.getType();
    private static int maxPlayerTaskID;
    private static boolean dirty;
    private static boolean loaded;
    private static boolean writesEnabled;

    public static synchronized void loadWorldData(MinecraftServer server) {
        loadWorldData(() -> WorldDataPaths.resolve(server, "task", "player_tasks.json"));
    }

    static synchronized void loadWorldData(Path dataPath) {
        loadWorldData(() -> dataPath);
    }

    private static void loadWorldData(Supplier<Path> dataPathSupplier) {
        TASK_PLAYER_DATA_CACHE.clear();
        maxPlayerTaskID = 0;
        dirty = false;
        loaded = false;
        writesEnabled = false;
        try {
            Path dataPath = dataPathSupplier.get();
            validatePrimaryJson(dataPath);
            Map<Integer, TaskPlayerData> loadedData = JsonDataStore.read(
                    dataPath,
                    GSON,
                    PLAYER_MAP_TYPE,
                    ConcurrentHashMap::new);
            validateLoadedData(loadedData);
            TASK_PLAYER_DATA_CACHE.putAll(loadedData);
            calculateMaxTaskIDs();
            writesEnabled = true;
            DreamingFishCore.LOGGER.info("玩家任务数据加载完成，共 {} 条", TASK_PLAYER_DATA_CACHE.size());
        } catch (Exception exception) {
            TASK_PLAYER_DATA_CACHE.clear();
            maxPlayerTaskID = 0;
            dirty = false;
            writesEnabled = false;
            DreamingFishCore.LOGGER.error("读取世界玩家任务数据失败，本次会话不会覆盖损坏文件", exception);
        } finally {
            loaded = true;
        }
    }

    /**
     * JsonDataStore can recover a broken primary file from its last-known-good backup. Player tasks use a
     * stricter policy: seeing a malformed primary must keep that exact file untouched and make this manager
     * read-only, so validate the primary before asking the shared store to read it.
     */
    private static void validatePrimaryJson(Path dataPath) throws IOException {
        Path normalized = dataPath.toAbsolutePath().normalize();
        if (Files.notExists(normalized) || Files.size(normalized) == 0L) {
            return;
        }
        try (Reader reader = Utf8JsonFileIO.openReader(normalized)) {
            Map<Integer, TaskPlayerData> parsed = GSON.fromJson(reader, PLAYER_MAP_TYPE);
            if (parsed == null) {
                throw new IllegalStateException("非空玩家任务文件不能以 JSON null 作为根对象");
            }
        }
    }

    private static void calculateMaxTaskIDs() {
        maxPlayerTaskID = 0;
        for (int taskId : TASK_PLAYER_DATA_CACHE.keySet()) {
            maxPlayerTaskID = Math.max(maxPlayerTaskID, taskId);
        }
    }

    private static void validateLoadedData(Map<Integer, TaskPlayerData> loadedData) {
        if (loadedData == null) {
            throw new IllegalStateException("玩家任务数据根对象为空");
        }
        if (loadedData.size() > MAX_TASK_COUNT) {
            throw new IllegalStateException("玩家任务数量超过上限：" + loadedData.size());
        }
        for (Map.Entry<Integer, TaskPlayerData> entry : loadedData.entrySet()) {
            if (!isValidTask(entry.getKey(), entry.getValue())) {
                throw new IllegalStateException("玩家任务数据包含非法项，任务ID：" + entry.getKey());
            }
        }
    }

    private static boolean isValidTask(Integer taskId, TaskPlayerData task) {
        if (taskId == null
                || taskId <= 0
                || task == null
                || task.getTaskId() <= 0
                || task.getTaskId() != taskId
                || task.getTaskName() == null
                || task.getTaskContent() == null) {
            return false;
        }

        Set<TaskPlayerData.FinishedPlayer> finishedPlayers = task.getFinishedPlayers();
        if (finishedPlayers == null || finishedPlayers.size() > MAX_FINISHED_PLAYERS_PER_TASK) {
            return false;
        }
        return finishedPlayers.stream().allMatch(player -> player != null
                && player.getPlayerUUID() != null
                && player.getPlayerName() != null
                && !player.getPlayerName().isBlank());
    }

    public static synchronized void createPlayerTask(String taskName, String taskContent, long endTime) {
        ensureWritable();
        if (taskName == null || taskContent == null) {
            throw new IllegalArgumentException("玩家任务名称和内容不能为空");
        }
        if (TASK_PLAYER_DATA_CACHE.size() >= MAX_TASK_COUNT) {
            throw new IllegalStateException("玩家任务数量已达到上限：" + MAX_TASK_COUNT);
        }
        int newTaskId = nextTaskId();
        TaskPlayerData newTask = new TaskPlayerData(
                newTaskId, taskName, taskContent, System.currentTimeMillis(), endTime);
        TASK_PLAYER_DATA_CACHE.put(newTaskId, newTask);
        maxPlayerTaskID = newTaskId;
        dirty = true;
    }

    public static synchronized void createOnlyOnePlayerTask(String taskName, String taskContent, long endTime,
                                                             String playerName, UUID playerUUID) {
        createPlayerTask(taskName, taskContent, endTime);
    }

    public static void playerCompleteOwnTask(int taskId, String playerName, UUID playerUUID) {
        boolean changed = false;
        synchronized (TaskDataManager.class) {
            ensureLoaded();
            if (!writesEnabled) {
                DreamingFishCore.LOGGER.error("玩家任务数据处于只读保护，拒绝记录任务完成状态");
                return;
            }
            if (playerUUID == null || playerName == null || playerName.isBlank()) {
                DreamingFishCore.LOGGER.warn("拒绝记录缺少有效 UUID 或姓名的任务完成者，任务ID：{}", taskId);
                return;
            }

            TaskPlayerData task = TASK_PLAYER_DATA_CACHE.get(taskId);
            if (task == null) {
                DreamingFishCore.LOGGER.warn("玩家任务ID不存在：{}", taskId);
                return;
            }
            if (!task.isPlayerFinished(playerUUID)) {
                if (task.getFinishedPlayers().size() >= MAX_FINISHED_PLAYERS_PER_TASK) {
                    DreamingFishCore.LOGGER.error("任务 {} 的完成者数量已达到上限 {}",
                            taskId, MAX_FINISHED_PLAYERS_PER_TASK);
                    return;
                }
                task.addFinishedPlayer(playerName, playerUUID);
                dirty = true;
                changed = true;
            }
        }
        if (changed) {
            broadcastFullTaskDataToAllPlayers();
        }
    }

    public static void playerCompleteStoryTask(int taskId, String playerName, UUID playerUUID) {
        if (StoryManager.playerCompleteTask(taskId, playerName, playerUUID)) {
            broadcastFullTaskDataToAllPlayers();
        }
    }

    public static void broadcastFullTaskDataToAllPlayers() {
        MinecraftServer server = SERVER_INSTANCE;
        if (server == null) {
            return;
        }
        Map<Integer, TaskPlayerData> taskSnapshot = snapshotTaskData();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!AuthSessionGuard.isAuthenticated(player)) {
                continue;
            }
            Packet_SyncFullTaskData packet = new Packet_SyncFullTaskData(
                    player.getUUID(),
                    taskSnapshot,
                    StoryManager.getStagesForPlayer(player.getUUID()));
            DreamingFishCore_NetworkManager.sendToClient(player, packet);
        }
        DreamingFishCore.LOGGER.info("已向全服玩家广播最新任务数据");
    }

    /** 个人剧情进度变化时只刷新对应玩家，避免每一步都打扰全服客户端。 */
    public static void syncFullTaskData(ServerPlayer player) {
        if (player == null || !AuthSessionGuard.isAuthenticated(player)) {
            return;
        }
        Map<Integer, TaskPlayerData> taskSnapshot = snapshotTaskData();
        DreamingFishCore_NetworkManager.sendToClient(
                player,
                new Packet_SyncFullTaskData(
                        player.getUUID(),
                        taskSnapshot,
                        StoryManager.getStagesForPlayer(player.getUUID())));
    }

    public static synchronized boolean saveIfDirty(MinecraftServer server) {
        return saveIfDirty(() -> WorldDataPaths.resolve(server, "task", "player_tasks.json"));
    }

    static synchronized boolean saveIfDirty(Path dataPath) {
        return saveIfDirty(() -> dataPath);
    }

    private static boolean saveIfDirty(Supplier<Path> dataPathSupplier) {
        if (!loaded || !dirty) {
            return true;
        }
        if (!writesEnabled) {
            DreamingFishCore.LOGGER.error("玩家任务数据处于只读保护，拒绝覆盖损坏文件");
            return false;
        }
        try {
            JsonDataStore.writeAtomic(
                    dataPathSupplier.get(),
                    GSON,
                    TASK_PLAYER_DATA_CACHE);
            dirty = false;
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("写入世界玩家任务数据失败，保留 dirty 状态等待下次保存", exception);
            return false;
        }
    }

    public static synchronized void clearWorldCache() {
        TASK_PLAYER_DATA_CACHE.clear();
        maxPlayerTaskID = 0;
        dirty = false;
        loaded = false;
        writesEnabled = false;
    }

    static synchronized boolean areWritesEnabled() {
        return loaded && writesEnabled;
    }

    private static synchronized Map<Integer, TaskPlayerData> snapshotTaskData() {
        // 故事任务现在由 StoryManager 的阶段视图统一下发。旧的通用任务文件里
        // 可能还留着同编号记录，但不能再次进入客户端的普通任务/HUD 投影。
        Map<Integer, TaskPlayerData> snapshot = new java.util.LinkedHashMap<>();
        TASK_PLAYER_DATA_CACHE.forEach((taskId, task) -> {
            if (!StoryManager.isStoryTaskNumber(taskId)) {
                snapshot.put(taskId, task);
            }
        });
        return Map.copyOf(snapshot);
    }

    private static int nextTaskId() {
        if (maxPlayerTaskID == Integer.MAX_VALUE) {
            throw new IllegalStateException("玩家任务ID已耗尽");
        }
        int candidate = maxPlayerTaskID + 1;
        while (TASK_PLAYER_DATA_CACHE.containsKey(candidate)) {
            if (candidate == Integer.MAX_VALUE) {
                throw new IllegalStateException("玩家任务ID已耗尽");
            }
            candidate++;
        }
        return candidate;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("玩家任务数据尚未随世界加载");
        }
    }

    private static void ensureWritable() {
        ensureLoaded();
        if (!writesEnabled) {
            throw new IllegalStateException("玩家任务数据因加载失败已进入只读保护模式");
        }
    }
}
