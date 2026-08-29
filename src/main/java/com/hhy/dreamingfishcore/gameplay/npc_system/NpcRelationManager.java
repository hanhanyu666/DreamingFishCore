package com.hhy.dreamingfishcore.gameplay.npc_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import com.hhy.dreamingfishcore.server.persistence.WorldDataPaths;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NpcRelationManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final Type RELATION_MAP_TYPE = new TypeToken<Map<String, NpcRelationData>>() {}.getType();
    private static final Map<String, NpcRelationData> RELATION_CACHE = new ConcurrentHashMap<>();

    private static boolean dirty;
    private static boolean loaded;

    public static void loadWorldData(MinecraftServer server) {
        RELATION_CACHE.clear();
        try {
            Map<String, NpcRelationData> loadedData = JsonDataStore.read(
                    WorldDataPaths.resolve(server, "npc", "relations.json"),
                    GSON,
                    RELATION_MAP_TYPE,
                    ConcurrentHashMap::new);
            loadedData.forEach(NpcRelationManager::putIfValid);
            RELATION_CACHE.values().forEach(NpcRelationData::refreshRelationType);
            dirty = false;
            DreamingFishCore.LOGGER.info("NPC关系数据加载完成，共 {} 条", RELATION_CACHE.size());
        } catch (Exception exception) {
            dirty = false;
            DreamingFishCore.LOGGER.error("读取世界 NPC 关系数据失败，本次会话不会覆盖损坏文件", exception);
        } finally {
            loaded = true;
        }
    }

    public static NpcRelationData getRelation(int npcId, UUID playerUUID) {
        ensureLoaded();
        String key = makeKey(npcId, playerUUID);
        NpcRelationData existing = RELATION_CACHE.get(key);
        if (existing != null) {
            return existing;
        }
        NpcRelationData created = new NpcRelationData(npcId, playerUUID);
        NpcRelationData raced = RELATION_CACHE.putIfAbsent(key, created);
        if (raced == null) {
            dirty = true;
            return created;
        }
        return raced;
    }

    public static void addFavorability(int npcId, UUID playerUUID, int amount) {
        NpcRelationData relation = getRelation(npcId, playerUUID);
        relation.addFavorability(amount);
        dirty = true;
    }

    public static boolean applyFavorabilityEffect(
            int npcId,
            UUID playerUUID,
            String effectId,
            int amount) {
        NpcRelationData relation = getRelation(npcId, playerUUID);
        if (!relation.applyFavorabilityEffect(effectId, amount)) {
            return false;
        }
        dirty = true;
        return true;
    }

    public static boolean canUseAction(int npcId, UUID playerUUID,
                                       NpcInteractionType interactionType, int requiredFavorability) {
        return getRelation(npcId, playerUUID).getFavorability() >= requiredFavorability;
    }

    /**
     * 兼容旧调用；运行期间保存请求只需要标脏。
     */
    public static void save() {
        ensureLoaded();
        dirty = true;
    }

    public static boolean saveIfDirty(MinecraftServer server) {
        if (!loaded || !dirty) {
            return true;
        }
        try {
            JsonDataStore.writeAtomic(
                    WorldDataPaths.resolve(server, "npc", "relations.json"),
                    GSON,
                    RELATION_CACHE);
            dirty = false;
            return true;
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.error("写入世界 NPC 关系数据失败，保留 dirty 状态等待下次保存", exception);
            return false;
        }
    }

    public static void clearWorldCache() {
        RELATION_CACHE.clear();
        dirty = false;
        loaded = false;
    }

    private static String makeKey(int npcId, UUID playerUUID) {
        return npcId + ":" + playerUUID;
    }

    private static void putIfValid(String key, NpcRelationData relation) {
        if (key == null || relation == null || relation.getNpcId() <= 0
                || relation.getTargetPlayerUUID() == null) {
            return;
        }
        RELATION_CACHE.put(key, relation);
    }

    private static void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("NPC关系数据尚未随世界加载");
        }
    }
}
