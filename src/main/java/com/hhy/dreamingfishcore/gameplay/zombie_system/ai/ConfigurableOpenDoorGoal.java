package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntity;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieSpeciesConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;

/** Vanilla door goal with a live story-stage gate. */
public final class ConfigurableOpenDoorGoal extends OpenDoorGoal {
    private final SiegeZombieEntity zombie;

    public ConfigurableOpenDoorGoal(SiegeZombieEntity zombie) {
        // Keep the door open briefly while the zombie passes, matching vanilla mobs.
        super(zombie, true);
        this.zombie = zombie;
    }

    @Override
    public boolean canUse() {
        if (zombie.isPassenger()
                || zombie.isVehicle()
                || !zombie.isAbilityEnabled(ZombieSpeciesConfig.Ability.OPEN_DOORS)) {
            return false;
        }

        // OpenDoorGoal inspects the active vanilla navigation path and only
        // becomes usable while the mob is colliding with an actual wooden door.
        // A closed door is therefore itself the explicit obstruction; unlike
        // mining/placing/stacking, no broad path probe is needed here (and a
        // pathfinder may legitimately report a door as technically reachable
        // before it has been opened).
        LivingEntity target = zombie.getTarget();
        return target != null
                && target.isAlive()
                && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !zombie.isPassenger()
                && !zombie.isVehicle()
                && zombie.isAbilityEnabled(ZombieSpeciesConfig.Ability.OPEN_DOORS)
                && super.canContinueToUse();
    }
}
