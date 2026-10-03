package com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ai;

import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ArcherZombieConfig;
import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ArcherZombieEntity;
import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.ArcherZombieSounds;
import com.hhy.dreamingfishcore.gameplay.zombie_system.archer.BoneSpikeEntity;
import net.minecraft.util.Mth;
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
 *   <li>在 {@code rangedAttackRange} 内且看得见 → 站定 + 转身瞄准，视线通畅时开火；</li>
 *   <li>比 {@code rangedAttackRange} 远，<b>或者射程内但视线被墙挡住</b> → 继续接近。</li>
 * </ul>
 *
 * <p>开火是一个明确的状态机：<b>蓄力</b>（{@code chargeTicks} 个 tick，期间站定、抬手）
 * → <b>发射</b> → <b>冷却</b>（{@code attackIntervalTicks} 个 tick，期间照常走位）。
 * 抬手状态通过实体同步数据传到客户端，由 {@code ArcherZombieModel} 摆出抬臂姿势。</p>
 *
 * <p><b>两个原版机制必须显式处理，否则表现会是「背身开枪」+「站不住」：</b></p>
 * <ol>
 *   <li><b>停要用 {@link net.minecraft.world.entity.Mob#stopInPlace()}，不能用
 *       {@code getNavigation().stop()}。</b>{@code PathNavigation#stop()} 只把 path 置空，
 *       {@code moveControl} 留下的 {@code zza}/{@code xxa}（移动输入）原封不动——
 *       而 {@code Mob#setSpeed(speed)} 会顺手把 {@code zza} 设成 speed，于是「站定蓄力」
 *       实际上一直在以刚才的速度往前傻走。{@code stopInPlace()} 才是把 speed 与输入一起归零。</li>
 *   <li><b>站定后要自己把身体转向目标。</b>原版 {@code BodyRotationControl#clientTick()} 在
 *       <b>移动中</b>会把 {@code yBodyRot} 直接设成 {@code mob.getYRot()}（= 上一次的行进方向），
 *       而 {@code getYRot()} 只有寻路（{@code MoveControl} 的 MOVE_TO）才会被改；头又只能在
 *       {@code getMaxHeadYRot()}（75°）内偏。所以不显式转向的话，它身子朝着旧方向、头扭 75°、
 *       骨刺却按数学精确命中——看起来就是「背身射人」。站定后原版还要 20 tick 才把头转正，
 *       比 16 tick 的蓄力更长，等不起。</li>
 * </ol>
 */
public final class ArcherZombieRangedAttackGoal extends Goal {
    /** 追击/后撤的寻路刷新间隔，避免每 tick 重建路径。 */
    private static final int PATH_REFRESH_TICKS = 10;
    /** 瞄准时的最大转向速度（度/tick）：转满 180° 只要 6 tick，既有转身过程又赶得上开火。 */
    private static final float TURN_SPEED_DEG = 30.0F;

    private final ArcherZombieEntity archer;
    private int chargeTicks;
    private int cooldownTicks;
    private int pathRefreshTicks;

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
    }

    @Override
    public void stop() {
        super.stop();
        this.cancelCharge();
        this.cooldownTicks = 0;
        this.pathRefreshTicks = 0;
        // 这条 Goal 交出去时也要真的停下：光清 path 会把移动输入留着，交给近战前会多滑一段。
        this.holdPosition();
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
        this.holdPosition();
        archer.playSound(ArcherZombieSounds.ARCHER_ZOMBIE_CHARGE.get(), 0.9F, 1.0F);
    }

    private void tickCharge(LivingEntity target, ArcherZombieConfig.Resolved settings) {
        this.chargeTicks++;
        archer.setAggressive(true);
        // 蓄力期间站定 + 持续瞄准：抬手动作才读得出来，也给玩家躲开的机会。
        this.holdPosition();
        faceTarget(target);
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

    /** 调整站位：太远或视线被挡就接近，太近就后撤，其余情况站定瞄准。 */
    private void reposition(LivingEntity target, double distance, ArcherZombieConfig.Resolved settings) {
        if (distance > settings.rangedAttackRange() || !archer.getSensing().hasLineOfSight(target)) {
            // 射程外，或者射程内但被墙挡住，都去接近。
            // （原来「射程内一律站桩」会让它卡在墙后干等，永远打不着。）
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

        // 射程内且看得见：站定瞄准。不要边走边打——移动中身体会被原版强行扭向行进方向。
        this.pathRefreshTicks = 0;
        this.holdPosition();
        faceTarget(target);
    }

    /**
     * 真正停下来，而不是只清路径。
     *
     * <p>{@code getNavigation().stop()} 只把 path 置空，{@code moveControl} 上一次留下的
     * {@code zza}/{@code xxa} 仍然生效（{@code Mob#setSpeed} 会顺手把 {@code zza} 设成 speed），
     * 所以「站定」会变成「保持原速继续走」。{@code stopInPlace()} 会把 speed 与移动输入一起归零。</p>
     */
    private void holdPosition() {
        archer.stopInPlace();
    }

    /**
     * 把身体拧向目标（每 tick 上限 {@link #TURN_SPEED_DEG} 度，看得见转身过程）。
     *
     * <p>不能只靠 {@code LookControl}：它只改 {@code yHeadRot}，而移动中 {@code yBodyRot} 会被
     * 原版设成 {@code getYRot()}，头又被限制在 ±{@code getMaxHeadYRot()} 内，
     * 于是远距离对射时会「身子朝后、头扭过来、子弹照样命中」。这里直接把 {@code yRot} 与
     * {@code yBodyRot} 一起转向目标，头自然跟上。</p>
     */
    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - archer.getX();
        double dz = target.getZ() - archer.getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        // 与原版一致的朝向公式：atan2(dz, dx) 转角度后减 90。
        float wanted = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float delta = Mth.wrapDegrees(wanted - archer.getYRot());
        float yaw = archer.getYRot() + Mth.clamp(delta, -TURN_SPEED_DEG, TURN_SPEED_DEG);
        archer.setYRot(yaw);
        archer.setYBodyRot(yaw);
    }

    private void cancelCharge() {
        this.chargeTicks = 0;
        if (archer.isCharging()) {
            archer.setCharging(false);
        }
        archer.setAggressive(false);
    }
}
