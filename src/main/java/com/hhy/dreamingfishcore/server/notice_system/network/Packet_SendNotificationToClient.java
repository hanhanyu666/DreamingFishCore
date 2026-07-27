package com.hhy.dreamingfishcore.server.notice_system.network;

import com.hhy.dreamingfishcore.server.notice_system.client.NotificationClientDisplay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端发送统一通知，支持左上角普通提示和中上方标题通知。 */
public final class Packet_SendNotificationToClient {
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

    public static void handle(
            Packet_SendNotificationToClient packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> NotificationClientDisplay.showNotification(
                packet.title, packet.message, packet.displayDuration, packet.position));
        context.setPacketHandled(true);
    }
}
