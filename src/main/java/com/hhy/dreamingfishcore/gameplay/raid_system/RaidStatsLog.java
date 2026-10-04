package com.hhy.dreamingfishcore.gameplay.raid_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.hhy.dreamingfishcore.DreamingFishCore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 对局事件日志（追加式 JSONL，一行一个事件）。
 *
 * <p>为什么用追加日志而不是往 {@link RaidManifest} 里加字段：</p>
 * <ul>
 *   <li>事件是"发生过的事"，天然适合追加；改 manifest 结构要动 JSON 兼容与已有测试；</li>
 *   <li>一行一条便于运营直接 grep / 导表（谁撤了、谁死了、几点撤的）；</li>
 *   <li>写坏一行只影响那一行，不会让整份对局记录读不出来。</li>
 * </ul>
 *
 * <p>记录的事件：对局开始、撤离成功、对局内死亡、对局结束（手动或时间到）。</p>
 */
public final class RaidStatsLog {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String SAVE_SUBDIR = "dreamingfishcore";
    private static final String FILE_NAME = "raid_stats.jsonl";

    private RaidStatsLog() {
    }

    public static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(SAVE_SUBDIR).resolve(FILE_NAME);
    }

    /** 对局开始。 */
    public static void raidStart(MinecraftServer server, RaidManifest manifest) {
        JsonObject json = base(manifest.raidId(), "raid_start");
        json.addProperty("map", manifest.mapId());
        json.addProperty("seed", manifest.raidSeed());
        json.addProperty("players", manifest.playerCount());
        json.addProperty("difficulty", manifest.difficultyTier());
        write(server, json);
    }

    /** 撤离成功。 */
    public static void extracted(MinecraftServer server, RaidManifest manifest, ServerPlayer player,
                                 String extractionId) {
        JsonObject json = base(manifest.raidId(), "extract");
        json.addProperty("player", player.getGameProfile().getName());
        json.addProperty("player_uuid", player.getUUID().toString());
        json.addProperty("extraction", extractionId);
        // 带回多少东西：只记件数，不动经济（出售价归 EconomySystem）
        int carried = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (!player.getInventory().getItem(slot).isEmpty()) {
                carried += player.getInventory().getItem(slot).getCount();
            }
        }
        json.addProperty("carried_items", carried);
        write(server, json);
    }

    /** 对局内死亡：按设计，战利品留在尸体里带不出去，这里只做记录。 */
    public static void died(MinecraftServer server, RaidManifest manifest, ServerPlayer player) {
        JsonObject json = base(manifest.raidId(), "death");
        json.addProperty("player", player.getGameProfile().getName());
        json.addProperty("player_uuid", player.getUUID().toString());
        write(server, json);
    }

    /** 对局结束。{@code reason} 例如 manual / time_up。 */
    public static void raidEnd(MinecraftServer server, RaidManifest manifest, String reason) {
        JsonObject json = base(manifest.raidId(), "raid_end");
        json.addProperty("reason", reason);
        json.addProperty("duration_seconds",
                Math.max(0L, (System.currentTimeMillis() - manifest.startedAtEpochMillis()) / 1000L));
        write(server, json);
    }

    private static JsonObject base(long raidId, String type) {
        JsonObject json = new JsonObject();
        json.addProperty("at", System.currentTimeMillis());
        json.addProperty("raid_id", raidId);
        json.addProperty("type", type);
        return json;
    }

    private static synchronized void write(MinecraftServer server, JsonObject json) {
        if (server == null) {
            return;
        }
        Path path = path(server);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(json) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("[raid_stats] 事件写入失败：{}", path, exception);
        }
    }
}
