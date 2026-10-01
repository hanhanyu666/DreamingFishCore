package com.hhy.dreamingfishcore.item.items;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.InfectionIdentity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基因复苏试剂的准入规则。
 *
 * <p>这道门槛现在由 {@link InfectionIdentity#allowsEarlyReversalReagent()} 表达，
 * 物品本身在 {@code use} 与 {@code finishUsingItem} 两处都按身份校验（服务端判定）。
 * 测试直接锁定那份规则，避免"物品能不能用"与"身份规则"两处说法漂移。</p>
 */
class PotionRestoreUnInfectedTest {
    @Test
    void reagentClearsSurvivorsAndUnstableInfectedOnly() {
        assertTrue(InfectionIdentity.SURVIVOR.allowsEarlyReversalReagent());
        assertTrue(InfectionIdentity.UNSTABLE.allowsEarlyReversalReagent());
        assertFalse(InfectionIdentity.STABLE.allowsEarlyReversalReagent());
        // 传播复发是稳定感染者的临时状态，凭这一瓶药无法解除，必须走重构疗程。
        assertFalse(InfectionIdentity.RELAPSE.allowsEarlyReversalReagent());
    }
}
