package com.hhy.dreamingfishcore.gameplay.story_system.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 当前开服剧情的文案目录。
 *
 * <p>文案与流程规则故意分离：策划可以修改这个文件里的句子和公告，而不会改变
 * Java 状态机的顺序、奖励或触发条件。文件损坏时只使用 JAR 内置副本，并禁止把
 * 只读副本写回运营文件。</p>
 */
public final class StoryTextCatalog {
    public static final String OPENING_STAGE_NAME = "opening.stage.name";
    public static final String OPENING_STAGE_DESCRIPTION = "opening.stage.description";
    public static final String OPENING_TASK_SETTLE_NAME = "opening.task.settle.name";
    public static final String OPENING_TASK_SETTLE_CONTENT = "opening.task.settle.content";
    public static final String OPENING_TASK_MEET_BAIZHI_NAME = "opening.task.meet_baizhi.name";
    public static final String OPENING_TASK_MEET_BAIZHI_CONTENT = "opening.task.meet_baizhi.content";
    public static final String OPENING_TASK_CHOOSE_NAME = "opening.task.choose.name";
    public static final String OPENING_TASK_CHOOSE_CONTENT = "opening.task.choose.content";
    public static final String OPENING_TASK_BUILD_NAME = "opening.task.build.name";
    public static final String OPENING_TASK_BUILD_CONTENT = "opening.task.build.content";
    public static final String OPENING_TRAVEL_GUIDANCE_TITLE = "opening.travel.guidance.title";
    public static final String OPENING_TRAVEL_GUIDANCE_CONTENT = "opening.travel.guidance.content";
    public static final String OPENING_TRAVEL_GUIDANCE_QUOTE = "opening.travel.guidance.quote";
    public static final String OPENING_TRAVEL_NOTIFICATION = "opening.travel.notification";
    public static final String OPENING_BAIZHI_GUIDANCE_TITLE = "opening.baizhi.guidance.title";
    public static final String OPENING_BAIZHI_GUIDANCE_CONTENT = "opening.baizhi.guidance.content";
    public static final String OPENING_BAIZHI_GUIDANCE_QUOTE = "opening.baizhi.guidance.quote";
    public static final String OPENING_CONTACT_GUIDANCE_TITLE = "opening.contact.guidance.title";
    public static final String OPENING_CONTACT_GUIDANCE_CONTENT = "opening.contact.guidance.content";
    public static final String OPENING_CONTACT_GUIDANCE_QUOTE = "opening.contact.guidance.quote";
    public static final String OPENING_CHOOSE_GUIDANCE_TITLE = "opening.choose.guidance.title";
    public static final String OPENING_CHOOSE_GUIDANCE_CONTENT = "opening.choose.guidance.content";
    public static final String OPENING_CHOOSE_GUIDANCE_QUOTE = "opening.choose.guidance.quote";
    public static final String OPENING_BUILD_GUIDANCE_TITLE = "opening.build.guidance.title";
    public static final String OPENING_BUILD_GUIDANCE_CONTENT = "opening.build.guidance.content";
    public static final String OPENING_BUILD_GUIDANCE_QUOTE = "opening.build.guidance.quote";
    public static final String OPENING_STARTER_NOTIFICATION = "opening.starter.notification";

    public static final String OPENING_BAIZHI_TALK = "opening.baizhi.dialogue.talk";
    public static final String OPENING_BAIZHI_CONTACT = "opening.baizhi.dialogue.contact";
    public static final String OPENING_BAIZHI_CHOOSE = "opening.baizhi.dialogue.choose";

    public static final String AFTERDREAM_PUBLIC_NOTICE_TITLE = "afterdream.notice.public.title";
    public static final String AFTERDREAM_PUBLIC_NOTICE_CONTENT = "afterdream.notice.public.content";
    public static final String AFTERDREAM_MASK_NOTICE_TITLE = "afterdream.notice.mask.title";
    public static final String AFTERDREAM_MASK_NOTICE_CONTENT = "afterdream.notice.mask.content";
    public static final String AFTERDREAM_EVOLUTION_NOTICE_TITLE = "afterdream.notice.evolution.title";
    public static final String AFTERDREAM_EVOLUTION_NOTICE_CONTENT = "afterdream.notice.evolution.content";
    public static final String AFTERDREAM_ZOMBIE_MEMORY_NOTICE_TITLE =
            "afterdream.notice.zombie_memory.title";
    public static final String AFTERDREAM_ZOMBIE_MEMORY_NOTICE_CONTENT =
            "afterdream.notice.zombie_memory.content";
    /**
     * 第二阶段后续公告的可选文案键。旧的运营文案文件可能尚未包含它们，
     * 所以阶段脚本会在缺失时回退到内置文本，不阻断故事状态写入。
     */
    public static final String AFTERDREAM_RECOVERY_RULES_NOTICE_TITLE =
            "afterdream.notice.recovery_rules.title";
    public static final String AFTERDREAM_RECOVERY_RULES_NOTICE_CONTENT =
            "afterdream.notice.recovery_rules.content";

    public static final String AFTERDREAM_JIANGWAN_COMMON = "afterdream.jiangwan.dialogue.common";
    public static final String AFTERDREAM_JIANGWAN_CHECKING = "afterdream.jiangwan.dialogue.checking";
    public static final String AFTERDREAM_JIANGWAN_LEVEL_ONE = "afterdream.jiangwan.dialogue.level_one";
    public static final String AFTERDREAM_JIANGWAN_NONINFECTED = "afterdream.jiangwan.dialogue.noninfected";
    public static final String AFTERDREAM_JIANGWAN_LEVEL_TWO = "afterdream.jiangwan.dialogue.level_two";
    public static final String AFTERDREAM_JIANGWAN_AWAITING = "afterdream.jiangwan.dialogue.awaiting";
    public static final String AFTERDREAM_JIANGWAN_COMPLETED = "afterdream.jiangwan.dialogue.completed";
    public static final String AFTERDREAM_JIANGWAN_MASK = "afterdream.jiangwan.dialogue.mask";
    public static final String AFTERDREAM_RECEPTION_GUIDANCE_TITLE = "afterdream.reception.guidance.title";
    public static final String AFTERDREAM_RECEPTION_GUIDANCE_CONTENT = "afterdream.reception.guidance.content";
    public static final String AFTERDREAM_RECEPTION_GUIDANCE_QUOTE = "afterdream.reception.guidance.quote";
    public static final String AFTERDREAM_ENTER_GUIDANCE_TITLE = "afterdream.enter.guidance.title";
    public static final String AFTERDREAM_ENTER_GUIDANCE_CONTENT = "afterdream.enter.guidance.content";
    public static final String AFTERDREAM_ENTER_GUIDANCE_QUOTE = "afterdream.enter.guidance.quote";
    public static final String AFTERDREAM_READ_GUIDANCE_TITLE = "afterdream.read.guidance.title";
    public static final String AFTERDREAM_READ_GUIDANCE_CONTENT = "afterdream.read.guidance.content";
    public static final String AFTERDREAM_READ_GUIDANCE_QUOTE = "afterdream.read.guidance.quote";
    public static final String AFTERDREAM_MASK_GUIDANCE_TITLE = "afterdream.mask.guidance.title";
    public static final String AFTERDREAM_MASK_GUIDANCE_CONTENT = "afterdream.mask.guidance.content";
    public static final String AFTERDREAM_MASK_GUIDANCE_QUOTE = "afterdream.mask.guidance.quote";
    public static final String AFTERDREAM_MASK_RECEIVED_NOTIFICATION = "afterdream.mask.notification";
    public static final String AFTERDREAM_INVENTORY_FULL = "afterdream.inventory.full";
    public static final String AFTERDREAM_STAGE_NAME = "afterdream.stage.name";
    public static final String AFTERDREAM_STAGE_DESCRIPTION = "afterdream.stage.description";
    public static final String AFTERDREAM_TASK_MESSAGE_NAME = "afterdream.task.message.name";
    public static final String AFTERDREAM_TASK_MESSAGE_CONTENT = "afterdream.task.message.content";
    public static final String AFTERDREAM_TASK_RECEPTION_NAME = "afterdream.task.reception.name";
    public static final String AFTERDREAM_TASK_RECEPTION_CONTENT = "afterdream.task.reception.content";
    public static final String AFTERDREAM_TASK_REVIEW_NAME = "afterdream.task.review.name";
    public static final String AFTERDREAM_TASK_REVIEW_CONTENT = "afterdream.task.review.content";
    public static final String AFTERDREAM_TASK_MASK_NAME = "afterdream.task.mask.name";
    public static final String AFTERDREAM_TASK_MASK_CONTENT = "afterdream.task.mask.content";

    private static final String DEFAULT_RESOURCE = "/dreamingfishcore/defaults/story_text.json";
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();
    private static final Set<String> REQUIRED_TEXT_KEYS = Set.of(
            OPENING_STAGE_NAME, OPENING_STAGE_DESCRIPTION,
            OPENING_TASK_SETTLE_NAME, OPENING_TASK_SETTLE_CONTENT,
            OPENING_TASK_MEET_BAIZHI_NAME, OPENING_TASK_MEET_BAIZHI_CONTENT,
            OPENING_TASK_CHOOSE_NAME, OPENING_TASK_CHOOSE_CONTENT,
            OPENING_TASK_BUILD_NAME, OPENING_TASK_BUILD_CONTENT,
            OPENING_TRAVEL_GUIDANCE_TITLE, OPENING_TRAVEL_GUIDANCE_CONTENT,
            OPENING_TRAVEL_GUIDANCE_QUOTE, OPENING_TRAVEL_NOTIFICATION,
            OPENING_BAIZHI_GUIDANCE_TITLE, OPENING_BAIZHI_GUIDANCE_CONTENT,
            OPENING_BAIZHI_GUIDANCE_QUOTE, OPENING_CONTACT_GUIDANCE_TITLE,
            OPENING_CONTACT_GUIDANCE_CONTENT, OPENING_CONTACT_GUIDANCE_QUOTE,
            OPENING_CHOOSE_GUIDANCE_TITLE, OPENING_CHOOSE_GUIDANCE_CONTENT,
            OPENING_CHOOSE_GUIDANCE_QUOTE, OPENING_BUILD_GUIDANCE_TITLE,
            OPENING_BUILD_GUIDANCE_CONTENT, OPENING_BUILD_GUIDANCE_QUOTE,
            OPENING_STARTER_NOTIFICATION, AFTERDREAM_PUBLIC_NOTICE_TITLE,
            AFTERDREAM_PUBLIC_NOTICE_CONTENT, AFTERDREAM_MASK_NOTICE_TITLE,
            AFTERDREAM_MASK_NOTICE_CONTENT, AFTERDREAM_EVOLUTION_NOTICE_TITLE,
            AFTERDREAM_EVOLUTION_NOTICE_CONTENT, AFTERDREAM_ZOMBIE_MEMORY_NOTICE_TITLE,
            AFTERDREAM_ZOMBIE_MEMORY_NOTICE_CONTENT, AFTERDREAM_RECEPTION_GUIDANCE_TITLE,
            AFTERDREAM_RECEPTION_GUIDANCE_CONTENT, AFTERDREAM_RECEPTION_GUIDANCE_QUOTE,
            AFTERDREAM_ENTER_GUIDANCE_TITLE, AFTERDREAM_ENTER_GUIDANCE_CONTENT,
            AFTERDREAM_ENTER_GUIDANCE_QUOTE,
            AFTERDREAM_READ_GUIDANCE_TITLE, AFTERDREAM_READ_GUIDANCE_CONTENT,
            AFTERDREAM_READ_GUIDANCE_QUOTE, AFTERDREAM_MASK_GUIDANCE_TITLE,
            AFTERDREAM_MASK_GUIDANCE_CONTENT, AFTERDREAM_MASK_GUIDANCE_QUOTE,
            AFTERDREAM_MASK_RECEIVED_NOTIFICATION, AFTERDREAM_INVENTORY_FULL,
            AFTERDREAM_STAGE_NAME, AFTERDREAM_STAGE_DESCRIPTION,
            AFTERDREAM_TASK_MESSAGE_NAME, AFTERDREAM_TASK_MESSAGE_CONTENT,
            AFTERDREAM_TASK_RECEPTION_NAME, AFTERDREAM_TASK_RECEPTION_CONTENT,
            AFTERDREAM_TASK_REVIEW_NAME, AFTERDREAM_TASK_REVIEW_CONTENT,
            AFTERDREAM_TASK_MASK_NAME, AFTERDREAM_TASK_MASK_CONTENT);
    private static final Set<String> REQUIRED_DIALOGUE_KEYS = Set.of(
            OPENING_BAIZHI_TALK, OPENING_BAIZHI_CONTACT, OPENING_BAIZHI_CHOOSE,
            AFTERDREAM_JIANGWAN_COMMON, AFTERDREAM_JIANGWAN_CHECKING,
            AFTERDREAM_JIANGWAN_LEVEL_ONE, AFTERDREAM_JIANGWAN_NONINFECTED,
            AFTERDREAM_JIANGWAN_LEVEL_TWO, AFTERDREAM_JIANGWAN_AWAITING,
            AFTERDREAM_JIANGWAN_COMPLETED, AFTERDREAM_JIANGWAN_MASK);

    private static final Map<String, String> TEXTS = new LinkedHashMap<>();
    private static final Map<String, List<String>> DIALOGUES = new LinkedHashMap<>();
    private static boolean loaded;
    private static boolean writable;
    private static long generation;

    private StoryTextCatalog() {
    }

    public static synchronized void loadWorldData() {
        clear();
        Path path = configPath();
        boolean existed = Files.exists(path);
        try {
            Document document = existed ? read(path) : bundled();
            boolean addedHospitalText = addMissingHospitalText(document);
            validate(document);
            install(document);
            writable = true;
            if (!existed || addedHospitalText) {
                JsonDataStore.writeAtomic(path, GSON, document);
            }
            loaded = true;
            DreamingFishCore.LOGGER.info("故事文案目录加载完成：{} 条文本、{} 组对白",
                    TEXTS.size(), DIALOGUES.size());
        } catch (Exception exception) {
            try {
                Document fallback = bundled();
                validate(fallback);
                install(fallback);
            } catch (RuntimeException fallbackFailure) {
                throw new IllegalStateException("内置故事文案也无法加载", fallbackFailure);
            }
            loaded = true;
            writable = false;
            DreamingFishCore.LOGGER.error("故事文案配置读取失败，使用只读内置文案：{}", path, exception);
        }
    }

    public static synchronized Summary validateDefinitions() {
        Document candidate;
        try {
            candidate = Files.exists(configPath()) ? read(configPath()) : bundled();
            validate(candidate);
        } catch (Exception exception) {
            throw new IllegalStateException("故事文案校验失败：" + exception.getMessage(), exception);
        }
        return new Summary(candidate.texts == null ? 0 : candidate.texts.size(),
                candidate.dialogues == null ? 0 : candidate.dialogues.size(), generation);
    }

    public static synchronized Summary reloadDefinitions() {
        if (!loaded) {
            loadWorldData();
        }
        if (!writable) {
            throw new IllegalStateException("故事文案处于只读保护，拒绝热重载");
        }
        Document candidate;
        try {
            candidate = Files.exists(configPath()) ? read(configPath()) : bundled();
            validate(candidate);
        } catch (Exception exception) {
            throw new IllegalStateException("故事文案校验失败：" + exception.getMessage(), exception);
        }
        install(candidate);
        return new Summary(TEXTS.size(), DIALOGUES.size(), generation);
    }

    public static synchronized String text(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return TEXTS.getOrDefault(key, key);
    }

    /** 阶段定义创建时的安全读取：内容目录尚未加载时使用代码内置默认文案。 */
    public static synchronized String textOrDefault(String key, String fallback) {
        String value = text(key);
        return value.equals(key) ? (fallback == null ? "" : fallback) : value;
    }

    public static synchronized List<String> dialogue(String key) {
        List<String> lines = DIALOGUES.get(key);
        return lines == null ? List.of() : List.copyOf(lines);
    }

    public static synchronized boolean isLoaded() {
        return loaded;
    }

    public static synchronized boolean isWritable() {
        return loaded && writable;
    }

    public static synchronized long generation() {
        return generation;
    }

    public static synchronized void clear() {
        TEXTS.clear();
        DIALOGUES.clear();
        loaded = false;
        writable = false;
        generation = 0L;
    }

    private static void install(Document document) {
        TEXTS.clear();
        DIALOGUES.clear();
        if (document.texts != null) {
            document.texts.forEach((key, value) -> {
                if (key != null && value != null) {
                    TEXTS.put(key, value);
                }
            });
        }
        if (document.dialogues != null) {
            document.dialogues.forEach((key, value) -> {
                if (key != null && value != null) {
                    DIALOGUES.put(key, List.copyOf(value));
                }
            });
        }
        generation = Math.max(1L, generation + 1L);
    }

    private static void validate(Document document) {
        if (document == null || document.schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("不支持的故事文案版本");
        }
        if (document.texts == null || document.dialogues == null) {
            throw new IllegalStateException("故事文案必须同时包含 texts 和 dialogues");
        }
        for (String key : REQUIRED_TEXT_KEYS) {
            if (document.texts.get(key) == null || document.texts.get(key).isBlank()) {
                throw new IllegalStateException("缺少故事文本：" + key);
            }
        }
        for (String key : REQUIRED_DIALOGUE_KEYS) {
            List<String> lines = document.dialogues.get(key);
            if (lines == null || lines.isEmpty() || lines.stream().anyMatch(line -> line == null || line.isBlank())) {
                throw new IllegalStateException("缺少故事对白：" + key);
            }
        }
    }

    private static Document read(Path path) throws Exception {
        return JsonDataStore.read(path, GSON, Document.class, Document::new);
    }

    private static Document bundled() {
        try (InputStream stream = StoryTextCatalog.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("未找到内置故事文案：" + DEFAULT_RESOURCE);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Document document = GSON.fromJson(reader, Document.class);
                if (document == null) {
                    throw new IllegalStateException("内置故事文案为空");
                }
                return document;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("读取内置故事文案失败", exception);
        }
    }

    /** 只补本次新增的医院文案，已有正文和旧必填字段不作覆盖或修复。 */
    private static boolean addMissingHospitalText(Document document) {
        if (document == null || document.texts == null) return false;
        boolean changed = false;
        for (var entry : bundled().texts.entrySet()) {
            if (entry.getKey().startsWith("hospital.") && !document.texts.containsKey(entry.getKey())) {
                document.texts.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        return changed;
    }

    private static Path configPath() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve(DreamingFishCore.MODID)
                    .resolve("story_text.json").toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            return Path.of("config", DreamingFishCore.MODID, "story_text.json")
                    .toAbsolutePath().normalize();
        }
    }

    public record Summary(int textCount, int dialogueCount, long generation) {
    }

    private static final class Document {
        private int schemaVersion = CURRENT_SCHEMA_VERSION;
        private Map<String, String> texts = new LinkedHashMap<>();
        private Map<String, List<String>> dialogues = new LinkedHashMap<>();

        private Document() {
        }
    }
}
