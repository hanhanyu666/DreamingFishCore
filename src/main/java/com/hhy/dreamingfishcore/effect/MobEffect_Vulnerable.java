package com.hhy.dreamingfishcore.effect;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「易损」：焦尸被火焰类伤害打裂后获得的破防状态。
 *
 * <p><b>这个效果本身不结算任何伤害，也不带属性修饰符</b>——它是一枚纯标记，真正的规则写在
 * {@code CharredZombieEntity#hurt} 里：身上有「易损」时，非火伤害不再被归零，且所有伤害额外
 * 按 {@code 1 + 0.1 × 等级} 放大。之所以不做成「每 tick 掉血」或属性修饰符，是因为这两者都
 * 表达不了「改变伤害闸门的判定」这件事。</p>
 *
 * <p>等级与持续时长都来自 {@code charred_zombie.json}（{@code vulnerableDurationTicks} /
 * {@code vulnerableAmplifier} / {@code vulnerableDamageBonusPerLevel}）。</p>
 */
public class MobEffect_Vulnerable extends DreamingFishMobEffect {
    /** 图标颜色：烧裂的炭口，橙红。 */
    private static final int COLOR = 0xFF7A18;

    public MobEffect_Vulnerable() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    /**
     * 不参与周期结算。
     *
     * <p>返回 {@code false} 让原版完全不调用 {@link #applyEffectTick}，比「每 tick 调一次再
     * 什么都不做」省一层开销——尸潮里同时挂着这个效果的实体可能很多。</p>
     */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }

    /** 即便被调用也没有任何副作用；保留重写是为了让「这是纯标记」这件事在代码里显式可见。 */
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        return true;
    }
}
