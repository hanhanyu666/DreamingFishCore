package com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.network;

import com.hhy.dreamingfishcore.client.cache.ClientCacheManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.client.cache.DeathScreenDataStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.handling.IPayloadContext;


/**
 * 正常复活响应包
 * 服务端 → 客户端
 * 通知客户端是否可以执行复活
 */
public class Packet_NormalRespawnResponse implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_NormalRespawnResponse> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "playerattribute_system/death_system/packet_normal_respawn_response"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_NormalRespawnResponse> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_NormalRespawnResponse.encode(packet, buf), Packet_NormalRespawnResponse::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    private final boolean success;
    private final float respawnPoint;

    public Packet_NormalRespawnResponse(boolean success, float respawnPoint) {
        this.success = success;
        this.respawnPoint = respawnPoint;
    }

    /**
     * 编码
     */
    public static void encode(Packet_NormalRespawnResponse packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.success);
        buf.writeFloat(packet.respawnPoint);
    }

    /**
     * 解码
     */
    public static Packet_NormalRespawnResponse decode(FriendlyByteBuf buf) {
        boolean success = buf.readBoolean();
        float respawnPoint = buf.readFloat();
        return new Packet_NormalRespawnResponse(success, respawnPoint);
    }

    /**
     * 处理（客户端）
     */
    public static void handle(Packet_NormalRespawnResponse packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            handleClient(packet);
        });
    }

    private static void handleClient(Packet_NormalRespawnResponse packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (packet.success) {
            // 成功，显示消息并执行复活
            mc.player.displayClientMessage(
                    Component.literal("§a复活成功！剩余复活点: " + String.format("%.1f", packet.respawnPoint)),
                    true
            );
            // 同步客户端复活点数
            ClientCacheManager.setRespawnPoint(mc.player.getUUID(), packet.respawnPoint);
            DeathScreenDataStorage.clearData();
            mc.player.respawn();
            mc.setScreen(null);
        } else {
            // 服务端会另外发送具体原因（余量耗尽、请求失效、结算异常）；
            // 这里只做中性提示，避免用错误的原因误导玩家。
            mc.player.displayClientMessage(
                    Component.literal("§c复活结算未完成，请查看提示后重新选择。"),
                    true
            );
        }
    }

    public boolean isSuccess() {
        return success;
    }

    public float getRespawnPoint() {
        return respawnPoint;
    }
}
