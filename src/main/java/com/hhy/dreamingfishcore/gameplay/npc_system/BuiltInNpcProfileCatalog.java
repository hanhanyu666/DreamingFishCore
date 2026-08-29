package com.hhy.dreamingfishcore.gameplay.npc_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Reads the complete opening NPC profiles bundled in the mod JAR. */
final class BuiltInNpcProfileCatalog {
    private static final String RESOURCE_PATH =
            "/dreamingfishcore/defaults/npc_data.json";
    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .create();
    private static final Type NPC_MAP_TYPE =
            new TypeToken<Map<Integer, NpcData>>() { }.getType();

    private BuiltInNpcProfileCatalog() {
    }

    /** Returns fresh profile objects so callers may safely persist them. */
    static Map<Integer, NpcData> loadProfiles() {
        try (InputStream stream = BuiltInNpcProfileCatalog.class
                .getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                DreamingFishCore.LOGGER.error("未找到内置 NPC 资料：{}", RESOURCE_PATH);
                return Map.of();
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Map<Integer, NpcData> parsed = GSON.fromJson(reader, NPC_MAP_TYPE);
                return parsed == null ? Map.of() : parsed;
            }
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("读取内置 NPC 资料失败：{}", RESOURCE_PATH, exception);
            return Map.of();
        }
    }
}
