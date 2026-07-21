package com.hhy.dreamingfishcore.gameplay.marker_system.network;

import com.hhy.dreamingfishcore.server.notice_system.client.tips.TipDisplayManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class Packet_MarkerRejected implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<Packet_MarkerRejected> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "marker_system/packet_marker_rejected"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_MarkerRejected> STREAM_CODEC =
            StreamCodec.of(Packet_MarkerRejected::encode, Packet_MarkerRejected::decode);

    private final String message;

    public Packet_MarkerRejected(String message) {
        this.message = message == null || message.isBlank() ? "标点失败" : message;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(FriendlyByteBuf buf, Packet_MarkerRejected packet) {
        buf.writeUtf(packet.message);
    }

    public static Packet_MarkerRejected decode(FriendlyByteBuf buf) {
        return new Packet_MarkerRejected(buf.readUtf());
    }

    public static void handle(Packet_MarkerRejected packet, IPayloadContext context) {
        context.enqueueWork(() -> TipDisplayManager.addMessage(packet.message, 1600));
    }
}
