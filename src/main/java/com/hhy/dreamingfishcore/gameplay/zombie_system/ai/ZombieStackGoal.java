package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntity;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieSpeciesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Lets one siege zombie temporarily ride the highest nearby vanilla zombie,
 * then leap forward.  This produces a visible, server-authoritative "zombie
 * wall" while retaining vanilla collision and passenger synchronization.
 */
public final class ZombieStackGoal extends Goal {
    /** Adult zombie dimensions/attachments from vanilla EntityType.ZOMBIE. */
    static final double ADULT_ZOMBIE_HEIGHT = 1.95D;
    static final double ADULT_ZOMBIE_STACK_BASE_OFFSET = 2.0125D - 0.7D;
    private static final double MINIMUM_LAUNCH_CORRIDOR = 0.8D;

    private final SiegeZombieEntity zombie;
    private Zombie platform;
    private UUID stackTargetId;
    private double stackBaseY;
    private int stackTicks;
    private long nextAllowedGameTime;
    private boolean launched;

    public ZombieStackGoal(SiegeZombieEntity zombie) {
        this.zombie = zombie;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        ZombieSpeciesConfig.ResolvedSettings settings = zombie.runtimeSettings();
        if (!zombie.isAbilityEnabled(ZombieSpeciesConfig.Ability.STACKING)
                || zombie.level().isClientSide
                || zombie.isPassenger()
                || zombie.isVehicle()
                || zombie.level().getGameTime() < nextAllowedGameTime) {
            return false;
        }

        LivingEntity target = zombie.getTarget();
        if (!(target instanceof Player)
                || !target.isAlive()
                || !isTargetHighEnough(
                        zombie.getY(),
                        target.getY(),
                        settings.stackMinimumTargetHeight())
                || !isObstacleAhead(target)) {
            return false;
        }

        if (!zombie.isVanillaPathBlocked(target)
                || ZombieConstructionMemory.hasRecentPlacementNear(
                        zombie.level(), zombie.blockPosition(),
                        ZombieConstructionMemory.LOCAL_LOCK_RADIUS)) {
            return false;
        }

        platform = findPlatform(target, settings);
        if (platform == null) {
            nextAllowedGameTime = zombie.level().getGameTime() + 10L;
        } else {
            stackTargetId = target.getUUID();
            stackBaseY = zombie.getY();
        }
        return platform != null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = zombie.getTarget();
        return !launched
                && platform != null
                && platform.isAlive()
                && zombie.isAlive()
                && zombie.isAbilityEnabled(ZombieSpeciesConfig.Ability.STACKING)
                && zombie.isPassenger()
                && zombie.getVehicle() == platform
                && target != null
                && target.isAlive()
                && stackTargetId != null
                && stackTargetId.equals(target.getUUID())
                && isTargetHighEnough(
                        stackBaseY,
                        target.getY(),
                        zombie.runtimeSettings().stackMinimumTargetHeight())
                && stackTicks < zombie.runtimeSettings().stackDurationTicks();
    }

    @Override
    public void start() {
        stackTicks = 0;
        launched = false;
        // The empty-slot check keeps the wall as a single vertical chain. Force
        // only bypasses vanilla's 60-tick remount delay, allowing the configured
        // stack cooldown to remain authoritative; NeoForge's mount event and
        // Entity's cycle checks still run.
        if (platform == null
                || !platform.getPassengers().isEmpty()
                || !hasClearRidingSpace(platform)
                || !zombie.startRiding(platform, true)) {
            launched = true;
        }
    }

    @Override
    public void stop() {
        if (zombie.isPassenger() && zombie.getVehicle() == platform) {
            zombie.stopRiding();
        }
        nextAllowedGameTime = zombie.level().getGameTime()
                + zombie.runtimeSettings().stackCooldownTicks();
        platform = null;
        stackTargetId = null;
        stackBaseY = 0.0D;
        stackTicks = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (platform == null) {
            return;
        }
        LivingEntity target = zombie.getTarget();
        if (target != null) {
            zombie.getLookControl().setLookAt(target, 45.0F, 45.0F);
        }
        stackTicks++;

        ZombieSpeciesConfig.ResolvedSettings settings = zombie.runtimeSettings();
        // Use a bounded half-duration so the default remains snappy while a
        // larger test/story value (for example 40) visibly holds the stack
        // instead of being silently capped at the same eight ticks.
        int launchDelay = Math.min(20, Math.max(2, settings.stackDurationTicks() / 2));
        if (stackTicks >= launchDelay) {
            launchTowardTarget(target, settings);
            launched = true;
        }
    }

    private void launchTowardTarget(
            LivingEntity target,
            ZombieSpeciesConfig.ResolvedSettings settings) {
        Vec3 mountedPosition = zombie.position();
        AABB mountedBox = zombie.getBoundingBox();
        if (zombie.isPassenger()) {
            zombie.stopRiding();
        }
        Vec3 direction;
        if (target == null) {
            direction = zombie.getLookAngle();
        } else {
            direction = target.position().subtract(zombie.position());
        }
        double length = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (length < 0.001D) {
            direction = new Vec3(1.0D, 0.0D, 0.0D);
            length = 1.0D;
        }
        double horizontalX = direction.x / length * settings.stackJumpHorizontalSpeed();
        double horizontalZ = direction.z / length * settings.stackJumpHorizontalSpeed();
        double corridorDistance = Math.max(
                MINIMUM_LAUNCH_CORRIDOR,
                settings.stackJumpHorizontalSpeed() * 4.0D);
        AABB launchCorridor = mountedBox.expandTowards(
                direction.x / length * corridorDistance,
                0.0D,
                direction.z / length * corridorDistance);
        boolean mountedOriginClear = zombie.level().noBlockCollision(zombie, mountedBox);
        if (mountedOriginClear) {
            // LivingEntity#stopRiding chooses a generic dismount point that
            // can be unsuitable beside a wall. Restore the known riding
            // origin before either launching or safely cancelling.
            zombie.setPos(mountedPosition.x, mountedPosition.y, mountedPosition.z);
        }
        if (!mountedOriginClear
                || !zombie.level().noBlockCollision(zombie, launchCorridor)) {
            // A passenger ignores ordinary collision while mounted. Never
            // convert an overlapping/blocked riding position into a forward
            // impulse: that is the path that could place it through a wall.
            zombie.setDeltaMovement(Vec3.ZERO);
            zombie.setJumping(false);
            return;
        }
        zombie.setDeltaMovement(
                horizontalX,
                Math.max(zombie.getDeltaMovement().y, settings.stackJumpVelocity()),
                horizontalZ);
        // Keep the vanilla jump animation/control state in sync with the
        // explicit impulse.  The impulse does the actual launch even while
        // the zombie is briefly airborne as a passenger.
        zombie.setJumping(true);
        zombie.hasImpulse = true;
    }

    private Zombie findPlatform(
            LivingEntity target,
            ZombieSpeciesConfig.ResolvedSettings settings) {
        Vec3 toTarget = target.position().subtract(zombie.position());
        double targetLength = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        if (targetLength < 0.001D) {
            return null;
        }
        Vec3 direction = new Vec3(toTarget.x / targetLength, 0.0D, toTarget.z / targetLength);
        double range = settings.stackSearchRange();
        return zombie.level()
                .getEntitiesOfClass(
                        Zombie.class,
                        zombie.getBoundingBox().inflate(range, 1.5D, range),
                        candidate -> candidate != zombie
                                && candidate.isAlive()
                                && !isInSamePassengerChain(candidate)
                                && isInFrontOfZombie(candidate, target, direction)
                                && candidate.getY() <= zombie.getY() + 0.35D)
                .stream()
                // A nearby stack may already be full; map all candidates before
                // selecting the nearest usable top rather than stopping at the
                // first (full) stack.
                .map(candidate -> highestAvailablePlatform(candidate, settings.maxStackHeight()))
                .filter(Objects::nonNull)
                .filter(this::hasClearRidingSpace)
                .min(Comparator.comparingDouble(candidate -> zombie.distanceToSqr(candidate)))
                .orElse(null);
    }

    private boolean hasClearRidingSpace(Zombie candidate) {
        Vec3 ridingPosition = candidate.getPassengerRidingPosition(zombie)
                .subtract(zombie.getVehicleAttachmentPoint(candidate));
        AABB ridingBox = zombie.getBoundingBox().move(
                ridingPosition.x - zombie.getX(),
                ridingPosition.y - zombie.getY(),
                ridingPosition.z - zombie.getZ());
        return zombie.level().noBlockCollision(zombie, ridingBox);
    }

    static boolean isTargetHighEnough(double zombieY, double targetY, double minimumHeight) {
        return targetY - zombieY >= minimumHeight;
    }

    static double estimatedAdultStackHeight(int layers) {
        if (layers <= 0) {
            return 0.0D;
        }
        return ADULT_ZOMBIE_HEIGHT + (layers - 1) * ADULT_ZOMBIE_STACK_BASE_OFFSET;
    }

    /**
     * Prefer a platform that is already moving toward the target.  Without
     * this directional tie-break, two zombies standing in a line can both
     * choose the other as a mount on alternating ticks, recreating the
     * front/back "左右脑互搏" loop the construction lock is meant to avoid.
     */
    private boolean isInFrontOfZombie(
            Zombie candidate,
            LivingEntity target,
            Vec3 direction) {
        Vec3 offset = candidate.position().subtract(zombie.position());
        double forward = offset.x * direction.x + offset.z * direction.z;
        if (forward > 0.15D) {
            return true;
        }

        // Side-by-side mobs may still form a useful wall, but never let a
        // clearly rearward mob become the platform for the mob in front.
        double zombieDistance = zombie.position().distanceToSqr(target.position());
        double candidateDistance = candidate.position().distanceToSqr(target.position());
        return forward > -0.35D && candidateDistance + 0.25D < zombieDistance;
    }

    private Zombie highestAvailablePlatform(
            Zombie candidate,
            int maxHeight) {
        // Walk to the bottom first, then find the highest free passenger slot.
        // This makes an existing two- or three-zombie chain usable by another
        // zombie while still enforcing the configured maximum height.
        Zombie bottom = candidate;
        Set<Entity> seen = new HashSet<>();
        seen.add(bottom);
        while (bottom.getVehicle() instanceof Zombie parent
                && parent.isAlive()
                && parent != zombie
                && seen.add(parent)) {
            bottom = parent;
        }

        Zombie highest = bottom;
        int depth = 1;
        seen.clear();
        seen.add(bottom);
        while (highest.getFirstPassenger() instanceof Zombie next
                && next.isAlive()
                && next != zombie
                && seen.add(next)) {
            highest = next;
            depth++;
        }
        return depth < maxHeight && highest.getPassengers().isEmpty() ? highest : null;
    }

    /** Returns true when mounting candidate would create a cycle with this zombie. */
    private boolean isInSamePassengerChain(Zombie candidate) {
        for (Entity current = candidate; current != null; current = current.getVehicle()) {
            if (current == zombie) {
                return true;
            }
        }
        for (Entity current = candidate; current != null; current = current.getFirstPassenger()) {
            if (current == zombie) {
                return true;
            }
        }
        return false;
    }

    private boolean isObstacleAhead(LivingEntity target) {
        Vec3 delta = target.position().subtract(zombie.position());
        double length = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (length < 0.001D) {
            return false;
        }
        BlockPos ahead = BlockPos.containing(
                zombie.getX() + delta.x / length * 1.25D,
                zombie.getY(),
                zombie.getZ() + delta.z / length * 1.25D);
        BlockState body = zombie.level().getBlockState(ahead);
        BlockState head = zombie.level().getBlockState(ahead.above());
        boolean wall = !body.getCollisionShape(zombie.level(), ahead).isEmpty()
                && !head.getCollisionShape(zombie.level(), ahead.above()).isEmpty();
        return wall || zombie.isTargetAbove(target) || zombie.horizontalCollision;
    }
}
