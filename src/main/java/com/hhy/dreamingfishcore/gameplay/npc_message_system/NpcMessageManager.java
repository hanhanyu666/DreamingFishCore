package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceManager;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceSeed;
import com.hhy.dreamingfishcore.gameplay.story_system.runtime.StoryFlowEngine;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.network.Packet_NpcMessageSnapshotResponse;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcData;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcManager;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcRelationData;
import com.hhy.dreamingfishcore.gameplay.npc_system.NpcRelationManager;
import com.hhy.dreamingfishcore.gameplay.npc_system.StoryNpcContentPolicy;
import com.hhy.dreamingfishcore.gameplay.zhuiguang_system.ZhuiguangMembershipAction;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private static final Map<String, NpcMessageDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<String, PlayerNpcMessageData> PLAYER_DATA = new ConcurrentHashMap<>();

    private static boolean configWritable;
    private static boolean loaded;
    private static boolean dirty;

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
            boolean migrated = false;
            if (config.getSchemaVersion() == 1) {
                int definitionCountBeforeFilter = config.getMessages().size();
                config.getMessages().removeIf(definition -> definition == null
                        || !StoryNpcContentPolicy.isRetained(definition.getNpcId()));
                migrated |= definitionCountBeforeFilter != config.getMessages().size();
                migrated |= BuiltInNpcMessageCatalog.migrateBaizhiObservationsCopy(
                        config.getMessages());
                List<NpcMessageDefinition> additions =
                        BuiltInNpcMessageCatalog.createMissingMessages(config.getMessages());
                if (!additions.isEmpty()) {
                    config.getMessages().addAll(additions);
                    migrated = true;
                }
            }
            Map<String, NpcMessageDefinition> validated = validateDefinitions(config);
            DEFINITIONS.clear();
            DEFINITIONS.putAll(validated);
            configWritable = true;
            if (migrated) {
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
        try {
            Map<String, PlayerNpcMessageData> loadedData = JsonDataStore.read(
                    WorldDataPaths.resolve(server, "communication", "npc_messages.json"),
                    GSON,
                    PLAYER_DATA_TYPE,
                    ConcurrentHashMap::new);
            boolean migratedCopy = false;
            for (Map.Entry<String, PlayerNpcMessageData> entry : loadedData.entrySet()) {
                String playerId = entry.getKey();
                PlayerNpcMessageData data = entry.getValue();
                if (!isUuid(playerId) || data == null) {
                    continue;
                }
                int messageCountBeforeFilter = data.getMessages().size();
                data.getMessages().removeIf(record -> !isValidRecord(record)
                        || !StoryNpcContentPolicy.isRetained(record.getNpcId()));
                migratedCopy |= messageCountBeforeFilter != data.getMessages().size();
                migratedCopy |= BuiltInNpcMessageCatalog.migrateDeliveredBaizhiObservations(
                        data.getMessages());
                if (data.getMessages().size() > MAX_STORED_MESSAGES_PER_PLAYER) {
                    int removeCount = data.getMessages().size() - MAX_STORED_MESSAGES_PER_PLAYER;
                    data.getMessages().subList(0, removeCount).clear();
                }
                PLAYER_DATA.put(playerId, data);
            }
            dirty = migratedCopy;
            if (migratedCopy) {
                DreamingFishCore.LOGGER.info("已更新存档中的旧版白芷私信文案");
            }
            DreamingFishCore.LOGGER.info("NPC 私信数据加载完成，共 {} 名玩家", PLAYER_DATA.size());
        } catch (Exception exception) {
            dirty = false;
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

    /** 管理员或剧情系统主动发送一条已配置消息。 */
    public static synchronized boolean sendConfiguredMessage(ServerPlayer player, String definitionId) {
        ensureLoaded();
        NpcMessageDefinition definition = DEFINITIONS.get(definitionId);
        return definition != null && deliverInternal(player, definition, true, true);
    }

    public static synchronized boolean reply(ServerPlayer player, String recordId, String replyId) {
        ensureLoaded();
        NpcMessageRecord source = dataFor(player.getUUID()).getMessages().stream()
                .filter(record -> record.getRecordId().equals(recordId))
                .findFirst()
                .orElse(null);
        if (source == null
                || source.getDirection() != NpcMessageRecord.Direction.NPC_TO_PLAYER
                || source.isReplied()) {
            return false;
        }

        NpcMessageDefinition definition = DEFINITIONS.get(source.getDefinitionId());
        if (definition == null) {
            return false;
        }
        int favorability = NpcRelationManager.getRelation(source.getNpcId(), player.getUUID()).getFavorability();
        boolean zhuiguangMember = ZhuiguangMembershipManager.isMember(player);
        NpcMessageReplyDefinition reply = definition.getReplies().stream()
                .filter(candidate -> candidate.getId().equals(replyId))
                .filter(candidate -> candidate.isAvailableFor(favorability, zhuiguangMember))
                .findFirst()
                .orElse(null);
        if (reply == null || !source.markReplied(reply.getId())) {
            return false;
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
        boolean membershipChanged = applyMembershipAction(player, reply.getMembershipAction());
        if (membershipChanged) {
            NotificationPushHelper.sendTopLeftNotification(
                    player,
                    "§e组织身份已更新§r\n§7当前："
                            + ZhuiguangMembershipManager.getDisplayName(
                            ZhuiguangMembershipManager.isMember(player)),
                    6500);
        }
        dirty = true;

        if (!reply.getFollowUpMessageId().isBlank()) {
            NpcMessageDefinition followUp = DEFINITIONS.get(reply.getFollowUpMessageId());
            if (followUp != null) {
                deliverInternal(player, followUp, true, false);
            } else {
                DreamingFishCore.LOGGER.warn("NPC 回复 {} 指向不存在的后续消息：{}",
                        reply.getId(), reply.getFollowUpMessageId());
            }
        }

        StoryFlowEngine.onNpcReply(player, definition.getId(), reply.getId());

        syncToClient(player);
        GuidanceManager.syncToClient(player);
        return true;
    }

    public static synchronized boolean markConversationRead(ServerPlayer player, int npcId) {
        ensureLoaded();
        boolean changed = false;
        for (NpcMessageRecord record : dataFor(player.getUUID()).getMessages()) {
            if (record.getNpcId() == npcId
                    && record.getDirection() == NpcMessageRecord.Direction.NPC_TO_PLAYER) {
                changed |= record.markRead();
            }
        }
        if (changed) {
            dirty = true;
            syncToClient(player);
        }
        return changed;
    }

    public static synchronized List<NpcConversationViewData> getView(UUID playerId) {
        ensureLoaded();
        Map<Integer, List<NpcMessageRecord>> byNpc = new LinkedHashMap<>();
        for (NpcMessageRecord record : dataFor(playerId).getMessages()) {
            byNpc.computeIfAbsent(record.getNpcId(), ignored -> new ArrayList<>()).add(record);
        }

        List<NpcConversationViewData> result = new ArrayList<>();
        boolean zhuiguangMember = ZhuiguangMembershipManager.isMember(playerId);
        for (Map.Entry<Integer, List<NpcMessageRecord>> thread : byNpc.entrySet()) {
            int npcId = thread.getKey();
            List<NpcMessageRecord> records = thread.getValue();
            records.sort(Comparator.comparingLong(NpcMessageRecord::getSentAtEpochMillis));
            if (records.size() > 256) {
                records = new ArrayList<>(records.subList(records.size() - 256, records.size()));
            }

            NpcRelationData relation = NpcRelationManager.getRelation(npcId, playerId);
            int favorability = relation.getFavorability();
            List<NpcMessageViewData> messageViews = new ArrayList<>();
            int unread = 0;
            for (NpcMessageRecord record : records) {
                if (record.getDirection() == NpcMessageRecord.Direction.NPC_TO_PLAYER && !record.isRead()) {
                    unread++;
                }
                messageViews.add(toView(record, favorability, zhuiguangMember));
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
                    favorability,
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
                .filter(record -> record.getDirection() == NpcMessageRecord.Direction.NPC_TO_PLAYER)
                .filter(record -> !record.isRead())
                .count();
    }

    public static synchronized List<String> getDefinitionIds() {
        return List.copyOf(DEFINITIONS.keySet());
    }

    /** 供剧情执行器在异常恢复时确认一次性消息是否已经成功投递。 */
    public static synchronized boolean hasReceivedDefinition(UUID playerId, String definitionId) {
        ensureLoaded();
        return playerId != null
                && definitionId != null
                && !definitionId.isBlank()
                && hasReceivedDefinitionInternal(playerId, definitionId);
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
                if (record.getDirection() != NpcMessageRecord.Direction.NPC_TO_PLAYER
                        || record.getGuidanceSnapshot() == null) {
                    continue;
                }
                if (GuidanceManager.createFromMessage(
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
                if (record.getDirection() != NpcMessageRecord.Direction.PLAYER_TO_NPC
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
    }

    private static boolean deliverInternal(
            ServerPlayer player,
            NpcMessageDefinition definition,
            boolean notifyPlayer,
            boolean syncAfter) {
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
        NpcMessageRecord record = NpcMessageRecord.incoming(definition, npcName, System.currentTimeMillis());
        appendRecord(player.getUUID(), record);
        dirty = true;
        boolean guideCreated = GuidanceManager.createFromMessage(
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

    private static NpcMessageViewData toView(
            NpcMessageRecord record,
            int favorability,
            boolean zhuiguangMember) {
        List<NpcReplyViewData> replies = List.of();
        if (record.getDirection() == NpcMessageRecord.Direction.NPC_TO_PLAYER && !record.isReplied()) {
            NpcMessageDefinition definition = DEFINITIONS.get(record.getDefinitionId());
            if (definition != null) {
                replies = definition.getReplies().stream()
                        .filter(reply -> reply.isAvailableFor(favorability, zhuiguangMember))
                        .limit(8)
                        .map(reply -> new NpcReplyViewData(reply.getId(), reply.getText()))
                        .toList();
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

    private static boolean applyMembershipAction(
            ServerPlayer player,
            ZhuiguangMembershipAction action) {
        return switch (action == null ? ZhuiguangMembershipAction.NONE : action) {
            case NONE -> false;
            case JOIN -> ZhuiguangMembershipManager.setMember(player, true);
            case LEAVE -> ZhuiguangMembershipManager.setMember(player, false);
        };
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
        for (NpcMessageDefinition definition : config.getMessages()) {
            if (!isValidDefinition(definition)) {
                DreamingFishCore.LOGGER.warn("忽略非法 NPC 私信定义：{}",
                        definition == null ? "<null>" : definition.getId());
                continue;
            }
            if (valid.putIfAbsent(definition.getId(), definition) != null) {
                DreamingFishCore.LOGGER.warn("忽略重复 NPC 私信 ID：{}", definition.getId());
            }
        }
        return valid;
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
        for (NpcMessageReplyDefinition reply : definition.getReplies()) {
            if (reply == null
                    || !REPLY_ID.matcher(reply.getId()).matches()
                    || reply.getId().length() > 48
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
        return record != null
                && !record.getRecordId().isBlank()
                && record.getRecordId().length() <= 64
                && !record.getDefinitionId().isBlank()
                && record.getDefinitionId().length() <= 256
                && record.getNpcId() > 0
                && record.getNpcName().length() <= 128
                && record.getSubject().length() <= 256
                && record.getFavorabilityEffectId().length() <= 128
                && !record.getContent().isBlank()
                && record.getContent().length() <= 4096;
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

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("NPC 私信数据尚未随世界加载");
        }
    }
}
