package com.hhy.dreamingfishcore.gameplay.organization_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端请求一次组织快照（打开终端「组织」页面时发送）。 */
public record Packet_OrganizationSnapshotRequest() implements CustomPacketPayload {

    public static final Type<Packet_OrganizationSnapshotRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "organization/snapshot_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_OrganizationSnapshotRequest>
            STREAM_CODEC = StreamCodec.of(
                    Packet_OrganizationSnapshotRequest::encode,
                    Packet_OrganizationSnapshotRequest::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer,
                               Packet_OrganizationSnapshotRequest packet) {
        // 无字段
    }

    private static Packet_OrganizationSnapshotRequest decode(RegistryFriendlyByteBuf buffer) {
        return new Packet_OrganizationSnapshotRequest();
    }

    public static void handle(Packet_OrganizationSnapshotRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                OrganizationSync.sendSnapshot(player);
            }
        });
    }
}
