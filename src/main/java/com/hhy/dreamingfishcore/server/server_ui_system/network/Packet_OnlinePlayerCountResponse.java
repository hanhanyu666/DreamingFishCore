package com.hhy.dreamingfishcore.server.server_ui_system.network;

import com.hhy.dreamingfishcore.server.server_ui_system.client.ServerInformationDisplay;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;


/**
 * 低频服务器状态响应包（在线人数 + NeoForge 口径的 TPS）。
 */
public class Packet_OnlinePlayerCountResponse implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_OnlinePlayerCountResponse> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "packet_online_player_count_response"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_OnlinePlayerCountResponse> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_OnlinePlayerCountResponse.encode(packet, buf), Packet_OnlinePlayerCountResponse::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
    private final int playerCount;
    private final float tps;

    /**
     * 保留旧构造方法，方便其他调用方构造仅包含人数的本地消息。
     * 网络协议的新消息始终由双参数构造方法创建。
     */
    public Packet_OnlinePlayerCountResponse(int playerCount) {
        this(playerCount, Float.NaN);
    }

    public Packet_OnlinePlayerCountResponse(int playerCount, float tps) {
        this.playerCount = playerCount;
        this.tps = tps;
    }

    public float tps() {
        return tps;
    }

    // 编码：人数和 TPS 都是定长基础类型，避免额外对象分配。
    public static void encode(Packet_OnlinePlayerCountResponse msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.playerCount);
        buf.writeFloat(msg.tps);
    }

    // 解码：读取人数和 TPS
    public static Packet_OnlinePlayerCountResponse decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        float tps = buf.readFloat();
        return new Packet_OnlinePlayerCountResponse(count, tps);
    }

    // 客户端处理逻辑：一次更新共享的服务器状态缓存
    public static void handle(Packet_OnlinePlayerCountResponse msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerInformationDisplay.ONLINE_PLAYERS = msg.playerCount;
            ServerInformationDisplay.updateServerTps(msg.tps);
        });
    }
}
