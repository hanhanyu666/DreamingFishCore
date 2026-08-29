package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Keeps a target that was supplied by a movement sound or a bounded alert
 * broadcast.  Vanilla's player target goal still handles visible targets;
 * this goal only owns the short-lived hidden-target marker installed by the
 * tracking manager.
 */
public final class SiegeZombieAlertTargetGoal extends TargetGoal {
    private static final TargetingConditions ALERT_TARGETING = TargetingConditions
            .forCombat()
            .ignoreLineOfSight();

    private final SiegeZombieEntity zombie;

    public SiegeZombieAlertTargetGoal(SiegeZombieEntity zombie) {
        super(zombie, false, false);
        this.zombie = zombie;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!zombie.hasActiveAlertTarget()) {
            return false;
        }
        LivingEntity target = zombie.getTarget();
        double range = zombie.getAlertPursuitRange();
        return target instanceof Player
                && zombie.distanceToSqr(target) <= range * range
                && this.canAttack(target, ALERT_TARGETING);
    }

    @Override
    public boolean canContinueToUse() {
        return zombie.hasActiveAlertTarget() && super.canContinueToUse();
    }

    @Override
    protected double getFollowDistance() {
        // A relay recipient can initially be farther than the 45-block direct
        // sight range (for example the 55-block zombie in the design). Give
        // the temporary alert enough room to walk toward the player while
        // still bounding a chain by its configured hop count.
        return zombie.getAlertPursuitRange();
    }

    @Override
    public void start() {
        this.targetMob = zombie.getTarget();
        super.start();
    }
}
