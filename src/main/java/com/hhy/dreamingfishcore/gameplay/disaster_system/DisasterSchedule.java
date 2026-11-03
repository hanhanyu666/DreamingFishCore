package com.hhy.dreamingfishcore.gameplay.disaster_system;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hhy.dreamingfishcore.gameplay.raid_system.RaidRandom;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 天灾日程：提前排好的一串"哪一天会有什么天灾"。
 *
 * <p>设计目标是**让玩家能提前做准备**，所以日程必须：</p>
 * <ul>
 *   <li><b>确定性</b>：同一个种子排出来的日程完全一样（服务器重启、回档都不会变卦）；</li>
 *   <li><b>可读</b>：按游戏日排，一天可以没有天灾，也可以有多条（数据结构支持多事件）；</li>
 *   <li><b>不憋屈</b>：不让同一种天灾连着两天来，而且**14 天里至少来一次大尸潮**
 *       ——否则可能一两周都碰不到主菜，玩家会以为系统坏了。</li>
 * </ul>
 *
 * <p>随机只走 {@link RaidRandom}（SplitMix64 + 标签哈希），禁止 {@code java.util.Random} 与时间种子，
 * 这样"这周的天灾表"可以当作世界事实长期保存与复现。</p>
 */
public final class DisasterSchedule {

    /** 天灾类型。权重越高越常见。 */
    public enum EventType {
        /** 大尸潮：最稀有，也最危险。 */
        ZOMBIE_HORDE("大尸潮"),
        /** 寒霜之夜：夜间严寒。 */
        FROST_NIGHT("寒霜之夜"),
        /** 风暴天：大风与雷暴。 */
        STORM_DAY("风暴天");

        private final String displayName;

        EventType(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /** 默认排多少天。 */
    public static final int DEFAULT_DAYS = 14;

    /**
     * 各类天灾的权重（相对值）。
     *
     * <p>大尸潮刻意比另外两种稀有：它是"重头戏"，天天来就不叫重头戏了。</p>
     */
    private static final Map<EventType, Integer> WEIGHTS = new EnumMap<>(EventType.class);

    static {
        WEIGHTS.put(EventType.ZOMBIE_HORDE, 20);
        WEIGHTS.put(EventType.FROST_NIGHT, 40);
        WEIGHTS.put(EventType.STORM_DAY, 40);
    }

    /** "这天没有天灾"的权重。与上面三个类型一起参与加权抽取。 */
    static final int NO_EVENT_WEIGHT = 45;

    /** 一天的计划。{@code events} 为空表示晴天（太平日子）。 */
    public record DayPlan(long day, List<EventType> events) {

        public DayPlan {
            events = events == null ? List.of() : List.copyOf(events);
        }

        public boolean hasEvents() {
            return !events.isEmpty();
        }

        public boolean contains(EventType type) {
            return events.contains(type);
        }

        /** 给人看的当日摘要，例如「第 12 天：寒霜之夜」。 */
        public String describe() {
            if (events.isEmpty()) {
                return "第 " + day + " 天：平安";
            }
            List<String> names = new ArrayList<>();
            events.forEach(event -> names.add(event.displayName()));
            return "第 " + day + " 天：" + String.join("、", names);
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("day", day);
            JsonArray eventsJson = new JsonArray();
            events.forEach(event -> eventsJson.add(event.name()));
            json.add("events", eventsJson);
            return json;
        }

        static DayPlan fromJson(JsonObject json) {
            if (json == null || !json.has("day")) {
                return null;
            }
            List<EventType> events = new ArrayList<>();
            JsonElement eventsJson = json.get("events");
            if (eventsJson != null && eventsJson.isJsonArray()) {
                for (JsonElement element : eventsJson.getAsJsonArray()) {
                    try {
                        events.add(EventType.valueOf(element.getAsString()));
                    } catch (RuntimeException ignored) {
                        // 单条坏数据跳过：日程不能因为一个拼错的类型整个作废
                    }
                }
            }
            return new DayPlan(json.get("day").getAsLong(), events);
        }
    }

    /** 一整份日程。{@code firstDay} 是这份日程覆盖的第一天（含）。 */
    public record Schedule(long seed, long firstDay, List<DayPlan> days) {

        public Schedule {
            days = days == null ? List.of() : List.copyOf(days);
        }

        public long lastDay() {
            return firstDay + days.size() - 1L;
        }

        public boolean covers(long day) {
            return day >= firstDay && day <= lastDay();
        }

        /** 某一天的安排；不在覆盖范围内返回 null。 */
        public DayPlan forDay(long day) {
            long index = day - firstDay;
            if (index < 0 || index >= days.size()) {
                return null;
            }
            return days.get((int) index);
        }

        /**
         * 从 {@code today} 之后的 {@code count} 天（**不含今天**，与"提前知道后 3 日"一致）。
         *
         * <p>超出日程范围的部分会被截掉——调用方可以用 {@link #covers(long)} 判断要不要重排。</p>
         */
        public List<DayPlan> upcoming(long today, int count) {
            List<DayPlan> result = new ArrayList<>();
            for (int offset = 1; offset <= Math.max(0, count); offset++) {
                DayPlan plan = forDay(today + offset);
                if (plan != null) {
                    result.add(plan);
                }
            }
            return result;
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("schema_version", 1);
            json.addProperty("seed", seed);
            json.addProperty("first_day", firstDay);
            JsonArray daysJson = new JsonArray();
            days.forEach(day -> daysJson.add(day.toJson()));
            json.add("days", daysJson);
            return json;
        }

        /** 读回来；{@code legacyFirstDay} 只在文件里缺 {@code first_day} 时兜底。 */
        public static Schedule fromJson(JsonObject json) {
            if (json == null) {
                return null;
            }
            long seed = json.has("seed") ? json.get("seed").getAsLong() : 0L;
            long firstDay = json.has("first_day") ? json.get("first_day").getAsLong() : 0L;
            List<DayPlan> days = new ArrayList<>();
            JsonElement daysJson = json.get("days");
            if (daysJson != null && daysJson.isJsonArray()) {
                for (JsonElement element : daysJson.getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    DayPlan plan = DayPlan.fromJson(element.getAsJsonObject());
                    if (plan != null) {
                        days.add(plan);
                    }
                }
            }
            return new Schedule(seed, firstDay, days);
        }
    }

    private DisasterSchedule() {
    }

    public static Schedule generate(long seed, long firstDay) {
        return generate(seed, firstDay, DEFAULT_DAYS);
    }

    /**
     * 排出 {@code count} 天的日程。
     *
     * <p>三条规则：不连续同类型（空日会打断"连续"）、14 天内至少一次大尸潮、其余按权重抽。</p>
     */
    public static Schedule generate(long seed, long firstDay, int count) {
        int days = Math.max(1, count);
        RaidRandom random = new RaidRandom(seed);

        List<DayPlan> plans = new ArrayList<>(days);
        EventType previous = null;
        boolean hordeSeen = false;

        for (int index = 0; index < days; index++) {
            long day = firstDay + index;
            // 候选：三个天灾类型（排除昨天刚来过的那种）+ 一个"平安"档
            List<Candidate> candidates = new ArrayList<>();
            for (EventType type : EventType.values()) {
                if (type == previous) {
                    continue;   // 不连续同类型
                }
                candidates.add(new Candidate(type, WEIGHTS.getOrDefault(type, 10)));
            }
            candidates.add(new Candidate(null, NO_EVENT_WEIGHT));

            List<Candidate> pool = new ArrayList<>(candidates);
            var picked = random.pickWeighted(pool, Candidate::weight);
            Candidate candidate = picked.orElse(null);
            if (candidate == null || candidate.type() == null) {
                plans.add(new DayPlan(day, List.of()));
                previous = null;    // 空日打断连续约束
                continue;
            }
            plans.add(new DayPlan(day, List.of(candidate.type())));
            previous = candidate.type();
            hordeSeen |= candidate.type() == EventType.ZOMBIE_HORDE;
        }

        if (!hordeSeen) {
            plans = forceHorde(plans);
        }
        return new Schedule(seed, firstDay, plans);
    }

    /**
     * 强行塞一次大尸潮。
     *
     * <p>优先挑**空日**（不挤掉已有安排），其次挑最后一天；塞完把相邻两天的同类型清掉，
     * 保持"不连续同类型"这条规则不被自己破坏。</p>
     */
    private static List<DayPlan> forceHorde(List<DayPlan> plans) {
        List<DayPlan> next = new ArrayList<>(plans);
        int target = -1;
        for (int index = 0; index < next.size(); index++) {
            if (!next.get(index).hasEvents()) {
                target = index;
                break;
            }
        }
        if (target < 0) {
            target = next.size() - 1;
        }
        next.set(target, new DayPlan(next.get(target).day(), List.of(EventType.ZOMBIE_HORDE)));
        for (int neighbor : new int[]{target - 1, target + 1}) {
            if (neighbor < 0 || neighbor >= next.size()) {
                continue;
            }
            DayPlan plan = next.get(neighbor);
            if (plan.contains(EventType.ZOMBIE_HORDE)) {
                List<EventType> remaining = new ArrayList<>(plan.events());
                remaining.removeIf(type -> type == EventType.ZOMBIE_HORDE);
                next.set(neighbor, new DayPlan(plan.day(), remaining));
            }
        }
        return next;
    }

    /** 加权抽取用的临时结构（{@code type == null} 表示平安日）。 */
    private record Candidate(EventType type, int weight) {
    }
}
