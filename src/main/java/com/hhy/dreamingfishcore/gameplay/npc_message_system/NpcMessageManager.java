package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceManager;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceSeed;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.network.Packet_NpcMessageSnapshotResponse;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcData;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcManager;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcRelationData;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcRelationManager;
import com.hhy.dreamingfishcore.gameplay.npc_system.StoryNpcContentPolicy;
import com.hhy.dreamingfishcore.gameplay.zhuiguang_system.ZhuiguangMembershipManager;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * NPC 私信的配置、投递、回复与持久化入口。
 *
 * <p>客户端只提交消息记录 ID 与预设回复 ID；服务端重新校验消息归属、
 * 是否已经回复以及当前好感度，避免客户端伪造内容或关系变化。</p>
 */
public final class NpcMessageManager {
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve(DreamingFishCore.MODID)
            .resolve("npc_messages.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Type PLAYER_DATA_TYPE = new TypeToken<Map<String, PlayerNpcMessageData>>() { }.getType();
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Pattern REPLY_ID = Pattern.compile("[a-z0-9_.-]+");
    private static final int MAX_STORED_MESSAGES_PER_PLAYER = 2048;
    /** 客户端最多展示、历史记录最多快照化的回复数量。 */
    static final int MAX_REPLY_OPTIONS = 8;

    private static final Map<String, NpcMessageDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<String, PlayerNpcMessageData> PLAYER_DATA = new ConcurrentHashMap<>();

    private static boolean configWritable;
    private static boolean loaded;
    private static boolean dirty;
    private static boolean worldWritesEnabled;

    private NpcMessageManager() {
    }

    public static void init() {
        reloadDefinitions();
    }

    public static synchronized int reloadDefinitions() {
        if (Files.notExists(CONFIG_PATH)) {
            NpcMessageConfig defaults = createDefaultConfig();
            Map<String, NpcMessageDefinition> validated = validateDefinitions(defaults);
            DEFINITIONS.clear();
            DEFINITIONS.putAll(validated);
            configWritable = true;
            saveDefinitionConfig(defaults);
            return DEFINITIONS.size();
        }

        try {
            if (Files.size(CONFIG_PATH) == 0L) {
                configWritable = false;
                DreamingFishCore.LOGGER.error("NPC 私信配置为空，保留当前内存配置并拒绝覆盖文件：{}", CONFIG_PATH);
                return DEFINITIONS.size();
            }
            NpcMessageConfig config = JsonDataStore.read(
                    CONFIG_PATH,
                    GSON,
                    NpcMessageConfig.class,
                    NpcMessageConfig::new);
            boolean changed = false;
            if (config.getSchemaVersion() == 1) {
                // 只补充当前 Java 阶段声明的消息定义；不把旧剧情节点转换成
                // 新状态，也不修改运营者已经写过的正文。
                List<NpcMessageDefinition> additions =
                        BuiltInNpcMessageCatalog.createMissingMessages(config.getMessages());
                if (!additions.isEmpty()) {
                    config.getMessages().addAll(additions);
                    changed = true;
                }
            }
            Map<String, NpcMessageDefinition> validated = validateDefinitions(config);
            DEFINITIONS.clear();
            DEFINITIONS.putAll(validated);
            configWritable = true;
            if (changed) {
                saveDefinitionConfig(config);
            }
            DreamingFishCore.LOGGER.info("NPC 私信配置加载完成，共 {} 条消息", DEFINITIONS.size());
        } catch (Exception exception) {
            configWritable = false;
            DreamingFishCore.LOGGER.error("NPC 私信配置及备份读取失败，保留当前内存配置：{}", CONFIG_PATH, exception);
        }
        return DEFINITIONS.size();
    }

    public static synchronized void loadWorldData(MinecraftServer server) {
        PLAYER_DATA.clear();
        dirty = false;
        worldWritesEnabled = false;
        try {
            Map<String, PlayerNpcMessageData> loadedData = JsonDataStore.read(
                    WorldDataPaths.resolve(server, "communication", "npc_messages.json"),
                    GSON,
                    PLAYER_DATA_TYPE,
                    ConcurrentHashMap::new);
            boolean repaired = false;
            for (Map.Entry<String, PlayerNpcMessageData> entry : loadedData.entrySet()) {
                String playerId = entry.getKey();
                PlayerNpcMessageData data = entry.getValue();
                if (!isUuid(playerId) || data == null) {
                    continue;
                }
                int messageCountBeforeFilter = data.getMessages().size();
                data.getMessages().removeIf(record -> !isValidRecord(record));
                repaired |= messageCountBeforeFilter != data.getMessages().size();
                if (data.getMessages().size() > MAX_STORED_MESSAGES_PER_PLAYER) {
                    int removeCount = data.getMessages().size() - MAX_STORED_MESSAGES_PER_PLAYER;
                    data.getMessages().subList(0, removeCount).clear();
                }
                PLAYER_DATA.put(playerId, data);
            }
            dirty = repaired;
            worldWritesEnabled = true;
            if (repaired) {
                DreamingFishCore.LOGGER.info("已清理格式非法的 NPC 私信记录");
            }
            DreamingFishCore.LOGGER.info("NPC 私信数据加载完成，共 {} 名玩家", PLAYER_DATA.size());
        } catch (Exception exception) {
            dirty = false;
            worldWritesEnabled = false;
            DreamingFishCore.LOGGER.error("读取 NPC 私信数据失败，本次会话不会覆盖损坏文件", exception);
        } finally {
            loaded = true;
        }
    }

    /** 玩家与 NPC 打开对话时，最多投递一条当前关系条件下尚未收到的消息。 */
    public static synchronized boolean deliverInteractionMessage(ServerPlayer player, int npcId) {
        ensureLoaded();
        int favorability = NpcRelationManager.getRelation(npcId, player.getUUID()).getFavorability();
        boolean zhuiguangMember = ZhuiguangMembershipManager.isMember(player);
        Optional<NpcMessageDefinition> next = DEFINITIONS.values().stream()
                .filter(definition -> definition.getNpcId() == npcId)
                .filter(definition -> StoryNpcContentPolicy.isRetainedMessage(
                        definition.getNpcId(), definition.getId()))
                // 主线私信的发送顺序只由对应 Java 阶段文件决定；即使服主把
                // 配置中的 trigger 改成 INTERACTION，也不能从 NPC 界面绕过状态机。
                .filter(definition -> !StoryNpcContentPolicy.isStoryControlledMessage(
                        definition.getId()))
                .filter(definition -> definition.getTrigger() == NpcMessageDefinition.DeliveryTrigger.INTERACTION)
                .filter(definition -> definition.isAvailableFor(favorability, zhuiguangMember))
                .filter(definition -> !definition.isOnce()
                        || !hasReceivedDefinitionInternal(player.getUUID(), definition.getId()))
                .sorted(Comparator.comparingInt(NpcMessageDefinition::getPriority).reversed()
                        .thenComparing(NpcMessageDefinition::getId))
                .findFirst();
        if (next.isEmpty()) {
            return false;
        }
        return deliverInternal(player, next.get(), true, true);
    }

    /** 主动发送一条普通（非主线）NPC 私信。主线必须调用 {@link #sendStoryMessage}。 */
    public static synchronized boolean sendConfiguredMessage(ServerPlayer player, String definitionId) {
        return sendConfiguredMessage(player, definitionId, false);
    }

    /**
     * 由 Java 阶段脚本调用的主线私信入口。
     *
     * <p>把主线入口单独命名，避免管理员命令、NPC 配置或未来 AI 修改时误把
     * 某条主线消息提前发送。消息正文仍来自配置，但发送时机只能由阶段文件决定。</p>
     */
    public static synchronized boolean sendStoryMessage(
            ServerPlayer player, String definitionId) {
        return sendConfiguredMessage(player, definitionId, true);
    }

    private static boolean sendConfiguredMessage(
            ServerPlayer player, String definitionId, boolean allowStoryMessage) {
        ensureLoaded();
        NpcMessageDefinition definition = DEFINITIONS.get(definitionId);
        if (definition == null) {
            return false;
        }
        // 故事状态机和管理员命令共用这个入口。仍然应用当前内容包策略，
        // 避免通过旧 ID 把已停用的 NPC 私信重新投递。
        if (!StoryNpcContentPolicy.isRetainedMessage(
                definition.getNpcId(), definition.getId())) {
            DreamingFishCore.LOGGER.warn(
                    "拒绝投递当前内容包未保留的 NPC 私信：{}", definition.getId());
            return false;
        }
        if (!allowStoryMessage
                && StoryNpcContentPolicy.isStoryControlledMessage(definition.getId())) {
            DreamingFishCore.LOGGER.warn(
                    "拒绝通过普通私信入口投递主线消息，请由对应阶段脚本推进：{}",
                    definition.getId());
            return false;
        }
        return deliverInternal(player, definition, true, true);
    }

    /**
     * 校验并保存一条预设回复。剧情推进不在消息存储层发生；调用方拿到结果后，
     * 再把“已回复”事实交给 StoryManager。
     */
    public static synchronized ReplyResult reply(ServerPlayer player, String recordId, String replyId) {
        ensureLoaded();
        if (!worldWritesEnabled) {
            return ReplyResult.rejected();
        }
        NpcMessageRecord source = dataFor(player.getUUID()).getMessages().stream()
                .filter(record -> record.getRecordId().equals(recordId))
                .findFirst()
                .orElse(null);
        if (source == null
                || source.getDirection() != NpcMessageRecord.Direction.NPC_TO_PLAYER
                || source.isReplied()) {
            return ReplyResult.rejected();
        }

        NpcMessageDefinition definition = DEFINITIONS.get(source.getDefinitionId());
        if (definition == null || !StoryNpcContentPolicy.isRetainedMessage(
                definition.getNpcId(), definition.getId())) {
            return ReplyResult.rejected();
        }

        // 回复按钮以投递时快照为准；没有快照的历史记录只可阅读，不能借当前
        // 配置重新激活已经移除的剧情节点。
        NpcReplySnapshot snapshot = source.getReplySnapshot(replyId);
        NpcMessageReplyDefinition reply = snapshot == null ? null : snapshot.toDefinition();
        if (reply == null || !source.markReplied(reply.getId())) {
            return ReplyResult.rejected();
        }

        source.markRead();
        NpcMessageRecord outgoing = NpcMessageRecord.outgoing(source, reply, System.currentTimeMillis());
        appendRecord(player.getUUID(), outgoing);
        if (reply.getFavorabilityDelta() != 0) {
            NpcRelationManager.applyFavorabilityEffect(
                    source.getNpcId(),
                    player.getUUID(),
                    outgoing.getFavorabilityEffectId(),
                    reply.getFavorabilityDelta());
        }
        dirty = true;

        if (!StoryNpcContentPolicy.isStoryControlledMessage(source.getDefinitionId())
                && !reply.getFollowUpMessageId().isBlank()) {
            NpcMessageDefinition followUp = DEFINITIONS.get(reply.getFollowUpMessageId());
            if (followUp != null && StoryNpcContentPolicy.isRetainedMessage(
                    followUp.getNpcId(), followUp.getId())) {
                deliverInternal(player, followUp, true, false);
            } else {
                DreamingFishCore.LOGGER.warn("NPC 回复 {} 指向不存在的后续消息：{}",
                        reply.getId(), reply.getFollowUpMessageId());
            }
        }

        syncToClient(player);
        GuidanceManager.syncToClient(player);
        return new ReplyResult(true, source.getDefinitionId(), reply.getId());
    }

    /** 标记会话已读，并返回其中可交给剧情状态机处理的消息定义 ID。 */
    public static synchronized ReadResult markConversationRead(ServerPlayer player, int npcId) {
        ensureLoaded();
        if (!worldWritesEnabled) {
            return ReadResult.empty();
        }
        boolean changed = false;
        // 重放所有已经读过的定义，而不是只处理“未读→已读”边沿。
        // Java 状态机按玩家事实判断是否已经推进；重放可以让上一次因管理器
        // 尚未加载而失败的投影在下次打开时补齐。
        java.util.LinkedHashSet<String> openedDefinitions = new java.util.LinkedHashSet<>();
        for (NpcMessageRecord record : dataFor(player.getUUID()).getMessages()) {
            if (isRetainedRecord(record)
                    && record.getNpcId() == npcId
                    && record.getDirection() == NpcMessageRecord.Direction.NPC_TO_PLAYER) {
                boolean newlyRead = record.markRead();
                changed |= newlyRead;
                String definitionId = record.getDefinitionId();
                if (record.isRead() && !definitionId.isBlank()) {
                    openedDefinitions.add(definitionId);
                }
            }
        }
        if (changed) {
            dirty = true;
            syncToClient(player);
        }
        // 已读事实可安全重放：调用方会把定义 ID 交给状态机，状态检查本身就是
        // 幂等边界，同时给上一次执行失败的投影留下重试机会。
        return new ReadResult(changed, Set.copyOf(openedDefinitions));
    }

    public static synchronized List<NpcConversationViewData> getView(UUID playerId) {
        ensureLoaded();
        Map<Integer, List<NpcMessageRecord>> byNpc = new LinkedHashMap<>();
        for (NpcMessageRecord record : dataFor(playerId).getMessages()) {
            // 旧主线记录仍可留在底层存档作为历史，但不再进入当前运行时视图。
            // 这样不需要迁移玩家数据，也不会让已退役的回复按钮重新出现。
            if (!isRetainedRecord(record)) {
                continue;
            }
            byNpc.computeIfAbsent(record.getNpcId(), ignored -> new ArrayList<>()).add(record);
        }

        List<NpcConversationViewData> result = new ArrayList<>();
        for (Map.Entry<Integer, List<NpcMessageRecord>> thread : byNpc.entrySet()) {
            int npcId = thread.getKey();
            List<NpcMessageRecord> records = thread.getValue();
            records.sort(Comparator.comparingLong(NpcMessageRecord::getSentAtEpochMillis));
            if (records.size() > 256) {
                records = new ArrayList<>(records.subList(records.size() - 256, records.size()));
            }

            NpcRelationData relation = NpcRelationManager.getRelation(npcId, playerId);
            List<NpcMessageViewData> messageViews = new ArrayList<>();
            int unread = 0;
            for (NpcMessageRecord record : records) {
                if (record.getDirection() == NpcMessageRecord.Direction.NPC_TO_PLAYER && !record.isRead()) {
                    unread++;
                }
                messageViews.add(toView(record));
            }

            NpcMessageRecord last = records.get(records.size() - 1);
            String npcName = records.stream()
                    .map(NpcMessageRecord::getNpcName)
                    .filter(name -> !name.isBlank())
                    .reduce((first, second) -> second)
                    .orElseGet(() -> NpcManager.getNpc(npcId).map(NpcData::getNpcName).orElse("NPC " + npcId));
            result.add(new NpcConversationViewData(
                    npcId,
                    npcName,
                    relation.getFavorability(),
                    relation.getRelationType().getDisplayName(),
                    unread,
                    last.getSentAtEpochMillis(),
                    messageViews));
        }

        result.sort(Comparator.comparingLong(NpcConversationViewData::lastMessageAtEpochMillis).reversed());
        return result.stream().limit(64).toList();
    }

    public static synchronized int getUnreadCount(UUID playerId) {
        ensureLoaded();
        return (int) dataFor(playerId).getMessages().stream()
                .filter(NpcMessageManager::isRetainedRecord)
                .filter(record -> record.getDirection() == NpcMessageRecord.Direction.NPC_TO_PLAYER)
                .filter(record -> !record.isRead())
                .count();
    }

    public static synchronized List<String> getDefinitionIds() {
        return List.copyOf(DEFINITIONS.keySet());
    }

    /** 供故事内容编译检查使用：定义存在，且属于本轮允许新投递的内容。 */
    public static synchronized boolean hasDeliverableDefinition(String definitionId) {
        NpcMessageDefinition definition = DEFINITIONS.get(definitionId);
        return definition != null && StoryNpcContentPolicy.isRetainedMessage(
                definition.getNpcId(), definition.getId());
    }

    /** 供故事内容编译检查使用：预设回复必须属于指定的可投递消息。 */
    public static synchronized boolean hasReplyDefinition(
            String definitionId, String replyId) {
        NpcMessageDefinition definition = DEFINITIONS.get(definitionId);
        return definition != null
                && StoryNpcContentPolicy.isRetainedMessage(
                definition.getNpcId(), definition.getId())
                && replyId != null
                && definition.getReplies().stream()
                .anyMatch(reply -> replyId.equals(reply.getId()));
    }

    /** 供剧情执行器在异常恢复时确认一次性消息是否已经成功投递。 */
    public static synchronized boolean hasReceivedDefinition(UUID playerId, String definitionId) {
        ensureLoaded();
        return playerId != null
                && definitionId != null
                && !definitionId.isBlank()
                && hasDeliverableDefinition(definitionId)
                && hasReceivedDefinitionInternal(playerId, definitionId);
    }

    /** 查询玩家是否已经读过指定的入站私信；故事状态机用它恢复已发生事实。 */
    public static synchronized boolean hasReadDefinition(UUID playerId, String definitionId) {
        ensureLoaded();
        if (playerId == null || definitionId == null || definitionId.isBlank()) {
            return false;
        }
        if (!hasDeliverableDefinition(definitionId)) {
            return false;
        }
        return dataFor(playerId).getMessages().stream()
                .anyMatch(record -> record != null
                        && record.getDirection() == NpcMessageRecord.Direction.NPC_TO_PLAYER
                        && definitionId.equals(record.getDefinitionId())
                        && record.isRead());
    }

    /**
     * 查询玩家是否已经提交过某条预设回复。
     *
     * <p>回复记录和剧情状态分属两个存档。服务器可能在其中一个写入后中断，
     * 因此开场状态机在登录时用这个只读事实补齐成员选择，而不是让玩家因为
     * “按钮已经消失”卡在中间节点。</p>
     */
    public static synchronized boolean hasSelectedReply(
            UUID playerId, String definitionId, String replyId) {
        ensureLoaded();
        if (playerId == null || definitionId == null || definitionId.isBlank()
                || replyId == null || replyId.isBlank()) {
            return false;
        }
        if (!hasReplyDefinition(definitionId, replyId)) {
            return false;
        }
        PlayerNpcMessageData data = PLAYER_DATA.get(playerId.toString());
        if (data == null) {
            return false;
        }
        return data.getMessages().stream()
                .filter(record -> record != null
                        && record.getDirection() == NpcMessageRecord.Direction.NPC_TO_PLAYER)
                .filter(record -> definitionId.equals(record.getDefinitionId()))
                .anyMatch(record -> replyId.equals(record.getSelectedReplyId()));
    }

    /** 世界私信存档已经加载后，剧情事件才可以投递玩家消息。 */
    public static synchronized boolean isWorldDataLoaded() {
        return loaded;
    }

    /**
     * 两个独立存档之间的幂等修复：若消息已经落盘而引导尚未来得及保存，
     * 根据消息中保存的作者配置快照补齐缺失记录。
     */
    public static synchronized int reconcileGuidanceRecords() {
        ensureLoaded();
        int repaired = 0;
        for (Map.Entry<String, PlayerNpcMessageData> playerEntry : PLAYER_DATA.entrySet()) {
            UUID playerId;
            try {
                playerId = UUID.fromString(playerEntry.getKey());
            } catch (RuntimeException exception) {
                continue;
            }
            for (NpcMessageRecord record : playerEntry.getValue().getMessages()) {
                if (!isRetainedRecord(record)
                        || record.getDirection() != NpcMessageRecord.Direction.NPC_TO_PLAYER
                        || record.getGuidanceSnapshot() == null) {
                    continue;
                }
                // 阶段主线的引导由对应 Java 阶段文件投影；不能从旧私信快照
                // 再生成第二份来源，否则重启/重置后会出现两条互相矛盾的引导。
                if (!StoryNpcContentPolicy.isStoryControlledMessage(record.getDefinitionId())
                        && GuidanceManager.createFromMessage(
                        playerId,
                        record.getGuidanceSnapshot(),
                        record.getRecordId(),
                        record.getNpcId(),
                        record.getNpcName(),
                        record.getContent())) {
                    repaired++;
                }
            }
        }
        if (repaired > 0) {
            DreamingFishCore.LOGGER.warn("已从 NPC 私信快照补齐 {} 条缺失的个人引导", repaired);
        }
        return repaired;
    }

    /** 从已保存的玩家回复补齐尚未写入关系存档的幂等好感度变化。 */
    public static synchronized int reconcileFavorabilityEffects() {
        ensureLoaded();
        int repaired = 0;
        for (Map.Entry<String, PlayerNpcMessageData> playerEntry : PLAYER_DATA.entrySet()) {
            UUID playerId;
            try {
                playerId = UUID.fromString(playerEntry.getKey());
            } catch (RuntimeException exception) {
                continue;
            }
            for (NpcMessageRecord record : playerEntry.getValue().getMessages()) {
                if (!isRetainedRecord(record)
                        || record.getDirection() != NpcMessageRecord.Direction.PLAYER_TO_NPC
                        || record.getFavorabilityDelta() == 0
                        || record.getFavorabilityEffectId().isBlank()) {
                    continue;
                }
                if (NpcRelationManager.applyFavorabilityEffect(
                        record.getNpcId(),
                        playerId,
                        record.getFavorabilityEffectId(),
                        record.getFavorabilityDelta())) {
                    repaired++;
                }
            }
        }
        if (repaired > 0) {
            DreamingFishCore.LOGGER.warn("已从 NPC 私信回复补齐 {} 条好感度变化", repaired);
        }
        return repaired;
    }

    public static void syncToClient(ServerPlayer player) {
        DreamingFishCore_NetworkManager.sendToClient(
                player,
                new Packet_NpcMessageSnapshotResponse(getView(player.getUUID())));
    }

    public static synchronized boolean saveIfDirty(MinecraftServer server) {
        if (!loaded || !dirty) {
            return true;
        }
        if (!worldWritesEnabled) {
            DreamingFishCore.LOGGER.error("NPC 私信世界数据处于只读保护，拒绝覆盖损坏文件");
            return false;
        }
        try {
            JsonDataStore.writeAtomic(
                    WorldDataPaths.resolve(server, "communication", "npc_messages.json"),
                    GSON,
                    PLAYER_DATA);
            dirty = false;
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("写入 NPC 私信数据失败，保留 dirty 状态等待下次保存", exception);
            return false;
        }
    }

    public static synchronized void clearWorldCache() {
        PLAYER_DATA.clear();
        dirty = false;
        loaded = false;
        worldWritesEnabled = false;
    }

    private static boolean deliverInternal(
            ServerPlayer player,
            NpcMessageDefinition definition,
            boolean notifyPlayer,
            boolean syncAfter) {
        if (!worldWritesEnabled || player == null || definition == null) {
            return false;
        }
        if (!StoryNpcContentPolicy.isRetainedMessage(
                definition.getNpcId(), definition.getId())) {
            DreamingFishCore.LOGGER.warn(
                    "拒绝投递当前内容包未保留的 NPC 私信：{}", definition.getId());
            return false;
        }
        int favorability = NpcRelationManager.getRelation(definition.getNpcId(), player.getUUID()).getFavorability();
        boolean zhuiguangMember = ZhuiguangMembershipManager.isMember(player);
        if (!definition.isAvailableFor(favorability, zhuiguangMember)
                || (definition.isOnce()
                && hasReceivedDefinitionInternal(player.getUUID(), definition.getId()))) {
            return false;
        }

        NpcData npc = NpcManager.getNpc(definition.getNpcId()).orElse(null);
        if (npc == null) {
            DreamingFishCore.LOGGER.warn("拒绝投递消息 {}：NPC {} 不存在", definition.getId(), definition.getNpcId());
            return false;
        }

        String npcName = bounded(npc.getNpcName(), 128);
        NpcMessageRecord record = NpcMessageRecord.incoming(
                definition,
                npcName,
                System.currentTimeMillis(),
                favorability,
                zhuiguangMember);
        appendRecord(player.getUUID(), record);
        dirty = true;
        // 主线私信只负责保存消息事实；对应阶段 Java 文件在这里收到消息后
        // 建立唯一的故事引导，避免 NPC 配置和阶段脚本各生成一份进度。
        boolean guideCreated = !StoryNpcContentPolicy.isStoryControlledMessage(definition.getId())
                && GuidanceManager.createFromMessage(
                        player.getUUID(),
                        record.getGuidanceSnapshot(),
                        record.getRecordId(),
                        definition.getNpcId(),
                        npcName,
                        definition.getContent());

        if (notifyPlayer) {
            NotificationPushHelper.sendTopLeftNotification(
                    player,
                    "§f收到来自 §e" + npcName + "§f 的新消息"
                            + "\n§7按 U 打开终端，在“NPC 私信”中查看详情");
        }
        if (syncAfter) {
            syncToClient(player);
            if (guideCreated) {
                GuidanceManager.syncToClient(player);
            }
        }
        return true;
    }

    private static NpcMessageViewData toView(NpcMessageRecord record) {
        List<NpcReplyViewData> replies = List.of();
        if (record.getDirection() == NpcMessageRecord.Direction.NPC_TO_PLAYER && !record.isReplied()) {
            if (record.hasReplySnapshot()) {
                replies = record.getReplySnapshots().stream()
                        .filter(snapshot -> snapshot != null)
                        .limit(MAX_REPLY_OPTIONS)
                        .map(snapshot -> new NpcReplyViewData(
                                snapshot.getId(), snapshot.getText()))
                        .toList();
            } else {
                // 缺少投递快照的历史记录只作为不可交互的通信档案展示。
                replies = List.of();
            }
        }
        return new NpcMessageViewData(
                record.getRecordId(),
                record.getDefinitionId(),
                record.getSubject(),
                record.getDirection(),
                record.getContent(),
                record.getSentAtEpochMillis(),
                record.isRead(),
                record.isReplied(),
                replies);
    }

    private static PlayerNpcMessageData dataFor(UUID playerId) {
        return PLAYER_DATA.computeIfAbsent(playerId.toString(), ignored -> new PlayerNpcMessageData());
    }

    private static void appendRecord(UUID playerId, NpcMessageRecord record) {
        List<NpcMessageRecord> records = dataFor(playerId).getMessages();
        records.add(record);
        if (records.size() > MAX_STORED_MESSAGES_PER_PLAYER) {
            records.subList(0, records.size() - MAX_STORED_MESSAGES_PER_PLAYER).clear();
        }
    }

    private static boolean hasReceivedDefinitionInternal(UUID playerId, String definitionId) {
        return dataFor(playerId).getMessages().stream()
                .anyMatch(record -> record.getDirection() == NpcMessageRecord.Direction.NPC_TO_PLAYER
                        && record.getDefinitionId().equals(definitionId));
    }

    private static Map<String, NpcMessageDefinition> validateDefinitions(NpcMessageConfig config) {
        Map<String, NpcMessageDefinition> valid = new LinkedHashMap<>();
        if (config == null || config.getSchemaVersion() != 1) {
            DreamingFishCore.LOGGER.error("NPC 私信配置 schemaVersion 不受支持");
            return valid;
        }
        int retiredCount = 0;
        for (NpcMessageDefinition definition : config.getMessages()) {
            if (!isValidDefinition(definition)) {
                DreamingFishCore.LOGGER.warn("忽略非法 NPC 私信定义：{}",
                        definition == null ? "<null>" : definition.getId());
                continue;
            }
            if (!StoryNpcContentPolicy.isRetainedMessage(
                    definition.getNpcId(), definition.getId())) {
                retiredCount++;
                continue;
            }
            if (valid.putIfAbsent(definition.getId(), definition) != null) {
                DreamingFishCore.LOGGER.warn("忽略重复 NPC 私信 ID：{}", definition.getId());
            }
        }
        if (retiredCount > 0) {
            DreamingFishCore.LOGGER.info(
                    "已从当前运行时忽略 {} 条退役 NPC 私信定义", retiredCount);
        }
        return valid;
    }

    /**
     * 当前运行时只承认内容策略保留的消息。
     * 玩家回复记录的 definitionId 会带 {@code #reply/...} 后缀，因此先还原来源定义。
     */
    private static boolean isRetainedRecord(NpcMessageRecord record) {
        if (record == null) {
            return false;
        }
        String definitionId = record.getDefinitionId();
        if (record.getDirection() == NpcMessageRecord.Direction.PLAYER_TO_NPC) {
            int replyMarker = definitionId.indexOf("#reply/");
            if (replyMarker > 0) {
                definitionId = definitionId.substring(0, replyMarker);
            }
        }
        return StoryNpcContentPolicy.isRetainedMessage(record.getNpcId(), definitionId);
    }

    private static boolean isValidDefinition(NpcMessageDefinition definition) {
        if (definition == null
                || !RESOURCE_ID.matcher(definition.getId()).matches()
                || definition.getId().length() > 128
                || definition.getNpcId() <= 0
                || definition.getContent().isBlank()
                || definition.getSubject().length() > 256
                || definition.getContent().length() > 4096
                || definition.getMinimumFavorability() > definition.getMaximumFavorability()) {
            return false;
        }
        GuidanceSeed guidance = definition.getGuidance();
        if (guidance != null && (!RESOURCE_ID.matcher(guidance.getId()).matches()
                || guidance.getId().length() > 128
                || guidance.getTitle().isBlank()
                || guidance.getTitle().length() > 256
                || guidance.getContent().isBlank()
                || guidance.getContent().length() > 4096
                || guidance.getStoryStageId().length() > 160
                || guidance.getLocationLabel().length() > 256
                || guidance.getDimension().length() > 160)) {
            return false;
        }
        Set<String> replyIds = new HashSet<>();
        for (NpcMessageReplyDefinition reply : definition.getReplies()) {
            if (reply == null
                    || !REPLY_ID.matcher(reply.getId()).matches()
                    || reply.getId().length() > 48
                    || !replyIds.add(reply.getId())
                    || reply.getText().isBlank()
                    || reply.getText().length() > 1024
                    || reply.getMinimumFavorability() > reply.getMaximumFavorability()
                    || (!reply.getFollowUpMessageId().isBlank()
                    && (!RESOURCE_ID.matcher(reply.getFollowUpMessageId()).matches()
                    || reply.getFollowUpMessageId().length() > 128))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidRecord(NpcMessageRecord record) {
        if (record == null
                || record.getRecordId().isBlank()
                || record.getRecordId().length() > 64
                || record.getDefinitionId().isBlank()
                || record.getDefinitionId().length() > 256
                || record.getNpcId() <= 0
                || record.getNpcName().length() > 128
                || record.getSubject().length() > 256
                || record.getFavorabilityEffectId().length() > 128
                || record.getContent().isBlank()
                || record.getContent().length() > 4096) {
            return false;
        }
        if (record.hasReplySnapshot()) {
            List<NpcReplySnapshot> snapshots = record.getReplySnapshots();
            if (snapshots.size() > MAX_REPLY_OPTIONS) {
                return false;
            }
            Set<String> ids = new HashSet<>();
            for (NpcReplySnapshot snapshot : snapshots) {
                if (snapshot == null
                        || !REPLY_ID.matcher(snapshot.getId()).matches()
                        || snapshot.getId().length() > 48
                        || !ids.add(snapshot.getId())
                        || snapshot.getText().isBlank()
                        || snapshot.getText().length() > 1024
                        || (!snapshot.getFollowUpMessageId().isBlank()
                        && (!RESOURCE_ID.matcher(snapshot.getFollowUpMessageId()).matches()
                        || snapshot.getFollowUpMessageId().length() > 128))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String bounded(String value, int maximumLength) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximumLength ? safe : safe.substring(0, maximumLength);
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void saveDefinitionConfig(NpcMessageConfig config) {
        if (!configWritable) {
            return;
        }
        try {
            JsonDataStore.writeAtomic(CONFIG_PATH, GSON, config);
        } catch (Exception exception) {
            configWritable = false;
            DreamingFishCore.LOGGER.error("写入默认 NPC 私信配置失败：{}", CONFIG_PATH, exception);
        }
    }

    private static NpcMessageConfig createDefaultConfig() {
        List<NpcMessageDefinition> bundledMessages =
                BuiltInNpcMessageCatalog.loadCompleteMessages();
        if (!bundledMessages.isEmpty()) {
            return new NpcMessageConfig(bundledMessages);
        }

        // 任何资源缺失回退都只能生成白名单角色的最小消息，不能复活已删除 NPC。
        return new NpcMessageConfig(
                BuiltInNpcMessageCatalog.createMissingMessages(List.of()));
    }

    /** 消息层保存回复后的结果；剧情桥接层据此推进 Java 状态机。 */
    public record ReplyResult(boolean accepted, String definitionId, String replyId) {
        private static ReplyResult rejected() {
            return new ReplyResult(false, "", "");
        }
    }

    /** 一次会话已读操作的结果；definitionIds 包含可幂等重放的入站消息。 */
    public record ReadResult(boolean changed, Set<String> definitionIds) {
        private static ReadResult empty() {
            return new ReadResult(false, Set.of());
        }
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("NPC 私信数据尚未随世界加载");
        }
    }
}
