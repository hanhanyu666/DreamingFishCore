package com.hhy.dreamingfishcore.gameplay.guidance_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.guidance_system.network.Packet_GuidanceSnapshotResponse;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 独立于短信会话存储的个人引导管理器。 */
public final class GuidanceManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, List<GuidanceEntry>>>() { }.getType();
    private static final Map<String, List<GuidanceEntry>> PLAYER_ENTRIES = new ConcurrentHashMap<>();

    private static boolean loaded;
    private static boolean dirty;
    private static boolean writesEnabled;

    private GuidanceManager() {
    }

    public static synchronized void loadWorldData(MinecraftServer server) {
        PLAYER_ENTRIES.clear();
        dirty = false;
        writesEnabled = false;
        try {
            Map<String, List<GuidanceEntry>> loadedData = JsonDataStore.read(
                    WorldDataPaths.resolve(server, "guidance", "player_guidance.json"),
                    GSON,
                    DATA_TYPE,
                    ConcurrentHashMap::new);
            boolean[] repairedRows = {false};
            loadedData.forEach((playerId, entries) -> {
                if (isUuid(playerId) && entries != null) {
                    int before = entries.size();
                    List<GuidanceEntry> valid = entries.stream()
                            .filter(GuidanceManager::isValidEntry)
                            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                    repairedRows[0] |= before != valid.size();
                    PLAYER_ENTRIES.put(playerId, valid);
                }
            });
            writesEnabled = true;
            dirty = repairedRows[0];
            if (dirty) {
                DreamingFishCore.LOGGER.info("已忽略格式非法的个人引导记录；历史内容不会按当前剧情白名单删除");
            }
            DreamingFishCore.LOGGER.info("个人引导数据加载完成，共 {} 名玩家", PLAYER_ENTRIES.size());
        } catch (Exception exception) {
            dirty = false;
            writesEnabled = false;
            DreamingFishCore.LOGGER.error("读取个人引导数据失败，本次会话不会覆盖损坏文件", exception);
        } finally {
            loaded = true;
        }
    }

    public static synchronized boolean createFromMessage(
            UUID playerId,
            GuidanceSeed seed,
            String sourceMessageRecordId,
            int sourceNpcId,
            String sourceNpcName,
            String sourceQuote) {
        ensureLoaded();
        if (!writesEnabled || !isValidSeed(seed)
                || sourceMessageRecordId == null || sourceMessageRecordId.isBlank()) {
            return false;
        }
        List<GuidanceEntry> entries = entriesFor(playerId);
        boolean alreadyExists = entries.stream().anyMatch(entry ->
                entry.getDefinitionId().equals(seed.getId())
                        || entry.getSourceMessageRecordId().equals(sourceMessageRecordId));
        if (alreadyExists) {
            return false;
        }
        entries.add(GuidanceEntry.fromMessage(
                seed,
                sourceMessageRecordId,
                sourceNpcId,
                sourceNpcName,
                sourceQuote,
                System.currentTimeMillis()));
        dirty = true;
        return true;
    }

    /**
     * 为公告、抵达地点等非私信剧情事件建立个人引导。
     * sourceEventId 同样参与幂等判断，重复打开公告不会生成重复记录。
     */
    public static synchronized boolean createFromStoryEvent(
            UUID playerId,
            GuidanceSeed seed,
            String sourceEventId,
            String sourceName,
            String sourceQuote) {
        return createFromStoryEvent(
                playerId, seed, sourceEventId, 0, sourceName, sourceQuote);
    }

    /** 为流程效果保留 NPC 来源，使引导记录能显示真实的对白来源。 */
    public static synchronized boolean createFromStoryEvent(
            UUID playerId,
            GuidanceSeed seed,
            String sourceEventId,
            int sourceNpcId,
            String sourceName,
            String sourceQuote) {
        return createFromMessage(
                playerId,
                seed,
                sourceEventId,
                sourceNpcId,
                sourceName == null ? "" : sourceName,
                sourceQuote == null ? "" : sourceQuote);
    }

    /**
     * 建立当前阶段步骤的唯一活动引导。
     *
     * <p>普通 NPC 私信使用 {@link #createFromMessage} 的“只创建一次”语义；阶段脚本
     * 使用本方法，因为阶段状态才是事实来源。玩家在开发环境重置故事状态、或一次
     * 保存中途进程退出时，已有的 RESOLVED/ARCHIVED 投影需要被重新激活，否则故事
     * 状态已经回到待办但客户端永远看不到引导。</p>
     */
    public static synchronized boolean ensureActiveFromStoryEvent(
            UUID playerId,
            GuidanceSeed seed,
            String sourceEventId,
            String sourceName,
            String sourceQuote) {
        return ensureActiveFromStoryEvent(
                playerId, seed, sourceEventId, 0, sourceName, sourceQuote);
    }

    public static synchronized boolean ensureActiveFromStoryEvent(
            UUID playerId,
            GuidanceSeed seed,
            String sourceEventId,
            int sourceNpcId,
            String sourceName,
            String sourceQuote) {
        ensureLoaded();
        if (!writesEnabled || playerId == null || !isValidSeed(seed)
                || sourceEventId == null || sourceEventId.isBlank()) {
            return false;
        }

        List<GuidanceEntry> entries = entriesFor(playerId);
        long now = System.currentTimeMillis();

        // 仅替换同一任务线的旧步骤；同阶段的其他任务线继续活动。
        boolean changed = false;
        String storyStageId = seed.getStoryStageId();
        if (!storyStageId.isBlank()) {
            for (GuidanceEntry entry : entries) {
                if (entry != null && entry.isReplacedBy(seed)
                        && entry.resolve(now)) {
                    changed = true;
                }
            }
        }

        // 故事引导以稳定 definitionId 作为唯一身份。sourceEventId 只是来源
        // 描述，不能参与查找；否则两个步骤若复用同一来源事件，会把新步骤
        // 错误地折叠到旧记录上（这正是“读完白芷后没有下一步引导”的根因）。
        GuidanceEntry existing = entries.stream()
                .filter(entry -> entry != null
                        && seed.getId().equals(entry.getDefinitionId()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            changed |= existing.updateProjection(seed);
            if (existing.reactivate()) {
                dirty = true;
                return true;
            }
            if (changed) {
                dirty = true;
            }
            return changed;
        }

        entries.add(GuidanceEntry.fromMessage(
                seed,
                sourceEventId,
                sourceNpcId,
                sourceName == null ? "" : sourceName,
                sourceQuote == null ? "" : sourceQuote,
                now));
        dirty = true;
        return true;
    }

    /** 查询个人引导定义是否已经存在（ACTIVE、RESOLVED、ARCHIVED 都算存在）。 */
    public static synchronized boolean hasDefinition(UUID playerId, String definitionId) {
        if (!loaded || playerId == null || definitionId == null || definitionId.isBlank()) {
            return false;
        }
        return entriesFor(playerId).stream()
                .anyMatch(entry -> entry != null && definitionId.equals(entry.getDefinitionId()));
    }

    /**
     * 关闭不再处于当前阶段的主线引导。
     *
     * <p>引导文件是投影历史，阶段切换不会删除历史记录；但旧阶段的 ACTIVE
     * 记录不能继续出现在当前 HUD 或“当前引导”列表里。这个集中操作由
     * StoryManager 在阶段切换和服务初始化时调用，普通引导（没有 storyStageId）
     * 不受影响。</p>
     */
    public static synchronized int closeStoryStagesExcept(String currentStageId) {
        if (!loaded || !writesEnabled) {
            return 0;
        }
        String current = currentStageId == null ? "" : currentStageId;
        int closed = 0;
        long now = System.currentTimeMillis();
        for (List<GuidanceEntry> entries : PLAYER_ENTRIES.values()) {
            if (entries == null) {
                continue;
            }
            for (GuidanceEntry entry : entries) {
                if (entry == null || entry.getStoryStageId().isBlank()
                        || current.equals(entry.getStoryStageId())) {
                    continue;
                }
                if (entry.archive(now)) {
                    closed++;
                }
            }
        }
        if (closed > 0) {
            dirty = true;
        }
        return closed;
    }

    /**
     * 只供服务端验证器或管理员工具调用。客户端没有“完成引导”数据包。
     */
    public static synchronized boolean resolve(UUID playerId, String recordOrDefinitionId) {
        ensureLoaded();
        if (!writesEnabled || recordOrDefinitionId == null || recordOrDefinitionId.isBlank()) {
            return false;
        }
        for (GuidanceEntry entry : entriesFor(playerId)) {
            if ((entry.getRecordId().equals(recordOrDefinitionId)
                    || entry.getDefinitionId().equals(recordOrDefinitionId))
                    && entry.resolve(System.currentTimeMillis())) {
                dirty = true;
                return true;
            }
        }
        return false;
    }

    /** 前情接入关闭被跳过的步骤，历史中标记为归档而非亲自完成。 */
    public static synchronized void archiveDefinitions(UUID playerId, Collection<String> definitionIds) {
        if (!loaded || !writesEnabled || playerId == null || definitionIds == null) {
            return;
        }
        for (GuidanceEntry entry : entriesFor(playerId)) {
            if (definitionIds.contains(entry.getDefinitionId()) && entry.archive(System.currentTimeMillis())) {
                dirty = true;
            }
        }
    }

    public static synchronized List<GuidanceViewData> getView(UUID playerId) {
        ensureLoaded();
        return entriesFor(playerId).stream()
                .sorted(Comparator.comparing((GuidanceEntry entry) -> entry.getStatus() != GuidanceEntry.Status.ACTIVE)
                        .thenComparing(Comparator.comparingLong(GuidanceEntry::getCreatedAtEpochMillis).reversed()))
                .limit(256)
                .map(GuidanceViewData::fromEntry)
                .toList();
    }

    /**
     * 返回已经收到指定个人引导的玩家 UUID 集合。
     *
     * <p>这只是曾分配名单，不代表完成，也不作为世界任务的解锁人数。记录状态
     * 无论是 ACTIVE、RESOLVED 还是 ARCHIVED 都算作曾经被分配过。</p>
     */
    public static synchronized Set<UUID> getPlayerIdsForDefinitions(
            Collection<String> definitionIds) {
        if (!loaded || definitionIds == null || definitionIds.isEmpty()) {
            return Set.of();
        }
        Set<String> wanted = definitionIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (wanted.isEmpty()) {
            return Set.of();
        }

        Set<UUID> result = new LinkedHashSet<>();
        PLAYER_ENTRIES.forEach((playerId, entries) -> {
            if (entries == null || entries.stream().noneMatch(
                    entry -> entry != null && wanted.contains(entry.getDefinitionId()))) {
                return;
            }
            try {
                result.add(UUID.fromString(playerId));
            } catch (IllegalArgumentException ignored) {
                // loadWorldData 已经过滤过非法 UUID；这里再防御一次，避免损坏快照阻断故事同步。
            }
        });
        return Collections.unmodifiableSet(result);
    }

    public static void syncToClient(ServerPlayer player) {
        DreamingFishCore_NetworkManager.sendToClient(
                player,
                new Packet_GuidanceSnapshotResponse(getView(player.getUUID())));
    }

    public static synchronized boolean saveIfDirty(MinecraftServer server) {
        if (!loaded || !dirty) {
            return true;
        }
        if (!writesEnabled) {
            DreamingFishCore.LOGGER.error("个人引导数据处于只读保护，拒绝覆盖损坏文件");
            return false;
        }
        try {
            JsonDataStore.writeAtomic(
                    WorldDataPaths.resolve(server, "guidance", "player_guidance.json"),
                    GSON,
                    PLAYER_ENTRIES);
            dirty = false;
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("写入个人引导数据失败，保留 dirty 状态等待下次保存", exception);
            return false;
        }
    }

    public static synchronized void clearWorldCache() {
        PLAYER_ENTRIES.clear();
        dirty = false;
        loaded = false;
        writesEnabled = false;
    }

    private static List<GuidanceEntry> entriesFor(UUID playerId) {
        return PLAYER_ENTRIES.computeIfAbsent(playerId.toString(), ignored -> new ArrayList<>());
    }

    private static boolean isValidSeed(GuidanceSeed seed) {
        return seed != null
                && !seed.getId().isBlank()
                && !seed.getTitle().isBlank()
                && !seed.getContent().isBlank();
    }

    private static boolean isValidEntry(GuidanceEntry entry) {
        return entry != null
                && !entry.getRecordId().isBlank()
                && entry.getRecordId().length() <= 64
                && !entry.getDefinitionId().isBlank()
                && entry.getDefinitionId().length() <= 160
                && entry.getSourceNpcName().length() <= 128
                && !entry.getTitle().isBlank()
                && entry.getTitle().length() <= 256
                && entry.getContent().length() <= 4096
                && entry.getSourceQuote().length() <= 4096
                && entry.getStoryStageId().length() <= 160
                && entry.getLocationLabel().length() <= 256
                && entry.getDimension().length() <= 160;
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("个人引导数据尚未随世界加载");
        }
    }
}
