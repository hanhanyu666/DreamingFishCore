package com.hhy.dreamingfishcore.server.rank_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.cache.ClientCacheManager;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerData;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerDataManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * 玩家Rank数据管理器（使用全局统一存储）
 */
@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerRankManager {
    public static void setPlayerRankServer(ServerPlayer serverPlayer, Rank rank) {
        PlayerData playerData = PlayerDataManager.getPlayerData(serverPlayer.getUUID());
        playerData.grantRank(rank);
        PlayerDataManager.updatePlayerData(serverPlayer, rank, playerData.getTitle(), playerData.getLevel(), playerData.getCurrentExperience());
    }

    public static boolean equipPlayerRankServer(ServerPlayer serverPlayer, Rank rank) {
        PlayerData playerData = PlayerDataManager.getPlayerData(serverPlayer.getUUID());
        Rank targetRank = rank == null ? RankRegistry.NO_RANK : rank;
        if (!playerData.ownsRank(targetRank)) {
            return false;
        }
        PlayerDataManager.updatePlayerData(serverPlayer, targetRank, playerData.getTitle(), playerData.getLevel(), playerData.getCurrentExperience());
        return true;
    }

    public static Set<String> getOwnedRankNamesServer(ServerPlayer serverPlayer) {
        return PlayerDataManager.getPlayerData(serverPlayer.getUUID()).getOwnedRankNames();
    }
    public static Rank getPlayerRankServer(ServerPlayer serverPlayer) {
        PlayerData playerData = PlayerDataManager.getPlayerData(serverPlayer.getUUID());
        return playerData.getRank();
    }

    // 客户端缓存
    public static void setPlayerRankClient(Player clientPlayer, Rank rank) {
        if (clientPlayer == null || rank == null) return;
        PlayerData data = ClientCacheManager.getOrCreatePlayerData(clientPlayer.getUUID());
        data.setRank(rank);
        ClientCacheManager.setPlayerData(clientPlayer.getUUID(), data);
    }

    public static Rank getPlayerRankClient(Player clientPlayer) {
        if (clientPlayer == null) return RankRegistry.NO_RANK;
        PlayerData data = ClientCacheManager.getPlayerData(clientPlayer.getUUID());
        return data != null ? data.getRank() : RankRegistry.NO_RANK;
    }

    public static Set<String> getOwnedRankNamesClient(Player clientPlayer) {
        if (clientPlayer == null) return Set.of();
        PlayerData data = ClientCacheManager.getPlayerData(clientPlayer.getUUID());
        return data == null ? Set.of() : data.getOwnedRankNames();
    }
}
