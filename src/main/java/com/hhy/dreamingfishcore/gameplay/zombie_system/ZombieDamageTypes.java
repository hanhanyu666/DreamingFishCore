package com.hhy.dreamingfishcore.gameplay.zombie_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/**
 * 模组丧尸相关的自定义伤害类型。
 *
 * <p>骨刺刻意不复用原版 {@code minecraft:mob_projectile}：那个类型的 {@code scaling} 是
 * {@code when_caused_by_living_non_player}，原版会在 {@code Player#hurt} 里按难度硬编码缩放
 * （简单 min(伤害/2+1, 伤害)、普通 ×1、困难 ×1.5）。这样"按难度变伤"就是隐式、不可调的。
 * 这里改用自己注册的类型并声明 {@code scaling: never}，把难度倍率完全交给
 * {@code archer_zombie.json}，同时避免本地倍率与原版缩放叠加成双倍变化。</p>
 *
 * <p>数据侧定义在 {@code data/dreamingfishcore/damage_type/bone_spike.json}，
 * 死亡提示词在语言的 {@code death.attack.bone_spike[.player]} 里。</p>
 */
public final class ZombieDamageTypes {
    /** 骨刺穿刺伤害。 */
    public static final ResourceKey<DamageType> BONE_SPIKE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "bone_spike"));

    /** 流血掉血；用它而不是 {@code magic}，死亡提示才说得清死因。 */
    public static final ResourceKey<DamageType> BLEEDING = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "bleeding"));

    private ZombieDamageTypes() {
    }
}
