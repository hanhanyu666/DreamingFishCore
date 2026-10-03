package com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ai;

import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ArcherZombieConfig;
import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ArcherZombieEntity;
import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ArcherZombieSounds;
import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.BoneSpikeEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 射手僵尸的远程攻击 AI。
 *
 * <p>站位按距离分三段处理，这一段的目标就是「保持距离」，而不是像普通僵尸那样一路贴身：</p>
 * <ul>
 *   <li>比 {@code meleeHandOffDistance} 还近 → 这个 Goal 直接不参与，把控制权交回原版近战
 *       （所以射手僵尸贴脸时仍然是一只普通僵尸）；</li>
 *   <li>比 {@code preferredMinDistance} 近（但还没到贴脸）→ 朝远离玩家的方向后撤；</li>
 *   <li>在 {@code rangedAttackRange} 内 → 站桩 + 横向游走，视线通畅时开火；</li>
 *   <li>比 {@code rangedAttackRange} 远 → 继续接近，进射程再打。</li>
 * </ul>
 *
 * <p>开火是一个明确的状态机：<b>蓄力</b>（{@code chargeTicks} 个 tick，期间站定、抬手）
 * → <b>发射</b> → <b>冷却</b>（{@code attackIntervalTicks} 个 tick，期间照常走位）。
 * 抬手状态通过实体同步数据传到客户端，由 {@code ArcherZombieModel} 摆出抬臂姿势。</p>
 */
public final class ArcherZombieRangedAttackGoal extends Goal {
    /** 追击/后撤的寻路刷新间隔，避免每 tick 重建路径。 */
    private static final int PATH_REFRESH_TICKS = 10;
    /** 站桩射击时的横向游走方向翻转间隔。 */
    private static final int STRAFE_FLIP_TICKS = 20;

    private final ArcherZombieEntity archer;
    private int chargeTicks;
    private int cooldownTicks;
    private int pathRefreshTicks;
    private int strafeTimer;
    private boolean strafeClockwise;

    public ArcherZombieRangedAttackGoal(ArcherZombieEntity archer) {
        this.archer = archer;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        ArcherZombieConfig.Resolved settings = archer.runtimeArcherSettings();
        if (!settings.enabled()) {
            return false;
        }
        LivingEntity target = archer.getTarget();
        return target != null && target.isAlive() && canEngage(target, settings);
    }

    @Override
    public boolean canContinueToUse() {
        ArcherZombieConfig.Resolved settings = archer.runtimeArcherSettings();
        if (!settings.enabled()) {
            return false;
        }
        LivingEntity target = archer.getTarget();
        return target != null && target.isAlive() && canEngage(target, settings);
    }

    /**
     * 是否可以由远程 AI 接管：尊重原版的创造/旁观免疫，并在目标贴脸时主动让位给近战。
     */
    private boolean canEngage(LivingEntity target, ArcherZombieConfig.Resolved settings) {
        if (!archer.canAttack(target)) {
            return false;
        }
        double distance = archer.distanceTo(target);
        if (distance < settings.meleeHandOffDistance()) {
            return false;
        }
        return distance <= settings.maxPursuitDistance();
    }

    @Override
    public void start() {
        super.start();
        this.chargeTicks = 0;
        this.cooldownTicks = 0;
        this.pathRefreshTicks = 0;
        this.strafeTimer = 0;
    }

    @Override
    public void stop() {
        super.stop();
        this.cancelCharge();
        this.cooldownTicks = 0;
        this.pathRefreshTicks = 0;
        archer.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ArcherZombieConfig.Resolved settings = archer.runtimeArcherSettings();
        LivingEntity target = archer.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        archer.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = archer.distanceTo(target);

        if (this.cooldownTicks > 0) {
            this.cooldownTicks--;
            this.cancelCharge();
            this.reposition(target, distance, settings);
            return;
        }

        if (archer.isCharging()) {
            this.tickCharge(target, settings);
            return;
        }

        if (this.canOpenFire(target, distance, settings)) {
            this.beginCharge(settings);
            return;
        }
        this.reposition(target, distance, settings);
    }

    private boolean canOpenFire(LivingEntity target, double distance, ArcherZombieConfig.Resolved settings) {
        return distance <= settings.rangedAttackRange() && archer.getSensing().hasLineOfSight(target);
    }

    private void beginCharge(ArcherZombieConfig.Resolved settings) {
        this.chargeTicks = 0;
        archer.setChargeDurationTicks(settings.chargeTicks());
        archer.setCharging(true);
        archer.setAggressive(true);
        archer.getNavigation().stop();
        archer.playSound(ArcherZombieSounds.ARCHER_ZOMBIE_CHARGE.get(), 0.9F, 1.0F);
    }

    private void tickCharge(LivingEntity target, ArcherZombieConfig.Resolved settings) {
        this.chargeTicks++;
        archer.setAggressive(true);
        // 蓄力期间站定：抬手动作才读得出来，也给玩家躲开的机会。
        archer.getNavigation().stop();
        if (this.chargeTicks >= settings.chargeTicks()) {
            this.fire(target, settings);
        }
    }

    /** 发射一发骨刺，并进入固定冷却。 */
    private void fire(LivingEntity target, ArcherZombieConfig.Resolved settings) {
        if (archer.level() instanceof net.minecraft.server.level.ServerLevel) {
            BoneSpikeEntity spike = new BoneSpikeEntity(archer.level(), archer, settings);
            this.aimAt(spike, target, settings);
            archer.level().addFreshEntity(spike);
            archer.playSound(ArcherZombieSounds.ARCHER_ZOMBIE_SHOOT.get(), 1.0F, 1.0F);
        }
        this.cancelCharge();
        this.cooldownTicks = settings.attackIntervalTicks();
    }

    /**
     * 瞄准：按骨刺初速估算飞行时间，做一次目标速度预判，再加上重力下坠补偿。
     *
     * <p>下坠量按 {@code ThrowableProjectile} 的实际积分方式算——它每 tick 给竖直速度减去
     * 一个重力值，所以 n 个 tick 的总下坠是 {@code g * n * (n + 1) / 2}，而不是抛物线常见的
     * {@code g * n² / 2}。用错公式会让远距离射击稳定偏低。</p>
     */
    private void aimAt(BoneSpikeEntity spike, LivingEntity target, ArcherZombieConfig.Resolved settings) {
        double dx = target.getX() - spike.getX();
        double dz = target.getZ() - spike.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        double speed = Math.max(0.1D, settings.projectileSpeed());
        double gravity = settings.projectileGravity();
        double vertical = (target.getY() + target.getBbHeight() * 0.5D) - spike.getY();

        // 先粗估一次飞行时间，再用它做预判与下坠补偿，最后按补偿后的向量长度校正一次。
        double flightTicks = Math.sqrt(horizontal * horizontal + vertical * vertical) / speed;
        Vec3 predicted = target.position().add(target.getDeltaMovement().scale(flightTicks));
        vertical = (predicted.y + target.getBbHeight() * 0.5D) - spike.getY();
        double drop = 0.5D * gravity * flightTicks * (flightTicks + 1.0D);
        double aimY = vertical + drop;
        flightTicks = Math.sqrt(horizontal * horizontal + aimY * aimY) / speed;
        predicted = target.position().add(target.getDeltaMovement().scale(flightTicks));
        vertical = (predicted.y + target.getBbHeight() * 0.5D) - spike.getY();
        drop = 0.5D * gravity * flightTicks * (flightTicks + 1.0D);

        spike.shoot(
                dx,
                vertical + drop,
                dz,
                (float) speed,
                (float) settings.projectileInaccuracy());
    }

    /** 调整站位：太远接近、太近后撤、在射程内则站桩横向游走。 */
    private void reposition(LivingEntity target, double distance, ArcherZombieConfig.Resolved settings) {
        if (distance > settings.rangedAttackRange()) {
            if (this.pathRefreshTicks-- <= 0) {
                this.pathRefreshTicks = PATH_REFRESH_TICKS;
                archer.getNavigation().moveTo(target, 1.0D);
            }
            return;
        }

        if (distance < settings.preferredMinDistance()) {
            if (this.pathRefreshTicks-- <= 0) {
                this.pathRefreshTicks = PATH_REFRESH_TICKS;
                Vec3 away = archer.position().subtract(target.position());
                away = away.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : away.normalize();
                Vec3 destination = archer.position().add(away.scale(settings.preferredMinDistance() + 2.0D));
                archer.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0D);
            }
            return;
        }

        // 射程内：停下并横向游走，避免成为活靶子。
        this.pathRefreshTicks = 0;
        archer.getNavigation().stop();
        if (this.strafeTimer++ >= STRAFE_FLIP_TICKS) {
            this.strafeTimer = 0;
            this.strafeClockwise = archer.getRandom().nextBoolean();
        }
        archer.getMoveControl().strafe(0.0F, this.strafeClockwise ? 0.4F : -0.4F);
    }

    private void cancelCharge() {
        this.chargeTicks = 0;
        if (archer.isCharging()) {
            archer.setCharging(false);
        }
        archer.setAggressive(false);
    }
}
