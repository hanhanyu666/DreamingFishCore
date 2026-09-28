package com.hhy.dreamingfishcore.effect;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组自有的状态效果。
 *
 * <p>体征系统原本借用原版效果表达负面状态：感染值过高时给「缓慢 + 虚弱」，勇气值过低时给
 * 「虚弱 + 缓慢」，勇气值高时给「力量」。这会让玩家在 HUD 上看到原版的名字，无法分辨惩罚
 * 来自哪里。现在改为三个模组效果，**数值与原版逐个对齐**：</p>
 *
 * <ul>
 *   <li>{@code dreamingfishcore:infection}「感染」——缓慢 + 虚弱（移速 -15%、攻击力 -4）；</li>
 *   <li>{@code dreamingfishcore:fear}「害怕」——与「感染」同样的惩罚；</li>
 *   <li>{@code dreamingfishcore:courage}「勇气」——力量（攻击力 +3）。</li>
 * </ul>
 *
 * <p>每个效果使用**自己命名空间下的属性修饰符 ID**，不直接复用 {@code minecraft:effect.slowness}
 * 之类的原版 ID。原因是原版在效果结束时按 ID 移除修饰符：复用原版 ID 会让「感染」消失时顺手
 * 抹掉玩家身上真正由药水提供的缓慢/虚弱。代价是「感染」与「害怕」同时存在时惩罚会各算一次
 * （移速 -30%、攻击力 -8），这是刻意的取舍。</p>
 *
 * <p>数值按等级线性放大（{@code MobEffect.AttributeTemplate.create} 使用 {@code amount * (等级 + 1)}），
 * 与原版缓慢/虚弱/力量的行为一致，所以 II 级自然就是 -30% / -8 / +6。</p>
 */
public final class DreamingFishCore_Effects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, DreamingFishCore.MODID);

    /** 感染：感染值过高时施加，取代原来的原版缓慢 + 虚弱。 */
    public static final DeferredHolder<MobEffect, MobEffect> INFECTION =
            EFFECTS.register("infection", DreamingFishCore_Effects::createInfection);

    /** 害怕：勇气值过低时施加，取代原来的原版虚弱 + 缓慢。 */
    public static final DeferredHolder<MobEffect, MobEffect> FEAR =
            EFFECTS.register("fear", DreamingFishCore_Effects::createFear);

    /** 勇气：勇气值高时施加，取代原来的原版力量。 */
    public static final DeferredHolder<MobEffect, MobEffect> COURAGE =
            EFFECTS.register("courage", DreamingFishCore_Effects::createCourage);

    /** 与原版缓慢 I 一致：移速 -15%（ADD_MULTIPLIED_TOTAL）。 */
    private static final double SLOWNESS_AMOUNT = -0.15D;
    /** 与原版虚弱 I 一致：攻击力 -4（ADD_VALUE）。 */
    private static final double WEAKNESS_AMOUNT = -4.0D;
    /** 与原版力量 I 一致：攻击力 +3（ADD_VALUE）。 */
    private static final double STRENGTH_AMOUNT = 3.0D;

    /** 粒子颜色：感染＝暗绿，害怕＝紫，勇气＝金。 */
    private static final int INFECTION_COLOR = 0x4E7A2F;
    private static final int FEAR_COLOR = 0x5B3A8C;
    private static final int COURAGE_COLOR = 0xE0A030;

    private DreamingFishCore_Effects() {
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }

    /** 感染：移速与攻击力双降，等同于原版缓慢 I + 虚弱 I。 */
    static MobEffect createInfection() {
        return new DreamingFishMobEffect(MobEffectCategory.HARMFUL, INFECTION_COLOR)
                .addAttributeModifier(Attributes.MOVEMENT_SPEED,
                        modifierId("effect.infection_slowness"),
                        SLOWNESS_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                .addAttributeModifier(Attributes.ATTACK_DAMAGE,
                        modifierId("effect.infection_weakness"),
                        WEAKNESS_AMOUNT, AttributeModifier.Operation.ADD_VALUE);
    }

    /** 害怕：惩罚与「感染」相同，但用独立的修饰符 ID，便于分别结束、分别排查。 */
    static MobEffect createFear() {
        return new DreamingFishMobEffect(MobEffectCategory.HARMFUL, FEAR_COLOR)
                .addAttributeModifier(Attributes.MOVEMENT_SPEED,
                        modifierId("effect.fear_slowness"),
                        SLOWNESS_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                .addAttributeModifier(Attributes.ATTACK_DAMAGE,
                        modifierId("effect.fear_weakness"),
                        WEAKNESS_AMOUNT, AttributeModifier.Operation.ADD_VALUE);
    }

    /** 勇气：攻击力提升，等同于原版力量 I。 */
    static MobEffect createCourage() {
        return new DreamingFishMobEffect(MobEffectCategory.BENEFICIAL, COURAGE_COLOR)
                .addAttributeModifier(Attributes.ATTACK_DAMAGE,
                        modifierId("effect.courage_strength"),
                        STRENGTH_AMOUNT, AttributeModifier.Operation.ADD_VALUE);
    }

    private static ResourceLocation modifierId(String path) {
        return ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, path);
    }
}
