package com.hhy.dreamingfishcore.gameplay.raid_system;

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
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 本局参与者名单：谁在图里、谁撤了、谁死了、谁掉线了。
 *
 * <p>规则由服主定（2026-10-04）：</p>
 * <ul>
 *   <li><b>只有开局时在线的玩家算本局参与者</b>。中途进服的人不是参与者，
 *       站进撤离点会被明确告知"不是本局参与者"，而不是静默无事发生；</li>
 *   <li><b>掉线给 5 分钟宽限</b>：宽限内回来恢复为"在图里"；超时按**未撤离**结算
 *       （背包与位置都保留，但不再算在图人数里，也不再能撤离）；</li>
 *   <li><b>撤离成功</b>标记 EXTRACTED、<b>死亡</b>标记 DEAD，两者即使重新登录也不再变回在图里
 *       （一局里撤出去/死了就不该继续搜）；</li>
 *   <li>"本局是否已经没人留在图里"只看 IN_RAID 是否为 0（撤了、死了、掉线超时都算出去了）。</li>
 * </ul>
 *
 * <p>名单与对局号绑定，存 {@code <存档>/dreamingfishcore/raid_roster.json}：换新局重来、重启读回。</p>
 */
public final class RaidRoster {

    /** 掉线宽限：5 分钟。想改就把它挪进 RaidConfig。 */
    public static final long GRACE_MILLIS = 5L * 60L * 1000L;

    /** 一个玩家在本局里的状态。 */
    public enum State {
        /** 还在图里。 */
        IN_RAID,
        /** 已撤离（带走了背包）。 */
        EXTRACTED,
        /** 已死亡（战利品留在尸体里）。 */
        DEAD,
        /** 掉线中，还在宽限内，可以回来。 */
        LEFT,
        /** 掉线超时，本局对他结束（按未撤离结算）。 */
        ABANDONED
    }

    /** 纯逻辑部分：不碰 Minecraft，方便单测。 */
    public static final class Roster {

        private final Map<UUID, State> states = new TreeMap<>();
        private final Map<UUID, String> names = new TreeMap<>();
        private final Map<UUID, Long> leftAt = new TreeMap<>();

        /** 登记为参与者（开局时批量调用；之后不再自动登记，因为中途进来不算）。 */
        public void join(UUID id, String name) {
            if (id == null) {
                return;
            }
            names.put(id, name == null ? "" : name);
            State existing = states.get(id);
            if (existing == null) {
                states.put(id, State.IN_RAID);
                leftAt.remove(id);
            }
            // 已经是 EXTRACTED / DEAD / LEFT / ABANDONED 的人不因为再登记而改变状态
        }

        /** 掉线：进入宽限计时。 */
        public void markLeft(UUID id, long nowMillis) {
            if (id != null && states.get(id) == State.IN_RAID) {
                states.put(id, State.LEFT);
                leftAt.put(id, nowMillis);
            }
        }

        /**
         * 登录：宽限内回到图里；超时或本来不是参与者则不动。
         *
         * @return true 表示"回来了"（调用方可以给他提示）
         */
        public boolean handleLogin(UUID id, long nowMillis) {
            if (id == null || states.get(id) != State.LEFT) {
                return false;
            }
            long left = leftAt.getOrDefault(id, nowMillis);
            if (nowMillis - left > GRACE_MILLIS) {
                states.put(id, State.ABANDONED);
                leftAt.remove(id);
                return false;
            }
            states.put(id, State.IN_RAID);
            leftAt.remove(id);
            return true;
        }

        /** 宽限到期检查（每 tick 调一次）：把超时的 LEFT 变成 ABANDONED。 */
        public int expireGrace(long nowMillis) {
            int expired = 0;
            for (Map.Entry<UUID, State> entry : new TreeMap<>(states).entrySet()) {
                if (entry.getValue() != State.LEFT) {
                    continue;
                }
                long left = leftAt.getOrDefault(entry.getKey(), nowMillis);
                if (nowMillis - left > GRACE_MILLIS) {
                    states.put(entry.getKey(), State.ABANDONED);
                    leftAt.remove(entry.getKey());
                    expired++;
                }
            }
            return expired;
        }

        public void set(UUID id, State state) {
            if (id != null && states.containsKey(id)) {
                states.put(id, state);
                leftAt.remove(id);
            }
        }

        public boolean isParticipant(UUID id) {
            return id != null && states.containsKey(id);
        }

        public boolean isInRaid(UUID id) {
            return id != null && states.get(id) == State.IN_RAID;
        }

        public boolean isEmpty() {
            return states.isEmpty();
        }

        public int size() {
            return states.size();
        }

        public int count(State state) {
            return (int) states.values().stream().filter(value -> value == state).count();
        }

        public State stateOf(UUID id) {
            return states.get(id);
        }

        public String nameOf(UUID id) {
            return names.getOrDefault(id, "");
        }

        /** 是否所有人都已经不在图里（撤离、死亡、掉线超时），且名单非空。 */
        public boolean nobodyLeftInRaid() {
            return !states.isEmpty() && count(State.IN_RAID) == 0;
        }

        public Map<UUID, State> states() {
            return Map.copyOf(states);
        }

        public JsonObject toJson() {
            JsonObject players = new JsonObject();
            states.forEach((id, state) -> {
                JsonObject entry = new JsonObject();
                entry.addProperty("state", state.name());
                entry.addProperty("name", names.getOrDefault(id, ""));
                if (leftAt.containsKey(id)) {
                    entry.addProperty("left_at", leftAt.get(id));
                }
                players.add(id.toString(), entry);
            });
            return players;
        }

        public static Roster fromJson(JsonObject json) {
            Roster roster = new Roster();
            if (json == null) {
                return roster;
            }
            json.entrySet().forEach(entry -> {
                if (!entry.getValue().isJsonObject()) {
                    return;
                }
                try {
                    UUID id = UUID.fromString(entry.getKey());
                    JsonObject value = entry.getValue().getAsJsonObject();
                    roster.states.put(id, State.valueOf(value.get("state").getAsString()));
                    roster.names.put(id, value.has("name") ? value.get("name").getAsString() : "");
                    if (value.has("left_at")) {
                        roster.leftAt.put(id, value.get("left_at").getAsLong());
                    }
                } catch (RuntimeException ignored) {
                    // 单条坏数据跳过，不影响其余名单
                }
            });
            return roster;
        }

        /** 供命令与日志打印的摘要。 */
        public String describe() {
            return "参与者 " + size() + "（在图中 " + count(State.IN_RAID) + "，已撤离 "
                    + count(State.EXTRACTED) + "，已死亡 " + count(State.DEAD) + "，掉线中 "
                    + count(State.LEFT) + "，超时未撤离 " + count(State.ABANDONED) + "）";
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String SAVE_SUBDIR = "dreamingfishcore";
    private static final String FILE_NAME = "raid_roster.json";

    private static volatile Roster roster = new Roster();
    private static volatile long boundRaidId = -1L;

    private RaidRoster() {
    }

    public static Roster roster() {
        return roster;
    }

    public static synchronized void clear() {
        roster = new Roster();
        boundRaidId = -1L;
    }

    private static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(SAVE_SUBDIR).resolve(FILE_NAME);
    }

    /** 让名单与"当前对局"对齐：换局就重来，同一局就沿用（服务端启动时从文件读回）。 */
    public static synchronized void ensureForCurrentRaid(MinecraftServer server) {
        RaidManifest manifest = RaidService.current().orElse(null);
        if (server == null || manifest == null) {
            clear();
            return;
        }
        if (boundRaidId == manifest.raidId()) {
            return;
        }
        roster = load(server, manifest.raidId());
        boundRaidId = manifest.raidId();
    }

    /** 开局时登记当前在线玩家为参与者。 */
    public static synchronized void registerOnlinePlayers(MinecraftServer server) {
        ensureForCurrentRaid(server);
        if (RaidService.current().isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            roster.join(player.getUUID(), player.getGameProfile().getName());
        }
        save(server);
        DreamingFishCore.LOGGER.info("[raid_roster] 开局登记参与者：{}", roster.describe());
    }

    /** 掉线。 */
    public static synchronized void markLeft(MinecraftServer server, ServerPlayer player) {
        ensureForCurrentRaid(server);
        roster.markLeft(player.getUUID(), System.currentTimeMillis());
        save(server);
    }

    /** 登录：宽限内回来则恢复为在图里。 */
    public static synchronized boolean handleLogin(MinecraftServer server, ServerPlayer player) {
        ensureForCurrentRaid(server);
        boolean back = roster.handleLogin(player.getUUID(), System.currentTimeMillis());
        if (back) {
            save(server);
        }
        return back;
    }

    /** 每 tick：处理掉线超时。 */
    public static synchronized int tickGrace(MinecraftServer server) {
        if (server == null || boundRaidId < 0L) {
            return 0;
        }
        int expired = roster.expireGrace(System.currentTimeMillis());
        if (expired > 0) {
            save(server);
            DreamingFishCore.LOGGER.info("[raid_roster] {} 人掉线超时，按未撤离结算：{}",
                    expired, roster.describe());
        }
        return expired;
    }

    /** 状态变更（撤离 / 死亡）。 */
    public static synchronized void mark(MinecraftServer server, ServerPlayer player, State state) {
        ensureForCurrentRaid(server);
        if (RaidService.current().isEmpty()) {
            return;
        }
        roster.join(player.getUUID(), player.getGameProfile().getName());
        roster.set(player.getUUID(), state);
        save(server);
    }

    private static Roster load(MinecraftServer server, long raidId) {
        Path path = path(server);
        if (!Files.isRegularFile(path)) {
            return new Roster();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                return new Roster();
            }
            JsonObject json = element.getAsJsonObject();
            if (!json.has("raid_id") || json.get("raid_id").getAsLong() != raidId) {
                return new Roster();     // 上一局的名单，丢弃
            }
            return Roster.fromJson(json.has("players") ? json.getAsJsonObject("players") : null);
        } catch (IOException | RuntimeException exception) {
            DreamingFishCore.LOGGER.error("[raid_roster] 名单读取失败：{}", path, exception);
            return new Roster();
        }
    }

    private static void save(MinecraftServer server) {
        RaidManifest manifest = RaidService.current().orElse(null);
        if (server == null || manifest == null) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("raid_id", manifest.raidId());
        root.add("players", roster.toJson());
        Path path = path(server);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("[raid_roster] 名单写入失败：{}", path, exception);
        }
    }

    /** 供命令打印（含每个人当前状态）。 */
    public static synchronized java.util.List<String> describe() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("本局" + roster.describe());
        roster.states().forEach((id, state) -> lines.add("  " + roster.nameOf(id) + "  " + state));
        return lines;
    }
}
