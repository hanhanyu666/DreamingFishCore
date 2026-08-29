package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Refreshes the player target range on every selection pass. The vanilla
 * target goal caches its range when constructed, so this replacement makes
 * story-stage range overrides take effect without changing vanilla timing or
 * line-of-sight rules.
 */
public final class SiegeZombiePlayerTargetGoal extends NearestAttackableTargetGoal<Player> {
    private final SiegeZombieEntity zombie;

    public SiegeZombiePlayerTargetGoal(SiegeZombieEntity zombie) {
        super(zombie, Player.class, 10, true, false, null);
        this.zombie = zombie;
    }

    @Override
    public boolean canUse() {
        // This TargetingConditions instance belongs only to this goal.
        this.targetConditions.range(zombie.getConfiguredTrackingRange());
        return super.canUse();
    }
}
