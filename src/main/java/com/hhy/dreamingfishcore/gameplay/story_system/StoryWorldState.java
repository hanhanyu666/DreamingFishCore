package com.hhy.dreamingfishcore.gameplay.story_system;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 全服故事世界的运行时状态。
 *
 * <p>这个类只负责“数据和规则”，不负责读写文件，也不直接操作 Minecraft 世界。
 * 文件读写由 {@link StoryManager} 完成，任务区域、NPC 和尸潮事件未来也会通过
 * {@link StoryManager} 调用这里的状态变更方法。</p>
 *
 * <p>所有字段都会被 Gson 按名称保存到世界存档。因此这里的字段代表已经发生过的全服历史，
 * 而不是某个玩家临时打开的界面状态。</p>
 */
public final class StoryWorldState {
    /** 存档结构版本；版本变化时由 validateAndMigrateLoadedState 负责迁移或拒绝。 */
    public static final int CURRENT_SCHEMA_VERSION = 2;
    /** 新世界没有其他进度时使用的第一阶段。 */
    public static final String DEFAULT_STAGE_ID = StoryStageCatalog.DREAM_BEGINNING_ID;

    // 限制存档中可增长集合的大小，避免错误脚本无限写入世界数据。
    private static final int MAX_WORLD_FLAGS = 4096;
    private static final int MAX_TASK_STATES = 16384;
    /** 单项个人任务最多保留的玩家记录，避免异常数据无限膨胀。 */
    private static final int MAX_PERSONAL_TASK_PLAYERS = 16384;
    /** 所有可持久化 ID 的统一格式：小写字母开头，允许命名空间和路径字符。 */
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._:/-]{0,127}");

    /** 当前存档结构版本。 */
    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    /** 当前全服所处阶段。 */
    private String currentStageId = DEFAULT_STAGE_ID;
    /** 只有服务器有玩家在线时才增加的活动时间，单位是游戏 tick。 */
    private long activeTicks;
    /** 当前阶段开始时的活动时间，用于计算阶段持续时长。 */
    private long stageEnteredAtActiveTick;
    /** 全服共享的永久布尔条件，例如某个 NPC 已失踪。 */
    private Set<String> worldFlags = new LinkedHashSet<>();
    /** 最近一轮“玩家讨论 → 服主回应”的状态。 */
    private OperationRound operationRound = new OperationRound();
    /** 结局 ID；空字符串代表尚未进入终章。 */
    private String endingId = "";
    /** key 是任务稳定 ID，value 是这个任务在世界里的运行结果。 */
    private Map<String, TaskProgress> taskProgress = new LinkedHashMap<>();
    /**
     * key 是任务稳定 ID，value 是已经完成该任务个人部分的玩家。
     *
     * <p>个人完成和世界任务结算是两条独立记录：个人任务可以在世界任务尚未解锁时
     * 先保存；只有上层故事规则确认所有应参与玩家都完成后，才会写入 taskProgress。</p>
     */
    private Map<String, Map<String, String>> personalTaskProgress = new LinkedHashMap<>();

    /** Gson 反序列化存档时需要无参构造方法。 */
    public StoryWorldState() {
    }

    /**
     * 校验从 JSON 读取的状态，并迁移重构前短暂存在的 schema 1。
     *
     * @return 是否发生了需要重新保存的迁移或容器修复
     * @throws IllegalStateException 存档版本未来版本或数据内容非法时抛出
     */
    boolean validateAndMigrateLoadedState() {
        boolean migrated = false;
        if (schemaVersion == 1) {
            if (currentStageId != null && !currentStageId.contains(":")) {
                currentStageId = "dreamingfishcore:" + currentStageId;
            }
            schemaVersion = CURRENT_SCHEMA_VERSION;
            migrated = true;
        }
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的世界故事状态版本：" + schemaVersion);
        }

        requireValidId(currentStageId, "当前阶段");
        if (activeTicks < 0L) {
            throw new IllegalStateException("在线活动时间不能为负数");
        }
        if (stageEnteredAtActiveTick < 0L || stageEnteredAtActiveTick > activeTicks) {
            throw new IllegalStateException("阶段进入时间超出在线活动时间范围");
        }

        if (worldFlags == null) {
            worldFlags = new LinkedHashSet<>();
            migrated = true;
        }
        if (worldFlags.size() > MAX_WORLD_FLAGS) {
            throw new IllegalStateException("世界旗标数量超过限制：" + worldFlags.size());
        }
        for (String flag : worldFlags) {
            requireValidId(flag, "世界旗标");
        }
        if (!(worldFlags instanceof LinkedHashSet<?>)) {
            worldFlags = new LinkedHashSet<>(worldFlags);
            migrated = true;
        }

        if (operationRound == null) {
            operationRound = new OperationRound();
            migrated = true;
        }
        operationRound.validate(activeTicks);

        if (endingId == null) {
            endingId = "";
            migrated = true;
        }
        if (!endingId.isEmpty()) {
            requireValidId(endingId, "终章状态");
        }

        if (taskProgress == null) {
            taskProgress = new LinkedHashMap<>();
            migrated = true;
        }
        if (taskProgress.size() > MAX_TASK_STATES) {
            throw new IllegalStateException("故事任务状态数量超过限制：" + taskProgress.size());
        }
        for (Map.Entry<String, TaskProgress> entry : taskProgress.entrySet()) {
            requireValidId(entry.getKey(), "故事任务");
            if (entry.getValue() == null) {
                throw new IllegalStateException("故事任务状态不能为空：" + entry.getKey());
            }
            entry.getValue().validate(activeTicks, entry.getKey());
        }
        if (!(taskProgress instanceof LinkedHashMap<?, ?>)) {
            taskProgress = new LinkedHashMap<>(taskProgress);
            migrated = true;
        }

        if (personalTaskProgress == null) {
            personalTaskProgress = new LinkedHashMap<>();
            migrated = true;
        }
        if (personalTaskProgress.size() > MAX_TASK_STATES) {
            throw new IllegalStateException("个人故事任务状态数量超过限制：" + personalTaskProgress.size());
        }
        for (Map.Entry<String, Map<String, String>> entry : personalTaskProgress.entrySet()) {
            requireValidId(entry.getKey(), "个人故事任务");
            Map<String, String> players = entry.getValue();
            if (players == null) {
                throw new IllegalStateException("个人故事任务参与者状态不能为空：" + entry.getKey());
            }
            if (players.size() > MAX_PERSONAL_TASK_PLAYERS) {
                throw new IllegalStateException(
                        "个人故事任务参与者数量超过限制：" + entry.getKey());
            }
            for (Map.Entry<String, String> player : players.entrySet()) {
                try {
                    UUID.fromString(player.getKey());
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException(
                            "个人故事任务参与者UUID非法：" + player.getKey(), exception);
                }
                if (player.getValue() == null || player.getValue().isBlank()) {
                    throw new IllegalStateException("个人故事任务参与者名称为空：" + entry.getKey());
                }
            }
            if (!(players instanceof LinkedHashMap<?, ?>)) {
                entry.setValue(new LinkedHashMap<>(players));
                migrated = true;
            }
        }
        if (!(personalTaskProgress instanceof LinkedHashMap<?, ?>)) {
            personalTaskProgress = new LinkedHashMap<>(personalTaskProgress);
            migrated = true;
        }
        return migrated;
    }

    /** 测试和工具使用的公开校验入口。服务器加载时使用上面的迁移版本。 */
    public void validateLoadedState() {
        validateAndMigrateLoadedState();
    }

    /** 让在线活动时间前进一 tick；由 StoryManager 在服务器 tick 中调用。 */
    boolean incrementActiveTicks() {
        if (activeTicks == Long.MAX_VALUE) {
            return false;
        }
        activeTicks++;
        return true;
    }

    /** 切换阶段并记录新阶段的进入时间；重复切到当前阶段返回 false。 */
    boolean changeStage(String stageId) {
        requireValidId(stageId, "阶段");
        if (stageId.equals(currentStageId)) {
            return false;
        }
        currentStageId = stageId;
        stageEnteredAtActiveTick = activeTicks;
        return true;
    }

    /** 设置或清除一个全服世界旗标；返回值表示状态是否真的发生变化。 */
    boolean setWorldFlag(String flagId, boolean enabled) {
        requireValidId(flagId, "世界旗标");
        if (enabled) {
            if (worldFlags.size() >= MAX_WORLD_FLAGS && !worldFlags.contains(flagId)) {
                throw new IllegalStateException("世界旗标数量已达到上限：" + MAX_WORLD_FLAGS);
            }
            return worldFlags.add(flagId);
        }
        return worldFlags.remove(flagId);
    }

    /** 开始一轮等待世界回应的运营记录。具体讨论内容由运营层另行保存。 */
    boolean beginOperationRound(String sourceId) {
        requireValidId(sourceId, "运营轮次来源");
        if (operationRound.status == OperationRoundStatus.AWAITING_RESPONSE
                && sourceId.equals(operationRound.sourceId)) {
            return false;
        }
        operationRound.number++;
        operationRound.status = OperationRoundStatus.AWAITING_RESPONSE;
        operationRound.sourceId = sourceId;
        operationRound.contentId = "";
        operationRound.changedAtActiveTick = activeTicks;
        return true;
    }

    /** 将当前运营轮次标记为已发布，并记录服主实际发布的内容 ID。 */
    boolean publishOperationRound(String contentId) {
        requireValidId(contentId, "运营轮次内容");
        if (operationRound.status != OperationRoundStatus.AWAITING_RESPONSE) {
            return false;
        }
        operationRound.status = OperationRoundStatus.PUBLISHED;
        operationRound.contentId = contentId;
        operationRound.changedAtActiveTick = activeTicks;
        return true;
    }

    /** 写入或清空结局 ID。结局文本本身不放在运行状态中。 */
    boolean setEndingId(String newEndingId) {
        String normalized = newEndingId == null ? "" : newEndingId;
        if (!normalized.isEmpty()) {
            requireValidId(normalized, "终章状态");
        }
        if (normalized.equals(endingId)) {
            return false;
        }
        endingId = normalized;
        return true;
    }

    /** 发布一个任务；任务第一次出现时创建 ACTIVE 状态。 */
    boolean activateTask(String taskKey) {
        requireValidId(taskKey, "故事任务");
        if (taskProgress.containsKey(taskKey)) {
            return false;
        }
        if (taskProgress.size() >= MAX_TASK_STATES) {
            throw new IllegalStateException("故事任务状态数量已达到上限：" + MAX_TASK_STATES);
        }
        taskProgress.put(taskKey, new TaskProgress());
        return true;
    }

    /**
     * 结算一个任务，并记录结算瞬间的参与玩家。
     *
     * <p>这里故意只接受成功或失败，不能把 ACTIVE 再写回去，因此已经结算的任务不能重开。
     * 参与者会先收集到临时 Map 中，全部成功后才修改任务结果，保证一次结算尽量保持原子性。</p>
     */
    boolean resolveTask(String taskKey, StoryTaskOutcome outcome, Iterable<TaskParticipant> participants) {
        requireValidId(taskKey, "故事任务");
        if (outcome == null || !outcome.isResolved()) {
            throw new IllegalArgumentException("任务结束结果必须是成功或失败");
        }
        TaskProgress progress = taskProgress.get(taskKey);
        if (progress == null) {
            throw new IllegalStateException("故事任务尚未发布：" + taskKey);
        }
        if (progress.outcome.isResolved()) {
            return false;
        }

        Map<String, String> participantsToAdd = collectParticipants(participants);
        progress.outcome = outcome;
        progress.resolvedAtActiveTick = activeTicks;
        participantsToAdd.forEach(progress.participantNames::putIfAbsent);
        return true;
    }

    /**
     * 旧客户端完成包的兼容入口。
     *
     * <p>它只增加某个玩家的个人记录，不改变全服任务的 ACTIVE/SUCCEEDED/FAILED 状态。</p>
     */
    boolean recordLegacyPlayerCompletion(String taskKey, TaskParticipant participant) {
        requireValidId(taskKey, "故事任务");
        TaskProgress progress = taskProgress.get(taskKey);
        if (progress == null) {
            throw new IllegalStateException("故事任务尚未发布：" + taskKey);
        }
        return progress.participantNames.putIfAbsent(
                participant.playerId().toString(), participant.playerName()) == null;
    }

    /**
     * 记录一名玩家完成自己的个人故事任务。
     *
     * <p>个人完成不会直接结算世界任务，也不要求世界任务已经发布。调用方传入本项
     * 个人任务的应参与玩家集合后，返回值会告诉上层是否已经达到“所有人完成”的门槛；
     * 上层随后可以按自己的故事规则解锁/结算世界任务。</p>
     */
    PersonalCompletionResult recordPersonalCompletion(
            String taskKey,
            TaskParticipant participant,
            Iterable<UUID> expectedPlayers) {
        requireValidId(taskKey, "故事任务");
        if (participant == null) {
            throw new IllegalArgumentException("个人故事任务参与者不能为空");
        }

        Map<String, String> completedPlayers = personalTaskProgress.computeIfAbsent(
                taskKey, ignored -> {
                    if (personalTaskProgress.size() >= MAX_TASK_STATES) {
                        throw new IllegalStateException("个人故事任务状态数量已达到上限：" + MAX_TASK_STATES);
                    }
                    return new LinkedHashMap<>();
                });
        if (completedPlayers.size() >= MAX_PERSONAL_TASK_PLAYERS
                && !completedPlayers.containsKey(participant.playerId().toString())) {
            throw new IllegalStateException("个人故事任务参与者数量已达到上限：" + taskKey);
        }

        boolean changed = completedPlayers.putIfAbsent(
                participant.playerId().toString(), participant.playerName()) == null;
        Set<String> expectedIds = new LinkedHashSet<>();
        if (expectedPlayers != null) {
            for (UUID expectedPlayer : expectedPlayers) {
                if (expectedPlayer != null) {
                    expectedIds.add(expectedPlayer.toString());
                }
            }
        }
        // 当前完成者始终属于本次门槛，避免调用方在构造快照时漏掉自己。
        expectedIds.add(participant.playerId().toString());
        boolean allPlayersCompleted = !expectedIds.isEmpty()
                && expectedIds.stream().allMatch(completedPlayers::containsKey);
        return new PersonalCompletionResult(changed, allPlayersCompleted);
    }

    /** 兼容旧调用方：没有额外参与者时，当前玩家构成完整门槛。 */
    PersonalCompletionResult recordPersonalCompletion(
            String taskKey, TaskParticipant participant) {
        return recordPersonalCompletion(
                taskKey,
                participant,
                participant == null ? null : Set.of(participant.playerId()));
    }

    /** 将参与者对象转换为 UUID 字符串到名称的 Map，并去除重复玩家。 */
    private static Map<String, String> collectParticipants(Iterable<TaskParticipant> participants) {
        Map<String, String> result = new LinkedHashMap<>();
        if (participants == null) {
            return result;
        }
        for (TaskParticipant participant : participants) {
            if (participant == null) {
                continue;
            }
            result.putIfAbsent(participant.playerId().toString(), participant.playerName());
        }
        return result;
    }

    /** 返回任务状态的副本，防止调用方直接修改内部存档对象。 */
    TaskProgress getTaskProgress(String taskKey) {
        TaskProgress progress = taskProgress.get(taskKey);
        return progress == null ? null : progress.copy();
    }

    /** 返回所有任务状态的只读副本，用于日志、统计和保存前查询。 */
    Map<String, TaskProgress> getTaskProgressView() {
        Map<String, TaskProgress> copy = new LinkedHashMap<>();
        taskProgress.forEach((key, value) -> copy.put(key, value.copy()));
        return Collections.unmodifiableMap(copy);
    }

    /** 返回已经完成某项个人任务的玩家数量。 */
    int getPersonalTaskCompletionCount(String taskKey) {
        Map<String, String> players = personalTaskProgress.get(taskKey);
        return players == null ? 0 : players.size();
    }

    /** 判断某名玩家是否已经完成某项个人任务。 */
    boolean hasPersonalTaskCompletion(String taskKey, UUID playerId) {
        Map<String, String> players = personalTaskProgress.get(taskKey);
        return players != null && playerId != null && players.containsKey(playerId.toString());
    }

    /** 返回个人任务完成者的副本，供世界任务达到门槛时生成全服参与者快照。 */
    Map<String, String> getPersonalTaskCompletions(String taskKey) {
        Map<String, String> players = personalTaskProgress.get(taskKey);
        if (players == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(players));
    }

    /** 返回所有个人任务完成记录的深层只读副本，供统计和管理视图使用。 */
    Map<String, Map<String, String>> getPersonalTaskProgressView() {
        if (personalTaskProgress == null || personalTaskProgress.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        personalTaskProgress.forEach((taskKey, players) ->
                copy.put(taskKey, players == null
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(players))));
        return Collections.unmodifiableMap(copy);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getCurrentStageId() {
        return currentStageId;
    }

    public long getActiveTicks() {
        return activeTicks;
    }

    public long getStageEnteredAtActiveTick() {
        return stageEnteredAtActiveTick;
    }

    public Set<String> getWorldFlags() {
        return Collections.unmodifiableSet(worldFlags);
    }

    public boolean hasWorldFlag(String flagId) {
        return worldFlags.contains(flagId);
    }

    public OperationRound getOperationRound() {
        return operationRound.copy();
    }

    public String getEndingId() {
        return endingId;
    }

    /** 统一校验阶段、任务、旗标和运营内容使用的稳定 ID。 */
    public static String requireValidId(String id, String fieldName) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(fieldName + "ID非法：" + id);
        }
        return id;
    }

    /** 运营轮次从空闲、等待回应到已发布的三个状态。 */
    public enum OperationRoundStatus {
        IDLE,
        AWAITING_RESPONSE,
        PUBLISHED
    }

    /** 任务结算时在任务区域内、且符合模式要求的一个玩家。 */
    public record TaskParticipant(UUID playerId, String playerName) {
        public TaskParticipant {
            if (playerId == null) {
                throw new IllegalArgumentException("任务参与者UUID不能为空");
            }
            if (playerName == null || playerName.isBlank()) {
                throw new IllegalArgumentException("任务参与者名称不能为空");
            }
        }
    }

    /** 个人完成对“全体完成”门槛造成的变化。 */
    public record PersonalCompletionResult(boolean changed, boolean allPlayersCompleted) {
        /** 保留旧命名，避免旧调用方把“门槛达到”误解为单人直接结算。 */
        public boolean resolvedNow() {
            return allPlayersCompleted;
        }
    }

    /** 一个任务的永久运行结果和参与者名单。 */
    public static final class TaskProgress {
        /** ACTIVE、SUCCEEDED 或 FAILED。 */
        private StoryTaskOutcome outcome = StoryTaskOutcome.ACTIVE;
        /** 结算发生时的活动 tick；ACTIVE 时固定为 -1。 */
        private long resolvedAtActiveTick = -1L;
        /** key 为 UUID 字符串，value 为结算时的玩家名称。 */
        private Map<String, String> participantNames = new LinkedHashMap<>();

        /** Gson 反序列化存档时需要无参构造方法。 */
        public TaskProgress() {
        }

        /** 校验任务状态内部的一致性。 */
        private void validate(long activeTicks, String taskKey) {
            if (outcome == null) {
                throw new IllegalStateException("故事任务结果不能为空：" + taskKey);
            }
            if (outcome.isResolved()) {
                if (resolvedAtActiveTick < 0L || resolvedAtActiveTick > activeTicks) {
                    throw new IllegalStateException("故事任务结束时间非法：" + taskKey);
                }
            } else if (resolvedAtActiveTick != -1L) {
                throw new IllegalStateException("进行中的故事任务不能有结束时间：" + taskKey);
            }
            if (participantNames == null) {
                participantNames = new LinkedHashMap<>();
            }
            for (Map.Entry<String, String> participant : participantNames.entrySet()) {
                try {
                    UUID.fromString(participant.getKey());
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException("故事任务参与者UUID非法：" + participant.getKey(), exception);
                }
                if (participant.getValue() == null || participant.getValue().isBlank()) {
                    throw new IllegalStateException("故事任务参与者名称为空：" + taskKey);
                }
            }
            if (!(participantNames instanceof LinkedHashMap<?, ?>)) {
                participantNames = new LinkedHashMap<>(participantNames);
            }
        }

        /** 返回深一层的副本，避免暴露内部 participantNames Map。 */
        private TaskProgress copy() {
            TaskProgress copy = new TaskProgress();
            copy.outcome = outcome;
            copy.resolvedAtActiveTick = resolvedAtActiveTick;
            copy.participantNames = new LinkedHashMap<>(participantNames);
            return copy;
        }

        public StoryTaskOutcome getOutcome() {
            return outcome;
        }

        public long getResolvedAtActiveTick() {
            return resolvedAtActiveTick;
        }

        public int getParticipantCount() {
            return participantNames.size();
        }

        public boolean hasParticipant(UUID playerId) {
            return playerId != null && participantNames.containsKey(playerId.toString());
        }

        public Map<String, String> getParticipantNames() {
            return Collections.unmodifiableMap(participantNames);
        }
    }

    /** 一轮玩家讨论和服主世界回应的最小运行记录。 */
    public static final class OperationRound {
        /** 从 1 开始递增的运营轮次编号。 */
        private long number;
        /** 当前轮次处于 IDLE、AWAITING_RESPONSE 或 PUBLISHED。 */
        private OperationRoundStatus status = OperationRoundStatus.IDLE;
        /** 讨论、投票或运营记录的稳定来源 ID。 */
        private String sourceId = "";
        /** 服主发布的回应内容包 ID。 */
        private String contentId = "";
        /** 最近一次变更发生时的活动 tick。 */
        private long changedAtActiveTick;

        /** Gson 反序列化存档时需要无参构造方法。 */
        public OperationRound() {
        }

        /** 校验轮次状态与世界活动时间的关系。 */
        private void validate(long activeTicks) {
            if (number < 0L) {
                throw new IllegalStateException("运营轮次编号不能为负数");
            }
            if (status == null) {
                throw new IllegalStateException("运营轮次状态不能为空");
            }
            if (sourceId == null) {
                sourceId = "";
            }
            if (contentId == null) {
                contentId = "";
            }
            if (!sourceId.isEmpty()) {
                requireValidId(sourceId, "运营轮次来源");
            }
            if (!contentId.isEmpty()) {
                requireValidId(contentId, "运营轮次内容");
            }
            if (status == OperationRoundStatus.AWAITING_RESPONSE && sourceId.isEmpty()) {
                throw new IllegalStateException("等待世界回应的运营轮次必须包含来源");
            }
            if (status == OperationRoundStatus.PUBLISHED && contentId.isEmpty()) {
                // schema 1 没有保存内容包 ID，这里用来源 ID 生成一个兼容值。
                contentId = sourceId.isEmpty() ? "dreamingfishcore:legacy_round" : sourceId;
            }
            if (changedAtActiveTick < 0L || changedAtActiveTick > activeTicks) {
                throw new IllegalStateException("运营轮次变更时间超出在线活动时间范围");
            }
        }

        private OperationRound copy() {
            OperationRound copy = new OperationRound();
            copy.number = number;
            copy.status = status;
            copy.sourceId = sourceId;
            copy.contentId = contentId;
            copy.changedAtActiveTick = changedAtActiveTick;
            return copy;
        }

        public long getNumber() {
            return number;
        }

        public OperationRoundStatus getStatus() {
            return status;
        }

        public String getSourceId() {
            return sourceId;
        }

        public String getContentId() {
            return contentId;
        }

        public long getChangedAtActiveTick() {
            return changedAtActiveTick;
        }
    }
}
