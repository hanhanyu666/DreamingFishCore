package com.hhy.dreamingfishcore.gameplay.zombie_system.charred;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.effect.DreamingFishCore_Effects;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ModZombieSpecies;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 焦尸：只受火焰类伤害的僵尸变体。
 *
 * <p>继承原版 {@link Zombie}，所以近战追击、开门、破门、装备随机生成、水中腐化等行为全部保留，
 * 差异集中在一条规则上：<b>非火焰类伤害在扣血前被归零</b>。规则本身写在 {@link #hurt} 里，
 * 判定表在 {@link CharredZombieRules}。</p>
 *
 * <h2>为什么归零要放在 hurt 层而不是直接返回 false</h2>
 * <p>最直觉的写法是「非火伤害 → 直接 {@code return false}」，但那会连带砍掉两条重要链路：</p>
 * <ul>
 *   <li>{@code Player#attack} 里 {@code EnchantmentHelper.doPostAttackEffects(...)} 被包在
 *       {@code if (target.hurt(...))} 内，而<b>火焰附加的点燃就在这个调用里</b>。返回 false
 *       等于「用火剑打它，既不疼也点不着」，玩家最顺手的反制手段直接失效；</li>
 *   <li>受伤动画、击退、仇恨（{@code setLastHurtByMob}）也都挂在 {@code hurt} 走完之后。</li>
 * </ul>
 * <p>所以这里的做法是：<b>照常调用 {@code super.hurt}，但把伤害压到 0</b>。命中照常生效、
 * 附魔照常点火、武器照常掉耐久，只有血量不动——附带好处是「打不动」这件事有了视觉与听觉反馈。</p>
 *
 * <h2>易损（被火打裂）</h2>
 * <p>被火焰类伤害命中后会挂上 {@code dreamingfishcore:vulnerable}（配置名「易损」）。期间：非火伤害
 * 不再归零（破防），且所有伤害按 {@code 1 + 10% × 等级} 放大。火因此从「唯一解」变成「破防开关键」，
 * 没带火的玩家也有事可做。</p>
 *
 * <h2>易燃体质</h2>
 * <p>火焰类伤害 ×2（配置项），且着火期间移速与攻击力临时提升——用火是双刃剑，点火的人要承担它变凶的代价。</p>
 */
public class CharredZombieEntity extends Zombie implements ModZombieSpecies {
    private static final ResourceLocation HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "charred_zombie_health");
    private static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "charred_zombie_speed");
    private static final ResourceLocation FOLLOW_RANGE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "charred_zombie_follow_range");
    private static final ResourceLocation BURNING_SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "charred_zombie_burning_speed");
    private static final ResourceLocation BURNING_ATTACK_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "charred_zombie_burning_attack");

    /** 与 {@link #createAttributes()} 里的基础值一致，配置改动按差值套用修饰符。 */
    private static final double BASE_MAX_HEALTH = 22.0D;
    private static final double BASE_MOVEMENT_SPEED = 0.20D;
    private static final double BASE_FOLLOW_RANGE = 35.0D;

    private static final int SETTINGS_REFRESH_INTERVAL_TICKS = 10;
    /** 免疫反馈的粒子数量与音效参数。 */
    private static final int IMMUNE_FEEDBACK_PARTICLES = 8;
    private static final float IMMUNE_FEEDBACK_VOLUME = 0.7F;
    private static final float IMMUNE_FEEDBACK_PITCH = 1.4F;

    private CharredZombieConfig.Resolved runtimeSettings;
    private int settingsRefreshCooldown;
    /** 着火增强当前是否已生效，用来避免每 tick 反复增删修饰符。 */
    private boolean burningBoostApplied;
    /** 发光是否由本类点亮，卸下「易损」时只关掉自己点的那一次。 */
    private boolean glowingByVulnerable;

    public CharredZombieEntity(EntityType<? extends CharredZombieEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 生命 22（原版 20，略硬）、移速 0.20（原版 0.23，略慢）、索敌距离沿用原版 35。
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE);
    }

    // ------------------------------------------------------------------
    // 伤害闸门
    // ------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || amount <= 0.0F) {
            return super.hurt(source, amount);
        }
        CharredZombieConfig.Resolved settings = runtimeSettings();
        if (!settings.enabled()) {
            return super.hurt(source, amount);
        }

        boolean fireDamage = isFireDamage(source, settings);
        boolean bypasses = bypassesImmunity(source, settings);
        int vulnerableAmplifier = vulnerableAmplifierOnSelf();
        boolean vulnerable = vulnerableAmplifier >= 0;

        if (CharredZombieRules.isImmune(fireDamage, vulnerable, bypasses)) {
            // 只受火焰类伤害：把伤害压到 0，但仍让 super 把「被击中」的全套链路跑完。
            // 绝不能在这里 return false —— 火焰附加的点燃挂在 Player#attack 里
            // EnchantmentHelper.doPostAttackEffects(...) 的调用条件（if (target.hurt(...))）上。
            boolean hit = super.hurt(source, 0.0F);
            if (hit && settings.immuneFeedbackEnabled()) {
                playImmuneFeedback();
            }
            return hit;
        }

        float multiplier = CharredZombieRules.damageMultiplier(
                fireDamage,
                vulnerable,
                vulnerableAmplifier,
                settings.fireDamageMultiplier(),
                settings.vulnerableDamageBonusPerLevel());
        float scaled = amount * multiplier;
        boolean damaged = super.hurt(source, scaled);
        if (damaged && fireDamage && scaled > 0.0F && settings.vulnerableEnabled()) {
            // 先结算火伤再上「易损」：本次命中不吃自己的加成，下一次才破防，避免自我放大。
            // `scaled > 0` 是给极端配置留的口子：fireDamageMultiplier 配成 0 时火伤为 0，
            // 那属于「没打出伤害」，不该算作「被火打裂」。
            applyVulnerable(source, settings);
        }
        return damaged;
    }

    /** 火焰类伤害：原版 {@code is_fire} 标签，加上配置里额外声明的火球类。 */
    public boolean isFireDamage(DamageSource source, CharredZombieConfig.Resolved settings) {
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return true;
        }
        return matchesAny(source, settings.extraFireDamageTypes());
    }

    /** 兜底伤害类型（默认虚空/溺水/摔落）：不走免疫、也不吃任何倍率。 */
    public boolean bypassesImmunity(DamageSource source, CharredZombieConfig.Resolved settings) {
        return matchesAny(source, settings.alwaysEffectiveDamageTypes());
    }

    private static boolean matchesAny(DamageSource source, List<ResourceKey<DamageType>> keys) {
        for (ResourceKey<DamageType> key : keys) {
            if (source.is(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 身上的「易损」等级，{@code -1} 表示没有。
     *
     * <p>用 {@code -1} 而不是 0 表示「没有」，是为了把「没效果」和「0 级效果」区分开：
     * {@link CharredZombieRules#damageMultiplier} 只在 {@code vulnerable} 为真时才乘加成，
     * 而 0 级也应当是「有易损、加成为 1.0」。</p>
     */
    private int vulnerableAmplifierOnSelf() {
        MobEffectInstance instance = this.getEffect(DreamingFishCore_Effects.VULNERABLE);
        return instance == null ? -1 : instance.getAmplifier();
    }

    private void applyVulnerable(DamageSource source, CharredZombieConfig.Resolved settings) {
        int amplifier = CharredZombieRules.refreshedAmplifier(
                vulnerableAmplifierOnSelf(), settings.vulnerableAmplifier());
        Entity attacker = source.getEntity();
        this.addEffect(
                new MobEffectInstance(
                        DreamingFishCore_Effects.VULNERABLE,
                        settings.vulnerableDurationTicks(),
                        amplifier),
                attacker instanceof LivingEntity living ? living : null);
        if (settings.vulnerableGlowing()) {
            // 发光是给玩家的可读信号：它现在打得动了。只在自己点亮过时才负责熄灭，
            // 免得把别的来源（例如光谱箭）给的发光一起清掉。
            this.setGlowingTag(true);
            this.glowingByVulnerable = true;
        }
    }

    /** 免疫时的反馈：灰烬爆开 + 一声熄灭。没有反馈的「打不动」会被当成 bug。 */
    private void playImmuneFeedback() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(
                ParticleTypes.ASH,
                this.getX(),
                this.getY() + this.getBbHeight() * 0.5D,
                this.getZ(),
                IMMUNE_FEEDBACK_PARTICLES,
                this.getBbWidth() * 0.5D,
                this.getBbHeight() * 0.3D,
                this.getBbWidth() * 0.5D,
                0.01D);
        SoundSource soundSource = this.getSoundSource();
        serverLevel.playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                SoundEvents.FIRE_EXTINGUISH,
                soundSource,
                IMMUNE_FEEDBACK_VOLUME,
                IMMUNE_FEEDBACK_PITCH);
    }

    // ------------------------------------------------------------------
    // 环境行为
    // ------------------------------------------------------------------

    /** 白天自燃：默认关掉，否则日光就是免费的火焰伤害，「必须用火」的机制会被白天破解。 */
    @Override
    protected boolean isSunSensitive() {
        return runtimeSettings().burnInDaylight();
    }

    /** 供测试与命令展示读取（{@code isSunSensitive} 是 protected，外部包读不到）。 */
    public boolean burnsInDaylight() {
        return this.isSunSensitive();
    }

    /** 泡水是否腐化成溺尸：默认保留（水把它浇灭、变质，是符合直觉的收场）。 */
    @Override
    protected boolean convertsInWater() {
        return runtimeSettings().convertInWater();
    }

    // ------------------------------------------------------------------
    // 配置与属性
    // ------------------------------------------------------------------

    /** 当前的不可变数值快照；伤害闸门与 AI 只读它。 */
    public CharredZombieConfig.Resolved runtimeSettings() {
        if (this.runtimeSettings == null) {
            this.runtimeSettings = CharredZombieConfig.current().resolve();
        }
        return this.runtimeSettings;
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide) {
            if (this.settingsRefreshCooldown-- <= 0) {
                this.settingsRefreshCooldown = SETTINGS_REFRESH_INTERVAL_TICKS;
                this.refreshRuntimeSettings();
            }
            this.syncBurningBoost();
            this.syncVulnerableGlow();
        }
        super.aiStep();
    }

    private void refreshRuntimeSettings() {
        CharredZombieConfig.Resolved previous = this.runtimeSettings;
        CharredZombieConfig.Resolved next = CharredZombieConfig.current().resolve();
        this.runtimeSettings = next;

        if (previous == null || previous.maxHealth() != next.maxHealth()) {
            this.applyHealthModifier(next);
        }
        if (previous == null || previous.movementSpeed() != next.movementSpeed()) {
            this.applySpeedModifier(next);
        }
        if (previous == null || previous.followRange() != next.followRange()) {
            this.applyFollowRangeModifier(next);
        }
        // 着火增强的数值变了就强制重挂一次，否则要等它先灭火再着火才会用上新数值。
        if (previous == null
                || previous.burningBoostEnabled() != next.burningBoostEnabled()
                || previous.burningSpeedBonus() != next.burningSpeedBonus()
                || previous.burningAttackBonus() != next.burningAttackBonus()) {
            this.burningBoostApplied = false;
        }
    }

    private void applyHealthModifier(CharredZombieConfig.Resolved settings) {
        AttributeInstance health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }
        health.removeModifier(HEALTH_MODIFIER_ID);
        double delta = settings.maxHealth() - BASE_MAX_HEALTH;
        if (Math.abs(delta) > 1.0E-6D) {
            health.addTransientModifier(new AttributeModifier(
                    HEALTH_MODIFIER_ID, delta, AttributeModifier.Operation.ADD_VALUE));
        }
        // 生命上限调小后当前生命必须一起夹回去，否则会出现「血条超出上限」的残留。
        if (this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }

    private void applySpeedModifier(CharredZombieConfig.Resolved settings) {
        AttributeInstance movement = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) {
            return;
        }
        movement.removeModifier(SPEED_MODIFIER_ID);
        double delta = settings.movementSpeed() - BASE_MOVEMENT_SPEED;
        if (Math.abs(delta) > 1.0E-6D) {
            movement.addTransientModifier(new AttributeModifier(
                    SPEED_MODIFIER_ID, delta, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void applyFollowRangeModifier(CharredZombieConfig.Resolved settings) {
        AttributeInstance followRange = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange == null) {
            return;
        }
        followRange.removeModifier(FOLLOW_RANGE_MODIFIER_ID);
        double delta = settings.followRange() - BASE_FOLLOW_RANGE;
        if (Math.abs(delta) > 1.0E-6D) {
            followRange.addTransientModifier(new AttributeModifier(
                    FOLLOW_RANGE_MODIFIER_ID, delta, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** 着火增强：只在状态翻转时增删修饰符，不是每 tick 重设。 */
    private void syncBurningBoost() {
        CharredZombieConfig.Resolved settings = runtimeSettings();
        boolean shouldBoost = CharredZombieRules.shouldBoostWhileBurning(
                settings.burningBoostEnabled(), this.isOnFire());
        if (shouldBoost == this.burningBoostApplied) {
            return;
        }
        this.burningBoostApplied = shouldBoost;
        setOrRemoveModifier(
                this.getAttribute(Attributes.MOVEMENT_SPEED),
                BURNING_SPEED_MODIFIER_ID,
                shouldBoost ? settings.burningSpeedBonus() : 0.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setOrRemoveModifier(
                this.getAttribute(Attributes.ATTACK_DAMAGE),
                BURNING_ATTACK_MODIFIER_ID,
                shouldBoost ? settings.burningAttackBonus() : 0.0D,
                AttributeModifier.Operation.ADD_VALUE);
    }

    private static void setOrRemoveModifier(
            @Nullable AttributeInstance attribute,
            ResourceLocation modifierId,
            double amount,
            AttributeModifier.Operation operation) {
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(modifierId);
        if (Math.abs(amount) > 1.0E-6D) {
            attribute.addTransientModifier(new AttributeModifier(modifierId, amount, operation));
        }
    }

    /** 「易损」结束后把自己点亮的发光收回去（不动别的来源）。 */
    private void syncVulnerableGlow() {
        if (this.glowingByVulnerable && !this.hasEffect(DreamingFishCore_Effects.VULNERABLE)) {
            this.glowingByVulnerable = false;
            this.setGlowingTag(false);
        }
    }

    // ------------------------------------------------------------------
    // 生成与掉落
    // ------------------------------------------------------------------

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        this.settingsRefreshCooldown = SETTINGS_REFRESH_INTERVAL_TICKS;
        this.refreshRuntimeSettings();
        // 套完生命修饰符后再按配置满血出生。
        this.setHealth(this.getMaxHealth());
        return result;
    }

    /** 与围攻/射手僵尸一致：沿用原版僵尸的战利品表（腐肉 + 稀有铁锭/胡萝卜/土豆）。 */
    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ResourceKey.create(
                Registries.LOOT_TABLE,
                ResourceLocation.withDefaultNamespace("entities/zombie"));
    }
}
