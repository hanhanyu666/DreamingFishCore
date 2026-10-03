package com.hhy.dreamingfishcore.gameplay.raid_system.anchor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.DreamingFishCore;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 锚点系统的游戏侧服务：把两层数据（数据包定义 + 存档覆盖）合成一份可用的锚点目录。
 *
 * <p>两层各管一件事：</p>
 * <ul>
 *   <li><b>定义层</b>：{@code data/<命名空间>/raid_anchors/*.json}，随地图/资源包分发，可版本控制、可审核；</li>
 *   <li><b>世界层</b>：{@code <存档>/dreamingfishcore/raid_anchors_overlay.json}，服主在游戏内的微调，
 *       只记差异且优先于定义层。</li>
 * </ul>
 *
 * <p>刻意不引入 {@code SavedData}：覆盖层是纯配置数据，用世界目录下的 JSON 更好审阅、更好备份，
 * 也方便服主直接改文件；将来若要与其它世界数据统一，再换实现即可，上层接口不变。</p>
 */
public final class RaidAnchorService {

    /** 数据包里的定义目录名。 */
    public static final String DATAPACK_DIR = "raid_anchors";
    /** 存档目录下的覆盖层文件名。 */
    public static final String OVERLAY_FILE = "raid_anchors_overlay.json";
    /** 本模组在存档目录下的子目录名。 */
    private static final String SAVE_SUBDIR = "dreamingfishcore";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static volatile RaidAnchorCatalog catalog = RaidAnchorCatalog.empty();
    private static volatile RaidAnchorOverlay overlay = RaidAnchorOverlay.empty();
    private static volatile List<String> loadProblems = List.of();
    private static volatile boolean loaded = false;

    private RaidAnchorService() {
    }

    public static RaidAnchorCatalog catalog() {
        return catalog;
    }

    public static RaidAnchorOverlay overlay() {
        return overlay;
    }

    /** 加载期问题（JSON 读不了、文件结构不对等）；与目录里的逐条校验问题分开记。 */
    public static List<String> loadProblems() {
        return loadProblems;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * 重新加载：读数据包定义 + 读存档覆盖层，合并校验后缓存。
     *
     * @return 给人看的加载摘要（命令与日志共用）
     */
    public static synchronized List<String> reload(MinecraftServer server) {
        List<String> messages = new ArrayList<>();
        if (server == null) {
            messages.add("服务器尚未就绪，锚点未加载");
            return messages;
        }

        List<String> problems = new ArrayList<>();
        List<RaidAnchor> definitions = loadDefinitions(server, problems);
        RaidAnchorOverlay loadedOverlay = loadOverlay(server, problems);

        RaidAnchorCatalog built = RaidAnchorCatalog.build(definitions, loadedOverlay, RaidAnchorZoneLookup.INSTANCE);

        overlay = loadedOverlay;
        catalog = built;
        loadProblems = List.copyOf(problems);
        loaded = true;

        messages.add("锚点已加载：定义层 " + definitions.size() + " 个，覆盖层 "
                + (loadedOverlay.isEmpty() ? "空" : loadedOverlay.patches().size() + " 个改动")
                + "，可用 " + built.anchors().size() + " 个");
        if (!problems.isEmpty()) {
            messages.add("加载期问题 " + problems.size() + " 条（见下）");
            problems.forEach(problem -> messages.add("  " + problem));
        }
        if (!built.problems().isEmpty()) {
            messages.add("校验问题 " + built.problems().size() + " 条（见下）");
            built.problems().forEach(problem -> messages.add("  " + problem));
        }
        DreamingFishCore.LOGGER.info("[raid_anchor] 加载完成：可用 {} 个，加载期问题 {} 条，校验问题 {} 条",
                built.anchors().size(), problems.size(), built.problems().size());
        return messages;
    }

    /** 首次使用时按需加载（命令与后续系统都可以直接调）。 */
    public static synchronized void ensureLoaded(MinecraftServer server) {
        if (!loaded) {
            reload(server);
        }
    }

    /** 服务器停止时清空缓存，避免跨世界串数据。 */
    public static synchronized void clear() {
        catalog = RaidAnchorCatalog.empty();
        overlay = RaidAnchorOverlay.empty();
        loadProblems = List.of();
        loaded = false;
    }

    /**
     * 写入新的覆盖层并立刻重算目录 + 落盘。
     *
     * <p>命令层统一走这里：改完必存盘，避免"改了但重启就丢"。</p>
     *
     * @return 给人看的摘要
     */
    public static synchronized List<String> applyOverlay(MinecraftServer server, RaidAnchorOverlay next) {
        List<String> messages = new ArrayList<>();
        if (server == null) {
            messages.add("服务器尚未就绪，无法修改锚点");
            return messages;
        }
        overlay = next;
        List<String> problems = new ArrayList<>();
        List<RaidAnchor> definitions = loadDefinitions(server, problems);
        catalog = RaidAnchorCatalog.build(definitions, next, RaidAnchorZoneLookup.INSTANCE);
        loadProblems = List.copyOf(problems);
        loaded = true;

        boolean saved = saveOverlay(server, next);
        messages.add(saved ? "覆盖层已保存" : "覆盖层保存失败（详见日志）");
        messages.add("当前可用锚点 " + catalog.anchors().size() + " 个");
        return messages;
    }

    /** 存档目录下的覆盖层文件路径。 */
    public static Path overlayPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(SAVE_SUBDIR).resolve(OVERLAY_FILE);
    }

    private static boolean saveOverlay(MinecraftServer server, RaidAnchorOverlay data) {
        Path path = overlayPath(server);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(data.toJson()) + System.lineSeparator(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("[raid_anchor] 覆盖层写入失败：{}", path, exception);
            return false;
        }
    }

    private static RaidAnchorOverlay loadOverlay(MinecraftServer server, List<String> problems) {
        Path path = overlayPath(server);
        if (!Files.isRegularFile(path)) {
            return RaidAnchorOverlay.empty();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                problems.add("覆盖层文件不是 JSON 对象：" + path);
                return RaidAnchorOverlay.empty();
            }
            return RaidAnchorOverlay.fromJson(element.getAsJsonObject(), problems);
        } catch (IOException | RuntimeException exception) {
            problems.add("覆盖层读取失败（已按空处理）：" + path + " -> " + exception.getMessage());
            return RaidAnchorOverlay.empty();
        }
    }

    /** 读数据包里的全部锚点定义；单个文件坏了只跳过该文件。 */
    private static List<RaidAnchor> loadDefinitions(MinecraftServer server, List<String> problems) {
        List<RaidAnchor> anchors = new ArrayList<>();
        Map<ResourceLocation, Resource> resources;
        try {
            resources = server.getResourceManager()
                    .listResources(DATAPACK_DIR, path -> path.getPath().endsWith(".json"));
        } catch (RuntimeException exception) {
            problems.add("枚举锚点定义失败：" + exception.getMessage());
            return anchors;
        }

        List<ResourceLocation> ids = new ArrayList<>(resources.keySet());
        // 遍历顺序确定：同一份数据每次加载得到完全相同的顺序，便于复现问题
        ids.sort(Comparator.comparing(ResourceLocation::toString));

        for (ResourceLocation id : ids) {
            Resource resource = resources.get(id);
            if (resource == null) {
                continue;
            }
            try (Reader reader = resource.openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                anchors.addAll(readDefinitionFile(id, element, problems));
            } catch (IOException | RuntimeException exception) {
                problems.add("锚点定义读取失败（已跳过）：" + id + " -> " + exception.getMessage());
            }
        }
        return anchors;
    }

    /**
     * 一个定义文件里可以只放数组，也可以放 {@code {"anchors": [...]}}，两种写法都认。
     *
     * <p>前者适合手写，后者留了加注释/元信息的余地。</p>
     */
    private static List<RaidAnchor> readDefinitionFile(ResourceLocation id, JsonElement element,
                                                       List<String> problems) {
        List<RaidAnchor> anchors = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            problems.add("锚点定义是空文件（已跳过）：" + id);
            return anchors;
        }

        JsonElement arrayElement = element;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            arrayElement = object.get("anchors");
            if (arrayElement == null) {
                problems.add("锚点定义缺少 anchors 字段（已跳过）：" + id);
                return anchors;
            }
        }
        if (!arrayElement.isJsonArray()) {
            problems.add("锚点定义的 anchors 不是数组（已跳过）：" + id);
            return anchors;
        }

        int index = 0;
        for (JsonElement item : arrayElement.getAsJsonArray()) {
            index++;
            if (item == null || !item.isJsonObject()) {
                problems.add("锚点定义第 " + index + " 项不是对象（已跳过）：" + id);
                continue;
            }
            RaidAnchor.ParseResult result = RaidAnchor.fromJson(item.getAsJsonObject(), RaidAnchor.Source.DEFINITION);
            if (result.ok()) {
                anchors.add(result.anchor());
            } else {
                problems.add("锚点定义第 " + index + " 项解析失败（已跳过）：" + id + " -> "
                        + String.join("；", result.problems()));
            }
        }
        return anchors;
    }

    /** 把（合并后的）锚点导出成可分发 JSON；给了区域就只导该区域。 */
    public static synchronized String exportJson(String zone) {
        List<RaidAnchor> anchors = zone == null || zone.isBlank()
                ? catalog.anchors()
                : catalog.byZone(zone);
        JsonObject root = new JsonObject();
        var array = new com.google.gson.JsonArray();
        anchors.stream().map(RaidAnchor::toJson).forEach(array::add);
        root.add("anchors", array);
        return GSON.toJson(root);
    }

    /** 按 id 取单个可用锚点。 */
    public static Optional<RaidAnchor> byId(String id) {
        return catalog.byId(id);
    }
}
