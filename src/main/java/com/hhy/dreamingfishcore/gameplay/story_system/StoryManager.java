package com.hhy.dreamingfishcore.gameplay.story_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.common.util.Utf8JsonFileIO;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 故事系统的唯一总入口（Facade / Manager）。
 *
 * <p>它把两类数据组合在一起：</p>
 * <ul>
 *     <li>{@link StoryStageData}、{@link StoryTaskData}：来自全局配置，回答“设计了什么内容”；</li>
 *     <li>{@link StoryWorldState}：来自当前世界存档，回答“这个世界实际发生了什么”。</li>
 * </ul>
 *
 * <p>NPC、任务脚本、管理命令和网络同步不应该各自保存一份故事进度，而应该调用本类。
 * 所有会修改静态状态的公开方法使用 {@code synchronized}，确保同一时刻只有一个线程修改数据。</p>
 */
public final class StoryManager {
    /** 配置文件结构版本，与世界存档版本是两套独立版本号。 */
    private static final int DEFINITION_SCHEMA_VERSION = 1;
    /** 全局故事定义文件。它属于服务器配置，不属于某个世界。 */
    private static final File STORY_DEFINITION_FILE =
            new File("config/dreamingfishcore/story_stage_data.json");
    /** 当前世界根目录下 data/dreamingfishcore/story/world_state.json。 */
    private static final String[] STATE_PATH = {"story", "world_state.json"};

    /** 故事定义和世界状态共用的 Gson 格式。 */
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    // 同一份定义建立多种索引，调用方可以按稳定字符串 ID 或旧数字编号快速查找。
    private static final Map<String, StoryStageData> STAGES_BY_ID = new ConcurrentHashMap<>();
    private static final Map<Integer, StoryStageData> STAGES_BY_NUMBER = new ConcurrentHashMap<>();
    private static final Map<String, StoryTaskData> TASKS_BY_KEY = new ConcurrentHashMap<>();
    private static final Map<Integer, StoryTaskData> TASKS_BY_NUMBER = new ConcurrentHashMap<>();
    /** 任务 ID 到所属阶段 ID 的反向索引，保留给后续任务执行器使用。 */
    private static final Map<String, String> TASK_STAGE_IDS = new ConcurrentHashMap<>();

    /** 当前服务器世界唯一的一份运行状态。 */
    private static StoryWorldState state = new StoryWorldState();
    /** 是否已经经过服务器世界加载流程。 */
    private static boolean loaded;
    /** 内存状态是否有尚未写入磁盘的变化。 */
    private static boolean dirty;
    /** 配置或存档损坏时设为 false，防止错误默认值覆盖原文件。 */
    private static boolean writesEnabled;

    /** 工具类不需要创建对象，所以构造方法私有。 */
    private StoryManager() {
    }

    /**
     * 在服务器世界启动时加载故事定义和世界进度。
     *
     * <p>加载顺序必须是：先定义、后世界状态。因为读取世界状态后需要确认其中的阶段 ID
     * 仍然存在于当前定义中。任何一部分失败都会进入只读保护。</p>
     */
    public static synchronized void loadWorldData(MinecraftServer server) {
        clearWorldCache();

        // 第一步：读取服务器全局配置，并建立阶段、任务索引。
        try {
            installDefinitions(loadDefinitionDocument());
        } catch (Exception exception) {
            installDefinitions(createDefaultDefinitions());
            loaded = true;
            writesEnabled = false;
            DreamingFishCore.LOGGER.error(
                    "故事定义加载失败，已使用只读默认定义；修复配置后重启服务器：{}",
                    STORY_DEFINITION_FILE.getAbsolutePath(), exception);
            return;
        }

        // 第二步：读取当前世界独有的运行状态。
        Path path = statePath(server);
        boolean fileExisted = Files.exists(path);
        try {
            StoryWorldState loadedState = JsonDataStore.read(
                    path,
                    GSON,
                    StoryWorldState.class,
                    StoryWorldState::new);
            boolean migrated = loadedState.validateAndMigrateLoadedState();
            if (!STAGES_BY_ID.containsKey(loadedState.getCurrentStageId())) {
                throw new IllegalStateException(
                        "世界存档当前阶段没有对应定义：" + loadedState.getCurrentStageId());
            }

            state = loadedState;
            loaded = true;
            writesEnabled = true;
            dirty = !fileExisted || migrated || activateDefaultTasks(state.getCurrentStageId());
            backupLegacyProgressIfPresent(server);

            DreamingFishCore.LOGGER.info(
                    "故事系统加载完成：阶段={}，定义={} 个，已发布任务={} 个，在线活动时间={} ticks",
                    state.getCurrentStageId(), STAGES_BY_ID.size(),
                    state.getTaskProgressView().size(), state.getActiveTicks());
        } catch (Exception exception) {
            state = new StoryWorldState();
            loaded = true;
            dirty = false;
            writesEnabled = false;
            DreamingFishCore.LOGGER.error(
                    "世界故事状态加载失败，本次会话已禁止故事状态修改与覆盖：{}", path, exception);
        }
    }

    /**
     * 读取并校验故事定义文件。文件缺失、空文件或空对象会生成最小默认配置；
     * 非空旧格式会先备份，再明确拒绝启动写入，避免猜错旧数据含义。
     */
    private static StoryDefinitionDocument loadDefinitionDocument() throws Exception {
        Path path = STORY_DEFINITION_FILE.toPath().toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (Files.notExists(path) || Files.size(path) == 0L) {
            StoryDefinitionDocument defaults = createDefaultDefinitions();
            writeDefinitionDocument(defaults);
            return defaults;
        }

        JsonElement root;
        try (Reader reader = Utf8JsonFileIO.openReader(STORY_DEFINITION_FILE)) {
            root = JsonParser.parseReader(reader);
        }
        if (root != null && root.isJsonObject() && root.getAsJsonObject().size() == 0) {
            StoryDefinitionDocument defaults = createDefaultDefinitions();
            writeDefinitionDocument(defaults);
            return defaults;
        }
        if (root == null || !root.isJsonObject()) {
            backupLegacyDefinition(path);
            throw new IllegalStateException("故事定义根节点必须是对象");
        }

        JsonObject object = root.getAsJsonObject();
        if (!object.has("schemaVersion") || !object.has("stages")) {
            backupLegacyDefinition(path);
            throw new IllegalStateException(
                    "检测到旧故事定义格式，原文件已备份；本次重构不静默转换旧格式");
        }

        StoryDefinitionDocument document = GSON.fromJson(object, StoryDefinitionDocument.class);
        validateDefinitionDocument(document);
        return document;
    }

    /** 将默认故事定义按 UTF-8 写入配置目录。 */
    private static void writeDefinitionDocument(StoryDefinitionDocument document) throws Exception {
        try (Writer writer = Utf8JsonFileIO.openWriter(STORY_DEFINITION_FILE)) {
            GSON.toJson(document, writer);
        }
    }

    /** 在同目录生成 .legacy-backup，保留无法自动迁移的旧配置。 */
    private static void backupLegacyDefinition(Path path) throws Exception {
        Path backup = path.resolveSibling(path.getFileName() + ".legacy-backup");
        Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    /** 备份旧 task_progress.json；旧格式语义不明确，因此不静默导入新世界状态。 */
    private static void backupLegacyProgressIfPresent(MinecraftServer server) {
        Path legacy = WorldDataPaths.resolve(server, "story", "task_progress.json");
        try {
            if (Files.notExists(legacy) || Files.size(legacy) == 0L) {
                return;
            }
            String content = Files.readString(legacy).trim();
            if (content.isEmpty() || "{}".equals(content)) {
                return;
            }
            Path backup = legacy.resolveSibling("task_progress.json.legacy-backup");
            Files.copy(legacy, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            DreamingFishCore.LOGGER.warn(
                    "检测到旧故事任务进度，已备份但未迁移：{}", backup);
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("备份旧故事任务进度失败", exception);
        }
    }

    /** 创建只包含“余梦期”、不包含正式任务的最小可启动配置。 */
    private static StoryDefinitionDocument createDefaultDefinitions() {
        StoryStageData defaultStage = new StoryStageData(
                StoryWorldState.DEFAULT_STAGE_ID,
                1,
                "余梦期",
                "梦屿故事的起点");
        return new StoryDefinitionDocument(DEFINITION_SCHEMA_VERSION, List.of(defaultStage));
    }

    /**
     * 校验整个配置文档，并保证阶段 ID、阶段编号、任务 ID、任务编号全局唯一。
     */
    private static void validateDefinitionDocument(StoryDefinitionDocument document) {
        if (document == null) {
            throw new IllegalStateException("故事定义不能为空");
        }
        if (document.schemaVersion != DEFINITION_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的故事定义版本：" + document.schemaVersion);
        }
        if (document.stages == null || document.stages.isEmpty()) {
            throw new IllegalStateException("故事定义至少需要一个阶段");
        }

        Set<String> stageIds = new HashSet<>();
        Set<Integer> stageNumbers = new HashSet<>();
        Set<String> taskKeys = new HashSet<>();
        Set<Integer> taskNumbers = new HashSet<>();
        for (StoryStageData stage : document.stages) {
            if (stage == null) {
                throw new IllegalStateException("故事定义包含空阶段");
            }
            stage.validateDefinition();
            if (!stageIds.add(stage.getStageId())) {
                throw new IllegalStateException("故事阶段ID重复：" + stage.getStageId());
            }
            if (!stageNumbers.add(stage.getStageNumber())) {
                throw new IllegalStateException("故事阶段编号重复：" + stage.getStageNumber());
            }
            for (StoryTaskData task : stage.getTasks()) {
                if (!taskKeys.add(task.getTaskKey())) {
                    throw new IllegalStateException("故事任务ID重复：" + task.getTaskKey());
                }
                if (!taskNumbers.add(task.getTaskId())) {
                    throw new IllegalStateException("故事任务数字编号重复：" + task.getTaskId());
                }
            }
        }
        if (!stageIds.contains(StoryWorldState.DEFAULT_STAGE_ID)) {
            throw new IllegalStateException("故事定义缺少默认阶段：" + StoryWorldState.DEFAULT_STAGE_ID);
        }
    }

    /**
     * 把已经验证的 List 转换成多个 Map 索引。
     * 这里先清空再安装，确保重载时不会残留旧定义。
     */
    private static void installDefinitions(StoryDefinitionDocument document) {
        STAGES_BY_ID.clear();
        STAGES_BY_NUMBER.clear();
        TASKS_BY_KEY.clear();
        TASKS_BY_NUMBER.clear();
        TASK_STAGE_IDS.clear();

        List<StoryStageData> sortedStages = new ArrayList<>(document.stages);
        sortedStages.sort(Comparator.comparingInt(StoryStageData::getStageNumber));
        for (StoryStageData stage : sortedStages) {
            STAGES_BY_ID.put(stage.getStageId(), stage);
            STAGES_BY_NUMBER.put(stage.getStageNumber(), stage);
            for (StoryTaskData task : stage.getTasks()) {
                TASKS_BY_KEY.put(task.getTaskKey(), task);
                TASKS_BY_NUMBER.put(task.getTaskId(), task);
                TASK_STAGE_IDS.put(task.getTaskKey(), stage.getStageId());
            }
        }
    }

    /** 发布阶段中 publishedByDefault=true 的任务，返回是否至少发布了一项新任务。 */
    private static boolean activateDefaultTasks(String stageId) {
        StoryStageData stage = STAGES_BY_ID.get(stageId);
        if (stage == null || stage.getTasks() == null) {
            return false;
        }
        boolean changed = false;
        for (StoryTaskData task : stage.getTasks()) {
            if (task.isPublishedByDefault()) {
                changed |= state.activateTask(task.getTaskKey());
            }
        }
        return changed;
    }

    /**
     * 每个服务器 tick 调用一次。只有至少一名玩家在线时才累计故事活动时间，
     * 因此长期关服或空服不会让剧情计时自动前进。
     */
    public static synchronized void tickActiveTime(MinecraftServer server) {
        if (!loaded || !writesEnabled || server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        if (state.incrementActiveTicks()) {
            dirty = true;
        }
    }

    /**
     * 只有状态发生变化时才原子写入世界存档。
     * 写入失败会保留 dirty=true，等待下一次自动保存重试。
     */
    public static synchronized void saveIfDirty(MinecraftServer server) {
        if (!loaded || !dirty || !writesEnabled) {
            return;
        }
        try {
            JsonDataStore.writeAtomic(statePath(server), GSON, state);
            dirty = false;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("写入世界故事状态失败，保留 dirty 状态等待下次保存", exception);
        }
    }

    /** 停服后清理所有静态缓存，避免下次进入另一个世界时继承旧进度。 */
    public static synchronized void clearWorldCache() {
        STAGES_BY_ID.clear();
        STAGES_BY_NUMBER.clear();
        TASKS_BY_KEY.clear();
        TASKS_BY_NUMBER.clear();
        TASK_STAGE_IDS.clear();
        state = new StoryWorldState();
        loaded = false;
        dirty = false;
        writesEnabled = false;
    }

    /**
     * 由服主或未来运营工具手动切换全服阶段，并发布该阶段的默认任务。
     *
     * @return 阶段确实发生变化时返回 true
     */
    public static synchronized boolean changeStage(String stageId) {
        ensureWritable();
        if (!STAGES_BY_ID.containsKey(stageId)) {
            throw new IllegalArgumentException("故事阶段不存在：" + stageId);
        }
        if (!state.changeStage(stageId)) {
            return false;
        }
        activateDefaultTasks(stageId);
        dirty = true;
        return true;
    }

    /** 设置或清除一个全服世界旗标。 */
    public static synchronized boolean setWorldFlag(String flagId, boolean enabled) {
        ensureWritable();
        if (!state.setWorldFlag(flagId, enabled)) {
            return false;
        }
        dirty = true;
        return true;
    }

    /** 记录一轮玩家讨论已经进入“等待服主回应”状态。 */
    public static synchronized boolean beginOperationRound(String sourceId) {
        ensureWritable();
        if (!state.beginOperationRound(sourceId)) {
            return false;
        }
        dirty = true;
        return true;
    }

    /** 记录服主已经为当前运营轮次发布了指定内容包。 */
    public static synchronized boolean publishOperationRound(String contentId) {
        ensureWritable();
        if (!state.publishOperationRound(contentId)) {
            return false;
        }
        dirty = true;
        return true;
    }

    /** 写入当前世界达成的结局 ID；传入空字符串可以清空。 */
    public static synchronized boolean setEndingId(String endingId) {
        ensureWritable();
        if (!state.setEndingId(endingId)) {
            return false;
        }
        dirty = true;
        return true;
    }

    /**
     * 将配置中已存在的任务发布到世界。发布后它才会进入进度分母和客户端视图。
     */
    public static synchronized boolean activateTask(String taskKey) {
        ensureWritable();
        if (!TASKS_BY_KEY.containsKey(taskKey)) {
            throw new IllegalArgumentException("故事任务不存在：" + taskKey);
        }
        if (!state.activateTask(taskKey)) {
            return false;
        }
        dirty = true;
        return true;
    }

    /**
     * 结算一个已发布任务。任务脚本应在判断成功/失败并收集区域内玩家后调用这里。
     *
     * @param taskKey 任务稳定字符串 ID
     * @param outcome 只能是 SUCCEEDED 或 FAILED
     * @param participants 结算瞬间应取得个人记录的玩家
     * @return 第一次成功结算返回 true；任务已经结算时返回 false
     */
    public static synchronized boolean resolveTask(
            String taskKey,
            StoryTaskOutcome outcome,
            Collection<StoryWorldState.TaskParticipant> participants) {
        ensureWritable();
        if (!TASKS_BY_KEY.containsKey(taskKey)) {
            throw new IllegalArgumentException("故事任务不存在：" + taskKey);
        }
        if (!state.resolveTask(taskKey, outcome, participants)) {
            return false;
        }
        dirty = true;
        return true;
    }

    /**
     * 旧客户端完成包的临时兼容入口。
     * 它只记录这个玩家的个人兼容状态，不能结算共享任务，也不能切换阶段。
     */
    public static synchronized boolean playerCompleteTask(
            int taskId, String playerName, UUID playerUUID) {
        ensureWritable();
        StoryTaskData task = TASKS_BY_NUMBER.get(taskId);
        if (task == null) {
            DreamingFishCore.LOGGER.warn("故事任务数字编号不存在：{}", taskId);
            return false;
        }
        try {
            if (!state.recordLegacyPlayerCompletion(
                    task.getTaskKey(), new StoryWorldState.TaskParticipant(playerUUID, playerName))) {
                return false;
            }
            dirty = true;
            DreamingFishCore.LOGGER.warn(
                    "兼容入口记录玩家 {} 完成故事任务 {}；未改变全服任务结果", playerName, task.getTaskKey());
            return true;
        } catch (IllegalStateException exception) {
            DreamingFishCore.LOGGER.warn("兼容入口拒绝未发布故事任务：{}", task.getTaskKey());
            return false;
        }
    }

    /** 返回按数字编号索引的所有阶段视图，不附带某个玩家的个人完成状态。 */
    public static Map<Integer, StoryStageData> getAllStages() {
        return getStagesForPlayer(null);
    }

    /**
     * 为指定玩家生成所有阶段的客户端视图。
     *
     * <p>这里返回的是副本和只读 Map。配置里尚未发布的任务会被过滤，
     * 已发布任务则合并全服结果和这个玩家的个人记录。</p>
     */
    public static Map<Integer, StoryStageData> getStagesForPlayer(UUID playerId) {
        ensureLoaded();
        Map<Integer, StoryStageData> result = new LinkedHashMap<>();
        STAGES_BY_NUMBER.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> result.put(entry.getKey(), createStageView(entry.getValue(), playerId)));
        return Collections.unmodifiableMap(result);
    }

    /** 返回按稳定字符串 ID 索引的阶段视图，主要用于管理命令补全。 */
    public static Map<String, StoryStageData> getAllStagesById() {
        ensureLoaded();
        Map<String, StoryStageData> result = new LinkedHashMap<>();
        STAGES_BY_NUMBER.values().stream()
                .sorted(Comparator.comparingInt(StoryStageData::getStageNumber))
                .forEach(stage -> result.put(stage.getStageId(), createStageView(stage, null)));
        return Collections.unmodifiableMap(result);
    }

    /** 把静态阶段定义与世界任务状态合并成一份可安全发送的阶段副本。 */
    private static StoryStageData createStageView(StoryStageData definition, UUID playerId) {
        List<StoryTaskData> taskViews = new ArrayList<>();
        for (StoryTaskData task : definition.getTasks()) {
            StoryWorldState.TaskProgress progress = state.getTaskProgress(task.getTaskKey());
            if (progress == null) {
                continue;
            }
            StoryTaskData view = task.copyForView();
            view.applyRuntimeView(progress, progress.hasParticipant(playerId));
            taskViews.add(view);
        }
        return definition.copyWithTasks(taskViews);
    }

    public static StoryStageData getStage(int stageNumber) {
        ensureLoaded();
        StoryStageData stage = STAGES_BY_NUMBER.get(stageNumber);
        return stage == null ? null : createStageView(stage, null);
    }

    public static StoryStageData getStage(String stageId) {
        ensureLoaded();
        StoryStageData stage = STAGES_BY_ID.get(stageId);
        return stage == null ? null : createStageView(stage, null);
    }

    public static StoryTaskData getTask(int taskId) {
        ensureLoaded();
        StoryTaskData task = TASKS_BY_NUMBER.get(taskId);
        return task == null ? null : createTaskView(task, null);
    }

    public static StoryTaskData getTask(String taskKey) {
        ensureLoaded();
        StoryTaskData task = TASKS_BY_KEY.get(taskKey);
        return task == null ? null : createTaskView(task, null);
    }

    /** 把单个任务定义与运行结果合并成视图；未发布任务会得到 published=false。 */
    private static StoryTaskData createTaskView(StoryTaskData definition, UUID playerId) {
        StoryTaskData view = definition.copyForView();
        StoryWorldState.TaskProgress progress = state.getTaskProgress(definition.getTaskKey());
        view.applyRuntimeView(progress, progress != null && progress.hasParticipant(playerId));
        return view;
    }

    public static List<StoryTaskData> getTasksByStage(int stageNumber) {
        StoryStageData stage = getStage(stageNumber);
        return stage == null ? null : stage.getTasks();
    }

    public static Map<Integer, StoryTaskData> getAllTasks() {
        ensureLoaded();
        Map<Integer, StoryTaskData> result = new LinkedHashMap<>();
        TASKS_BY_NUMBER.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    StoryTaskData view = createTaskView(entry.getValue(), null);
                    if (view.isTaskState()) {
                        result.put(entry.getKey(), view);
                    }
                });
        return Collections.unmodifiableMap(result);
    }

    public static boolean isPlayerFinishedTask(int taskId, UUID playerUUID) {
        StoryTaskData task = TASKS_BY_NUMBER.get(taskId);
        if (task == null) {
            return false;
        }
        StoryWorldState.TaskProgress progress = state.getTaskProgress(task.getTaskKey());
        return progress != null && progress.hasParticipant(playerUUID);
    }

    public static boolean isPlayerFinishedStage(int stageNumber, UUID playerUUID) {
        StoryStageData stage = STAGES_BY_NUMBER.get(stageNumber);
        if (stage == null) {
            return false;
        }
        List<StoryTaskData> published = getPublishedTasks(stage);
        return !published.isEmpty() && published.stream()
                .allMatch(task -> isPlayerFinishedTask(task.getTaskId(), playerUUID));
    }

    public static int getPlayerCompletedTaskCount(int stageNumber, UUID playerUUID) {
        StoryStageData stage = STAGES_BY_NUMBER.get(stageNumber);
        if (stage == null) {
            return 0;
        }
        return (int) getPublishedTasks(stage).stream()
                .filter(task -> isPlayerFinishedTask(task.getTaskId(), playerUUID))
                .count();
    }

    /** 从阶段配置中筛出已经存在于世界 taskProgress 中的任务。 */
    private static List<StoryTaskData> getPublishedTasks(StoryStageData stage) {
        return stage.getTasks().stream()
                .filter(task -> state.getTaskProgress(task.getTaskKey()) != null)
                .toList();
    }

    public static StoryStageData.MonsterModifier getMonsterModifier(int stageNumber) {
        StoryStageData stage = STAGES_BY_NUMBER.get(stageNumber);
        return stage == null ? null : stage.getMonsterModifier();
    }

    /**
     * 根据阶段倍率计算一组怪物属性值。
     *
     * <p>它只是纯计算，不会直接修改实体；未来怪物事件层需要把返回值写入怪物属性。</p>
     */
    public static float[] applyMonsterModifier(int stageNumber, float baseHealth, float baseDamage,
                                                float baseSpeed, float baseKnockbackResistance) {
        StoryStageData.MonsterModifier modifier = getMonsterModifier(stageNumber);
        if (modifier == null) {
            return new float[]{baseHealth, baseDamage, baseSpeed, baseKnockbackResistance};
        }
        return new float[]{
                baseHealth * modifier.getHealthMultiplier(),
                baseDamage * modifier.getDamageMultiplier(),
                baseSpeed * modifier.getSpeedMultiplier(),
                baseKnockbackResistance + modifier.getKnockbackResistance()
        };
    }

    public static int getTaskFinishedCount(int taskId) {
        StoryTaskData task = TASKS_BY_NUMBER.get(taskId);
        if (task == null) {
            return 0;
        }
        StoryWorldState.TaskProgress progress = state.getTaskProgress(task.getTaskKey());
        return progress == null ? 0 : progress.getParticipantCount();
    }

    public static int[] getStageTaskFinishedCounts(int stageNumber) {
        StoryStageData stage = STAGES_BY_NUMBER.get(stageNumber);
        if (stage == null) {
            return new int[0];
        }
        List<StoryTaskData> tasks = getPublishedTasks(stage);
        int[] result = new int[tasks.size()];
        for (int index = 0; index < tasks.size(); index++) {
            result[index] = getTaskFinishedCount(tasks.get(index).getTaskId());
        }
        return result;
    }

    public static List<String> getTaskFinishedPlayers(int taskId) {
        StoryTaskData task = TASKS_BY_NUMBER.get(taskId);
        if (task == null) {
            return List.of();
        }
        StoryWorldState.TaskProgress progress = state.getTaskProgress(task.getTaskKey());
        if (progress == null) {
            return List.of();
        }
        return progress.getParticipantNames().values().stream().sorted().toList();
    }

    public static int getStageUniquePlayerCount(int stageNumber) {
        StoryStageData stage = STAGES_BY_NUMBER.get(stageNumber);
        if (stage == null) {
            return 0;
        }
        Set<String> players = new HashSet<>();
        for (StoryTaskData task : stage.getTasks()) {
            StoryWorldState.TaskProgress progress = state.getTaskProgress(task.getTaskKey());
            if (progress != null) {
                players.addAll(progress.getParticipantNames().keySet());
            }
        }
        return players.size();
    }

    public static int getTotalTaskCompletions() {
        return state.getTaskProgressView().values().stream()
                .mapToInt(StoryWorldState.TaskProgress::getParticipantCount)
                .sum();
    }

    public static int getTotalUniquePlayers() {
        Set<String> players = new HashSet<>();
        state.getTaskProgressView().values()
                .forEach(progress -> players.addAll(progress.getParticipantNames().keySet()));
        return players.size();
    }

    public static int getStageCount() {
        return STAGES_BY_ID.size();
    }

    public static String getTaskStatisticsString(int taskId) {
        StoryTaskData task = TASKS_BY_NUMBER.get(taskId);
        if (task == null) {
            return "任务不存在";
        }
        StoryWorldState.TaskProgress progress = state.getTaskProgress(task.getTaskKey());
        String outcome = progress == null ? "未发布" : progress.getOutcome().name();
        int playerCount = progress == null ? 0 : progress.getParticipantCount();
        return String.format("任务 [%d / %s] %s: %s，%d 人在场",
                task.getTaskId(), task.getTaskKey(), task.getTaskName(), outcome, playerCount);
    }

    public static String getStageStatisticsString(int stageNumber) {
        StoryStageData stage = STAGES_BY_NUMBER.get(stageNumber);
        if (stage == null) {
            return "阶段不存在";
        }
        StringBuilder result = new StringBuilder();
        result.append(String.format("=== 阶段 %d / %s: %s ===\n",
                stage.getStageNumber(), stage.getStageId(), stage.getStageName()));
        for (StoryTaskData task : getPublishedTasks(stage)) {
            StoryWorldState.TaskProgress progress = state.getTaskProgress(task.getTaskKey());
            result.append(String.format("  [%d / %s] %s: %s，%d 人在场\n",
                    task.getTaskId(), task.getTaskKey(), task.getTaskName(),
                    progress.getOutcome(), progress.getParticipantCount()));
        }
        ProgressSnapshot progress = getProgress(stage.getStageId(), null);
        result.append(String.format("全服阶段任务进度: %d/%d，失败: %d，参与人数: %d 人",
                progress.globalResolved(), progress.publishedTasks(), progress.globalFailed(),
                getStageUniquePlayerCount(stageNumber)));
        return result.toString();
    }

    /**
     * 统计某阶段的双进度。
     *
     * <p>分母只包含已发布任务。全服分子包含成功和失败；个人分子表示指定玩家
     * 是否在结算参与者中。playerId 为 null 时个人进度自然为 0。</p>
     */
    public static ProgressSnapshot getProgress(String stageId, UUID playerId) {
        ensureLoaded();
        StoryStageData stage = STAGES_BY_ID.get(stageId);
        if (stage == null) {
            throw new IllegalArgumentException("故事阶段不存在：" + stageId);
        }
        int total = 0;
        int resolved = 0;
        int failed = 0;
        int personal = 0;
        int personalFailed = 0;
        for (StoryTaskData task : stage.getTasks()) {
            StoryWorldState.TaskProgress progress = state.getTaskProgress(task.getTaskKey());
            if (progress == null) {
                continue;
            }
            total++;
            if (progress.getOutcome().isResolved()) {
                resolved++;
            }
            if (progress.getOutcome() == StoryTaskOutcome.FAILED) {
                failed++;
            }
            if (progress.hasParticipant(playerId)) {
                personal++;
                if (progress.getOutcome() == StoryTaskOutcome.FAILED) {
                    personalFailed++;
                }
            }
        }
        return new ProgressSnapshot(total, resolved, failed, personal, personalFailed);
    }

    /**
     * 生成当前世界故事状态的只读摘要，供管理命令和未来管理界面使用。
     */
    public static Snapshot getSnapshot() {
        ensureLoaded();
        StoryStageData currentStage = STAGES_BY_ID.get(state.getCurrentStageId());
        StoryWorldState.OperationRound round = state.getOperationRound();
        ProgressSnapshot progress = getProgress(state.getCurrentStageId(), null);
        return new Snapshot(
                state.getSchemaVersion(),
                currentStage.getStageId(),
                currentStage.getStageNumber(),
                currentStage.getStageName(),
                state.getActiveTicks(),
                state.getStageEnteredAtActiveTick(),
                Set.copyOf(state.getWorldFlags()),
                round.getNumber(),
                round.getStatus(),
                round.getSourceId(),
                round.getContentId(),
                round.getChangedAtActiveTick(),
                state.getEndingId(),
                progress,
                writesEnabled);
    }

    public static boolean hasWorldFlag(String flagId) {
        ensureLoaded();
        return state.hasWorldFlag(flagId);
    }

    public static boolean areWritesEnabled() {
        return loaded && writesEnabled;
    }

    /** 兼容旧调用方：只把状态标记为待保存，不立即写磁盘。 */
    @Deprecated
    public static void saveStageData() {
        ensureLoaded();
        dirty = true;
    }

    @Deprecated
    public static void loadStageData() {
        throw new IllegalStateException("请通过世界生命周期加载故事系统");
    }

    /** 解析当前世界独有的故事状态存档路径。 */
    private static Path statePath(MinecraftServer server) {
        return WorldDataPaths.resolve(server, STATE_PATH[0], STATE_PATH[1]);
    }

    /** 在任何查询前确认服务器世界已经完成加载。 */
    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("故事系统尚未随服务器世界加载");
        }
    }

    /** 在任何修改前同时确认系统已加载且没有处于只读保护。 */
    private static void ensureWritable() {
        ensureLoaded();
        if (!writesEnabled) {
            throw new IllegalStateException("故事系统因加载失败已进入只读保护模式");
        }
    }

    /**
     * 一次进度查询的不可变结果。
     * {@code record} 会自动生成构造方法和 publishedTasks() 等访问方法。
     */
    public record ProgressSnapshot(
            int publishedTasks,
            int globalResolved,
            int globalFailed,
            int personalResolved,
            int personalFailed) {

        public float globalRatio() {
            return publishedTasks == 0 ? 0.0f : (float) globalResolved / publishedTasks;
        }

        public float personalRatio() {
            return publishedTasks == 0 ? 0.0f : (float) personalResolved / publishedTasks;
        }
    }

    /** 当前世界故事状态的不可变摘要，不允许调用者反向修改真实状态。 */
    public record Snapshot(
            int schemaVersion,
            String currentStageId,
            int currentStageNumber,
            String currentStageName,
            long activeTicks,
            long stageEnteredAtActiveTick,
            Set<String> worldFlags,
            long operationRoundNumber,
            StoryWorldState.OperationRoundStatus operationRoundStatus,
            String operationRoundSourceId,
            String operationRoundContentId,
            long operationRoundChangedAtActiveTick,
            String endingId,
            ProgressSnapshot currentStageProgress,
            boolean writesEnabled) {
    }

    /**
     * story_stage_data.json 的根对象。
     * Gson 通过字段名把 schemaVersion 和 stages 与 JSON 对应起来。
     */
    private static final class StoryDefinitionDocument {
        private int schemaVersion;
        private List<StoryStageData> stages;

        private StoryDefinitionDocument() {
        }

        private StoryDefinitionDocument(int schemaVersion, List<StoryStageData> stages) {
            this.schemaVersion = schemaVersion;
            this.stages = new ArrayList<>(stages);
        }
    }
}
