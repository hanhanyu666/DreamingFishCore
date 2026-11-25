package com.hhy.dreamingfishcore.gameplay.map_region_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S：客户端请求一次区域快照（进服 / 重开地图时用）。 */
public record Packet_MapRegionRequest() implements CustomPacketPayload {

    public static final Type<Packet_MapRegionRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "map_region/request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_MapRegionRequest> STREAM_CODEC =
            StreamCodec.of(Packet_MapRegionRequest::encode, Packet_MapRegionRequest::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_MapRegionRequest packet) {
    }

    private static Packet_MapRegionRequest decode(FriendlyByteBuf buffer) {
        return new Packet_MapRegionRequest();
    }

    public static void handle(Packet_MapRegionRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                com.hhy.dreamingfishcore.gameplay.map_region_system.MapRegionSync.sendTo(player);
            }
        });
    }
}
