package com.hhy.dreamingfishcore.gameplay.creeper_system.ender;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.creeper_system.ender.ai.EnderCreeperTeleportGoal;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 末影人苦力怕：会瞬移贴脸的苦力怕，传送时拖着末影人那串紫黑粒子。
 *
 * <p>继承原版 {@link Creeper}，引爆、怕猫、闪电充能全部保留；新增的只有瞬移。瞬移本身没有自研落点
 * 搜索——直接用 {@link LivingEntity#randomTeleport}，它本来就负责「往下找到实地、确认不卡墙不落液体、
 * 失败就退回原位」，并且广播实体事件 46 来画那 128 颗传送粒子（和末影人一模一样）。</p>
 *
 * <h2>三条硬约束</h2>
 * <ul>
 *   <li><b>已点燃就不许瞬移</b>：否则引信一响它闪走，玩家永远炸不到，等于无敌；</li>
 *   <li><b>贴脸（小于 {@code teleportMinDistance}）不许瞬移</b>：否则它会一直闪，永远炸不到人；</li>
 *   <li><b>瞬移有冷却</b>：没有冷却就是每 tick 闪一次的鬼畜。</li>
 * </ul>
 */
public class EnderCreeperEntity extends Creeper {
    private static final ResourceLocation HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "ender_creeper_health");
    private static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "ender_creeper_speed");
    private static final ResourceLocation FOLLOW_RANGE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "ender_creeper_follow_range");

    /** 与 {@link #createAttributes()} 里的基础值一致，配置改动按差值套用修饰符。 */
    private static final double BASE_MAX_HEALTH = 20.0D;
    private static final double BASE_MOVEMENT_SPEED = 0.25D;
    private static final double BASE_FOLLOW_RANGE = 16.0D;

    private static final int SETTINGS_REFRESH_INTERVAL_TICKS = 10;
    /** 落点最多试几次；全失败就这一轮不闪（而不是硬塞到一个坏位置）。 */
    private static final int TELEPORT_ATTEMPTS = 8;
    private static final int AURA_PARTICLE_INTERVAL_TICKS = 10;
    private static final int AURA_PARTICLE_COUNT = 2;
    private static final int WATER_DAMAGE_INTERVAL_TICKS = 20;

    private EnderCreeperConfig.Resolved runtimeSettings;
    private int settingsRefreshCooldown;
    private int teleportCooldown;
    private int auraParticleCooldown;
    private int waterDamageCooldown;

    public EnderCreeperEntity(EntityType<? extends EnderCreeperEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Creeper.createAttributes()
                .add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, BASE_FOLLOW_RANGE);
    }


    // ---- 音效：用对应生物的那套，而不是原版苦力怕的嘶嘶声 ----

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return net.minecraft.sounds.SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return net.minecraft.sounds.SoundEvents.ENDERMAN_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return net.minecraft.sounds.SoundEvents.ENDERMAN_DEATH;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // 瞬移拉近比走路快得多；寻路保留着当兜底（瞬移冷却期间/落点全失败时它还得能挪一步）。
        this.goalSelector.addGoal(1, new EnderCreeperTeleportGoal(this));
    }

    // ------------------------------------------------------------------
    // 瞬移
    // ------------------------------------------------------------------

    /** 冷却是否已走完。触发条件里的另外两条（点燃、最小距离）由调用方判断。 */
    public boolean isTeleportReady() {
        return this.teleportCooldown <= 0;
    }

    public void startTeleportCooldown() {
        this.teleportCooldown = this.runtimeSettings().teleportCooldownTicks();
    }

    /**
     * 闪到目标背后。
     *
     * <p>落点先取「目标位置沿其视线反方向退一点」，再在 {@code landingRadius} 内随机偏，
     * 所以它不会每次都精确落在同一个点，玩家没法靠背对墙一劳永逸。</p>
     */
    public boolean teleportBehindTarget(LivingEntity target) {
        EnderCreeperConfig.Resolved settings = this.runtimeSettings();
        Vec3 anchor = target.position().subtract(target.getLookAngle().scale(settings.landingRadius() * 0.6D));
        return tryTeleportAround(anchor, settings.landingRadius());
    }

    /** 被击中时朝远离攻击者的方向闪走。 */
    public boolean teleportAwayFrom(Entity threat) {
        Vec3 away = this.position().subtract(threat.position());
        if (away.lengthSqr() < 1.0E-4D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 anchor = this.position().add(away.normalize().scale(this.runtimeSettings().dodgeRadius()));
        return tryTeleportAround(anchor, 2.0D);
    }

    /**
     * 在锚点周围随机取点并交给 {@link LivingEntity#randomTeleport}。
     *
     * <p>失败会自己退回原位，所以这里只负责重试，不负责保证。</p>
     */
    private boolean tryTeleportAround(Vec3 anchor, double radius) {
        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double distance = radius * (0.5D + this.random.nextDouble() * 0.5D);
            double x = anchor.x + Math.cos(angle) * distance;
            double z = anchor.z + Math.sin(angle) * distance;
            if (this.randomTeleport(x, anchor.y + 0.5D, z, true)) {
                this.playTeleportSound();
                com.hhy.dreamingfishcore.DreamingFishCore.LOGGER.debug(
                        "[creeper] 末影苦力怕瞬移到 {}", this.blockPosition());
                this.startTeleportCooldown();
                return true;
            }
        }
        // 全失败也走一次冷却：否则 canUse 每 tick 都为真，会变成每 tick 试 8 个落点的高频空转。
        this.startTeleportCooldown();
        return false;
    }

    /** 事件 46 只管粒子；音效得自己补，否则瞬移是「无声的」，玩家会以为它凭空消失。 */
    private void playTeleportSound() {
        this.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.9F + this.random.nextFloat() * 0.2F);
    }

    // ------------------------------------------------------------------
    // 受击闪避
    // ------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean damaged = super.hurt(source, amount);
        if (!damaged || this.level().isClientSide) {
            return damaged;
        }
        // 已点燃时绝不闪：见类注释的「三条硬约束」。
        if (this.isIgnited() || !this.isTeleportReady() || !this.runtimeSettings().teleportEnabled()) {
            return damaged;
        }
        Entity attacker = source.getEntity();
        if (attacker != null && attacker != this
                && this.random.nextFloat() < this.runtimeSettings().dodgeChance()) {
            this.teleportAwayFrom(attacker);
        }
        return damaged;
    }

    // ------------------------------------------------------------------
    // 每 tick
    // ------------------------------------------------------------------

    @Override
    public void aiStep() {
        if (!this.level().isClientSide) {
            if (this.settingsRefreshCooldown-- <= 0) {
                this.settingsRefreshCooldown = SETTINGS_REFRESH_INTERVAL_TICKS;
                this.refreshRuntimeSettings();
            }
            if (this.teleportCooldown > 0) {
                this.teleportCooldown--;
            }
            // 兜底索敌：它只在"有目标"时才会瞬移。若玩家在 24 格内但它没锁上（比如刚出生、
            // 或被打了却没还手），就主动锁定——否则会站着不动，看起来像"不会传送"。
            if (this.getTarget() == null && this.tickCount % 20 == 0) {
                var nearby = this.level().getNearestPlayer(this, 24.0D);
                if (nearby != null && !nearby.isCreative() && !nearby.isSpectator()) {
                    this.setTarget(nearby);
                }
            }

            this.tickAuraParticles();
            this.tickWaterDamage();
        }
        super.aiStep();
    }

    private void tickAuraParticles() {
        if (!this.runtimeSettings().auraParticles() || this.auraParticleCooldown-- > 0) {
            return;
        }
        this.auraParticleCooldown = AURA_PARTICLE_INTERVAL_TICKS;
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(
                ParticleTypes.PORTAL,
                this.getRandomX(0.8D),
                this.getY() + this.random.nextDouble() * this.getBbHeight(),
                this.getRandomZ(0.8D),
                AURA_PARTICLE_COUNT,
                0.05D,
                0.05D,
                0.05D,
                0.01D);
    }

    /**
     * 怕水：与末影人同款，泡在水里每 {@value #WATER_DAMAGE_INTERVAL_TICKS} tick 掉一次血。
     *
     * <p>刻意走 {@code super.hurt} 而不是 {@code this.hurt}：否则水伤会把「受击闪避」触发成
     * 每秒钟闪一次，在水里就变成鬼畜。水伤也不该触发闪避——它要逃的是水，不是人。</p>
     */
    private void tickWaterDamage() {
        EnderCreeperConfig.Resolved settings = this.runtimeSettings();
        if (!settings.waterHurts() || !this.isInWaterRainOrBubble()) {
            return;
        }
        if (this.waterDamageCooldown-- > 0) {
            return;
        }
        this.waterDamageCooldown = WATER_DAMAGE_INTERVAL_TICKS;
        super.hurt(this.damageSources().drown(), (float) settings.waterDamagePerSecond());
    }

    // ------------------------------------------------------------------
    // 配置与属性
    // ------------------------------------------------------------------

    public EnderCreeperConfig.Resolved runtimeSettings() {
        if (this.runtimeSettings == null) {
            this.runtimeSettings = EnderCreeperConfig.current().resolve();
        }
        return this.runtimeSettings;
    }

    private void refreshRuntimeSettings() {
        EnderCreeperConfig.Resolved previous = this.runtimeSettings;
        EnderCreeperConfig.Resolved next = EnderCreeperConfig.current().resolve();
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

    private void applyHealthModifier(EnderCreeperConfig.Resolved settings) {
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
        if (this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }

    private void applySpeedModifier(EnderCreeperConfig.Resolved settings) {
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

    private void applyFollowRangeModifier(EnderCreeperConfig.Resolved settings) {
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
        this.setHealth(this.getMaxHealth());
        return result;
    }

    /** 沿用原版苦力怕的战利品表（火药 + 唱片）。 */
    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ResourceKey.create(
                Registries.LOOT_TABLE,
                ResourceLocation.withDefaultNamespace("entities/creeper"));
    }
}
