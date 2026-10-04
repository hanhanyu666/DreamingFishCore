package com.hhy.dreamingfishcore.gameplay.raid_system.extraction;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidConfig;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidManifest;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidRoster;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidService;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 撤离读条与执行（设计稿式玩法里"玩家真正撤出去"的那一段）。
 *
 * <p>这一版是**最小可用闭环**：</p>
 * <ul>
 *   <li>读条判定是纯逻辑（{@link Progress}），可单测；</li>
 *   <li>进度用**动作栏文本**显示，所以**不需要新增自定义包、不动协议版本**；</li>
 *   <li>完成时把玩家传到主世界（或配置的出口），给聊天小结，并把限次撤离点扣一次；</li>
 *   <li>限次点用尽后本局关闭（数据属于本局，`raid new` 时清空）。</li>
 * </ul>
 *
 * <p>还没做（下一步）：本局参与者名单、结算与统计落盘、死亡钩子、自动结束条件。</p>
 */
public final class ExtractionRunner {

    /** 读条半径（格）。 */
    public static final double DEFAULT_RADIUS = 3.0D;
    /** 读条时长（tick）。 */
    public static final int DEFAULT_DURATION_TICKS = 100;

    /**
     * 一个玩家对一个撤离点的读条状态（纯逻辑，单独测）。
     *
     * <p>规则：在范围内逐 tick 累加；离开范围清零；到时长算完成；完成后需要显式重置。</p>
     */
    public static final class Progress {

        public enum Step {
            /** 不在范围内且没有进度。 */
            IDLE,
            /** 正在读条。 */
            PROGRESSING,
            /** 刚刚完成。 */
            COMPLETED,
            /** 中途被打断（进度清零）。 */
            INTERRUPTED
        }

        private final int required;
        private int ticks;

        public Progress(int required) {
            this.required = Math.max(1, required);
        }

        public Step advance(boolean inRange) {
            if (!inRange) {
                boolean had = ticks > 0;
                ticks = 0;
                return had ? Step.INTERRUPTED : Step.IDLE;
            }
            if (ticks >= required) {
                return Step.COMPLETED;
            }
            ticks++;
            return ticks >= required ? Step.COMPLETED : Step.PROGRESSING;
        }

        public int ticks() {
            return ticks;
        }

        public int required() {
            return required;
        }

        /** 0.0~1.0 的完成度，供动作栏显示。 */
        public double ratio() {
            return Math.min(1.0D, (double) ticks / required);
        }

        public void reset() {
            ticks = 0;
        }
    }

    /** 玩家 UUID → (撤离点 id → 读条状态)。 */
    private static final Map<UUID, Map<String, Progress>> PROGRESS = new HashMap<>();
    /** 撤离点 id → 本局剩余可用次数（未配置 max_uses 的点不入表）。 */
    private static final Map<String, Integer> USES_LEFT = new HashMap<>();

    private ExtractionRunner() {
    }

    public static synchronized void clear() {
        PROGRESS.clear();
        USES_LEFT.clear();
        HINTED.clear();
    }

    /** 每 tick 调一次。 */
    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Optional<RaidManifest> raid = RaidService.current();
        if (raid.isEmpty()) {
            clear();
            return;
        }
        // 自动结束（默认 30 分钟，配置里可关）：时间到就清战利品、归档、收工
        if (RaidConfig.shouldAutoEnd(raid.get().startedAtEpochMillis(), System.currentTimeMillis(),
                RaidConfig.autoEndSeconds())) {
            autoEnd(server, raid.get(), "time_up");
            return;
        }
        // 无人留在图里也可以收工（撤了、死了、掉线超时都算出去）；服主可在配置里关掉
        if (RaidConfig.autoEndWhenAllOut() && RaidRoster.roster().nobodyLeftInRaid()) {
            autoEnd(server, raid.get(), "all_out");
            return;
        }
        List<ExtractionService.MarkerPoint> markers = ExtractionService.activeMarkers();
        if (markers.isEmpty()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // 与其它系统口径一致：只有生存与冒险模式能撤离。
            // 但**不能静默跳过**——服主常常在创造模式下测试，什么都不发生会让人以为功能坏了，
            // 所以在范围里站定时给一条明确提示（每个玩家只提示一次，避免刷屏）。
            if (player.isCreative() || player.isSpectator()) {
                hintForNonSurvival(player, markers);
                continue;
            }
            // 只有开局在线的人算本局参与者：中途进来的站进撤离点不会读条，但必须给明确说明
            if (!RaidRoster.roster().isInRaid(player.getUUID())) {
                if (hintOnce(player) && distanceToAny(player, markers) <= RaidConfig.extractionRadius()) {
                    player.sendSystemMessage(Component.literal(
                            "[搜打撤] 你不是本局参与者（本局开始时你不在），撤离点对你无效——等下一局"));
                }
                continue;
            }
            for (ExtractionService.MarkerPoint marker : markers) {
                ServerLevel level = levelOf(server, marker.dimension());
                if (level == null || player.level() != level) {
                    continue;
                }
                double distance = horizontalDistance(player, marker);
                Progress progress = progressOf(player.getUUID(), marker.id());
                Progress.Step step = progress.advance(distance <= RaidConfig.extractionRadius());
                switch (step) {
                    case PROGRESSING -> player.displayClientMessage(Component.literal(
                            "撤离中 " + Math.round(progress.ratio() * 100.0D) + "%（保持在范围内）"), true);
                    case INTERRUPTED -> player.displayClientMessage(
                            Component.literal("撤离中断：已离开撤离点范围"), true);
                    case COMPLETED -> extract(server, player, marker);
                    default -> {
                    }
                }
            }
        }
    }

    /** 读条完成：扣次数、传送、结算。 */
    private static void extract(MinecraftServer server, ServerPlayer player,
                                ExtractionService.MarkerPoint marker) {
        Integer left = USES_LEFT.get(marker.id());
        if (left != null && left <= 0) {
            player.displayClientMessage(Component.literal("这个撤离点本局已经用尽了"), true);
            progressOf(player.getUUID(), marker.id()).reset();
            return;
        }
        if (left != null) {
            USES_LEFT.put(marker.id(), left - 1);
        }

        ServerLevel target = levelOf(server, RaidConfig.exitDimension());
        if (target != null) {
            double[] exit = RaidConfig.exitPosition();
            if (exit != null) {
                player.teleportTo(target, exit[0], exit[1], exit[2], player.getYRot(), player.getXRot());
            } else {
                var spawn = target.getSharedSpawnPos();
                player.teleportTo(target, spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D,
                        player.getYRot(), player.getXRot());
            }
        }

        RaidManifest manifest = RaidService.current().orElse(null);
        player.displayClientMessage(Component.literal("撤离成功！"
                + (manifest != null ? "（第 " + manifest.raidId() + " 局" : "（")
                + "，撤离点 " + marker.id() + "，背包已带走）"), false);
        progressOf(player.getUUID(), marker.id()).reset();
        RaidRoster.mark(server, player, RaidRoster.State.EXTRACTED);   // 名单：已撤离
        saveUses(server);
        if (manifest != null) {
            com.hhy.dreamingfishcore.gameplay.raid_system.RaidStatsLog
                    .extracted(server, manifest, player, marker.id());
        }
        DreamingFishCore.LOGGER.info("[raid_extraction] {} 从 {} 撤离成功（对局 {}）",
                player.getGameProfile().getName(), marker.id(),
                manifest != null ? manifest.raidId() : -1L);
    }

    /** 时间到自动结束：清战利品（含还原覆盖前内容）→ 归档对局 → 清空撤离状态。 */
    private static void autoEnd(MinecraftServer server, RaidManifest manifest, String reason) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("[搜打撤] " + ("all_out".equals(reason) ? "本局已无人留在图里，自动结束" : "本局时间到，自动结束") + "（第 " + manifest.raidId() + " 局）"), false);
        com.hhy.dreamingfishcore.gameplay.raid_system.loot.RaidLootApplier
                .clear(server, manifest.raidId());
        com.hhy.dreamingfishcore.gameplay.raid_system.RaidStatsLog.raidEnd(server, manifest, reason);
        RaidService.endRaid(server);
        clear();
        DreamingFishCore.LOGGER.info("[raid] 第 {} 局自动结束（原因 {}）", manifest.raidId(), reason);
    }

    /** 限次撤离点的本局余量（未配置时返回 null，表示不限次）。 */
    public static synchronized Integer usesLeft(String extractionId) {
        return USES_LEFT.get(extractionId);
    }

    private static final String USES_FILE_NAME = "raid_extraction_uses.json";

    /**
     * 读回本局的限次余量（服务端启动时调）。
     *
     * <p>只认**同一个对局号**的记录：换了新局就该从满次数开始，不能被上一局用剩的次数污染。</p>
     */
    public static synchronized void load(MinecraftServer server) {
        if (server == null) {
            return;
        }
        RaidManifest manifest = RaidService.current().orElse(null);
        if (manifest == null) {
            return;
        }
        java.nio.file.Path path = usesPath(server);
        if (!java.nio.file.Files.isRegularFile(path)) {
            return;
        }
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(path,
                java.nio.charset.StandardCharsets.UTF_8)) {
            com.google.gson.JsonElement element = com.google.gson.JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                return;
            }
            com.google.gson.JsonObject json = element.getAsJsonObject();
            if (!json.has("raid_id") || json.get("raid_id").getAsLong() != manifest.raidId()) {
                return;     // 上一局的余量，忽略
            }
            if (json.has("uses") && json.get("uses").isJsonObject()) {
                USES_LEFT.clear();
                json.getAsJsonObject("uses").entrySet().forEach(entry ->
                        USES_LEFT.put(entry.getKey(), entry.getValue().getAsInt()));
                DreamingFishCore.LOGGER.info("[raid_extraction] 已读回本局限次余量：{}", USES_LEFT);
            }
        } catch (java.io.IOException | RuntimeException exception) {
            DreamingFishCore.LOGGER.error("[raid_extraction] 限次余量读取失败：{}", path, exception);
        }
    }

    /** 写回本局的限次余量（每次扣减后调）。 */
    private static synchronized void saveUses(MinecraftServer server) {
        RaidManifest manifest = RaidService.current().orElse(null);
        if (server == null || manifest == null || USES_LEFT.isEmpty()) {
            return;
        }
        com.google.gson.JsonObject uses = new com.google.gson.JsonObject();
        USES_LEFT.forEach(uses::addProperty);
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("raid_id", manifest.raidId());
        root.add("uses", uses);
        java.nio.file.Path path = usesPath(server);
        try {
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path, root.toString(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            DreamingFishCore.LOGGER.error("[raid_extraction] 限次余量写入失败：{}", path, exception);
        }
    }

    private static java.nio.file.Path usesPath(MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("dreamingfishcore").resolve(USES_FILE_NAME);
    }

    /** 已经提示过"创造模式无法撤离"的玩家，避免每 tick 刷屏。 */
    private static final java.util.Set<UUID> HINTED = new java.util.HashSet<>();

    /** 每个玩家只提示一次（离开范围后重置），避免每 tick 刷屏。 */
    private static boolean hintOnce(ServerPlayer player) {
        return HINTED.add(player.getUUID());
    }

    /** 与最近的撤离点之间的水平距离（没有点时返回一个很大的值）。 */
    private static double distanceToAny(ServerPlayer player, List<ExtractionService.MarkerPoint> markers) {
        double best = Double.MAX_VALUE;
        for (ExtractionService.MarkerPoint marker : markers) {
            if (player.level() != levelOf(player.getServer(), marker.dimension())) {
                continue;
            }
            best = Math.min(best, horizontalDistance(player, marker));
        }
        return best;
    }
    /** 创造/旁观玩家站进撤离点范围时提示一次——否则会让人以为撤离功能坏了。 */
    private static void hintForNonSurvival(ServerPlayer player, List<ExtractionService.MarkerPoint> markers) {
        boolean inRange = false;
        for (ExtractionService.MarkerPoint marker : markers) {
            ServerLevel level = levelOf(player.getServer(), marker.dimension());
            if (level != null && player.level() == level
                    && horizontalDistance(player, marker) <= RaidConfig.extractionRadius()) {
                inRange = true;
                break;
            }
        }
        if (!inRange) {
            HINTED.remove(player.getUUID());
            return;
        }
        if (HINTED.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal(
                    "[搜打撤] 创造/旁观模式不会触发撤离——切换到生存模式再站进来（撤离点是给你测位置用的）"));
        }
    }

    private static Progress progressOf(UUID playerId, String extractionId) {        Map<String, Progress> byPoint = PROGRESS.computeIfAbsent(playerId, key -> new HashMap<>());
        return byPoint.computeIfAbsent(extractionId, key -> {
            int duration = RaidConfig.extractionTicks();
            Integer uses = maxUsesOf(extractionId);
            if (uses != null) {
                USES_LEFT.putIfAbsent(extractionId, uses);
            }
            return new Progress(duration);
        });
    }

    private static Integer maxUsesOf(String extractionId) {
        for (ExtractionSelector.Candidate candidate : ExtractionService.candidates()) {
            if (candidate.id().equals(extractionId)) {
                return candidate.maxUses() > 0 ? candidate.maxUses() : null;
            }
        }
        return null;
    }

    private static double horizontalDistance(ServerPlayer player, ExtractionService.MarkerPoint marker) {
        double dx = player.getX() - marker.x();
        double dz = player.getZ() - marker.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static ServerLevel levelOf(MinecraftServer server, String dimensionId) {
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(dimensionId.toLowerCase(Locale.ROOT)));
            return server.getLevel(key);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
