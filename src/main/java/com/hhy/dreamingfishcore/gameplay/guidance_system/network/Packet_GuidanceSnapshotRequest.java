package com.hhy.dreamingfishcore.gameplay.guidance_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端请求自己的个人引导快照。 */
public record Packet_GuidanceSnapshotRequest() implements CustomPacketPayload {
    public static final Type<Packet_GuidanceSnapshotRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "guidance/snapshot_request"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_GuidanceSnapshotRequest>
            STREAM_CODEC = StreamCodec.of(Packet_GuidanceSnapshotRequest::encode, Packet_GuidanceSnapshotRequest::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_GuidanceSnapshotRequest packet) {
    }

    private static Packet_GuidanceSnapshotRequest decode(FriendlyByteBuf buffer) {
        return new Packet_GuidanceSnapshotRequest();
    }

    public static void handle(Packet_GuidanceSnapshotRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                GuidanceManager.syncToClient(player);
            }
        });
    }
}
