package com.hhy.dreamingfishcore.network.packets.marker_system;

import com.hhy.dreamingfishcore.screen.server_screen.tips.TipDisplayManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class Packet_MarkerRejected {
    private final String message;

    public Packet_MarkerRejected(String message) {
        this.message = message == null || message.isBlank() ? "标点失败" : message;
    }

    public static void encode(Packet_MarkerRejected packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.message);
    }

    public static Packet_MarkerRejected decode(FriendlyByteBuf buf) {
        return new Packet_MarkerRejected(buf.readUtf());
    }

    public static void handle(Packet_MarkerRejected packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> TipDisplayManager.addMessage(packet.message, 1600));
        context.setPacketHandled(true);
    }
}
