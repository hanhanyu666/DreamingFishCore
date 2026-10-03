package com.hhy.dreamingfishcore.gameplay.raid_system.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidManifest;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidRandom;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidService;
import com.hhy.dreamingfishcore.gameplay.raid_system.anchor.RaidAnchor;
import com.hhy.dreamingfishcore.gameplay.raid_system.anchor.RaidAnchorService;
import com.hhy.dreamingfishcore.gameplay.raid_system.anchor.RaidAnchorType;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 战利品配置加载 + 本局规划：把"物品价值表、区域模板、锚点、对局种子"四样东西接起来。
 *
 * <p>配置来源与锚点一致，走数据包目录（随地图分发、可审核）：</p>
 * <pre>
 * data/&lt;命名空间&gt;/raid_items/*.json   物品价值表
 * data/&lt;命名空间&gt;/raid_zones/*.json   区域模板
 * </pre>
 *
 * <p>容器类型从锚点标签里取：形如 {@code container:safe} 的标签表示这个点是保险箱，
 * 用于匹配物品的 {@code allowed_container_types}；没有这种标签就当作不限容器。</p>
 *
 * <p>规划结果**不写回 RaidManifest**（那需要改 manifest 结构，留到后面单独一刀）：
 * 摘要直接回显在命令里，明细写到存档目录的 {@code raid_plan_&lt;对局号&gt;.json}，
 * 这样既能看、也能对账，还不用动已经有测试覆盖的 manifest 格式。</p>
 */
public final class RaidLootService {

    public static final String ITEM_DIR = "raid_items";
    public static final String ZONE_DIR = "raid_zones";
    /** 锚点标签里表示容器类型的前缀。 */
    public static final String CONTAINER_TAG_PREFIX = "container:";

    private static final String SAVE_SUBDIR = "dreamingfishcore";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static volatile List<LootConfig.Item> items = List.of();
    private static volatile Map<String, LootConfig.Zone> zones = Map.of();
    private static volatile List<String> problems = List.of();
    private static volatile boolean loaded;
    private static volatile Map<String, RaidLootPlanner.ZonePlan> plan = Map.of();

    private RaidLootService() {
    }

    public static List<LootConfig.Item> items() {
        return items;
    }

    public static Map<String, LootConfig.Zone> zones() {
        return zones;
    }

    public static List<String> problems() {
        return problems;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static Map<String, RaidLootPlanner.ZonePlan> plan() {
        return plan;
    }

    public static synchronized void clear() {
        items = List.of();
        zones = Map.of();
        problems = List.of();
        plan = Map.of();
        loaded = false;
    }

    public static synchronized void ensureLoaded(MinecraftServer server) {
        if (!loaded && server != null) {
            reload(server);
        }
    }

    /** 重新读物品表与区域模板。 */
    public static synchronized List<String> reload(MinecraftServer server) {
        List<String> messages = new ArrayList<>();
        if (server == null) {
            messages.add("服务器尚未就绪");
            return messages;
        }
        List<String> found = new ArrayList<>();
        items = readItems(server, found);
        zones = readZones(server, found);
        problems = List.copyOf(found);
        loaded = true;
        plan = Map.of();

        messages.add("战利品配置已加载：物品 " + items.size() + " 条，区域模板 " + zones.size() + " 个");
        if (!problems.isEmpty()) {
            messages.add("配置问题 " + problems.size() + " 条：");
            problems.forEach(problem -> messages.add("  " + problem));
        }
        DreamingFishCore.LOGGER.info("[raid_loot] 加载完成：物品 {} 条，区域 {} 个，问题 {} 条",
                items.size(), zones.size(), problems.size());
        return messages;
    }

    /**
     * 用当前对局与本局锚点算出各区域的计划。
     *
     * <p>顺序按设计稿：先做全局稀有物品分配（第 10 步），再激活资源点并分配物品（第 11~14 步）。</p>
     */
    public static synchronized List<String> generatePlan(MinecraftServer server) {
        List<String> messages = new ArrayList<>();
        if (server == null) {
            messages.add("服务器尚未就绪");
            return messages;
        }
        ensureLoaded(server);
        RaidAnchorService.ensureLoaded(server);

        RaidManifest manifest = RaidService.current().orElse(null);
        if (manifest == null) {
            messages.add("当前没有进行中的对局：先用 /dreamingfish raid new <地图> 开一局");
            return messages;
        }
        if (zones.isEmpty()) {
            messages.add("没有任何区域模板：把区域配置放到 data/<命名空间>/raid_zones/ 下再 reload");
            return messages;
        }
        if (items.isEmpty()) {
            messages.add("物品价值表是空的：把物品配置放到 data/<命名空间>/raid_items/ 下再 reload");
            return messages;
        }

        RaidRandom rareRandom = manifest.randomFor("loot_rare");
        Map<String, Integer> globalRare = new TreeMap<>();
        // 第 10 步：全局稀有物品先分配，跨区域累计上限
        for (Map.Entry<String, LootConfig.Zone> entry : new TreeMap<>(zones).entrySet()) {
            LootAllocator.allocateGlobalRare(entry.getValue(), rareRandom, items, globalRare)
                    .forEach((itemId, count) -> globalRare.merge(itemId, count, Integer::sum));
        }

        Map<String, RaidLootPlanner.ZoneInput> inputs = new TreeMap<>();
        for (Map.Entry<String, LootConfig.Zone> entry : new TreeMap<>(zones).entrySet()) {
            LootConfig.Zone zone = entry.getValue();
            inputs.put(entry.getKey(), new RaidLootPlanner.ZoneInput(zone,
                    collectPoints(zone.zoneId(), RaidAnchorType.CONTAINER_LOOT),
                    zone.containerCount()));
        }

        plan = RaidLootPlanner.planRaid(inputs, items, manifest.randomFor("loot"), globalRare, 0, 0.0D);

        messages.add("对局 #" + manifest.raidId() + " 的战利品计划（种子 " + manifest.raidSeed() + "）：");
        for (RaidLootPlanner.ZonePlan zonePlan : plan.values()) {
            messages.add("  " + zonePlan.summary());
        }
        if (!globalRare.isEmpty()) {
            messages.add("  全局稀有物品配额：" + globalRare);
        }
        writePlan(server, manifest, messages);
        return messages;
    }

    /** 把某个区域里的候选锚点转成规划用的点。 */
    private static List<RaidLootPlanner.Point> collectPoints(String zoneId, RaidAnchorType type) {
        List<RaidLootPlanner.Point> points = new ArrayList<>();
        for (RaidAnchor anchor : RaidAnchorService.catalog().byZone(zoneId)) {
            if (anchor.type() != type || !anchor.enabled()) {
                continue;
            }
            points.add(new RaidLootPlanner.Point(anchor.id(), anchor.group(), anchor.weight(),
                    anchor.x(), anchor.z(), anchor.qualityMultiplier(), containerTypeOf(anchor)));
        }
        // 顺序确定：规划器内部也排序，这里先排一次便于日志阅读
        points.sort(Comparator.comparing(RaidLootPlanner.Point::anchorId));
        return points;
    }

    private static String containerTypeOf(RaidAnchor anchor) {
        for (String tag : anchor.tags()) {
            if (tag.startsWith(CONTAINER_TAG_PREFIX)) {
                return tag.substring(CONTAINER_TAG_PREFIX.length());
            }
        }
        return null;
    }

    private static void writePlan(MinecraftServer server, RaidManifest manifest, List<String> messages) {
        JsonObject root = new JsonObject();
        root.addProperty("raid_id", manifest.raidId());
        root.addProperty("raid_seed", manifest.raidSeed());
        JsonArray zoneArray = new JsonArray();
        for (RaidLootPlanner.ZonePlan zonePlan : plan.values()) {
            JsonObject zoneJson = new JsonObject();
            zoneJson.addProperty("zone", zonePlan.zoneId());
            zoneJson.addProperty("budget", zonePlan.zoneBudget());
            zoneJson.addProperty("spent", zonePlan.allocation().spentTotal());
            zoneJson.addProperty("active_points", zonePlan.activePointCount());
            JsonArray pointArray = new JsonArray();
            for (LootAllocator.PointAllocation point : zonePlan.allocation().points()) {
                JsonObject pointJson = new JsonObject();
                pointJson.addProperty("anchor", point.anchorId());
                pointJson.addProperty("budget", point.budget());
                pointJson.addProperty("spent", point.spent());
                JsonArray itemArray = new JsonArray();
                point.itemIds().forEach(itemArray::add);
                pointJson.add("items", itemArray);
                pointArray.add(pointJson);
            }
            zoneJson.add("points", pointArray);
            zoneArray.add(zoneJson);
        }
        root.add("zones", zoneArray);

        Path path = server.getWorldPath(LevelResource.ROOT).resolve(SAVE_SUBDIR)
                .resolve("raid_plan_" + manifest.raidId() + ".json");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
            messages.add("  明细已写入 " + path);
        } catch (IOException exception) {
            messages.add("  明细写入失败：" + exception.getMessage());
            DreamingFishCore.LOGGER.error("[raid_loot] 计划明细写入失败：{}", path, exception);
        }
    }

    // ---------------------------------------------------------------- 加载

    private static List<LootConfig.Item> readItems(MinecraftServer server, List<String> problems) {
        List<LootConfig.Item> result = new ArrayList<>();
        for (JsonObject json : readDir(server, ITEM_DIR, problems)) {
            LootConfig.ParseResult<LootConfig.Item> parsed = LootConfig.Item.fromJson(json);
            if (parsed.ok()) {
                result.add(parsed.value());
            } else {
                problems.add("物品条目解析失败（已跳过）：" + String.join("；", parsed.problems()));
            }
            parsed.problems().forEach(problem -> problems.add("物品条目提示：" + problem));
        }
        result.sort(Comparator.comparing(LootConfig.Item::itemId));
        return List.copyOf(result);
    }

    private static Map<String, LootConfig.Zone> readZones(MinecraftServer server, List<String> problems) {
        Map<String, LootConfig.Zone> result = new TreeMap<>();
        for (JsonObject json : readDir(server, ZONE_DIR, problems)) {
            LootConfig.ParseResult<LootConfig.Zone> parsed = LootConfig.Zone.fromJson(json);
            if (parsed.ok()) {
                result.put(parsed.value().zoneId(), parsed.value());
            } else {
                problems.add("区域模板解析失败（已跳过）：" + String.join("；", parsed.problems()));
            }
            parsed.problems().forEach(problem -> problems.add("区域模板提示：" + problem));
        }
        return Map.copyOf(result);
    }

    /**
     * 读一个目录下的全部 JSON 对象；单个文件坏了只跳过该文件。
     *
     * <p>遍历顺序按资源 id 排序，保证"同一份数据每次加载顺序一致"（确定性的前提）。</p>
     */
    private static List<JsonObject> readDir(MinecraftServer server, String directory, List<String> problems) {
        List<JsonObject> objects = new ArrayList<>();
        Map<ResourceLocation, Resource> resources;
        try {
            resources = server.getResourceManager()
                    .listResources(directory, path -> path.getPath().endsWith(".json"));
        } catch (RuntimeException exception) {
            problems.add("枚举 " + directory + " 失败：" + exception.getMessage());
            return objects;
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
                collectObjects(id, element, objects, problems);
            } catch (IOException | RuntimeException exception) {
                problems.add(directory + " 读取失败（已跳过）：" + id + " -> " + exception.getMessage());
            }
        }
        return objects;
    }

    /** 一个文件里可以只放一个对象、也可以放数组或 {@code {"items": [...]}} / {@code {"zones": [...]}}。 */
    static void collectObjects(ResourceLocation id, JsonElement element, List<JsonObject> objects,
                               List<String> problems) {
        if (element == null || element.isJsonNull()) {
            problems.add("空文件（已跳过）：" + id);
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String key : List.of("items", "zones", "entries")) {
                JsonElement array = object.get(key);
                if (array != null && array.isJsonArray()) {
                    for (JsonElement item : array.getAsJsonArray()) {
                        if (item.isJsonObject()) {
                            objects.add(item.getAsJsonObject());
                        } else {
                            problems.add(key + " 里有非对象项（已跳过）：" + id);
                        }
                    }
                    return;
                }
            }
            objects.add(object);
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (item.isJsonObject()) {
                    objects.add(item.getAsJsonObject());
                } else {
                    problems.add("数组里有非对象项（已跳过）：" + id);
                }
            }
            return;
        }
        problems.add("内容既不是对象也不是数组（已跳过）：" + id);
    }

    /** 供命令打印区域与物品总览。 */
    public static List<String> overview() {
        List<String> lines = new ArrayList<>();
        lines.add("物品价值表 " + items.size() + " 条，区域模板 " + zones.size() + " 个");
        Map<String, LootConfig.Zone> ordered = new LinkedHashMap<>(new TreeMap<>(zones));
        ordered.forEach((zoneId, zone) -> lines.add("  区域 " + zoneId + "（等级 " + zone.tier()
                + "，预算 " + zone.budget().min() + "~" + zone.budget().max()
                + "，容器点 " + zone.containerCount().min() + "~" + zone.containerCount().max() + "）"));
        items.stream().limit(20).forEach(item -> lines.add("  物品 " + item.itemId()
                + "（成本 " + item.spawnCost() + "，权重 " + item.rarityWeight()
                + (item.hasGlobalLimit() ? "，单局上限 " + item.raidGlobalLimit() : "") + "）"));
        if (items.size() > 20) {
            lines.add("  …（还有 " + (items.size() - 20) + " 条物品，用 export 或看配置文件）");
        }
        if (!problems.isEmpty()) {
            lines.add("配置问题 " + problems.size() + " 条：");
            problems.forEach(problem -> lines.add("  " + problem));
        }
        return lines;
    }
}
