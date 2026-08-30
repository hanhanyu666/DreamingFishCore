package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.event.DeathEventHandler;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.DeathItemStorage;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.PendingDeathData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.DeathCorpseManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.CustomRespawnInventoryManager;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * 死亡不掉落请求包
 * 客户端点击"保留物品"按钮后发送到服务端
 */
public class Packet_KeepInventoryRequest implements CustomPacketPayload {
    public static final Type<Packet_KeepInventoryRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID,
                    "playerattribute_system/death_system/packet_keep_inventory_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_KeepInventoryRequest> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> encode(packet, buf), Packet_KeepInventoryRequest::decode);

    private final UUID deathId;

    public Packet_KeepInventoryRequest(UUID deathId) {
        this.deathId = deathId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
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
    public static void handle(Packet_KeepInventoryRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
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
            float originalRespawnPoint = currentRespawnPoint;
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

            // 先记录扣费，再尝试从尸体还原。这样尸体还原失败时可以完整回滚点数，
            // 而不会出现“物品已经取走、点数却没有结算”的半完成状态。
            try {
                data.consumeRespawnPoint(cost);
                PlayerAttributesDataManager.updatePlayerAttributesData(player, data);

                // 新记录先从尸体还原；旧记录继续使用升级前的物品快照。
                boolean itemsResolved = PendingDeathData.hasCorpseReference(player)
                        ? DeathCorpseManager.restoreForKeepInventory(player)
                        : DeathItemStorage.keepStoredItems(player);
                if (!itemsResolved) {
                    data.setRespawnPoint(originalRespawnPoint);
                    PlayerAttributesDataManager.updatePlayerAttributesData(player, data);
                    PendingDeathData.rollbackResolution(player, packet.deathId);
                    sendResponse(player, false, currentRespawnPoint);
                    return;
                }

                // 不再依赖全局 keepInventory；只为这一次已成功还原物品的复活复制物品栏。
                CustomRespawnInventoryManager.request(player);
                PendingDeathData.complete(player, packet.deathId);
            } catch (RuntimeException exception) {
                CustomRespawnInventoryManager.cancel(player);
                data.setRespawnPoint(originalRespawnPoint);
                try {
                    PlayerAttributesDataManager.updatePlayerAttributesData(player, data);
                } catch (RuntimeException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                PendingDeathData.rollbackResolution(player, packet.deathId);
                DreamingFishCore.LOGGER.error("玩家 {} 保留物品结算失败，已取消复活",
                        player.getScoreboardName(), exception);
                sendResponse(player, false, currentRespawnPoint);
                return;
            }

            // 发送成功消息
            sendResponse(player, true, data.getRespawnPoint());

            DreamingFishCore.LOGGER.info("玩家 {} 消耗 {} 复活点保留物品（剩余: {}）",
                    player.getScoreboardName(), cost, data.getRespawnPoint());
        });
    }

    /**
     * 发送响应给客户端
     */
    private static void sendResponse(ServerPlayer player, boolean success, float respawnPoint) {
        Packet_KeepInventoryResponse response = new Packet_KeepInventoryResponse(success, respawnPoint);
        DreamingFishCore_NetworkManager.sendToClient(response, player);
    }

}
