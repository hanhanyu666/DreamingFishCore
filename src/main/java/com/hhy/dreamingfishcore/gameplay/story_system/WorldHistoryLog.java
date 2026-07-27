package com.hhy.dreamingfishcore.gameplay.story_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.server.MinecraftServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 梦屿世界的只追加重大事件日志。
 *
 * <p>普通世界状态使用一个会被反复覆盖的 JSON 文件保存“现在是什么样”。本类使用 JSONL：
 * 文件中的每一行都是一个独立 JSON 对象，只在文件末尾增加新行，用来回答“世界为什么变成现在这样”。
 * 阶段切换、任务结算和运营内容发布都会留下记录，未来可直接用于服务器年表和官网导出。</p>
 *
 * <p>历史文件只追加、不重写。如果读取时发现序号中断或某一行损坏，本次服务器会话会禁止继续
 * 写入历史文件，避免用新内容掩盖损坏位置。故事状态本身仍可继续工作，但管理员状态命令会显示
 * 历史日志已经进入只读保护。</p>
 */
public final class WorldHistoryLog {
    /** 历史事件自身的结构版本，独立于 StoryWorldState 和故事定义版本。 */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final String[] HISTORY_PATH = {"story", "world_history.jsonl"};
    /** 内存只保留最近的少量事件；磁盘文件仍保留完整历史。 */
    private static final int RECENT_EVENT_LIMIT = 200;
    private static final int MAX_DETAIL_ENTRIES = 32;
    private static final int MAX_TEXT_LENGTH = 1024;

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private static final List<HistoryEvent> recentEvents = new ArrayList<>();
    private static Path historyPath;
    private static long nextSequence = 1L;
    private static boolean loaded;
    private static boolean writesEnabled;

    private WorldHistoryLog() {
    }

    /** 在服务器启动时解析当前世界对应的历史文件。 */
    public static synchronized void loadWorldData(MinecraftServer server) {
        load(WorldDataPaths.resolve(server, HISTORY_PATH[0], HISTORY_PATH[1]));
    }

    /**
     * 实际加载实现单独接收 Path，既方便服务器生命周期调用，也方便单元测试使用临时目录。
     */
    static synchronized void load(Path path) {
        clearWorldCache();
        historyPath = path.toAbsolutePath().normalize();

        if (Files.notExists(historyPath)) {
            loaded = true;
            writesEnabled = true;
            return;
        }

        long expectedSequence = 1L;
        try (BufferedReader reader = Files.newBufferedReader(historyPath, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                HistoryEvent event = GSON.fromJson(line, HistoryEvent.class);
                validateLoadedEvent(event, expectedSequence, lineNumber);
                remember(event);
                expectedSequence++;
            }
            nextSequence = expectedSequence;
            loaded = true;
            writesEnabled = true;
            DreamingFishCore.LOGGER.info(
                    "世界历史日志加载完成：{} 条事件，下一序号={}，文件={}",
                    expectedSequence - 1L, nextSequence, historyPath);
        } catch (Exception exception) {
            nextSequence = expectedSequence;
            loaded = true;
            writesEnabled = false;
            DreamingFishCore.LOGGER.error(
                    "世界历史日志损坏，本次会话禁止继续追加：{}", historyPath, exception);
        }
    }

    /**
     * 立即把一条事件追加到磁盘。
     *
     * <p>这里没有 dirty 标记，因为历史事件不能等到下一次自动保存：如果阶段刚切换服务器就崩溃，
     * 延迟写入会丢掉解释这次变化的记录。FileChannel.force(true) 会请求操作系统把本次追加刷到磁盘。</p>
     *
     * @return 事件成功写入时返回 true；日志未加载或已进入只读保护时返回 false
     */
    public static synchronized boolean append(
            long activeTick,
            EventType type,
            String subjectId,
            String actor,
            Map<String, String> details) {
        if (!loaded || !writesEnabled || historyPath == null) {
            return false;
        }

        HistoryEvent event = new HistoryEvent(
                CURRENT_SCHEMA_VERSION,
                nextSequence,
                activeTick,
                Instant.now().toEpochMilli(),
                type,
                subjectId == null ? "" : subjectId,
                normalizeActor(actor),
                copyAndValidateDetails(details));
        validateLoadedEvent(event, nextSequence, -1);

        try {
            Path parent = historyPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            byte[] encoded = (GSON.toJson(event) + "\n").getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(
                    historyPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND)) {
                ByteBuffer buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            remember(event);
            nextSequence++;
            return true;
        } catch (IOException | RuntimeException exception) {
            writesEnabled = false;
            DreamingFishCore.LOGGER.error(
                    "追加世界历史事件失败，后续历史写入已暂停：{}", historyPath, exception);
            return false;
        }
    }

    /** 返回最近事件的不可变副本，调用者无法修改内部缓存。 */
    public static synchronized List<HistoryEvent> getRecentEvents(int limit) {
        int safeLimit = Math.max(0, Math.min(limit, RECENT_EVENT_LIMIT));
        int fromIndex = Math.max(0, recentEvents.size() - safeLimit);
        return List.copyOf(recentEvents.subList(fromIndex, recentEvents.size()));
    }

    /** 返回适合管理员命令显示的日志状态。 */
    public static synchronized Status getStatus() {
        return new Status(
                Math.max(0L, nextSequence - 1L),
                recentEvents.size(),
                loaded,
                writesEnabled,
                historyPath == null ? "" : historyPath.toString());
    }

    /** 停服后清空静态字段，避免下一个世界继承上一个世界的路径和序号。 */
    public static synchronized void clearWorldCache() {
        recentEvents.clear();
        historyPath = null;
        nextSequence = 1L;
        loaded = false;
        writesEnabled = false;
    }

    /** 内存缓存超过上限时只丢弃最旧的缓存项，不会删除磁盘历史。 */
    private static void remember(HistoryEvent event) {
        recentEvents.add(event);
        if (recentEvents.size() > RECENT_EVENT_LIMIT) {
            recentEvents.remove(0);
        }
    }

    private static void validateLoadedEvent(HistoryEvent event, long expectedSequence, int lineNumber) {
        String location = lineNumber > 0 ? "第 " + lineNumber + " 行" : "待写入事件";
        if (event == null) {
            throw new IllegalStateException(location + "为空");
        }
        if (event.schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException(location + "使用了不支持的历史版本：" + event.schemaVersion);
        }
        if (event.sequence != expectedSequence) {
            throw new IllegalStateException(
                    location + "历史序号不连续：期望 " + expectedSequence + "，实际 " + event.sequence);
        }
        if (event.activeTick < 0L || event.recordedAtEpochMillis <= 0L) {
            throw new IllegalStateException(location + "包含非法时间");
        }
        if (event.type == null) {
            throw new IllegalStateException(location + "缺少事件类型");
        }
        if (event.subjectId == null) {
            throw new IllegalStateException(location + "缺少事件对象");
        }
        if (!event.subjectId.isEmpty()) {
            StoryWorldState.requireValidId(event.subjectId, "历史事件对象");
        }
        normalizeActor(event.actor);
        event.details = copyAndValidateDetails(event.details);
    }

    private static String normalizeActor(String actor) {
        String normalized = actor == null ? "system" : actor.strip();
        if (normalized.isEmpty() || normalized.length() > 128) {
            throw new IllegalArgumentException("历史事件执行者不能为空且不能超过 128 个字符");
        }
        return normalized;
    }

    private static Map<String, String> copyAndValidateDetails(Map<String, String> details) {
        if (details == null || details.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (details.size() > MAX_DETAIL_ENTRIES) {
            throw new IllegalArgumentException("历史事件详细字段超过限制：" + details.size());
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : details.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank() || key.length() > 64) {
                throw new IllegalArgumentException("历史事件详细字段名非法");
            }
            if (value == null || value.length() > MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException("历史事件详细字段值非法：" + key);
            }
            copy.put(key, value);
        }
        return copy;
    }

    /** 重大事件类型。名称会直接写入 JSONL，因此已经发布后不要随意改名。 */
    public enum EventType {
        STAGE_CHANGED,
        WORLD_FLAG_CHANGED,
        OPERATION_ROUND_STARTED,
        OPERATION_ROUND_PUBLISHED,
        TASK_PUBLISHED,
        TASK_SUCCEEDED,
        TASK_FAILED,
        ENDING_CHANGED,
        CONTENT_RELOADED
    }

    /**
     * 一行历史记录的数据结构。字段不提供 setter，外部只能读取，不能修改已加载的历史。
     */
    public static final class HistoryEvent {
        private int schemaVersion;
        private long sequence;
        private long activeTick;
        private long recordedAtEpochMillis;
        private EventType type;
        private String subjectId;
        private String actor;
        private Map<String, String> details = new LinkedHashMap<>();

        private HistoryEvent() {
        }

        private HistoryEvent(
                int schemaVersion,
                long sequence,
                long activeTick,
                long recordedAtEpochMillis,
                EventType type,
                String subjectId,
                String actor,
                Map<String, String> details) {
            this.schemaVersion = schemaVersion;
            this.sequence = sequence;
            this.activeTick = activeTick;
            this.recordedAtEpochMillis = recordedAtEpochMillis;
            this.type = type;
            this.subjectId = subjectId;
            this.actor = actor;
            this.details = details;
        }

        public int getSchemaVersion() {
            return schemaVersion;
        }

        public long getSequence() {
            return sequence;
        }

        public long getActiveTick() {
            return activeTick;
        }

        public long getRecordedAtEpochMillis() {
            return recordedAtEpochMillis;
        }

        public EventType getType() {
            return type;
        }

        public String getSubjectId() {
            return subjectId;
        }

        public String getActor() {
            return actor;
        }

        public Map<String, String> getDetails() {
            return Collections.unmodifiableMap(details);
        }
    }

    /** 管理员查询使用的简短状态。 */
    public record Status(
            long eventCount,
            int cachedEventCount,
            boolean loaded,
            boolean writesEnabled,
            String path) {
    }
}
