package com.hhy.dreamingfishcore.item.items;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 感染抑制剂的纯逻辑：剂量数值与"什么时候不该吃"。
 *
 * <p>剂量是内容数值，被改掉就会悄悄影响平衡，所以钉在测试里；
 * {@code canTreat} 同时决定"拒绝使用"与"结算时不再消耗"两处判定，必须一致。</p>
 *
 * <p>注意这里不构造物品实例：{@code Item.Properties} 需要游戏引导，普通单测环境里会直接抛
 * {@code ExceptionInInitializerError}。注册与"哪个物品对应哪个剂量"由
 * {@link SuppressantGameTest} 在 gametest 里验。</p>
 */
class InfectionSuppressantTest {

    @Test
    void dosesMatchTheDesignedNumbers() {
        assertEquals(5.0F, Item_InfectionSuppressant.LOW_DOSE,
                "低剂量一次必须降 5 点感染进度");
        assertEquals(15.0F, Item_InfectionSuppressant.HIGH_DOSE,
                "高剂量一次必须降 15 点感染进度");
    }

    @Test
    void zeroInfectionIsNotWorthTreating() {
        assertFalse(Item_InfectionSuppressant.canTreat(0.0F), "感染进度为 0 时不该吃药");
        assertFalse(Item_InfectionSuppressant.canTreat(-1.0F), "负进度按 0 处理");
        assertTrue(Item_InfectionSuppressant.canTreat(0.5F), "只要还有进度就值得吃");
        assertTrue(Item_InfectionSuppressant.canTreat(15.0F));
    }
}
