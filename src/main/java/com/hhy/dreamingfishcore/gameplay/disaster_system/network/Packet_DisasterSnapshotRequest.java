package com.hhy.dreamingfishcore.gameplay.disaster_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S：请求当前天灾日程快照（终端 UI 的"天灾预警"页打开时发一次）。 */
public record Packet_DisasterSnapshotRequest() implements CustomPacketPayload {

    public static final Type<Packet_DisasterSnapshotRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "disaster/snapshot_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_DisasterSnapshotRequest> STREAM_CODEC =
            StreamCodec.of(Packet_DisasterSnapshotRequest::encode, Packet_DisasterSnapshotRequest::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_DisasterSnapshotRequest packet) {
    }

    private static Packet_DisasterSnapshotRequest decode(FriendlyByteBuf buffer) {
        return new Packet_DisasterSnapshotRequest();
    }

    public static void handle(Packet_DisasterSnapshotRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Packet_DisasterSnapshotResponse.send(player);
            }
        });
    }
}
