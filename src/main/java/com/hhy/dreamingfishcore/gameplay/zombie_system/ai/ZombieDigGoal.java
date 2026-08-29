package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntity;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieSpeciesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Breaks the first breakable block between a siege zombie and its target.
 *
 * <p>This goal intentionally uses the normal block-destruction hooks, so
 * {@code mobGriefing}, protection plugins and block-specific entity-destroy
 * rules still apply.  Hard/unbreakable blocks and block entities are skipped.</p>
 */
public final class ZombieDigGoal extends Goal {
    /** Body/head samples form a normal two-block-high Minecraft doorway. */
    private static final double BODY_RAY_HEIGHT = 0.65D;
    private static final double HEAD_RAY_HEIGHT = 1.45D;
    private static final double RAY_RANGE_PADDING = 0.75D;

    private final SiegeZombieEntity zombie;
    private BlockPos digPos;
    private int digTicks;
    private int requiredTicks;
    private int lastBreakProgress = -1;
    private long nextAllowedGameTime;

    public ZombieDigGoal(SiegeZombieEntity zombie) {
        this.zombie = zombie;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!zombie.isAbilityEnabled(ZombieSpeciesConfig.Ability.DIGGING)
                || zombie.level().isClientSide
                || zombie.isPassenger()
                || zombie.isVehicle()
                || zombie.level().getGameTime() < nextAllowedGameTime
                || !EventHooks.canEntityGrief(zombie.level(), zombie)) {
            return false;
        }

        LivingEntity target = zombie.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }

        // Vanilla navigation gets first refusal.  In particular, a pillar or
        // a one-block offset that the normal zombie can walk around must never
        // be treated as a mining target.  The shared gate also gives the
        // vanilla attack goal a short chance to finish its path attempt.
        if (!zombie.isVanillaPathBlocked(target)
                || ZombieConstructionMemory.hasRecentPlacementForTarget(
                        zombie.level(), target.getUUID())
                || ZombieConstructionMemory.hasRecentPlacementNear(
                        zombie.level(), zombie.blockPosition(),
                        ZombieConstructionMemory.LOCAL_LOCK_RADIUS)) {
            return false;
        }

        ZombieSpeciesConfig.ResolvedSettings settings = zombie.runtimeSettings();
        this.digPos = findDigTarget(target, settings);
        if (this.digPos == null) {
            // Do not rescan the same obstruction every goal update when the
            // configured allow/protection rules reject all nearby blocks.
            nextAllowedGameTime = zombie.level().getGameTime() + 10L;
        }
        return this.digPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = zombie.getTarget();
        return digPos != null
                && zombie.isAlive()
                && !zombie.isPassenger()
                && !zombie.isVehicle()
                && zombie.isAbilityEnabled(ZombieSpeciesConfig.Ability.DIGGING)
                && target != null
                && target.isAlive()
                && zombie.isVanillaPathBlocked(target)
                && !ZombieConstructionMemory.hasRecentPlacementForTarget(
                        zombie.level(), target.getUUID())
                && !ZombieConstructionMemory.hasRecentPlacementNear(
                        zombie.level(), zombie.blockPosition(),
                        ZombieConstructionMemory.LOCAL_LOCK_RADIUS)
                && digTicks <= requiredTicks + 40
                && isDiggable(zombie.level(), digPos, zombie.runtimeSettings(), zombie);
    }

    @Override
    public void start() {
        digTicks = 0;
        lastBreakProgress = -1;
        BlockState state = zombie.level().getBlockState(digPos);
        ZombieSpeciesConfig.ResolvedSettings settings = zombie.runtimeSettings();
        // This species currently mines as a normal grounded survival player
        // with an empty hand. Tool-bearing variants can later opt into
        // ZombieDigSpeed#toolRequiredTicks without changing this goal.
        requiredTicks = ZombieDigSpeed.emptyHandRequiredTicks(
                state,
                zombie.level(),
                digPos);
        LivingEntity target = zombie.getTarget();
        if (target != null) {
            zombie.beginOrExtendBreachCommitment(
                    target,
                    Math.max(
                            settings.breachCommitmentTicks(),
                            requiredTicks + settings.digCooldownTicks() + 60));
        }
        // A heard/relayed player may stop moving while the zombie is already
        // opening a wall. Keep that existing alert alive long enough to
        // finish this block and select the second block of the doorway.
        zombie.extendActiveAlertMemory(
                requiredTicks + settings.digCooldownTicks() + 40);
        zombie.getNavigation().stop();
    }

    @Override
    public void stop() {
        if (digPos != null && lastBreakProgress >= 0) {
            zombie.level().destroyBlockProgress(zombie.getId(), digPos, -1);
        }
        if (digPos != null) {
            nextAllowedGameTime = zombie.level().getGameTime()
                    + zombie.runtimeSettings().digCooldownTicks();
        }
        digPos = null;
        digTicks = 0;
        lastBreakProgress = -1;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (digPos == null) {
            return;
        }

        zombie.getLookControl().setLookAt(
                digPos.getX() + 0.5D,
                digPos.getY() + 0.5D,
                digPos.getZ() + 0.5D,
                30.0F,
                30.0F);

        double distance = zombie.distanceToSqr(
                digPos.getX() + 0.5D,
                digPos.getY() + 0.5D,
                digPos.getZ() + 0.5D);
        if (distance > 8.0D) {
            if (digTicks % 10 == 0) {
                zombie.getNavigation().moveTo(
                        digPos.getX() + 0.5D,
                        digPos.getY() + 1.0D,
                        digPos.getZ() + 0.5D,
                        1.15D);
            }
            return;
        }

        zombie.getNavigation().stop();
        if (digTicks % 6 == 0) {
            zombie.swing(InteractionHand.MAIN_HAND);
        }

        digTicks++;
        int progress = Mth.clamp((int) ((float) digTicks / (float) requiredTicks * 10.0F), 0, 9);
        if (progress != lastBreakProgress) {
            zombie.level().destroyBlockProgress(zombie.getId(), digPos, progress);
            lastBreakProgress = progress;
        }

        if (digTicks >= requiredTicks && isDiggable(zombie.level(), digPos, zombie.runtimeSettings(), zombie)) {
            // The hook is checked again immediately before mutation in case a protection rule changed.
            if (CommonHooks.canEntityDestroy(zombie.level(), digPos, zombie)) {
                zombie.level().destroyBlock(digPos, true, zombie);
            }
            zombie.level().destroyBlockProgress(zombie.getId(), digPos, -1);
            nextAllowedGameTime = zombie.level().getGameTime()
                    + zombie.runtimeSettings().digCooldownTicks();
            digPos = null;
        }
    }

    private BlockPos findDigTarget(LivingEntity target, ZombieSpeciesConfig.ResolvedSettings settings) {
        // Open the head space first. This avoids asking navigation to enter a
        // one-block-high hole and makes the following body block produce a
        // normal two-block doorway rather than a random tunnel.
        BlockPos headBlock = firstRayObstacle(target, settings, HEAD_RAY_HEIGHT);
        if (headBlock != null) {
            return headBlock;
        }
        return firstRayObstacle(target, settings, BODY_RAY_HEIGHT);
    }

    /**
     * Finds the first collidable block on a level-adjusted ray toward the
     * target. Unlike the old candidate filter, this does not require the
     * block above a wall to be air: a complete wall or a wall joined to a roof
     * is exactly the obstruction a siege zombie must be able to breach.
     */
    @Nullable
    private BlockPos firstRayObstacle(
            LivingEntity target,
            ZombieSpeciesConfig.ResolvedSettings settings,
            double sampleHeight) {
        double zombieHeight = Math.min(sampleHeight, Math.max(0.1D, zombie.getBbHeight() - 0.1D));
        double targetHeight = Math.min(sampleHeight, Math.max(0.1D, target.getBbHeight() - 0.1D));
        Vec3 start = zombie.position().add(0.0D, zombieHeight, 0.0D);
        Vec3 targetPoint = zombie.getPursuitPosition(target).add(0.0D, targetHeight, 0.0D);
        Vec3 delta = targetPoint.subtract(start);
        double length = delta.length();
        if (length < 0.001D) {
            return null;
        }

        double rayLength = Math.min(length, settings.digRange() + RAY_RANGE_PADDING);
        Vec3 end = start.add(delta.scale(rayLength / length));
        BlockHitResult hit = zombie.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                zombie));
        if (hit.getType() != HitResult.Type.BLOCK
                // A vertical face means the ray found terrain, a floor or a
                // ceiling. Stacking/placing owns those cases; digging should
                // only create an entrance through a wall-facing surface.
                || hit.getDirection().getAxis() == Direction.Axis.Y) {
            return null;
        }

        BlockPos candidate = hit.getBlockPos();
        int baseY = zombie.blockPosition().getY();
        if (candidate.getY() < baseY || candidate.getY() > baseY + 2) {
            return null;
        }
        double allowedDistance = settings.digRange() + RAY_RANGE_PADDING;
        if (zombie.distanceToSqr(
                candidate.getX() + 0.5D,
                candidate.getY() + 0.5D,
                candidate.getZ() + 0.5D) > allowedDistance * allowedDistance) {
            return null;
        }
        return isDiggable(zombie.level(), candidate, settings, zombie)
                ? candidate.immutable()
                : null;
    }

    private static boolean isDiggable(
            Level level,
            BlockPos pos,
            ZombieSpeciesConfig.ResolvedSettings settings,
            SiegeZombieEntity zombie) {
        BlockState state = level.getBlockState(pos);
        if (ZombieConstructionMemory.isRecentlyPlaced(level, pos)
                || state.isAir()
                || state.is(Blocks.BEDROCK)
                || state.is(net.minecraft.tags.BlockTags.DOORS)
                || state.is(net.minecraft.tags.BlockTags.TRAPDOORS)
                || state.liquid()
                || state.hasBlockEntity()) {
            return false;
        }

        ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (settings.protectedBlockIds().contains(blockId)) {
            return false;
        }
        Set<ResourceLocation> allowList = settings.diggableBlockIds();
        if (!allowList.isEmpty() && !allowList.contains(blockId)) {
            return false;
        }

        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0.0F
                && hardness <= settings.maxDigHardness()
                // Avoid firing the NeoForge destruction event on every AI tick;
                // the event-aware hook is reserved for the final mutation below.
                && state.canEntityDestroy(level, pos, zombie);
    }
}
