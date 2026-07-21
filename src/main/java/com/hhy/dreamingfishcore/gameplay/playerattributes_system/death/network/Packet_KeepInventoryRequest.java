package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.event.DeathEventHandler;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.DeathItemStorage;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.PendingDeathData;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 死亡不掉落请求包
 * 客户端点击"保留物品"按钮后发送到服务端
 */
public class Packet_KeepInventoryRequest {
    private final UUID deathId;

    public Packet_KeepInventoryRequest(UUID deathId) {
        this.deathId = deathId;
    }

    /**
     * 编码本次死亡的唯一标识。
     */
    public static void encode(Packet_KeepInventoryRequest packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.deathId);
    }

    /**
     * 解码
     */
    public static Packet_KeepInventoryRequest decode(FriendlyByteBuf buf) {
        return new Packet_KeepInventoryRequest(buf.readUUID());
    }

    /**
     * 处理（服务端）
     */
    public static void handle(Packet_KeepInventoryRequest packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            if (!PendingDeathData.beginResolution(player, packet.deathId)) {
                DreamingFishCore.LOGGER.warn("拒绝玩家 {} 的无效或重复保留物品请求，deathId={}",
                        player.getScoreboardName(), packet.deathId);
                if (DeathEventHandler.hasDeathState(player)) {
                    DeathEventHandler.restoreDeathState(player);
                }
                return;
            }

            PlayerAttributesData data = PlayerAttributesDataManager.getPlayerAttributesData(player.getUUID());
            if (data == null) {
                PendingDeathData.rollbackResolution(player, packet.deathId);
                sendResponse(player, false, 0.0F);
                return;
            }

            float currentRespawnPoint = data.getRespawnPoint();
            boolean isInfected = data.isInfected();

            // 计算保留物品消耗（基础消耗 + 30）
            float cost = DeathEventHandler.getKeepInventoryCost(isInfected);

            // 检查复活点数是否足够
            if (currentRespawnPoint < cost) {
                // 复活点不足，发送失败消息
                PendingDeathData.rollbackResolution(player, packet.deathId);
                sendResponse(player, false, currentRespawnPoint);
                return;
            }

            // keepInventory 已开启，确认快照有效即可保留玩家身上的实际物品。
            if (!DeathItemStorage.keepStoredItems(player)) {
                PendingDeathData.rollbackResolution(player, packet.deathId);
                sendResponse(player, false, currentRespawnPoint);
                return;
            }

            data.consumeRespawnPoint(cost);
            PlayerAttributesDataManager.updatePlayerAttributesData(player, data);
            PendingDeathData.complete(player, packet.deathId);

            // 发送成功消息
            sendResponse(player, true, data.getRespawnPoint());

            DreamingFishCore.LOGGER.info("玩家 {} 消耗 {} 复活点保留物品（剩余: {}）",
                    player.getScoreboardName(), cost, data.getRespawnPoint());
        });
        context.setPacketHandled(true);
    }

    /**
     * 发送响应给客户端
     */
    private static void sendResponse(ServerPlayer player, boolean success, float respawnPoint) {
        Packet_KeepInventoryResponse response = new Packet_KeepInventoryResponse(success, respawnPoint);
        DreamingFishCore_NetworkManager.sendToClient(response, player);
    }

}
