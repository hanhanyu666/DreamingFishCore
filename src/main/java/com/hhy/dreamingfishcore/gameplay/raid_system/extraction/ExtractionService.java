package com.hhy.dreamingfishcore.gameplay.raid_system.extraction;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

/**
 * 撤离点服务：读配置、按本局种子选出一批撤离点，并写进对局记录。
 *
 * <p>配置走数据包目录（与锚点、物品表一致）：{@code data/<命名空间>/raid_extractions/*.json}。</p>
 *
 * <pre>
 * {
 *   "random_count": 2,                          // 本局随机开放几个（默认 2）
 *   "tags": ["factory_power_on"],               // 本局已满足的条件（暂时由服主写死，后续会改由事件驱动）
 *   "extractions": [ { "id": "...", ... } ]     // 也可以整个文件直接是数组
 * }
 * </pre>
 *
 * <p>出生点优先取本局第一个 {@code PLAYER_SPAWN} 锚点（按 id 排序），没有就用世界出生点——
 * 这样"撤离点离出生点多远"的规则在还没有出生组系统时也能生效。</p>
 */
public final class ExtractionService {

    public static final String DIR = "raid_extractions";
    public static final int DEFAULT_RANDOM_COUNT = 2;

    private static volatile List<ExtractionSelector.Candidate> candidates = List.of();
    private static volatile Set<String> activeTags = Set.of();
    private static volatile int randomCount = DEFAULT_RANDOM_COUNT;
    private static volatile List<String> problems = List.of();
    private static volatile ExtractionSelector.Result lastResult =
            new ExtractionSelector.Result(List.of(), List.of(), List.of());
    private static volatile boolean loaded;

    private ExtractionService() {
    }

    public static List<ExtractionSelector.Candidate> candidates() {
        return candidates;
    }

    public static Set<String> activeTags() {
        return activeTags;
    }

    public static int randomCount() {
        return randomCount;
    }

    public static List<String> problems() {
        return problems;
    }

    public static ExtractionSelector.Result lastResult() {
        return lastResult;
    }

    public static synchronized void clear() {
        candidates = List.of();
        activeTags = Set.of();
        randomCount = DEFAULT_RANDOM_COUNT;
        problems = List.of();
        lastResult = new ExtractionSelector.Result(List.of(), List.of(), List.of());
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
        List<ExtractionSelector.Candidate> parsed = new ArrayList<>();
        Set<String> tags = new TreeSet<>();
        List<String> found = new ArrayList<>();
        int count = DEFAULT_RANDOM_COUNT;

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
                JsonArray array = null;
                if (element != null && element.isJsonObject()) {
                    JsonObject root = element.getAsJsonObject();
                    if (root.has("random_count") && root.get("random_count").isJsonPrimitive()) {
                        count = Math.max(0, root.get("random_count").getAsInt());
                    }
                    if (root.has("tags") && root.get("tags").isJsonArray()) {
                        root.getAsJsonArray("tags").forEach(tag -> {
                            if (tag.isJsonPrimitive()) {
                                tags.add(tag.getAsString().trim().toLowerCase(Locale.ROOT));
                            }
                        });
                    }
                    JsonElement extractions = root.get("extractions");
                    array = extractions != null && extractions.isJsonArray() ? extractions.getAsJsonArray() : null;
                } else if (element != null && element.isJsonArray()) {
                    array = element.getAsJsonArray();
                }
                if (array == null) {
                    found.add("配置里没有 extractions 数组（已跳过）：" + id);
                    continue;
                }
                int index = 0;
                for (JsonElement item : array) {
                    index++;
                    if (!item.isJsonObject()) {
                        found.add("第 " + index + " 项不是对象（已跳过）：" + id);
                        continue;
                    }
                    ExtractionSelector.Candidate candidate = parse(item.getAsJsonObject(), found,
                            id + " 第 " + index + " 项");
                    if (candidate != null) {
                        parsed.add(candidate);
                    }
                }
            } catch (IOException | RuntimeException exception) {
                found.add("读取失败（已跳过）：" + id + " -> " + exception.getMessage());
            }
        }

        parsed.sort(Comparator.comparing(ExtractionSelector.Candidate::id));
        candidates = List.copyOf(parsed);
        activeTags = Set.copyOf(tags);
        randomCount = count;
        problems = List.copyOf(found);
        loaded = true;

        messages.add("撤离点配置已加载：候选 " + candidates.size() + " 个，本局条件 " + activeTags
                + "，随机开放 " + randomCount + " 个");
        if (!problems.isEmpty()) {
            messages.add("配置问题 " + problems.size() + " 条：");
            problems.forEach(problem -> messages.add("  " + problem));
        }
        DreamingFishCore.LOGGER.info("[raid_extraction] 加载完成：候选 {} 个，问题 {} 条",
                candidates.size(), problems.size());
        return messages;
    }

    private static ExtractionSelector.Candidate parse(JsonObject json, List<String> problems, String where) {
        JsonElement idJson = json.get("id");
        if (idJson == null || !idJson.isJsonPrimitive()) {
            problems.add(where + " 缺少 id（已跳过）");
            return null;
        }
        String id = idJson.getAsString().trim().toLowerCase(Locale.ROOT);
        ExtractionSelector.Kind kind;
        try {
            JsonElement kindJson = json.get("kind");
            kind = kindJson == null || !kindJson.isJsonPrimitive()
                    ? ExtractionSelector.Kind.RANDOM
                    : ExtractionSelector.Kind.valueOf(kindJson.getAsString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            problems.add(where + " kind 不是合法类型（已跳过）：" + json.get("kind"));
            return null;
        }

        double[] position = readPosition(json, problems, where);
        if (position == null) {
            return null;
        }
        return new ExtractionSelector.Candidate(id, kind,
                json.has("weight") ? Math.max(0, json.get("weight").getAsInt()) : 100,
                position[0], position[1],
                readStrings(json, "required_tags"),
                readStrings(json, "forbidden_spawn_groups"),
                json.has("minimum_distance_from_spawn") ? json.get("minimum_distance_from_spawn").getAsDouble() : 0.0D,
                json.has("maximum_distance_from_spawn") ? json.get("maximum_distance_from_spawn").getAsDouble() : 0.0D,
                json.has("max_uses") ? Math.max(0, json.get("max_uses").getAsInt()) : 0);
    }

    private static double[] readPosition(JsonObject json, List<String> problems, String where) {
        JsonElement position = json.get("position");
        if (position == null || !position.isJsonArray() || position.getAsJsonArray().size() < 3) {
            problems.add(where + " 缺少 position（需要 3 个数字，已跳过）");
            return null;
        }
        JsonArray array = position.getAsJsonArray();
        double x = array.get(0).getAsDouble();
        double z = array.get(2).getAsDouble();
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            problems.add(where + " 坐标不是有效数字（已跳过）");
            return null;
        }
        return new double[]{x, z};
    }

    private static Set<String> readStrings(JsonObject json, String key) {
        Set<String> values = new TreeSet<>();
        JsonElement element = json.get(key);
        if (element != null && element.isJsonArray()) {
            element.getAsJsonArray().forEach(item -> {
                if (item.isJsonPrimitive()) {
                    values.add(item.getAsString().trim().toLowerCase(Locale.ROOT));
                }
            });
        }
        return values;
    }

    /**
     * 按本局种子选出一批撤离点并写进对局记录（{@code activeExtractions}）。
     *
     * @return 给人看的摘要
     */
    public static synchronized List<String> selectAndRecord(MinecraftServer server) {
        List<String> messages = new ArrayList<>();
        if (server == null) {
            messages.add("服务器尚未就绪");
            return messages;
        }
        ensureLoaded(server);
        RaidAnchorService.ensureLoaded(server);

        RaidManifest manifest = RaidService.current().orElse(null);
        if (manifest == null) {
            messages.add("当前没有进行中的对局");
            return messages;
        }
        if (candidates.isEmpty()) {
            messages.add("没有撤离点候选：把配置放到 data/<命名空间>/" + DIR + "/ 下再 reload");
            return messages;
        }

        // 出生点：优先第一个 PLAYER_SPAWN 锚点（按 id 排序），否则世界出生点
        double spawnX;
        double spawnZ;
        String spawnGroup = "";
        List<RaidAnchor> spawns = RaidAnchorService.catalog().byType(RaidAnchorType.PLAYER_SPAWN);
        if (!spawns.isEmpty()) {
            RaidAnchor spawn = spawns.get(0);
            spawnX = spawn.x();
            spawnZ = spawn.z();
            spawnGroup = spawn.group();
        } else {
            BlockPos shared = server.overworld().getSharedSpawnPos();
            spawnX = shared.getX();
            spawnZ = shared.getZ();
        }

        ExtractionSelector.Rules rules = new ExtractionSelector.Rules(randomCount, activeTags,
                spawnGroup, spawnX, spawnZ);
        ExtractionSelector.Result result = ExtractionSelector.select(candidates, rules,
                manifest.randomFor("extractions"));
        lastResult = result;

        RaidService.replaceCurrent(server, manifest.withExtractions(new LinkedHashSet<>(result.all())));

        messages.add("本局撤离点 " + result.all().size() + " 个（保底 " + result.alwaysOpen().size() + "）：");
        messages.addAll(ExtractionSelector.describe(candidates, result));
        if (!result.rejections().isEmpty()) {
            messages.add("  未开放的 " + result.rejections().size() + " 个：");
            result.rejections().stream().limit(8)
                    .forEach(rejection -> messages.add("    " + rejection.id() + " [" + rejection.code()
                            + "] " + rejection.message()));
            if (result.rejections().size() > 8) {
                messages.add("    …（还有 " + (result.rejections().size() - 8) + " 条）");
            }
        }
        return messages;
    }

    /** 供命令打印配置总览。 */
    public static List<String> overview() {
        List<String> lines = new ArrayList<>();
        lines.add("撤离点候选 " + candidates.size() + " 个，本局条件 " + activeTags
                + "，随机开放 " + randomCount + " 个");
        candidates.forEach(candidate -> lines.add("  " + candidate.id() + "（" + candidate.kind()
                + "，权重 " + candidate.weight()
                + (candidate.requiredTags().isEmpty() ? "" : "，条件 " + candidate.requiredTags())
                + (candidate.maxUses() > 0 ? "，可用 " + candidate.maxUses() + " 次" : "") + "）"));
        if (!problems.isEmpty()) {
            lines.add("配置问题 " + problems.size() + " 条：");
            problems.forEach(problem -> lines.add("  " + problem));
        }
        return lines;
    }
}
