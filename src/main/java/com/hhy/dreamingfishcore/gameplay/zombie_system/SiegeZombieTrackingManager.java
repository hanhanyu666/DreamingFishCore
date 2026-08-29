package com.hhy.dreamingfishcore.gameplay.zombie_system;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server-side tracking extensions for the siege-zombie species.
 *
 * <p>The manager intentionally does not scan every zombie every tick.  A
 * moving player emits a throttled server-side noise sample, which performs one
 * spatial query.  Target acquisition then starts a bounded breadth-first alert
 * wave.  The wave has both a hop limit and a recipient limit, so a dense horde
 * cannot turn one sound into an unbounded O(n^2) cascade.</p>
 */
public final class SiegeZombieTrackingManager {
    private static final double MOVEMENT_THRESHOLD_SQR = 0.00025D;
    private static final double CROUCH_MOVEMENT_THRESHOLD_SQR = 0.0009D;
    private static final double TELEPORT_DISTANCE_SQR = 64.0D;
    private static final int MAX_HARD_WAVE_HOPS = 8;
    private static final int MAX_HARD_WAVE_RECIPIENTS = 256;
    private static final int MAX_HARD_WAVE_EXPANSIONS = 256;
    private static final int MAX_WAVE_EXPANSIONS_PER_SERVER_TICK = 512;
    private static final int MAX_HEARING_RELAY_SEEDS = 8;
    /** Bounds ray-trace work when a sound is emitted inside a very dense horde. */
    private static final int MAX_HEARING_LISTENERS = 256;
    private static final int MAX_HEARING_CANDIDATES = 512;

    private static final Map<MinecraftServer, ServerState> SERVER_STATES = new WeakHashMap<>();

    private SiegeZombieTrackingManager() {
    }

    /** Called from the server-side PlayerTickEvent after the player's movement is applied. */
    public static void onPlayerTick(ServerPlayer player) {
        if (player == null || player.level().isClientSide() || player.getServer() == null) {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        MinecraftServer server = level.getServer();
        ServerState state = stateFor(server);
        cleanupState(state, server.getTickCount());
        long gameTime = level.getGameTime();
        MotionState motion = state.motion.computeIfAbsent(player.getUUID(), ignored -> new MotionState());

        if (motion.level != level || !motion.initialized) {
            motion.level = level;
            motion.x = player.getX();
            motion.z = player.getZ();
            motion.initialized = true;
            return;
        }

        double dx = player.getX() - motion.x;
        double dz = player.getZ() - motion.z;
        double displacementSqr = dx * dx + dz * dz;
        motion.x = player.getX();
        motion.z = player.getZ();

        if (!player.isAlive()
                || player.isSpectator()
                || !player.canBeSeenAsEnemy()
                || displacementSqr > TELEPORT_DISTANCE_SQR) {
            return;
        }

        ZombieSpeciesConfig.ResolvedSettings settings =
                ZombieSpeciesConfig.current().resolveForLevel(level);
        if (!settings.enabled() || !settings.isAbilityEnabled(ZombieSpeciesConfig.Ability.HEARING)) {
            return;
        }

        double velocitySqr = player.getDeltaMovement().horizontalDistanceSqr();
        double movementThreshold = player.isShiftKeyDown()
                ? CROUCH_MOVEMENT_THRESHOLD_SQR
                : MOVEMENT_THRESHOLD_SQR;
        if (Math.max(displacementSqr, velocitySqr) < movementThreshold) {
            return;
        }

        int soundInterval = Math.max(1, settings.hearingCooldownTicks());
        if (gameTime < motion.nextSoundTick) {
            return;
        }
        motion.nextSoundTick = gameTime + soundInterval;

        double hearingRange = settings.hearingRange();
        // Sprinting is louder; crouching is quieter without becoming silent.
        if (player.isSprinting()) {
            hearingRange = Math.min(128.0D, hearingRange * 1.15D);
        } else if (player.isShiftKeyDown()) {
            hearingRange *= 0.55D;
        }

        List<SiegeZombieEntity> listeners = findHearingListeners(level, player, hearingRange);
        if (listeners.isEmpty()) {
            return;
        }

        // Every zombie inside the sound radius hears this sample directly.  A
        // small subset is allowed to become relay seeds; the wave itself is
        // still bounded independently of the size of the horde.
        for (SiegeZombieEntity listener : listeners) {
            listener.acceptTrackingTarget(player, settings.alertMemoryTicks());
        }

        List<SiegeZombieEntity> relaySeeds = new ArrayList<>();
        for (SiegeZombieEntity listener : listeners) {
            if (listener.isAbilityEnabled(ZombieSpeciesConfig.Ability.BROADCASTING)) {
                relaySeeds.add(listener);
                if (relaySeeds.size() >= MAX_HEARING_RELAY_SEEDS) {
                    break;
                }
            }
        }
        if (!relaySeeds.isEmpty()) {
            startWave(level, player, relaySeeds.get(0), relaySeeds, settings, true);
        }
    }

    /** Called when a siege zombie obtains a fresh target through vanilla sight or retaliation. */
    public static void onDirectTargetAcquired(SiegeZombieEntity source, Player target) {
        if (source == null
                || target == null
                || source.level().isClientSide()
                || !(source.level() instanceof ServerLevel level)
                || !target.isAlive()
                || !target.canBeSeenAsEnemy()) {
            return;
        }

        ZombieSpeciesConfig.ResolvedSettings settings = source.runtimeSettings();
        if (!settings.enabled() || !settings.isAbilityEnabled(ZombieSpeciesConfig.Ability.BROADCASTING)) {
            return;
        }
        startWave(level, target, source, List.of(source), settings, false);
    }

    /** Removes motion state when a player leaves, avoiding stale UUID entries on long-running servers. */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null && event.getEntity().getServer() != null) {
            ServerState state = SERVER_STATES.get(event.getEntity().getServer());
            if (state != null) {
                state.motion.remove(event.getEntity().getUUID());
            }
        }
    }

    /** Clears server-scoped state when a server stops or a dev run is restarted. */
    public static void clear(MinecraftServer server) {
        if (server != null) {
            SERVER_STATES.remove(server);
        }
    }

    private static List<SiegeZombieEntity> findHearingListeners(
            ServerLevel level,
            Player player,
            double hearingRange) {
        double rangeSqr = hearingRange * hearingRange;
        AABB searchBox = player.getBoundingBox().inflate(hearingRange, 4.0D, hearingRange);
        List<SiegeZombieEntity> candidates = level.getEntitiesOfClass(
                SiegeZombieEntity.class,
                searchBox,
                zombie -> zombie.isAlive()
                        && zombie.isAbilityEnabled(ZombieSpeciesConfig.Ability.HEARING)
                        && zombie.canAcceptTrackingTarget(player)
                        && zombie.distanceToSqr(player) <= rangeSqr);
        candidates.sort(Comparator.comparingDouble(zombie -> zombie.distanceToSqr(player)));

        List<SiegeZombieEntity> listeners = new ArrayList<>();
        int inspected = 0;
        for (SiegeZombieEntity candidate : candidates) {
            if (++inspected > MAX_HEARING_CANDIDATES
                    || listeners.size() >= MAX_HEARING_LISTENERS) {
                break;
            }
            // LOS is deliberately checked only after the nearest-candidate
            // ordering/cap. This keeps a sound sample from ray-tracing every
            // entity in a pathological pile-up.
            if (!candidate.hasLineOfSight(player)) {
                listeners.add(candidate);
            }
        }
        return listeners;
    }

    private static void startWave(
            ServerLevel level,
            Player target,
            SiegeZombieEntity source,
            List<SiegeZombieEntity> seeds,
            ZombieSpeciesConfig.ResolvedSettings settings,
            boolean markSeeds) {
        if (source == null || !source.isAlive() || !target.isAlive()) {
            return;
        }

        MinecraftServer server = level.getServer();
        ServerState state = stateFor(server);
        long gameTime = level.getGameTime();
        WaveStamp previousWave = state.lastWaveByTarget.get(target.getUUID());
        long cooldown = Math.max(0, settings.broadcastCooldownTicks());
        if (previousWave != null
                && (gameTime == previousWave.gameTime()
                || gameTime - previousWave.gameTime() < cooldown)) {
            return;
        }
        state.lastWaveByTarget.put(
                target.getUUID(),
                new WaveStamp(gameTime, server.getTickCount()));

        int maxHops = Math.min(MAX_HARD_WAVE_HOPS, Math.max(0, settings.broadcastMaxHops()));
        int maxRecipients = Math.min(
                MAX_HARD_WAVE_RECIPIENTS,
                Math.max(1, settings.broadcastMaxRecipients()));
        int memoryTicks = settings.alertMemoryTicks();

        ArrayDeque<RelayNode> queue = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        int seedCount = 0;
        for (SiegeZombieEntity seed : seeds) {
            if (seed == null || !seed.isAlive() || !visited.add(seed.getUUID())) {
                continue;
            }
            if (markSeeds) {
                seed.acceptTrackingTarget(target, memoryTicks);
            }
            queue.addLast(new RelayNode(seed, 0));
            if (++seedCount >= MAX_HEARING_RELAY_SEEDS) {
                break;
            }
        }
        if (queue.isEmpty() || maxHops <= 0) {
            return;
        }

        int recipients = 0;
        int expansions = 0;
        while (!queue.isEmpty()
                && recipients < maxRecipients
                && expansions++ < MAX_HARD_WAVE_EXPANSIONS
                && consumeExpansionBudget(state, server.getTickCount())) {
            RelayNode node = queue.removeFirst();
            if (node.depth() >= maxHops || !node.zombie().isAlive()) {
                continue;
            }

            double radius = settings.broadcastRange();
            double radiusSqr = radius * radius;
            AABB searchBox = node.zombie().getBoundingBox().inflate(radius, 4.0D, radius);
            List<SiegeZombieEntity> candidates = level.getEntitiesOfClass(
                    SiegeZombieEntity.class,
                    searchBox,
                    candidate -> candidate.isAlive()
                            && !visited.contains(candidate.getUUID())
                            && candidate.distanceToSqr(node.zombie()) <= radiusSqr
                            && candidate.canAcceptTrackingTarget(target)
                            && candidate.runtimeSettings().enabled());
            candidates.sort(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(node.zombie())));

            for (SiegeZombieEntity candidate : candidates) {
                if (recipients >= maxRecipients) {
                    break;
                }
                visited.add(candidate.getUUID());
                candidate.acceptTrackingTarget(target, memoryTicks);
                recipients++;

                // A recipient may relay only while the bounded wave still has
                // room.  Once the cap is reached it remains an alerted target
                // but cannot create another expensive search fan-out.
                if (node.depth() + 1 < maxHops
                        && candidate.isAbilityEnabled(ZombieSpeciesConfig.Ability.BROADCASTING)) {
                    queue.addLast(new RelayNode(candidate, node.depth() + 1));
                }
            }
        }
    }

    private static ServerState stateFor(MinecraftServer server) {
        return SERVER_STATES.computeIfAbsent(server, ignored -> new ServerState());
    }

    private static boolean consumeExpansionBudget(ServerState state, int serverTick) {
        if (state.budgetTick != serverTick) {
            state.budgetTick = serverTick;
            state.remainingExpansions = MAX_WAVE_EXPANSIONS_PER_SERVER_TICK;
        }
        if (state.remainingExpansions <= 0) {
            return false;
        }
        state.remainingExpansions--;
        return true;
    }

    private static void cleanupState(ServerState state, int serverTick) {
        if (state.lastCleanupTick != Long.MIN_VALUE
                && serverTick - state.lastCleanupTick < 200) {
            return;
        }
        state.lastCleanupTick = serverTick;
        state.lastWaveByTarget.entrySet().removeIf(
                entry -> serverTick - entry.getValue().serverTick() > 1200);
    }

    private static final class ServerState {
        private final Map<UUID, MotionState> motion = new HashMap<>();
        private final Map<UUID, WaveStamp> lastWaveByTarget = new HashMap<>();
        private int budgetTick = Integer.MIN_VALUE;
        private int remainingExpansions;
        private long lastCleanupTick = Long.MIN_VALUE;
    }

    private static final class MotionState {
        private ServerLevel level;
        private double x;
        private double z;
        private boolean initialized;
        private long nextSoundTick;
    }

    private record RelayNode(SiegeZombieEntity zombie, int depth) {
    }

    private record WaveStamp(long gameTime, long serverTick) {
    }
}
