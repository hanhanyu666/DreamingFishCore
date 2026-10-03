package com.hhy.dreamingfishcore.gameplay.raid_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.raid_system.anchor.RaidAnchorService;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 对局服务：管理"当前这一局"，并把 {@link RaidManifest} 落盘。
 *
 * <p>第 1 步只做对局身份与种子：开新局、读回旧局、暴露各子系统的随机源。
 * 具体生成内容（撤离点、资源点、露天物品…）由后续步骤往 manifest 里填。</p>
 *
 * <p>持久化的意义（设计稿 §14.4）：服务器重启后要**读回**这一局，而不是重新随机一次。</p>
 */
public final class RaidService {

    private static final String SAVE_SUBDIR = "dreamingfishcore";
    private static final String MANIFEST_FILE = "raid_manifest.json";
    /** 存档里保留的历史对局数：便于运营回查，避免无限增长。 */
    public static final int HISTORY_LIMIT = 50;
    private static final String HISTORY_FILE = "raid_history.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static volatile RaidManifest current;
    private static volatile List<RaidManifest> history = List.of();
    private static volatile boolean loaded;

    private RaidService() {
    }

    public static Optional<RaidManifest> current() {
        return Optional.ofNullable(current);
    }

    public static List<RaidManifest> history() {
        return history;
    }

    /** 按需加载（命令与后续系统都可以直接调）。 */
    public static synchronized void ensureLoaded(MinecraftServer server) {
        if (!loaded && server != null) {
            load(server);
        }
    }

    public static synchronized List<String> load(MinecraftServer server) {
        List<String> messages = new ArrayList<>();
        current = null;
        history = List.of();
        loaded = true;
        if (server == null) {
            return messages;
        }

        Path path = manifestPath(server);
        if (Files.isRegularFile(path)) {
            RaidManifest manifest = readManifest(path, messages);
            current = manifest;
            messages.add(manifest == null
                    ? "对局记录读取失败（已按没有进行中的对局处理）"
                    : "已读回进行中的对局 #" + manifest.raidId());
        } else {
            messages.add("当前没有进行中的对局");
        }

        Path historyPath = historyPath(server);
        if (Files.isRegularFile(historyPath)) {
            history = readHistory(historyPath, messages);
            messages.add("历史对局 " + history.size() + " 条");
        }
        return messages;
    }

    /**
     * 开一局新的：对局号递增、种子由（服务器种子, 地图 id, 对局号）决定，并把上一局归档。
     *
     * @return 给人看的摘要
     */
    public static synchronized List<String> startNewRaid(MinecraftServer server, String mapId,
                                                         int difficultyTier, int playerCount) {
        List<String> messages = new ArrayList<>();
        if (server == null) {
            messages.add("服务器尚未就绪，无法开局");
            return messages;
        }
        ensureLoaded(server);

        long previousId = current == null ? lastHistoryId() : current.raidId();
        long raidId = previousId + 1;
        long serverSeed = server.overworld().getSeed();
        long seed = RaidRandom.raidSeed(serverSeed, mapId, raidId);

        List<String> notes = new ArrayList<>();
        notes.add("服务器种子 " + serverSeed);
        RaidAnchorService.ensureLoaded(server);
        Map<String, Integer> counts = RaidAnchorService.catalog().countByType();
        if (counts.isEmpty()) {
            notes.add("当前没有任何候选锚点（先用 /dreamingfish raid_anchor 放置）");
        } else {
            notes.add("候选锚点 " + counts);
        }

        RaidManifest manifest = new RaidManifest(raidId, seed, mapId, System.currentTimeMillis(),
                Math.max(1, playerCount), difficultyTier, java.util.Set.of(), java.util.Set.of(),
                java.util.Set.of(), Map.of(), notes);

        if (current != null) {
            archive(current);
        }
        current = manifest;
        boolean saved = save(server);
        messages.add("已开新局 #" + raidId + "（地图 " + (mapId == null || mapId.isBlank() ? "未指定" : mapId)
                + "，难度档 " + difficultyTier + "，人数 " + manifest.playerCount() + "）");
        messages.add("主种子 " + seed + "（0x" + Long.toHexString(seed) + "）");
        messages.add(saved ? "已写入存档" : "写入存档失败（详见日志）");
        DreamingFishCore.LOGGER.info("[raid] 开新局 #{}（地图 {}，种子 {}）", raidId, mapId, seed);
        return messages;
    }

    /** 结束当前对局（归档并清空进行中记录）。 */
    public static synchronized List<String> endRaid(MinecraftServer server) {
        List<String> messages = new ArrayList<>();
        if (server == null) {
            messages.add("服务器尚未就绪");
            return messages;
        }
        ensureLoaded(server);
        if (current == null) {
            messages.add("当前没有进行中的对局");
            return messages;
        }
        long raidId = current.raidId();
        archive(current);
        current = null;
        boolean saved = save(server);
        messages.add("已结束对局 #" + raidId);
        messages.add(saved ? "已写入存档" : "写入存档失败（详见日志）");
        return messages;
    }

    /** 按系统取名取随机源；没有进行中的对局时返回空（调用方应据此拒绝生成）。 */
    public static Optional<RaidRandom> randomFor(String system) {
        RaidManifest manifest = current;
        return manifest == null ? Optional.empty() : Optional.of(manifest.randomFor(system));
    }

    /** 预测某个（地图, 对局号）组合会用到的种子——复现别人报的问题时很有用。 */
    public static long predictSeed(MinecraftServer server, String mapId, long raidId) {
        long serverSeed = server == null ? 0L : server.overworld().getSeed();
        return RaidRandom.raidSeed(serverSeed, mapId, raidId);
    }

    public static synchronized void clear() {
        current = null;
        history = List.of();
        loaded = false;
    }

    public static Path manifestPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(SAVE_SUBDIR).resolve(MANIFEST_FILE);
    }

    private static Path historyPath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(SAVE_SUBDIR).resolve(HISTORY_FILE);
    }

    private static long lastHistoryId() {
        long max = 0L;
        for (RaidManifest manifest : history) {
            max = Math.max(max, manifest.raidId());
        }
        return max;
    }

    private static void archive(RaidManifest manifest) {
        List<RaidManifest> next = new ArrayList<>(history);
        next.add(manifest);
        while (next.size() > HISTORY_LIMIT) {
            next.remove(0);
        }
        history = List.copyOf(next);
    }

    private static boolean save(MinecraftServer server) {
        boolean ok = writeManifest(manifestPath(server), current != null ? current.toJson() : null);
        var array = new com.google.gson.JsonArray();
        history.stream().map(RaidManifest::toJson).forEach(array::add);
        com.google.gson.JsonObject historyRoot = new com.google.gson.JsonObject();
        historyRoot.add("raids", array);
        return writeJson(historyPath(server), historyRoot) && ok;
    }

    private static boolean writeManifest(Path path, com.google.gson.JsonObject json) {
        if (json == null) {
            try {
                Files.deleteIfExists(path);
                return true;
            } catch (IOException exception) {
                DreamingFishCore.LOGGER.error("[raid] 删除对局记录失败：{}", path, exception);
                return false;
            }
        }
        return writeJson(path, json);
    }

    private static boolean writeJson(Path path, com.google.gson.JsonObject json) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(json) + System.lineSeparator(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("[raid] 写入失败：{}", path, exception);
            return false;
        }
    }

    private static RaidManifest readManifest(Path path, List<String> messages) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                messages.add("对局记录不是 JSON 对象：" + path);
                return null;
            }
            RaidManifest manifest = RaidManifest.fromJson(element.getAsJsonObject());
            if (manifest == null) {
                messages.add("对局记录字段不完整：" + path);
            }
            return manifest;
        } catch (IOException | RuntimeException exception) {
            messages.add("对局记录读取失败：" + exception.getMessage());
            return null;
        }
    }

    private static List<RaidManifest> readHistory(Path path, List<String> messages) {
        List<RaidManifest> raids = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()
                    || !element.getAsJsonObject().has("raids")) {
                return raids;
            }
            for (JsonElement item : element.getAsJsonObject().getAsJsonArray("raids")) {
                RaidManifest manifest = RaidManifest.fromJson(item.getAsJsonObject());
                if (manifest != null) {
                    raids.add(manifest);
                }
            }
        } catch (IOException | RuntimeException exception) {
            messages.add("历史对局读取失败：" + exception.getMessage());
        }
        return raids;
    }
}
