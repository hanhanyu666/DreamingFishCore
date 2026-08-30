package com.hhy.dreamingfishcore.server.title_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.rank_system.PlayerRankManager;
import com.hhy.dreamingfishcore.server.rank_system.Rank;
import com.hhy.dreamingfishcore.server.title_system.PlayerTitleManager;
import com.hhy.dreamingfishcore.server.title_system.Title;
import com.hhy.dreamingfishcore.server.title_system.network.Packet_RichChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

/** Builds structured player chat instead of flattening metadata and message text into one long line. */
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class ChangeChatEvent {
    private ChangeChatEvent() {
    }

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        event.setCanceled(true);
        broadcastRichChat(player, event.getRawText(), "", "");
    }

    public static void broadcastRichChat(ServerPlayer player, String body, String quotedPlayerName, String quotedBody) {
        Title playerTitle = PlayerTitleManager.getPlayerTitleServer(player);
        Rank rank = PlayerRankManager.getPlayerRankServer(player);
        String titleName = playerTitle.getTitleName();
        int titleColor = playerTitle.getColor();
        String rankName = rank.getRankName();
        int rankColor = rank.getRankColor();
        Packet_RichChatMessage packet = new Packet_RichChatMessage(
                player.getUUID(),
                rankName,
                rankColor,
                titleName,
                titleColor,
                player.getScoreboardName(),
                body,
                System.currentTimeMillis(),
                quotedPlayerName == null ? "" : quotedPlayerName,
                quotedBody == null ? "" : quotedBody);

        for (ServerPlayer onlinePlayer : player.getServer().getPlayerList().getPlayers()) {
            DreamingFishCore_NetworkManager.sendToClient(packet, onlinePlayer);
        }

        DreamingFishCore.LOGGER.info(
                "聊天 - 玩家:{}, Rank:{}, 称号:{}, 内容:{}",
                player.getScoreboardName(), rankName, titleName, body);
    }
}
