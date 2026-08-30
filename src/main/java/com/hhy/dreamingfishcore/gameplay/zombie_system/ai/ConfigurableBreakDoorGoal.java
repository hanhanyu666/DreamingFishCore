package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntity;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieSpeciesConfig;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieTaskLocationRules;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;

/** Vanilla hard-mode door breaker with a live task-location protection gate. */
public final class ConfigurableBreakDoorGoal extends BreakDoorGoal {
    private final SiegeZombieEntity zombie;

    public ConfigurableBreakDoorGoal(SiegeZombieEntity zombie) {
        super(zombie, difficulty -> difficulty == Difficulty.HARD);
        this.zombie = zombie;
    }

    @Override
    public boolean canUse() {
        if (!zombie.isAbilityEnabled(ZombieSpeciesConfig.Ability.BREAKING_DOORS)) {
            return false;
        }
        // BreakDoorGoal discovers the actual door position in super.canUse().
        // Only inspect the protection boundary after that discovery; checking
        // the initial BlockPos.ZERO would be incorrect near world spawn.
        return super.canUse()
                && !ZombieTaskLocationRules.blocksDestructiveAction(zombie, doorPos);
    }

    @Override
    public boolean canContinueToUse() {
        return zombie.isAbilityEnabled(ZombieSpeciesConfig.Ability.BREAKING_DOORS)
                && !ZombieTaskLocationRules.blocksDestructiveAction(zombie, doorPos)
                && super.canContinueToUse();
    }

    @Override
    public void tick() {
        // The zone or story-stage setting can change while the vanilla goal is
        // mid-swing. Guard the mutation itself as well as canUse/canContinue.
        if (!ZombieTaskLocationRules.blocksDestructiveAction(zombie, doorPos)) {
            super.tick();
        }
    }
}
