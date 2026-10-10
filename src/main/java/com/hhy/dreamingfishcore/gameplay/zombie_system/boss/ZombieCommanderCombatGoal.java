package com.hhy.dreamingfishcore.gameplay.zombie_system.boss;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 走位 -> 有预警的施法 -> 连发/召唤 -> 冷却；两种技能互斥，不边跑边开火。
 *
 * <p>贴脸时打的是**三段连招**（横斩 -&gt; 反手斩 -&gt; 举刀劈），不再只是"被堵住时戳一下"。
 * 连招的节奏完全由 {@link ZombieCommanderRules} 里的常量决定，而那些常量必须和资源包里的
 * 动画关键帧对齐 —— 换句话说，**这里是照着动画写的，不是动画照着这里写**。
 * 改动作请先改 {@code tools/commander_animations.py}。</p>
 */
final class ZombieCommanderCombatGoal extends Goal {
    private final ZombieCommanderEntity boss;
    /** 打完最后一发后继续播完动画收势所需的 tick（shoot 动画 44 tick：24 蓄力 + 12 连发 + 8 收势）。 */
    private static final int SHOOT_RECOVER_TICKS = 8;
    /** 旗子插地后把旗子拔回背上的那一段（summon 动画 52 tick，插地落在第 40 tick）。 */
    private static final int SUMMON_RECOVER_TICKS = 12;
    private ZombieCommanderConfig.Resolved castSettings;
    private LivingEntity castTarget;
    private int castTicks;
    private int shotsRemaining;
    private int shotDelay;
    private int castRecover;
    private int pathRefresh;
    private int meleeCooldown;
    /** 0 表示没在连招，1..3 是正在打的第几段。 */
    private int meleeStep;
    private int meleeTicks;
    private boolean meleeHitDone;

    ZombieCommanderCombatGoal(ZombieCommanderEntity boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        LivingEntity target = this.boss.getTarget();
        return this.boss.runtimeSettings().enabled() && target != null && target.isAlive()
                && this.boss.canAttack(target)
                && this.boss.distanceTo(target) <= this.boss.runtimeSettings().followRange();
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { this.pathRefresh = 0; }
    @Override public void stop() {
        cancelCast();
        cancelMelee();
        this.boss.stopInPlace();
        // 冷却存实体里，不因目标离开、Goal 重启而归零。
    }

    @Override public void tick() {
        LivingEntity target = this.boss.getTarget();
        if (target == null) {
            cancelMelee();
            return;
        }
        this.boss.getLookControl().setLookAt(target, 30, 30);
        if (this.meleeCooldown > 0) this.meleeCooldown--;

        if (this.meleeStep > 0) {
            tickMelee(target);
            return;
        }
        if (this.boss.getCastState() != 0) {
            tickCast(target);
            return;
        }
        ZombieCommanderConfig.Resolved settings = this.boss.runtimeSettings();
        double distance = this.boss.distanceTo(target);
        boolean visible = this.boss.getSensing().hasLineOfSight(target);

        // 贴脸优先起连招。这一步刻意排在远程前面：不然 Boss 会在"想拉开距离"和
        // "被贴住只能戳一下"之间来回抖，而三段连招本来就是它解决贴脸的答案。
        if (visible && distance <= settings.meleeRange() && this.meleeCooldown <= 0) {
            beginMelee(target, 1);
            return;
        }
        if (visible && distance <= settings.rangedAttackRange()) {
            if (this.boss.summonCooldown() <= 0 && this.boss.activeMinionCount() < settings.maxMinions()) {
                beginCast(target, settings, 2);
                return;
            }
            if (this.boss.attackCooldown() <= 0 && distance >= settings.preferredMinDistance()) {
                beginCast(target, settings, 1);
                return;
            }
        }
        reposition(target, distance, visible, settings);
    }

    // ------------------------------------------------------------------
    // 三段连招
    // ------------------------------------------------------------------

    private void beginMelee(LivingEntity target, int step) {
        this.meleeStep = step;
        this.meleeTicks = 0;
        this.meleeHitDone = false;
        this.boss.setMeleeStep(step);
        this.boss.setAggressive(true);
        this.boss.stopInPlace();
        faceTarget(target);
    }

    private void tickMelee(LivingEntity target) {
        this.meleeTicks++;
        ZombieCommanderConfig.Resolved settings = this.boss.runtimeSettings();
        // 挥砍期间站定、只转身 —— 边走边砍会让三段动作看着完全不像连招。
        this.boss.stopInPlace();
        faceTarget(target);

        // 命中结算就一次，落在动画里刀挥到最深的那一帧。
        if (!this.meleeHitDone
                && this.meleeTicks >= ZombieCommanderRules.meleeImpactTick(this.meleeStep)) {
            this.meleeHitDone = true;
            if (target.isAlive() && this.boss.canAttack(target)
                    && this.boss.distanceTo(target) <= settings.meleeChainRange()) {
                this.boss.meleeStrike(target, this.meleeStep);
            }
        }

        if (this.meleeTicks < ZombieCommanderRules.meleeLengthTick(this.meleeStep)) return;

        int next = ZombieCommanderRules.meleeChainStep(this.meleeStep, this.boss.distanceTo(target),
                settings.meleeChainRange());
        if (next > 0 && target.isAlive() && this.boss.canAttack(target)
                && this.boss.getSensing().hasLineOfSight(target)) {
            beginMelee(target, next);
            return;
        }
        // 三段打完（或目标被第一刀推开、接不上）才进冷却
        cancelMelee();
        this.meleeCooldown = settings.meleeComboCooldownTicks();
    }

    private void cancelMelee() {
        this.meleeStep = 0;
        this.meleeTicks = 0;
        this.meleeHitDone = false;
        this.boss.setMeleeStep(0);
    }

    // ------------------------------------------------------------------
    // 远程施法
    // ------------------------------------------------------------------

    private void beginCast(LivingEntity target, ZombieCommanderConfig.Resolved settings, int state) {
        this.castSettings = settings;
        this.castTarget = target;
        this.castTicks = 0;
        this.shotsRemaining = 0;
        this.shotDelay = 0;
        this.castRecover = 0;
        this.boss.setCastState(state);
        this.boss.setAggressive(true);
        this.boss.stopInPlace();
        faceTarget(target);
        this.boss.playSound(state == 2 ? SoundEvents.EVOKER_PREPARE_SUMMON : SoundEvents.ZOMBIE_AMBIENT,
                1.1F, 0.7F);
    }

    private void tickCast(LivingEntity target) {
        if (target != this.castTarget || !target.isAlive() || !this.boss.canAttack(target)
                || !this.boss.getSensing().hasLineOfSight(target)
                || this.boss.distanceTo(target) > this.castSettings.rangedAttackRange()) {
            cancelCast();
            this.boss.setAttackCooldown(20);
            return;
        }
        this.boss.stopInPlace();
        faceTarget(target);

        // 召唤：动画在 2.0 秒（= 40 tick，正好是 summonChargeTicks 的默认值）把旗子插进地里，
        // 护卫就在那一刻凭空出现。插完还要播一段收势（把旗子拔回背上），所以再留一段收尾时间，
        // 不然动画会被硬切断、旗子瞬间弹回背后。
        if (this.boss.getCastState() == 2) {
            if (this.castRecover > 0) {
                if (--this.castRecover <= 0) cancelCast();
                return;
            }
            this.castTicks++;
            this.boss.tickChargeAura(this.castTicks, this.castSettings.summonChargeTicks());
            if (this.castTicks >= this.castSettings.summonChargeTicks()) {
                int spawned = this.boss.summonGuards(this.castSettings);
                if (spawned > 0) this.boss.summonBurst();
                this.boss.setSummonCooldown(spawned > 0 ? this.castSettings.summonCooldownTicks() : 100);
                this.boss.setAttackCooldown(20);
                this.castRecover = SUMMON_RECOVER_TICKS;
            }
            return;
        }

        // 射击：动画总长 2.2 秒（44 tick）—— 蓄力 24 tick、三发各隔 6 tick、最后一段收枪。
        // 打完最后一发不能立刻退出施法状态，否则动画会在挥到一半时被掐掉。
        if (this.castRecover > 0) {
            if (--this.castRecover <= 0) cancelCast();
            return;
        }
        this.castTicks++;
        this.boss.tickChargeAura(this.castTicks, this.castSettings.chargeTicks());
        if (this.castTicks < this.castSettings.chargeTicks()) return;
        if (this.castTicks == this.castSettings.chargeTicks()) {
            this.shotsRemaining = this.boss.isEnraged()
                    ? this.castSettings.enragedBurstCount() : this.castSettings.burstCount();
            // 蓄满的那一刻放一次音爆环 —— 「要打出去了」最醒目的一帧，每轮只放一次。
            this.boss.burstAtRelease();
        }
        if (this.shotDelay-- <= 0 && this.shotsRemaining > 0) {
            this.boss.fireAt(target, this.castSettings);
            this.shotsRemaining--;
            this.shotDelay = this.castSettings.burstSpacingTicks() - 1;
            if (this.shotsRemaining == 0) {
                this.boss.setAttackCooldown(this.boss.isEnraged()
                        ? this.castSettings.enragedAttackIntervalTicks()
                        : this.castSettings.attackIntervalTicks());
                this.castRecover = SHOOT_RECOVER_TICKS;
            }
        }
    }

    private void cancelCast() {
        this.boss.setCastState(0);
        this.boss.setAggressive(false);
        this.castSettings = null;
        this.castTarget = null;
        this.castTicks = 0;
        this.shotsRemaining = 0;
        this.castRecover = 0;
    }

    private void reposition(LivingEntity target, double distance, boolean visible,
                            ZombieCommanderConfig.Resolved settings) {
        if (!visible || distance > settings.rangedAttackRange()) {
            if (this.pathRefresh-- <= 0) {
                this.pathRefresh = 10;
                this.boss.getNavigation().moveTo(target, 1.0D);
            }
        } else if (distance < settings.preferredMinDistance()) {
            if (this.pathRefresh-- <= 0) {
                this.pathRefresh = 10;
                Vec3 retreat = DefaultRandomPos.getPosAway(this.boss, 8, 3, target.position());
                if (retreat == null || !this.boss.getNavigation().moveTo(retreat.x, retreat.y, retreat.z, 1.15D)) {
                    this.boss.stopInPlace();
                    faceTarget(target);
                }
            }
        } else {
            this.pathRefresh = 0;
            this.boss.stopInPlace();
            faceTarget(target);
        }
    }

    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - this.boss.getX();
        double dz = target.getZ() - this.boss.getZ();
        if (dx * dx + dz * dz < 1.0E-4D) return;
        float wanted = (float) (Mth.atan2(dz, dx) * 180 / Math.PI) - 90;
        float yaw = this.boss.getYRot() + Mth.clamp(Mth.wrapDegrees(wanted - this.boss.getYRot()), -30, 30);
        this.boss.setYRot(yaw);
        this.boss.setYBodyRot(yaw);
    }
}
