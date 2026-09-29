package com.hhy.dreamingfishcore.gameplay.organization_system.network;

import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationManager;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationResult;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 组织数据的下发入口。
 *
 * <p>组织列表本身是公共信息，所以变更后直接广播给所有在线且已认证的玩家；单人相关的
 * 权限开关由 {@link OrganizationManager#buildSnapshot} 按接收者逐个计算，不会串号。</p>
 *
 * <p>下发统一走 {@link DreamingFishCore_NetworkManager#sendToClient}，复用登录认证门禁，
 * 而不是直接调用 PacketDistributor。</p>
 */
public final class OrganizationSync {

    private OrganizationSync() {
    }

    /** 给单个玩家发一次完整快照。 */
    public static void sendSnapshot(ServerPlayer player) {
        if (player == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        DreamingFishCore_NetworkManager.sendToClient(player,
                new Packet_OrganizationSnapshotResponse(
                        OrganizationManager.buildSnapshot(player, server)));
    }

    /** 变更后刷新所有在线玩家的视图。 */
    public static void broadcast(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (AuthSessionGuard.isAuthenticated(player)) {
                sendSnapshot(player);
            }
        }
    }

    /** 把操作结果回给发起者。 */
    public static void sendResult(ServerPlayer player, OrganizationResult result) {
        if (player == null || result == null) {
            return;
        }
        DreamingFishCore_NetworkManager.sendToClient(
                player, Packet_OrganizationActionResult.of(result));
    }
}
