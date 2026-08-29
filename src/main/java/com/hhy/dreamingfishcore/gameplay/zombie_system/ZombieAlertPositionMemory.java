package com.hhy.dreamingfishcore.gameplay.zombie_system;

import net.minecraft.core.BlockPos;

/** Pure hidden-target anchor rules shared by sound and broadcast acquisition. */
final class ZombieAlertPositionMemory {
    private ZombieAlertPositionMemory() {
    }

    /**
     * A new sound always refreshes alert time, but only meaningful movement
     * relocates the path anchor. This prevents footsteps inside a tiny room
     * from making an outside horde continually swap walls and paths.
     */
    static boolean shouldRelocate(
            BlockPos currentAnchor,
            BlockPos heardPosition,
            double minimumDistance) {
        if (currentAnchor == null || heardPosition == null) {
            return true;
        }
        double dx = heardPosition.getX() - currentAnchor.getX();
        double dy = heardPosition.getY() - currentAnchor.getY();
        double dz = heardPosition.getZ() - currentAnchor.getZ();
        double distance = Math.max(1.0D, minimumDistance);
        return dx * dx + dy * dy + dz * dz >= distance * distance;
    }
}
