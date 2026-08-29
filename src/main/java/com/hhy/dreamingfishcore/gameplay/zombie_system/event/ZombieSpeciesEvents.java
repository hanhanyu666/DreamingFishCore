package com.hhy.dreamingfishcore.gameplay.zombie_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieEntities;
import com.hhy.dreamingfishcore.gameplay.zombie_system.SiegeZombieTrackingManager;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieSpawnPoolRewriter;
import com.hhy.dreamingfishcore.gameplay.zombie_system.ZombieSpeciesConfig;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runtime hooks that keep optional natural spawning stage-aware. */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class ZombieSpeciesEvents {
    /** Logs an invalid/unsupported spawn entry only once per server lifetime. */
    private static final AtomicBoolean SPAWN_POOL_WARNING_LOGGED = new AtomicBoolean();

    private ZombieSpeciesEvents() {
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
            // Keep every vanilla/datapack entry intact.  Only append the
            // additional species entries; a failed conversion must never
            // erase the original monster pool and stop all natural spawning.
            List<MobSpawnSettings.SpawnerData> additions = ZombieSpawnPoolRewriter.additions(
                    original,
                    SiegeZombieEntities.SIEGE_ZOMBIE.get(),
                    settings.vanillaZombieSpawnPercent(),
                    settings.customZombieSpawnPercent(),
                    settings.otherMonsterSpawnPercent());
            for (MobSpawnSettings.SpawnerData entry : additions) {
                event.addSpawnerData(entry);
            }
        } catch (RuntimeException exception) {
            if (SPAWN_POOL_WARNING_LOGGED.compareAndSet(false, true)) {
                DreamingFishCore.LOGGER.error(
                        "Failed to append siege-zombie natural spawn entries; "
                                + "the original monster spawn pool was preserved",
                        exception);
            }
        }
    }

    /** Samples player movement once per server tick for hidden-target hearing. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SiegeZombieTrackingManager.onPlayerTick(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SiegeZombieTrackingManager.onPlayerLoggedOut(event);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SiegeZombieTrackingManager.clear(event.getServer());
        SPAWN_POOL_WARNING_LOGGED.set(false);
    }
}
