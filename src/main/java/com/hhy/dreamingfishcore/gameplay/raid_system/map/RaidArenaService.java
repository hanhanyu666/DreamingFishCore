package com.hhy.dreamingfishcore.gameplay.raid_system.map;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidConfig;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidRoster;
import java.util.ArrayList;
import java.util.List;
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
 * 竞技场的进出场与时间（开局自动化）。
 *
 * <p>开局：把本局参与者送进竞技场，并把世界时间设到"本局起始点"；
 * 收尾：把还在竞技场里的人送回去。地图作者只要把零件摆好，不用每次手动 tp。</p>
 *
 * <p><b>昼夜时间的原版限制</b>：Minecraft 所有维度共享同一个昼夜时钟（只有主世界推进它），
 * 维度无法各自独立计时——除非用 {@code fixed_time} 冻住（那就没有日夜变化了）。
 * 所以"昼夜跟着本局走"的实现是"开局把时间拨到起始点，之后自然流动"，
 * 副作用是**主世界时钟也会跟着变**。要关掉就把 {@link #SET_TIME_ON_START} 改成 false。</p>
 */
public final class RaidArenaService {

    /** 竞技场维度 id（随 mod 发布，见 data/dreamingfishcore/dimension/raid_arena.json）。 */
    public static final ResourceLocation ARENA_ID = ResourceLocation.parse("dreamingfishcore:raid_arena");
    /** 进场落点（虚空里的一块落脚处，地图作者可自行改）。 */
    public static final double SPAWN_X = 0.5D;
    public static final double SPAWN_Y = 100.0D;
    public static final double SPAWN_Z = 0.5D;
    /** 开局把时间拨到清晨（1000 tick ≈ 早上 7 点）。 */
    public static final long START_TIME = 1000L;
    /** 是否在开局时拨时间。原版限制导致这会影响主世界时钟，所以留个开关。 */
    public static final boolean SET_TIME_ON_START = true;
    /** 是否在开局时自动把参与者送进竞技场。 */
    public static final boolean TELEPORT_ON_START = true;
    /** 是否在收尾时把竞技场里的人送回来。 */
    public static final boolean RECALL_ON_END = true;

    private RaidArenaService() {
    }

    /** 竞技场维度；数据包没加载或维度不存在时返回 null。 */
    public static ServerLevel arena(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ARENA_ID);
            return server.getLevel(key);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** 开局：拨时间 + 把参与者送进竞技场。 */
    public static List<String> onRaidStart(MinecraftServer server) {
        List<String> messages = new ArrayList<>();
        if (server == null) {
            return messages;
        }
        ServerLevel arena = arena(server);
        if (arena == null) {
            messages.add("⚠ 没找到竞技场维度 " + ARENA_ID + "（检查数据包是否加载），本局不自动进场");
            return messages;
        }
        if (SET_TIME_ON_START) {
            // 所有维度共享时钟，所以设一次主世界即可
            server.overworld().setDayTime(START_TIME);
            messages.add("已将世界时间设为本局起始（" + START_TIME + " tick）");
        }
        if (!TELEPORT_ON_START) {
            return messages;
        }
        int moved = 0;
        for (ServerPlayer player : participants(server)) {
            if (player.level() == arena) {
                continue;
            }
            player.teleportTo(arena, SPAWN_X, SPAWN_Y, SPAWN_Z, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("[搜打撤] 已进入本局地图，祝你活着出来"));
            moved++;
        }
        messages.add("已把 " + moved + " 名参与者送入竞技场");
        return messages;
    }

    /** 收尾：把还在竞技场里的人送回出口（配置的 exit_dimension / exit_position，默认主世界出生点）。 */
    public static List<String> onRaidEnd(MinecraftServer server) {
        List<String> messages = new ArrayList<>();
        if (server == null || !RECALL_ON_END) {
            return messages;
        }
        ServerLevel arena = arena(server);
        if (arena == null) {
            return messages;
        }
        ServerLevel exit = exitLevel(server);
        if (exit == null) {
            messages.add("⚠ 出口维度不存在，无法召回玩家");
            return messages;
        }
        double[] position = RaidConfig.exitPosition();
        double x;
        double y;
        double z;
        if (position != null && position.length >= 3) {
            x = position[0];
            y = position[1];
            z = position[2];
        } else {
            var spawn = exit.getSharedSpawnPos();
            x = spawn.getX() + 0.5D;
            y = spawn.getY();
            z = spawn.getZ() + 0.5D;
        }
        int moved = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != arena) {
                continue;
            }
            player.teleportTo(exit, x, y, z, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("[搜打撤] 本局已结束，你被送回了安全区"));
            moved++;
        }
        if (moved > 0) {
            messages.add("已把 " + moved + " 名玩家送离竞技场");
        }
        return messages;
    }

    /** 本局参与者（在册且仍在图里的人）。 */
    private static List<ServerPlayer> participants(MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>();
        RaidRoster.Roster roster = RaidRoster.roster();
        for (UUID id : roster.states().keySet()) {
            if (roster.stateOf(id) != RaidRoster.State.IN_RAID) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    private static ServerLevel exitLevel(MinecraftServer server) {
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(RaidConfig.exitDimension()));
            ServerLevel level = server.getLevel(key);
            return level != null ? level : server.overworld();
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.warn("[raid_arena] 出口维度解析失败，回退主世界：{}",
                    RaidConfig.exitDimension());
            return server.overworld();
        }
    }

    /** 供命令打印状态。 */
    public static List<String> status(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        ServerLevel arena = arena(server);
        lines.add("竞技场维度 " + ARENA_ID + "：" + (arena == null ? "未加载（检查数据包）" : "已加载"));
        if (arena != null) {
            lines.add("  进场落点：" + SPAWN_X + " / " + SPAWN_Y + " / " + SPAWN_Z);
            lines.add("  当前世界时间：" + arena.getDayTime() + " tick（0=清晨，6000=正午，13000=日落，18000=午夜）");
        }
        lines.add("  开局拨时间：" + SET_TIME_ON_START + "（" + START_TIME + " tick）"
                + "，开局自动进场：" + TELEPORT_ON_START + "，收尾自动召回：" + RECALL_ON_END);
        return lines;
    }
}
