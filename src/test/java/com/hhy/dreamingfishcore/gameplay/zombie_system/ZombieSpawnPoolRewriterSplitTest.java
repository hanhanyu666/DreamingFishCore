package com.hhy.dreamingfishcore.gameplay.zombie_system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 刷怪池重写器的纯数值部分：权重缩放与「原版 / 自定义丧尸 / 射手僵尸」三分分配。
 *
 * <p>刻意只测不依赖 {@link net.minecraft.world.entity.EntityType} 的静态方法——这个项目的
 * 单元测试一律不引导原版注册表。</p>
 */
class ZombieSpawnPoolRewriterSplitTest {

    @Test
    void scaledPercentWeightRoundsToNearest() {
        assertEquals(120, ZombieSpawnPoolRewriter.scaledPercentWeight(100, 120));
        assertEquals(80, ZombieSpawnPoolRewriter.scaledPercentWeight(100, 80));
        assertEquals(0, ZombieSpawnPoolRewriter.scaledPercentWeight(100, 0));
        assertEquals(0, ZombieSpawnPoolRewriter.scaledPercentWeight(0, 120));
        // 5 * 150% = 7.5 → 8
        assertEquals(8, ZombieSpawnPoolRewriter.scaledPercentWeight(5, 150));
    }

    @Test
    void legacyTwoWaySplitIsUnchangedWhenNoArcherShare() {
        // 120% 家族权重，原版 40 / 自定义 60 → 48 / 72，射手为 0。
        assertArrayEquals(new int[] {48, 72, 0},
                ZombieSpawnPoolRewriter.splitZombieFamilyWeight(120, 40, 60, 0));
    }

    @Test
    void archerShareIsCarvedOutOfTheCustomShareOnly() {
        // 自定义份额 72，射手占其中 40% → 29（四舍五入），自定义剩 43，原版仍是 48。
        assertArrayEquals(new int[] {48, 43, 29},
                ZombieSpawnPoolRewriter.splitZombieFamilyWeight(120, 40, 60, 40));
        // 100% 转射手时自定义份额整份归射手。
        assertArrayEquals(new int[] {48, 0, 72},
                ZombieSpawnPoolRewriter.splitZombieFamilyWeight(120, 40, 60, 100));
    }

    @Test
    void zeroCustomShareNeverProducesArchers() {
        assertArrayEquals(new int[] {100, 0, 0},
                ZombieSpawnPoolRewriter.splitZombieFamilyWeight(100, 100, 0, 50));
    }

    @Test
    void degenerateInputsProduceNoWeight() {
        assertArrayEquals(new int[] {0, 0, 0},
                ZombieSpawnPoolRewriter.splitZombieFamilyWeight(0, 40, 60, 40));
        assertArrayEquals(new int[] {0, 0, 0},
                ZombieSpawnPoolRewriter.splitZombieFamilyWeight(120, 0, 0, 40));
    }

    /** 性质：三份之和恒等于输入权重，且没有任何负数（不会凭空多出/吃掉权重）。 */
    @Test
    void splitAlwaysConservesTotalWeight() {
        for (int scaled = 1; scaled <= 400; scaled++) {
            for (int vanilla = 0; vanilla <= 100; vanilla += 5) {
                for (int custom = 0; custom <= 100; custom += 5) {
                    for (int archerPercent = 0; archerPercent <= 100; archerPercent += 10) {
                        if (vanilla == 0 && custom == 0) {
                            continue;
                        }
                        int[] split = ZombieSpawnPoolRewriter.splitZombieFamilyWeight(
                                scaled, vanilla, custom, archerPercent);
                        assertEquals(scaled, split[0] + split[1] + split[2],
                                "scaled=" + scaled + " vanilla=" + vanilla
                                        + " custom=" + custom + " archer=" + archerPercent);
                        assertTrue(split[0] >= 0 && split[1] >= 0 && split[2] >= 0);
                    }
                }
            }
        }
    }

    /** 性质：射手百分比越大，射手那份单调不减、自定义那份单调不增。 */
    @Test
    void archerShareIsMonotonic() {
        int previousArcher = -1;
        int previousCustom = Integer.MAX_VALUE;
        for (int archerPercent = 0; archerPercent <= 100; archerPercent++) {
            int[] split = ZombieSpawnPoolRewriter.splitZombieFamilyWeight(
                    240, 40, 60, archerPercent);
            assertTrue(split[2] >= previousArcher, "射手权重应单调不减");
            assertTrue(split[1] <= previousCustom, "自定义丧尸权重应单调不增");
            previousArcher = split[2];
            previousCustom = split[1];
        }
    }
}
