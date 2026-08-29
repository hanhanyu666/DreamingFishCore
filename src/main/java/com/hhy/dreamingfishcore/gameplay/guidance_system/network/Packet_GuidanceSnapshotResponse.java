package com.hhy.dreamingfishcore.gameplay.guidance_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceEntry;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceViewData;
import com.hhy.dreamingfishcore.gameplay.guidance_system.client.cache.GuidanceClientCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** 服务端返回的个人引导只读快照。 */
public record Packet_GuidanceSnapshotResponse(List<GuidanceViewData> entries) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 256;

    public static final Type<Packet_GuidanceSnapshotResponse> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "guidance/snapshot_response"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_GuidanceSnapshotResponse>
            STREAM_CODEC = StreamCodec.of(Packet_GuidanceSnapshotResponse::encode, Packet_GuidanceSnapshotResponse::decode);

    public Packet_GuidanceSnapshotResponse {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_GuidanceSnapshotResponse packet) {
        buffer.writeVarInt(packet.entries.size());
        for (GuidanceViewData entry : packet.entries) {
            buffer.writeUtf(entry.recordId(), 64);
            buffer.writeUtf(entry.definitionId(), 160);
            buffer.writeVarInt(entry.sourceNpcId());
            buffer.writeUtf(entry.sourceNpcName(), 128);
            buffer.writeUtf(entry.title(), 256);
            buffer.writeUtf(entry.content(), 4096);
            buffer.writeUtf(entry.sourceQuote(), 4096);
            buffer.writeUtf(entry.storyStageId(), 160);
            buffer.writeUtf(entry.locationLabel(), 256);
            buffer.writeUtf(entry.dimension(), 160);
            buffer.writeBoolean(entry.hasLocation());
            buffer.writeInt(entry.x());
            buffer.writeInt(entry.y());
            buffer.writeInt(entry.z());
            buffer.writeEnum(entry.status());
            buffer.writeLong(entry.createdAtEpochMillis());
            buffer.writeLong(entry.resolvedAtEpochMillis());
        }
    }

    private static Packet_GuidanceSnapshotResponse decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("引导记录数量非法：" + count);
        }
        List<GuidanceViewData> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new GuidanceViewData(
                    buffer.readUtf(64),
                    buffer.readUtf(160),
                    buffer.readVarInt(),
                    buffer.readUtf(128),
                    buffer.readUtf(256),
                    buffer.readUtf(4096),
                    buffer.readUtf(4096),
                    buffer.readUtf(160),
                    buffer.readUtf(256),
                    buffer.readUtf(160),
                    buffer.readBoolean(),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readEnum(GuidanceEntry.Status.class),
                    buffer.readLong(),
                    buffer.readLong()));
        }
        return new Packet_GuidanceSnapshotResponse(entries);
    }

    public static void handle(Packet_GuidanceSnapshotResponse packet, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(packet));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(Packet_GuidanceSnapshotResponse packet) {
        GuidanceClientCache.set(packet.entries);
    }
}
