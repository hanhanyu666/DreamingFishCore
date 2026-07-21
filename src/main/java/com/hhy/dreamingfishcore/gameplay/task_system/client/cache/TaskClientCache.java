package com.hhy.dreamingfishcore.gameplay.task_system.client.cache;

import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryTaskData;
import com.hhy.dreamingfishcore.gameplay.task_system.TaskPlayerData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端任务与故事阶段快照。
 */
@OnlyIn(Dist.CLIENT)
public final class TaskClientCache {
    private static Map<Integer, TaskPlayerData> playerTasks = new ConcurrentHashMap<>();
    private static Map<Integer, StoryStageData> storyStages = new ConcurrentHashMap<>();

    private TaskClientCache() {
    }

    public static Map<Integer, TaskPlayerData> getPlayerTasks() {
        return Collections.unmodifiableMap(playerTasks);
    }

    public static void setPlayerTasks(Map<Integer, TaskPlayerData> tasks) {
        playerTasks = tasks == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(tasks);
    }

    public static Map<Integer, StoryStageData> getStoryStages() {
        return Collections.unmodifiableMap(storyStages);
    }

    public static void setStoryStages(Map<Integer, StoryStageData> stages) {
        storyStages = stages == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(stages);
    }

    public static void update(Map<Integer, TaskPlayerData> tasks, Map<Integer, StoryStageData> stages) {
        setPlayerTasks(tasks);
        setStoryStages(stages);
    }

    public static TaskPlayerData getPlayerTask(int taskId) {
        return playerTasks.get(taskId);
    }

    public static StoryStageData getStoryStage(int stageId) {
        return storyStages.get(stageId);
    }

    public static boolean hasUnfinishedTasks() {
        for (StoryStageData stage : storyStages.values()) {
            if (stage == null || stage.getTasks() == null) {
                continue;
            }
            for (StoryTaskData task : stage.getTasks()) {
                if (task != null && !task.isClientPlayerFinished()) {
                    return true;
                }
            }
        }
        for (TaskPlayerData task : playerTasks.values()) {
            if (task != null && !task.isClientPlayerFinished()) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        playerTasks.clear();
        storyStages.clear();
    }
}
