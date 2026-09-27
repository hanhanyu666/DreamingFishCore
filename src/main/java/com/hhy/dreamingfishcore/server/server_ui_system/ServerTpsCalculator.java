package com.hhy.dreamingfishcore.server.server_ui_system;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.TickRateManager;

/**
 * Calculates the server's effective ticks per second using the same formula as
 * NeoForge's {@code /neoforge tps} command.
 *
 * <p>The server records the time spent processing each tick, rather than the
 * time spent sleeping until the next scheduled tick.  A server that finishes
 * a tick in less than its target interval can therefore still only report its
 * configured target rate (normally 20 TPS).  Once processing takes longer
 * than that interval, the value falls below the target and reflects the
 * server's actual capacity.</p>
 */
public final class ServerTpsCalculator {
    public static final float DEFAULT_TPS = 20.0F;

    private ServerTpsCalculator() {
    }

    /**
     * Calculates the effective TPS for a live server.
     *
     * <p>{@link MinecraftServer#getAverageTickTimeNanos()} is Minecraft's
     * already-maintained aggregate of the same rolling tick-time samples used
     * by the command.  Reading that aggregate keeps this display path O(1)
     * instead of scanning the 100-entry array for every refresh.</p>
     */
    public static float calculate(MinecraftServer server) {
        if (server == null) {
            return DEFAULT_TPS;
        }

        TickRateManager tickRateManager = server.tickRateManager();
        if (tickRateManager == null) {
            return calculate(server.getAverageTickTimeNanos(),
                    TimeUtil.NANOSECONDS_PER_SECOND / (long) DEFAULT_TPS,
                    DEFAULT_TPS);
        }

        return calculate(server.getAverageTickTimeNanos(),
                tickRateManager.nanosecondsPerTick(),
                tickRateManager.tickrate());
    }

    /**
     * Pure calculation helper used by the server path and unit tests.
     *
     * @param averageTickTimeNanos average time spent processing one tick
     * @param targetTickTimeNanos configured target interval for one tick
     * @param targetTps configured target rate (the upper bound for the result)
     */
    public static float calculate(long averageTickTimeNanos,
                                  long targetTickTimeNanos,
                                  float targetTps) {
        float safeTargetTps = Float.isFinite(targetTps) && targetTps > 0.0F
                ? targetTps
                : DEFAULT_TPS;
        long safeTargetTickTimeNanos = targetTickTimeNanos > 0L
                ? targetTickTimeNanos
                : Math.max(1L, (long) (TimeUtil.NANOSECONDS_PER_SECOND / safeTargetTps));

        // No completed tick is available immediately after startup.  The
        // configured rate is the only truthful value until the first sample.
        if (averageTickTimeNanos <= 0L) {
            return safeTargetTps;
        }

        // Equivalent to NeoForge TPSCommand:
        // 1000 / max(averageTickTimeMillis, millisecondsPerTick).
        // Keeping the values in nanoseconds avoids a conversion and preserves
        // the precision of Minecraft's maintained aggregate.
        double effectiveTickTimeNanos = Math.max(
                (double) averageTickTimeNanos,
                (double) safeTargetTickTimeNanos);
        double tps = TimeUtil.NANOSECONDS_PER_SECOND / effectiveTickTimeNanos;
        if (!Double.isFinite(tps) || tps < 0.0D) {
            return 0.0F;
        }

        return (float) Math.min(safeTargetTps, tps);
    }
}
