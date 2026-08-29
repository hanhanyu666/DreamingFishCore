package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesDataManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.event.DeathEventHandler;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.DeathItemStorage;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.PendingDeathData;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.corpse.DeathCorpseManager;
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
 * 正常复活请求包
 * 客户端点击"正常复活"按钮后发送到服务端
 */
public class Packet_NormalRespawnRequest implements CustomPacketPayload {
    public static final Type<Packet_NormalRespawnRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID,
                    "playerattribute_system/death_system/packet_normal_respawn_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_NormalRespawnRequest> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> encode(packet, buf), Packet_NormalRespawnRequest::decode);

    private final UUID deathId;
    private final boolean lockCorpse;

    public Packet_NormalRespawnRequest(UUID deathId, boolean lockCorpse) {
        this.deathId = deathId;
        this.lockCorpse = lockCorpse;
    }

    public Packet_NormalRespawnRequest(UUID deathId) {
        this(deathId, true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 编码本次死亡的唯一标识。
     */
    public static void encode(Packet_NormalRespawnRequest packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.deathId);
        buf.writeBoolean(packet.lockCorpse);
    }

    /**
     * 解码
     */
    public static Packet_NormalRespawnRequest decode(FriendlyByteBuf buf) {
        return new Packet_NormalRespawnRequest(buf.readUUID(), buf.readBoolean());
    }

    /**
     * 处理（服务端）
     */
    public static void handle(Packet_NormalRespawnRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player == null) return;

            if (!PendingDeathData.beginResolution(player, packet.deathId)) {
                DreamingFishCore.LOGGER.warn("拒绝玩家 {} 的无效或重复普通复活请求，deathId={}",
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

            // 计算正常复活消耗
            float cost = DeathEventHandler.getNormalCost(isInfected);

            // 检查复活点数是否足够
            if (currentRespawnPoint < cost) {
                // 复活点不足，发送失败消息
                PendingDeathData.rollbackResolution(player, packet.deathId);
                sendResponse(player, false, currentRespawnPoint);
                return;
            }

            // 新记录只解锁死亡点尸体；旧记录继续使用升级前的物品快照掉落流程。
            boolean itemsResolved = PendingDeathData.hasCorpseReference(player)
                    ? DeathCorpseManager.finalizeForNormalRespawn(player, packet.lockCorpse)
                    : DeathItemStorage.dropStoredItems(player);
            if (!itemsResolved) {
                PendingDeathData.rollbackResolution(player, packet.deathId);
                sendResponse(player, false, currentRespawnPoint);
                return;
            }

            data.consumeRespawnPoint(cost);
            PlayerAttributesDataManager.updatePlayerAttributesData(player, data);
            PendingDeathData.complete(player, packet.deathId);

            // 发送成功消息，让客户端执行复活
            sendResponse(player, true, data.getRespawnPoint());

            DreamingFishCore.LOGGER.info("玩家 {} 正常复活，消耗 {} 复活点（剩余: {}，尸体锁定={}）",
                    player.getScoreboardName(), cost, data.getRespawnPoint(), packet.lockCorpse);
        });
    }

    /**
     * 发送响应给客户端
     */
    private static void sendResponse(ServerPlayer player, boolean success, float respawnPoint) {
        Packet_NormalRespawnResponse response = new Packet_NormalRespawnResponse(success, respawnPoint);
        DreamingFishCore_NetworkManager.sendToClient(response, player);
    }

}
