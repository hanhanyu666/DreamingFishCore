package com.hhy.dreamingfishcore.server.server_ui_system.network;

import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.common.util.Util_Player;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public class Packet_ServerPlayerListRequest implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_ServerPlayerListRequest> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "packet_server_player_list_request"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_ServerPlayerListRequest> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_ServerPlayerListRequest.encode(packet, buf), Packet_ServerPlayerListRequest::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
    public Packet_ServerPlayerListRequest() {}

    public static void encode(Packet_ServerPlayerListRequest msg, FriendlyByteBuf buf) {
        // 无需数据
    }

    public static Packet_ServerPlayerListRequest decode(FriendlyByteBuf buf) {
        return new Packet_ServerPlayerListRequest();
    }

    public static void handle(Packet_ServerPlayerListRequest msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player != null) {
                List<Map.Entry<UUID, String>> accounts = Util_Player.getOnlinePlayerNames(player.server);
                DreamingFishCore_NetworkManager.sendToClient(player, new Packet_ServerPlayerListResponse(accounts));
            }
        });
    }
}
