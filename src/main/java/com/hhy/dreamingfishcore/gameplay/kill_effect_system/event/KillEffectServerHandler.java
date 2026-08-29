package com.hhy.dreamingfishcore.gameplay.kill_effect_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.kill_effect_system.KillEffectConfig;
import com.hhy.dreamingfishcore.gameplay.kill_effect_system.network.Packet_PlayKillEffect;
import com.hhy.dreamingfishcore.server.rank_system.PlayerRankManager;
import com.hhy.dreamingfishcore.server.rank_system.Rank;
import com.hhy.dreamingfishcore.server.rank_system.RankRegistry;
import com.hhy.dreamingfishcore.server.rank_system.RankTier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side trigger for the cosmetic effect shown when an eligible player kills a creature. */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public final class KillEffectServerHandler {
    private static final int DEFAULT_DURATION_TICKS = 36;

    private KillEffectServerHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof LivingEntity target)
                || target instanceof ServerPlayer || target.level().isClientSide()) {
            return;
        }

        ServerPlayer killer = resolveKiller(target, event.getSource());
        if (killer == null) {
            return;
        }

        Rank rank = PlayerRankManager.getPlayerRankServer(killer);
        Rank canonicalRank = rank == null
                ? RankRegistry.NO_RANK
                : RankRegistry.getRankByName(rank.getRankName());
        if (!canonicalRank.isAtLeast(RankTier.FISH_PLUS)
                || !KillEffectConfig.isRankLevelEligible(canonicalRank.getRankLevel())) {
            return;
        }

        Packet_PlayKillEffect packet = new Packet_PlayKillEffect(
                target.getId(),
                target.getUUID(),
                target.getX(),
                target.getY(),
                target.getZ(),
                target.getBbWidth(),
                target.getBbHeight(),
                target.getUUID().getMostSignificantBits()
                        ^ target.getUUID().getLeastSignificantBits()
                        ^ target.level().getGameTime(),
                DEFAULT_DURATION_TICKS);
        // Include the killer even when the dying entity is removed from the tracker on the same
        // tick. The tracking-and-self variant avoids a duplicate packet for nearby killers.
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, packet);
    }

    private static ServerPlayer resolveKiller(LivingEntity target, DamageSource source) {
        ServerPlayer killer = playerFrom(source.getDirectEntity());
        if (killer != null) {
            return killer;
        }

        killer = playerFrom(source.getEntity());
        if (killer != null) {
            return killer;
        }

        return playerFrom(target.getKillCredit());
    }

    /** Resolves direct melee hits, projectiles, pets, and other owned damage entities. */
    private static ServerPlayer playerFrom(Entity entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        if (entity instanceof OwnableEntity ownable
                && ownable.getOwner() instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        return null;
    }
}
