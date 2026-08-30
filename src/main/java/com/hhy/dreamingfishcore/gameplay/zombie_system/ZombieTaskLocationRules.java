package com.hhy.dreamingfishcore.gameplay.zombie_system;

import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/** Shared task-location gates for zombie block actions and the safe-zone player effect. */
public final class ZombieTaskLocationRules {
    private static final int REGENERATION_REFRESH_TICKS = 20;
    private static final int REGENERATION_DURATION_TICKS = 60;

    private ZombieTaskLocationRules() {
    }

    /**
     * Returns true when a destructive zombie action must be denied. Both the
     * actor and the affected block are checked so a wall cannot be damaged
     * from just outside a task-location boundary.
     */
    public static boolean blocksDestructiveAction(
            SiegeZombieEntity zombie,
            @Nullable BlockPos affectedPos) {
        if (zombie == null
                || zombie.level().isClientSide()
                || !zombie.isAbilityEnabled(ZombieSpeciesConfig.Ability.TASK_LOCATION_PROTECTION)) {
            return false;
        }

        Level level = zombie.level();
        boolean actorInside = isTaskLocation(level, zombie.blockPosition());
        boolean affectedInside = affectedPos != null && isTaskLocation(level, affectedPos);
        return shouldBlockDestructiveAction(true, actorInside, affectedInside);
    }

    /**
     * Returns true when an automatic hostile-monster spawn must be rejected in
     * an active task location. Explicit administrator/player creation sources
     * (commands, spawn eggs and summons) are intentionally left available for
     * scripted scenes and testing.
     */
    public static boolean blocksMonsterSpawn(
            @Nullable EntityType<?> entityType,
            @Nullable ServerLevelAccessor level,
            @Nullable BlockPos position,
            @Nullable MobSpawnType spawnType) {
        if (entityType == null
                || level == null
                || position == null
                || spawnType == null
                || entityType.getCategory() != MobCategory.MONSTER
                || !isAutomaticSpawnType(spawnType)) {
            return false;
        }

        ServerLevel serverLevel = level.getLevel();
        if (serverLevel == null || serverLevel.isClientSide()) {
            return false;
        }
        // Most spawn attempts are outside a task location. Avoid resolving a
        // story snapshot for those common misses.
        if (!isTaskLocation(serverLevel, position)) {
            return false;
        }
        ZombieSpeciesConfig.ResolvedSettings settings = ZombieSpeciesConfig.current()
                .resolveForLevel(serverLevel);
        return shouldBlockMonsterSpawn(
                settings.taskLocationSpawnProtection(),
                true,
                true);
    }

    /** Maintains Regeneration I for every living player inside an active task location. */
    public static void onPlayerTick(ServerPlayer player) {
        if (player == null
                || player.level().isClientSide()
                || !player.isAlive()
                || !AuthSessionGuard.isAuthenticated(player)
                || player.tickCount % REGENERATION_REFRESH_TICKS != 0) {
            return;
        }

        Level level = player.serverLevel();
        ZombieSpeciesConfig.ResolvedSettings settings = ZombieSpeciesConfig.current()
                .resolveForLevel(level);
        boolean inside = isTaskLocation(level, player.blockPosition());
        if (!shouldApplyRegeneration(
                settings.enabled(),
                settings.taskLocationRegeneration(),
                inside)) {
            return;
        }

        // A short renewal avoids permanently tagging players after they leave.
        // Vanilla's effect merge rules preserve a stronger/longer externally
        // supplied regeneration effect instead of downgrading it.
        player.addEffect(new MobEffectInstance(
                MobEffects.REGENERATION,
                REGENERATION_DURATION_TICKS,
                0,
                true,
                false,
                true));
    }

    static boolean shouldBlockDestructiveAction(
            boolean protectionEnabled,
            boolean actorInside,
            boolean affectedInside) {
        return protectionEnabled && (actorInside || affectedInside);
    }

    static boolean shouldApplyRegeneration(
            boolean systemEnabled,
            boolean regenerationEnabled,
            boolean playerInside) {
        return systemEnabled && regenerationEnabled && playerInside;
    }

    static boolean shouldBlockMonsterSpawn(
            boolean protectionEnabled,
            boolean monster,
            boolean insideTaskLocation) {
        return protectionEnabled && monster && insideTaskLocation;
    }

    /**
     * Spawn sources that happen automatically in the world are protected.
     * Explicit creation and lifecycle conversions are not treated as a new
     * hostile spawn, so story scripts and the spawn egg remain usable.
     */
    static boolean isAutomaticSpawnType(MobSpawnType spawnType) {
        if (spawnType == null) {
            return false;
        }
        return switch (spawnType) {
            case SPAWN_EGG, COMMAND, MOB_SUMMONED, BREEDING, CONVERSION, BUCKET, DISPENSER -> false;
            default -> true;
        };
    }

    private static boolean isTaskLocation(Level level, BlockPos position) {
        return level != null
                && position != null
                && TaskLocationManager.findLocationAt(level, position).isPresent();
    }
}
