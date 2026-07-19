package com.hhy.dreamingfishcore.core.npc_system;

import com.hhy.dreamingfishcore.utils.Utf8JsonFileIO;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NpcRelationManager {
    private static final File RELATION_DATA_FILE = new File("config/dreamingfishcore/data/npc_relation_data.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Type RELATION_MAP_TYPE = new TypeToken<Map<String, NpcRelationData>>() {}.getType();
    private static final Type LEGACY_RELATION_LIST_TYPE = new TypeToken<List<NpcRelationData>>() {}.getType();

    private static Map<String, NpcRelationData> relationCache = new ConcurrentHashMap<>();

    public static void init() {
        ensureFile();
        load();
    }

    public static NpcRelationData getRelation(int npcId, UUID playerUUID) {
        String key = makeKey(npcId, playerUUID);
        return relationCache.computeIfAbsent(key, ignored -> new NpcRelationData(npcId, playerUUID));
    }

    public static void addFavorability(int npcId, UUID playerUUID, int amount) {
        NpcRelationData relation = getRelation(npcId, playerUUID);
        relation.addFavorability(amount);
        save();
    }

    public static boolean canUseAction(int npcId, UUID playerUUID, NpcInteractionType interactionType, int requiredFavorability) {
        return getRelation(npcId, playerUUID).getFavorability() >= requiredFavorability;
    }

    public static void load() {
        ensureFile();
        try (Reader reader = Utf8JsonFileIO.openReader(RELATION_DATA_FILE)) {
            JsonElement root = JsonParser.parseReader(reader);
            relationCache = new ConcurrentHashMap<>();

            if (root == null || root.isJsonNull()) {
                save();
            } else if (root.isJsonObject()) {
                Map<String, NpcRelationData> loaded = GSON.fromJson(root, RELATION_MAP_TYPE);
                if (loaded != null) {
                    loaded.forEach((key, data) -> putIfValid(key, data));
                }
            } else if (root.isJsonArray()) {
                List<NpcRelationData> legacyRelations = GSON.fromJson(root, LEGACY_RELATION_LIST_TYPE);
                if (legacyRelations != null) {
                    for (NpcRelationData relation : legacyRelations) {
                        putIfValid(relation);
                    }
                }
                save();
            } else {
                DreamingFishCore.LOGGER.warn("NPC关系数据格式无效，已重置: {}", RELATION_DATA_FILE.getPath());
                save();
            }

            relationCache.values().forEach(NpcRelationData::refreshRelationType);
            DreamingFishCore.LOGGER.info("NPC关系数据加载完成，共 {} 条", relationCache.size());
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            relationCache = new ConcurrentHashMap<>();
            DreamingFishCore.LOGGER.error("加载NPC关系数据失败", e);
            save();
        }
    }

    public static void save() {
        ensureFile();
        try (Writer writer = Utf8JsonFileIO.openWriter(RELATION_DATA_FILE)) {
            GSON.toJson(relationCache, writer);
        } catch (IOException e) {
            DreamingFishCore.LOGGER.error("保存NPC关系数据失败", e);
        }
    }

    private static String makeKey(int npcId, UUID playerUUID) {
        return npcId + ":" + playerUUID;
    }

    private static void putIfValid(NpcRelationData relation) {
        if (relation == null || relation.getNpcId() <= 0 || relation.getTargetPlayerUUID() == null) {
            return;
        }
        putIfValid(makeKey(relation.getNpcId(), relation.getTargetPlayerUUID()), relation);
    }

    private static void putIfValid(String key, NpcRelationData relation) {
        if (key == null || relation == null || relation.getNpcId() <= 0 || relation.getTargetPlayerUUID() == null) {
            return;
        }
        relationCache.put(key, relation);
    }

    private static void ensureFile() {
        try {
            File parent = RELATION_DATA_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (!RELATION_DATA_FILE.exists()) {
                RELATION_DATA_FILE.createNewFile();
            }
        } catch (IOException e) {
            DreamingFishCore.LOGGER.error("初始化NPC关系数据文件失败", e);
        }
    }
}
