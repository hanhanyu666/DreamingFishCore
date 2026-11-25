package com.hhy.dreamingfishcore.gameplay.map_region_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.map_region_system.MapRegion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;

/**
 * S2C：地图区域快照（领地 / 剧情地区 / 外缘）。
 *
 * <p>全量下发：区域总数在几十个量级，全量比增量简单得多，也不会出现"漏收一条就永远画错"。</p>
 */
public record Packet_MapRegionSnapshot(List<MapRegion> regions) implements CustomPacketPayload {

    public static final Type<Packet_MapRegionSnapshot> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "map_region/snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_MapRegionSnapshot> STREAM_CODEC =
            StreamCodec.of(Packet_MapRegionSnapshot::encode, Packet_MapRegionSnapshot::decode);

    public Packet_MapRegionSnapshot {
        regions = regions == null ? List.of() : List.copyOf(regions);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_MapRegionSnapshot packet) {
        buffer.writeVarInt(packet.regions().size());
        for (MapRegion region : packet.regions()) {
            buffer.writeUtf(region.id(), 128);
            buffer.writeUtf(region.name(), 128);
            buffer.writeUtf(region.dimension().location().toString(), 256);
            buffer.writeVarInt(region.kind().ordinal());
            buffer.writeVarInt(region.minChunkX());
            buffer.writeVarInt(region.minChunkZ());
            buffer.writeVarInt(region.maxChunkX());
            buffer.writeVarInt(region.maxChunkZ());
            buffer.writeInt(region.argb());
        }
    }

    private static Packet_MapRegionSnapshot decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<MapRegion> regions = new ArrayList<>(Math.max(0, count));
        for (int index = 0; index < count; index++) {
            String id = buffer.readUtf(128);
            String name = buffer.readUtf(128);
            String dimension = buffer.readUtf(256);
            int kindOrdinal = buffer.readVarInt();
            int minCX = buffer.readVarInt();
            int minCZ = buffer.readVarInt();
            int maxCX = buffer.readVarInt();
            int maxCZ = buffer.readVarInt();
            int argb = buffer.readInt();

            MapRegion.Kind[] kinds = MapRegion.Kind.values();
            MapRegion.Kind kind = kindOrdinal >= 0 && kindOrdinal < kinds.length
                    ? kinds[kindOrdinal] : MapRegion.Kind.STORY;
            ResourceLocation dimensionId = ResourceLocation.tryParse(dimension);
            ResourceKey<Level> key = dimensionId == null
                    ? null : ResourceKey.create(Registries.DIMENSION, dimensionId);
            if (key == null) {
                continue;
            }
            regions.add(new MapRegion(id, name, key, minCX, minCZ, maxCX, maxCZ, argb, kind));
        }
        return new Packet_MapRegionSnapshot(regions);
    }

    public static void handle(Packet_MapRegionSnapshot packet, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() ->
                com.hhy.dreamingfishcore.gameplay.map_region_system.client.MapRegionClientCache.accept(packet));
    }
}
