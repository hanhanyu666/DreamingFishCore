package com.hhy.dreamingfishcore.gameplay.disaster_system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.DayPlan;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.EventType;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule.Schedule;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 天灾日程的世界侧：读写、按游戏日推进、当天播报、用完自动续排。
 *
 * <p>几个刻意的取舍：</p>
 * <ul>
 *   <li><b>日程是"世界事实"</b>，落在 {@code <存档>/dreamingfishcore/disaster_schedule.json}，
 *       重启/回档后仍然是同一份（玩家照着预告做准备才有意义）；</li>
 *   <li><b>只在跨天时做事</b>：每 tick 被调用，但先比日期，绝大多数 tick 直接返回；</li>
 *   <li><b>续排而不是无限加长</b>：日程走到最后一天时，以"结束日 +1"为起点再排 14 天，
 *       旧表丢掉（它是历史，不需要留）；这样文件永远只有 14 天，不会越长越大；</li>
 *   <li><b>软失败</b>：文件坏了回退到"重新排一份"并记录日志，绝不阻断开服。</li>
 * </ul>
 */
public final class DisasterService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String SUBDIR = "dreamingfishcore";
    private static final String FILE_NAME = "disaster_schedule.json";
    /** 一天多少 tick（原版常量，写在本地免得依赖别处的口径）。 */
    public static final long TICKS_PER_DAY = 24_000L;
    /** 播报与查询都用这个数：玩家可以提前知道"后 3 日"。 */
    public static final int PREVIEW_DAYS = 3;

    private static volatile Schedule schedule;
    /** 已经播报过哪一天；-1 表示还没播报过。 */
    private static volatile long announcedDay = -1L;
    /** 已经播报过哪一天的"明日预告"；用来避免同一个傍晚反复刷屏。 */
    private static volatile long warnedDay = -1L;

    private DisasterService() {
    }

    /** 当前游戏日（从 0 开始）。所有维度共享同一个时间，所以直接读主世界。 */
    public static long today(MinecraftServer server) {
        if (server == null) {
            return 0L;
        }
        return Math.floorDiv(server.overworld().getDayTime(), TICKS_PER_DAY);
    }

    public static Schedule schedule() {
        return schedule;
    }

    public static synchronized void clear() {
        schedule = null;
        announcedDay = -1L;
        warnedDay = -1L;
    }

    private static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(SUBDIR).resolve(FILE_NAME);
    }

    /**
     * 确保持有一份覆盖今天（以及之后若干天）的日程。
     *
     * <p>没有文件、文件损坏、或者日程已经不够长 → 重新排一份并落盘。</p>
     */
    public static synchronized void ensureLoaded(MinecraftServer server) {
        if (server == null) {
            return;
        }
        long day = today(server);
        if (schedule != null && schedule.covers(day)) {
            return;
        }
        Schedule loaded = read(server);
        if (loaded != null && loaded.covers(day)) {
            schedule = loaded;
            DreamingFishCore.LOGGER.info("[disaster] 读回天灾日程：第 {}~{} 天（种子 {}）",
                    loaded.firstDay(), loaded.lastDay(), loaded.seed());
            return;
        }
        // 没有可用日程：从今天开始排 14 天
        long seed = seedFor(server, day);
        schedule = DisasterSchedule.generate(seed, day, DisasterSchedule.DEFAULT_DAYS);
        save(server);
        DreamingFishCore.LOGGER.info("[disaster] 已生成天灾日程：第 {}~{} 天（种子 {}）",
                schedule.firstDay(), schedule.lastDay(), seed);
    }

    /**
     * 重新排一份日程（从今天起）。**仅用于服务器自测**：会丢掉当前日程，
     * 所以命令行那边把它限制在 3 级权限。
     */
    public static synchronized List<String> reroll(MinecraftServer server) {
        List<String> messages = new ArrayList<>();
        if (server == null) {
            messages.add("服务器尚未就绪");
            return messages;
        }
        long day = today(server);
        // 与自动排程的种子错开一档，测试时才能看出"确实换了一份表"
        long seed = seedFor(server, day) ^ 0x5DEECE66DL;
        schedule = DisasterSchedule.generate(seed, day, DisasterSchedule.DEFAULT_DAYS);
        announcedDay = -1L;     // 允许新表当天再播报一次
        save(server);
        messages.add("已重新排程：第 " + schedule.firstDay() + " ~ " + schedule.lastDay()
                + " 天（种子 " + seed + "）");
        return messages;
    }

    /**
     * 调整某一天的天灾（加一条 / 去一条）。**支持一天多条叠加**。
     *
     * <p>只改已存在的日程（日期越界返回 false）。改完立刻落盘——日程是世界事实；
     * 同时把 announcedDay / warnedDay 复位，让当天的播报与傍晚预告反映新内容。</p>
     */
    public static synchronized boolean setDayEvent(MinecraftServer server, long day,
                                                   EventType type, boolean add) {
        Schedule current = schedule;
        if (server == null || current == null || type == null || !current.covers(day)) {
            return false;
        }
        List<DayPlan> days = new ArrayList<>(current.days());
        int index = (int) (day - current.firstDay());
        List<EventType> events = new ArrayList<>(days.get(index).events());
        boolean changed;
        if (add) {
            changed = !events.contains(type);
            if (changed) {
                events.add(type);
            }
        } else {
            changed = events.remove(type);
        }
        if (!changed) {
            return false;
        }
        // 固定顺序，避免每次编辑后 UI 里的事件顺序乱跳
        events.sort(java.util.Comparator.comparingInt(Enum::ordinal));
        days.set(index, new DayPlan(day, events));
        schedule = new Schedule(current.seed(), current.firstDay(), days);
        announcedDay = -1L;
        warnedDay = -1L;
        save(server);
        DreamingFishCore.LOGGER.info("[disaster] 管理员手动调整第 {} 天：{} {} {}",
                day, add ? "加入" : "移除", type.name(), events);
        return true;
    }

    /** 今天的安排（没有就返回空计划，而不是 null，调用方少一层判空）。 */
    public static DayPlan todayPlan(MinecraftServer server) {
        ensureLoaded(server);
        long day = today(server);
        DayPlan plan = schedule == null ? null : schedule.forDay(day);
        return plan == null ? new DayPlan(day, List.of()) : plan;
    }

    /** 今天之后 {@code days} 天的安排（"提前知道后 3 日"）。 */
    public static List<DayPlan> upcoming(MinecraftServer server, int days) {
        ensureLoaded(server);
        if (schedule == null) {
            return List.of();
        }
        return schedule.upcoming(today(server), days);
    }

    /**
     * 每 tick 调用：只在**跨天**时做事。
     *
     * <p>跨天时做两件事：播报今天的安排；如果今天是日程最后一天，顺手续排下一段。</p>
     */
    public static synchronized void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ensureLoaded(server);
        long day = today(server);
        DayPlan plan = todayPlan(server);
        // 效果每 tick 都要推进（大尸潮分批、寒霜节流），所以放在跨天判断之前
        DisasterEffects.tick(server, plan);
        // 日落后播报明日预告：与跨天无关，所以也放在跨天判断之前
        maybeWarnTomorrow(server);
        if (day == announcedDay) {
            return;
        }
        announcedDay = day;
        announce(server, plan);
        // 跨天的一次性效果：雷雨天气、尸潮排期
        DisasterEffects.onDayStarted(server, plan);
        // 今天是最后一天 → 明天就没预告了，提前续排
        if (schedule != null && day >= schedule.lastDay()) {
            long nextFirstDay = schedule.lastDay() + 1L;
            long seed = seedFor(server, nextFirstDay);
            schedule = DisasterSchedule.generate(seed, nextFirstDay, DisasterSchedule.DEFAULT_DAYS);
            save(server);
            DreamingFishCore.LOGGER.info("[disaster] 天灾日程已续排：第 {}~{} 天（种子 {}）",
                    schedule.firstDay(), schedule.lastDay(), seed);
        }
    }

    /**
     * 日落后播报"明天的天灾"，让玩家有一整个晚上准备。
     *
     * <p>每个傍晚只播一次（用日期去重）；明天是平安日就不播，避免刷屏。</p>
     */
    private static void maybeWarnTomorrow(MinecraftServer server) {
        Schedule current = schedule;
        if (server == null || current == null) {
            return;
        }
        if (!DisasterTiming.isEveningWindow(server.overworld().getDayTime())) {
            return;
        }
        long day = today(server);
        if (warnedDay == day) {
            return;
        }
        warnedDay = day;
        DayPlan tomorrow = current.forDay(day + 1L);
        if (tomorrow == null || !tomorrow.hasEvents()) {
            return;
        }
        List<String> names = new ArrayList<>();
        tomorrow.events().forEach(event -> names.add(coloredName(event)));
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "§e[天灾] 明日预告：第 " + tomorrow.day() + " 天 " + String.join("§7、§e", names)
                        + "§7——今晚准备一下。"), false);
    }

    /** 广播当天安排。平安日也播一句，否则玩家会以为系统停了。 */
    private static void announce(MinecraftServer server, DayPlan plan) {
        List<String> events = new ArrayList<>();
        plan.events().forEach(event -> events.add(coloredName(event)));
        String text;
        if (events.isEmpty()) {
            text = "§7[天灾] 第 " + plan.day() + " 天：今日平安。";
        } else {
            text = "§c[天灾] 第 " + plan.day() + " 天：" + String.join("§7、§c", events)
                    + "§7（共 " + events.size() + " 项）";
        }
        server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
    }

    /** 给聊天栏/命令用的着色名。 */
    public static String coloredName(EventType type) {
        return switch (type) {
            case ZOMBIE_HORDE -> "§4大尸潮";
            case FROST_NIGHT -> "§b寒霜之夜";
            case STORM_DAY -> "§9风暴天";
        };
    }

    /**
     * 排程种子：世界种子 + 起始日混一下。
     *
     * <p>刻意不用时间种子——同一颗世界种子、同一段日期，重排出来的表必须一样，
     * 否则"重启后预告变了"会让玩家觉得被耍。</p>
     */
    static long seedFor(MinecraftServer server, long firstDay) {
        long worldSeed = server == null ? 0L : server.overworld().getSeed();
        return worldSeed ^ (firstDay * 0x9E3779B97F4A7C15L);
    }

    /** 供命令与方块显示：今天 + 后 N 日的可读文本。 */
    public static List<String> describe(MinecraftServer server, int previewDays) {
        List<String> lines = new ArrayList<>();
        DayPlan todayPlan = todayPlan(server);
        lines.add("§e今日 " + todayPlan.describe());
        List<DayPlan> upcoming = upcoming(server, previewDays);
        if (upcoming.isEmpty()) {
            lines.add("§7（日程已到末尾，下一次跨天会自动续排）");
        } else {
            lines.add("§7后 " + upcoming.size() + " 日：");
            upcoming.forEach(plan -> lines.add("  §f" + plan.describe()));
        }
        Schedule current = schedule;
        if (current != null) {
            lines.add("§8日程覆盖：第 " + current.firstDay() + " ~ " + current.lastDay()
                    + " 天（种子 " + current.seed() + "）");
        }
        return lines;
    }

    // ---------------------------------------------------------------- 持久化

    private static Schedule read(MinecraftServer server) {
        Path path = path(server);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonObject json = element.getAsJsonObject();
            Schedule parsed = Schedule.fromJson(json);
            if (parsed == null || parsed.days().isEmpty()) {
                return null;
            }
            return parsed;
        } catch (IOException | RuntimeException exception) {
            DreamingFishCore.LOGGER.error("[disaster] 天灾日程读取失败，将重新排一份：{}", path, exception);
            return null;
        }
    }

    private static void save(MinecraftServer server) {
        Schedule current = schedule;
        if (server == null || current == null) {
            return;
        }
        Path path = path(server);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(current.toJson()) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.error("[disaster] 天灾日程写入失败：{}", path, exception);
        }
    }
}
