package com.hhy.dreamingfishcore.gameplay.npc_message_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端请求自己的 NPC 私信快照。 */
public record Packet_NpcMessageSnapshotRequest() implements CustomPacketPayload {
    public static final Type<Packet_NpcMessageSnapshotRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "npc_message/snapshot_request"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_NpcMessageSnapshotRequest>
            STREAM_CODEC = StreamCodec.of(Packet_NpcMessageSnapshotRequest::encode, Packet_NpcMessageSnapshotRequest::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_NpcMessageSnapshotRequest packet) {
    }

    private static Packet_NpcMessageSnapshotRequest decode(FriendlyByteBuf buffer) {
        return new Packet_NpcMessageSnapshotRequest();
    }

    public static void handle(Packet_NpcMessageSnapshotRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                NpcMessageManager.syncToClient(player);
            }
        });
    }
}
