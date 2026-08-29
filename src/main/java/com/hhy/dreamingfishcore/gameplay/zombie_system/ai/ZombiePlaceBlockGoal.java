package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntity;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieSpeciesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

import java.util.EnumSet;
import java.util.UUID;

/** Places a configured block as a step or the first bridge block in front of a target. */
public final class ZombiePlaceBlockGoal extends Goal {
    /** Safety valve against a whole horde manufacturing a staircase forever. */
    private static final int MAX_PLACEMENTS_PER_TARGET = 3;
    private final SiegeZombieEntity zombie;
    private BlockPos placementPos;
    private long nextAllowedGameTime;
    private UUID placementTarget;
    private int placementsForTarget;

    public ZombiePlaceBlockGoal(SiegeZombieEntity zombie) {
        this.zombie = zombie;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!zombie.isAbilityEnabled(ZombieSpeciesConfig.Ability.PLACING_BLOCKS)
                || zombie.level().isClientSide
                || zombie.isPassenger()
                || zombie.isVehicle()
                || zombie.level().getGameTime() < nextAllowedGameTime
                || zombie.getPlacementBlocksRemaining() <= 0
                || !EventHooks.canEntityGrief(zombie.level(), zombie)) {
            return false;
        }

        LivingEntity target = zombie.getTarget();
        if (target == null
                || !target.isAlive()
                || !(target instanceof Player)
                // A block is a last-resort climbing aid, not a general bridge
                // builder.  Only a target clearly above the zombie can enable
                // this goal.
                || !zombie.isTargetAbove(target)) {
            return false;
        }

        if (!target.getUUID().equals(placementTarget)) {
            placementTarget = target.getUUID();
            placementsForTarget = 0;
        }

        // Reset the small per-engagement budget once ordinary navigation is
        // usable again; a new unreachable obstruction gets a fresh chance.
        if (!zombie.isVanillaPathBlocked(target)) {
            placementsForTarget = 0;
            return false;
        }
        if (placementsForTarget >= MAX_PLACEMENTS_PER_TARGET
                || ZombieConstructionMemory.hasRecentPlacementForTarget(
                        zombie.level(), target.getUUID())
                || ZombieConstructionMemory.hasRecentPlacementNear(
                        zombie.level(), zombie.blockPosition(),
                        ZombieConstructionMemory.LOCAL_LOCK_RADIUS)) {
            return false;
        }
        placementPos = findPlacementPosition(target, zombie.runtimeSettings());
        if (placementPos == null) {
            // A failed candidate search should not run every other AI tick in
            // a large horde; vanilla navigation gets time to change first.
            nextAllowedGameTime = zombie.level().getGameTime() + 10L;
        }
        return placementPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        if (placementPos == null
                || !zombie.isAbilityEnabled(ZombieSpeciesConfig.Ability.PLACING_BLOCKS)
                || zombie.isPassenger()
                || zombie.isVehicle()) {
            placementPos = null;
            return;
        }
        zombie.getLookControl().setLookAt(
                placementPos.getX() + 0.5D,
                placementPos.getY() + 0.5D,
                placementPos.getZ() + 0.5D,
                30.0F,
                30.0F);

        ZombieSpeciesConfig.ResolvedSettings settings = zombie.runtimeSettings();
        Block block = zombie.getConfiguredPlacementBlock(settings);
        BlockState placedState = Block.updateFromNeighbourShapes(
                block.defaultBlockState(), zombie.level(), placementPos);
        Direction supportDirection = findSupportDirection(zombie.level(), placementPos);
        if (EventHooks.canEntityGrief(zombie.level(), zombie)
                && isValidPlacement(zombie.level(), placementPos, placedState, zombie)
                && supportDirection != null
                && !EventHooks.onBlockPlace(
                        zombie,
                        BlockSnapshot.create(zombie.level().dimension(), zombie.level(), placementPos, 3),
                        supportDirection)
                && zombie.level().setBlock(placementPos, placedState, 3)) {
            zombie.swing(InteractionHand.MAIN_HAND);
            zombie.consumePlacementBlock();
            placementsForTarget++;
            ZombieConstructionMemory.remember(
                    zombie.level(), placementPos, zombie.level().getBlockState(placementPos));
            ZombieConstructionMemory.rememberPlacementForTarget(
                    zombie.level(), placementTarget);
            zombie.level().gameEvent(
                    GameEvent.BLOCK_PLACE,
                    placementPos,
                    GameEvent.Context.of(zombie, placedState));
            zombie.level().playSound(
                    null,
                    placementPos,
                    placedState.getSoundType().getPlaceSound(),
                    SoundSource.BLOCKS,
                    0.75F,
                    0.85F + zombie.getRandom().nextFloat() * 0.2F);
            nextAllowedGameTime = zombie.level().getGameTime() + settings.placeCooldownTicks();
        } else {
            // Avoid retrying a protected/occupied position every goal update.
            nextAllowedGameTime = zombie.level().getGameTime() + 10L;
        }
        placementPos = null;
    }

    private BlockPos findPlacementPosition(
            LivingEntity target,
            ZombieSpeciesConfig.ResolvedSettings settings) {
        if (!(target instanceof Player)) {
            return null;
        }
        Vec3 delta = target.position().subtract(zombie.position());
        double horizontalLength = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontalLength < 0.001D) {
            Vec3 look = zombie.getLookAngle();
            delta = new Vec3(look.x, 0.0D, look.z);
            horizontalLength = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        }
        if (horizontalLength < 0.001D) {
            return null;
        }
        Vec3 direction = new Vec3(delta.x / horizontalLength, 0.0D, delta.z / horizontalLength);
        // Keep this invariant inside the search as well as in canUse().  It
        // prevents a stale goal invocation from placing a horizontal bridge
        // after the target has descended onto the zombie's level.
        if (!zombie.isTargetAbove(target)) {
            return null;
        }
        BlockState placingState = zombie.getConfiguredPlacementBlock(settings).defaultBlockState();
        if (placingState.isAir() || placingState.liquid() || placingState.hasBlockEntity()) {
            return null;
        }

        int baseY = zombie.blockPosition().getY();
        int maxDistance = Math.min(settings.placeRange(), Math.max(1, Mth.ceil(horizontalLength)));
        for (int distance = 1; distance <= maxDistance; distance++) {
            BlockPos ahead = BlockPos.containing(
                    zombie.getX() + direction.x * distance,
                    zombie.getY(),
                    zombie.getZ() + direction.z * distance);

            // Build a one-block ramp immediately before a wall.  Placing on top
            // of the wall would only make the obstacle taller; the ramp reduces
            // the remaining climb instead.
            BlockState wall = zombie.level().getBlockState(ahead);
            BlockState wallHead = zombie.level().getBlockState(ahead.above());
            boolean bodyBlocked = !wall.getCollisionShape(zombie.level(), ahead).isEmpty();
            boolean headBlocked = !wallHead.getCollisionShape(zombie.level(), ahead.above()).isEmpty();
            if (bodyBlocked && headBlocked) {
                int rampDistance = Math.max(0, distance - 1);
                BlockPos ramp = BlockPos.containing(
                        zombie.getX() + direction.x * rampDistance,
                        baseY,
                        zombie.getZ() + direction.z * rampDistance);
                if (isValidPlacement(zombie.level(), ramp, placingState, zombie)) {
                    return ramp;
                }
            }

            // A one-block gap or a step at the zombie's feet.
            BlockPos feet = new BlockPos(ahead.getX(), baseY, ahead.getZ());
            if (isValidPlacement(zombie.level(), feet, placingState, zombie)
                    && shouldBridgeOrStep(feet, ahead, target)) {
                return feet;
            }

            BlockPos lower = feet.below();
            if (isValidPlacement(zombie.level(), lower, placingState, zombie)
                    && !hasCollision(zombie.level(), ahead.below())) {
                return lower;
            }
        }
        return null;
    }

    private boolean shouldBridgeOrStep(BlockPos feet, BlockPos ahead, LivingEntity target) {
        BlockState below = zombie.level().getBlockState(feet.below());
        boolean targetHigher = zombie.isTargetAbove(target);
        boolean obstacle = !zombie.level().getBlockState(ahead).getCollisionShape(zombie.level(), ahead).isEmpty();
        boolean gap = below.isAir()
                || below.liquid()
                || !hasCollision(zombie.level(), feet.below());
        // A crowd bump is not a construction reason.  horizontalCollision is
        // true when another zombie merely brushes this one, and using it here
        // would let a horde manufacture blocks in an otherwise open room.  A
        // real block obstacle or a genuine gap must be present instead.
        return targetHigher && (obstacle || gap);
    }

    private static boolean hasCollision(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static Direction findSupportDirection(Level level, BlockPos pos) {
        // Prefer the block underneath so protection/event handlers see the same
        // face a player would normally click when placing a block.
        if (level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
            return Direction.UP;
        }
        for (Direction supportOffset : Direction.values()) {
            if (supportOffset == Direction.DOWN) {
                continue;
            }
            BlockPos supportPos = pos.relative(supportOffset);
            if (level.getBlockState(supportPos).isFaceSturdy(
                    level, supportPos, supportOffset.getOpposite())) {
                // EventHooks expects the face pointing from the support block
                // towards the destination (the opposite of the offset above).
                return supportOffset.getOpposite();
            }
        }
        return null;
    }

    private static boolean isValidPlacement(
            Level level,
            BlockPos pos,
            BlockState placingState,
            SiegeZombieEntity zombie) {
        BlockState existing = level.getBlockState(pos);
        if (ZombieConstructionMemory.isRecentlyPlaced(level, pos)
                || !(existing.isAir() || existing.canBeReplaced())
                || placingState.isAir()
                || placingState.liquid()
                || placingState.hasBlockEntity()) {
            return false;
        }

        if (!placingState.canSurvive(level, pos)
                || findSupportDirection(level, pos) == null) {
            return false;
        }

        AABB occupied = new AABB(pos);
        if (zombie.getBoundingBox().intersects(occupied)) {
            return false;
        }
        return level.getEntities(zombie, occupied).stream()
                .noneMatch(entity -> entity instanceof LivingEntity);
    }
}
