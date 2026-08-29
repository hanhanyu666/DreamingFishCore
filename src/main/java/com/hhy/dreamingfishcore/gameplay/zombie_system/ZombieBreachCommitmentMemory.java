package com.hhy.dreamingfishcore.gameplay.zombie_system;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/** Pure state transitions for one zombie's temporary exterior breach commitment. */
final class ZombieBreachCommitmentMemory {
    private ZombieBreachCommitmentMemory() {
    }

    /**
     * Starts a commitment or extends an active commitment for the same target.
     * Extension deliberately preserves the original anchor: newly heard
     * positions are intelligence for the next decision, not an order to
     * abandon a wall that this zombie has already selected.
     */
    static Snapshot beginOrExtend(
            Snapshot current,
            UUID targetId,
            BlockPos selectedAnchor,
            long gameTime,
            int minimumTicks) {
        if (targetId == null || selectedAnchor == null) {
            throw new IllegalArgumentException("targetId and selectedAnchor are required");
        }
        long requestedExpiry = gameTime + Math.max(20, minimumTicks);
        if (isActive(current, targetId, gameTime)) {
            return new Snapshot(
                    current.targetId(),
                    current.anchor(),
                    Math.max(current.expiresAt(), requestedExpiry));
        }
        return new Snapshot(targetId, selectedAnchor.immutable(), requestedExpiry);
    }

    static boolean isActive(Snapshot snapshot, UUID targetId, long gameTime) {
        return snapshot != null
                && targetId != null
                && targetId.equals(snapshot.targetId())
                && gameTime <= snapshot.expiresAt();
    }

    static BlockPos effectiveAnchor(
            BlockPos latestAlertAnchor,
            Snapshot commitment,
            UUID targetId,
            long gameTime) {
        return isActive(commitment, targetId, gameTime)
                ? commitment.anchor()
                : latestAlertAnchor;
    }

    record Snapshot(UUID targetId, BlockPos anchor, long expiresAt) {
    }
}
