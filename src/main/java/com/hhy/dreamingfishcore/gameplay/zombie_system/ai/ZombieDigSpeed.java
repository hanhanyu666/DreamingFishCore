package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mirrors the normal survival player's per-tick block-damage formula.
 *
 * <p>The current siege zombie deliberately calls the empty-hand entry point.
 * The tool entry point is kept separate so a future tool-bearing variant can
 * use the held item's material-specific destroy speed without making a
 * pickaxe faster than an empty hand against wood.</p>
 */
final class ZombieDigSpeed {
    private static final int HARVESTABLE_BLOCK_DIVISOR = 30;
    private static final int WRONG_TOOL_DIVISOR = 100;
    private static final float EMPTY_HAND_DESTROY_SPEED = 1.0F;

    private ZombieDigSpeed() {
    }

    /** Normal, grounded survival player with an empty hand and no status effects. */
    static int emptyHandRequiredTicks(BlockState state, BlockGetter level, BlockPos pos) {
        return requiredTicks(
                state.getDestroySpeed(level, pos),
                EMPTY_HAND_DESTROY_SPEED,
                !state.requiresCorrectToolForDrops());
    }

    /**
     * Future tool-bearing variant entry point. A wrong tool normally reports
     * speed 1 for the material and still pays the wrong-tool divisor.
     */
    static int toolRequiredTicks(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            ItemStack tool) {
        if (tool == null || tool.isEmpty()) {
            return emptyHandRequiredTicks(state, level, pos);
        }
        boolean canHarvest = !state.requiresCorrectToolForDrops()
                || tool.isCorrectToolForDrops(state);
        return requiredTicks(
                state.getDestroySpeed(level, pos),
                tool.getDestroySpeed(state),
                canHarvest);
    }

    /** Pure formula kept package-visible for regression tests. */
    static int requiredTicks(float hardness, float destroySpeed, boolean canHarvest) {
        if (hardness < 0.0F || destroySpeed <= 0.0F) {
            return Integer.MAX_VALUE;
        }
        if (hardness == 0.0F) {
            return 1;
        }

        int divisor = canHarvest ? HARVESTABLE_BLOCK_DIVISOR : WRONG_TOOL_DIVISOR;
        double ticks = (double) hardness * divisor / destroySpeed;
        if (!Double.isFinite(ticks) || ticks >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int) Math.ceil(ticks));
    }
}
