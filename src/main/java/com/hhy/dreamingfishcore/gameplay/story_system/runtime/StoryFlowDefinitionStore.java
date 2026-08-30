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
import java.util.HashSet;
import java.util.Set;

/** 负责故事流程 JSON 的读写与结构校验，不执行 Minecraft 效果。 */
public final class StoryFlowDefinitionStore {
    private static final String DEFAULT_RESOURCE =
            "/dreamingfishcore/defaults/story_flows.json";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    private StoryFlowDefinitionStore() {
    }

    /** 读取服主配置；文件缺失时从 JAR 内唯一模板写出一份可直接编辑的流程。 */
    public static StoryFlowDefinitionDocument readOrCreate() throws Exception {
        Path path = getConfigPath();
        if (Files.notExists(path)) {
            StoryFlowDefinitionDocument defaults = createDefaultDocument();
            JsonDataStore.writeAtomic(path, GSON, defaults);
            return defaults;
        }
        if (Files.size(path) == 0L) {
            throw new IllegalStateException("故事流程配置为空，已拒绝覆盖原文件：" + path);
        }
        StoryFlowDefinitionDocument document = JsonDataStore.read(
                path, GSON, StoryFlowDefinitionDocument.class,
                StoryFlowDefinitionDocument::new);
        if (document == null) {
            throw new IllegalStateException("故事流程配置为空：" + path);
        }
        return document;
    }

    /** 只读取候选配置，不修改当前运行索引。 */
    public static StoryFlowDefinitionDocument readCandidate() {
        try {
            return readOrCreate();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "故事流程配置读取失败：" + exception.getMessage(), exception);
        }
    }

    public static void validate(
            StoryFlowDefinitionDocument document,
            Set<String> availableStageIds) {
        if (document == null) {
            throw new IllegalStateException("故事流程文档不能为空");
        }
        if (document.getSchemaVersion() != StoryFlowDefinitionDocument.CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException(
                    "不支持的故事流程配置版本：" + document.getSchemaVersion()
                            + "，请将 story_flows.json 更新为 schemaVersion "
                            + StoryFlowDefinitionDocument.CURRENT_SCHEMA_VERSION);
        }
        if (document.getFlows().isEmpty()) {
            throw new IllegalStateException("故事流程文档至少需要一个流程");
        }
        Set<String> flowIds = new HashSet<>();
        for (StoryFlowDefinition flow : document.getFlows()) {
            if (flow == null || !flowIds.add(flow.getId())) {
                throw new IllegalStateException("故事流程 ID 重复或为空");
            }
            flow.validate(availableStageIds == null ? Set.of() : availableStageIds);
        }
    }

    public static Summary summary(StoryFlowDefinitionDocument document) {
        int nodes = document == null
                ? 0
                : document.getFlows().stream()
                        .filter(flow -> flow != null)
                        .mapToInt(flow -> flow.getNodes().size())
                        .sum();
        return new Summary(
                document == null ? 0 : document.getSchemaVersion(),
                document == null ? 0 : document.getFlows().size(),
                nodes);
    }

    public static Path getConfigPath() {
        try {
            Path configDirectory = FMLPaths.CONFIGDIR.get();
            if (configDirectory != null) {
                return configDirectory
                        .resolve(DreamingFishCore.MODID)
                        .resolve("story_flows.json")
                        .toAbsolutePath()
                        .normalize();
            }
        } catch (RuntimeException ignored) {
            // Plain JVM unit tests do not initialize FMLPaths; use a local fallback.
        }
        return Path.of("config", DreamingFishCore.MODID, "story_flows.json")
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 返回 JAR 内流程模板的新对象。流程内容只有 JSON 一份事实来源，Java 不保留平行副本。
     */
    public static StoryFlowDefinitionDocument createDefaultDocument() {
        return loadBundledDefault();
    }

    /** 配置损坏时读取只读内置模板；资源缺失属于打包错误，必须明确失败。 */
    static StoryFlowDefinitionDocument loadBundledDefault() {
        try (InputStream stream = StoryFlowDefinitionStore.class
                .getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("未找到内置故事流程模板：" + DEFAULT_RESOURCE);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                StoryFlowDefinitionDocument document =
                        GSON.fromJson(reader, StoryFlowDefinitionDocument.class);
                if (document == null) {
                    throw new IllegalStateException("内置故事流程模板为空：" + DEFAULT_RESOURCE);
                }
                return document;
            }
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "读取内置故事流程模板失败：" + DEFAULT_RESOURCE, exception);
        }
    }

    public record Summary(int schemaVersion, int flowCount, int nodeCount) {
    }
}
