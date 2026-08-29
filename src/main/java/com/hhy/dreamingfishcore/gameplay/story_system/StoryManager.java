package com.hhy.dreamingfishcore.gameplay.story_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceManager;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.gameplay.task_system.TaskDataManager;
import com.hhy.dreamingfishcore.gameplay.zhuiguang_system.ZhuiguangMembershipManager;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import com.hhy.dreamingfishcore.server.notice_system.NoticeDeliveryService;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerData;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerDataManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final Path STORY_DEFINITION_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(DreamingFishCore.MODID)
            .resolve("story_stage_data.json");
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
    /**
     * 内置开场世界任务与个人引导的关联。
     *
     * <p>个人引导本身仍由 GuidanceManager 按玩家保存；这里仅提供故事页和全体完成
     * 门槛所需的稳定关联，不改变 NPC 对话配置。</p>
     */
    private static final Map<String, List<String>> PERSONAL_TASK_GUIDANCE_IDS = Map.of(
            OpeningStoryDefinitionCatalog.SETTLE_IN_ABYDOS_TASK_ID,
            List.of("dreamingfishcore:guidance/opening/travel_to_abydos"),
            OpeningStoryDefinitionCatalog.MEET_BAIZHI_TASK_ID,
            List.of("dreamingfishcore:guidance/opening/talk_to_baizhi"),
            OpeningStoryDefinitionCatalog.CHOOSE_ZHUIGUANG_PATH_TASK_ID,
            // 联系周岑后任务已经分配；最终选择（加入或保持独立）才是完成条件。
            // 因此两种引导都要进入“全体已分配玩家完成”的门槛。
            List.of(
                    "dreamingfishcore:guidance/opening/contact_zhoucen",
                    "dreamingfishcore:guidance/opening/choose_membership"),
            OpeningStoryDefinitionCatalog.BUILD_ZHUIGUANG_BASE_TASK_ID,
            List.of("dreamingfishcore:guidance/opening/build_zhuiguang_base"));

    /** 当前服务器世界唯一的一份运行状态。 */
    private static StoryWorldState state = new StoryWorldState();
    /** 是否已经经过服务器世界加载流程。 */
    private static boolean loaded;
    /** 内存状态是否有尚未写入磁盘的变化。 */
    private static boolean dirty;
    /** 配置或存档损坏时设为 false，防止错误默认值覆盖原文件。 */
    private static boolean writesEnabled;
    /** 当前内存中故事定义的代数；每次成功热重载后递增。 */
    private static long definitionGeneration;

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
            definitionGeneration = 1L;
        } catch (Exception exception) {
            installDefinitions(createDefaultDefinitions());
            loaded = true;
            writesEnabled = false;
            DreamingFishCore.LOGGER.error(
                    "故事定义加载失败，已使用只读默认定义；修复配置后重启服务器：{}",
                    STORY_DEFINITION_PATH.toAbsolutePath(), exception);
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
     * 读取并校验故事定义文件。只有文件缺失时才生成最小默认配置；
     * 空文件或空对象会进入只读保护，避免覆盖可能因异常中断而损坏的配置。
     * 非空旧格式会先备份，再明确拒绝启动写入，避免猜错旧数据含义。
     */
    private static StoryDefinitionDocument loadDefinitionDocument() throws Exception {
        Path path = STORY_DEFINITION_PATH.toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (Files.notExists(path)) {
            StoryDefinitionDocument defaults = createDefaultDefinitions();
            writeDefinitionDocument(defaults);
            return defaults;
        }
        if (Files.size(path) == 0L) {
            throw new IllegalStateException("故事定义文件为空，已拒绝覆盖原文件");
        }

        JsonElement root = JsonDataStore.read(
                path,
                GSON,
                JsonElement.class,
                JsonObject::new);
        if (root != null && root.isJsonObject() && root.getAsJsonObject().size() == 0) {
            throw new IllegalStateException("故事定义对象为空，已拒绝覆盖原文件");
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
        boolean openingTasksAdded = ensureBuiltInOpeningDefinitions(document);
        validateDefinitionDocument(document);
        if (openingTasksAdded) {
            writeDefinitionDocument(document);
            DreamingFishCore.LOGGER.info("已向故事定义补齐开场阶段的四项任务");
        }
        return document;
    }

    /** 将默认故事定义原子写入配置目录，并保留上一版备份。 */
    private static void writeDefinitionDocument(StoryDefinitionDocument document) throws Exception {
        JsonDataStore.writeAtomic(STORY_DEFINITION_PATH, GSON, document);
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

    /** 创建“梦的开始”与“余梦期”的默认阶段定义。 */
    private static StoryDefinitionDocument createDefaultDefinitions() {
        StoryStageData dreamBeginning = new StoryStageData(
                StoryWorldState.DEFAULT_STAGE_ID,
                1,
                "梦的开始",
                OpeningStoryDefinitionCatalog.STAGE_DESCRIPTION);
        OpeningStoryDefinitionCatalog.createTasks().forEach(dreamBeginning::addTask);
        StoryStageData afterdream = new StoryStageData(
                "dreamingfishcore:afterdream",
                2,
                "余梦期",
                "");
        return new StoryDefinitionDocument(
                DEFINITION_SCHEMA_VERSION,
                List.of(dreamBeginning, afterdream));
    }

    /** 为已有服务器只补缺失任务；保留服主对阶段和同 ID 任务作出的改写。 */
    private static boolean ensureBuiltInOpeningDefinitions(StoryDefinitionDocument document) {
        if (document == null || document.stages == null) {
            return false;
        }
        for (StoryStageData stage : document.stages) {
            if (stage != null && OpeningStoryDefinitionCatalog.STAGE_ID.equals(stage.getStageId())) {
                boolean changed = OpeningStoryDefinitionCatalog.ensureTasks(stage);
                if ("梦屿故事的起点".equals(stage.getStageDescription())) {
                    stage.setStageDescription(OpeningStoryDefinitionCatalog.STAGE_DESCRIPTION);
                    changed = true;
                }
                return changed;
            }
        }
        return false;
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
            // 开场个人任务先由各玩家分别完成；世界任务只有在所有已分配玩家
            // 完成个人部分后才解锁，因此不能在阶段加载时提前发布。
            if (task.isPublishedByDefault()
                    && !isPersonalStoryTask(task.getTaskKey())
                    && state.activateTask(task.getTaskKey())) {
                changed = true;
                recordHistory(
                        WorldHistoryLog.EventType.TASK_PUBLISHED,
                        task.getTaskKey(),
                        "system",
                        Map.of("reason", "publishedByDefault"));
            }
        }
        return changed;
    }

    /**
     * 只校验磁盘上的候选定义，不替换当前正在运行的定义。
     * 这给管理员提供“先检查、后发布”的安全入口。
     */
    public static synchronized DefinitionSummary validateDefinitions() {
        ensureLoaded();
        StoryDefinitionDocument candidate = readDefinitionForReload();
        validateDefinitionCompatibility(candidate);
        return createDefinitionSummary(candidate);
    }

    /**
     * 校验并一次性替换故事定义。
     *
     * <p>候选文件在本方法中只存在于局部变量里。只有解析、结构校验、ID 唯一性校验以及
     * 与已经发布任务的兼容性校验全部通过后，才会调用 installDefinitions 修改索引；
     * 所以半份坏配置不会把服务器切换到半旧半新的状态。</p>
     */
    public static synchronized DefinitionSummary reloadDefinitions() {
        ensureWritable();
        StoryDefinitionDocument candidate = readDefinitionForReload();
        validateDefinitionCompatibility(candidate);
        installDefinitions(candidate);
        definitionGeneration = Math.max(1L, definitionGeneration + 1L);
        if (activateDefaultTasks(state.getCurrentStageId())) {
            dirty = true;
        }
        return createDefinitionSummary(candidate);
    }

    /** 当前成功安装的故事定义代数。 */
    public static synchronized long getDefinitionGeneration() {
        ensureLoaded();
        return definitionGeneration;
    }

    /** 内容包管理器需要写一条带执行者的历史事件；实际事件仍由本类集中组织。 */
    static synchronized void recordHistory(
            WorldHistoryLog.EventType type,
            String subjectId,
            String actor,
            Map<String, String> details) {
        if (!loaded) {
            return;
        }
        WorldHistoryLog.append(state.getActiveTicks(), type, subjectId, actor, details);
    }

    /** 热重载使用的定义文件读取包装，统一把 checked exception 转成管理员可读错误。 */
    private static StoryDefinitionDocument readDefinitionForReload() {
        try {
            return loadDefinitionDocument();
        } catch (Exception exception) {
            throw new IllegalStateException("故事定义校验失败：" + exception.getMessage(), exception);
        }
    }

    /** 旧任务已经发布后不能在热重载时凭空消失，否则历史进度将失去含义。 */
    private static void validateDefinitionCompatibility(StoryDefinitionDocument candidate) {
        Set<String> stageIds = new HashSet<>();
        Set<String> taskKeys = new HashSet<>();
        for (StoryStageData stage : candidate.stages) {
            stageIds.add(stage.getStageId());
            for (StoryTaskData task : stage.getTasks()) {
                taskKeys.add(task.getTaskKey());
            }
        }
        if (!stageIds.contains(state.getCurrentStageId())) {
            throw new IllegalStateException(
                    "候选故事定义删除了当前世界阶段：" + state.getCurrentStageId());
        }
        for (String publishedTaskKey : state.getTaskProgressView().keySet()) {
            if (!taskKeys.contains(publishedTaskKey)) {
                throw new IllegalStateException(
                        "候选故事定义删除了已发布任务：" + publishedTaskKey);
            }
        }
    }

    private static DefinitionSummary createDefinitionSummary(StoryDefinitionDocument document) {
        int taskCount = document.stages.stream()
                .mapToInt(stage -> stage.getTasks().size())
                .sum();
        return new DefinitionSummary(
                document.schemaVersion,
                document.stages.size(),
                taskCount,
                definitionGeneration);
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
    public static synchronized boolean saveIfDirty(MinecraftServer server) {
        if (!loaded || !dirty || !writesEnabled) {
            return true;
        }
        try {
            JsonDataStore.writeAtomic(statePath(server), GSON, state);
            dirty = false;
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("写入世界故事状态失败，保留 dirty 状态等待下次保存", exception);
            return false;
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
        definitionGeneration = 0L;
    }

    /**
     * 由服主或未来运营工具手动切换全服阶段，并发布该阶段的默认任务。
     *
     * @return 阶段确实发生变化时返回 true
     */
    public static synchronized boolean changeStage(String stageId) {
        return changeStage(stageId, "system");
    }

    /** 带执行者名称的阶段切换入口，供服主命令写入可追溯的历史记录。 */
    public static synchronized boolean changeStage(String stageId, String actor) {
        ensureWritable();
        if (!STAGES_BY_ID.containsKey(stageId)) {
            throw new IllegalArgumentException("故事阶段不存在：" + stageId);
        }
        String previousStageId = state.getCurrentStageId();
        if (!state.changeStage(stageId)) {
            return false;
        }
        activateDefaultTasks(stageId);
        recordHistory(
                WorldHistoryLog.EventType.STAGE_CHANGED,
                stageId,
                actor,
                Map.of("previousStageId", previousStageId));
        dirty = true;
        try {
            TaskDataManager.broadcastFullTaskDataToAllPlayers();
        } catch (RuntimeException exception) {
            // 阶段状态、默认任务和 dirty 已经完成；客户端同步失败不能回滚这次切换。
            DreamingFishCore.LOGGER.error(
                    "故事阶段已切换为 {}，但向在线玩家同步阶段任务数据失败", stageId, exception);
        }
        try {
            NoticeDeliveryService.deliverPendingToAllOnlinePlayers();
        } catch (RuntimeException exception) {
            // 阶段状态已经变更并标记为 dirty；公告投递失败不能影响这次切换。
            DreamingFishCore.LOGGER.error(
                    "故事阶段已切换为 {}，但向在线玩家补投阶段公告失败", stageId, exception);
        }
        return true;
    }

    /** 设置或清除一个全服世界旗标。 */
    public static synchronized boolean setWorldFlag(String flagId, boolean enabled) {
        ensureWritable();
        if (!state.setWorldFlag(flagId, enabled)) {
            return false;
        }
        recordHistory(
                WorldHistoryLog.EventType.WORLD_FLAG_CHANGED,
                flagId,
                "system",
                Map.of("enabled", Boolean.toString(enabled)));
        dirty = true;
        return true;
    }

    /** 记录一轮玩家讨论已经进入“等待服主回应”状态。 */
    public static synchronized boolean beginOperationRound(String sourceId) {
        ensureWritable();
        if (!state.beginOperationRound(sourceId)) {
            return false;
        }
        recordHistory(
                WorldHistoryLog.EventType.OPERATION_ROUND_STARTED,
                sourceId,
                "system",
                Map.of("roundNumber", Long.toString(state.getOperationRound().getNumber())));
        dirty = true;
        return true;
    }

    /** 记录服主已经为当前运营轮次发布了指定内容包。 */
    public static synchronized boolean publishOperationRound(String contentId) {
        ensureWritable();
        if (!state.publishOperationRound(contentId)) {
            return false;
        }
        recordHistory(
                WorldHistoryLog.EventType.OPERATION_ROUND_PUBLISHED,
                contentId,
                "system",
                Map.of("roundNumber", Long.toString(state.getOperationRound().getNumber())));
        dirty = true;
        return true;
    }

    /** 写入当前世界达成的结局 ID；传入空字符串可以清空。 */
    public static synchronized boolean setEndingId(String endingId) {
        ensureWritable();
        String previousEndingId = state.getEndingId();
        if (!state.setEndingId(endingId)) {
            return false;
        }
        String subjectId = endingId == null || endingId.isEmpty()
                ? "dreamingfishcore:none"
                : endingId;
        recordHistory(
                WorldHistoryLog.EventType.ENDING_CHANGED,
                subjectId,
                "system",
                Map.of("previousEndingId", previousEndingId,
                        "endingId", endingId == null ? "" : endingId));
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
        recordHistory(WorldHistoryLog.EventType.TASK_PUBLISHED, taskKey, "system", Map.of());
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
        recordHistory(
                outcome == StoryTaskOutcome.SUCCEEDED
                        ? WorldHistoryLog.EventType.TASK_SUCCEEDED
                        : WorldHistoryLog.EventType.TASK_FAILED,
                taskKey,
                "system",
                Map.of("participantCount", Integer.toString(participants == null ? 0 : participants.size())));
        dirty = true;
        return true;
    }

    /**
     * 按任务定义中的 {@code locationId} 结算，并在同一个服务器 tick 内取得区域参与者快照。
     *
     * <p>任务脚本不应该自行复制“生存/冒险、同维度、位于三维边界内”的筛选规则。
     * 没有配置地点的任务仍可调用 {@link #resolveTask(String, StoryTaskOutcome, Collection)}
     * 显式传入其他来源的参与者。</p>
     */
    public static synchronized boolean resolveTaskAtConfiguredLocation(
            MinecraftServer server, String taskKey, StoryTaskOutcome outcome) {
        ensureWritable();
        StoryTaskData task = TASKS_BY_KEY.get(taskKey);
        if (task == null) {
            throw new IllegalArgumentException("故事任务不存在：" + taskKey);
        }
        if (task.getLocationId().isBlank()) {
            throw new IllegalStateException("故事任务没有配置任务地点：" + taskKey);
        }
        return resolveTask(
                taskKey,
                outcome,
                TaskLocationManager.collectTaskParticipants(server, task.getLocationId()));
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

    /**
     * 用稳定字符串 ID 记录一名玩家完成个人故事任务。
     *
     * <p>个人进度先单独保存，即使对应世界任务尚未解锁也不会丢失。只有当前服务器中
     * 所有符合条件的玩家全部完成后，才会在世界层解锁并结算对应任务；这个过程不自动写入
     * 世界历史，历史内容由运营者手动维护。入口只接受服务端剧情验证后的完成。</p>
     */
    public static synchronized boolean recordPlayerTaskProgress(
            String taskKey, String playerName, UUID playerUUID) {
        ensureWritable();
        if (playerUUID == null || playerName == null || playerName.isBlank()) {
            DreamingFishCore.LOGGER.warn("拒绝记录缺少玩家身份的个人故事任务：{}", taskKey);
            return false;
        }
        if (!TASKS_BY_KEY.containsKey(taskKey)) {
            DreamingFishCore.LOGGER.warn("故事任务不存在：{}", taskKey);
            return false;
        }
        if (!isPersonalStoryTask(taskKey)) {
            DreamingFishCore.LOGGER.warn("拒绝把非个人故事任务当作个人进度记录：{}", taskKey);
            return false;
        }
        if (OpeningStoryDefinitionCatalog.isMemberOnlyTask(taskKey)
                && !ZhuiguangMembershipManager.isMember(playerUUID)) {
            DreamingFishCore.LOGGER.warn(
                    "拒绝为非逐光会成员记录建设任务个人进度：{}", playerName);
            return false;
        }

        try {
            StoryWorldState.TaskParticipant participant =
                    new StoryWorldState.TaskParticipant(playerUUID, playerName);
            Set<UUID> expectedPlayers = getExpectedPersonalPlayers(taskKey);
            expectedPlayers.add(playerUUID);
            StoryWorldState.PersonalCompletionResult result = state.recordPersonalCompletion(
                    taskKey, participant, expectedPlayers);

            // 个人任务全部完成后，才把对应世界任务从“未解锁”推进到 SUCCEEDED。
            // 这里直接操作世界状态，刻意绕过 activateTask/resolveTask 的历史记录入口。
            boolean worldAdvanced = false;
            if (result.allPlayersCompleted()) {
                state.activateTask(taskKey);
                StoryWorldState.TaskProgress worldProgress = state.getTaskProgress(taskKey);
                if (worldProgress != null && !worldProgress.getOutcome().isResolved()) {
                    List<StoryWorldState.TaskParticipant> completedParticipants =
                            personalTaskParticipants(taskKey);
                    worldAdvanced = state.resolveTask(
                            taskKey, StoryTaskOutcome.SUCCEEDED, completedParticipants);
                }
            }

            if (!result.changed() && !worldAdvanced) {
                return false;
            }
            dirty = true;
            DreamingFishCore.LOGGER.info(
                    "玩家 {} 完成个人故事任务 {}，{}",
                    playerName,
                    taskKey,
                    worldAdvanced ? "全体个人任务已完成，世界任务已推进" : "个人进度已记录");
            // 个人进度和世界任务状态变化后，所有在线玩家都需要看到最新视图。
            try {
                TaskDataManager.broadcastFullTaskDataToAllPlayers();
            } catch (RuntimeException exception) {
                DreamingFishCore.LOGGER.error(
                        "个人故事任务 {} 已写入，但向在线玩家广播最新世界进度失败",
                        taskKey,
                        exception);
            }
            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            DreamingFishCore.LOGGER.warn("记录个人故事任务 {} 失败", taskKey, exception);
            return false;
        }
    }

    /** 返回某个故事任务是否有独立的个人部分。 */
    public static boolean isPersonalStoryTask(String taskKey) {
        return taskKey != null && PERSONAL_TASK_GUIDANCE_IDS.containsKey(taskKey);
    }

    /** 返回这项个人任务当前应统计的服务器玩家。建设任务只统计逐光会成员。 */
    private static Set<UUID> getAssignedPersonalPlayers(String taskKey) {
        List<String> definitionIds = PERSONAL_TASK_GUIDANCE_IDS.get(taskKey);
        return definitionIds == null
                ? Set.of()
                : GuidanceManager.getPlayerIdsForDefinitions(definitionIds);
    }

    /**
     * 个人故事任务的全服门槛不再按“收到引导的人”计算，而是按玩家数据中的服务器玩家计算。
     * 这样一名玩家完成任务只会让比例增加一小段，不会直接把世界任务结算；旧存档中只有引导
     * 或完成记录、但尚未有玩家基础数据的 UUID 也会保留在统计集合中。
     */
    private static Set<UUID> getExpectedPersonalPlayers(String taskKey) {
        Set<UUID> expected = new LinkedHashSet<>();
        boolean memberOnly = OpeningStoryDefinitionCatalog.isMemberOnlyTask(taskKey);
        Map<UUID, PlayerData> playerDataById = Map.of();

        if (PlayerDataManager.isLoaded()) {
            try {
                playerDataById = PlayerDataManager.loadAllPlayerDataFromFile();
                for (Map.Entry<UUID, PlayerData> entry : playerDataById.entrySet()) {
                    UUID playerId = entry.getKey();
                    PlayerData data = entry.getValue();
                    if (playerId != null && (!memberOnly || data != null && data.isZhuiguangMember())) {
                        expected.add(playerId);
                    }
                }
            } catch (RuntimeException exception) {
                DreamingFishCore.LOGGER.warn(
                        "读取服务器玩家列表失败，个人故事任务 {} 暂时使用已知进度统计", taskKey, exception);
            }
        }

        // 兼容玩家数据迁移前已经创建的引导与完成记录。
        for (UUID assignedPlayer : getAssignedPersonalPlayers(taskKey)) {
            if (!memberOnly || !PlayerDataManager.isLoaded()
                    || playerDataById.getOrDefault(assignedPlayer, null) == null
                    || playerDataById.get(assignedPlayer).isZhuiguangMember()) {
                expected.add(assignedPlayer);
            }
        }
        Map<UUID, PlayerData> knownPlayerData = playerDataById;
        state.getPersonalTaskCompletions(taskKey).keySet().forEach(playerId -> {
            try {
                UUID parsed = UUID.fromString(playerId);
                PlayerData data = knownPlayerData.get(parsed);
                if (!memberOnly || !PlayerDataManager.isLoaded()
                        || data == null || data.isZhuiguangMember()) {
                    expected.add(parsed);
                }
            } catch (IllegalArgumentException ignored) {
                // 世界状态加载时已经校验过；这里仅防御手动编辑的旧存档。
            }
        });
        return expected;
    }

    /** 把个人完成记录转换成世界任务结算所需的参与者快照。 */
    private static List<StoryWorldState.TaskParticipant> personalTaskParticipants(String taskKey) {
        Map<String, String> completed = state.getPersonalTaskCompletions(taskKey);
        List<StoryWorldState.TaskParticipant> participants = new ArrayList<>();
        completed.forEach((playerId, playerName) -> {
            try {
                participants.add(new StoryWorldState.TaskParticipant(
                        UUID.fromString(playerId), playerName));
            } catch (IllegalArgumentException exception) {
                DreamingFishCore.LOGGER.warn(
                        "忽略个人任务 {} 中非法的玩家 UUID {}", taskKey, playerId);
            }
        });
        return participants;
    }

    /** 返回按数字编号索引的所有阶段视图，不附带某个玩家的个人完成状态。 */
    public static Map<Integer, StoryStageData> getAllStages() {
        return getStagesForPlayer(null);
    }

    /**
     * 为指定玩家生成所有阶段的客户端视图。
     *
     * <p>普通世界任务只有在发布后进入视图；带个人部分的任务只有在这名玩家
     * 实际收到对应的剧情引导后才进入故事页，并合并当前玩家的个人完成状态。
     * 这样不会因为配置里预先写了整条任务链，就把尚未经历的剧情提前剧透给玩家。
     * 管理/兼容查询传入 {@code null} 时仍返回所有任务。</p>
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
            if (playerId != null
                    && OpeningStoryDefinitionCatalog.isMemberOnlyTask(task.getTaskKey())
                    && !ZhuiguangMembershipManager.isMember(playerId)) {
                continue;
            }
            boolean personalTask = isPersonalStoryTask(task.getTaskKey());
            // 个人任务的定义会随故事阶段一起保存，但分配是逐个玩家发生的。
            // 没有收到对应引导的玩家不能看到这张任务卡；否则新玩家第一次打开
            // 故事页就会同时看到整条开场链。已写入个人完成记录时保留任务，
            // 以兼容引导存档因迁移/清理而缺失的旧数据。
            if (playerId != null
                    && personalTask
                    && !isPersonalTaskVisibleToPlayer(task.getTaskKey(), playerId)) {
                continue;
            }
            StoryWorldState.TaskProgress progress = state.getTaskProgress(task.getTaskKey());
            if (progress == null && !personalTask) {
                continue;
            }
            StoryTaskData view = task.copyForView();
            int personalCompleted = personalTask
                    ? state.getPersonalTaskCompletionCount(task.getTaskKey())
                    : 0;
            int personalExpected = personalTask
                    ? getExpectedPersonalPlayers(task.getTaskKey()).size()
                    : 0;
            boolean playerFinished = personalTask
                    ? state.hasPersonalTaskCompletion(task.getTaskKey(), playerId)
                    : progress != null && progress.hasParticipant(playerId);
            view.applyRuntimeView(
                    progress,
                    playerFinished,
                    personalTask,
                    personalCompleted,
                    personalExpected);
            taskViews.add(view);
        }
        StoryStageData view = definition.copyWithTasks(taskViews);
        view.setCurrentStage(definition.getStageId().equals(state.getCurrentStageId()));
        // 阶段全服比例必须基于完整定义计算，不能因客户端隐藏了尚未分配的个人任务而变化。
        view.setGlobalProgressPercentage(calculateGlobalStageProgress(definition));
        return view;
    }

    /** 以所有符合条件的玩家为分母，计算一个阶段的全服完成比例。 */
    private static float calculateGlobalStageProgress(StoryStageData definition) {
        if (definition == null || definition.getTasks() == null) {
            return 0.0f;
        }
        float progressSum = 0.0f;
        int trackedTaskCount = 0;
        for (StoryTaskData task : definition.getTasks()) {
            if (task == null) {
                continue;
            }
            if (isPersonalStoryTask(task.getTaskKey())) {
                int expected = getExpectedPersonalPlayers(task.getTaskKey()).size();
                if (expected <= 0) {
                    continue;
                }
                progressSum += Math.min(1.0f,
                        (float) state.getPersonalTaskCompletionCount(task.getTaskKey()) / expected);
                trackedTaskCount++;
                continue;
            }
            StoryWorldState.TaskProgress progress = state.getTaskProgress(task.getTaskKey());
            if (progress == null) {
                continue;
            }
            progressSum += progress.getOutcome().isResolved() ? 1.0f : 0.0f;
            trackedTaskCount++;
        }
        return trackedTaskCount == 0 ? 0.0f : progressSum / trackedTaskCount;
    }

    /** 判断个人任务是否已经实际分配给指定玩家。 */
    private static boolean isPersonalTaskVisibleToPlayer(String taskKey, UUID playerId) {
        if (playerId == null) {
            return true;
        }
        return getAssignedPersonalPlayers(taskKey).contains(playerId)
                || state.hasPersonalTaskCompletion(taskKey, playerId);
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

    /** 把单个任务定义与运行结果合并成视图；个人任务允许得到 published=false。 */
    private static StoryTaskData createTaskView(StoryTaskData definition, UUID playerId) {
        StoryTaskData view = definition.copyForView();
        StoryWorldState.TaskProgress progress = state.getTaskProgress(definition.getTaskKey());
        boolean personalTask = isPersonalStoryTask(definition.getTaskKey());
        int personalCompleted = personalTask
                ? state.getPersonalTaskCompletionCount(definition.getTaskKey())
                : 0;
        int personalExpected = personalTask
                ? getExpectedPersonalPlayers(definition.getTaskKey()).size()
                : 0;
        boolean playerFinished = personalTask
                ? state.hasPersonalTaskCompletion(definition.getTaskKey(), playerId)
                : progress != null && progress.hasParticipant(playerId);
        view.applyRuntimeView(
                progress,
                playerFinished,
                personalTask,
                personalCompleted,
                personalExpected);
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
                    if (view.isTaskState() || view.isPersonalTask()) {
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
        if (isPersonalStoryTask(task.getTaskKey())) {
            return state.hasPersonalTaskCompletion(task.getTaskKey(), playerUUID);
        }
        return progress != null && progress.hasParticipant(playerUUID);
    }

    public static boolean isPlayerFinishedStage(int stageNumber, UUID playerUUID) {
        StoryStageData stage = STAGES_BY_NUMBER.get(stageNumber);
        if (stage == null) {
            return false;
        }
        List<StoryTaskData> tracked = getTrackedTasks(stage);
        return !tracked.isEmpty() && tracked.stream()
                .allMatch(task -> isPlayerFinishedTask(task.getTaskId(), playerUUID));
    }

    public static int getPlayerCompletedTaskCount(int stageNumber, UUID playerUUID) {
        StoryStageData stage = STAGES_BY_NUMBER.get(stageNumber);
        if (stage == null) {
            return 0;
        }
        return (int) getTrackedTasks(stage).stream()
                .filter(task -> isPlayerFinishedTask(task.getTaskId(), playerUUID))
                .count();
    }

    /** 从阶段配置中筛出已经存在于世界 taskProgress 中的任务。 */
    private static List<StoryTaskData> getPublishedTasks(StoryStageData stage) {
        return stage.getTasks().stream()
                .filter(task -> state.getTaskProgress(task.getTaskKey()) != null)
                .toList();
    }

    /**
     * 返回当前阶段已经进入故事进度的任务：已发布的世界任务，加上可提前显示的个人任务。
     */
    private static List<StoryTaskData> getTrackedTasks(StoryStageData stage) {
        return stage.getTasks().stream()
                .filter(task -> isPersonalStoryTask(task.getTaskKey())
                        || state.getTaskProgress(task.getTaskKey()) != null)
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
        return isPersonalStoryTask(task.getTaskKey())
                ? state.getPersonalTaskCompletionCount(task.getTaskKey())
                : progress == null ? 0 : progress.getParticipantCount();
    }

    public static int[] getStageTaskFinishedCounts(int stageNumber) {
        StoryStageData stage = STAGES_BY_NUMBER.get(stageNumber);
        if (stage == null) {
            return new int[0];
        }
        List<StoryTaskData> tasks = getTrackedTasks(stage);
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
        if (isPersonalStoryTask(task.getTaskKey())) {
            return state.getPersonalTaskCompletions(task.getTaskKey()).values().stream().sorted().toList();
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
            if (isPersonalStoryTask(task.getTaskKey())) {
                players.addAll(state.getPersonalTaskCompletions(task.getTaskKey()).keySet());
                continue;
            }
            StoryWorldState.TaskProgress progress = state.getTaskProgress(task.getTaskKey());
            if (progress != null) {
                players.addAll(progress.getParticipantNames().keySet());
            }
        }
        return players.size();
    }

    public static int getTotalTaskCompletions() {
        int sharedCompletions = state.getTaskProgressView().values().stream()
                .mapToInt(StoryWorldState.TaskProgress::getParticipantCount)
                .sum();
        int personalCompletions = state.getPersonalTaskProgressView().values().stream()
                .mapToInt(Map::size)
                .sum();
        return sharedCompletions + personalCompletions;
    }

    public static int getTotalUniquePlayers() {
        Set<String> players = new HashSet<>();
        state.getTaskProgressView().values()
                .forEach(progress -> players.addAll(progress.getParticipantNames().keySet()));
        state.getPersonalTaskProgressView().values()
                .forEach(progress -> players.addAll(progress.keySet()));
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
        String outcome = progress == null
                ? (isPersonalStoryTask(task.getTaskKey()) ? "个人进行中" : "未发布")
                : progress.getOutcome().name();
        int playerCount = isPersonalStoryTask(task.getTaskKey())
                ? state.getPersonalTaskCompletionCount(task.getTaskKey())
                : progress == null ? 0 : progress.getParticipantCount();
        String countLabel = isPersonalStoryTask(task.getTaskKey())
                ? "人已完成个人部分"
                : "人在场";
        return String.format("任务 [%d / %s] %s: %s，%d %s",
                task.getTaskId(), task.getTaskKey(), task.getTaskName(), outcome, playerCount, countLabel);
    }

    public static String getStageStatisticsString(int stageNumber) {
        StoryStageData stage = STAGES_BY_NUMBER.get(stageNumber);
        if (stage == null) {
            return "阶段不存在";
        }
        StringBuilder result = new StringBuilder();
        result.append(String.format("=== 阶段 %d / %s: %s ===\n",
                stage.getStageNumber(), stage.getStageId(), stage.getStageName()));
        for (StoryTaskData task : getTrackedTasks(stage)) {
            StoryWorldState.TaskProgress progress = state.getTaskProgress(task.getTaskKey());
            String outcome = progress == null
                    ? (isPersonalStoryTask(task.getTaskKey()) ? "个人进行中" : "未发布")
                    : progress.getOutcome().name();
            int participantCount = isPersonalStoryTask(task.getTaskKey())
                    ? state.getPersonalTaskCompletionCount(task.getTaskKey())
                    : progress == null ? 0 : progress.getParticipantCount();
            String countLabel = isPersonalStoryTask(task.getTaskKey())
                    ? "人已完成个人部分"
                    : "人在场";
            result.append(String.format("  [%d / %s] %s: %s，%d %s\n",
                    task.getTaskId(), task.getTaskKey(), task.getTaskName(),
                    outcome, participantCount, countLabel));
        }
        ProgressSnapshot progress = getProgress(stage.getStageId(), null);
        result.append(String.format("全服玩家完成比例: %.1f%%，已结算任务: %d/%d，失败: %d，参与人数: %d 人",
                progress.globalPlayerRatio() * 100.0f,
                progress.globalResolved(), progress.publishedTasks(), progress.globalFailed(),
                getStageUniquePlayerCount(stageNumber)));
        return result.toString();
    }

    /**
     * 统计某阶段的双进度。
     *
     * <p>任务数量与当前玩家个人完成数仍保留给旧管理接口；全服比例由所有符合条件
     * 的玩家完成情况计算，个人任务不会因一名玩家完成就被当成全服已结算。</p>
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
        for (StoryTaskData task : getTrackedTasks(stage)) {
            StoryWorldState.TaskProgress progress = state.getTaskProgress(task.getTaskKey());
            total++;
            if (progress != null) {
                if (progress.getOutcome().isResolved()) {
                    resolved++;
                }
                if (progress.getOutcome() == StoryTaskOutcome.FAILED) {
                    failed++;
                }
            }
            boolean playerFinished = isPersonalStoryTask(task.getTaskKey())
                    ? state.hasPersonalTaskCompletion(task.getTaskKey(), playerId)
                    : progress != null && progress.hasParticipant(playerId);
            if (playerFinished) {
                personal++;
                if (progress != null && progress.getOutcome() == StoryTaskOutcome.FAILED) {
                    personalFailed++;
                }
            }
        }
        return new ProgressSnapshot(
                total,
                resolved,
                failed,
                personal,
                personalFailed,
                calculateGlobalStageProgress(stage));
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

    /**
     * Returns the server-authoritative story stage for systems that need to gate
     * runtime behaviour.  Entity AI can be constructed before a world is loaded,
     * so this accessor deliberately falls back to the first stage instead of
     * throwing during that bootstrap window.
     */
    public static synchronized String getCurrentStageIdOrDefault() {
        return loaded ? state.getCurrentStageId() : StoryWorldState.DEFAULT_STAGE_ID;
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
            int personalFailed,
            float globalPlayerRatio) {

        /** 保留旧的五参数构造方式，供外部兼容调用。 */
        public ProgressSnapshot(
                int publishedTasks,
                int globalResolved,
                int globalFailed,
                int personalResolved,
                int personalFailed) {
            this(publishedTasks, globalResolved, globalFailed,
                    personalResolved, personalFailed, -1.0f);
        }

        public float globalRatio() {
            return publishedTasks == 0 ? 0.0f : (float) globalResolved / publishedTasks;
        }

        /** 全服玩家完成比例；旧调用构造的快照回退到已结算任务比例。 */
        public float globalPlayerRatio() {
            return globalPlayerRatio >= 0.0f
                    ? Math.max(0.0f, Math.min(1.0f, globalPlayerRatio))
                    : globalRatio();
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

    /** 故事定义校验或热重载后的摘要，避免把内部 Map 暴露给命令层。 */
    public record DefinitionSummary(
            int schemaVersion,
            int stageCount,
            int taskCount,
            long generation) {
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
