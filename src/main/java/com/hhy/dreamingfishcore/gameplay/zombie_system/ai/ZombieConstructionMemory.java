package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Short-lived server memory for blocks placed by siege zombies.
 *
 * <p>Placed blocks are ordinary Minecraft blocks, so another mob cannot tell
 * from the block state alone that a siege zombie just created one.  Remembering
 * the position briefly lets the digging and placing goals yield to each other
 * while the vanilla navigator recalculates.  This is deliberately not saved:
 * after a restart the world state is authoritative again.</p>
 */
public final class ZombieConstructionMemory {
    /** Five seconds is long enough for nearby mobs to see the new path. */
    private static final long PROTECTION_TICKS = 100L;
    private static final int MAX_ENTRIES = 2048;
    /**
     * Keep the whole local construction party in sync. A radius of one only
     * covered the placer itself; two also covers the common front/back pair
     * where the rear zombie is two blocks away from the new step.
     */
    public static final int LOCAL_LOCK_RADIUS = 2;
    /** A target gets at most one generated step during each protection window. */
    private static final int MAX_TARGET_ENTRIES = 512;
    private static final Map<Key, Entry> RECENT_PLACEMENTS = new HashMap<>();
    private static final Map<TargetKey, Long> RECENT_TARGET_PLACEMENTS = new HashMap<>();

    private ZombieConstructionMemory() {
    }

    public static synchronized void remember(Level level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null || state.isAir()) {
            return;
        }
        long now = level.getGameTime();
        purgeExpired(now);
        if (RECENT_PLACEMENTS.size() >= MAX_ENTRIES) {
            // Keep the memory bounded even if a server has an unusually large
            // siege encounter.  Expired entries are removed first; when the
            // cap is still reached, discard one arbitrary old entry.
            Iterator<Key> iterator = RECENT_PLACEMENTS.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        RECENT_PLACEMENTS.put(
                new Key(level, pos.asLong()),
                new Entry(state, now + PROTECTION_TICKS));
    }

    /** Returns true only while the remembered block still has the same state. */
    public static synchronized boolean isRecentlyPlaced(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        long now = level.getGameTime();
        purgeExpired(now);
        Key key = new Key(level, pos.asLong());
        Entry entry = RECENT_PLACEMENTS.get(key);
        if (entry == null) {
            return false;
        }
        if (!level.getBlockState(pos).equals(entry.state())) {
            RECENT_PLACEMENTS.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Records that a zombie has just built for a target.  This is separate
     * from the block-position memory because two zombies may choose different
     * candidate positions while still pursuing the same player.
     */
    public static synchronized void rememberPlacementForTarget(Level level, UUID targetId) {
        if (level == null || targetId == null) {
            return;
        }
        long now = level.getGameTime();
        purgeExpired(now);
        if (RECENT_TARGET_PLACEMENTS.size() >= MAX_TARGET_ENTRIES) {
            Iterator<TargetKey> iterator = RECENT_TARGET_PLACEMENTS.keySet().iterator();
            if (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
        RECENT_TARGET_PLACEMENTS.put(
                new TargetKey(level, targetId), now + PROTECTION_TICKS);
    }

    /** Returns whether another siege zombie recently built for this target. */
    public static synchronized boolean hasRecentPlacementForTarget(
            Level level,
            UUID targetId) {
        if (level == null || targetId == null) {
            return false;
        }
        long now = level.getGameTime();
        purgeExpired(now);
        Long expiresAt = RECENT_TARGET_PLACEMENTS.get(new TargetKey(level, targetId));
        return expiresAt != null && expiresAt > now;
    }

    /**
     * Used as a local construction lock.  When one zombie just placed a step,
     * nearby zombies wait briefly instead of immediately digging or placing a
     * competing step into the same route.
     */
    public static synchronized boolean hasRecentPlacementNear(
            Level level,
            BlockPos center,
            int radius) {
        if (level == null || center == null || radius < 0) {
            return false;
        }
        long now = level.getGameTime();
        purgeExpired(now);
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Key key = new Key(level, pos.asLong());
                    Entry entry = RECENT_PLACEMENTS.get(key);
                    if (entry != null && level.getBlockState(pos).equals(entry.state())) {
                        return true;
                    } else if (entry != null) {
                        // The block was removed/replaced before its timeout.
                        RECENT_PLACEMENTS.remove(key);
                    }
                }
            }
        }
        return false;
    }

    private static void purgeExpired(long now) {
        RECENT_PLACEMENTS.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        RECENT_TARGET_PLACEMENTS.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private record Key(Level level, long position) {
    }

    private record TargetKey(Level level, UUID targetId) {
    }

    private record Entry(BlockState state, long expiresAt) {
    }
}
