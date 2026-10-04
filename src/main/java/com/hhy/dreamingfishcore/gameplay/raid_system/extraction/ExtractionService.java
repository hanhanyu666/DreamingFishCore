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
import java.util.TreeMap;
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
    /** 撤离点的完整位置与维度，用来做粒子标识（Candidate 只带 x/z）。 */
    private static volatile Map<String, MarkerPoint> markers = Map.of();
    /** 粒子标识的节流：每多少 tick 喷一次。 */
    private static final int MARKER_INTERVAL_TICKS = 10;
    private static int markerTick;

    /**
     * 粒子标识用的点。
     *
     * @param id        撤离点 id
     * @param dimension 所在维度（配置里可写，默认主世界）
     * @param kind      类型（不同类型用不同粒子，方便一眼区分保底/随机/限次）
     */
    public record MarkerPoint(String id, String dimension, ExtractionSelector.Kind kind,
                              double x, double y, double z) {
    }

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
        markers = Map.of();
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
        Map<String, MarkerPoint> markerDraft = new TreeMap<>();
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
                            id + " 第 " + index + " 项", markerDraft);
                    if (candidate != null) {
                        parsed.add(candidate);
                    }
                }
            } catch (IOException | RuntimeException exception) {
                found.add("读取失败（已跳过）：" + id + " -> " + exception.getMessage());
            }
        }

        // 主要来源是**锚点系统**（EXTRACTION 类型）：在游戏里用准心放置 + 区域校验，
        // 比手写坐标可靠得多（手写坐标最容易"埋进石头里"，粒子看不见、人也走不到）。
        // 配置文件里的 extractions 仍然支持，作为补充一起合并；同一个 id 以锚点为准。
        RaidAnchorService.ensureLoaded(server);
        for (RaidAnchor anchor : RaidAnchorService.catalog().byType(RaidAnchorType.EXTRACTION)) {
            if (!anchor.enabled()) {
                continue;
            }
            ExtractionSelector.Candidate candidate = fromAnchor(anchor);
            parsed.add(candidate);
            markerDraft.put(candidate.id(), new MarkerPoint(candidate.id(),
                    RaidAnchorZoneLookup.dimensionOf(anchor.zone()).orElse("minecraft:overworld"),
                    candidate.kind(), anchor.x(), anchor.y(), anchor.z()));
        }

        // 去重：后进的覆盖先进的（锚点在后，所以锚点优先）
        Map<String, ExtractionSelector.Candidate> byId = new TreeMap<>();
        parsed.forEach(candidate -> byId.put(candidate.id(), candidate));
        parsed = new ArrayList<>(byId.values());

        parsed.sort(Comparator.comparing(ExtractionSelector.Candidate::id));
        candidates = List.copyOf(parsed);
        activeTags = Set.copyOf(tags);
        randomCount = count;
        problems = List.copyOf(found);
        markers = Map.copyOf(markerDraft);
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

    private static ExtractionSelector.Candidate parse(JsonObject json, List<String> problems, String where,
                                                      Map<String, MarkerPoint> markers) {
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
        recordMarker(json, id, kind, markers);
        return new ExtractionSelector.Candidate(id, kind,
                json.has("weight") ? Math.max(0, json.get("weight").getAsInt()) : 100,
                position[0], position[1],
                readStrings(json, "required_tags"),
                readStrings(json, "forbidden_spawn_groups"),
                json.has("minimum_distance_from_spawn") ? json.get("minimum_distance_from_spawn").getAsDouble() : 0.0D,
                json.has("maximum_distance_from_spawn") ? json.get("maximum_distance_from_spawn").getAsDouble() : 0.0D,
                json.has("max_uses") ? Math.max(0, json.get("max_uses").getAsInt()) : 0);
    }

    /** 记下粒子标识要用的完整位置（含 Y 与维度）——Candidate 只带 x/z，不够喷粒子。 */
    private static void recordMarker(JsonObject json, String id, ExtractionSelector.Kind kind,
                                     Map<String, MarkerPoint> markers) {
        JsonElement position = json.get("position");
        if (position == null || !position.isJsonArray() || position.getAsJsonArray().size() < 3) {
            return;
        }
        JsonArray array = position.getAsJsonArray();
        double y = array.get(1).getAsDouble();
        String dimension = "minecraft:overworld";
        JsonElement dimensionJson = json.get("dimension");
        if (dimensionJson != null && dimensionJson.isJsonPrimitive()) {
            dimension = dimensionJson.getAsString().trim();
        }
        markers.put(id, new MarkerPoint(id, dimension, kind, array.get(0).getAsDouble(), y,
                array.get(2).getAsDouble()));
    }

    /** 本局开放的撤离点（含位置与维度），供粒子标识使用。 */
    public static List<MarkerPoint> activeMarkers() {
        List<MarkerPoint> active = new ArrayList<>();
        for (String id : lastResult.all()) {
            MarkerPoint marker = markers.get(id);
            if (marker != null) {
                active.add(marker);
            }
        }
        return active;
    }

    /**
     * 每 tick 调一次：给本局开放的撤离点喷粒子标识。
     *
     * <p>用服务端粒子（不新增自定义包、不动协议版本）：不同类型用不同粒子，
     * 玩家一眼就能分清保底 / 随机 / 条件 / 限次。节流到每 {@value #MARKER_INTERVAL_TICKS} tick 一次。</p>
     */
    public static void tickMarkers(net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return;
        }
        if (++markerTick < MARKER_INTERVAL_TICKS) {
            return;
        }
        markerTick = 0;
        if (!loaded || RaidService.current().isEmpty()) {
            return;
        }
        for (MarkerPoint marker : activeMarkers()) {
            net.minecraft.server.level.ServerLevel level = levelOf(server, marker.dimension());
            if (level == null) {
                continue;
            }
            net.minecraft.core.particles.SimpleParticleType particle = switch (marker.kind()) {
                case FIXED -> net.minecraft.core.particles.ParticleTypes.END_ROD;
                case CONDITIONAL -> net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME;
                case SINGLE_USE -> net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK;
                default -> net.minecraft.core.particles.ParticleTypes.CLOUD;
            };
            // 一小段向上的光柱：每隔几格补一点，远处也能看见
            for (int offset = 0; offset <= 3; offset++) {
                level.sendParticles(particle, marker.x(), marker.y() + 0.6D + offset * 0.8D, marker.z(),
                        2, 0.25D, 0.05D, 0.25D, 0.0D);
            }
        }
    }

    private static net.minecraft.server.level.ServerLevel levelOf(net.minecraft.server.MinecraftServer server,
                                                                  String dimensionId) {
        try {
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            net.minecraft.resources.ResourceLocation.parse(dimensionId));
            return server.getLevel(key);
        } catch (RuntimeException exception) {
            return null;
        }
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

    /**
     * 把一个 {@code EXTRACTION} 锚点转成撤离点候选。
     *
     * <p>高级字段用**标签**表达，这样在游戏里放点即可，不必改 JSON：</p>
     * <pre>
     * extract:fixed / extract:random / extract:conditional / extract:single_use   类型（默认随机）
     * requires:factory_power_on                                                  需要的条件标签
     * nospawn:east_spawn                                                         禁止使用的出生组
     * mindist:350 / maxdist:900                                                  与出生点的距离窗口
     * uses:4                                                                     本局可用次数
     * </pre>
     */
    private static ExtractionSelector.Candidate fromAnchor(RaidAnchor anchor) {
        ExtractionSelector.Kind kind = ExtractionSelector.Kind.RANDOM;
        Set<String> required = new TreeSet<>();
        Set<String> forbidden = new TreeSet<>();
        double minDistance = 0.0D;
        double maxDistance = 0.0D;
        int uses = 0;
        for (String raw : anchor.tags()) {
            String tag = raw.toLowerCase(Locale.ROOT);
            try {
                if (tag.startsWith("extract:")) {
                    kind = ExtractionSelector.Kind.valueOf(
                            tag.substring("extract:".length()).toUpperCase(Locale.ROOT));
                } else if (tag.startsWith("requires:")) {
                    required.add(tag.substring("requires:".length()));
                } else if (tag.startsWith("nospawn:")) {
                    forbidden.add(tag.substring("nospawn:".length()));
                } else if (tag.startsWith("mindist:")) {
                    minDistance = Double.parseDouble(tag.substring("mindist:".length()));
                } else if (tag.startsWith("maxdist:")) {
                    maxDistance = Double.parseDouble(tag.substring("maxdist:".length()));
                } else if (tag.startsWith("uses:")) {
                    uses = Math.max(0, Integer.parseInt(tag.substring("uses:".length())));
                }
            } catch (IllegalArgumentException ignored) {
                // 标签值写坏了就当没写，不影响这个点被使用
            }
        }
        return new ExtractionSelector.Candidate(anchor.id(), kind, Math.max(1, anchor.weight()),
                anchor.x(), anchor.z(), required, forbidden, minDistance, maxDistance, uses);
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
