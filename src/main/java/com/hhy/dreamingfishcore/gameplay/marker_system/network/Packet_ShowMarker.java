package com.hhy.dreamingfishcore.gameplay.marker_system.network;

import com.hhy.dreamingfishcore.gameplay.marker_system.MarkerManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class Packet_ShowMarker {
    private final UUID ownerId;
    private final String ownerName;
    private final double x;
    private final double y;
    private final double z;
    private final long createdAtMs;

    public Packet_ShowMarker(UUID ownerId, String ownerName, double x, double y, double z, long createdAtMs) {
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.createdAtMs = createdAtMs;
    }

    public static void encode(Packet_ShowMarker packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.ownerId);
        buf.writeUtf(packet.ownerName);
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.y);
        buf.writeDouble(packet.z);
        buf.writeLong(packet.createdAtMs);
    }

    public static Packet_ShowMarker decode(FriendlyByteBuf buf) {
        return new Packet_ShowMarker(buf.readUUID(), buf.readUtf(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readLong());
    }

    public static void handle(Packet_ShowMarker packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> MarkerManager.addOrReplace(
                packet.ownerId, packet.ownerName, new Vec3(packet.x, packet.y, packet.z), packet.createdAtMs));
        context.setPacketHandled(true);
    }
}
