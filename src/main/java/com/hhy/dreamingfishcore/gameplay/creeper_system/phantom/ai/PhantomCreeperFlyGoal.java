package com.hhy.dreamingfishcore.gameplay.creeper_system.phantom.ai;

import com.hhy.dreamingfishcore.gameplay.creeper_system.phantom.PhantomCreeperConfig;
import com.hhy.dreamingfishcore.gameplay.creeper_system.phantom.PhantomCreeperEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * 幻翼苦力怕的飞行控制：**平时在天上盘旋，隔一段时间俯冲一次**。
 *
 * <p>三段式状态机（对应原版幻翼的 circle / sweep / climb）：</p>
 *
 * <ol>
 *   <li><b>盘旋 CIRCLE</b>：在目标（没有目标时是自己的锚点）头顶 {@code hoverHeight} 高度、
 *       半径 {@code max(6, diveRange * 0.8)} 的圆上做**切向**绕飞，方向随机定一次后保持；</li>
 *   <li><b>俯冲 DIVE</b>：冷却走完 + 目标进入 {@code diveRange} 才扑，直冲眼睛高度；</li>
 *   <li><b>爬升 CLIMB</b>：扑空（或撞完没炸到）就爬回盘旋高度，然后进入冷却继续盘旋。</li>
 * </ol>
 *
 * <p><b>为什么要冷却</b>：早先是"一进圈就扑"，结果是它几乎不盘旋、全程贴脸打，
 * 既没有幻翼的观感、玩家也没喘息时间。现在扑一次要等 {@link #DIVE_COOLDOWN_TICKS}。</p>
 *
 * <p><b>撞到人就点引信</b>：俯冲到目标 1.5 格内立刻 {@code ignite()}，之后由原版苦力怕的膨胀
 * 逻辑接管 → 在玩家怀里爆炸；没撞到就爬升重来，绝不半空自爆。</p>
 */
public final class PhantomCreeperFlyGoal extends Goal {

    /** 三个阶段。 */
    private enum Phase { CIRCLE, DIVE, CLIMB }

    /** 惯性系数：越大越贴近「身体有重量」，越小越像瞬移变向。 */
    private static final double INERTIA = 0.18D;
    /** 贴墙时的抬升量，避免抵着墙原地抖。 */
    private static final double WALL_LIFT = 0.08D;
    /** 振翅音效间隔（tick）。 */
    private static final int FLAP_SOUND_INTERVAL = 20;
    /** 俯冲命中判定距离（格）：进到这个距离就点引信。 */
    private static final double DIVE_HIT_RADIUS = 1.5D;
    /** 切向绕飞的角度步长（弧度/tick）：0.12 ≈ 约 3 秒绕一圈。 */
    private static final double ORBIT_STEP = 0.12D;
    /** 盘旋时允许的最低半径（格）。 */
    private static final double MIN_ORBIT_RADIUS = 6.0D;
    /** 两次俯冲之间的冷却（tick）：6 秒。 */
    private static final int DIVE_COOLDOWN_TICKS = 120;
    /** 单次俯冲最长持续（tick）：4 秒没碰到就爬升重来。 */
    private static final int DIVE_TIMEOUT_TICKS = 80;
    /** 没有目标时，绕着出生点盘旋的高度（格）。 */
    private static final double IDLE_HOVER_HEIGHT = 8.0D;

    private final PhantomCreeperEntity mob;
    private Phase phase = Phase.CIRCLE;
    private int phaseTicks;
    private int diveCooldown;
    /** 绕飞方向：+1 逆时针，-1 顺时针（随机一次，之后保持，免得左右摇摆）。 */
    private int orbitDirection;
    private int flapSoundCooldown;
    /** 没有目标时的盘旋中心（出生点附近）。 */
    private Vec3 idleAnchor;

    public PhantomCreeperFlyGoal(PhantomCreeperEntity mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        // 泡水时不飞：让原版的浮水逻辑接管，避免在水里僵住。
        return !this.mob.isInWater();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.phase = Phase.CIRCLE;
        this.phaseTicks = 0;
        this.diveCooldown = 0;
        this.orbitDirection = this.mob.getRandom().nextBoolean() ? 1 : -1;
        this.idleAnchor = this.mob.position();
    }

    @Override
    public void tick() {
        PhantomCreeperConfig.Resolved settings = this.mob.runtimeSettings();
        Vec3 velocity = this.mob.getDeltaMovement();
        LivingEntity target = this.mob.getTarget();
        this.phaseTicks++;
        if (this.diveCooldown > 0) {
            this.diveCooldown--;
        }

        // ---- 没有目标：绕着出生点盘旋，而不是慢慢沉下去 ----
        if (target == null || !target.isAlive()) {
            if (this.idleAnchor == null) {
                this.idleAnchor = this.mob.position();
            }
            Vec3 center = this.idleAnchor.add(0.0D, IDLE_HOVER_HEIGHT, 0.0D);
            Vec3 desired = orbitVelocity(center, settings);
            this.mob.setDeltaMovement(steer(velocity, desired));
            this.phase = Phase.CIRCLE;
            this.tickFlapSound(settings);
            return;
        }

        // 有目标后就不再需要锚点（但保留，目标丢了下一次还会绕原来的位置）
        double distanceSq = this.mob.distanceToSqr(target);
        double diveRange = settings.diveRange();
        Vec3 hoverPoint = target.position().add(0.0D, settings.hoverHeight(), 0.0D);

        // ---- 阶段切换 ----
        if (this.mob.isIgnited()) {
            this.phase = Phase.DIVE;        // 已经贴上引信：一路冲过去
        } else if (this.phase == Phase.DIVE) {
            if (distanceSq <= DIVE_HIT_RADIUS * DIVE_HIT_RADIUS) {
                // 撞上目标 → **自己引爆**（半径 1.8，比原版小；原版引信改不了半径）
                this.mob.explodeFromDive();
                this.phaseTicks = 0;
            } else if (this.phaseTicks > DIVE_TIMEOUT_TICKS) {
                // 扑空：爬升回去，并且进冷却，让它重新盘旋一阵子
                this.phase = Phase.CLIMB;
                this.phaseTicks = 0;
                this.diveCooldown = DIVE_COOLDOWN_TICKS;
            }
        } else if (this.phase == Phase.CLIMB
                && this.mob.position().y >= target.getY() + settings.hoverHeight() * 0.9D) {
            this.phase = Phase.CIRCLE;
            this.phaseTicks = 0;
        } else if (this.phase == Phase.CIRCLE
                && this.diveCooldown <= 0
                && distanceSq <= diveRange * diveRange) {
            // 冷却走完 + 目标进圈 → 俯冲
            this.phase = Phase.DIVE;
            this.phaseTicks = 0;
        }

        // ---- 目标速度 ----
        Vec3 desired;
        switch (this.phase) {
            case DIVE -> {
                Vec3 offset = target.getEyePosition().subtract(this.mob.position());
                desired = offset.lengthSqr() < 1.0E-4D
                        ? Vec3.ZERO
                        : offset.normalize().scale(settings.diveSpeed());
            }
            case CLIMB -> {
                Vec3 offset = hoverPoint.subtract(this.mob.position());
                desired = offset.lengthSqr() < 1.0E-4D
                        ? Vec3.ZERO
                        : offset.normalize().scale(settings.flySpeed());
            }
            default -> desired = orbitVelocity(hoverPoint, settings);
        }

        this.mob.setDeltaMovement(steer(velocity, desired));
        // 始终盯着目标：这就是"面向玩家"的来源（朝向由 LookControl 写进 yHeadRot）
        this.mob.getLookControl().setLookAt(target, 40.0F, 40.0F);
        this.tickFlapSound(settings);
    }

    /** 惯性混合 + 贴墙抬升。 */
    private Vec3 steer(Vec3 velocity, Vec3 desired) {
        Vec3 next = velocity.add(desired.subtract(velocity).scale(INERTIA));
        if (this.mob.horizontalCollision) {
            next = next.add(0.0D, WALL_LIFT, 0.0D);
        }
        return next;
    }

    /**
     * 盘旋：绕着中心那一圈做**切向**运动，同时把高度拉回中心高度。
     *
     * <p>关键是"切向"——直接朝中心飞会变成一头撞上去再停住，看起来像发呆。</p>
     */
    private Vec3 orbitVelocity(Vec3 center, PhantomCreeperConfig.Resolved settings) {
        double radius = Math.max(MIN_ORBIT_RADIUS, settings.diveRange() * 0.8D);
        Vec3 offset = this.mob.position().subtract(center);
        double angle = Math.atan2(offset.z, offset.x);
        if (Double.isNaN(angle)) {
            angle = 0.0D;
        }
        double nextAngle = angle + ORBIT_STEP * this.orbitDirection;
        Vec3 goal = center.add(Math.cos(nextAngle) * radius, 0.0D, Math.sin(nextAngle) * radius);

        Vec3 horizontal = new Vec3(goal.x - this.mob.getX(), 0.0D, goal.z - this.mob.getZ());
        Vec3 tangent = horizontal.lengthSqr() < 1.0E-4D
                ? Vec3.ZERO
                : horizontal.normalize().scale(settings.flySpeed());
        double vertical = (center.y - this.mob.getY()) * 0.15D;
        vertical = Math.max(-settings.flySpeed(), Math.min(settings.flySpeed(), vertical));
        return tangent.add(0.0D, vertical, 0.0D);
    }

    private void tickFlapSound(PhantomCreeperConfig.Resolved settings) {
        if (!settings.flapSoundEnabled()) {
            return;
        }
        if (this.flapSoundCooldown-- > 0) {
            return;
        }
        this.flapSoundCooldown = FLAP_SOUND_INTERVAL;
        this.mob.playFlapSound();
    }
}
