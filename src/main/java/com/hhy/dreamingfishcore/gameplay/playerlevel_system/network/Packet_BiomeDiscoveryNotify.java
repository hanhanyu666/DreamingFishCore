package com.hhy.dreamingfishcore.gameplay.playerlevel_system.network;

import com.hhy.dreamingfishcore.gameplay.playerlevel_system.client.ui.notification.BiomeDiscoveryToast;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class Packet_BiomeDiscoveryNotify {
    private final String biomeId;
    private final String biomeName;
    private final int totalExplored;
    private final long experienceReward;
    private final boolean newlyDiscovered;

    public Packet_BiomeDiscoveryNotify(String biomeId, String biomeName, int totalExplored,
                                       long experienceReward, boolean newlyDiscovered) {
        this.biomeId = biomeId;
        this.biomeName = biomeName;
        this.totalExplored = totalExplored;
        this.experienceReward = experienceReward;
        this.newlyDiscovered = newlyDiscovered;
    }

    public static void encode(Packet_BiomeDiscoveryNotify packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.biomeId);
        buf.writeUtf(packet.biomeName);
        buf.writeInt(packet.totalExplored);
        buf.writeLong(packet.experienceReward);
        buf.writeBoolean(packet.newlyDiscovered);
    }

    public static Packet_BiomeDiscoveryNotify decode(FriendlyByteBuf buf) {
        return new Packet_BiomeDiscoveryNotify(buf.readUtf(), buf.readUtf(), buf.readInt(),
                buf.readLong(), buf.readBoolean());
    }

    public static void handle(Packet_BiomeDiscoveryNotify packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> BiomeDiscoveryToast.show(
                packet.biomeId, packet.biomeName, packet.totalExplored,
                packet.experienceReward, packet.newlyDiscovered));
        context.setPacketHandled(true);
    }
}
