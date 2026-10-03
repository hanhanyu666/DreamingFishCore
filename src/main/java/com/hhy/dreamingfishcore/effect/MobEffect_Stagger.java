package com.hhy.dreamingfishcore.effect;

import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「硬直」：攻击未生锈的金刚僵尸后，攻击者身上的短暂僵直。
 *
 * <p>加在**攻击者**身上而不是金刚僵尸身上：抄原版铁的反馈是「你打不动，而且手被震麻了」。
 * 属性修饰符由 {@code DreamingFishCore_Effects} 配置，效果是攻击力归零 + 移速下降，持续
 * {@code adamant_zombie.json} 的 {@code staggerDurationTicks}（默认 10 tick = 0.5 秒）。</p>
 *
 * <p>攻击力按 {@code ADD_MULTIPLIED_TOTAL -1.0} 配，也就是总倍率 (1 - 1) = 0：硬直期间连
 * 别的目标也打不出伤害。这是刻意的——「硬直」如果只是扣一点攻击力，玩家根本感觉不到。</p>
 */
public class MobEffect_Stagger extends DreamingFishMobEffect {
    /** 图标颜色：铁灰。 */
    private static final int COLOR = 0x8A8A8A;

    public MobEffect_Stagger() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
