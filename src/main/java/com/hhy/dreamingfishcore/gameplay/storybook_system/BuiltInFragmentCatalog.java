package com.hhy.dreamingfishcore.gameplay.storybook_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 内置剧情线索（残页）目录。
 *
 * <p>线索正文随模组一起分发，首次开服时写入
 * {@code config/dreamingfishcore/data/fragment_data.json}；之后以配置文件为准，
 * 服主可以直接编辑该 JSON 增删线索，不需要重新构建模组。</p>
 *
 * <p>本类只负责把 jar 内的文案读出来，不保存任何玩家状态。</p>
 */
public final class BuiltInFragmentCatalog {

    /** 与 {@code src/main/resources/dreamingfishcore/defaults/fragment_data.json} 对应。 */
    private static final String DEFAULT_RESOURCE = "/dreamingfishcore/defaults/fragment_data.json";

    private static final int MAX_FRAGMENT_ENTRIES = 16_384;
    private static final int MAX_FRAGMENT_TITLE_LENGTH = 512;
    private static final int MAX_FRAGMENT_CONTENT_LENGTH = 32_768;
    private static final int MAX_FRAGMENT_META_LENGTH = 256;

    private static final Type FRAGMENT_LIST_TYPE = new TypeToken<List<FragmentData>>() {}.getType();

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    private static final List<FragmentData> BUILT_IN_FRAGMENTS = new ArrayList<>();

    private static boolean loaded;
    private static boolean available;

    private BuiltInFragmentCatalog() {
    }

    /**
     * 读取 jar 内置线索。读取或校验失败时保持不可用，调用方应继续使用空配置，
     * 不能因为内置文案损坏而阻止服务器启动。
     */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;

        try (InputStream stream = BuiltInFragmentCatalog.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                DreamingFishCore.LOGGER.error("找不到内置线索资源：{}", DEFAULT_RESOURCE);
                return;
            }

            List<FragmentData> parsed;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                parsed = GSON.fromJson(reader, FRAGMENT_LIST_TYPE);
            }

            validate(parsed);

            BUILT_IN_FRAGMENTS.clear();
            BUILT_IN_FRAGMENTS.addAll(parsed);
            available = !BUILT_IN_FRAGMENTS.isEmpty();
            DreamingFishCore.LOGGER.info("内置线索已读取，共 {} 条", BUILT_IN_FRAGMENTS.size());
        } catch (Exception exception) {
            BUILT_IN_FRAGMENTS.clear();
            available = false;
            // 允许下一次世界加载时重试，避免一次读取失败永久失去内置文案。
            loaded = false;
            DreamingFishCore.LOGGER.error("内置线索读取失败，将保留空配置：{}", DEFAULT_RESOURCE, exception);
        }
    }

    /** 内置线索是否可用。 */
    public static synchronized boolean isAvailable() {
        return available;
    }

    /** 返回内置线索的独立副本，避免调用方修改静态内容。 */
    public static synchronized List<FragmentData> fragments() {
        return new ArrayList<>(BUILT_IN_FRAGMENTS);
    }

    /** 测试与热重载使用：清空已加载状态。 */
    static synchronized void reset() {
        BUILT_IN_FRAGMENTS.clear();
        loaded = false;
        available = false;
    }

    static List<FragmentData> parseForTest(String json) {
        List<FragmentData> parsed = GSON.fromJson(json, FRAGMENT_LIST_TYPE);
        validate(parsed);
        return parsed;
    }

    private static void validate(List<FragmentData> fragments) {
        if (fragments == null) {
            throw new IllegalStateException("内置线索根节点不是数组");
        }
        if (fragments.size() > MAX_FRAGMENT_ENTRIES) {
            throw new IllegalStateException("内置线索条目超过上限：" + MAX_FRAGMENT_ENTRIES);
        }

        Set<Integer> ids = new HashSet<>();
        for (FragmentData fragment : fragments) {
            if (fragment == null) {
                throw new IllegalStateException("内置线索包含空条目");
            }
            if (fragment.getId() <= 0 || !ids.add(fragment.getId())) {
                throw new IllegalStateException("内置线索 ID 非法或重复：" + fragment.getId());
            }
            if (fragment.getStageId() <= 0 || fragment.getChapterId() < 0) {
                throw new IllegalStateException("内置线索阶段 ID 必须为正数且章节 ID 不能为负数："
                        + fragment.getId());
            }
            requireLength(fragment.getAuthorName(), MAX_FRAGMENT_META_LENGTH, "authorName", fragment.getId());
            requireLength(fragment.getTime(), MAX_FRAGMENT_META_LENGTH, "time", fragment.getId());
            requireLength(fragment.getTitle(), MAX_FRAGMENT_TITLE_LENGTH, "title", fragment.getId());
            requireLength(fragment.getContent(), MAX_FRAGMENT_CONTENT_LENGTH, "content", fragment.getId());
        }
    }

    private static void requireLength(String value, int maxLength, String field, int fragmentId) {
        if (value == null || value.isEmpty() || value.length() > maxLength) {
            throw new IllegalStateException(
                    "内置线索 " + fragmentId + " 的 " + field + " 为空或过长（上限 " + maxLength + "）");
        }
    }
}
