package com.hhy.dreamingfishcore.gameplay.zombie_system.archer;

import com.hhy.dreamingfishcore.effect.DreamingFishCore_Effects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 射手僵尸发射的骨刺。
 *
 * <p>选 {@link ThrowableProjectile} 而不是 {@code AbstractArrow}：骨刺不需要箭的拾取、附魔
 * 与「插在地上」那一整套逻辑，只需要「轻微下坠 + 扫掠命中 + 命中即消散」，投掷物基类正好
 * 只提供这些，重力还能按实例覆盖。</p>
 *
 * <p>行为：
 * <ul>
 *   <li>飞行时按 {@link #getDefaultGravity()} 轻微下坠；</li>
 *   <li>命中生物：造成穿刺伤害，并按配置附加「缓慢」与「流血」；</li>
 *   <li>命中方块或离开发射点超过 {@link #maxRange}：消散并播放音效。</li>
 * </ul>
 *
 * <p>数值在创建时从 {@link ArcherZombieConfig.Resolved} 拷贝进来并写入 NBT，所以在线改配置
 * 不会让「已经在飞的骨刺」半路变伤害。</p>
 */
public class BoneSpikeEntity extends ThrowableProjectile {
    private static final String TAG_DAMAGE = "BoneSpikeDamage";
    private static final String TAG_GRAVITY = "BoneSpikeGravity";
    private static final String TAG_MAX_RANGE = "BoneSpikeMaxRange";
    private static final String TAG_SLOW_DURATION = "BoneSpikeSlowDuration";
    private static final String TAG_SLOW_AMPLIFIER = "BoneSpikeSlowAmplifier";
    private static final String TAG_BLEED_DURATION = "BoneSpikeBleedDuration";
    private static final String TAG_BLEED_AMPLIFIER = "BoneSpikeBleedAmplifier";
    private static final String TAG_ORIGIN = "BoneSpikeOrigin";

    private static final double DEFAULT_DAMAGE = 4.0D;
    private static final double DEFAULT_GRAVITY = 0.045D;
    private static final double DEFAULT_MAX_RANGE = 24.0D;

    private double damage = DEFAULT_DAMAGE;
    private double gravity = DEFAULT_GRAVITY;
    private double maxRange = DEFAULT_MAX_RANGE;
    private int slowDurationTicks;
    private int slowAmplifier;
    private int bleedDurationTicks;
    private int bleedAmplifier;

    /** 发射点，用于射程判定；为 null 时在第一次 tick 里补齐。 */
    @Nullable
    private Vec3 origin;

    public BoneSpikeEntity(EntityType<? extends BoneSpikeEntity> entityType, Level level) {
        super(entityType, level);
    }

    /** 由射手僵尸开火时使用的构造器：把当前配置一次性固化到这一发骨刺上。 */
    public BoneSpikeEntity(Level level, LivingEntity shooter, ArcherZombieConfig.Resolved settings) {
        this(ArcherZombieEntities.BONE_SPIKE.get(), level);
        this.setOwner(shooter);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.15D, shooter.getZ());
        this.origin = this.position();
        this.damage = settings.projectileDamage();
        this.gravity = settings.projectileGravity();
        this.maxRange = settings.projectileMaxRange();
        if (settings.slowEnabled() && settings.slowDurationTicks() > 0) {
            this.slowDurationTicks = settings.slowDurationTicks();
            this.slowAmplifier = settings.slowAmplifier();
        }
        if (settings.bleedEnabled() && settings.bleedDurationTicks() > 0) {
            this.bleedDurationTicks = settings.bleedDurationTicks();
            this.bleedAmplifier = settings.bleedAmplifier();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // 骨刺的行为参数由发射方一次性写死并通过 NBT 保存，不需要额外的同步字段。
    }

    @Override
    protected double getDefaultGravity() {
        return this.gravity;
    }

    /** 不伤害同族：尸潮里不会互相击杀（原版骷髅的箭是会的，这里刻意收紧）。 */
    @Override
    protected boolean canHitEntity(Entity target) {
        if (target instanceof Zombie) {
            return false;
        }
        Entity owner = this.getOwner();
        if (owner != null && owner == target) {
            return false;
        }
        return super.canHitEntity(target);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.origin == null) {
            this.origin = this.position();
        }

        if (this.level().isClientSide) {
            // 细小的飞行拖尾，让弹道可读（下坠时更明显）。
            this.level().addParticle(
                    ParticleTypes.CRIT,
                    this.getX(), this.getY(), this.getZ(),
                    0.0D, 0.0D, 0.0D);
            return;
        }

        if (this.origin.distanceToSqr(this.position()) > this.maxRange * this.maxRange) {
            // 超出射程：无命中地消散，不播放命中音效。
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) {
            return;
        }
        Entity hit = result.getEntity();
        Entity owner = this.getOwner();
        LivingEntity thrower = owner instanceof LivingEntity living ? living : null;
        // 自定义伤害类型（scaling: never），难度影响完全交给配置里的四个倍率决定，
        // 不会和原版 mob_projectile 那套硬编码的难度缩放叠加成双倍变化。
        DamageSource source = this.damageSources().source(
                com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieDamageTypes.BONE_SPIKE, thrower, this);
        float damage = (float) (this.damage
                * ArcherZombieConfig.damageMultiplierFor(this.level().getDifficulty()));

        if (hit.hurt(source, damage) && hit instanceof LivingEntity livingHit) {
            applyOnHitEffects(livingHit, thrower);
        }
    }

    private void applyOnHitEffects(LivingEntity hit, @Nullable LivingEntity thrower) {
        if (this.slowDurationTicks > 0) {
            hit.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, this.slowDurationTicks, this.slowAmplifier), thrower);
        }
        if (this.bleedDurationTicks > 0) {
            hit.addEffect(new MobEffectInstance(
                    DreamingFishCore_Effects.BLEEDING, this.bleedDurationTicks, this.bleedAmplifier), thrower);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        // 命中方块与命中生物统一在这里收尾。两侧都立刻消散：只用服务端消散的话，客户端的
        // 本地预测会让骨刺穿过墙继续飞一小段，看起来像"穿模"。
        if (!this.level().isClientSide) {
            this.burstParticles();
            this.playImpactSound();
        }
        this.discard();
    }

    private void burstParticles() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.CRIT,
                    this.getX(), this.getY(), this.getZ(),
                    10, 0.18D, 0.18D, 0.18D, 0.03D);
        }
    }

    private void playImpactSound() {
        this.level().playSound(
                null,
                this.getX(), this.getY(), this.getZ(),
                ArcherZombieSounds.BONE_SPIKE_HIT.get(),
                SoundSource.NEUTRAL,
                0.9F,
                1.15F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putDouble(TAG_DAMAGE, this.damage);
        compound.putDouble(TAG_GRAVITY, this.gravity);
        compound.putDouble(TAG_MAX_RANGE, this.maxRange);
        compound.putInt(TAG_SLOW_DURATION, this.slowDurationTicks);
        compound.putInt(TAG_SLOW_AMPLIFIER, this.slowAmplifier);
        compound.putInt(TAG_BLEED_DURATION, this.bleedDurationTicks);
        compound.putInt(TAG_BLEED_AMPLIFIER, this.bleedAmplifier);
        if (this.origin != null) {
            compound.putDouble(TAG_ORIGIN + "X", this.origin.x);
            compound.putDouble(TAG_ORIGIN + "Y", this.origin.y);
            compound.putDouble(TAG_ORIGIN + "Z", this.origin.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains(TAG_DAMAGE)) {
            this.damage = compound.getDouble(TAG_DAMAGE);
        }
        if (compound.contains(TAG_GRAVITY)) {
            this.gravity = compound.getDouble(TAG_GRAVITY);
        }
        if (compound.contains(TAG_MAX_RANGE)) {
            this.maxRange = compound.getDouble(TAG_MAX_RANGE);
        }
        this.slowDurationTicks = compound.getInt(TAG_SLOW_DURATION);
        this.slowAmplifier = compound.getInt(TAG_SLOW_AMPLIFIER);
        this.bleedDurationTicks = compound.getInt(TAG_BLEED_DURATION);
        this.bleedAmplifier = compound.getInt(TAG_BLEED_AMPLIFIER);
        if (compound.contains(TAG_ORIGIN + "X")) {
            this.origin = new Vec3(
                    compound.getDouble(TAG_ORIGIN + "X"),
                    compound.getDouble(TAG_ORIGIN + "Y"),
                    compound.getDouble(TAG_ORIGIN + "Z"));
        }
    }
}
