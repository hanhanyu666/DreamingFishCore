package com.hhy.dreamingfishcore.gameplay.research_system;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 研究桌的纯逻辑部分：候选筛选、课题抽取、经验点数换算。
 *
 * <p>刻意与 {@code ServerPlayer}、网络、方块解耦，这样「跳过已学」「命名空间过滤」
 * 「不重复抽取」「数量不超过候选数」这几条规则可以直接单测。</p>
 */
public final class ResearchMath {

    private ResearchMath() {
    }

    /**
     * 筛出可以被研究出来的物品。
     *
     * @param pool        蓝图抽取池（已经过免蓝图 / 白名单 / 黑名单过滤）
     * @param learned     玩家已经学会的物品
     * @param allowed     命名空间过滤器，例如「只允许 minecraft」
     * @param skipLearned 是否跳过已经学会的
     */
    public static List<String> candidates(List<String> pool,
                                          Collection<String> learned,
                                          Predicate<String> allowed,
                                          boolean skipLearned) {
        Set<String> seen = new LinkedHashSet<>();
        for (String itemId : pool) {
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            if (allowed != null && !allowed.test(itemId)) {
                continue;
            }
            if (skipLearned && learned != null && learned.contains(itemId)) {
                continue;
            }
            seen.add(itemId);
        }
        return new ArrayList<>(seen);
    }

    /**
     * 掷出本次课题的数量。
     *
     * <p>结果落在 {@code [min, max]} 内，并被候选总数夹住；候选为空时返回 0。</p>
     */
    public static int rollSize(int min, int max, int available, net.minecraft.util.RandomSource random) {
        if (available <= 0) {
            return 0;
        }
        int upper = Math.min(Math.max(max, 1), available);
        int lower = Math.min(Math.max(min, 1), upper);
        if (lower >= upper) {
            return upper;
        }
        return lower + random.nextInt(upper - lower + 1);
    }

    /**
     * 从候选里不重复地抽 {@code count} 个。
     *
     * <p>用部分 Fisher–Yates 洗牌：不改动传入的列表，抽出的顺序也是随机的。</p>
     */
    public static List<String> pickDistinct(List<String> candidates, int count,
                                            net.minecraft.util.RandomSource random) {
        if (candidates == null || candidates.isEmpty() || count <= 0) {
            return List.of();
        }
        List<String> pool = new ArrayList<>(candidates);
        int take = Math.min(count, pool.size());
        for (int i = 0; i < take; i++) {
            int j = i + random.nextInt(pool.size() - i);
            String tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }
        return new ArrayList<>(pool.subList(0, take));
    }

    /**
     * 提交多少个物品才能解锁对应配方：**物品堆叠上限的四分之一，向上取整**，至少 1 个。
     *
     * <p>64 → 16、16 → 4、1 → 1（钻石镐这类不可堆叠的物品交 1 个即可）。
     * 除数非法（小于 1）时按 1 处理，也就是退化成"提交一整组"，而不是"提交 0 个"——
     * 配置写坏了最坏结果是变贵，不能让玩家白拿配方。</p>
     *
     * @param maxStackSize 物品的堆叠上限
     * @param divisor      配置里的"四分之一"除数
     */
    public static int requiredSubmitCount(int maxStackSize, int divisor) {
        int safeDivisor = Math.max(divisor, 1);
        int safeMaxStackSize = Math.max(maxStackSize, 1);
        // 用 long 做中间量：堆叠上限写成 Integer.MAX_VALUE 时也不会溢出成负数。
        long numerator = (long) safeMaxStackSize + safeDivisor - 1L;
        return (int) Math.max(1L, numerator / safeDivisor);
    }

    /**
     * 玩家当前持有的经验点数（不是等级）。
     *
     * <p>由等级与当前等级的进度反推：等级只决定「升到该级需要多少点」，
     * 这个换算与 {@code /xp query} 用的是同一套原版公式。</p>
     */
    public static int experiencePointsOf(int level, float progress, int neededForNextLevel) {
        int safeLevel = Math.max(level, 0);
        int points = pointsToReachLevel(safeLevel);
        float safeProgress = Math.max(0.0F, Math.min(progress, 1.0F));
        return points + Math.round(safeProgress * Math.max(neededForNextLevel, 0));
    }

    /** 从 0 级升到 {@code level} 级累计需要的经验点数（原版公式）。 */
    public static int pointsToReachLevel(int level) {
        int safeLevel = Math.max(level, 0);
        if (safeLevel <= 16) {
            return safeLevel * safeLevel + 6 * safeLevel;
        }
        if (safeLevel <= 31) {
            return (int) Math.round(2.5D * safeLevel * safeLevel - 40.5D * safeLevel + 360.0D);
        }
        return (int) Math.round(4.5D * safeLevel * safeLevel - 162.5D * safeLevel + 2220.0D);
    }
}
