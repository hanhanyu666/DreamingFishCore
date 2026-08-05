package com.hhy.dreamingfishcore.gameplay.marker_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.marker_system.MarkerManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public class Packet_ShowMarker implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<Packet_ShowMarker> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "marker_system/packet_show_marker"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_ShowMarker> STREAM_CODEC =
            StreamCodec.of(Packet_ShowMarker::encode, Packet_ShowMarker::decode);

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

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(FriendlyByteBuf buf, Packet_ShowMarker packet) {
        buf.writeUUID(packet.ownerId);
        buf.writeUtf(packet.ownerName);
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.y);
        buf.writeDouble(packet.z);
        buf.writeLong(packet.createdAtMs);
    }

    public static Packet_ShowMarker decode(FriendlyByteBuf buf) {
        return new Packet_ShowMarker(
                buf.readUUID(),
                buf.readUtf(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readLong()
        );
    }

    public static void handle(Packet_ShowMarker packet, IPayloadContext context) {
        context.enqueueWork(() -> MarkerManager.addOrReplace(
                packet.ownerId,
                packet.ownerName,
                new Vec3(packet.x, packet.y, packet.z),
                packet.createdAtMs
        ));
    }
}
