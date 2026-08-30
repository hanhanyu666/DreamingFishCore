package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceManager;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceSeed;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageManager;
import com.hhy.dreamingfishcore.gameplay.story_system.OpeningStoryDefinitionCatalog;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.gameplay.task_system.TaskDataManager;
import com.hhy.dreamingfishcore.gameplay.zhuiguang_system.ZhuiguangMembershipManager;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 故事运行时的唯一事实入口、流程状态机和效果注册表。
 *
 * <p>所有外部模块只提交已经在服务端验证过的 {@link StoryEvent}。流程节点再按当前
 * 全服阶段、玩家流程游标和条件筛选，依次执行内容包声明的效果。阿拜多斯开场也使用
 * 这条通用链路，不再存在一份平行的开场推进器。</p>
 */
public final class StoryFlowEngine {
    private static final Map<String, StoryFlowDefinition> FLOWS = new LinkedHashMap<>();
    private static final Map<String, StoryEffectExecutor> EFFECT_EXECUTORS =
            new ConcurrentHashMap<>();
    private static final Map<String, Map<String, StoryFlowProgress>> PROGRESS =
            new LinkedHashMap<>();
    private static final Map<UUID, String> LAST_LOCATIONS = new ConcurrentHashMap<>();
    private static final StoryEventListener INTERNAL_LISTENER = StoryFlowEngine::dispatch;
    private static final Gson PROGRESS_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();
    private static final Type PROGRESS_TYPE = new TypeToken<
            Map<String, Map<String, StoryFlowProgress>>>() { }.getType();

    private static boolean loaded;
    private static boolean definitionsWritable;
    private static boolean progressWritable;
    private static boolean dirty;
    private static long generation;
    static {
        registerBuiltInEffects();
    }

    private StoryFlowEngine() {
    }

    /** 在 StoryManager 定义加载后调用，同时加载每名玩家的流程游标。 */
    public static synchronized void loadWorldData(MinecraftServer minecraftServer) {
        clearWorldCache();

        StoryFlowDefinitionDocument document;
        try {
            document = StoryFlowDefinitionStore.readCandidate();
            StoryFlowDefinitionStore.validate(
                    document,
                    StoryManager.getAllStagesById().keySet());
            definitionsWritable = true;
        } catch (RuntimeException exception) {
            document = StoryFlowDefinitionStore.loadBundledDefault();
            StoryFlowDefinitionStore.validate(
                    document,
                    StoryManager.getAllStagesById().keySet());
            definitionsWritable = false;
            DreamingFishCore.LOGGER.error(
                    "故事流程配置加载失败，已使用只读内置流程：{}",
                    StoryFlowDefinitionStore.getConfigPath(), exception);
        }
        install(document);
        loadProgress(minecraftServer);
        loaded = true;
        StoryEventGateway.register(INTERNAL_LISTENER);
    }

    public static synchronized boolean isLoaded() {
        return loaded;
    }

    public static synchronized long getGeneration() {
        return generation;
    }

    public static synchronized Collection<StoryFlowDefinition> getDefinitions() {
        return List.copyOf(FLOWS.values());
    }

    /** 注册一个可被内容包节点引用的服务端效果；同一 ID 的重复注册会被拒绝。 */
    public static void registerEffectExecutor(String effectId, StoryEffectExecutor executor) {
        if (effectId == null || effectId.isBlank() || executor == null) {
            throw new IllegalArgumentException("故事效果 ID 和执行器不能为空");
        }
        String normalized = normalizeEffectId(effectId);
        if (!StoryFlowNode.isValidEffectId(normalized)) {
            throw new IllegalArgumentException("故事效果 ID 非法：" + effectId);
        }
        if (EFFECT_EXECUTORS.putIfAbsent(normalized, executor) != null) {
            throw new IllegalStateException("故事效果已经注册：" + normalized);
        }
    }

    public static void unregisterEffectExecutor(String effectId) {
        if (effectId != null && !effectId.isBlank()) {
            EFFECT_EXECUTORS.remove(normalizeEffectId(effectId));
        }
    }

    public static synchronized StoryFlowDefinitionStore.Summary validateDefinitions() {
        ensureLoaded();
        StoryFlowDefinitionDocument candidate = StoryFlowDefinitionStore.readCandidate();
        StoryFlowDefinitionStore.validate(candidate, StoryManager.getAllStagesById().keySet());
        validateProgressAgainst(candidate);
        return StoryFlowDefinitionStore.summary(candidate);
    }

    /** 先校验候选流程和现有玩家游标，再替换索引。 */
    public static synchronized StoryFlowDefinitionStore.Summary reloadDefinitions() {
        ensureLoaded();
        if (!definitionsWritable) {
            throw new IllegalStateException("故事流程当前处于只读保护，拒绝热重载");
        }
        StoryFlowDefinitionDocument candidate = StoryFlowDefinitionStore.readCandidate();
        StoryFlowDefinitionStore.validate(candidate, StoryManager.getAllStagesById().keySet());
        validateProgressAgainst(candidate);
        install(candidate);
        generation = Math.max(1L, generation + 1L);
        return StoryFlowDefinitionStore.summary(candidate);
    }

    public static void onPlayerAuthenticated(ServerPlayer player) {
        if (player != null) {
            LAST_LOCATIONS.remove(player.getUUID());
            emit(StoryEvent.authenticated(player));
        }
    }

    /** 玩家断开后只清理瞬时地点游标；个人流程进度仍保存在世界数据中。 */
    public static void onPlayerDisconnected(ServerPlayer player) {
        if (player != null) {
            LAST_LOCATIONS.remove(player.getUUID());
        }
    }

    public static void onNoticeRead(ServerPlayer player, String noticeKey, String noticeTitle) {
        emit(StoryEvent.noticeRead(player, noticeKey, noticeTitle));
    }

    /** 每秒检查一次地点；地点事件只在真正跨入稳定 ID 时发出。 */
    public static void onPlayerLocationTick(
            ServerPlayer player, TaskLocationDefinition currentLocation) {
        if (player == null) {
            return;
        }
        applyContinuousLocationEffects(player, currentLocation);
        if (currentLocation == null || currentLocation.getId() == null
                || currentLocation.getId().isBlank()) {
            LAST_LOCATIONS.remove(player.getUUID());
            return;
        }
        String locationId = currentLocation.getId().trim();
        String previous = LAST_LOCATIONS.put(player.getUUID(), locationId);
        if (!Objects.equals(previous, locationId)) {
            emit(StoryEvent.locationEntered(player, locationId, currentLocation.getName()));
        }
    }

    public static void onNpcInteraction(ServerPlayer player, int npcId) {
        emit(StoryEvent.npcInteraction(player, npcId));
    }

    public static void onNpcReply(ServerPlayer player, String messageDefinitionId, String replyId) {
        emit(StoryEvent.npcReply(player, messageDefinitionId, replyId));
    }

    /** 根据当前玩家游标返回内容包声明的 NPC 台词。 */
    public static synchronized Optional<List<String>> getDialogueOverride(
            ServerPlayer player, int npcId) {
        if (!loaded || player == null || npcId <= 0) {
            return Optional.empty();
        }
        String currentStage = StoryManager.getCurrentStageIdOrDefault();
        for (StoryFlowDefinition flow : FLOWS.values()) {
            if (!flow.isEnabled() || flow.getScope() != StoryFlowScope.PLAYER
                    || !flow.getStageId().equals(currentStage)) {
                continue;
            }
            StoryFlowProgress progress = existingProgress(player.getUUID().toString(), flow.getId());
            StoryFlowProgress view = progress == null
                    ? new StoryFlowProgress(flow.getInitialNodeId()) : progress;
            for (StoryFlowNode node : flow.getNodes()) {
                if (node.hasDialogueFor(npcId)
                        && matchesState(flow, node, view, player)) {
                    return Optional.of(List.copyOf(node.getDialogueLines()));
                }
            }
        }
        return Optional.empty();
    }

    /** 返回某名玩家在指定流程中的当前节点，供管理界面和测试使用。 */
    public static synchronized String getPlayerCursor(UUID playerId, String flowId) {
        if (playerId == null || flowId == null) {
            return "";
        }
        StoryFlowDefinition flow = FLOWS.get(flowId);
        if (flow == null) {
            return "";
        }
        StoryFlowProgress progress = existingProgress(playerId.toString(), flowId);
        return progress == null ? flow.getInitialNodeId() : progress.getCursor();
    }

    public static synchronized boolean areWritesEnabled() {
        return loaded && definitionsWritable && progressWritable;
    }

    public static synchronized boolean saveIfDirty(MinecraftServer minecraftServer) {
        if (!loaded || !dirty || !definitionsWritable || !progressWritable) {
            return true;
        }
        if (minecraftServer == null) {
            return false;
        }
        try {
            JsonDataStore.writeAtomic(
                    progressPath(minecraftServer), PROGRESS_GSON, PROGRESS);
            dirty = false;
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error(
                    "写入故事流程玩家进度失败，保留 dirty 状态等待下次保存", exception);
            return false;
        }
    }

    public static synchronized void clearWorldCache() {
        StoryEventGateway.unregister(INTERNAL_LISTENER);
        FLOWS.clear();
        PROGRESS.clear();
        LAST_LOCATIONS.clear();
        loaded = false;
        definitionsWritable = false;
        progressWritable = false;
        dirty = false;
        generation = 0L;
    }

    private static synchronized void dispatch(StoryEvent event) {
        // 配置或进度文件损坏时允许只读查看，但绝不让事实事件修改内存状态；
        // 这样停服保存不会用默认流程覆盖原始文件，也不会出现半迁移进度。
        if (!loaded || !definitionsWritable || !progressWritable
                || !StoryManager.areWritesEnabled() || event == null
                || (event.playerId() != null
                && (event.player() == null || !AuthSessionGuard.isAuthenticated(event.player())))) {
            return;
        }
        String currentStage = StoryManager.getCurrentStageIdOrDefault();
        for (StoryFlowDefinition flow : FLOWS.values()) {
            if (!flow.isEnabled() || !flow.getStageId().equals(currentStage)) {
                continue;
            }
            if (flow.getScope() == StoryFlowScope.PLAYER && event.playerId() == null) {
                continue;
            }

            List<StoryFlowNode> candidates = flow.matchingNodes(event);
            if (candidates.isEmpty()) {
                continue;
            }
            String ownerKey = ownerKey(flow, event);
            StoryFlowProgress existing = existingProgress(ownerKey, flow.getId());
            StoryFlowProgress progress = existing == null
                    ? new StoryFlowProgress(flow.getInitialNodeId()) : existing;
            for (StoryFlowNode node : candidates) {
                if (!matchesState(flow, node, progress, event.player())
                        || (!node.isRepeatable() && progress.hasCompletedNode(node.getId()))) {
                    continue;
                }
                if (existing == null) {
                    putProgress(ownerKey, flow.getId(), progress);
                    existing = progress;
                }
                try {
                    executeNode(flow, node, progress, event);
                } catch (RuntimeException exception) {
                    DreamingFishCore.LOGGER.error(
                            "故事流程节点执行失败：{}/{}", flow.getId(), node.getId(), exception);
                }
                // 一个事实在一条流程中只消费一个节点；后续节点等待下一条事实。
                break;
            }
        }
    }

    private static void executeNode(
            StoryFlowDefinition flow,
            StoryFlowNode node,
            StoryFlowProgress progress,
            StoryEvent event) {
        for (int index = 0; index < node.getEffects().size(); index++) {
            StoryFlowEffect effect = node.getEffects().get(index);
            String effectId = normalizeEffectId(effect.getType());
            String effectKey = node.getId() + "/" + effect.getId();
            if (effect.isOnce() && progress.hasAppliedEffect(effectKey)) {
                continue;
            }
            StoryEffectExecutor executor = EFFECT_EXECUTORS.get(effectId);
            if (executor == null) {
                throw new IllegalStateException("故事效果尚未注册：" + effectId);
            }
            executor.execute(new StoryEffectContext(
                    event, flow, node, effect, index, effectId));
            if (effect.isOnce()) {
                progress.markEffectApplied(effectKey, System.currentTimeMillis());
                // 即使后续效果失败，已经成功的效果也必须先落下幂等令牌，
                // 否则重启后会重复发消息或发奖励。
                dirty = true;
            }
        }
        if (!node.getNextNodeId().isBlank()) {
            progress.setCursor(node.getNextNodeId(), System.currentTimeMillis());
            dirty = true;
        }
        if (!node.isRepeatable()) {
            progress.markNodeCompleted(node.getId(), System.currentTimeMillis());
            dirty = true;
        }
    }

    private static boolean matchesState(
            StoryFlowDefinition flow,
            StoryFlowNode node,
            StoryFlowProgress progress,
            ServerPlayer player) {
        for (Map.Entry<String, String> condition : node.getConditions().entrySet()) {
            String key = condition.getKey();
            String expected = condition.getValue() == null ? "" : condition.getValue().trim();
            if (expected.isBlank() || "*".equals(expected)) {
                continue;
            }
            boolean matches = switch (key) {
                case "cursor" -> expected.equals(progress.getCursor());
                case "membership" -> matchesMembership(expected, player);
                case "playerFlag" -> progress.hasFlag(expected);
                case "worldFlag" -> StoryManager.hasWorldFlag(expected);
                default -> {
                    DreamingFishCore.LOGGER.warn(
                            "故事流程节点使用了未实现的条件：{}/{}/{}",
                            flow.getId(), node.getId(), key);
                    yield false;
                }
            };
            if (!matches) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesMembership(String expected, ServerPlayer player) {
        if (player == null) {
            return false;
        }
        boolean member = ZhuiguangMembershipManager.isMember(player);
        return switch (expected.toUpperCase(java.util.Locale.ROOT)) {
            case "ANY" -> true;
            case "MEMBER" -> member;
            case "NON_MEMBER" -> !member;
            default -> false;
        };
    }

    private static String ownerKey(StoryFlowDefinition flow, StoryEvent event) {
        return switch (flow.getScope()) {
            case PLAYER -> event.playerId().toString();
            case WORLD -> "@world";
            case COOP -> "@coop";
        };
    }

    private static StoryFlowProgress existingProgress(String ownerKey, String flowId) {
        Map<String, StoryFlowProgress> byFlow = PROGRESS.get(ownerKey);
        return byFlow == null ? null : byFlow.get(flowId);
    }

    private static void putProgress(
            String ownerKey, String flowId, StoryFlowProgress progress) {
        PROGRESS.computeIfAbsent(ownerKey, ignored -> new LinkedHashMap<>())
                .put(flowId, progress);
    }

    private static void loadProgress(MinecraftServer minecraftServer) {
        PROGRESS.clear();
        progressWritable = false;
        dirty = false;
        if (minecraftServer == null) {
            progressWritable = true;
            return;
        }
        Path path = progressPath(minecraftServer);
        boolean fileExisted = Files.exists(path);
        try {
            Map<String, Map<String, StoryFlowProgress>> stored = JsonDataStore.read(
                    path, PROGRESS_GSON, PROGRESS_TYPE, LinkedHashMap::new);
            boolean repaired = false;
            for (Map.Entry<String, Map<String, StoryFlowProgress>> owner : stored.entrySet()) {
                if (!isValidOwnerKey(owner.getKey()) || owner.getValue() == null) {
                    repaired = true;
                    continue;
                }
                Map<String, StoryFlowProgress> validFlows = new LinkedHashMap<>();
                for (Map.Entry<String, StoryFlowProgress> flowEntry : owner.getValue().entrySet()) {
                    StoryFlowDefinition flow = FLOWS.get(flowEntry.getKey());
                    StoryFlowProgress value = flowEntry.getValue();
                    if (flow == null || value == null || !isScopeOwner(flow, owner.getKey())) {
                        repaired = true;
                        continue;
                    }
                    repaired |= value.repair();
                    value.validate(owner.getKey(), flowEntry.getKey(), flow);
                    validFlows.put(flowEntry.getKey(), value);
                }
                if (!validFlows.isEmpty()) {
                    PROGRESS.put(owner.getKey(), validFlows);
                }
            }
            progressWritable = true;
            dirty = !fileExisted || repaired;
            DreamingFishCore.LOGGER.info(
                    "故事流程玩家进度加载完成，共 {} 个所有者", PROGRESS.size());
        } catch (Exception exception) {
            progressWritable = false;
            dirty = false;
            DreamingFishCore.LOGGER.error(
                    "读取故事流程玩家进度失败，本次会话不会覆盖文件：{}", path, exception);
        }
    }

    private static void validateProgressAgainst(StoryFlowDefinitionDocument candidate) {
        Map<String, StoryFlowDefinition> candidateById = new LinkedHashMap<>();
        for (StoryFlowDefinition flow : candidate.getFlows()) {
            candidateById.put(flow.getId(), flow);
        }
        for (Map.Entry<String, Map<String, StoryFlowProgress>> owner : PROGRESS.entrySet()) {
            for (Map.Entry<String, StoryFlowProgress> flowEntry : owner.getValue().entrySet()) {
                StoryFlowDefinition flow = candidateById.get(flowEntry.getKey());
                if (flow == null || !isScopeOwner(flow, owner.getKey())) {
                    throw new IllegalStateException(
                            "候选流程删除了仍有玩家进度的流程：" + flowEntry.getKey());
                }
                flowEntry.getValue().validate(owner.getKey(), flowEntry.getKey(), flow);
            }
        }
    }

    private static boolean isScopeOwner(StoryFlowDefinition flow, String ownerKey) {
        return switch (flow.getScope()) {
            case WORLD -> "@world".equals(ownerKey);
            case COOP -> "@coop".equals(ownerKey);
            case PLAYER -> isUuid(ownerKey);
        };
    }

    private static boolean isValidOwnerKey(String ownerKey) {
        return "@world".equals(ownerKey) || "@coop".equals(ownerKey) || isUuid(ownerKey);
    }

    private static Path progressPath(MinecraftServer minecraftServer) {
        return WorldDataPaths.resolve(minecraftServer, "story", "flow_player_progress.json");
    }

    private static void install(StoryFlowDefinitionDocument document) {
        FLOWS.clear();
        List<StoryFlowDefinition> sorted = new ArrayList<>(document.getFlows());
        sorted.sort(Comparator.comparing(StoryFlowDefinition::getId));
        for (StoryFlowDefinition flow : sorted) {
            FLOWS.put(flow.getId(), flow);
        }
        generation = Math.max(1L, generation + 1L);
    }

    private static void emit(StoryEvent event) {
        if (loaded && event != null) {
            StoryEventGateway.emit(event);
        }
    }

    private static String normalizeEffectId(String effectId) {
        return effectId.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("故事流程引擎尚未随服务器世界加载");
        }
    }

    private static void requirePlayer(StoryEffectContext context) {
        if (context.event().player() == null) {
            throw new IllegalStateException("该故事效果需要玩家上下文");
        }
    }

    private static String requiredParameter(StoryFlowEffect effect, String key) {
        String value = effect.getParameter(key);
        if (value.isBlank()) {
            throw new IllegalStateException("故事效果缺少参数：" + key);
        }
        return value;
    }

    private static void registerBuiltInEffects() {
        EFFECT_EXECUTORS.put("SEND_NPC_MESSAGE", StoryFlowEngine::sendNpcMessage);
        EFFECT_EXECUTORS.put("CREATE_GUIDANCE", StoryFlowEngine::createGuidance);
        EFFECT_EXECUTORS.put("RESOLVE_GUIDANCE", StoryFlowEngine::resolveGuidance);
        EFFECT_EXECUTORS.put("RECORD_PERSONAL_TASK", StoryFlowEngine::recordPersonalTask);
        EFFECT_EXECUTORS.put("GIVE_ITEMS", StoryFlowEngine::giveItems);
        EFFECT_EXECUTORS.put("NOTIFY_PLAYER", StoryFlowEngine::notifyPlayer);
        EFFECT_EXECUTORS.put("SYNC_PLAYER", StoryFlowEngine::syncPlayer);
        EFFECT_EXECUTORS.put("SET_PLAYER_FLAG", StoryFlowEngine::setPlayerFlag);
        EFFECT_EXECUTORS.put("SET_WORLD_FLAG", StoryFlowEngine::setWorldFlag);
    }

    private static void sendNpcMessage(StoryEffectContext context) {
        requirePlayer(context);
        ServerPlayer player = context.event().player();
        String messageId = requiredParameter(context.effect(), "messageId");
        if (!NpcMessageManager.sendConfiguredMessage(player, messageId)
                && !NpcMessageManager.hasReceivedDefinition(player.getUUID(), messageId)) {
            throw new IllegalStateException("NPC 私信无法投递：" + messageId);
        }
    }

    private static void createGuidance(StoryEffectContext context) {
        requirePlayer(context);
        StoryFlowEffect effect = context.effect();
        String id = requiredParameter(effect, "id");
        String title = requiredParameter(effect, "title");
        String content = requiredParameter(effect, "content");
        String stageId = effect.getParameter("stageId");
        if (stageId.isBlank()) {
            stageId = context.flow().getStageId();
        }
        GuidanceSeed seed = new GuidanceSeed(id, title, content).withStoryStage(stageId);
        String locationId = effect.getParameter("locationId");
        if (!locationId.isBlank()) {
            Optional<TaskLocationDefinition> location = TaskLocationManager.getLocation(locationId);
            if (location.isPresent()) {
                TaskLocationDefinition value = location.get();
                seed.withLocation(
                        value.getName(),
                        value.getDimension(),
                        midpoint(value.getMin().getX(), value.getMax().getX()),
                        midpoint(value.getMin().getY(), value.getMax().getY()),
                        midpoint(value.getMin().getZ(), value.getMax().getZ()));
            } else if (!effect.getParameter("locationLabel").isBlank()) {
                seed.withLocation(
                        effect.getParameter("locationLabel"),
                        effect.getParameter("dimension").isBlank()
                                ? "minecraft:overworld" : effect.getParameter("dimension"),
                        parseInt(effect.getParameter("x"), 0),
                        parseInt(effect.getParameter("y"), 0),
                        parseInt(effect.getParameter("z"), 0));
            }
        }
        String sourceEventId = effect.getParameter("sourceEventId");
        if (sourceEventId.isBlank()) {
            sourceEventId = context.flow().getId() + "/" + context.node().getId();
        }
        String sourceName = effect.getParameter("sourceName");
        String sourceQuote = effect.getParameter("sourceQuote");
        boolean created = GuidanceManager.createFromStoryEvent(
                context.event().player().getUUID(), seed, sourceEventId, sourceNpcId(effect),
                sourceName, sourceQuote);
        if (!created && !GuidanceManager.hasDefinition(
                context.event().player().getUUID(), id)) {
            throw new IllegalStateException("个人引导无法创建：" + id);
        }
    }

    private static int sourceNpcId(StoryFlowEffect effect) {
        return parseInt(effect.getParameter("sourceNpcId"), 0);
    }

    private static void resolveGuidance(StoryEffectContext context) {
        requirePlayer(context);
        String id = requiredParameter(context.effect(), "id");
        UUID playerId = context.event().player().getUUID();
        if (!GuidanceManager.resolve(playerId, id)
                && !GuidanceManager.hasDefinition(playerId, id)) {
            throw new IllegalStateException("个人引导不存在，无法完成：" + id);
        }
    }

    private static void recordPersonalTask(StoryEffectContext context) {
        requirePlayer(context);
        String taskId = requiredParameter(context.effect(), "taskId");
        ServerPlayer player = context.event().player();
        if (!StoryManager.recordPlayerTaskProgress(
                taskId, player.getScoreboardName(), player.getUUID())
                && !StoryManager.isPlayerFinishedTask(taskId, player.getUUID())) {
            throw new IllegalStateException("个人故事任务无法记录：" + taskId);
        }
    }

    private static void giveItems(StoryEffectContext context) {
        requirePlayer(context);
        if (context.effect().getItemGrants().isEmpty()) {
            throw new IllegalStateException("GIVE_ITEMS 效果没有物品");
        }
        ServerPlayer player = context.event().player();
        for (StoryFlowItemGrant grant : context.effect().getItemGrants()) {
            ResourceLocation itemId = ResourceLocation.tryParse(grant.getItemId());
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                throw new IllegalStateException("奖励物品不存在：" + grant.getItemId());
            }
            Item item = BuiltInRegistries.ITEM.get(itemId);
            int remaining = grant.getCount();
            while (remaining > 0) {
                int amount = Math.min(remaining, item.getDefaultMaxStackSize());
                ItemStack stack = new ItemStack(item, amount);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                remaining -= amount;
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static void notifyPlayer(StoryEffectContext context) {
        requirePlayer(context);
        String text = requiredParameter(context.effect(), "text");
        int duration = parseInt(context.effect().getParameter("durationMillis"), 6500);
        duration = Math.max(500, Math.min(60_000, duration));
        NotificationPushHelper.sendTopLeftNotification(context.event().player(), text, duration);
    }

    private static void syncPlayer(StoryEffectContext context) {
        requirePlayer(context);
        ServerPlayer player = context.event().player();
        NpcMessageManager.syncToClient(player);
        GuidanceManager.syncToClient(player);
        TaskDataManager.syncFullTaskData(player);
    }

    private static void setPlayerFlag(StoryEffectContext context) {
        requirePlayer(context);
        String flagId = requiredParameter(context.effect(), "id");
        boolean enabled = Boolean.parseBoolean(
                context.effect().getParameter("enabled").isBlank()
                        ? "true" : context.effect().getParameter("enabled"));
        StoryFlowProgress progress = existingProgress(
                context.event().player().getUUID().toString(), context.flow().getId());
        if (progress == null) {
            throw new IllegalStateException("玩家流程状态尚未建立：" + context.flow().getId());
        }
        progress.setFlag(flagId, enabled, System.currentTimeMillis());
        dirty = true;
    }

    private static void setWorldFlag(StoryEffectContext context) {
        String flagId = requiredParameter(context.effect(), "id");
        boolean enabled = Boolean.parseBoolean(
                context.effect().getParameter("enabled").isBlank()
                        ? "true" : context.effect().getParameter("enabled"));
        StoryManager.setWorldFlag(flagId, enabled);
    }

    private static void applyContinuousLocationEffects(
            ServerPlayer player, TaskLocationDefinition location) {
        if (location == null
                || !OpeningStoryDefinitionCatalog.ZHUIGUANG_LOCATION_ID.equals(location.getId())
                || !ZhuiguangMembershipManager.isMember(player)) {
            return;
        }
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.REGENERATION,
                60,
                0,
                true,
                false,
                true));
    }

    private static int midpoint(int first, int second) {
        return (int) (((long) first + second) / 2L);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
