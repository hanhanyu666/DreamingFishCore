package com.hhy.dreamingfishcore.gameplay.raid_system.loot;

import com.hhy.dreamingfishcore.gameplay.raid_system.RaidRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 锚点激活器：从候选点里挑出"本局真的会出现"的那些（设计稿 §7.4）。
 *
 * <p>对应设计稿强调的分离里的第一条：**点位是否激活，与点位生成什么物品，必须分开**。
 * 本类只管"激活哪些"，物品由 {@link LootAllocator} 管。</p>
 *
 * <p>三条硬要求：</p>
 * <ol>
 *   <li><b>不许全部堆在一个角落</b>：每个子区域（group）至少激活 1 个、最多 {@code maxPerGroup} 个，
 *       且激活点之间保持 {@code minDistance} 间距；</li>
 *   <li><b>确定性</b>：候选点按 id 排序后处理，权重抽取走 {@link RaidRandom}，同种子同结果；</li>
 *   <li><b>必须能停下来</b>：候选点被间距/分组上限挡住时立刻收尾，不做"抽不到就重抽"的循环。</li>
 * </ol>
 */
public final class AnchorActivator {

    /** 一个候选点。距离判定只需要水平面（X/Z），所以不要求完整坐标。 */
    public record Candidate(String id, String group, int weight, double x, double z) {

        public Candidate {
            id = id == null ? "" : id;
            group = group == null || group.isBlank() ? "" : group;
        }

        public double distanceSquaredTo(Candidate other) {
            double dx = x - other.x;
            double dz = z - other.z;
            return dx * dx + dz * dz;
        }
    }

    /**
     * 激活规则。
     *
     * @param minCount    至少要激活几个（受候选数量限制）
     * @param maxCount    最多激活几个
     * @param maxPerGroup 每个子区域最多几个（0 表示不限）
     * @param minDistance 激活点之间的最小水平间距（格；0 表示不限）
     */
    public record Rules(int minCount, int maxCount, int maxPerGroup, double minDistance) {

        public Rules {
            minCount = Math.max(0, minCount);
            maxCount = Math.max(minCount, maxCount);
            maxPerGroup = Math.max(0, maxPerGroup);
            minDistance = Math.max(0.0D, minDistance);
        }

        public static Rules of(int minCount, int maxCount) {
            return new Rules(minCount, maxCount, 0, 0.0D);
        }
    }

    public record Problem(String candidateId, String code, String message) {

        public static Problem of(String candidateId, String code, String message) {
            return new Problem(candidateId == null ? "" : candidateId, code, message);
        }
    }

    public record Result(List<String> selected, List<String> skippedByDistance, List<String> skippedByGroup,
                         Map<String, Integer> perGroup, List<Problem> problems) {

        public Result {
            selected = List.copyOf(selected);
            skippedByDistance = List.copyOf(skippedByDistance);
            skippedByGroup = List.copyOf(skippedByGroup);
            perGroup = Map.copyOf(perGroup);
            problems = List.copyOf(problems);
        }

        public boolean isEmpty() {
            return selected.isEmpty();
        }
    }

    private AnchorActivator() {
    }

    public static Result activate(List<Candidate> candidates, Rules rules, RaidRandom random) {
        List<Problem> problems = new ArrayList<>();
        List<String> selected = new ArrayList<>();
        List<String> skippedByDistance = new ArrayList<>();
        List<String> skippedByGroup = new ArrayList<>();
        Map<String, Integer> perGroup = new TreeMap<>();

        if (candidates == null || candidates.isEmpty() || rules == null || rules.maxCount() <= 0) {
            return new Result(selected, skippedByDistance, skippedByGroup, perGroup, problems);
        }

        // 顺序确定：先按 id 排序，再按分组归类；分组也按名字排序
        List<Candidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparing(Candidate::id));
        Map<String, List<Candidate>> byGroup = new LinkedHashMap<>();
        for (Candidate candidate : ordered) {
            if (candidate.weight() <= 0) {
                continue;
            }
            byGroup.computeIfAbsent(candidate.group(), key -> new ArrayList<>()).add(candidate);
        }

        List<Candidate> chosen = new ArrayList<>();

        // 第一轮：让每个子区域至少出一个（设计稿 §3.3 的目的：每个区域都有探索理由）
        for (Map.Entry<String, List<Candidate>> entry : byGroup.entrySet()) {
            if (chosen.size() >= rules.maxCount()) {
                break;
            }
            if (chosen.size() >= rules.minCount()) {
                break;
            }
            List<Candidate> pool = new ArrayList<>();
            for (Candidate candidate : entry.getValue()) {
                if (canPlace(candidate, chosen, rules, skippedByDistance, skippedByGroup, perGroup)) {
                    pool.add(candidate);
                }
            }
            random.pickWeighted(pool, Candidate::weight).ifPresent(candidate -> {
                chosen.add(candidate);
                perGroup.merge(candidate.group(), 1, Integer::sum);
            });
        }

        // 第二轮：按权重补足到 maxCount
        int guard = 0;
        while (chosen.size() < rules.maxCount()) {
            List<Candidate> pool = new ArrayList<>();
            for (Candidate candidate : ordered) {
                if (candidate.weight() <= 0 || chosen.contains(candidate)) {
                    continue;
                }
                if (canPlace(candidate, chosen, rules, skippedByDistance, skippedByGroup, perGroup)) {
                    pool.add(candidate);
                }
            }
            if (pool.isEmpty()) {
                break;      // 被间距或分组上限挡住：收尾，不重抽
            }
            var pick = random.pickWeighted(pool, Candidate::weight);
            if (pick.isEmpty()) {
                break;
            }
            Candidate candidate = pick.get();
            chosen.add(candidate);
            perGroup.merge(candidate.group(), 1, Integer::sum);
            if (++guard > candidates.size() + 8) {
                problems.add(Problem.of("", "SAFETY_STOP", "激活循环达到上限，提前收尾"));
                break;
            }
        }

        if (chosen.size() < rules.minCount()) {
            problems.add(Problem.of("", "MIN_NOT_MET", "只激活了 " + chosen.size() + " 个，少于要求的 "
                    + rules.minCount() + " 个（候选不足或被间距/分组上限挡住）"));
        }

        chosen.sort(Comparator.comparing(Candidate::id));
        chosen.forEach(candidate -> selected.add(candidate.id()));
        return new Result(selected, skippedByDistance, skippedByGroup, perGroup, problems);
    }

    private static boolean canPlace(Candidate candidate, List<Candidate> chosen, Rules rules,
                                    List<String> skippedByDistance, List<String> skippedByGroup,
                                    Map<String, Integer> perGroup) {
        if (rules.maxPerGroup() > 0
                && perGroup.getOrDefault(candidate.group(), 0) >= rules.maxPerGroup()) {
            if (!skippedByGroup.contains(candidate.id())) {
                skippedByGroup.add(candidate.id());
            }
            return false;
        }
        if (rules.minDistance() > 0.0D) {
            double limit = rules.minDistance() * rules.minDistance();
            for (Candidate other : chosen) {
                if (candidate.distanceSquaredTo(other) < limit) {
                    if (!skippedByDistance.contains(candidate.id())) {
                        skippedByDistance.add(candidate.id());
                    }
                    return false;
                }
            }
        }
        return true;
    }

    /** 供命令与日志打印：每个子区域激活了几个。 */
    public static String describeGroups(Map<String, Integer> perGroup) {
        if (perGroup == null || perGroup.isEmpty()) {
            return "(无)";
        }
        StringBuilder builder = new StringBuilder();
        new TreeSet<>(perGroup.keySet()).forEach(group -> {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(group.isEmpty() ? "(未分组)" : group).append('×').append(perGroup.get(group));
        });
        return builder.toString();
    }
}
