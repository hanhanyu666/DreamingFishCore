package com.hhy.dreamingfishcore.gameplay.zombie_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntities;
import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieTrackingManager;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieSpawnPoolRewriter;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieSpeciesConfig;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieTaskLocationRules;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runtime hooks that keep optional natural spawning stage-aware. */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class ZombieSpeciesEvents {
    /** Logs an invalid/unsupported spawn entry only once per server lifetime. */
    private static final AtomicBoolean SPAWN_POOL_WARNING_LOGGED = new AtomicBoolean();

    private ZombieSpeciesEvents() {
    }

    /**
     * Reject automatic hostile-monster placement before a mob is finalized.
     * Spawn eggs and commands are intentionally exempt so administrators can
     * still stage encounters inside a protected location.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMonsterSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (ZombieTaskLocationRules.blocksMonsterSpawn(
                event.getEntityType(),
                event.getLevel(),
                event.getPos(),
                event.getSpawnType())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    /**
     * PositionCheck is the fallback for automatic sources that bypass a normal
     * spawn-placement predicate (for example a custom spawner rule).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMonsterSpawnPosition(MobSpawnEvent.PositionCheck event) {
        BlockPos position = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (ZombieTaskLocationRules.blocksMonsterSpawn(
                event.getEntity().getType(),
                event.getLevel(),
                position,
                event.getSpawnType())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.MONSTER) {
            return;
        }

        if (!(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        ZombieSpeciesConfig.ResolvedSettings settings = ZombieSpeciesConfig.current().resolveForLevel(level);
        if (!settings.enabled() || !settings.naturalSpawn()) {
            return;
        }

        List<MobSpawnSettings.SpawnerData> original =
                List.copyOf(event.getSpawnerDataList());
        try {
            // Replace only the vanilla zombie-family entries with a split
            // vanilla/custom pair and apply the configured family/other
            // multipliers. The mob cap itself remains vanilla.
            List<MobSpawnSettings.SpawnerData> rewritten = ZombieSpawnPoolRewriter.rewrite(
                    original,
                    SiegeZombieEntities.SIEGE_ZOMBIE.get(),
                    settings.zombieFamilySpawnPercent(),
                    settings.vanillaZombieSpawnPercent(),
                    settings.customZombieSpawnPercent(),
                    settings.otherMonsterSpawnPercent());
            if (rewritten == original) {
                return;
            }

            // Remove only entries that the rewrite actually replaced. Do not
            // clear the whole event list: another handler may have appended an
            // unrelated entry after our snapshot. Identity sets are required
            // because SpawnerData intentionally uses identity equality.
            Set<MobSpawnSettings.SpawnerData> rewrittenEntries =
                    Collections.newSetFromMap(new IdentityHashMap<>());
            rewrittenEntries.addAll(rewritten);
            Set<MobSpawnSettings.SpawnerData> originalEntries =
                    Collections.newSetFromMap(new IdentityHashMap<>());
            originalEntries.addAll(original);
            Set<MobSpawnSettings.SpawnerData> removed =
                    Collections.newSetFromMap(new IdentityHashMap<>());
            for (MobSpawnSettings.SpawnerData entry : original) {
                if (!rewrittenEntries.contains(entry)) {
                    if (event.removeSpawnerData(entry)) {
                        removed.add(entry);
                    }
                }
            }
            for (MobSpawnSettings.SpawnerData entry : rewritten) {
                if (removed.contains(entry) || !originalEntries.contains(entry)) {
                    event.addSpawnerData(entry);
                }
            }
        } catch (RuntimeException exception) {
            if (SPAWN_POOL_WARNING_LOGGED.compareAndSet(false, true)) {
                DreamingFishCore.LOGGER.error(
                        "Failed to split vanilla zombie natural spawn entries; "
                                + "the original monster spawn pool was preserved",
                        exception);
            }
        }
    }

    /** Samples player movement once per server tick for hidden-target hearing. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                && AuthSessionGuard.isAuthenticated(player)) {
            SiegeZombieTrackingManager.onPlayerTick(player);
            ZombieTaskLocationRules.onPlayerTick(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SiegeZombieTrackingManager.onPlayerLoggedOut(event);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SiegeZombieTrackingManager.clear(event.getServer());
        ZombieSpawnPoolRewriter.clearCache();
        SPAWN_POOL_WARNING_LOGGED.set(false);
    }
}
