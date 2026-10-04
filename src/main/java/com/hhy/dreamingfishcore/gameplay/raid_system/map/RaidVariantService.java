package com.hhy.dreamingfishcore.gameplay.raid_system.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidManifest;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidService;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

/**
 * 地图变体的配置加载与选择（设计稿 §6 的世界侧接线）。
 *
 * <p>配置走数据包目录：{@code data/<命名空间>/raid_maps/*.json}，一张地图一份：</p>
 *
 * <pre>
 * {
 *   "map": "abandoned_factory",
 *   "edges": ["residential-road", "road-factory", "factory-laboratory"],
 *   "spawn_nodes": ["residential", "forest"],
 *   "extraction_nodes": ["laboratory"],
 *   "must_reach": ["factory"],
 *   "groups": [
 *     { "id": "factory_north_entrance", "choose": 1,
 *       "variants": [
 *         { "id": "open",    "weight": 40 },
 *         { "id": "blocked", "weight": 30, "disabled_edges": ["road-factory"] },
 *         { "id": "breached","weight": 10, "enabled_edges": ["forest-warehouse"] }
 *       ] }
 *   ]
 * }
 * </pre>
 *
 * <p>解析软失败：缺 map 名或整份文件坏了只跳过该文件；单个变体组坏了只跳过那一组。
 * 选中结果写进 {@link RaidManifest#withVariants} 并立即落盘，所以 {@code /dreamingfish raid info}
 * 就能看到本局地图状态。</p>
 */
public final class RaidVariantService {

    public static final String DIR = "raid_maps";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 一张地图的变体定义。 */
    public record MapDefinition(String mapId, RaidMapVariants.Graph graph,
                                Set<String> spawnNodes, Set<String> extractionNodes,
                                Set<String> mustReach, List<RaidMapVariants.Group> groups) {

        public MapDefinition {
            spawnNodes = spawnNodes == null ? Set.of() : Set.copyOf(new TreeSet<>(spawnNodes));
            extractionNodes = extractionNodes == null ? Set.of() : Set.copyOf(new TreeSet<>(extractionNodes));
            mustReach = mustReach == null ? Set.of() : Set.copyOf(new TreeSet<>(mustReach));
            groups = groups == null ? List.of() : List.copyOf(groups);
        }
    }

    private static volatile Map<String, MapDefinition> definitions = Map.of();
    private static volatile List<String> problems = List.of();
    private static volatile boolean loaded;
    private static volatile RaidMapVariants.Outcome lastOutcome;

    private RaidVariantService() {
    }

    public static Map<String, MapDefinition> definitions() {
        return definitions;
    }

    public static List<String> problems() {
        return problems;
    }

    public static RaidMapVariants.Outcome lastOutcome() {
        return lastOutcome;
    }

    public static synchronized void clear() {
        definitions = Map.of();
        problems = List.of();
        lastOutcome = null;
        loaded = false;
    }

    public static synchronized void ensureLoaded(MinecraftServer server) {
        if (!loaded && server != null) {
            reload(server);
        }
    }

    public static synchronized List<String> reload(MinecraftServer server) {
        List<String> messages = new ArrayList<>();
        if (server == null) {
            messages.add("服务器尚未就绪");
            return messages;
        }
        List<String> found = new ArrayList<>();
        Map<String, MapDefinition> parsed = new TreeMap<>();

        Map<ResourceLocation, Resource> resources;
        try {
            resources = server.getResourceManager()
                    .listResources(DIR, path -> path.getPath().endsWith(".json"));
        } catch (RuntimeException exception) {
            problems = List.of("枚举 " + DIR + " 失败：" + exception.getMessage());
            messages.add(problems.get(0));
            return messages;
        }

        List<ResourceLocation> ids = new ArrayList<>(resources.keySet());
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation id : ids) {
            Resource resource = resources.get(id);
            if (resource == null) {
                continue;
            }
            try (Reader reader = resource.openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element == null || !element.isJsonObject()) {
                    found.add("不是 JSON 对象（已跳过）：" + id);
                    continue;
                }
                MapDefinition definition = parse(element.getAsJsonObject(), found, id.toString());
                if (definition != null) {
                    parsed.put(definition.mapId(), definition);
                }
            } catch (IOException | RuntimeException exception) {
                found.add("读取失败（已跳过）：" + id + " -> " + exception.getMessage());
            }
        }

        definitions = Map.copyOf(parsed);
        problems = List.copyOf(found);
        loaded = true;
        messages.add("地图变体配置已加载：地图 " + definitions.size() + " 张");
        if (!problems.isEmpty()) {
            messages.add("配置问题 " + problems.size() + " 条：");
            problems.forEach(problem -> messages.add("  " + problem));
        }
        DreamingFishCore.LOGGER.info("[raid_variant] 加载完成：地图 {} 张，问题 {} 条",
                definitions.size(), problems.size());
        return messages;
    }

    /**
     * 为当前对局选地图变体并写进对局记录。
     *
     * @return 给人看的摘要
     */
    public static synchronized List<String> selectAndRecord(MinecraftServer server) {
        List<String> messages = new ArrayList<>();
        ensureLoaded(server);
        RaidManifest manifest = server == null ? null : RaidService.current().orElse(null);
        if (manifest == null) {
            messages.add("当前没有进行中的对局");
            return messages;
        }
        MapDefinition definition = definitions.get(manifest.mapId());
        if (definition == null) {
            messages.add("没有地图 " + manifest.mapId() + " 的变体配置（在 data/<命名空间>/" + DIR
                    + "/ 下建一份，文件名与地图 id 对应即可）");
            return messages;
        }

        // 抽到合格为止（上限 20 次），都不合格就退回无变体
        RaidMapVariants.Outcome outcome = RaidMapVariants.selectValid(definition.groups(),
                definition.graph(), definition.spawnNodes(), definition.extractionNodes(),
                definition.mustReach(), manifest.randomFor("variants"), 20);
        lastOutcome = outcome;

        RaidService.replaceCurrent(server,
                manifest.withVariants(new LinkedHashSet<>(outcome.selection().chosenByGroup().values())));
        messages.addAll(RaidMapVariants.describe(outcome));
        return messages;
    }

    /** 供命令打印总览。 */
    public static List<String> overview() {
        List<String> lines = new ArrayList<>();
        lines.add("地图变体配置 " + definitions.size() + " 张");
        definitions.forEach((mapId, definition) -> {
            lines.add("  " + mapId + "：节点 " + definition.graph().nodes().size()
                    + "，边 " + definition.graph().nodes().stream()
                    .mapToInt(node -> definition.graph().openNeighbors(node, RaidMapVariants.Selection.empty()).size())
                    .sum() / 2
                    + "，变体组 " + definition.groups().size()
                    + "，出生区 " + definition.spawnNodes()
                    + "，撤离区 " + definition.extractionNodes());
            definition.groups().forEach(group -> lines.add("    组 " + group.id() + "（选 " + group.choose()
                    + "）：" + group.variants().stream().map(RaidMapVariants.Variant::id).toList()));
        });
        if (!problems.isEmpty()) {
            lines.add("配置问题 " + problems.size() + " 条：");
            problems.forEach(problem -> lines.add("  " + problem));
        }
        return lines;
    }

    private static MapDefinition parse(JsonObject json, List<String> problems, String where) {
        JsonElement mapJson = json.get("map");
        if (mapJson == null || !mapJson.isJsonPrimitive()) {
            problems.add(where + " 缺少 map（地图 id，要与 raid new 时用的名字一致），已跳过");
            return null;
        }
        String mapId = mapJson.getAsString().trim();
        RaidMapVariants.Graph graph = RaidMapVariants.Graph.of(readStrings(json, "edges").toArray(new String[0]));

        List<RaidMapVariants.Group> groups = new ArrayList<>();
        JsonElement groupsJson = json.get("groups");
        if (groupsJson != null && groupsJson.isJsonArray()) {
            for (JsonElement element : groupsJson.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    problems.add(where + " 的 groups 里有非对象项，已跳过");
                    continue;
                }
                RaidMapVariants.Group group = parseGroup(element.getAsJsonObject(), problems, where);
                if (group != null) {
                    groups.add(group);
                }
            }
        }
        return new MapDefinition(mapId, graph, readStrings(json, "spawn_nodes"),
                readStrings(json, "extraction_nodes"), readStrings(json, "must_reach"), groups);
    }

    private static RaidMapVariants.Group parseGroup(JsonObject json, List<String> problems, String where) {
        JsonElement idJson = json.get("id");
        if (idJson == null || !idJson.isJsonPrimitive()) {
            problems.add(where + " 里有变体组缺少 id，已跳过");
            return null;
        }
        String groupId = idJson.getAsString().trim();
        int choose = json.has("choose") && json.get("choose").isJsonPrimitive()
                ? Math.max(1, json.get("choose").getAsInt()) : 1;
        List<RaidMapVariants.Variant> variants = new ArrayList<>();
        JsonElement variantsJson = json.get("variants");
        if (variantsJson != null && variantsJson.isJsonArray()) {
            for (JsonElement element : variantsJson.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    problems.add(where + " 的 " + groupId + " 里有非对象变体，已跳过");
                    continue;
                }
                JsonObject variantJson = element.getAsJsonObject();
                JsonElement variantId = variantJson.get("id");
                if (variantId == null || !variantId.isJsonPrimitive()) {
                    problems.add(where + " 的 " + groupId + " 里有变体缺少 id，已跳过");
                    continue;
                }
                variants.add(new RaidMapVariants.Variant(variantId.getAsString().trim(),
                        variantJson.has("weight") && variantJson.get("weight").isJsonPrimitive()
                                ? variantJson.get("weight").getAsInt() : 100,
                        readStrings(variantJson, "disabled_edges"),
                        readStrings(variantJson, "enabled_edges")));
            }
        }
        if (variants.isEmpty()) {
            problems.add(where + " 的变体组 " + groupId + " 没有可用变体，已跳过");
            return null;
        }
        return new RaidMapVariants.Group(groupId, choose, variants);
    }

    private static Set<String> readStrings(JsonObject json, String key) {
        Set<String> values = new TreeSet<>();
        JsonElement element = json.get(key);
        if (element != null && element.isJsonArray()) {
            element.getAsJsonArray().forEach(item -> {
                if (item.isJsonPrimitive()) {
                    String value = item.getAsString().trim();
                    if (!value.isEmpty()) {
                        values.add(value);
                    }
                }
            });
        }
        return values;
    }

    /** 便于将来把配置导出成示例（服主可以拿一张真实地图的配置当模板）。 */
    public static String exportExample() {
        JsonObject root = new JsonObject();
        root.addProperty("map", "abandoned_factory");
        JsonArray edges = new JsonArray();
        List.of("residential-road", "residential-forest", "road-factory",
                "forest-warehouse", "warehouse-factory", "factory-laboratory").forEach(edges::add);
        root.add("edges", edges);
        JsonArray spawns = new JsonArray();
        List.of("residential", "forest").forEach(spawns::add);
        root.add("spawn_nodes", spawns);
        JsonArray extractions = new JsonArray();
        extractions.add("laboratory");
        root.add("extraction_nodes", extractions);
        return GSON.toJson(root);
    }
}
