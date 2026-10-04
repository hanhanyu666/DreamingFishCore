package com.hhy.dreamingfishcore.gameplay.raid_system.extraction;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidManifest;
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
        List<ExtractionService.MarkerPoint> markers = ExtractionService.activeMarkers();
        if (markers.isEmpty()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // 与其它系统口径一致：只认生存与冒险模式
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            for (ExtractionService.MarkerPoint marker : markers) {
                ServerLevel level = levelOf(server, marker.dimension());
                if (level == null || player.level() != level) {
                    continue;
                }
                double distance = horizontalDistance(player, marker);
                Progress progress = progressOf(player.getUUID(), marker.id());
                Progress.Step step = progress.advance(distance <= DEFAULT_RADIUS);
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

        ServerLevel target = levelOf(server, "minecraft:overworld");
        if (target != null) {
            var spawn = target.getSharedSpawnPos();
            player.teleportTo(target, spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
        }

        RaidManifest manifest = RaidService.current().orElse(null);
        player.displayClientMessage(Component.literal("撤离成功！"
                + (manifest != null ? "（第 " + manifest.raidId() + " 局" : "（")
                + "，撤离点 " + marker.id() + "，背包已带走）"), false);
        progressOf(player.getUUID(), marker.id()).reset();
        DreamingFishCore.LOGGER.info("[raid_extraction] {} 从 {} 撤离成功（对局 {}）",
                player.getGameProfile().getName(), marker.id(),
                manifest != null ? manifest.raidId() : -1L);
    }

    /** 限次撤离点的本局余量（未配置时返回 null，表示不限次）。 */
    public static synchronized Integer usesLeft(String extractionId) {
        return USES_LEFT.get(extractionId);
    }

    private static Progress progressOf(UUID playerId, String extractionId) {
        Map<String, Progress> byPoint = PROGRESS.computeIfAbsent(playerId, key -> new HashMap<>());
        return byPoint.computeIfAbsent(extractionId, key -> {
            int duration = DEFAULT_DURATION_TICKS;
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
