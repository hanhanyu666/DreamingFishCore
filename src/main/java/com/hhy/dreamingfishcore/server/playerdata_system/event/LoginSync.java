package com.hhy.dreamingfishcore.server.playerdata_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.playerdata_system.network.Packet_SyncPlayerData;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.server.login_system.event.PlayerAuthenticatedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Collection;

@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class LoginSync {

    @SubscribeEvent
    public static void onPlayerAuthenticated(PlayerAuthenticatedEvent event) {
        ServerPlayer newPlayer = event.getPlayer();
        if (!AuthSessionGuard.isAuthenticated(newPlayer)) {
            return;
        }

        //给新加入玩家发所有在线玩家的数据（包括自己）
        for (ServerPlayer onlinePlayer : newPlayer.getServer().getPlayerList().getPlayers()) {
            if (!AuthSessionGuard.isAuthenticated(onlinePlayer)
                    && !onlinePlayer.getUUID().equals(newPlayer.getUUID())) {
                continue;
            }
            sendSyncPacketToPlayer(newPlayer, onlinePlayer);
        }

        //给其他所有在线玩家发新加入玩家的数据
        for (ServerPlayer onlinePlayer : newPlayer.getServer().getPlayerList().getPlayers()) {
            if (AuthSessionGuard.isAuthenticated(onlinePlayer)
                    && !onlinePlayer.getUUID().equals(newPlayer.getUUID())) {
                sendSyncPacketToPlayer(onlinePlayer, newPlayer);
            }
        }
    }


    //给单个玩家发送指定玩家的同步包
    public static void sendSyncPacketToPlayer(ServerPlayer targetReceiver, ServerPlayer dataOwner) {
        if (!AuthSessionGuard.isAuthenticated(targetReceiver)
                || !AuthSessionGuard.isAuthenticated(dataOwner)) {
            return;
        }
        Packet_SyncPlayerData syncPacket = new Packet_SyncPlayerData(dataOwner);
        DreamingFishCore_NetworkManager.sendToClient(
                targetReceiver,
                syncPacket
        );
        DreamingFishCore.LOGGER.info("已向玩家{}发送{}的同步包",
                targetReceiver.getName().getString(),
                dataOwner.getName().getString()
        );
    }

    //广播指定玩家的数据给所有在线玩家
    public static void broadcastPlayerDataToAllOnlinePlayers(ServerPlayer dataOwner) {
        if (!AuthSessionGuard.isAuthenticated(dataOwner)) {
            return;
        }
        //获取服务器内所有在线玩家
        Collection<ServerPlayer> onlinePlayers = dataOwner.getServer().getPlayerList().getPlayers();
        for (ServerPlayer onlinePlayer : onlinePlayers) {
            if (!AuthSessionGuard.isAuthenticated(onlinePlayer)
                    || onlinePlayer.getUUID().equals(dataOwner.getUUID())) {
                continue;
            }
            //给每个在线玩家发送新玩家的数据
            sendSyncPacketToPlayer(onlinePlayer, dataOwner);
        }
    }

    /**
     * 玩家基础资料发生服务端权威变更时，同时刷新本人和其他在线玩家的只读快照。
     */
    public static void broadcastPlayerDataIncludingOwner(ServerPlayer dataOwner) {
        Collection<ServerPlayer> onlinePlayers = dataOwner.getServer().getPlayerList().getPlayers();
        for (ServerPlayer onlinePlayer : onlinePlayers) {
            if (AuthSessionGuard.isAuthenticated(onlinePlayer)
                    && AuthSessionGuard.isAuthenticated(dataOwner)) {
                sendSyncPacketToPlayer(onlinePlayer, dataOwner);
            }
        }
    }
}
