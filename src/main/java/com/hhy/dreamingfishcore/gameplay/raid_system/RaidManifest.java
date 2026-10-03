package com.hhy.dreamingfishcore.gameplay.raid_system;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 一局生成的完整记录（设计稿 §14.4）。
 *
 * <p>必须持久化：**服务器重启后要读回它，而不是重新随机一次**。否则玩家会遇到
 * "重启后撤离点/资源点全变了"，而且举报与 bug 都无法复现。</p>
 *
 * <p>第 1 步只落地"对局身份 + 种子 + 本局启用了什么"这些骨架字段；资源点清单、露天物品清单、
 * 怪物与事件清单由后续步骤往 {@link #notes} 之外的专门字段里填（届时加字段即可，JSON 向后兼容）。</p>
 *
 * @param raidId              对局号（同一存档内递增）
 * @param raidSeed            本局主种子（{@link RaidRandom#raidSeed}）
 * @param mapId               地图模板 id
 * @param startedAtEpochMillis 开局时间（真实时间，仅供日志与运营查看）
 * @param playerCount         开局人数（设计稿 §9.3 用于资源量微调）
 * @param difficultyTier      难度档（由服主/玩法决定，第 1 步只记录与透传）
 * @param activeSpawnGroups   本局启用的出生组
 * @param activeVariants      本局启用的地图变体
 * @param activeExtractions   本局启用的撤离点
 * @param allocatedRareItems  全局稀有物品分配结果（id → 本局数量）
 * @param notes               人可读的生成摘要，方便复现与对账
 */
public record RaidManifest(long raidId,
                           long raidSeed,
                           String mapId,
                           long startedAtEpochMillis,
                           int playerCount,
                           int difficultyTier,
                           Set<String> activeSpawnGroups,
                           Set<String> activeVariants,
                           Set<String> activeExtractions,
                           Map<String, Integer> allocatedRareItems,
                           List<String> notes) {

    public RaidManifest {
        mapId = mapId == null ? "" : mapId;
        activeSpawnGroups = ordered(activeSpawnGroups);
        activeVariants = ordered(activeVariants);
        activeExtractions = ordered(activeExtractions);
        allocatedRareItems = allocatedRareItems == null
                ? Map.of() : new TreeMap<>(allocatedRareItems);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    private static Set<String> ordered(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(new java.util.TreeSet<>(values)));
    }

    /** 本局各子系统的随机源。调用方各自取自己那一条，互不影响。 */
    public RaidRandom randomFor(String system) {
        return new RaidRandom(raidSeed).forSystem(system);
    }

    public RaidManifest withNote(String note) {
        List<String> next = new ArrayList<>(notes);
        next.add(note);
        return new RaidManifest(raidId, raidSeed, mapId, startedAtEpochMillis, playerCount,
                difficultyTier, activeSpawnGroups, activeVariants, activeExtractions,
                allocatedRareItems, next);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("raid_id", raidId);
        json.addProperty("raid_seed", raidSeed);
        json.addProperty("map_id", mapId);
        json.addProperty("started_at", startedAtEpochMillis);
        json.addProperty("player_count", playerCount);
        json.addProperty("difficulty_tier", difficultyTier);
        json.add("active_spawn_groups", toArray(activeSpawnGroups));
        json.add("active_variants", toArray(activeVariants));
        json.add("active_extractions", toArray(activeExtractions));

        JsonObject rares = new JsonObject();
        allocatedRareItems.forEach(rares::addProperty);
        json.add("allocated_rare_items", rares);

        JsonArray noteArray = new JsonArray();
        notes.forEach(noteArray::add);
        json.add("notes", noteArray);
        return json;
    }

    public static RaidManifest fromJson(JsonObject json) {
        if (json == null) {
            return null;
        }
        try {
            Map<String, Integer> rares = new TreeMap<>();
            JsonElement raresJson = json.get("allocated_rare_items");
            if (raresJson != null && raresJson.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : raresJson.getAsJsonObject().entrySet()) {
                    rares.put(entry.getKey(), entry.getValue().getAsInt());
                }
            }
            return new RaidManifest(
                    json.get("raid_id").getAsLong(),
                    json.get("raid_seed").getAsLong(),
                    json.has("map_id") ? json.get("map_id").getAsString() : "",
                    json.has("started_at") ? json.get("started_at").getAsLong() : 0L,
                    json.has("player_count") ? json.get("player_count").getAsInt() : 1,
                    json.has("difficulty_tier") ? json.get("difficulty_tier").getAsInt() : 0,
                    readStrings(json.get("active_spawn_groups")),
                    readStrings(json.get("active_variants")),
                    readStrings(json.get("active_extractions")),
                    rares,
                    new ArrayList<>(readStrings(json.get("notes"))));
        } catch (RuntimeException exception) {
            // 坏文件不该让服务器起不来：返回 null，调用方按"没有进行中的对局"处理并记日志
            return null;
        }
    }

    private static JsonArray toArray(Set<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static Set<String> readStrings(JsonElement element) {
        Set<String> values = new LinkedHashSet<>();
        if (element != null && element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (item != null && item.isJsonPrimitive()) {
                    values.add(item.getAsString());
                }
            }
        }
        return values;
    }

    /** 供命令与日志打印。 */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        lines.add("对局 #" + raidId + "  地图 " + (mapId.isEmpty() ? "(未指定)" : mapId));
        lines.add("  主种子 " + raidSeed + "（0x" + Long.toHexString(raidSeed) + "）");
        lines.add("  人数 " + playerCount + "  难度档 " + difficultyTier
                + "  开局时间 " + startedAtEpochMillis);
        if (!activeSpawnGroups.isEmpty()) {
            lines.add("  出生组 " + String.join(", ", activeSpawnGroups));
        }
        if (!activeVariants.isEmpty()) {
            lines.add("  地图变体 " + String.join(", ", activeVariants));
        }
        if (!activeExtractions.isEmpty()) {
            lines.add("  撤离点 " + String.join(", ", activeExtractions));
        }
        if (!allocatedRareItems.isEmpty()) {
            lines.add("  稀有物品 " + allocatedRareItems);
        }
        notes.forEach(note -> lines.add("  " + note));
        return lines;
    }
}
