package com.hhy.dreamingfishcore.server.title_system;

import com.hhy.dreamingfishcore.client.cache.ClientCacheManager;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerData;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerDataManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;


public class PlayerTitleManager {
    public static void setPlayerTitleServer(ServerPlayer serverPlayer, Title title) {
        PlayerData playerData = PlayerDataManager.getPlayerData(serverPlayer.getUUID());
        PlayerDataManager.updatePlayerData(serverPlayer, playerData.getRank(), title, playerData.getLevel(), playerData.getCurrentExperience());
    }
    public static Title getPlayerTitleServer(ServerPlayer serverPlayer) {
        PlayerData playerData = PlayerDataManager.getPlayerData(serverPlayer.getUUID());
        return playerData.getTitle();
    }

    // 客户端缓存
    public static void setPlayerTitleClient(Player clientPlayer, Title title) {
        if (clientPlayer == null) return;
        PlayerData data = ClientCacheManager.getOrCreatePlayerData(clientPlayer.getUUID());
        data.setTitle(title);
        ClientCacheManager.setPlayerData(clientPlayer.getUUID(), data);
    }

    public static Title getPlayerTitleClient(Player clientPlayer) {
        if (clientPlayer == null) return TitleRegistry.getDefaultTitle();
        PlayerData data = ClientCacheManager.getPlayerData(clientPlayer.getUUID());
        return data != null ? data.getTitle() : TitleRegistry.getDefaultTitle();
    }
}
