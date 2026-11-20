package com.hhy.dreamingfishcore.gameplay.story_system;

import com.hhy.dreamingfishcore.gameplay.story_system.StoryWorldState.TaskProgress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 故事系统的**只读统计**（从 {@link StoryManager} 切出来的第一块）。
 *
 * <p>为什么先切这块：它没有任何副作用（只读世界状态 + 定义表），
 * 所以搬运是**纯移动**，行为可以逐行对照，风险最低。</p>
 *
 * <p>切出来之后 {@code StoryManager} 只保留一行转发，调用方零改动；
 * 以后要加"统计类"逻辑（比如周报、成就进度），也都往这里放，别再塞回门面。</p>
 */
public final class StoryStatistics {

    private StoryStatistics() {
    }

    /** 某个任务完成/参与的人数。 */
    public static int taskFinishedCount(int taskId) {
        StoryTaskData task = StoryManager.taskByNumber(taskId);
        if (task == null) {
            return 0;
        }
        StoryWorldState state = StoryManager.stateForQueries();
        TaskProgress progress = state.getTaskProgress(task.getTaskKey());
        return StoryManager.isPersonalStoryTask(task.getTaskKey())
                ? state.getPersonalTaskCompletionCount(task.getTaskKey())
                : progress == null ? 0 : progress.getParticipantCount();
    }

    /** 某个阶段下每个任务的完成人数（顺序与该阶段的任务顺序一致）。 */
    public static int[] stageTaskFinishedCounts(int stageNumber) {
        StoryStageData stage = StoryManager.stageByNumber(stageNumber);
        if (stage == null) {
            return new int[0];
        }
        List<StoryTaskData> tasks = StoryManager.trackedTasks(stage);
        int[] result = new int[tasks.size()];
        for (int index = 0; index < tasks.size(); index++) {
            result[index] = taskFinishedCount(tasks.get(index).getTaskId());
        }
        return result;
    }

    /** 某个任务的参与者名字（个人任务为已完成者）。 */
    public static List<String> taskFinishedPlayers(int taskId) {
        StoryTaskData task = StoryManager.taskByNumber(taskId);
        if (task == null) {
            return List.of();
        }
        StoryWorldState state = StoryManager.stateForQueries();
        if (StoryManager.isPersonalStoryTask(task.getTaskKey())) {
            return state.getPersonalTaskCompletions(task.getTaskKey()).values().stream().sorted().toList();
        }
        TaskProgress progress = state.getTaskProgress(task.getTaskKey());
        if (progress == null) {
            return List.of();
        }
        return progress.getParticipantNames().values().stream().sorted().toList();
    }

    /** 某个阶段有多少个不同玩家参与过。 */
    public static int stageUniquePlayerCount(int stageNumber) {
        StoryStageData stage = StoryManager.stageByNumber(stageNumber);
        if (stage == null) {
            return 0;
        }
        StoryWorldState state = StoryManager.stateForQueries();
        Set<String> players = new HashSet<>();
        for (StoryTaskData task : stage.getTasks()) {
            if (StoryManager.isPersonalStoryTask(task.getTaskKey())) {
                players.addAll(state.getPersonalTaskCompletions(task.getTaskKey()).keySet());
                continue;
            }
            TaskProgress progress = state.getTaskProgress(task.getTaskKey());
            if (progress != null) {
                players.addAll(progress.getParticipantNames().keySet());
            }
        }
        return players.size();
    }

    /** 全服任务完成总次数（公共 + 个人）。 */
    public static int totalTaskCompletions() {
        StoryWorldState state = StoryManager.stateForQueries();
        int sharedCompletions = state.getTaskProgressView().values().stream()
                .mapToInt(TaskProgress::getParticipantCount)
                .sum();
        int personalCompletions = state.getPersonalTaskProgressView().values().stream()
                .mapToInt(java.util.Map::size)
                .sum();
        return sharedCompletions + personalCompletions;
    }

    /** 全服参与过剧情任务的玩家数。 */
    public static int totalUniquePlayers() {
        StoryWorldState state = StoryManager.stateForQueries();
        Set<String> players = new HashSet<>();
        state.getTaskProgressView().values()
                .forEach(progress -> players.addAll(progress.getParticipantNames().keySet()));
        state.getPersonalTaskProgressView().values()
                .forEach(progress -> players.addAll(progress.keySet()));
        return players.size();
    }

    /** 定义里一共有多少个阶段。 */
    public static int stageCount() {
        return StoryManager.stageDefinitionCount();
    }

    /** 单个任务的一行统计文本（命令输出用）。 */
    public static String taskStatisticsString(int taskId) {
        StoryTaskData task = StoryManager.taskByNumber(taskId);
        if (task == null) {
            return "任务不存在";
        }
        StoryWorldState state = StoryManager.stateForQueries();
        boolean personal = StoryManager.isPersonalStoryTask(task.getTaskKey());
        TaskProgress progress = state.getTaskProgress(task.getTaskKey());
        String outcome = progress == null
                ? (personal ? "个人进行中" : "未发布")
                : progress.getOutcome().name();
        int playerCount = personal
                ? state.getPersonalTaskCompletionCount(task.getTaskKey())
                : progress == null ? 0 : progress.getParticipantCount();
        String countLabel = personal ? "人已完成个人部分" : "人在场";
        return String.format("任务 [%d / %s] %s: %s，%d %s",
                task.getTaskId(), task.getTaskKey(), task.getTaskName(), outcome, playerCount, countLabel);
    }

    /** 一个阶段的完整统计文本（命令输出用）。 */
    public static String stageStatisticsString(int stageNumber) {
        StoryStageData stage = StoryManager.stageByNumber(stageNumber);
        if (stage == null) {
            return "阶段不存在";
        }
        StoryWorldState state = StoryManager.stateForQueries();
        StringBuilder result = new StringBuilder();
        result.append(String.format("=== 阶段 %d / %s: %s ===\n",
                stage.getStageNumber(), stage.getStageId(), stage.getStageName()));
        for (StoryTaskData task : StoryManager.trackedTasks(stage)) {
            boolean personal = StoryManager.isPersonalStoryTask(task.getTaskKey());
            TaskProgress progress = state.getTaskProgress(task.getTaskKey());
            String outcome = progress == null
                    ? (personal ? "个人进行中" : "未发布")
                    : progress.getOutcome().name();
            int participantCount = personal
                    ? state.getPersonalTaskCompletionCount(task.getTaskKey())
                    : progress == null ? 0 : progress.getParticipantCount();
            String countLabel = personal ? "人已完成个人部分" : "人在场";
            result.append(String.format("  [%d / %s] %s: %s，%d %s\n",
                    task.getTaskId(), task.getTaskKey(), task.getTaskName(),
                    outcome, participantCount, countLabel));
        }
        result.append("阶段推进方式：服主手动；固定人数门槛请查看 worldtask list");
        return result.toString();
    }
}
