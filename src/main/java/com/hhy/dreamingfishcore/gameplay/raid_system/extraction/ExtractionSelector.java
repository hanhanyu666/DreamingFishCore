package com.hhy.dreamingfishcore.gameplay.raid_system.extraction;

import com.hhy.dreamingfishcore.gameplay.raid_system.RaidRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 撤离点选择器（设计稿 §5）：从候选撤离点里筛出本局开放的那些。
 *
 * <p>严格按设计稿 §5.3 的顺序：收集候选 → 按条件筛掉 → 按出生组筛掉 → 按权重抽 →
 * 查与出生点的距离 → 保证至少有一个保底撤离点。区别是这里**不做"抽不中再重抽"的循环**：
 * 筛完就抽，抽不满就按实际数量交差并记原因。</p>
 *
 * <p>三类不变量：</p>
 * <ol>
 *   <li><b>永远有路可走</b>：至少一个"每局必开"的撤离点；一个候选都没有时也如实报告而不是崩；</li>
 *   <li><b>确定性</b>：候选按 id 排序后处理，权重抽取走 {@link RaidRandom}，同种子同结果；</li>
 *   <li><b>可解释</b>：每个被筛掉的点都带一条原因，方便服主查"为什么这个撤离点没开"。</li>
 * </ol>
 */
public final class ExtractionSelector {

    /** 撤离点类型（设计稿 §5.1）。 */
    public enum Kind {
        /** 每局必定开放，作为保底。 */
        FIXED,
        /** 普通随机点：从候选里按权重抽一部分。 */
        RANDOM,
        /** 条件点：需要满足 requiredTags 才可能开放。 */
        CONDITIONAL,
        /** 单次点：开放后只能用有限次数（车辆之类）。 */
        SINGLE_USE
    }

    /**
     * 一个候选撤离点。
     *
     * @param id                    唯一 id
     * @param kind                  类型
     * @param weight                被抽中的权重
     * @param x                     坐标 X（距离按水平面算）
     * @param z                     坐标 Z
     * @param requiredTags          需要的条件标签（例如 factory_power_on）
     * @param forbiddenSpawnGroups  不允许使用它的出生组
     * @param minDistanceFromSpawn  与出生点的最小距离（0 表示不限）
     * @param maxDistanceFromSpawn  与出生点的最大距离（0 表示不限）
     * @param maxUses               可用次数（0 表示不限）
     */
    public record Candidate(String id, Kind kind, int weight, double x, double z,
                            Set<String> requiredTags, Set<String> forbiddenSpawnGroups,
                            double minDistanceFromSpawn, double maxDistanceFromSpawn, int maxUses) {

        public Candidate {
            id = id == null ? "" : id;
            kind = kind == null ? Kind.RANDOM : kind;
            requiredTags = requiredTags == null ? Set.of() : Set.copyOf(new TreeSet<>(requiredTags));
            forbiddenSpawnGroups = forbiddenSpawnGroups == null
                    ? Set.of() : Set.copyOf(new TreeSet<>(forbiddenSpawnGroups));
        }

        public double distanceTo(double otherX, double otherZ) {
            double dx = x - otherX;
            double dz = z - otherZ;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    /**
     * 本局的选择规则。
     *
     * @param randomCount        普通随机点最多开几个
     * @param availableTags      本局已满足的条件标签
     * @param spawnGroup         本局出生组（空串表示不限）
     * @param spawnX             出生点 X（用于距离判定）
     * @param spawnZ             出生点 Z
     */
    public record Rules(int randomCount, Set<String> availableTags, String spawnGroup,
                        double spawnX, double spawnZ) {

        public Rules {
            randomCount = Math.max(0, randomCount);
            availableTags = availableTags == null ? Set.of() : Set.copyOf(new TreeSet<>(availableTags));
            spawnGroup = spawnGroup == null ? "" : spawnGroup;
        }

        public static Rules of(int randomCount) {
            return new Rules(randomCount, Set.of(), "", 0.0D, 0.0D);
        }
    }

    /** 被筛掉的原因。 */
    public record Rejection(String id, String code, String message) {

        public static Rejection of(String id, String code, String message) {
            return new Rejection(id, code, message);
        }
    }

    /**
     * 选择结果。
     *
     * @param alwaysOpen 每局必开的（没有固定点时会从随机抽中的里提升一个进来）
     * @param randomOpen 本局随机开放、且**没有被提升为保底**的那些（两个列表不重叠，便于直接展示）
     * @param rejections 被筛掉的原因
     */
    public record Result(List<String> alwaysOpen, List<String> randomOpen, List<Rejection> rejections) {

        public Result {
            alwaysOpen = List.copyOf(alwaysOpen);
            randomOpen = List.copyOf(randomOpen);
            rejections = List.copyOf(rejections);
        }

        /** 本局全部可用的撤离点（保底在前）。 */
        public List<String> all() {
            List<String> ids = new ArrayList<>(alwaysOpen);
            randomOpen.stream().filter(id -> !ids.contains(id)).forEach(ids::add);
            return List.copyOf(ids);
        }

        public boolean hasAlwaysOpen() {
            return !alwaysOpen.isEmpty();
        }

        public List<Rejection> rejectionsOf(String code) {
            return rejections.stream().filter(rejection -> rejection.code().equals(code)).toList();
        }
    }

    private ExtractionSelector() {
    }

    public static Result select(List<Candidate> candidates, Rules rules, RaidRandom random) {
        List<Rejection> rejections = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) {
            rejections.add(Rejection.of("", "NO_CANDIDATES", "没有任何候选撤离点"));
            return new Result(List.of(), List.of(), rejections);
        }

        // 顺序确定：按 id 排序，保证同种子同结果
        List<Candidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparing(Candidate::id));

        List<Candidate> fixed = new ArrayList<>();
        List<Candidate> pool = new ArrayList<>();
        for (Candidate candidate : ordered) {
            if (candidate.kind() == Kind.FIXED) {
                fixed.add(candidate);
                continue;
            }
            if (!eligible(candidate, rules, rejections)) {
                continue;
            }
            pool.add(candidate);
        }

        // 按权重抽，不重复；池子是排好序的，抽取结果确定
        List<Candidate> chosen = new ArrayList<>();
        List<Candidate> remaining = new ArrayList<>(pool);
        while (chosen.size() < rules.randomCount() && !remaining.isEmpty()) {
            var pick = random.pickWeighted(remaining, Candidate::weight);
            if (pick.isEmpty()) {
                break;
            }
            Candidate candidate = pick.get();
            chosen.add(candidate);
            remaining.remove(candidate);
        }
        chosen.sort(Comparator.comparing(Candidate::id));

        List<String> alwaysOpen = new ArrayList<>();
        fixed.forEach(candidate -> alwaysOpen.add(candidate.id()));

        // 保底：一个必开点都没有时，从本局开放的点里提升一个；再没有就把最靠前的合格候选提上来
        if (alwaysOpen.isEmpty()) {
            if (!chosen.isEmpty()) {
                Candidate promoted = chosen.remove(0);
                alwaysOpen.add(promoted.id());
                rejections.add(Rejection.of(promoted.id(), "PROMOTED_TO_FALLBACK",
                        "本局没有固定撤离点，已把它提升为保底撤离点"));
            } else if (!pool.isEmpty()) {
                Candidate promoted = pool.get(0);
                alwaysOpen.add(promoted.id());
                rejections.add(Rejection.of(promoted.id(), "PROMOTED_TO_FALLBACK",
                        "本局没有固定撤离点且抽签没抽到，已把它提升为保底撤离点"));
            } else {
                rejections.add(Rejection.of("", "NO_FALLBACK",
                        "没有任何可用撤离点——检查候选点的条件标签、出生组与距离设置"));
            }
        }

        List<String> randomOpen = new ArrayList<>();
        chosen.forEach(candidate -> randomOpen.add(candidate.id()));
        return new Result(alwaysOpen, randomOpen, rejections);
    }

    /** 单个候选是否可能开放；不满足就写一条原因。 */
    private static boolean eligible(Candidate candidate, Rules rules, List<Rejection> rejections) {
        if (candidate.weight() <= 0) {
            rejections.add(Rejection.of(candidate.id(), "ZERO_WEIGHT", "权重为 0，不参与抽取"));
            return false;
        }
        if (!rules.availableTags().containsAll(candidate.requiredTags())) {
            Set<String> missing = new TreeSet<>(candidate.requiredTags());
            missing.removeAll(rules.availableTags());
            rejections.add(Rejection.of(candidate.id(), "TAG_MISSING", "条件未满足：" + missing));
            return false;
        }
        if (!rules.spawnGroup().isEmpty()
                && candidate.forbiddenSpawnGroups().contains(rules.spawnGroup())) {
            rejections.add(Rejection.of(candidate.id(), "SPAWN_GROUP_FORBIDDEN",
                    "该点不允许出生组 " + rules.spawnGroup() + " 使用"));
            return false;
        }
        double distance = candidate.distanceTo(rules.spawnX(), rules.spawnZ());
        if (candidate.minDistanceFromSpawn() > 0.0D && distance < candidate.minDistanceFromSpawn()) {
            rejections.add(Rejection.of(candidate.id(), "TOO_CLOSE",
                    "离出生点太近（" + Math.round(distance) + " < "
                            + Math.round(candidate.minDistanceFromSpawn()) + "）"));
            return false;
        }
        if (candidate.maxDistanceFromSpawn() > 0.0D && distance > candidate.maxDistanceFromSpawn()) {
            rejections.add(Rejection.of(candidate.id(), "TOO_FAR",
                    "离出生点太远（" + Math.round(distance) + " > "
                            + Math.round(candidate.maxDistanceFromSpawn()) + "）"));
            return false;
        }
        return true;
    }

    /** 供命令与日志打印：本局开放了哪些撤离点、各自类型与可用次数。 */
    public static List<String> describe(List<Candidate> candidates, Result result) {
        Map<String, Candidate> byId = new LinkedHashMap<>();
        if (candidates != null) {
            candidates.forEach(candidate -> byId.put(candidate.id(), candidate));
        }
        List<String> lines = new ArrayList<>();
        for (String id : result.all()) {
            Candidate candidate = byId.get(id);
            if (candidate == null) {
                lines.add("  " + id + "（候选已不存在）");
                continue;
            }
            lines.add("  " + id + "（" + candidate.kind()
                    + (result.alwaysOpen().contains(id) ? "，保底" : "，本局随机开放")
                    + (candidate.maxUses() > 0 ? "，可用 " + candidate.maxUses() + " 次" : "")
                    + (candidate.requiredTags().isEmpty() ? "" : "，条件 " + candidate.requiredTags())
                    + "）");
        }
        return lines;
    }
}
