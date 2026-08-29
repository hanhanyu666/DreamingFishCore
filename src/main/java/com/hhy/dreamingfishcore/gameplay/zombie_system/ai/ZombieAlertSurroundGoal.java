package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntity;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieSpeciesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Continues the same UUID-stable surrounding assignment after a player is
 * heard behind walls. The desired ring point is projected outward to the
 * first exterior position reachable by vanilla navigation; once the zombie
 * arrives, the ordinary dig goal breaches the wall from that side.
 */
public final class ZombieAlertSurroundGoal extends Goal {
    private static final double ARRIVAL_DISTANCE_SQUARED = 2.25D;
    private static final int FAILED_RETRY_TICKS = 30;
    private static final int COMPLETED_RETRY_TICKS = 80;
    private static final int MAX_STAGING_CANDIDATES = 4;

    private final SiegeZombieEntity zombie;
    private BlockPos stagingPos;
    private BlockPos targetAnchor;
    private UUID targetId;
    private Path stagingPath;
    private int travelTicks;
    private int travelLimit;
    private long nextAllowedGameTime;

    public ZombieAlertSurroundGoal(SiegeZombieEntity zombie) {
        this.zombie = zombie;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        ZombieSpeciesConfig.ResolvedSettings settings = zombie.runtimeSettings();
        if (zombie.level().isClientSide
                || !settings.enabled()
                || !settings.surrounding()
                || zombie.isPassenger()
                || zombie.isVehicle()
                || zombie.level().getGameTime() < nextAllowedGameTime) {
            return false;
        }

        LivingEntity target = zombie.getTarget();
        if (!(target instanceof Player player)
                || !player.isAlive()
                || player.isCreative()
                || player.isSpectator()
                || !zombie.hasPursuitLock(player)
                || zombie.getSensing().hasLineOfSight(player)
                || !zombie.isVanillaPathBlocked(player)) {
            return false;
        }

        BlockPos alertAnchor = zombie.getActiveAlertAnchor();
        if (alertAnchor == null) {
            return false;
        }
        List<BlockPos> candidates = findExteriorCandidates(alertAnchor, settings);
        for (BlockPos candidate : candidates) {
            double distanceSquared = zombie.distanceToSqr(
                    candidate.getX() + 0.5D,
                    candidate.getY(),
                    candidate.getZ() + 0.5D);
            if (distanceSquared <= ARRIVAL_DISTANCE_SQUARED) {
                nextAllowedGameTime = zombie.level().getGameTime() + COMPLETED_RETRY_TICKS;
                clearCandidate();
                return false;
            }

            Path path = zombie.getNavigation().createPath(candidate, 0);
            if (path != null && path.canReach()) {
                stagingPos = candidate.immutable();
                stagingPath = path;
                targetId = player.getUUID();
                travelLimit = Mth.clamp(Mth.ceil(Math.sqrt(distanceSquared) * 20.0D), 80, 300);
                zombie.beginOrExtendBreachCommitment(
                        player,
                        Math.max(settings.breachCommitmentTicks(), travelLimit + 60));
                BlockPos committedAnchor = zombie.getActiveAlertAnchor();
                targetAnchor = committedAnchor == null
                        ? alertAnchor.immutable()
                        : committedAnchor.immutable();
                return true;
            }
        }

        nextAllowedGameTime = zombie.level().getGameTime() + FAILED_RETRY_TICKS;
        clearCandidate();
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = zombie.getTarget();
        BlockPos currentAnchor = zombie.getActiveAlertAnchor();
        return stagingPos != null
                && stagingPath != null
                && targetAnchor != null
                && targetId != null
                && target instanceof Player player
                && player.isAlive()
                && player.getUUID().equals(targetId)
                && zombie.hasPursuitLock(player)
                && zombie.isAbilityEnabled(ZombieSpeciesConfig.Ability.SURROUNDING)
                && !zombie.getSensing().hasLineOfSight(player)
                && currentAnchor != null
                && currentAnchor.equals(targetAnchor)
                && zombie.distanceToSqr(
                        stagingPos.getX() + 0.5D,
                        stagingPos.getY(),
                        stagingPos.getZ() + 0.5D) > ARRIVAL_DISTANCE_SQUARED
                && !zombie.getNavigation().isDone()
                && travelTicks < travelLimit;
    }

    @Override
    public void start() {
        travelTicks = 0;
        if (stagingPath != null) {
            zombie.getNavigation().moveTo(stagingPath, 1.1D);
        }
        zombie.extendActiveAlertMemory(Math.max(
                zombie.runtimeSettings().alertMemoryTicks(),
                travelLimit + 60));
        LivingEntity target = zombie.getTarget();
        if (target != null) {
            zombie.beginOrExtendBreachCommitment(
                    target,
                    Math.max(zombie.runtimeSettings().breachCommitmentTicks(), travelLimit + 60));
        }
    }

    @Override
    public void tick() {
        travelTicks++;
        if (stagingPos != null) {
            zombie.getLookControl().setLookAt(
                    stagingPos.getX() + 0.5D,
                    stagingPos.getY() + 1.0D,
                    stagingPos.getZ() + 0.5D,
                    30.0F,
                    30.0F);
        }
    }

    @Override
    public void stop() {
        boolean arrived = stagingPos != null
                && zombie.distanceToSqr(
                        stagingPos.getX() + 0.5D,
                        stagingPos.getY(),
                        stagingPos.getZ() + 0.5D) <= ARRIVAL_DISTANCE_SQUARED;
        zombie.getNavigation().stop();
        nextAllowedGameTime = zombie.level().getGameTime()
                + (arrived ? COMPLETED_RETRY_TICKS : FAILED_RETRY_TICKS);
        clearCandidate();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    /**
     * Walks from the remembered player position toward this zombie's existing
     * angular slot. Every solid-to-open transition is a possible exterior;
     * canUse() accepts the first one vanilla pathfinding can actually reach.
     */
    private List<BlockPos> findExteriorCandidates(
            BlockPos alertAnchor,
            ZombieSpeciesConfig.ResolvedSettings settings) {
        Vec3 outward = ZombieSurroundingSteering.slotDirection(zombie.getUUID());
        int baseY = zombie.blockPosition().getY();
        int maxDistance = Mth.clamp(
                Mth.ceil(Math.max(settings.surroundActivationRange(), settings.digRange() + 4.0D)),
                6,
                24);
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        boolean waitingForExterior = false;

        for (int sample = 0; sample <= maxDistance * 2; sample++) {
            double distance = sample * 0.5D;
            BlockPos pos = BlockPos.containing(
                    alertAnchor.getX() + 0.5D + outward.x * distance,
                    baseY,
                    alertAnchor.getZ() + 0.5D + outward.z * distance);
            if (!visited.add(pos)) {
                continue;
            }

            if (blocksPassage(zombie.level(), pos)) {
                waitingForExterior = true;
                continue;
            }
            if (waitingForExterior && isStandable(zombie.level(), pos)) {
                result.add(pos.immutable());
                waitingForExterior = false;
                if (result.size() >= MAX_STAGING_CANDIDATES) {
                    break;
                }
            }
        }
        return result;
    }

    private static boolean blocksPassage(Level level, BlockPos pos) {
        BlockState body = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        return !body.getCollisionShape(level, pos).isEmpty()
                || !head.getCollisionShape(level, pos.above()).isEmpty();
    }

    private static boolean isStandable(Level level, BlockPos pos) {
        BlockState body = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());
        return !body.liquid()
                && !head.liquid()
                && body.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && !floor.getCollisionShape(level, pos.below()).isEmpty();
    }

    private void clearCandidate() {
        stagingPos = null;
        stagingPath = null;
        targetAnchor = null;
        targetId = null;
        travelTicks = 0;
        travelLimit = 0;
    }
}
