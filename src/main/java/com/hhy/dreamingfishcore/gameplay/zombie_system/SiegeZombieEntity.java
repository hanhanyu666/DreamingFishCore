package com.hhy.dreamingfishcore.gameplay.zombie_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ai.ConfigurableOpenDoorGoal;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ai.ConfigurableBreakDoorGoal;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ai.ProtectedTurtleEggGoal;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ai.ZombieAlertSurroundGoal;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ai.SiegeZombieAlertTargetGoal;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ai.SiegeZombieAttackGoal;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ai.SiegeZombiePlayerTargetGoal;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ai.ZombieSurroundingSteering;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ai.ZombieDigGoal;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ai.ZombiePlaceBlockGoal;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ai.ZombieStackGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.UUID;

/**
 * A story-aware zombie variant.  It deliberately extends vanilla {@link Zombie}
 * so the normal model, skin, sounds, combat and baby/jockey behaviour remain
 * compatible with resource packs and existing server mechanics.
 */
public class SiegeZombieEntity extends Zombie {
    /** Friend-authored appearance variants; UUID assignment is save-stable and network-free. */
    private static final int SKIN_VARIANT_COUNT = 12;
    private static final int EYE_LIGHT_VARIANT_COUNT = 6;
    private static final int SKIN_VARIANT_SALT = 0x5A17C3E1;
    private static final int EYE_LIGHT_VARIANT_SALT = 0x31E9B75D;
    private static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "siege_zombie_speed");
    private static final ResourceLocation TRACKING_RANGE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "siege_zombie_tracking_range");
    /** Vanilla Zombie's 1.21.1 default follow range; the species default is 45. */
    private static final double DEFAULT_TRACKING_RANGE = 45.0D;
    private static final String PLACEMENT_BLOCKS_TAG = "SiegeZombiePlacementBlocks";

    /** Runtime settings are refreshed after a story-stage or config reload. */
    private ZombieSpeciesConfig.ResolvedSettings runtimeSettings;
    private int settingsRefreshCooldown;
    /** -1 means this entity has not yet received its configured block supply. */
    private int placementBlocksRemaining = -1;

    /*
     * The obstruction goals must never win merely because they happen to be
     * checked before vanilla's attack goal.  Keep a small, per-entity verdict
     * cache so the stack/place/dig fallbacks share the same pathfinding
     * decision and so a transient null/partial path does not immediately stop
     * normal navigation.  The door goal intentionally follows the vanilla
     * DoorInteractGoal collision check instead.
     */
    private static final long NO_PATH_FAILURE = Long.MIN_VALUE;
    private static final int PATH_RECHECK_INTERVAL_TICKS = 5;
    private static final int PATH_FAILURE_GRACE_TICKS = 20;
    private static final int CROWD_SAMPLE_INTERVAL_TICKS = 4;
    private static final double STEERING_PROBE_DISTANCE = 0.45D;
    private static final double STEERING_LOOKAHEAD_DISTANCE = 1.4D;
    private static final double COLLISION_SEPARATION_PUSH = 0.018D;
    @Nullable
    private UUID pathGateTarget;
    @Nullable
    private BlockPos pathGateAlertAnchor;
    private long pathGateNextCheck;
    private long pathFailureSince = NO_PATH_FAILURE;
    private boolean pathGateReachable = true;
    /** Cached local repulsion; staggered sampling keeps a large horde cheap. */
    private Vec3 crowdAvoidance = Vec3.ZERO;
    /** True only while the vanilla-derived melee goal owns movement control. */
    private boolean meleeAttackGoalRunning;

    /** A target acquired from movement sound or an alert wave may ignore LOS briefly. */
    @Nullable
    private UUID alertTarget;
    /** Stable last-heard position used for hidden pathing until movement is meaningful. */
    @Nullable
    private BlockPos alertTargetAnchor;
    private long alertTargetExpires = Long.MIN_VALUE;
    /** Per-zombie frozen hidden-target anchor while it travels to or opens one breach. */
    @Nullable
    private ZombieBreachCommitmentMemory.Snapshot breachCommitment;
    private boolean suppressTrackingBroadcast;

    public SiegeZombieEntity(EntityType<? extends SiegeZombieEntity> entityType, Level level) {
        super(entityType, level);
    }

    /** Returns one of the twelve supplied base skins without synchronized entity data. */
    public int getSkinVariantIndex() {
        return stableAppearanceIndex(SKIN_VARIANT_SALT, SKIN_VARIANT_COUNT);
    }

    /** Returns one of the six supplied full-bright eye colors. */
    public int getEyeLightVariantIndex() {
        return stableAppearanceIndex(EYE_LIGHT_VARIANT_SALT, EYE_LIGHT_VARIANT_COUNT);
    }

    private int stableAppearanceIndex(int salt, int variantCount) {
        int mixed = this.getUUID().hashCode() ^ salt;
        mixed ^= mixed >>> 16;
        return Math.floorMod(mixed, variantCount);
    }

    public void setMeleeAttackGoalRunning(boolean running) {
        meleeAttackGoalRunning = running;
        if (!running) {
            crowdAvoidance = Vec3.ZERO;
        }
    }

    /**
     * Mob's constructor invokes this method before subclass fields are initialized,
     * so goals are created locally instead of stored in field initializers.
     */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Zombie.registerGoals() installs a vanilla turtle-egg RemoveBlockGoal.
        // Replace it so task-location protection covers every inherited block
        // destruction path, while preserving the ordinary behavior elsewhere.
        for (WrappedGoal wrapped : new ArrayList<>(this.goalSelector.getAvailableGoals())) {
            if (wrapped.getGoal() instanceof RemoveBlockGoal) {
                this.goalSelector.removeGoal(wrapped.getGoal());
            }
        }
        this.goalSelector.addGoal(4, new ProtectedTurtleEggGoal(this));
        // Zombie.addBehaviourGoals() installs a player target goal with a
        // cached range. Replace that one priority-2 goal instead of adding a
        // second scan, keeping the vanilla ten-tick cadence and avoiding a
        // needless duplicate query in a large horde.
        for (WrappedGoal wrapped : new ArrayList<>(this.targetSelector.getAvailableGoals())) {
            if (wrapped.getPriority() == 2
                    && wrapped.getGoal() instanceof NearestAttackableTargetGoal<?>) {
                this.targetSelector.removeGoal(wrapped.getGoal());
            }
        }
        // The alert goal is lower priority than the visible-player goal, so a
        // newly visible player always wins over a sound/broadcast marker.
        this.targetSelector.addGoal(2, new SiegeZombiePlayerTargetGoal(this));
        this.targetSelector.addGoal(3, new SiegeZombieAlertTargetGoal(this));
        // Replace the vanilla attack goal with the alert-aware equivalent. It
        // keeps priority 2 and the same speed/attack checks, but can replan a
        // hidden sound target instead of waiting for LOS to return.
        for (WrappedGoal wrapped : new ArrayList<>(this.goalSelector.getAvailableGoals())) {
            if (wrapped.getPriority() == 2
                    && wrapped.getGoal() instanceof ZombieAttackGoal) {
                this.goalSelector.removeGoal(wrapped.getGoal());
            }
        }
        this.goalSelector.addGoal(2, new SiegeZombieAttackGoal(this));
        // Opening a real wooden door is the one priority-1 exception, matching
        // vanilla's DoorInteractGoal and allowing it to interrupt an attack
        // that is physically colliding with the door.  The other fallback
        // goals use the same priority as vanilla ZombieAttackGoal and are
        // inserted after it.  GoalSelector therefore cannot start them while
        // the vanilla attack goal is running, and it cannot immediately
        // restart the attack over a running fallback goal.  Their shared path
        // gate is still required after the attack goal has stopped, so a
        // transient path refresh never turns into destructive AI.
        this.goalSelector.addGoal(1, new ConfigurableOpenDoorGoal(this));
        this.goalSelector.addGoal(1, new ConfigurableBreakDoorGoal(this));
        // Insertion order at priority 2 is intentional: carrying the existing
        // surround slot around a hidden target is non-destructive, then an
        // elevated-player stack, then a step/bridge, then mining.
        this.goalSelector.addGoal(2, new ZombieAlertSurroundGoal(this));
        this.goalSelector.addGoal(2, new ZombieStackGoal(this));
        this.goalSelector.addGoal(2, new ZombiePlaceBlockGoal(this));
        this.goalSelector.addGoal(2, new ZombieDigGoal(this));
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Replace the inherited 35-block follow range with the species default.
        // Runtime story-stage overrides are applied as a transient modifier.
        return Zombie.createAttributes().add(Attributes.FOLLOW_RANGE, DEFAULT_TRACKING_RANGE);
    }

    /** The inherited BreakDoorGoal is replaced by ConfigurableBreakDoorGoal below. */
    @Override
    protected boolean supportsBreakDoorGoal() {
        return false;
    }

    /** Keep village navigation informed while the custom goal applies its door-position gate. */
    @Override
    public boolean canBreakDoors() {
        return isAbilityEnabled(ZombieSpeciesConfig.Ability.BREAKING_DOORS)
                && !ZombieTaskLocationRules.blocksDestructiveAction(this, null);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity previous = this.getTarget();
        // Vanilla's MeleeAttackGoal clears its target when a path ends. Keep
        // a still-valid sound/broadcast target through that hand-off so the
        // fallback obstruction goals (and the alert goal itself) can continue
        // the short-lived pursuit instead of losing the target one tick early.
        LivingEntity preservedAlertTarget = null;
        if (!suppressTrackingBroadcast
                && target == null
                && alertTarget != null
                && previous instanceof Player player
                && player.isAlive()
                && player.getUUID().equals(alertTarget)
                && this.level().getGameTime() <= alertTargetExpires
                && this.distanceToSqr(player) <= getAlertPursuitRange() * getAlertPursuitRange()) {
            preservedAlertTarget = previous;
        }
        super.setTarget(target);
        if (preservedAlertTarget != null && this.getTarget() == null) {
            super.setTarget(preservedAlertTarget);
        }
        LivingEntity applied = this.getTarget();

        if (!suppressTrackingBroadcast && applied != previous) {
            clearAlertTarget();
        }

        if (!suppressTrackingBroadcast
                && !this.level().isClientSide
                && applied instanceof Player player
                && applied != previous
                && player.isAlive()) {
            SiegeZombieTrackingManager.onDirectTargetAcquired(this, player);
        }
    }

    /**
     * Installs a target supplied by the sound/broadcast subsystem without
     * recursively treating that target as a fresh direct sighting.
     */
    public void acceptTrackingTarget(Player player, int memoryTicks) {
        if (player == null || !player.isAlive() || !canAcceptTrackingTarget(player)) {
            return;
        }
        UUID playerId = player.getUUID();
        BlockPos heardPosition = player.blockPosition();
        if (!playerId.equals(alertTarget)
                || ZombieAlertPositionMemory.shouldRelocate(
                        alertTargetAnchor,
                        heardPosition,
                        runtimeSettings().alertRetargetDistance())) {
            alertTargetAnchor = heardPosition.immutable();
        }
        boolean sameAlertTarget = playerId.equals(alertTarget);
        alertTarget = playerId;
        long refreshedExpiry = this.level().getGameTime() + Math.max(20, memoryTicks);
        // A new footstep refresh must not shorten a longer extension granted
        // while this zombie is already travelling to or mining a breach.
        alertTargetExpires = sameAlertTarget
                ? Math.max(alertTargetExpires, refreshedExpiry)
                : refreshedExpiry;
        suppressTrackingBroadcast = true;
        try {
            this.setTarget(player);
        } finally {
            suppressTrackingBroadcast = false;
        }
    }

    /** Returns whether this zombie can take an alert without replacing another player target. */
    public boolean canAcceptTrackingTarget(Player player) {
        LivingEntity current = this.getTarget();
        return current == null
                || !current.isAlive()
                || current == player
                || !(current instanceof Player);
    }

    /** Live configured range used by the dynamic visible-player target goal. */
    public double getConfiguredTrackingRange() {
        return Math.max(1.0D, runtimeSettings().trackingRange());
    }

    /**
     * Maximum distance at which a relayed alert is allowed to keep walking.
     * The value is deliberately finite: a broadcast is an expanding wave,
     * not a permanent global aggro flag.
     */
    public double getAlertPursuitRange() {
        ZombieSpeciesConfig.ResolvedSettings settings = runtimeSettings();
        double direct = settings.trackingRange();
        double relay = settings.broadcasting()
                ? settings.broadcastRange() * Math.max(0, settings.broadcastMaxHops())
                : 0.0D;
        double hearing = settings.hearing() ? settings.hearingRange() : 0.0D;
        return Math.min(256.0D, Math.max(direct, Math.max(hearing, direct + relay)));
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (!super.canAttack(target)) {
            return false;
        }
        // Ordinary vanilla selectors should respect a lowered story-stage
        // range too. A sound/broadcast marker is the explicit exception: it
        // may pull a recipient toward a player who started outside direct
        // sight range.
        if (target instanceof Player && !hasActiveAlertTarget()) {
            double range = getConfiguredTrackingRange();
            return this.distanceToSqr(target) <= range * range;
        }
        return true;
    }

    /** Returns whether an alert marker is present, even if its timeout has elapsed. */
    public boolean hasAlertMarker() {
        return alertTarget != null;
    }

    /** Used by the alert target goal to distinguish a hidden, externally supplied target. */
    public boolean hasActiveAlertTarget() {
        if (alertTarget == null || this.level().getGameTime() > alertTargetExpires) {
            if (alertTarget != null) {
                clearAlertTarget();
            }
            return false;
        }
        LivingEntity target = this.getTarget();
        if (!(target instanceof Player player)
                || !player.isAlive()
                || !player.getUUID().equals(alertTarget)
                || !runtimeSettings().enabled()) {
            return false;
        }
        // Once ordinary sensing can see the player, the vanilla visible-target
        // goal should own the engagement again instead of retaining a hidden
        // alert marker until its full timeout.
        double directRange = getConfiguredTrackingRange();
        if (this.getSensing().hasLineOfSight(player)) {
            // Seeing the live player is stronger information than any frozen
            // sound anchor. Even outside direct acquisition range, do not let
            // a later loss of sight revive an obsolete breach commitment.
            clearBreachCommitment();
            if (this.distanceToSqr(player) <= directRange * directRange) {
                clearAlertTarget();
                return false;
            }
        }
        return true;
    }

    /**
     * Sight, hearing and relay are acquisition sources for the same pursuit
     * target. Once acquired, every downstream movement/attack system asks
     * this method instead of treating a heard player as a weaker target.
     */
    public boolean hasPursuitLock(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        return this.getSensing().hasLineOfSight(target)
                || (target instanceof Player && hasActiveAlertTarget());
    }

    /**
     * Effective hidden pursuit position for this zombie. The latest
     * meaningful sound keeps updating in {@link #alertTargetAnchor}; once an
     * exterior route or dig is selected, a temporary per-zombie commitment
     * freezes the position used by navigation until that breach is resolved.
     */
    @Nullable
    public BlockPos getActiveAlertAnchor() {
        if (!hasActiveAlertTarget() || alertTargetAnchor == null || alertTarget == null) {
            return null;
        }
        if (!ZombieBreachCommitmentMemory.isActive(
                breachCommitment,
                alertTarget,
                this.level().getGameTime())) {
            breachCommitment = null;
        }
        return ZombieBreachCommitmentMemory.effectiveAnchor(
                alertTargetAnchor,
                breachCommitment,
                alertTarget,
                this.level().getGameTime());
    }

    /**
     * Starts or extends a breach decision for the current hidden player.
     * Extensions retain the original selected anchor even if newer footsteps
     * have already moved the raw intelligence anchor elsewhere in a building.
     */
    public void beginOrExtendBreachCommitment(LivingEntity target, int minimumTicks) {
        if (!(target instanceof Player)
                || alertTarget == null
                || alertTargetAnchor == null
                || !target.getUUID().equals(alertTarget)
                || !hasActiveAlertTarget()) {
            return;
        }
        breachCommitment = ZombieBreachCommitmentMemory.beginOrExtend(
                breachCommitment,
                target.getUUID(),
                alertTargetAnchor,
                this.level().getGameTime(),
                minimumTicks);
        extendActiveAlertMemory(minimumTicks + 40);
    }

    private boolean hasActiveBreachCommitment(LivingEntity target) {
        return target instanceof Player
                && breachCommitment != null
                && !this.getSensing().hasLineOfSight(target)
                && ZombieBreachCommitmentMemory.isActive(
                        breachCommitment,
                        target.getUUID(),
                        this.level().getGameTime());
    }

    private void clearBreachCommitment() {
        breachCommitment = null;
    }

    /**
     * Visible targets expose their live position. A hidden heard/broadcast
     * target exposes the stabilized sound anchor while retaining the same
     * LivingEntity attack target and alert lifetime.
     */
    public Vec3 getPursuitPosition(LivingEntity target) {
        if (target instanceof Player
                && !this.getSensing().hasLineOfSight(target)) {
            BlockPos anchor = getActiveAlertAnchor();
            if (anchor != null) {
                return Vec3.atBottomCenterOf(anchor);
            }
        }
        return target.position();
    }

    /** Clears only the marker; the normal GoalSelector decides when to clear the Mob target. */
    public void clearAlertTarget() {
        alertTarget = null;
        alertTargetAnchor = null;
        alertTargetExpires = Long.MIN_VALUE;
        clearBreachCommitment();
    }

    /**
     * Extends an already active sound/broadcast marker while the zombie is
     * physically breaching an obstruction. This never creates a new marker,
     * so a stationary player who was never heard remains undiscovered.
     */
    public void extendActiveAlertMemory(int minimumTicks) {
        if (alertTarget == null || minimumTicks <= 0) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target != null
                && target.isAlive()
                && target.getUUID().equals(alertTarget)) {
            alertTargetExpires = Math.max(
                    alertTargetExpires,
                    this.level().getGameTime() + Math.max(20, minimumTicks));
        }
    }

    public static boolean checkSpawnRules(
            EntityType<SiegeZombieEntity> type,
            ServerLevelAccessor level,
            MobSpawnType spawnType,
            BlockPos pos,
            RandomSource random) {
        if (ZombieTaskLocationRules.blocksMonsterSpawn(type, level, pos, spawnType)) {
            return false;
        }
        return Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    /**
     * Runs after vanilla navigation has selected its next path node and before
     * MoveControl consumes it. Only the final movement direction is blended;
     * the underlying path and its obstruction verdict stay untouched.
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        applySurroundingSteering();
    }

    private void applySurroundingSteering() {
        ZombieSpeciesConfig.ResolvedSettings settings = runtimeSettings();
        LivingEntity target = this.getTarget();
        if (!settings.enabled()
                || !settings.surrounding()
                || !meleeAttackGoalRunning
                || !(target instanceof Player player)
                || !player.isAlive()
                || player.isCreative()
                || player.isSpectator()
                || this.isPassenger()
                || this.isVehicle()
                || !this.onGround()
                || Math.abs(player.getY() - this.getY()) > 2.5D
                || !this.hasPursuitLock(player)) {
            crowdAvoidance = Vec3.ZERO;
            return;
        }

        Vec3 pursuitPosition = this.getPursuitPosition(player);
        double deltaX = pursuitPosition.x - this.getX();
        double deltaZ = pursuitPosition.z - this.getZ();
        double horizontalDistanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        double activationRange = settings.surroundActivationRange();
        if (horizontalDistanceSquared > activationRange * activationRange
                || this.isWithinMeleeAttackRange(player)) {
            crowdAvoidance = Vec3.ZERO;
            return;
        }

        if (Math.floorMod(this.tickCount + this.getId(), CROWD_SAMPLE_INTERVAL_TICKS) == 0) {
            crowdAvoidance = sampleCrowdAvoidance(player, settings.surroundRadius());
        }

        MoveControl moveControl = this.getMoveControl();
        Vec3 pathWaypoint = moveControl.hasWanted()
                ? new Vec3(moveControl.getWantedX(), this.getY(), moveControl.getWantedZ())
                : new Vec3(pursuitPosition.x, this.getY(), pursuitPosition.z);
        Vec3 direction = ZombieSurroundingSteering.calculateDirection(
                this.getUUID(),
                this.position(),
                pathWaypoint,
                pursuitPosition,
                crowdAvoidance,
                settings.surroundRadius(),
                activationRange,
                settings.surroundSteeringStrength());
        if (direction.lengthSqr() < 1.0E-8D) {
            return;
        }

        // If the lateral blend would step into a wall, leave MoveControl's
        // original path waypoint alone for this tick. Entity collisions are
        // deliberately ignored here because the crowd vector is meant to
        // separate those entities rather than freeze in front of them.
        AABB probe = this.getBoundingBox().move(
                direction.x * STEERING_PROBE_DISTANCE,
                0.0D,
                direction.z * STEERING_PROBE_DISTANCE);
        if (!this.level().noBlockCollision(this, probe)) {
            return;
        }

        double speed = moveControl.hasWanted() ? moveControl.getSpeedModifier() : 1.0D;
        moveControl.setWantedPosition(
                this.getX() + direction.x * STEERING_LOOKAHEAD_DISTANCE,
                moveControl.hasWanted() ? moveControl.getWantedY() : this.getY(),
                this.getZ() + direction.z * STEERING_LOOKAHEAD_DISTANCE,
                Math.max(0.1D, speed));
    }

    private Vec3 sampleCrowdAvoidance(Player target, double preferredRadius) {
        double sampleRadius = Math.max(1.5D, Math.min(2.75D, preferredRadius));
        AABB searchBox = this.getBoundingBox().inflate(sampleRadius, 0.75D, sampleRadius);
        Vec3 result = Vec3.ZERO;
        for (Zombie nearby : this.level().getEntitiesOfClass(
                Zombie.class,
                searchBox,
                other -> other != this
                        && !other.isPassenger()
                        && !other.isVehicle()
                        && other.getTarget() == target)) {
            double awayX = this.getX() - nearby.getX();
            double awayZ = this.getZ() - nearby.getZ();
            double distanceSquared = awayX * awayX + awayZ * awayZ;
            if (distanceSquared < 1.0E-6D) {
                double angle = (this.getId() * 31L + nearby.getId() * 17L) * 0.61803398875D;
                awayX = Math.cos(angle);
                awayZ = Math.sin(angle);
                distanceSquared = 1.0D;
            }
            double distance = Math.sqrt(distanceSquared);
            if (distance >= sampleRadius) {
                continue;
            }
            double weight = (sampleRadius - distance) / sampleRadius;
            result = result.add(awayX / distance * weight, 0.0D, awayZ / distance * weight);
        }
        return result;
    }

    /** Adds a small final shove when two attackers physically overlap. */
    @Override
    public void push(Entity other) {
        super.push(other);
        if (this.level().isClientSide
                || !(other instanceof Zombie otherZombie)
                || this.isPassenger()
                || this.isVehicle()
                || otherZombie.isPassenger()
                || otherZombie.isVehicle()) {
            return;
        }
        ZombieSpeciesConfig.ResolvedSettings settings = runtimeSettings();
        LivingEntity target = this.getTarget();
        if (!settings.enabled()
                || !settings.surrounding()
                || !meleeAttackGoalRunning
                || !(target instanceof Player)
                || otherZombie.getTarget() != target
                || this.distanceToSqr(target) > settings.surroundActivationRange()
                * settings.surroundActivationRange()) {
            return;
        }

        double awayX = this.getX() - otherZombie.getX();
        double awayZ = this.getZ() - otherZombie.getZ();
        double distanceSquared = awayX * awayX + awayZ * awayZ;
        if (distanceSquared < 1.0E-6D) {
            double angle = (this.getId() * 31L + otherZombie.getId() * 17L) * 0.61803398875D;
            awayX = Math.cos(angle);
            awayZ = Math.sin(angle);
            distanceSquared = 1.0D;
        }
        double inverseDistance = 1.0D / Math.sqrt(distanceSquared);
        this.push(
                awayX * inverseDistance * COLLISION_SEPARATION_PUSH,
                0.0D,
                awayZ * inverseDistance * COLLISION_SEPARATION_PUSH);
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide) {
            if (settingsRefreshCooldown-- <= 0) {
                settingsRefreshCooldown = 10;
                refreshRuntimeSettings();
            }
            ensurePlacementSupply();
            // Only prime the shared verdict after navigation has actually
            // stopped. Rebuilding a path for every active attack would
            // duplicate vanilla's pathfinding work across a large horde; the
            // fallback goals themselves apply the short grace window before
            // any destructive action.
            LivingEntity target = this.getTarget();
            if (target != null && this.getNavigation().isDone()) {
                isVanillaPathBlocked(target);
            }
        }
        super.aiStep();
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
        @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        refreshRuntimeSettings();
        ensurePlacementSupply();
        return result;
    }

    /** Returns the immutable stage-resolved settings currently used by this entity. */
    public ZombieSpeciesConfig.ResolvedSettings runtimeSettings() {
        if (runtimeSettings == null) {
            runtimeSettings = ZombieSpeciesConfig.current().resolveForLevel(this.level());
        }
        return runtimeSettings;
    }

    public boolean isAbilityEnabled(ZombieSpeciesConfig.Ability ability) {
        ZombieSpeciesConfig.ResolvedSettings settings = runtimeSettings();
        return settings.enabled() && settings.isAbilityEnabled(ability);
    }

    public int getPlacementBlocksRemaining() {
        return Math.max(0, placementBlocksRemaining);
    }

    /**
     * Returns whether a target is clearly standing above this zombie.  A
     * three-quarter-block margin accepts a normal one-block platform while
     * rejecting the tiny Y jitter that occurs when two mobs stand on the same
     * floor.
     */
    public boolean isTargetAbove(LivingEntity target) {
        return target != null && target.getY() > this.getY() + 0.75D;
    }

    public void consumePlacementBlock() {
        if (placementBlocksRemaining > 0) {
            placementBlocksRemaining--;
        }
    }

    /**
     * Returns {@code true} only after vanilla-style pathfinding has failed for
     * a short, continuous window.  A path that can reach the target (including
     * a route around a pillar) always keeps the custom obstruction goals out
     * of the way.  The grace period lets the normal zombie attack goal make
     * its first path attempt before an obstruction action can interrupt it.
     */
    public boolean isVanillaPathBlocked(@Nullable LivingEntity target) {
        if (this.level().isClientSide || target == null || !target.isAlive()) {
            resetPathGate();
            return false;
        }

        Vec3 pursuitPosition = getPursuitPosition(target);
        BlockPos pursuitPos = BlockPos.containing(pursuitPosition);

        // Ground navigation only sees loaded chunks.  A null path at the
        // edge of a ticketed area is not proof that a block should be broken;
        // wait for vanilla's normal chunk tickets/path refresh instead.
        if (!this.level().hasChunkAt(pursuitPos)) {
            resetPathGate();
            return false;
        }

        // GroundPathNavigation deliberately declines to build a path while a
        // mob is in mid-air.  Do not mistake that expected transient result
        // for a solid obstruction and start breaking blocks during a jump.
        if (!this.onGround() && !this.isInLiquid() && !this.isPassenger()) {
            resetPathGate();
            return false;
        }

        // Once the vanilla melee hitbox can reach a visible target there is no
        // obstruction to solve, even if a stale navigation path is present.
        // Keep checking a close target through a wall: the melee goal still
        // cannot attack while LOS is blocked, so mining/stacking may be the
        // legitimate last resort.
        if (this.isWithinMeleeAttackRange(target) && this.getSensing().hasLineOfSight(target)) {
            resetPathGate();
            return false;
        }

        long gameTime = this.level().getGameTime();
        UUID targetId = target.getUUID();
        BlockPos activeAlertAnchor = target instanceof Player
                && !this.getSensing().hasLineOfSight(target)
                ? getActiveAlertAnchor()
                : null;
        boolean alertAnchorChanged = activeAlertAnchor == null
                ? pathGateAlertAnchor != null
                : !activeAlertAnchor.equals(pathGateAlertAnchor);
        if (!targetId.equals(pathGateTarget) || alertAnchorChanged) {
            pathGateTarget = targetId;
            pathGateAlertAnchor = activeAlertAnchor == null
                    ? null
                    : activeAlertAnchor.immutable();
            pathGateNextCheck = Long.MIN_VALUE;
            pathFailureSince = NO_PATH_FAILURE;
            pathGateReachable = true;
        }

        if (gameTime >= pathGateNextCheck) {
            boolean reachable;
            boolean routeCheckCompleted = false;
            try {
                Path path = this.getNavigation().getPath();
                // Reuse the path vanilla is already following whenever it is
                // still aimed at this target.  Calling createPath on every
                // siege zombie would duplicate the vanilla path workload and
                // can itself make a large horde feel less responsive.
                BlockPos targetPos = pursuitPos;
                if (path == null
                        || path.isDone()
                        || path.getTarget() == null
                        || path.getTarget().distManhattan(targetPos) > 1) {
                    path = this.getNavigation().createPath(targetPos, 0);
                }
                reachable = path != null && pathCanReachAttack(path, target);
                routeCheckCompleted = true;
                // Do not use PathNavigation#isStuck as a second obstruction
                // signal here.  Vanilla sets that bit only after its own long
                // crowd/timeout handling; overriding a valid path because of
                // it would reintroduce the very false positives this gate is
                // meant to prevent (for example, a briefly crowded pillar).
            } catch (RuntimeException ignored) {
                // Failing closed is safer than allowing destructive AI during
                // a transient unloaded/changing navigation region.
                reachable = true;
            }

            if (reachable) {
                // A normal route to the committed interior anchor now exists:
                // the chosen breach has opened (or another entrance became
                // usable), so hand control back to the newest sound position.
                if (routeCheckCompleted && hasActiveBreachCommitment(target)) {
                    clearBreachCommitment();
                    resetPathGate();
                    return false;
                }
                pathGateReachable = true;
                pathGateNextCheck = gameTime + PATH_RECHECK_INTERVAL_TICKS;
                pathFailureSince = NO_PATH_FAILURE;
            } else {
                pathGateReachable = false;
                pathGateNextCheck = gameTime + PATH_RECHECK_INTERVAL_TICKS;
                if (pathFailureSince == NO_PATH_FAILURE) {
                    pathFailureSince = gameTime;
                }
            }
        }

        return !pathGateReachable
                && pathFailureSince != NO_PATH_FAILURE
                && gameTime - pathFailureSince >= PATH_FAILURE_GRACE_TICKS;
    }

    /**
     * Vanilla's attack goal is allowed to attack from a nearby partial path
     * endpoint.  Treat that case as reachable too; requiring an exact target
     * node would make a low ledge look like a siege obstruction even though a
     * normal zombie can already hit the player from the endpoint.
     */
    private boolean pathCanReachAttack(Path path, LivingEntity target) {
        if (path.canReach()) {
            return true;
        }
        if (path.getNodeCount() == 0) {
            return false;
        }

        Node end = path.getEndNode();
        if (end == null) {
            return false;
        }
        Vec3 endpoint = path.getEntityPosAtNode(this, path.getNodeCount() - 1);
        AABB endpointBox = this.getBoundingBox().move(
                endpoint.x - this.getX(),
                endpoint.y - this.getY(),
                endpoint.z - this.getZ());
        if (!endpointBox.inflate(0.3D, 0.0D, 0.3D).intersects(target.getHitbox())) {
            return false;
        }

        Vec3 endpointEyes = endpoint.add(0.0D, this.getEyeHeight(), 0.0D);
        return this.level().clip(new ClipContext(
                endpointEyes,
                target.getEyePosition(),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this)).getType() == HitResult.Type.MISS;
    }

    private void resetPathGate() {
        pathGateTarget = null;
        pathGateAlertAnchor = null;
        pathGateNextCheck = 0L;
        pathFailureSince = NO_PATH_FAILURE;
        pathGateReachable = true;
    }

    /** Resolves the configured block and falls back safely if a datapack removed it. */
    public net.minecraft.world.level.block.Block getConfiguredPlacementBlock(
            ZombieSpeciesConfig.ResolvedSettings settings) {
        try {
            ResourceLocation id = settings.placementBlockId();
            return BuiltInRegistries.BLOCK.getOptional(id)
                    .orElse(net.minecraft.world.level.block.Blocks.COBBLESTONE);
        } catch (RuntimeException exception) {
            return net.minecraft.world.level.block.Blocks.COBBLESTONE;
        }
    }

    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        // Reuse vanilla zombie drops (rotten flesh, rare iron/carrot/potato, etc.).
        return ResourceKey.create(
                Registries.LOOT_TABLE,
                ResourceLocation.withDefaultNamespace("entities/zombie"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        // Preserve -1 as the lazy-initialisation sentinel.  A freshly summoned
        // entity can be saved before its first AI tick and must still receive
        // the configured starting supply after a restart.
        compound.putInt(PLACEMENT_BLOCKS_TAG, placementBlocksRemaining);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains(PLACEMENT_BLOCKS_TAG, 99)) {
            placementBlocksRemaining = Math.max(0, compound.getInt(PLACEMENT_BLOCKS_TAG));
        } else {
            placementBlocksRemaining = -1;
        }
    }

    private void ensurePlacementSupply() {
        if (placementBlocksRemaining < 0) {
            placementBlocksRemaining = Math.max(0, runtimeSettings().placementBlocks());
        }
    }

    private void refreshRuntimeSettings() {
        if (this.level().isClientSide) {
            return;
        }

        ZombieSpeciesConfig.ResolvedSettings previous = runtimeSettings;
        ZombieSpeciesConfig.ResolvedSettings next;
        try {
            next = ZombieSpeciesConfig.current().resolveForLevel(this.level());
        } catch (RuntimeException exception) {
            // A malformed stage ID should not crash an active server tick.
            next = ZombieSpeciesConfig.current().resolveForStage(null);
            DreamingFishCore.LOGGER.warn("解析 siege_zombie 故事阶段配置失败，使用默认能力", exception);
        }
        runtimeSettings = next;

        if (previous == null || previous.enabled() != next.enabled()
                || previous.speedMultiplier() != next.speedMultiplier()) {
            applySpeedModifier(next);
        }
        if (previous == null || previous.trackingRange() != next.trackingRange()) {
            applyTrackingRangeModifier(next);
        }

        if (this.getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanOpenDoors(next.enabled() && (next.openDoors() || next.breakingDoors()));
        }
    }

    private void applySpeedModifier(ZombieSpeciesConfig.ResolvedSettings settings) {
        AttributeInstance movement = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) {
            return;
        }
        movement.removeModifier(SPEED_MODIFIER_ID);
        if (settings.enabled() && Math.abs(settings.speedMultiplier() - 1.0D) > 1.0E-6D) {
            movement.addTransientModifier(new AttributeModifier(
                    SPEED_MODIFIER_ID,
                    settings.speedMultiplier() - 1.0D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private void applyTrackingRangeModifier(ZombieSpeciesConfig.ResolvedSettings settings) {
        AttributeInstance followRange = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange == null) {
            return;
        }
        followRange.removeModifier(TRACKING_RANGE_MODIFIER_ID);
        double delta = settings.trackingRange() - DEFAULT_TRACKING_RANGE;
        if (Math.abs(delta) > 1.0E-6D) {
            followRange.addTransientModifier(new AttributeModifier(
                    TRACKING_RANGE_MODIFIER_ID,
                    delta,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
