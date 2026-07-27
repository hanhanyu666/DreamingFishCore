package com.hhy.dreamingfishcore.gameplay.playerlevel_system.network;

import com.hhy.dreamingfishcore.gameplay.playerlevel_system.client.ui.notification.BiomeDiscoveryToast;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 新生物群系发现提示（服务端 -> 客户端）
 */
public class Packet_BiomeDiscoveryNotify implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<Packet_BiomeDiscoveryNotify> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "playerdata_system/packet_biome_discovery_notify"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf,
            Packet_BiomeDiscoveryNotify> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(Packet_BiomeDiscoveryNotify::encode,
                    Packet_BiomeDiscoveryNotify::decode);

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

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(FriendlyByteBuf buf, Packet_BiomeDiscoveryNotify packet) {
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
