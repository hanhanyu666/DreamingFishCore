package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntity;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieTaskLocationRules;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;

/** Preserves vanilla turtle-egg behavior while honoring task-location protection. */
public final class ProtectedTurtleEggGoal extends RemoveBlockGoal {
    private final SiegeZombieEntity zombie;

    public ProtectedTurtleEggGoal(SiegeZombieEntity zombie) {
        super(Blocks.TURTLE_EGG, zombie, 1.0D, 3);
        this.zombie = zombie;
    }

    @Override
    public boolean canUse() {
        // super.canUse() discovers the actual egg in blockPos; only then can
        // the boundary check safely inspect the affected block.
        return super.canUse()
                && !ZombieTaskLocationRules.blocksDestructiveAction(zombie, blockPos);
    }

    @Override
    public boolean canContinueToUse() {
        return !ZombieTaskLocationRules.blocksDestructiveAction(zombie, blockPos)
                && super.canContinueToUse();
    }

    @Override
    public void tick() {
        if (!ZombieTaskLocationRules.blocksDestructiveAction(zombie, blockPos)) {
            super.tick();
        }
    }

    @Override
    public void playDestroyProgressSound(LevelAccessor level, BlockPos pos) {
        level.playSound(
                null,
                pos,
                SoundEvents.ZOMBIE_DESTROY_EGG,
                SoundSource.HOSTILE,
                0.5F,
                0.9F + zombie.getRandom().nextFloat() * 0.2F);
    }

    @Override
    public void playBreakSound(Level level, BlockPos pos) {
        level.playSound(
                null,
                pos,
                SoundEvents.TURTLE_EGG_BREAK,
                SoundSource.BLOCKS,
                0.7F,
                0.9F + level.random.nextFloat() * 0.2F);
    }

    @Override
    public double acceptedDistance() {
        return 1.14D;
    }
}
