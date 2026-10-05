package com.hhy.dreamingfishcore.gameplay.story_system;

import com.hhy.dreamingfishcore.gameplay.hospital_system.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceManager;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceSeed;
import com.hhy.dreamingfishcore.gameplay.opening_story_system.OpeningStory;
import com.hhy.dreamingfishcore.gameplay.opening_story_system.OpeningStoryProgress;
import com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory;
import com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamPlayerProgress;
import com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamWorldProgress;
import com.hhy.dreamingfishcore.gameplay.clue_system.ClueEntryDispatcher;
import com.hhy.dreamingfishcore.gameplay.clue_system.ClueSourceType;
import com.hhy.dreamingfishcore.gameplay.task_location_system.StoryLocationResolver;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_system.TaskDataManager;
import com.hhy.dreamingfishcore.gameplay.zhuiguang_system.ZhuiguangMembershipManager;
import com.hhy.dreamingfishcore.gameplay.npc_system.StoryNpcContentPolicy;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import com.hhy.dreamingfishcore.server.notice_system.NoticeDeliveryService;
import com.hhy.dreamingfishcore.server.notice_system.NoticeCategory;
import com.hhy.dreamingfishcore.server.notice_system.NoticeData;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    /** Java 阶段定义的内部版本，仅用于管理命令显示。 */
    private static final int DEFINITION_SCHEMA_VERSION = 1;
    /** 当前世界唯一的剧情事实文件；旧版 world_state/task_progress 不再读取。 */
    private static final String[] STATE_PATH = {"story", "story_state.json"};

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
    /** 当前内存中故事定义的代数；每次成功热重载后递增。 */
    private static long definitionGeneration;
    private static StoryOperationsCatalog.Document operationsDocument = StoryOperationsCatalog.Document.empty();
    /** 仅用于把地点观察转换为“进入地点”事实；不写入存档。 */
    private static final Map<UUID, String> LAST_LOCATIONS = new ConcurrentHashMap<>();

    /** 工具类不需要创建对象，所以构造方法私有。 */
    public static boolean isLoaded() { return loaded; }

    public static synchronized HospitalProgress getHospitalProgress() { ensureLoaded(); return state.getHospital(); }

    public static synchronized int getPersonalCompletionCount(String taskId) {
        return loaded ? state.getPersonalTaskCompletionCount(taskId) : 0;
    }

    private StoryManager() {
    }

    /** 返回唯一故事存档中的开场个人状态；阶段脚本不得自行维护副本。 */
    public static synchronized OpeningStoryProgress getOrCreateOpeningProgress(UUID playerId) {
        ensureWritable();
        if (playerId == null) {
            throw new IllegalArgumentException("玩家 UUID 不能为空");
        }
        return state.getOpeningPlayerProgress()
                .computeIfAbsent(playerId.toString(), ignored -> new OpeningStoryProgress());
    }

    public static synchronized OpeningStoryProgress findOpeningProgress(UUID playerId) {
        if (!loaded || playerId == null) {
            return null;
        }
        return state.getOpeningPlayerProgress().get(playerId.toString());
    }

    /** 返回唯一故事存档中的余梦期个人状态。 */
    public static synchronized AfterdreamPlayerProgress getOrCreateAfterdreamProgress(UUID playerId) {
        ensureWritable();
        if (playerId == null) {
            throw new IllegalArgumentException("玩家 UUID 不能为空");
        }
        return state.getAfterdreamPlayerProgress()
                .computeIfAbsent(playerId.toString(), ignored -> new AfterdreamPlayerProgress());
    }

    public static synchronized AfterdreamPlayerProgress findAfterdreamProgress(UUID playerId) {
        if (!loaded || playerId == null) {
            return null;
        }
        return state.getAfterdreamPlayerProgress().get(playerId.toString());
    }

    public static synchronized AfterdreamWorldProgress getAfterdreamWorldProgress() {
        ensureLoaded();
        return state.getAfterdreamWorldProgress();
    }

    /** 阶段脚本每次改变事实后调用；只有 StoryManager 会写入世界文件。 */
    public static synchronized void markDirty() {
        if (loaded && writesEnabled) {
            dirty = true;
        }
    }

    /* ---------------------------------------------------------------------
     * 运行时事实入口
     * ------------------------------------------------------------------ */

    /**
     * 所有登录事件的唯一剧情入口。阶段脚本不会自己订阅 NeoForge 事件，
     * 这样一名玩家登录时不会被两套状态机各推进一次。
     */
    public static synchronized void onPlayerAuthenticated(ServerPlayer player) {
        if (player == null || !loaded || !AuthSessionGuard.isAuthenticated(player)) {
            return;
        }
        if (writesEnabled) {
            deliverRecaps(player);
            restoreRecapEntry(player);
            dirty |= state.getOperations().reach(player.getUUID(), currentStageNumber() * 1000);
        }
        switch (getCurrentStageIdOrDefault()) {
            case OpeningStory.STAGE_ID ->
                    com.hhy.dreamingfishcore.gameplay.opening_story_system.OpeningStory
                            .onPlayerAuthenticated(player);
            case AfterdreamStory.STAGE_ID ->
                    com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory
                            .onPlayerAuthenticated(player);
            default -> {
                // 后续阶段尚未编写脚本。
            }
        }
        // 登录时立即观察当前位置，避免玩家在目标区域内重连后必须离开再回来
        // 才触发阶段地点事件。
        try {
            TaskLocationManager.findLocationAt(player.serverLevel(), player.blockPosition())
                    .ifPresent(location -> onLocationObserved(player, location));
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("登录时无法观察玩家 {} 的故事地点",
                    player.getScoreboardName(), exception);
        }
        syncStoryProjection(player);
        syncWorldTaskGuidance(player);
        HospitalStory.onAuthenticated(player);
        NoticeDeliveryService.syncVisibleNotices(player);
    }

    public static synchronized void onPlayerDisconnected(ServerPlayer player) {
        if (player != null) {
            LAST_LOCATIONS.remove(player.getUUID());
            HospitalStory.clearResponse(player.getUUID());
            if (AfterdreamStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
                com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory
                        .onPlayerDisconnected(player);
            }
        }
    }

    /**
     * NPC 对话界面真正打开时的会话边界。阶段脚本可以用它区分一次新
     * 对话和服务端为了刷新台词而重新下发的界面。
     */
    public static synchronized void onNpcDialogueOpened(ServerPlayer player, int npcId) {
        if (player == null || !loaded) {
            return;
        }
        // 线索入口：与 NPC 交互（不分阶段；声明了 npc=<编号> 的线索在这里发放）。
        ClueEntryDispatcher.fire(player, ClueSourceType.NPC, Integer.toString(npcId));
        if (AfterdreamStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
            com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory
                    .onNpcDialogueOpened(player, npcId);
        }
    }

    /** 在所有公告、私信、引导管理器加载完成后调用一次。 */
    public static synchronized void onServicesReady(MinecraftServer server) {
        if (!loaded) {
            return;
        }
        // 引导是当前阶段的投影，不允许上一阶段残留的 ACTIVE 记录继续成为
        // 玩家 HUD 的目标。历史记录仍保存在引导文件中，只关闭其活动状态。
        try {
            GuidanceManager.closeStoryStagesExcept(getCurrentStageIdOrDefault());
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("无法清理上一阶段的活动引导", exception);
        }
        if (AfterdreamStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
            com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory
                    .onStageActivated(getCurrentStageIdOrDefault());
            HospitalStory.reconcile();
        }
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (AuthSessionGuard.isAuthenticated(player)) {
                    onPlayerAuthenticated(player);
                }
            }
        }
    }

    public static synchronized void onNoticeRead(ServerPlayer player, String noticeKey, String title) {
        if (player == null || !loaded) {
            return;
        }
        // 线索入口：读完公告（声明了 broadcast=<公告 key> 的线索在这里发放）。
        ClueEntryDispatcher.fire(player, ClueSourceType.BROADCAST, noticeKey);
        if (OpeningStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
            com.hhy.dreamingfishcore.gameplay.opening_story_system.OpeningStory
                    .onNoticeRead(player, noticeKey);
        } else if (AfterdreamStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
            com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory
                    .onNoticeRead(player, noticeKey);
        }
        HospitalStory.onNoticeRead(player, noticeKey);
        // 如果玩家在阅读公告前就已经站在目标地点，地点边沿缓存不会再次变化；
        // 立即重放一次当前位置，避免玩家必须先离开再回来才能继续主线。
        replayCurrentStoryLocation(player);
    }

    public static synchronized void onNpcMessageRead(
            ServerPlayer player, String definitionId, int npcId) {
        if (player == null || !loaded) {
            return;
        }
        // 按当前阶段分发。原先只路由到余梦期，所以第三阶段（梦外行动）读完简报无法推进任务；
        // 这里补上分支，同时保持余梦期行为完全不变。
        String stageId = getCurrentStageIdOrDefault();
        if (AfterdreamStory.STAGE_ID.equals(stageId)) {
            com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory
                    .onNpcMessageRead(player, definitionId, npcId);
        } else if (com.hhy.dreamingfishcore.gameplay.extraction_story_system.ExtractionEraStory
                .STAGE_ID.equals(stageId)) {
            com.hhy.dreamingfishcore.gameplay.extraction_story_system.ExtractionEraStory
                    .onNpcMessageRead(player, definitionId, npcId);
        } else {
            return;
        }
        // 同上：读信本身可能在目标区域内发生，不能把“进入地点”错过。
        replayCurrentStoryLocation(player);
    }

    /** 将当前位置作为一次幂等的故事地点事实重新交给当前阶段。 */
    private static void replayCurrentStoryLocation(ServerPlayer player) {
        try {
            TaskLocationManager.findLocationAt(player.serverLevel(), player.blockPosition())
                    .ifPresent(location -> {
                        if (OpeningStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
                            OpeningStory.onLocationEntered(player, location);
                        } else if (AfterdreamStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
                            AfterdreamStory.onLocationEntered(player, location);
                        }
                    });
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("无法重放玩家 {} 的当前故事地点",
                    player.getScoreboardName(), exception);
        }
    }

    public static synchronized void onNpcReply(
            ServerPlayer player, String definitionId, String replyId) {
        if (player == null || !loaded
                || !OpeningStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
            return;
        }
        com.hhy.dreamingfishcore.gameplay.opening_story_system.OpeningStory
                .onNpcReply(player, definitionId, replyId);
    }

    /** 每 20 tick 观察一次地点；阶段脚本只收到一次真正的进入事件。 */
    public static synchronized void onLocationObserved(
            ServerPlayer player, TaskLocationDefinition location) {
        if (player == null || !loaded) {
            return;
        }
        applyContinuousLocationEffects(player, location);
        String locationId = location == null ? "" : location.getId();
        if (locationId == null || locationId.isBlank()) {
            LAST_LOCATIONS.remove(player.getUUID());
            return;
        }
        String normalized = locationId.trim();
        String previous = LAST_LOCATIONS.put(player.getUUID(), normalized);
        if (normalized.equals(previous)) {
            return;
        }
        // 线索入口：真正进入地点时触发。地点名与稳定 ID 各试一次，
        // 服主写 area=逐光会医疗接待点 或 area=dreamingfishcore:location_xxx 都能命中（发放本身幂等）。
        ClueEntryDispatcher.fire(player, ClueSourceType.AREA, location.getName());
        ClueEntryDispatcher.fire(player, ClueSourceType.AREA, normalized);
        if (OpeningStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
            com.hhy.dreamingfishcore.gameplay.opening_story_system.OpeningStory
                    .onLocationEntered(player, location);
        } else if (AfterdreamStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
            com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory
                    .onLocationEntered(player, location);
        }
    }

    /**
     * NPC 交互的唯一剧情入口。地点优先从已经由 NPC 管理器校验过的实体位置解析，
     * 因为玩家点击 NPC 时通常站在建筑边缘，玩家脚下方块不一定仍属于剧情地点。
     */
    public static synchronized void onNpcInteraction(
            ServerPlayer player, int npcId, Entity interactionTarget) {
        if (player == null || !loaded) {
            return;
        }
        String locationId = "";
        try {
            if (interactionTarget != null && interactionTarget.level() == player.level()) {
                locationId = TaskLocationManager.findLocationAt(
                        player.serverLevel(), interactionTarget.blockPosition())
                        .map(TaskLocationDefinition::getId).orElse("");
            }
            if (locationId.isBlank()) {
                locationId = TaskLocationManager.findLocationAt(
                        player.serverLevel(), player.blockPosition())
                        .map(TaskLocationDefinition::getId).orElse("");
            }
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("无法解析玩家 {} 的故事地点，NPC 事件按无地点处理",
                    player.getScoreboardName(), exception);
        }
        if (OpeningStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
            com.hhy.dreamingfishcore.gameplay.opening_story_system.OpeningStory
                    .onNpcInteraction(player, npcId, locationId);
        } else if (AfterdreamStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
            var hospitalLocation = TaskLocationManager.getLocation(AfterdreamStory.activeMedicalLocationId());
            if (hospitalLocation.isPresent() && (hospitalLocation.get().contains(player.level().dimension(), player.blockPosition())
                    || interactionTarget != null && hospitalLocation.get().contains(interactionTarget.level().dimension(), interactionTarget.blockPosition()))) {
                locationId = AfterdreamStory.activeMedicalLocationId();
            }
            com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory
                    .onNpcInteraction(player, npcId, locationId);
        }
    }

    public static synchronized void onGeneRevivalPotionUsed(ServerPlayer player) {
        if (player != null && AfterdreamStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
            com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory
                    .onGeneRevivalPotionUsed(player);
        }
    }

    public static synchronized void onVirusEvolution() {
        if (AfterdreamStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
            com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory
                    .onVirusEvolution();
        }
    }

    public static synchronized Optional<List<String>> getDialogueOverride(
            ServerPlayer player, int npcId) {
        if (AfterdreamStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
            Optional<List<String>> value =
                    com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory
                            .getDialogueOverride(player, npcId);
            if (value.isPresent()) {
                return value;
            }
        }
        if (OpeningStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
            return com.hhy.dreamingfishcore.gameplay.opening_story_system.OpeningStory
                    .getDialogueOverride(player, npcId);
        }
        return Optional.empty();
    }

    public static synchronized Optional<String> getDialogueRevision(
            ServerPlayer player, int npcId) {
        String value = "";
        if (AfterdreamStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
            value = com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory
                    .getDialogueRevision(player, npcId);
        }
        if (value.isBlank() && OpeningStory.STAGE_ID.equals(getCurrentStageIdOrDefault())) {
            value = com.hhy.dreamingfishcore.gameplay.opening_story_system.OpeningStory
                    .getDialogueRevision(player, npcId);
        }
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /**
     * NPC 私信系统用这个查询决定是否交给配置的 follow-up 机制。
     * 主线消息的后续节点必须由对应阶段文件决定，普通 NPC 私信仍可使用配置串联。
     */
    public static boolean isStoryControlledMessage(String definitionId) {
        return StoryNpcContentPolicy.isStoryControlledMessage(definitionId);
    }

    private static void applyContinuousLocationEffects(
            ServerPlayer player, TaskLocationDefinition location) {
        if (location == null
                || !StoryLocationResolver.matches(StoryLocationResolver.Role.ZHUIGUANG, location)
                || !ZhuiguangMembershipManager.isMember(player)) {
            return;
        }
        player.addEffect(new MobEffectInstance(
                MobEffects.REGENERATION, 60, 0, true, false, true));
    }

    private static void syncStoryProjection(ServerPlayer player) {
        try {
            GuidanceManager.syncToClient(player);
            TaskDataManager.syncFullTaskData(player);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("同步玩家 {} 的故事投影失败",
                    player.getScoreboardName(), exception);
        }
    }

    /**
     * 在服务器世界启动时加载故事定义和世界进度。
     *
     * <p>加载顺序必须是：先定义、后世界状态。因为读取世界状态后需要确认其中的阶段 ID
     * 仍然存在于当前定义中。任何一部分失败都会进入只读保护。</p>
     */
    public static synchronized void loadWorldData(MinecraftServer server) {
        clearWorldCache();

        // 阶段流程来自 Java，运营配置补充受限的前情草稿和世界任务定义；
        // 不再读取可执行的 story_stage_data.json，也不做旧定义迁移。
        try {
            HospitalConfig.reload();
            DailyTemplateSupportService.configuredItem();
            operationsDocument = StoryOperationsCatalog.read();
            StoryDefinitionDocument document = createDefaultDefinitions();
            validateDefinitionDocument(document);
            validateOperations(operationsDocument, document);
            installDefinitions(document);
            definitionGeneration = 1L;
        } catch (Exception exception) {
            operationsDocument = StoryOperationsCatalog.Document.empty();
            installDefinitions(createDefaultDefinitions());
            loaded = true;
            writesEnabled = false;
            DreamingFishCore.LOGGER.error(
                    "故事定义或运营配置加载失败，已使用只读默认定义；请按日志修复后重启服务器",
                    exception);
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
            // 仅升级同一 story_state 文件的 schema 3/4；旧文件名不参与迁移。
            boolean upgraded = loadedState.getSchemaVersion() == 3 || loadedState.getSchemaVersion() == 4;
            loadedState.validateLoadedState();
            if (!STAGES_BY_ID.containsKey(loadedState.getCurrentStageId())) {
                throw new IllegalStateException(
                        "世界存档当前阶段没有对应定义：" + loadedState.getCurrentStageId());
            }

            state = loadedState;
            for (String taskId : state.getTaskProgressView().keySet()) {
                if (!TASKS_BY_KEY.containsKey(taskId)) {
                    throw new IllegalStateException("已发布任务缺少定义，请恢复运营配置：" + taskId);
                }
            }
            loaded = true;
            writesEnabled = true;
            dirty = !fileExisted || upgraded;
            dirty |= activateDefaultTasks(state.getCurrentStageId());
            evaluateWorldTaskGates();

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
     * 创建当前开服版本的阶段定义。
     *
     * <p>这是唯一的定义入口。每个阶段文件负责写清楚自己的任务顺序，
     * StoryManager 只把它们索引起来供任务/界面查询；运行时绝不从 JSON
     * 读取可执行的节点、游标或迁移规则。</p>
     */
    private static StoryDefinitionDocument createDefaultDefinitions() {
        return createDefaultDefinitions(operationsDocument);
    }

    private static StoryDefinitionDocument createDefaultDefinitions(StoryOperationsCatalog.Document operations) {
        List<StoryStageData> stages = List.of(OpeningStory.createStageDefinition(),
                AfterdreamStory.createStageDefinition(),
                com.hhy.dreamingfishcore.gameplay.extraction_story_system.ExtractionEraStory
                        .createStageDefinition());
        for (StoryOperationsCatalog.WorldTask task : operations.worldTasks()) {
            StoryStageData stage = stages.stream().filter(value -> value.getStageId().equals(task.stageId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("世界任务阶段尚未实现：" + task.stageId()));
            stage.addTask(task.definition());
        }
        return new StoryDefinitionDocument(DEFINITION_SCHEMA_VERSION, stages);
    }

    private static void validateOperations(StoryOperationsCatalog.Document operations, StoryDefinitionDocument definitions) {
        Map<String, StoryTaskData> tasks = new LinkedHashMap<>();
        Map<String, Integer> stageNumbers = new LinkedHashMap<>();
        Map<String, Integer> taskStages = new LinkedHashMap<>();
        Map<String, String> taskStageIds = new LinkedHashMap<>();
        definitions.stages.forEach(stage -> {
            stageNumbers.put(stage.getStageId(), stage.getStageNumber());
            stage.getTasks().forEach(task -> {
                tasks.put(task.getTaskKey(), task);
                taskStages.put(task.getTaskKey(), stage.getStageNumber());
                taskStageIds.put(task.getTaskKey(), stage.getStageId());
            });
        });
        for (StoryOperationsCatalog.WorldTask task : operations.worldTasks()) {
            if (task.locationId() != null && !task.locationId().isBlank()
                    && TaskLocationManager.getLocation(task.locationId()).isEmpty()) {
                throw new IllegalArgumentException("世界任务地点不存在：" + task.locationId());
            }
            for (String prerequisite : task.prerequisiteTasks()) {
                StoryTaskData required = tasks.get(prerequisite);
                if (required == null || required.getScope() != StoryTaskData.Scope.PERSONAL
                        || taskStages.get(prerequisite) > stageNumbers.get(task.stageId())) {
                    throw new IllegalArgumentException("世界任务的前置个人任务不存在、类型错误或位于未来阶段：" + prerequisite);
                }
            }
        }
        if (loaded) {
            for (String taskId : state.getTaskProgressView().keySet()) {
                if (!tasks.containsKey(taskId)
                        || !java.util.Objects.equals(TASK_STAGE_IDS.get(taskId), taskStageIds.get(taskId))) {
                    throw new IllegalArgumentException("已发布任务不能删除或更换所属阶段：" + taskId);
                }
            }
        }
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

    /** 发布当前阶段中需要显示的共享任务，返回是否至少发布了一项新任务。 */
    private static boolean activateDefaultTasks(String stageId) {
        StoryStageData stage = STAGES_BY_ID.get(stageId);
        if (stage == null || stage.getTasks() == null) {
            return false;
        }
        boolean changed = false;
        for (StoryTaskData task : stage.getTasks()) {
            // 内置个人任务按个人状态展示；运营配置的世界任务默认关闭，
            // 由固定人数门槛或管理员命令发布。
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

    /** 校验当前 Java 阶段定义，不读取或写入旧的阶段配置文件。 */
    public static synchronized DefinitionSummary validateDefinitions() {
        ensureLoaded();
        StoryOperationsCatalog.Document candidate = StoryOperationsCatalog.read();
        StoryDefinitionDocument document = createDefaultDefinitions(candidate);
        validateDefinitionDocument(document);
        validateOperations(candidate, document);
        return createDefinitionSummary(document);
    }

    /** 重新安装当前 Java 阶段定义；可编辑文本由 StoryTextCatalog 单独重载。 */
    public static synchronized DefinitionSummary reloadDefinitions() {
        ensureWritable();
        StoryOperationsCatalog.Document candidate = StoryOperationsCatalog.read();
        StoryDefinitionDocument document = createDefaultDefinitions(candidate);
        validateDefinitionDocument(document);
        validateOperations(candidate, document);
        installDefinitions(document);
        operationsDocument = candidate;
        definitionGeneration = Math.max(1L, definitionGeneration + 1L);
        if (activateDefaultTasks(state.getCurrentStageId())) {
            dirty = true;
        }
        evaluateWorldTaskGates();
        refreshOnlineStoryViews();
        return createDefinitionSummary(document);
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

    private static DefinitionSummary createDefinitionSummary(StoryDefinitionDocument document) {
        int taskCount = document.stages.stream()
                .mapToInt(stage -> (int) stage.getTasks().stream()
                        .count())
                .sum();
        return new DefinitionSummary(
                document.schemaVersion,
                document.stages.size(),
                taskCount,
                definitionGeneration);
    }

    /**
     * 每个服务器 tick 调用一次。余梦期的世界事件先使用世界 gameTime 推进，
     * 因此“两游戏日后”的阶段事件即使暂时没有玩家在线也不会停住；旧的
     * 在线活动时间仍只在有玩家在线时累计，继续服务个人剧情记录。
     */
    public static synchronized void tickActiveTime(MinecraftServer server) {
        if (!loaded || !writesEnabled || server == null) {
            return;
        }
        if (AfterdreamStory.STAGE_ID.equals(state.getCurrentStageId())) {
            AfterdreamStory.tickWorldTime(server);
        }
        if (server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        if (state.incrementActiveTicks()) {
            dirty = true;
        }
        if (AfterdreamStory.STAGE_ID.equals(state.getCurrentStageId())) {
            com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory
                    .tickActiveTime(server);
        }
    }

    /**
     * 只有状态发生变化时才原子写入世界存档。
     * 写入失败会保留 dirty=true，等待下一次自动保存重试。
     */
    public static synchronized boolean saveIfDirty(MinecraftServer server) {
        boolean saved = true;
        if (loaded && dirty && writesEnabled) {
            try {
                JsonDataStore.writeAtomic(statePath(server), GSON, state);
                dirty = false;
            } catch (Exception exception) {
                DreamingFishCore.LOGGER.error("写入世界故事状态失败，保留 dirty 状态等待下次保存", exception);
                saved = false;
            }
        }
        return saved;
    }

    /** 停服后清理所有静态缓存，避免下次进入另一个世界时继承旧进度。 */
    public static synchronized void clearWorldCache() {
        STAGES_BY_ID.clear();
        STAGES_BY_NUMBER.clear();
        TASKS_BY_KEY.clear();
        TASKS_BY_NUMBER.clear();
        TASK_STAGE_IDS.clear();
        state = new StoryWorldState();
        LAST_LOCATIONS.clear();
        com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory
                .clearTransientSessions();
        loaded = false;
        dirty = false;
        writesEnabled = false;
        definitionGeneration = 0L;
        operationsDocument = StoryOperationsCatalog.Document.empty();
        HospitalConfig.clear();
        HospitalStory.clearTransient();
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
        if (STAGES_BY_ID.get(stageId).getStageNumber() < currentStageNumber()) {
            throw new IllegalArgumentException("世界阶段只能向前发布；不能通过切章重放历史任务");
        }
        if (!state.changeStage(stageId)) {
            return false;
        }
        // 阶段切换本身可能发生在玩家已经站在新阶段目标地点时；清掉上一阶段的
        // 边沿缓存，下一次投影会把当前位置当作一次新的进入事件处理。
        LAST_LOCATIONS.clear();
        for (String taskKey : TASKS_BY_KEY.keySet()) {
            if (isHistoricalTask(taskKey)) {
                state.getOperations().archive(taskKey);
            }
        }
        dirty = true;
        try {
            GuidanceManager.closeStoryStagesExcept(stageId);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("阶段切换后无法关闭旧阶段引导", exception);
        }
        activateDefaultTasks(stageId);
        recordHistory(
                WorldHistoryLog.EventType.STAGE_CHANGED,
                stageId,
                actor,
                Map.of("previousStageId", previousStageId));
        dirty = true;
        evaluateWorldTaskGates();
        try {
            // 阶段脚本是唯一的入口；这里不再经过第二个协调器。
            onStageActivated(stageId);
        } catch (RuntimeException exception) {
            // 阶段状态已经改变；流程效果失败时保留阶段并记录日志，避免回滚世界历史。
            DreamingFishCore.LOGGER.error(
                    "故事阶段已切换为 {}，但世界范围剧情节点执行失败", stageId, exception);
        }
        try {
            // 先执行阶段入口，再广播投影，客户端不会短暂看到上一阶段的当前目标。
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
        onStageActivatedForOnlinePlayers(stageId);
        return true;
    }

    /** 阶段切换后执行该阶段的世界入口，并为在线玩家建立个人投影。 */
    private static void onStageActivated(String stageId) {
        if (AfterdreamStory.STAGE_ID.equals(stageId)) {
            com.hhy.dreamingfishcore.gameplay.afterdream_story_system.AfterdreamStory
                    .onStageActivated(stageId);
            HospitalStory.reconcile();
        }
    }

    private static void onStageActivatedForOnlinePlayers(String stageId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (AuthSessionGuard.isAuthenticated(player)) {
                onPlayerAuthenticated(player);
            }
        }
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
     * 将当前阶段已定义的世界任务发布到世界；手动发布可以绕过人数门槛。
     */
    public static synchronized boolean activateTask(String taskKey) {
        ensureWritable();
        if (!TASKS_BY_KEY.containsKey(taskKey)) {
            throw new IllegalArgumentException("故事任务不存在：" + taskKey);
        }
        requireCurrentWorldTask(taskKey);
        if (!state.activateTask(taskKey)) {
            return false;
        }
        recordHistory(WorldHistoryLog.EventType.TASK_PUBLISHED, taskKey, "system", Map.of());
        dirty = true;
        refreshOnlineStoryViews();
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
        requireCurrentWorldTask(taskKey);
        HospitalStory.validateResolution(taskKey, outcome);
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
        for (StoryOperationsCatalog.WorldTask task : operationsDocument.worldTasks()) {
            if (task.id().equals(taskKey) && outcome == StoryTaskOutcome.SUCCEEDED
                    && task.successFlag() != null && !task.successFlag().isBlank()) {
                setWorldFlag(task.successFlag(), true);
            }
        }
        refreshOnlineStoryViews();
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
     * 管理员人工结算故事任务的旧命令入口。
     *
     * <p>客户端完成包不会调用此方法；普通主线仍必须由 Java 状态机写入。保留它
     * 只是让服主可以在确有运营需要时记录一个世界任务参与者。</p>
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
            if (!state.recordAdminPlayerParticipation(
                    task.getTaskKey(), new StoryWorldState.TaskParticipant(playerUUID, playerName))) {
                return false;
            }
            dirty = true;
            DreamingFishCore.LOGGER.warn(
                    "管理员人工入口记录玩家 {} 参与故事任务 {}；未改变全服任务结果",
                    playerName, task.getTaskKey());
            return true;
        } catch (IllegalStateException exception) {
            DreamingFishCore.LOGGER.warn("管理员人工入口拒绝未发布故事任务：{}", task.getTaskKey());
            return false;
        }
    }

    /**
     * 用稳定字符串 ID 记录一名玩家完成个人故事任务。
     *
     * <p>个人进度先单独保存，不再参与全服分母、比例或世界任务自动结算。
     * 阶段和公告由服主手动推进；入口只接受服务端剧情验证后的完成。</p>
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
        if (isHistoricalTask(taskKey) || isTaskWaived(taskKey, playerUUID)) {
            return false;
        }
        if (OpeningStory.isMemberOnlyTask(taskKey)
                && !ZhuiguangMembershipManager.isMember(playerUUID)) {
            DreamingFishCore.LOGGER.warn(
                    "拒绝为非逐光会成员记录建设任务个人进度：{}", playerName);
            return false;
        }

        try {
            StoryWorldState.TaskParticipant participant =
                    new StoryWorldState.TaskParticipant(playerUUID, playerName);
            StoryWorldState.PersonalCompletionResult result = state.recordPersonalCompletion(
                    taskKey, participant, Set.of());

            // 仅真实的新完成记录参与固定人数门槛；章节切换仍由服主操作。
            if (!result.changed()) {
                return false;
            }
            dirty = true;
            evaluateWorldTaskGates();
            DreamingFishCore.LOGGER.info(
                    "玩家 {} 完成个人故事任务 {}，已记录个人事实（阶段由服主推进）",
                    playerName,
                    taskKey);
            // 个人记录变化后刷新在线玩家视图；不再据此结算世界任务。
            try {
                TaskDataManager.broadcastFullTaskDataToAllPlayers();
            } catch (RuntimeException exception) {
                DreamingFishCore.LOGGER.error(
                        "个人故事任务 {} 已写入，但向玩家同步最新任务视图失败",
                        taskKey,
                        exception);
            }
            return true;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            DreamingFishCore.LOGGER.warn("记录个人故事任务 {} 失败", taskKey, exception);
            return false;
        }
    }

    /** 以稳定任务 ID 查询某名玩家是否已经取得个人故事记录。 */
    public static synchronized boolean isPlayerFinishedTask(String taskKey, UUID playerUUID) {
        if (taskKey == null || taskKey.isBlank() || playerUUID == null || !loaded) {
            return false;
        }
        if (!TASKS_BY_KEY.containsKey(taskKey)) {
            return false;
        }
        if (isPersonalStoryTask(taskKey)) {
            return state.hasPersonalTaskCompletion(taskKey, playerUUID);
        }
        StoryWorldState.TaskProgress progress = state.getTaskProgress(taskKey);
        return progress != null && progress.hasParticipant(playerUUID);
    }

    /**
     * 判断数字编号是否属于故事定义。
     *
     * <p>任务客户端包仍服务于旧的通用玩家任务，但不能借数字编号把 Java
     * 主线任务伪造为已完成；主线只能由对应状态机或管理员命令写入。</p>
     */
    public static synchronized boolean isStoryTaskNumber(int taskId) {
        return loaded && taskId > 0 && TASKS_BY_NUMBER.containsKey(taskId);
    }

    public static synchronized boolean isTaskWaived(String taskKey, UUID playerId) {
        return loaded && state.getOperations().isWaived(playerId, taskKey);
    }

    private static int currentStageNumber() {
        StoryStageData stage = STAGES_BY_ID.get(state.getCurrentStageId());
        return stage == null ? 1 : stage.getStageNumber();
    }

    private static int playerStoryOrder(UUID playerId) {
        int order = state.getOperations().reachedOrder(playerId);
        OpeningStoryProgress opening = findOpeningProgress(playerId);
        if (opening != null) {
            order = Math.max(order, switch (opening.getStep()) {
                case NOT_STARTED -> 1000;
                case TRAVEL_TO_ABYDOS -> 1010;
                case TALK_TO_BAIZHI -> 1020;
                case CONTACT_ZHOUCEN -> 1030;
                case CHOOSE_MEMBERSHIP -> 1040;
                case BUILD_ZHUIGUANG_BASE, DECLINED_ZHUIGUANG -> 1050;
            });
        }
        AfterdreamPlayerProgress afterdream = findAfterdreamProgress(playerId);
        if (afterdream != null) {
            order = Math.max(order, switch (afterdream.getStep()) {
                case NOT_STARTED, MESSAGE_RECEIVED -> 2000;
                case MESSAGE_READ -> 2010;
                case RECEPTION_READY -> 2020;
                case INTRODUCTION -> 2021;
                default -> 2030;
            });
        }
        return order;
    }

    /** 每份已发布正文不可变；再次发布同一 ID 只重试补投。 */
    public static synchronized boolean publishRecap(String recapId, String actor) {
        ensureWritable();
        StoryOperationsCatalog.RecapDraft draft = StoryOperationsCatalog.read().recaps().stream()
                .filter(value -> value.id().equals(recapId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("前情草稿不存在：" + recapId));
        StoryCheckpoint checkpoint = StoryCheckpoint.require(draft.checkpointId());
        StoryStageData targetStage = STAGES_BY_ID.get(checkpoint.stageId());
        if (targetStage == null) {
            throw new IllegalArgumentException("接入点所在阶段尚未实现：" + checkpoint.stageId());
        }
        boolean changed = state.getOperations().publish(draft.id(), draft.title(), draft.content(),
                draft.checkpointId(), System.currentTimeMillis());
        if (changed) {
            dirty = true;
            recordHistory(WorldHistoryLog.EventType.RECAP_PUBLISHED, recapId, actor,
                    Map.of("checkpoint", draft.checkpointId()));
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !saveIfDirty(server)) {
            throw new IllegalStateException("前情发布尚未保存成功，保留待保存状态；请重试同一发布命令");
        }
        onStageActivatedForOnlinePlayers(state.getCurrentStageId());
        return changed;
    }

    public static synchronized String describeRecaps() {
        ensureLoaded();
        StringBuilder result = new StringBuilder("前情草稿（正文路径：" + StoryOperationsCatalog.path() + "）");
        for (StoryOperationsCatalog.RecapDraft draft : StoryOperationsCatalog.read().recaps()) {
            result.append("\n").append(draft.id()).append(" → ").append(draft.checkpointId())
                    .append(state.getOperations().recaps().containsKey(draft.id()) ? " [已发布，正文已冻结]" : " [未发布]");
        }
        result.append("\n可用接入点：");
        StoryCheckpoint.all().forEach(checkpoint -> result.append("\n").append(checkpoint.id()));
        return result.toString();
    }

    private static void deliverRecaps(ServerPlayer player) {
        List<StoryOperationsState.Recap> published = state.getOperations().recaps().values().stream()
                .sorted(Comparator.comparingInt(value -> StoryCheckpoint.require(value.checkpointId()).order()))
                .toList();
        for (StoryOperationsState.Recap recap : published) {
            StoryCheckpoint checkpoint = StoryCheckpoint.require(recap.checkpointId());
            StoryStageData targetStage = STAGES_BY_ID.get(checkpoint.stageId());
            if (targetStage == null || targetStage.getStageNumber() > currentStageNumber()) {
                continue;
            }
            Set<String> completed = checkpoint.skippedTasks().stream()
                    .filter(task -> state.hasPersonalTaskCompletion(task, player.getUUID()))
                    .collect(java.util.stream.Collectors.toSet());
            if (state.getOperations().deliver(recap.id(), player.getUUID(), playerStoryOrder(player.getUUID()), checkpoint, completed)) {
                dirty = true;
                try {
                    NotificationPushHelper.sendTopLeftNotification(player,
                            "§6前情总结：" + recap.title() + "\n§7打开终端，在剧情广播中查看。", 6000);
                } catch (RuntimeException exception) {
                    DreamingFishCore.LOGGER.warn("前情已发放至档案，弹出提示失败：{}", recap.id(), exception);
                }
            }
        }
    }

    /** 即使上次在发放后、创建引导前中断，也从同一份接入记录重建。 */
    private static void restoreRecapEntry(ServerPlayer player) {
        for (StoryOperationsState.Recap recap : state.getOperations().recaps().values()) {
            if (!state.getOperations().hasReceived(player.getUUID(), recap.id())) {
                continue;
            }
            StoryCheckpoint checkpoint = StoryCheckpoint.require(recap.checkpointId());
            if (!checkpoint.stageId().equals(state.getCurrentStageId())) {
                continue;
            }
            if (StoryCheckpoint.OPENING_MEMBERSHIP.equals(checkpoint.id())) {
                dirty |= getOrCreateOpeningProgress(player.getUUID()).enterMembershipFromRecap(System.currentTimeMillis());
            } else if (StoryCheckpoint.AFTERDREAM_RECEPTION.equals(checkpoint.id())) {
                dirty |= getOrCreateAfterdreamProgress(player.getUUID()).enterReceptionFromRecap();
            }
            for (String task : checkpoint.skippedTasks()) {
                StoryTaskData definition = TASKS_BY_KEY.get(task);
                if (definition != null && isTaskWaived(task, player.getUUID())) {
                    GuidanceManager.archiveDefinitions(player.getUUID(), definition.getGuidanceDefinitionIds());
                }
            }
        }
    }

    public static synchronized List<NoticeData> getRecapNotices(UUID playerId) {
        if (!loaded) {
            return List.of();
        }
        return state.getOperations().recaps().values().stream()
                .filter(recap -> state.getOperations().hasReceived(playerId, recap.id()))
                .map(recap -> new NoticeData(recap.noticeId(), "前情总结 · " + recap.title(), recap.content(),
                        recap.publishedAt(), NoticeCategory.GAME, "", "前情回顾", recap.id()))
                .toList();
    }

    public static synchronized Set<Integer> getReadRecapNoticeIds(UUID playerId) {
        if (!loaded) {
            return Set.of();
        }
        return state.getOperations().recaps().values().stream()
                .filter(recap -> state.getOperations().hasRead(playerId, recap.id()))
                .map(StoryOperationsState.Recap::noticeId).collect(java.util.stream.Collectors.toSet());
    }

    public static synchronized boolean markRecapRead(ServerPlayer player, int noticeId) {
        if (!loaded || !writesEnabled || player == null || !AuthSessionGuard.isAuthenticated(player)) {
            return false;
        }
        for (StoryOperationsState.Recap recap : state.getOperations().recaps().values()) {
            if (recap.noticeId() == noticeId && state.getOperations().hasReceived(player.getUUID(), recap.id())) {
                dirty |= state.getOperations().markRead(player.getUUID(), recap.id());
                return true;
            }
        }
        return false;
    }

    private static void requireCurrentWorldTask(String taskKey) {
        if (isPersonalStoryTask(taskKey) || !state.getCurrentStageId().equals(TASK_STAGE_IDS.get(taskKey))
                || state.getOperations().isArchived(taskKey)) {
            throw new IllegalArgumentException("只能发布或结算当前阶段未归档的世界任务：" + taskKey);
        }
    }

    /** 人数门槛仅开放世界任务，永远不调用 changeStage。 */
    private static void evaluateWorldTaskGates() {
        if (!loaded || !writesEnabled) {
            return;
        }
        boolean changed = false;
        Map<String, Map<String, String>> completions = state.getPersonalTaskProgressView();
        for (StoryOperationsCatalog.WorldTask task : operationsDocument.worldTasks()) {
            if (task.stageId().equals(state.getCurrentStageId())
                    && !state.getOperations().isArchived(task.id())
                    && task.gate().isSatisfied(completions) && state.activateTask(task.id())) {
                dirty = true;
                changed = true;
                recordHistory(WorldHistoryLog.EventType.TASK_PUBLISHED, task.id(), "system",
                        Map.of("reason", "personalCompletionGate", "completedPlayers",
                                Integer.toString(task.gate().completedPlayers(completions))));
            }
        }
        if (changed) {
            refreshOnlineStoryViews();
        }
    }

    public static synchronized String describeWorldTaskGates() {
        ensureLoaded();
        StringBuilder result = new StringBuilder("世界任务门槛（章节仍由服主手动发布）");
        for (StoryOperationsCatalog.WorldTask task : operationsDocument.worldTasks()) {
            StoryWorldState.TaskProgress progress = state.getTaskProgress(task.id());
            result.append("\n").append(task.id()).append("：")
                    .append(task.gate().completedPlayers(state.getPersonalTaskProgressView()))
                    .append('/').append(task.requiredPlayers()).append(" 人，")
                    .append(isHistoricalTask(task.id()) ? "已归档" : progress == null ? "未解锁" : progress.getOutcome());
        }
        return result.toString();
    }

    private static void syncWorldTaskGuidance(ServerPlayer player) {
        if (!loaded || !writesEnabled || !AuthSessionGuard.isAuthenticated(player)) {
            return;
        }
        for (StoryOperationsCatalog.WorldTask task : operationsDocument.worldTasks()) {
            if (!task.stageId().equals(state.getCurrentStageId())) {
                continue;
            }
            StoryWorldState.TaskProgress progress = state.getTaskProgress(task.id());
            if (progress == null || state.getOperations().isArchived(task.id())) {
                continue;
            }
            if (progress.getOutcome().isResolved()) {
                GuidanceManager.resolve(player.getUUID(), task.id());
                continue;
            }
            GuidanceSeed seed = new GuidanceSeed(task.id(), task.name(), task.content())
                    .withStoryStage(task.stageId()).withStoryLine(task.id());
            if (task.locationId() != null && !task.locationId().isBlank()) {
                TaskLocationManager.getLocation(task.locationId()).ifPresent(location -> seed.withLocation(
                        location.getName(), location.getDimension(), location.getMin().getX(),
                        location.getMin().getY(), location.getMin().getZ()));
            }
            GuidanceManager.ensureActiveFromStoryEvent(player.getUUID(), seed, task.id(), "公共行动", task.content());
        }
        GuidanceManager.syncToClient(player);
    }

    private static void refreshOnlineStoryViews() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (AuthSessionGuard.isAuthenticated(player)) {
                try {
                    syncWorldTaskGuidance(player);
                    syncStoryProjection(player);
                } catch (RuntimeException exception) {
                    DreamingFishCore.LOGGER.warn("剧情已保存于内存，玩家投影等待下次同步：{}", player.getScoreboardName(), exception);
                }
            }
        }
    }

    /** 返回某个故事任务是否有独立的个人部分。 */
    public static boolean isPersonalStoryTask(String taskKey) {
        StoryTaskData task = taskKey == null ? null : TASKS_BY_KEY.get(taskKey);
        return task != null && task.getScope() == StoryTaskData.Scope.PERSONAL;
    }

    /**
     * 当前开服版本不计算个人任务的全服分母。个人任务只对当前玩家显示自己的
     * 完成事实；全服阶段切换由服主显式完成。
     */
    private static Set<UUID> getExpectedPersonalPlayers(String taskKey) {
        return Set.of();
    }

    /** 返回按数字编号索引的所有阶段视图，不附带某个玩家的个人完成状态。 */
    public static Map<Integer, StoryStageData> getAllStages() {
        return getStagesForPlayer(null);
    }

    /**
     * 为指定玩家生成已经开放阶段的客户端视图；管理/定义校验传入
     * {@code null} 时才返回全部阶段。
     *
     * <p>普通世界任务只有在发布后进入视图；带个人部分的任务只有在这名玩家
     * 实际收到对应的剧情引导后才进入故事页，并合并当前玩家的个人完成状态。
     * 这样不会因为配置里预先写了整条任务链，就把尚未经历的剧情提前剧透给玩家。
     * 管理查询传入 {@code null} 时仍返回所有任务。</p>
     */
    public static Map<Integer, StoryStageData> getStagesForPlayer(UUID playerId) {
        ensureLoaded();
        Map<Integer, StoryStageData> result = new LinkedHashMap<>();
        StoryStageData current = STAGES_BY_ID.get(state.getCurrentStageId());
        int currentStageNumber = current == null ? Integer.MAX_VALUE : current.getStageNumber();
        STAGES_BY_NUMBER.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> playerId == null
                        || entry.getValue() == null
                        || entry.getValue().getStageNumber() <= currentStageNumber)
                .forEach(entry -> result.put(entry.getKey(), createStageView(entry.getValue(), playerId)));
        return Collections.unmodifiableMap(result);
    }

    /**
     * 返回当前阶段及此前已经开放阶段的稳定 ID。
     * 公告终端只需要这组轻量索引，不必为每个阶段重新生成玩家任务视图。
     */
    public static synchronized Set<String> getVisibleStageIds() {
        ensureLoaded();
        StoryStageData current = STAGES_BY_ID.get(state.getCurrentStageId());
        int maximumStageNumber = current == null ? 1 : current.getStageNumber();
        Set<String> visible = new LinkedHashSet<>();
        STAGES_BY_NUMBER.values().stream()
                .filter(stage -> stage != null && stage.getStageNumber() > 0
                        && stage.getStageNumber() <= maximumStageNumber)
                .sorted(Comparator.comparingInt(StoryStageData::getStageNumber))
                .forEach(stage -> visible.add(stage.getStageId()));
        return Collections.unmodifiableSet(visible);
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
        // 已经结束的阶段是历史档案，但“进入下一阶段”不等于其中每个
        // 任务都成功。视图必须保留真实的成功/失败/未结算结果，避免把
        // 维护错误伪装成玩家已经完成。
        StoryStageData currentDefinition = STAGES_BY_ID.get(state.getCurrentStageId());
        int currentStageNumber = currentDefinition == null
                ? definition.getStageNumber()
                : currentDefinition.getStageNumber();
        boolean historicalStage = definition.getStageNumber() < currentStageNumber;
        List<StoryTaskData> taskViews = new ArrayList<>();
        for (StoryTaskData task : definition.getTasks()) {
            if (playerId != null
                    && !historicalStage
                    && OpeningStory.isMemberOnlyTask(task.getTaskKey())
                    && !ZhuiguangMembershipManager.isMember(playerId)) {
                continue;
            }
            boolean personalTask = isPersonalStoryTask(task.getTaskKey());
            // 个人任务的定义会随故事阶段一起保存，但分配是逐个玩家发生的。
            // 没有收到对应引导的玩家不能看到这张任务卡；否则新玩家第一次打开
            // 故事页就会同时看到整条开场链。已写入个人完成记录时保留任务，
            // 即使引导投影暂时缺失，也保留玩家已经发生的事实。
            if (playerId != null
                    && !historicalStage
                    && personalTask
                    && !isPersonalTaskVisibleToPlayer(task.getTaskKey(), playerId)) {
                continue;
            }
            StoryWorldState.TaskProgress progress = state.getTaskProgress(task.getTaskKey());
            if (progress == null && !personalTask && !historicalStage) {
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
            // 历史归档表示不再要求执行，不能替玩家补写个人完成或世界成功。
            AfterdreamStory.updateTaskLocationForView(view);
            view.setArchived(historicalStage || state.getOperations().isArchived(task.getTaskKey()));
            view.setWaived(isTaskWaived(task.getTaskKey(), playerId));
            taskViews.add(view);
        }
        StoryStageData view = definition.copyWithTasks(taskViews);
        view.setCurrentStage(definition.getStageId().equals(state.getCurrentStageId()));
        // 手动推进模式下活动阶段不根据比例自动切换；历史阶段仍可显示
        // 已记录的真实比例，但不会因为“阶段已过去”被伪造为 100%。
        view.setGlobalProgressPercentage(calculateRecordedStageProgress(definition));
        return view;
    }

    /**
     * 只读取已经落盘/进入内存的任务结果，不参与任何自动阶段推进。
     * 这样手动运营模式仍能在历史页显示真实成功、失败和未结算状态。
     */
    private static float calculateRecordedStageProgress(StoryStageData definition) {
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
            } else {
                StoryWorldState.TaskProgress progress = state.getTaskProgress(task.getTaskKey());
                if (progress != null && progress.getOutcome().isResolved()) {
                    progressSum += 1.0f;
                }
            }
            trackedTaskCount++;
        }
        return trackedTaskCount == 0 ? 0.0f : progressSum / trackedTaskCount;
    }

    /** 计算当前阶段已记录的全服进度。 */
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
        if (HospitalStory.isTask(taskKey)) return HospitalStory.isVisible(taskKey, playerId);
        String stageId = TASK_STAGE_IDS.get(taskKey);
        if (OpeningStory.STAGE_ID.equals(stageId)) {
            return OpeningStory.isTaskVisibleToPlayer(taskKey, playerId);
        }
        if (AfterdreamStory.STAGE_ID.equals(stageId)) {
            return AfterdreamStory.isTaskVisibleToPlayer(taskKey, playerId);
        }
        return state.hasPersonalTaskCompletion(taskKey, playerId);
    }

    /** 当前阶段推进后，所有更早阶段的任务都属于已收束的历史事实。 */
    private static boolean isHistoricalTask(String taskKey) {
        String stageId = TASK_STAGE_IDS.get(taskKey);
        StoryStageData taskStage = stageId == null ? null : STAGES_BY_ID.get(stageId);
        StoryStageData currentStage = STAGES_BY_ID.get(state.getCurrentStageId());
        return taskStage != null && currentStage != null
                && taskStage.getStageNumber() < currentStage.getStageNumber();
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
        AfterdreamStory.updateTaskLocationForView(view);
        view.setArchived(isHistoricalTask(definition.getTaskKey()) || state.getOperations().isArchived(definition.getTaskKey()));
        view.setWaived(isTaskWaived(definition.getTaskKey(), playerId));
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

    /** 返回管理视图可见的任务：已发布的世界任务，加上个人任务。 */
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
        result.append("阶段推进方式：服主手动；固定人数门槛请查看 worldtask list");
        return result.toString();
    }

    /**
     * 管理命令和状态页使用的进度快照。
     *
     * <p>当前主线由服主手动推进，因此手动模式直接返回空的全服进度快照；个人任务的
     * 完成事实仍通过 {@link #isPlayerFinishedTask(String, UUID)} 查询。</p>
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
     * 当前 Java 阶段定义的内存容器。
     * 它不是用户配置格式，也不会从磁盘反序列化。
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
