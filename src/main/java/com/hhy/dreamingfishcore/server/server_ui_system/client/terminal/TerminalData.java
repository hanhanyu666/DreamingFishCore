package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.cache.ClientCacheManager;
import com.hhy.dreamingfishcore.client.cache.EconomyTerminalClientCache;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceEntry;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceViewData;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcConversationViewData;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageRecord;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageViewData;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryTaskData;
import com.hhy.dreamingfishcore.gameplay.story_system.network.Packet_WorldHistoryResponse;
import com.hhy.dreamingfishcore.server.notice_system.NoticeCategory;
import com.hhy.dreamingfishcore.server.notice_system.NoticeData;
import com.hhy.dreamingfishcore.server.notice_system.client.cache.NoticeClientCache;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 梦屿终端的客户端数据：公告与世界历史快照（由网络包写入），以及各页面共用的派生规则。
 *
 * <p>规则与旧终端保持一致：未开放的故事阶段不在终端出现；游戏公告只在其阶段开放后可见；
 * 历史记录由服务端筛选后下发。</p>
 */
public final class TerminalData {
    private static final DateTimeFormatter HISTORY_DATE = DateTimeFormatter.ofPattern("MM.dd HH:mm");
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    private static List<NoticeData> notices = List.of();
    private static Set<Integer> readNoticeIds = Set.of();
    private static List<Packet_WorldHistoryResponse.HistoryEntry> history = List.of();
    private static long historyTotal;
    private static boolean historyLoaded;
    private static boolean historyWritable;
    /** 公告与已读集合的版本号，界面据此判断是否需要重建。 */
    private static int noticeVersion;

    private TerminalData() {
    }

    // ==================== 网络包入口 ====================

    public static void setNoticeData(List<NoticeData> list, Set<Integer> readIds) {
        notices = list == null ? List.of() : List.copyOf(list.stream().filter(Objects::nonNull).toList());
        readNoticeIds = readIds == null ? Set.of() : Set.copyOf(readIds);
        noticeVersion++;
    }

    public static void setHistoryData(List<Packet_WorldHistoryResponse.HistoryEntry> entries, long total,
                                      boolean loaded, boolean writable) {
        history = entries == null ? List.of() : List.copyOf(entries);
        historyTotal = Math.max(0L, total);
        historyLoaded = loaded;
        historyWritable = writable;
    }

    /** 本地标记已读（服务端确认前先更新界面）。 */
    public static void markNoticeRead(int noticeId) {
        if (readNoticeIds.contains(noticeId)) {
            return;
        }
        Set<Integer> next = new HashSet<>(readNoticeIds);
        next.add(noticeId);
        readNoticeIds = Set.copyOf(next);
        NoticeClientCache.markRead(noticeId);
        noticeVersion++;
    }

    // ==================== 故事阶段 ====================

    /**
     * 玩家当前可见的故事阶段（当前阶段及之前），按阶段编号排序。
     * 当前阶段标记尚未同步时只展示编号最小的一个阶段。
     */
    public static Map<Integer, StoryStageData> visibleStages() {
        Map<Integer, StoryStageData> all = ClientCacheManager.getStoryStages();
        if (all == null || all.isEmpty()) {
            return Map.of();
        }
        int current = all.values().stream()
                .filter(stage -> stage != null && stage.isCurrentStage())
                .mapToInt(StoryStageData::getStageNumber)
                .filter(number -> number > 0)
                .min()
                .orElse(1);
        int maximum = Math.max(1, current);
        List<Map.Entry<Integer, StoryStageData>> entries = new ArrayList<>(all.entrySet());
        entries.removeIf(entry -> entry == null || entry.getValue() == null
                || entry.getValue().getStageNumber() <= 0
                || entry.getValue().getStageNumber() > maximum);
        entries.sort(Comparator.comparingInt((Map.Entry<Integer, StoryStageData> entry) -> entry.getValue().getStageNumber())
                .thenComparingInt(entry -> entry.getKey() == null ? Integer.MAX_VALUE : entry.getKey()));
        Map<Integer, StoryStageData> visible = new LinkedHashMap<>();
        for (Map.Entry<Integer, StoryStageData> entry : entries) {
            visible.put(entry.getKey(), entry.getValue());
        }
        if (visible.isEmpty()) {
            all.entrySet().stream()
                    .filter(entry -> entry != null && entry.getValue() != null)
                    .min(Comparator.comparingInt(entry -> entry.getValue().getStageNumber()))
                    .ifPresent(entry -> visible.put(entry.getKey(), entry.getValue()));
        }
        return Collections.unmodifiableMap(visible);
    }

    public static List<StoryStageData> visibleStageList() {
        List<StoryStageData> list = new ArrayList<>(visibleStages().values());
        list.removeIf(stage -> stage == null || safe(stage.getStageId(), "").isEmpty());
        list.sort(Comparator.comparingInt(StoryStageData::getStageNumber));
        return list;
    }

    public static StoryStageData currentStage() {
        Map<Integer, StoryStageData> all = ClientCacheManager.getStoryStages();
        if (all == null) {
            return null;
        }
        return all.values().stream().filter(stage -> stage != null && stage.isCurrentStage()).findFirst().orElse(null);
    }

    public static int currentVisibleStageNumber(Map<Integer, StoryStageData> stages) {
        return stages.values().stream()
                .filter(Objects::nonNull)
                .filter(StoryStageData::isCurrentStage)
                .mapToInt(StoryStageData::getStageNumber)
                .filter(number -> number > 0)
                .max()
                .orElseGet(() -> stages.values().stream()
                        .filter(Objects::nonNull)
                        .mapToInt(StoryStageData::getStageNumber)
                        .filter(number -> number > 0)
                        .max()
                        .orElse(1));
    }

    public static String stageLabel(StoryStageData stage) {
        if (stage == null) {
            return "未知阶段";
        }
        String number = stage.getStageNumber() > 0 ? String.valueOf(stage.getStageNumber()) : "?";
        String name = safe(stage.getStageName(), "");
        return name.isEmpty() ? "第" + number + "阶段" : "第" + number + "阶段 · " + name;
    }

    /** 当前阶段是否仍有本人未完成的任务（终端入口红点）。 */
    public static boolean hasOpenStoryProgress() {
        Map<Integer, StoryStageData> all = ClientCacheManager.getStoryStages();
        if (all == null) {
            return false;
        }
        return all.values().stream()
                .filter(stage -> stage != null && stage.isCurrentStage())
                .flatMap(stage -> stage.getTasks() == null ? java.util.stream.Stream.empty() : stage.getTasks().stream())
                .anyMatch(task -> task != null && !task.isClientPlayerFinished());
    }

    public static int totalTasks(Map<Integer, StoryStageData> stages) {
        return stages.values().stream().filter(Objects::nonNull)
                .mapToInt(stage -> stage.getTasks() == null ? 0 : stage.getTasks().size()).sum();
    }

    // ==================== 任务 ====================

    public static String taskStatusLabel(StoryTaskData task, boolean historicalStage) {
        if (task.isFailed()) {
            return historicalStage || task.isArchived() ? "已结束 · 失败" : "失败";
        }
        if (task.isPersonalTask() && task.isClientPlayerFinished()) {
            return "亲自完成";
        }
        if (!task.isPersonalTask() && task.isCompleted()) {
            return "世界已完成";
        }
        if (task.isWaived()) {
            return "已接入后续 · 无需补做";
        }
        if (historicalStage || task.isArchived()) {
            return "已归档 · 无需补做";
        }
        if (task.isPersonalTask() && !task.isTaskState()) {
            return "个人任务";
        }
        return task.isPersonalTask() ? "个人进行中" : "进行中";
    }

    public enum TaskTone {
        FAILED,
        DONE,
        MUTED,
        ACTIVE
    }

    public static TaskTone taskTone(StoryTaskData task, boolean historicalStage) {
        if (task.isFailed()) {
            return TaskTone.FAILED;
        }
        if (task.isClientPlayerFinished() || task.isCompleted()) {
            return TaskTone.DONE;
        }
        if (historicalStage || task.isArchived() || task.isWaived()) {
            return TaskTone.MUTED;
        }
        return TaskTone.ACTIVE;
    }

    /** 与任务对应的最新个人线索；进行中的线索优先。 */
    public static GuidanceViewData guidanceFor(StoryTaskData task, List<GuidanceViewData> entries) {
        if (task == null || entries == null || entries.isEmpty()) {
            return null;
        }
        List<String> linked = task.getGuidanceDefinitionIds();
        return entries.stream()
                .filter(Objects::nonNull)
                .filter(entry -> task.getTaskKey().equals(entry.definitionId()) || linked.contains(entry.definitionId()))
                .sorted(Comparator.comparing((GuidanceViewData entry) -> entry.status() != GuidanceEntry.Status.ACTIVE)
                        .thenComparing(Comparator.comparingLong(GuidanceViewData::createdAtEpochMillis).reversed()))
                .findFirst()
                .orElse(null);
    }

    // ==================== 公告 ====================

    public static int noticeVersion() {
        return noticeVersion;
    }

    public static List<NoticeData> notices() {
        return notices;
    }

    public static boolean isRead(NoticeData notice) {
        return notice != null && readNoticeIds.contains(notice.getNoticeId());
    }

    /** 游戏公告只在所属阶段开放后可见；服务器公告始终可见。 */
    public static boolean isVisible(NoticeData notice) {
        if (notice == null) {
            return false;
        }
        if (!notice.isGameNotice()) {
            return true;
        }
        String stageId = safe(notice.getStoryStageId(), "");
        if (stageId.isEmpty()) {
            return false;
        }
        return visibleStages().values().stream().anyMatch(stage -> stage != null && stageId.equals(stage.getStageId()));
    }

    public static List<NoticeData> noticesFor(NoticeCategory category, String stageId) {
        List<NoticeData> result = new ArrayList<>();
        for (NoticeData notice : notices) {
            if (!isVisible(notice) || notice.getCategory() != category) {
                continue;
            }
            if (category == NoticeCategory.GAME && stageId != null && !stageId.isEmpty()
                    && !stageId.equals(notice.getStoryStageId())) {
                continue;
            }
            result.add(notice);
        }
        return result;
    }

    public static int countNotices(NoticeCategory category) {
        int count = 0;
        for (NoticeData notice : notices) {
            if (isVisible(notice) && notice.getCategory() == category) {
                count++;
            }
        }
        return count;
    }

    public static int countUnread(NoticeCategory category) {
        int count = 0;
        for (NoticeData notice : notices) {
            if (isVisible(notice) && notice.getCategory() == category && !isRead(notice)) {
                count++;
            }
        }
        return count;
    }

    public static int unreadNotices() {
        int count = 0;
        for (NoticeData notice : notices) {
            if (isVisible(notice) && !isRead(notice)) {
                count++;
            }
        }
        return count;
    }

    public static NoticeData latestNotice() {
        return notices.stream().filter(TerminalData::isVisible)
                .max(Comparator.comparingLong(NoticeData::getPublishTime)).orElse(null);
    }

    public static NoticeData findNotice(int id) {
        for (NoticeData notice : notices) {
            if (notice.getNoticeId() == id) {
                return notice;
            }
        }
        return null;
    }

    public static String noticeStageLabel(NoticeData notice) {
        String stageId = notice == null ? "" : safe(notice.getStoryStageId(), "");
        if (stageId.isBlank()) {
            return "未指定";
        }
        Map<Integer, StoryStageData> stages = ClientCacheManager.getStoryStages();
        if (stages != null) {
            for (StoryStageData stage : stages.values()) {
                if (stage != null && stageId.equals(stage.getStageId())) {
                    return "第" + stage.getStageNumber() + "阶段 · " + safe(stage.getStageName(), "未命名");
                }
            }
        }
        return stageId;
    }

    // ==================== 历史 ====================

    public static List<Packet_WorldHistoryResponse.HistoryEntry> history() {
        return history;
    }

    public static long historyTotal() {
        return historyTotal;
    }

    public static boolean historyLoaded() {
        return historyLoaded;
    }

    public static boolean historyWritable() {
        return historyWritable;
    }

    public enum HistoryTone {
        STAGE,
        DISCUSSION,
        RESPONSE,
        TASK,
        SUCCESS,
        FAILURE,
        ENDING,
        OTHER
    }

    /** 历史事件的展示文案。 */
    public record HistoryView(String glyph, String title, String subtitle, HistoryTone tone) {
    }

    public static HistoryView describe(Packet_WorldHistoryResponse.HistoryEntry entry) {
        String subject = historySubject(entry.subjectId());
        String actor = historyActor(entry.actor());
        return switch (entry.type()) {
            case "STAGE_CHANGED" -> new HistoryView("章", "故事进入「" + subject + "」", actor + " 发布了新的世界阶段", HistoryTone.STAGE);
            case "OPERATION_ROUND_STARTED" -> new HistoryView("议", "一次公共讨论形成记录", "梦屿正在等待世界作出回应", HistoryTone.DISCUSSION);
            case "OPERATION_ROUND_PUBLISHED" -> new HistoryView("答", "世界回应了玩家的讨论", actor + " 发布了新的回应", HistoryTone.RESPONSE);
            case "TASK_PUBLISHED" -> new HistoryView("令", "新的行动「" + subject + "」已发布", "等待参与者前往现场", HistoryTone.TASK);
            case "TASK_SUCCEEDED" -> new HistoryView("成", "行动「" + subject + "」成功", participants(entry), HistoryTone.SUCCESS);
            case "TASK_FAILED" -> new HistoryView("失", "行动「" + subject + "」失败", participants(entry) + "，结果已写入历史", HistoryTone.FAILURE);
            case "ENDING_CHANGED" -> new HistoryView("终", "世界进入「" + subject + "」", "这个选择将长期留在梦屿", HistoryTone.ENDING);
            default -> new HistoryView("记", subject, actor, HistoryTone.OTHER);
        };
    }

    private static String participants(Packet_WorldHistoryResponse.HistoryEntry entry) {
        String count = entry.details().get("participantCount");
        return count == null ? "参与者共同完成了这次行动" : count + " 名参与者被记录在场";
    }

    private static String historySubject(String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            return "未命名事件";
        }
        Map<Integer, StoryStageData> stages = ClientCacheManager.getStoryStages();
        if (stages != null) {
            for (StoryStageData stage : stages.values()) {
                if (stage == null) {
                    continue;
                }
                if (subjectId.equals(stage.getStageId())) {
                    return stage.getStageName();
                }
                if (stage.getTasks() == null) {
                    continue;
                }
                for (StoryTaskData task : stage.getTasks()) {
                    if (task != null && subjectId.equals(task.getTaskKey())) {
                        return task.getTaskName();
                    }
                }
            }
        }
        int separator = subjectId.indexOf(':');
        String path = separator >= 0 ? subjectId.substring(separator + 1) : subjectId;
        return path.replace('_', ' ').replace('-', ' ');
    }

    private static String historyActor(String actor) {
        return actor == null || actor.isBlank() || "system".equalsIgnoreCase(actor) ? "梦屿系统" : actor;
    }

    // ==================== NPC 私信 ====================

    public static boolean isUnreadIncoming(NpcMessageViewData message) {
        return message != null && message.direction() == NpcMessageRecord.Direction.NPC_TO_PLAYER && !message.read();
    }

    public static boolean isOutgoing(NpcMessageViewData message) {
        return message.direction() == NpcMessageRecord.Direction.PLAYER_TO_NPC;
    }

    public static NpcMessageViewData latestReplySource(List<NpcMessageViewData> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            NpcMessageViewData message = messages.get(index);
            if (message.direction() == NpcMessageRecord.Direction.NPC_TO_PLAYER && !message.replied()
                    && !message.availableReplies().isEmpty()) {
                return message;
            }
        }
        return null;
    }

    public static String latestPreview(NpcConversationViewData conversation) {
        if (conversation.messages().isEmpty()) {
            return "暂无消息";
        }
        NpcMessageViewData latest = conversation.messages().get(conversation.messages().size() - 1);
        String prefix = isOutgoing(latest) ? "你: " : "";
        return prefix + latest.content().replace('\n', ' ');
    }

    // ==================== 经济 ====================

    public static String economyMetric(EconomyTerminalClientCache.Snapshot snapshot, int value) {
        if (!snapshot.loaded() || !snapshot.available() || !snapshot.compatible()) {
            return "--";
        }
        if (value >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (value >= 10_000) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }

    public static boolean economyReady(EconomyTerminalClientCache.Snapshot snapshot) {
        return snapshot.loaded() && snapshot.available() && snapshot.compatible();
    }

    public static String relationship(String relationship) {
        return switch (relationship == null ? "NONE" : relationship) {
            case "OWNER" -> "领主";
            case "MEMBER" -> "成员";
            default -> "";
        };
    }

    public static ItemStack marketItem(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    public static String marketItemName(String itemId) {
        ItemStack stack = marketItem(itemId);
        if (!stack.isEmpty()) {
            String name = stack.getHoverName().getString();
            if (!name.isBlank()) {
                return name;
            }
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        return id == null ? itemId : id.getPath().replace('_', ' ');
    }

    public static String marketExpiry(long expirationTime) {
        long remaining = Math.max(0L, expirationTime - System.currentTimeMillis());
        long minutes = remaining / 60_000L;
        if (minutes >= 60L) {
            return "剩余 " + (minutes / 60L) + "时" + (minutes % 60L) + "分";
        }
        return "剩余 " + minutes + "分";
    }

    // ==================== 格式化 ====================

    public static String historyDate(long epochMillis) {
        if (epochMillis <= 0L) {
            return "--";
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(HISTORY_DATE);
    }

    public static String fullDate(long epochMillis) {
        if (epochMillis <= 0L) {
            return "--";
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(FULL_DATE);
    }

    public static String playDuration(long millis) {
        if (millis <= 0) {
            return "0分";
        }
        long totalMinutes = millis / 60000L;
        long days = totalMinutes / 1440L;
        long hours = (totalMinutes % 1440L) / 60L;
        long minutes = totalMinutes % 60L;
        if (days > 0) {
            return days + "天" + hours + "时";
        }
        if (hours > 0) {
            return hours + "时" + minutes + "分";
        }
        return Math.max(1, minutes) + "分";
    }

    public static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
