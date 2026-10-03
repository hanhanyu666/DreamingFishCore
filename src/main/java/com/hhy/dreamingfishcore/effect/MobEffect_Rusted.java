package com.hhy.dreamingfishcore.effect;

import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「生锈」：金刚僵尸被水浇到后的状态，等级就是锈级。
 *
 * <p>这个效果承担两件事，而且都是「白拿」的：</p>
 * <ol>
 *   <li><b>锈级本身</b>——等级即层数（{@code amplifier = 层数 - 1}），实体的伤害闸门与客户端
 *       皮肤都读它，所以锈级天然跨端同步、天然存档（挂在实体身上的效果会跟着实体存盘）。</li>
 *   <li><b>属性惩罚</b>——原版会按 {@code amount × (等级 + 1)} 缩放修饰符，所以这里只需要写
 *       「每层」的数值：移速 -20%、攻击 -1、护甲 -0.5，三层就是 -60% / -3 / -1.5。</li>
 * </ol>
 *
 * <p>受伤倍率（每层 +20%）做不成属性修饰符，写在 {@code AdamantZombieEntity} 的伤害闸门里。</p>
 *
 * <p>持续时间用 {@code -1}（无限）：锈不会退，所以不该有倒计时。因为无限效果的粒子会一直飘，
 * 实体侧用 {@code visible = false} 挂它，锈屑只在「涨层」的那一刻喷一次。</p>
 */
public class MobEffect_Rusted extends DreamingFishMobEffect {
    /** 图标颜色：锈褐。 */
    private static final int COLOR = 0x8B5A2B;

    public MobEffect_Rusted() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
