package com.hhy.dreamingfishcore.gameplay.frontier_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 外缘惩罚开关的纯逻辑。
 *
 * <p>要钉住的核心是**默认行为不变**：开关关着的时候，重生代价必须与现在完全一致
 * （否则这条改动会悄悄改变所有玩家的死亡成本）。</p>
 */
class FrontierRulesTest {

    @Test
    void switchOffKeepsCurrentBehaviour() {
        assertTrue(FrontierRules.shouldCharge(false, false), "开关关着：外缘外也照常收费");
        assertTrue(FrontierRules.shouldCharge(false, true));
        assertEquals(5.0F, FrontierRules.charge(5.0F, false, false), 1e-6,
                "开关关着时不能改变任何代价");
    }

    @Test
    void switchOnChargesOnlyInsideTheFrontier() {
        assertTrue(FrontierRules.shouldCharge(true, true), "外缘内照常收费");
        assertFalse(FrontierRules.shouldCharge(true, false), "外缘外不收费");
        assertEquals(5.0F, FrontierRules.charge(5.0F, true, true), 1e-6);
        assertEquals(0.0F, FrontierRules.charge(5.0F, true, false), 1e-6);
    }

    @Test
    void zeroOrNegativeCostStaysZero() {
        assertEquals(0.0F, FrontierRules.charge(0.0F, false, true), 1e-6);
        assertEquals(0.0F, FrontierRules.charge(-3.0F, true, true), 1e-6,
                "负数按 0 处理，绝不反过来变成「给点数」");
    }

    @Test
    void costIsNeverIncreased() {
        for (boolean switchOn : new boolean[]{true, false}) {
            for (boolean inFrontier : new boolean[]{true, false}) {
                for (float cost : new float[]{0.5F, 2.5F, 5.0F, 10.0F}) {
                    float charged = FrontierRules.charge(cost, switchOn, inFrontier);
                    assertTrue(charged <= cost, "折算后不该比原价更高：" + charged + " > " + cost);
                    assertTrue(charged >= 0.0F, "折算后不该是负数");
                }
            }
        }
    }
}
