package com.hhy.dreamingfishcore.gameplay.creeper_system.phantom;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.creeper_system.phantom.ai.PhantomCreeperFlyGoal;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

/**
 * 幻翼苦力怕：趴平了在天上飞的苦力怕，背上挂一对幻翼翅膀。
 *
 * <p>继承原版 {@link Creeper}，所以引爆、怕猫、闪电充能这些全部原样保留；新增的只有「飞」这一件事：
 * 常驻 {@code noGravity}，由 {@link PhantomCreeperFlyGoal} 每 tick 直接写速度。巡航到目标头顶、
 * 进圈或点燃后俯冲，落到目标身边时原版 {@code SwellGoal} 会照常触发引爆。</p>
 *
 * <h2>为什么把原版的近战/游走目标摘掉</h2>
 * <p>{@code MeleeAttackGoal} 与 {@code WaterAvoidingRandomStrollGoal} 都会去开寻路、改
 * {@code MoveControl} 的移动输入；而飞行是直接覆盖 {@code setDeltaMovement} 的。两套东西同时跑会
 * 互相抵消——表现就是「悬在半空抽搐」。所以它们在 {@link #registerGoals()} 里被摘掉，
 * 只留下 {@code SwellGoal}（引爆）与怕猫的 {@code AvoidEntityGoal}。</p>
 *
 * <h2>没有 {@code enabled} 总开关</h2>
 * <p>见 {@link PhantomCreeperConfig} 的说明：这个变体的特殊机制就是飞行本身，关掉只会剩一只
 * 不会走路的苦力怕，所以不给坏掉的开关。</p>
 */
public class PhantomCreeperEntity extends Creeper {
    private static final ResourceLocation HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "phantom_creeper_health");
    private static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "phantom_creeper_speed");
    private static final ResourceLocation FOLLOW_RANGE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "phantom_creeper_follow_range");

    /** 与 {@link #createAttributes()} 里的基础值一致，配置改动按差值套用修饰符。 */
    private static final double BASE_MAX_HEALTH = 20.0D;
    private static final double BASE_MOVEMENT_SPEED = 0.25D;
    private static final double BASE_FOLLOW_RANGE = 16.0D;
    /**
     * 爆炸半径（原版苦力怕是 3.0）。
     *
     * <p>1.21.1 的 {@code Creeper#explosionRadius} 是 private、{@code explodeCreeper()} 也是 private，
     * 没法从子类改半径；所以幻翼苦力怕**不用原版那套引信**，而是自己在俯冲命中时引爆，
     * 半径取 1.8——它是扑脸打法，玩家很难躲，威力必须比原版小。</p>
     */
    private static final float EXPLOSION_RADIUS = 1.8F;

    private static final int SETTINGS_REFRESH_INTERVAL_TICKS = 10;

    private PhantomCreeperConfig.Resolved runtimeSettings;
    private int settingsRefreshCooldown;

    public PhantomCreeperEntity(EntityType<? extends PhantomCreeperEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 生命与移速沿用原版苦力怕，只把索敌从 16 拉到 48——它会飞，看得远才有意义。
        return Creeper.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE);
    }


    /**
     * 俯冲命中时自己引爆：半径 {@link #EXPLOSION_RADIUS}，破坏方块按 MOB 规则（与原版苦力怕同类）。
     *
     * <p>不用原版引信的原因见 {@link #EXPLOSION_RADIUS}：半径改不了，而它又是扑脸打法。</p>
     */
    public void explodeFromDive() {
        if (this.level().isClientSide) {
            return;
        }
        this.level().explode(this, this.getX(), this.getY(0.0625D), this.getZ(),
                EXPLOSION_RADIUS, net.minecraft.world.level.Level.ExplosionInteraction.MOB);
        this.discard();
    }

    // ---- 音效：用对应生物的那套，而不是原版苦力怕的嘶嘶声 ----

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return net.minecraft.sounds.SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return net.minecraft.sounds.SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return net.minecraft.sounds.SoundEvents.PHANTOM_DEATH;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // 见类注释「为什么把原版的近战/游走目标摘掉」。
        this.goalSelector.removeAllGoals(goal ->
                goal instanceof MeleeAttackGoal || goal instanceof WaterAvoidingRandomStrollGoal);
        this.goalSelector.addGoal(0, new PhantomCreeperFlyGoal(this));
    }

    // ------------------------------------------------------------------
    // 飞行状态
    // ------------------------------------------------------------------

    @Override
    public void aiStep() {
        if (!this.level().isClientSide && this.settingsRefreshCooldown-- <= 0) {
            this.settingsRefreshCooldown = SETTINGS_REFRESH_INTERVAL_TICKS;
            this.refreshRuntimeSettings();
        }
        // 泡水时交还重力，让原版浮水逻辑把它托起来；出水后重新失重。
        this.setNoGravity(!this.isInWater());
        super.aiStep();
    }

    /**
     * 渲染是否使用「趴平」的姿势。
     *
     * <p>泡水时它其实是被托在水面上的，这时保持直立更像溺水而不是飞行，所以只在水外趴平。</p>
     */
    public boolean isFlyingPose() {
        return !this.isInWater();
    }

    /** 飞行怪不该吃摔落伤害；顺带也不会因为 {@code Creeper#causeFallDamage} 里那段「摔一下就更急着炸」而被意外引爆。 */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    /** 振翅音效。由飞行目标按固定间隔调用，实体只负责播。 */
    public void playFlapSound() {
        this.playSound(SoundEvents.PHANTOM_FLAP, 0.6F, 0.9F + this.random.nextFloat() * 0.2F);
    }

    // ------------------------------------------------------------------
    // 配置与属性
    // ------------------------------------------------------------------

    /** 当前的不可变数值快照；飞行目标只读它。 */
    public PhantomCreeperConfig.Resolved runtimeSettings() {
        if (this.runtimeSettings == null) {
            this.runtimeSettings = PhantomCreeperConfig.current().resolve();
        }
        return this.runtimeSettings;
    }

    private void refreshRuntimeSettings() {
        PhantomCreeperConfig.Resolved previous = this.runtimeSettings;
        PhantomCreeperConfig.Resolved next = PhantomCreeperConfig.current().resolve();
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
    }

    private void applyHealthModifier(PhantomCreeperConfig.Resolved settings) {
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
        // 上限调小后当前生命必须一起夹回去，否则会出现「血条超出上限」的残留。
        if (this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }

    private void applySpeedModifier(PhantomCreeperConfig.Resolved settings) {
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

    private void applyFollowRangeModifier(PhantomCreeperConfig.Resolved settings) {
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

    /** 沿用原版苦力怕的战利品表（火药 + 唱片）；不覆写会按注册名去找一张不存在的表，什么都不掉。 */
    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ResourceKey.create(
                Registries.LOOT_TABLE,
                ResourceLocation.withDefaultNamespace("entities/creeper"));
    }
}
