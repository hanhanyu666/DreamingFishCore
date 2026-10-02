package com.hhy.dreamingfishcore.item.items;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 感染抑制剂的纯逻辑：剂量数值与"什么时候不该吃"。
 *
 * <p>剂量是内容数值，被改掉就会悄悄影响平衡，所以钉在测试里，并且必须来自
 * {@link InfectionRules}（数值集中管理是不变量）。{@code canTreat} 同时决定
 * "拒绝使用"与"结算时不再消耗"两处判定，必须一致。</p>
 *
 * <p>注意这里不构造物品实例：{@code Item.Properties} 需要游戏引导，普通单测环境里会直接抛
 * {@code ExceptionInInitializerError}。注册、剂量归属与"归零解身份"由
 * {@link SuppressantGameTest} 在 gametest 里验。</p>
 */
class InfectionSuppressantTest {

    @Test
    void dosesMatchTheDesignedNumbersAndComeFromRules() {
        assertEquals(5.0F, Item_InfectionSuppressant.LOW_DOSE,
                "低剂量一次必须降 5 点感染读数");
        assertEquals(15.0F, Item_InfectionSuppressant.HIGH_DOSE,
                "高剂量一次必须降 15 点感染读数");
        assertEquals(InfectionRules.SUPPRESSANT_DOSE_LOW, Item_InfectionSuppressant.LOW_DOSE,
                "剂量必须在 InfectionRules 里集中定义，物品只做引用");
        assertEquals(InfectionRules.SUPPRESSANT_DOSE_HIGH, Item_InfectionSuppressant.HIGH_DOSE);
    }

    @Test
    void zeroReadingIsOnlyWorthTreatingWhenStillInfected() {
        assertFalse(Item_InfectionSuppressant.canTreat(0.0F, false),
                "读数 0 且不是感染者时不该吃药");
        assertFalse(Item_InfectionSuppressant.canTreat(-1.0F, false), "负读数按 0 处理");
        assertTrue(Item_InfectionSuppressant.canTreat(0.5F, false), "只要还有读数就值得吃");
        assertTrue(Item_InfectionSuppressant.canTreat(15.0F, false));
        assertTrue(Item_InfectionSuppressant.canTreat(0.0F, true),
                "读数为 0 但仍是感染者时必须允许服用（这一口就是去解身份的）");
    }
}
