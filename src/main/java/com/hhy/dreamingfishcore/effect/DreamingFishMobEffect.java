package com.hhy.dreamingfishcore.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 模组状态效果的通用基类。
 *
 * <p>原版 {@link MobEffect} 的两个构造器都是 {@code protected}，只有同包类或子类能调用
 * （原版 {@code MobEffects} 之所以能直接 {@code new}，是因为它就在 {@code net.minecraft.world.effect}
 * 包里）。模组只能继承。属性修饰符由 {@link DreamingFishCore_Effects} 在构造后通过
 * {@code addAttributeModifier(...)} 配置。</p>
 */
public class DreamingFishMobEffect extends MobEffect {

    public DreamingFishMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
