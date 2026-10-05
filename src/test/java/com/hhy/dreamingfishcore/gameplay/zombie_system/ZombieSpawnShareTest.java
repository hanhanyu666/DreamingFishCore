package com.hhy.dreamingfishcore.gameplay.zombie_system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 焦尸 / 金刚僵尸在刷怪池里的份额不变量。
 *
 * <p>这两个类型是从「自定义丧尸」的份额里再切出来的（与射手僵尸同一思路），所以要钉住两件事：</p>
 * <ol>
 *   <li>比例确实是 <b>焦尸 25% / 金刚 10%</b>（服主定的方案 A）——改数值就得改这里，跑不掉；</li>
 *   <li>切分<b>不改变僵尸家族的总权重</b>：原版 + 围攻 + 射手 + 焦尸 + 金刚 = 缩放后的家族权重。
 *       这条是这套重写器的核心承诺（不稀释原版僵尸），切分再多一档也不能破。</li>
 * </ol>
 */
class ZombieSpawnShareTest {

    /** 与重写器里同一套取整规则（proportionalWeight 是包内可见的纯函数）。 */
    private static int charred(int customShare) {
        return ZombieSpawnPoolRewriter.proportionalWeight(
                customShare, ZombieSpawnPoolRewriter.CHARRED_PERCENT_OF_CUSTOM, 100L);
    }

    private static int adamant(int customShare) {
        return ZombieSpawnPoolRewriter.proportionalWeight(
                customShare, ZombieSpawnPoolRewriter.ADAMANT_PERCENT_OF_CUSTOM, 100L);
    }

    @Test
    void theAgreedPercentagesAreTwentyFiveAndTen() {
        assertEquals(25, ZombieSpawnPoolRewriter.CHARRED_PERCENT_OF_CUSTOM,
                "焦尸占自定义丧尸份额的 25%");
        assertEquals(10, ZombieSpawnPoolRewriter.ADAMANT_PERCENT_OF_CUSTOM,
                "金刚僵尸占自定义丧尸份额的 10%（比焦尸更稀有）");
    }

    @Test
    void sharesAreTakenFromTheCustomShareAndNeverAddUpToMoreThanIt() {
        for (int customShare : new int[]{100, 95, 57, 40, 19, 10, 5, 3, 1}) {
            int charred = charred(customShare);
            int adamant = adamant(customShare);
            int siege = Math.max(0, customShare - charred - adamant);
            assertEquals(customShare, siege + charred + adamant,
                    "切分后围攻+焦尸+金刚必须正好等于自定义份额（customShare=" + customShare + "）");
        }
    }

    @Test
    void concreteNumbersMatchTheAgreedSplit() {
        assertEquals(25, charred(100));
        assertEquals(10, adamant(100));
        assertEquals(65, 100 - charred(100) - adamant(100));

        // 沙漠僵尸模板的缩放份额是 19 这种小数字时，取整规则仍按包内同一套算。
        assertEquals(5, charred(19));
        assertEquals(2, adamant(19));
        assertEquals(12, 19 - charred(19) - adamant(19));
    }

    @Test
    void sharesThatRoundToZeroDropOutInsteadOfLeavingZeroWeightEntries() {
        // 自定义份额极小时，金刚（10%）会先归零，然后是焦尸（25%）。
        assertEquals(1, charred(3));
        assertEquals(0, adamant(3), "3 的 10% 取整后是 0，这一档就不该进池");
        assertEquals(0, charred(1), "1 的 25% 取整后也是 0");
        assertEquals(0, adamant(1));
    }

    @Test
    void splittingKeepsTheWholeZombieFamilyWeightUnchanged() {
        // 模拟一次完整重写：原版僵尸 95、家族倍率 120、原版/自定义 60:40、射手占自定义的 50%。
        int scaledFamily = ZombieSpawnPoolRewriter.scaledPercentWeight(95, 120);
        int vanilla = ZombieSpawnPoolRewriter.proportionalWeight(scaledFamily, 60, 100);
        int customTotal = scaledFamily - vanilla;
        int archer = ZombieSpawnPoolRewriter.proportionalWeight(customTotal, 50, 100);
        int custom = customTotal - archer;

        int charred = charred(custom);
        int adamant = adamant(custom);
        int siege = custom - charred - adamant;

        assertEquals(scaledFamily, vanilla + siege + archer + charred + adamant,
                "切出焦尸与金刚之后，僵尸家族总权重必须仍然等于缩放后的家族权重");
        assertEquals(114, scaledFamily);
        assertEquals(68, vanilla);
        assertEquals(46, customTotal);
        assertEquals(23, archer);
        assertEquals(23, custom);
        assertEquals(6, charred);
        assertEquals(2, adamant);
        assertEquals(15, siege);
    }
}