package com.hhy.dreamingfishcore.gameplay.task_system;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.gameplay.task_system.network.Packet_SyncFullTaskData;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.hhy.dreamingfishcore.server.server_management_system.GetServerInstance.SERVER_INSTANCE;

public class TaskDataManager {
    public static final Map<Integer, TaskPlayerData> TASK_PLAYER_DATA_CACHE = new ConcurrentHashMap<>();
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private static final Type PLAYER_MAP_TYPE = new TypeToken<Map<Integer, TaskPlayerData>>() {}.getType();
    private static int maxPlayerTaskID;
    private static boolean dirty;
    private static boolean loaded;

    public static void loadWorldData(MinecraftServer server) {
        TASK_PLAYER_DATA_CACHE.clear();
        maxPlayerTaskID = 0;
        try {
            Map<Integer, TaskPlayerData> loadedData = JsonDataStore.read(
                    WorldDataPaths.resolve(server, "task", "player_tasks.json"),
                    GSON,
                    PLAYER_MAP_TYPE,
                    ConcurrentHashMap::new);
            TASK_PLAYER_DATA_CACHE.putAll(loadedData);
            int beforeSize = TASK_PLAYER_DATA_CACHE.size();
            TASK_PLAYER_DATA_CACHE.entrySet().removeIf(entry -> !isValidTask(entry.getKey(), entry.getValue()));
            dirty = TASK_PLAYER_DATA_CACHE.size() != beforeSize;
            if (dirty) {
                DreamingFishCore.LOGGER.warn("玩家任务数据包含无效项，已在内存中清理 {} 条",
                        beforeSize - TASK_PLAYER_DATA_CACHE.size());
            }
            calculateMaxTaskIDs();
            DreamingFishCore.LOGGER.info("玩家任务数据加载完成，共 {} 条", TASK_PLAYER_DATA_CACHE.size());
        } catch (Exception exception) {
            dirty = false;
            DreamingFishCore.LOGGER.error("读取世界玩家任务数据失败，本次会话不会覆盖损坏文件", exception);
        } finally {
            loaded = true;
        }
    }

    private static void calculateMaxTaskIDs() {
        maxPlayerTaskID = 0;
        for (int taskId : TASK_PLAYER_DATA_CACHE.keySet()) {
            maxPlayerTaskID = Math.max(maxPlayerTaskID, taskId);
        }
    }

    private static boolean isValidTask(Integer taskId, TaskPlayerData task) {
        return taskId != null
                && taskId > 0
                && task != null
                && task.getTaskId() > 0
                && task.getTaskName() != null
                && task.getTaskContent() != null;
    }

    public static void createPlayerTask(String taskName, String taskContent, long endTime) {
        ensureLoaded();
        int newTaskId = maxPlayerTaskID + 1;
        TaskPlayerData newTask = new TaskPlayerData(
                newTaskId, taskName, taskContent, System.currentTimeMillis(), endTime);
        TASK_PLAYER_DATA_CACHE.put(newTaskId, newTask);
        maxPlayerTaskID = newTaskId;
        dirty = true;
    }

    public static void createOnlyOnePlayerTask(String taskName, String taskContent, long endTime,
                                                String playerName, UUID playerUUID) {
        createPlayerTask(taskName, taskContent, endTime);
    }

    public static void playerCompleteOwnTask(int taskId, String playerName, UUID playerUUID) {
        ensureLoaded();
        TaskPlayerData task = TASK_PLAYER_DATA_CACHE.get(taskId);
        if (task == null) {
            DreamingFishCore.LOGGER.warn("玩家任务ID不存在：{}", taskId);
            return;
        }
        if (!task.isPlayerFinished(playerUUID)) {
            task.addFinishedPlayer(playerName, playerUUID);
            dirty = true;
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

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!AuthSessionGuard.isAuthenticated(player)) {
                continue;
            }
            Packet_SyncFullTaskData packet = new Packet_SyncFullTaskData(
                    player.getUUID(),
                    TASK_PLAYER_DATA_CACHE,
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
        DreamingFishCore_NetworkManager.sendToClient(
                player,
                new Packet_SyncFullTaskData(
                        player.getUUID(),
                        TASK_PLAYER_DATA_CACHE,
                        StoryManager.getStagesForPlayer(player.getUUID())));
    }

    public static boolean saveIfDirty(MinecraftServer server) {
        if (!loaded || !dirty) {
            return true;
        }
        try {
            JsonDataStore.writeAtomic(
                    WorldDataPaths.resolve(server, "task", "player_tasks.json"),
                    GSON,
                    TASK_PLAYER_DATA_CACHE);
            dirty = false;
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("写入世界玩家任务数据失败，保留 dirty 状态等待下次保存", exception);
            return false;
        }
    }

    public static void clearWorldCache() {
        TASK_PLAYER_DATA_CACHE.clear();
        maxPlayerTaskID = 0;
        dirty = false;
        loaded = false;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("玩家任务数据尚未随世界加载");
        }
    }
}
