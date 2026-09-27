package com.hhy.dreamingfishcore.gameplay.npc_message_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.npc_system.StoryNpcContentPolicy;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 内置故事私信内容。
 *
 * <p>NPC 私信配置属于服主可编辑内容，因此这里采用“只补缺失 ID”的方式。
 * 这样已有服务器可以保留自己的文案，新版本只会增加本次开场需要的内容。</p>
 */
public final class BuiltInNpcMessageCatalog {
    private static final String BUILT_IN_MESSAGE_RESOURCE =
            "/dreamingfishcore/defaults/npc_messages.json";
    private static final Gson GSON = new GsonBuilder().create();

    private BuiltInNpcMessageCatalog() {
    }

    /**
     * 返回现有列表中缺失的内置消息；不会修改传入列表。
     */
    public static List<NpcMessageDefinition> createMissingMessages(
            List<NpcMessageDefinition> existing) {
        Set<String> existingIds = new HashSet<>();
        if (existing != null) {
            for (NpcMessageDefinition definition : existing) {
                if (definition != null) {
                    existingIds.add(definition.getId());
                }
            }
        }

        List<NpcMessageDefinition> additions = new ArrayList<>();
        List<NpcMessageDefinition> builtInMessages = loadCompleteMessages();
        for (NpcMessageDefinition definition : builtInMessages) {
            if (definition != null) {
                addIfMissing(existingIds, additions, definition);
            }
        }
        return additions;
    }

    /** 读取 JAR 内的完整 NPC 私信集；返回的新对象可安全写入服主配置。 */
    static List<NpcMessageDefinition> loadCompleteMessages() {
        try (InputStream stream = BuiltInNpcMessageCatalog.class
                .getResourceAsStream(BUILT_IN_MESSAGE_RESOURCE)) {
            if (stream == null) {
                DreamingFishCore.LOGGER.error(
                        "未找到内置 NPC 私信：{}", BUILT_IN_MESSAGE_RESOURCE);
                return List.of();
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                NpcMessageConfig config = GSON.fromJson(reader, NpcMessageConfig.class);
                if (config == null || config.getMessages().isEmpty()) {
                    DreamingFishCore.LOGGER.error(
                            "内置 NPC 私信为空：{}", BUILT_IN_MESSAGE_RESOURCE);
                    return List.of();
                }
                List<NpcMessageDefinition> retained = new ArrayList<>();
                for (NpcMessageDefinition definition : config.getMessages()) {
                    if (definition != null
                            && StoryNpcContentPolicy.isRetainedMessage(
                            definition.getNpcId(), definition.getId())) {
                        retained.add(definition);
                    }
                }
                return retained;
            }
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error(
                    "读取内置 NPC 私信失败：{}", BUILT_IN_MESSAGE_RESOURCE, exception);
            return List.of();
        }
    }

    private static void addIfMissing(
            Set<String> existingIds,
            List<NpcMessageDefinition> additions,
            NpcMessageDefinition definition) {
        if (existingIds.add(definition.getId())) {
            additions.add(definition);
        }
    }

}
