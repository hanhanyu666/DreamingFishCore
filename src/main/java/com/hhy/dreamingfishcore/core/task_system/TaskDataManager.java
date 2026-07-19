package com.hhy.dreamingfishcore.core.task_system;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.core.story_system.StoryStageManager;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.network.packets.task_system.Packet_SyncFullTaskData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.hhy.dreamingfishcore.server.GetServerInstance.SERVER_INSTANCE;

@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TaskDataManager {
    private static final File TASK_PLAYER_DATA_FILE = new File("config/dreamingfishcore/task_player_data.json");
    public static Map<Integer, TaskPlayerData> TASK_PLAYER_DATA_CACHE = new ConcurrentHashMap<>();
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private static int maxPlayerTaskID = 0;

    private static boolean dirCreated = false;
    private static boolean fileCreated = false;

    static Type playerMapType = new TypeToken<Map<Integer, TaskPlayerData>>() {}.getType();

    static {
        try {
            if (!TASK_PLAYER_DATA_FILE.getParentFile().exists()) {
                dirCreated = TASK_PLAYER_DATA_FILE.getParentFile().mkdirs();
                if (dirCreated) {
                    DreamingFishCore.LOGGER.info("玩家任务数据目录创建成功：{}", TASK_PLAYER_DATA_FILE.getParentFile().getPath());
                } else {
                    DreamingFishCore.LOGGER.info("玩家任务数据目录创建失败：{}", TASK_PLAYER_DATA_FILE.getParentFile().getPath());
                }
            }
            if (!TASK_PLAYER_DATA_FILE.exists()) {
                fileCreated = TASK_PLAYER_DATA_FILE.createNewFile();
                if (fileCreated) {
                    DreamingFishCore.LOGGER.info("玩家任务数据文件创建成功：{}", TASK_PLAYER_DATA_FILE.getPath());
                } else {
                    DreamingFishCore.LOGGER.error("玩家任务数据文件创建失败：{}", TASK_PLAYER_DATA_FILE.getPath());
                }
            } else {
                DreamingFishCore.LOGGER.info("玩家任务数据文件已存在：{}", TASK_PLAYER_DATA_FILE.getPath());
            }
        } catch (IOException e) {
            DreamingFishCore.LOGGER.error("玩家初始化任务数据文件失败", e);
        }
    }

    private static void calculateMaxTaskIDs() {
        //计算玩家任务最大ID
        for (int taskId : TASK_PLAYER_DATA_CACHE.keySet()) {
            if (taskId > maxPlayerTaskID) {
                maxPlayerTaskID = taskId;
            }
        }
    }

    @SubscribeEvent
    public static void loadingTaskData(ServerStartingEvent event) {
        //加载玩家任务数据
        try (FileReader reader = new FileReader(TASK_PLAYER_DATA_FILE)) {
            Map<Integer, TaskPlayerData> loadedData = GSON.fromJson(reader, playerMapType);
            TASK_PLAYER_DATA_CACHE = loadedData != null ? new ConcurrentHashMap<>(loadedData) : new ConcurrentHashMap<>();
            int beforeSize = TASK_PLAYER_DATA_CACHE.size();
            TASK_PLAYER_DATA_CACHE.entrySet().removeIf(entry -> !isValidTask(entry.getKey(), entry.getValue()));
            if (TASK_PLAYER_DATA_CACHE.size() != beforeSize) {
                DreamingFishCore.LOGGER.warn("玩家任务数据包含无效旧数据，已清理 {} 条", beforeSize - TASK_PLAYER_DATA_CACHE.size());
                savePlayerTaskData();
            }
        } catch (IOException | RuntimeException e) {
            DreamingFishCore.LOGGER.error("加载玩家任务数据失败", e);
            TASK_PLAYER_DATA_CACHE = new ConcurrentHashMap<>();
            savePlayerTaskData();
        }
        calculateMaxTaskIDs();
    }

    //写入文件
    private static void savePlayerTaskData() {
        try (FileWriter writer = new FileWriter(TASK_PLAYER_DATA_FILE)) {
            GSON.toJson(TASK_PLAYER_DATA_CACHE, writer);
        } catch (IOException e) {
            DreamingFishCore.LOGGER.error("保存玩家任务数据失败", e);
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

    //添加一个所有玩家的个人任务
    public static void createPlayerTask (String taskName, String taskContent, long endTime) {
        int newTaskId = maxPlayerTaskID + 1;
        long startTime = System.currentTimeMillis();
        TaskPlayerData newTask = new TaskPlayerData(newTaskId, taskName, taskContent, startTime, endTime);
        TASK_PLAYER_DATA_CACHE.put(newTaskId, newTask);
        maxPlayerTaskID = newTaskId;
        savePlayerTaskData();
    }

    //给专属玩家添加一个任务
    public static void createOnlyOnePlayerTask(String taskName, String taskContent, long endTime, String playerName, UUID playerUUID) {
        int newTaskId = maxPlayerTaskID + 1;
        long startTime = System.currentTimeMillis();
        TaskPlayerData newTask = new TaskPlayerData(newTaskId, taskName, taskContent, startTime, endTime);
        TASK_PLAYER_DATA_CACHE.put(newTaskId, newTask);
        maxPlayerTaskID = newTaskId;
        savePlayerTaskData();
    }

    //玩家完成个人任务
    public static void playerCompleteOwnTask(int taskId, String playerName, UUID playerUUID) {
        TaskPlayerData task = TASK_PLAYER_DATA_CACHE.get(taskId);
        if (task != null) {
            if (!task.isPlayerFinished(playerUUID)) {
                task.addFinishedPlayer(playerName, playerUUID);
                savePlayerTaskData();
                broadcastFullTaskDataToAllPlayers();
            } else {
                DreamingFishCore.LOGGER.warn("玩家任务ID不存在：{}", taskId);
            }
        }
    }

    //玩家完成故事任务
    public static void playerCompleteStoryTask(int taskId, String playerName, UUID playerUUID) {
        if (StoryStageManager.playerCompleteTask(taskId, playerName, playerUUID)) {
            broadcastFullTaskDataToAllPlayers();
        }
    }

    //对全服玩家更新任务数据
    public static void broadcastFullTaskDataToAllPlayers() {
        MinecraftServer server = SERVER_INSTANCE;
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Packet_SyncFullTaskData packet = new Packet_SyncFullTaskData(
                    player.getUUID(),
                    TASK_PLAYER_DATA_CACHE,
                    StoryStageManager.getAllStages()
            );
            DreamingFishCore_NetworkManager.sendToClient(
                    player,
                    packet
            );
        }
        DreamingFishCore.LOGGER.info("已向全服玩家广播最新任务数据");
    }
}
