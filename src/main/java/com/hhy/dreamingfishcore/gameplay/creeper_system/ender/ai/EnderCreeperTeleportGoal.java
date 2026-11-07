package com.hhy.dreamingfishcore.gameplay.creeper_system.ender.ai;

import com.hhy.dreamingfishcore.gameplay.creeper_system.ender.EnderCreeperConfig;
import com.hhy.dreamingfishcore.gameplay.creeper_system.ender.EnderCreeperEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * 末影人苦力怕的「追猎瞬移」。
 *
 * <p>这是一个<b>瞬时</b>目标：{@link #canUse()} 判定条件满足就 {@link #start()} 闪一次，
 * 然后立刻结束（{@code canContinueToUse} 恒为 false）。闪完的冷却由实体自己记，
 * 所以下一次能不能闪是由 {@link EnderCreeperEntity#isTeleportReady()} 把关的，这个类不持有状态。</p>
 *
 * <p>不占任何 {@code Flag}：它不寻路、不改朝向，只把实体挪一个位置，和原版的近战/游走目标不冲突。</p>
 */
public final class EnderCreeperTeleportGoal extends Goal {
    private final EnderCreeperEntity mob;

    public EnderCreeperTeleportGoal(EnderCreeperEntity mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        EnderCreeperConfig.Resolved settings = this.mob.runtimeSettings();
        if (!settings.teleportEnabled() || this.mob.isIgnited() || !this.mob.isTeleportReady()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        double distanceSq = this.mob.distanceToSqr(target);
        return distanceSq >= settings.teleportMinDistance() * settings.teleportMinDistance()
                && distanceSq <= settings.teleportMaxDistance() * settings.teleportMaxDistance();
    }

    @Override
    public void start() {
        LivingEntity target = this.mob.getTarget();
        if (target != null) {
            this.mob.teleportBehindTarget(target);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }
}
