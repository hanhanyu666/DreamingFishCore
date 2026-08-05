package com.hhy.dreamingfishcore.server.notice_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.notice_system.client.NotificationClientDisplay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 服务端发送统一通知，支持左上角普通提示和中上方标题通知。 */
public final class Packet_SendNotificationToClient implements CustomPacketPayload {
    public static final Type<Packet_SendNotificationToClient> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            DreamingFishCore.MODID, "notice_system/packet_send_notification_to_client"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_SendNotificationToClient> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> encode(packet, buffer), Packet_SendNotificationToClient::decode);

    private final String title;
    private final String message;
    private final int displayDuration;
    private final byte position;

    public Packet_SendNotificationToClient(
            String title, String message, int displayDuration, byte position) {
        this.title = title;
        this.message = message;
        this.displayDuration = displayDuration;
        this.position = position;
    }

    public static Packet_SendNotificationToClient decode(FriendlyByteBuf buffer) {
        return new Packet_SendNotificationToClient(
                buffer.readUtf(), buffer.readUtf(), buffer.readInt(), buffer.readByte());
    }

    public static void encode(Packet_SendNotificationToClient packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.title);
        buffer.writeUtf(packet.message);
        buffer.writeInt(packet.displayDuration);
        buffer.writeByte(packet.position);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            Packet_SendNotificationToClient packet,
            IPayloadContext context) {
        context.enqueueWork(() -> NotificationClientDisplay.showNotification(
                packet.title, packet.message, packet.displayDuration, packet.position));
    }
}
