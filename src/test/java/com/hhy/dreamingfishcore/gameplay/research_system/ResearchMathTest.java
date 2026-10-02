package com.hhy.dreamingfishcore.gameplay.research_system;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 研究桌的纯逻辑：候选筛选、课题数量、不重复抽取、经验点数换算。 */
class ResearchMathTest {

    private static final List<String> POOL = List.of(
            "minecraft:iron_sword",
            "minecraft:iron_pickaxe",
            "minecraft:anvil",
            "create:cogwheel");

    // ==================== 候选筛选 ====================

    @Test
    void namespaceFilterKeepsOnlyAllowedMods() {
        List<String> candidates = ResearchMath.candidates(
                POOL, Set.of(), id -> id.startsWith("minecraft:"), true);

        assertEquals(List.of("minecraft:iron_sword", "minecraft:iron_pickaxe", "minecraft:anvil"), candidates);
    }

    @Test
    void skipLearnedDropsKnownItems() {
        List<String> candidates = ResearchMath.candidates(
                POOL, Set.of("minecraft:iron_sword"), id -> true, true);

        assertFalse(candidates.contains("minecraft:iron_sword"));
        assertEquals(3, candidates.size());
    }

    @Test
    void skipLearnedOffKeepsEverything() {
        List<String> candidates = ResearchMath.candidates(
                POOL, Set.of("minecraft:iron_sword", "minecraft:anvil"), id -> true, false);

        assertEquals(4, candidates.size(), "关掉跳过已学之后已经学会的也应留在候选里");
    }

    @Test
    void candidatesAreDeduplicated() {
        List<String> duplicated = List.of("minecraft:anvil", "minecraft:anvil", "minecraft:iron_sword");

        List<String> candidates = ResearchMath.candidates(duplicated, Set.of(), id -> true, true);

        assertEquals(List.of("minecraft:anvil", "minecraft:iron_sword"), candidates);
    }

    @Test
    void nullNamespaceFilterMeansNoRestriction() {
        assertEquals(4, ResearchMath.candidates(POOL, Set.of(), null, true).size());
    }

    @Test
    void blankItemIdsAreIgnored() {
        List<String> messy = new ArrayList<>(List.of("minecraft:anvil", "", "   "));

        assertEquals(List.of("minecraft:anvil"),
                ResearchMath.candidates(messy, Set.of(), null, true));
    }

    // ==================== 数量 ====================

    @Test
    void rollSizeStaysWithinConfiguredRange() {
        for (long seed = 0; seed < 50; seed++) {
            int size = ResearchMath.rollSize(10, 15, 100, RandomSource.create(seed));
            assertTrue(size >= 10 && size <= 15, "越界：" + size);
        }
    }

    @Test
    void rollSizeIsCappedByAvailableCandidates() {
        int size = ResearchMath.rollSize(10, 15, 3, RandomSource.create(1L));

        assertEquals(3, size, "候选只有 3 个时不可能给出 10 个");
    }

    @Test
    void rollSizeIsZeroOnEmptyPool() {
        assertEquals(0, ResearchMath.rollSize(10, 15, 0, RandomSource.create(1L)));
    }

    @Test
    void rollSizeHandlesSingleValueRange() {
        assertEquals(7, ResearchMath.rollSize(7, 7, 100, RandomSource.create(3L)));
    }

    // ==================== 抽取 ====================

    @Test
    void pickDistinctReturnsRequestedCountWithoutDuplicates() {
        List<String> candidates = new ArrayList<>(POOL);

        List<String> picked = ResearchMath.pickDistinct(candidates, 3, RandomSource.create(2026L));

        assertEquals(3, picked.size());
        assertEquals(3, Set.copyOf(picked).size(), "不能重复抽到同一个");
        assertTrue(candidates.containsAll(picked));
    }

    @Test
    void pickDistinctNeverExceedsPoolSize() {
        List<String> picked = ResearchMath.pickDistinct(POOL, 99, RandomSource.create(5L));

        assertEquals(POOL.size(), picked.size());
    }

    @Test
    void pickDistinctDoesNotMutateInput() {
        List<String> candidates = new ArrayList<>(POOL);

        ResearchMath.pickDistinct(candidates, 4, RandomSource.create(9L));

        assertEquals(POOL, candidates, "抽取不能改动调用方传进来的列表");
    }

    @Test
    void pickDistinctReturnsEmptyForEmptyInputOrZeroCount() {
        assertTrue(ResearchMath.pickDistinct(List.of(), 5, RandomSource.create(1L)).isEmpty());
        assertTrue(ResearchMath.pickDistinct(POOL, 0, RandomSource.create(1L)).isEmpty());
    }

    // ==================== 提交数量（四分之一组） ====================

    @Test
    void submitCountIsAQuarterOfStackSize() {
        assertEquals(16, ResearchMath.requiredSubmitCount(64, 4), "铁锭这类 64 堆叠的交 16 个");
        assertEquals(4, ResearchMath.requiredSubmitCount(16, 4), "鸡蛋这类 16 堆叠的交 4 个");
        assertEquals(1, ResearchMath.requiredSubmitCount(1, 4), "不可堆叠的（钻石镐）交 1 个");
    }

    @Test
    void submitCountRoundsUpInsteadOfDown() {
        // 向上取整：10 / 4 = 2.5 → 3；不取整的话会出现"交 2 个就够了"，比配置更便宜。
        assertEquals(3, ResearchMath.requiredSubmitCount(10, 4));
        assertEquals(1, ResearchMath.requiredSubmitCount(3, 4));
        assertEquals(2, ResearchMath.requiredSubmitCount(5, 4));
    }

    @Test
    void submitCountWithDivisorOneNeedsTheWholeStack() {
        assertEquals(64, ResearchMath.requiredSubmitCount(64, 1));
        assertEquals(16, ResearchMath.requiredSubmitCount(16, 1));
        assertEquals(1, ResearchMath.requiredSubmitCount(1, 1));
    }

    @Test
    void illegalDivisorFallsBackToWholeStackNeverZero() {
        // 配置写成 0 / 负数时按 1 处理：最坏是"变贵"，绝不能变成"交 0 个白拿配方"。
        assertEquals(64, ResearchMath.requiredSubmitCount(64, 0));
        assertEquals(64, ResearchMath.requiredSubmitCount(64, -5));
        assertEquals(64, ResearchMath.requiredSubmitCount(64, Integer.MIN_VALUE));
    }

    @Test
    void submitCountIsAlwaysAtLeastOne() {
        // 除数比堆叠上限还大时也至少交 1 个。
        assertEquals(1, ResearchMath.requiredSubmitCount(1, 64));
        assertEquals(1, ResearchMath.requiredSubmitCount(0, 64));
        assertEquals(1, ResearchMath.requiredSubmitCount(-3, 4));
    }

    // ==================== 经验点数 ====================
    @Test
    void pointsToReachLevelMatchesVanillaTotals() {
        assertEquals(0, ResearchMath.pointsToReachLevel(0));
        assertEquals(7, ResearchMath.pointsToReachLevel(1));
        assertEquals(352, ResearchMath.pointsToReachLevel(16));
        assertEquals(394, ResearchMath.pointsToReachLevel(17));
        assertEquals(1395, ResearchMath.pointsToReachLevel(30));
        assertEquals(1507, ResearchMath.pointsToReachLevel(31));
        assertEquals(1628, ResearchMath.pointsToReachLevel(32));
    }

    @Test
    void pointsToReachLevelClampsNegativeInput() {
        assertEquals(0, ResearchMath.pointsToReachLevel(-5));
    }

    @Test
    void experiencePointsAddsLevelProgress() {
        // 5 级累计 55 点；进度 0.5、升下一级需要 17 点 → 再加 9 点。
        assertEquals(64, ResearchMath.experiencePointsOf(5, 0.5F, 17));
    }

    @Test
    void experiencePointsClampsProgress() {
        assertEquals(55, ResearchMath.experiencePointsOf(5, -1.0F, 17));
        assertEquals(72, ResearchMath.experiencePointsOf(5, 2.0F, 17), "进度最多算满一级");
    }

    @Test
    void experiencePointsTreatsNegativeLevelAsZero() {
        assertEquals(0, ResearchMath.experiencePointsOf(-3, 0.0F, 7));
    }
}
