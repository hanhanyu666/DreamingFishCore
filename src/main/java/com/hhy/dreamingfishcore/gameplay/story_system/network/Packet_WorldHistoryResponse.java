package com.hhy.dreamingfishcore.gameplay.story_system.network;

import com.hhy.dreamingfishcore.gameplay.story_system.WorldHistoryLog;
import com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen.ServerScreenUI_Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 服务端返回给终端历史页面的只读视图。 */
public final class Packet_WorldHistoryResponse implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    private static final int MAX_EVENTS = 60;
    private static final int MAX_DETAILS = 4;

    public static final Type<Packet_WorldHistoryResponse> TYPE = new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    com.hhy.dreamingfishcore.DreamingFishCore.MODID,
                    "story_system/packet_world_history_response"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_WorldHistoryResponse>
            STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of(
                    (buffer, packet) -> Packet_WorldHistoryResponse.encode(packet, buffer),
                    Packet_WorldHistoryResponse::decode);

    private final List<HistoryEntry> entries;
    private final long totalEventCount;
    private final boolean historyLoaded;
    private final boolean writesEnabled;

    public Packet_WorldHistoryResponse(
            List<HistoryEntry> entries,
            long totalEventCount,
            boolean historyLoaded,
            boolean writesEnabled) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
        this.totalEventCount = Math.max(0L, totalEventCount);
        this.historyLoaded = historyLoaded;
        this.writesEnabled = writesEnabled;
    }

    public static Packet_WorldHistoryResponse fromServerEvents(
            List<WorldHistoryLog.HistoryEvent> events,
            long totalEventCount,
            boolean historyLoaded,
            boolean writesEnabled) {
        List<HistoryEntry> entries = new ArrayList<>();
        if (events != null) {
            events.stream()
                    .limit(MAX_EVENTS)
                    .map(Packet_WorldHistoryResponse::toPublicEntry)
                    .forEach(entries::add);
        }
        return new Packet_WorldHistoryResponse(entries, totalEventCount, historyLoaded, writesEnabled);
    }

    private static HistoryEntry toPublicEntry(WorldHistoryLog.HistoryEvent event) {
        Map<String, String> publicDetails = new LinkedHashMap<>();
        copyDetail(event, publicDetails, "participantCount");
        copyDetail(event, publicDetails, "roundNumber");
        copyDetail(event, publicDetails, "previousStageId");

        return new HistoryEntry(
                event.getSequence(),
                event.getActiveTick(),
                event.getRecordedAtEpochMillis(),
                event.getType().name(),
                event.getSubjectId(),
                event.getActor(),
                publicDetails);
    }

    private static void copyDetail(
            WorldHistoryLog.HistoryEvent event,
            Map<String, String> destination,
            String key) {
        String value = event.getDetails().get(key);
        if (value != null) {
            destination.put(key, value);
        }
    }

    @Override
    public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(Packet_WorldHistoryResponse packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entries.size());
        for (HistoryEntry entry : packet.entries) {
            buffer.writeLong(entry.sequence());
            buffer.writeLong(entry.activeTick());
            buffer.writeLong(entry.recordedAtEpochMillis());
            buffer.writeUtf(entry.type(), 64);
            buffer.writeUtf(entry.subjectId(), 128);
            buffer.writeUtf(entry.actor(), 128);
            buffer.writeVarInt(entry.details().size());
            for (Map.Entry<String, String> detail : entry.details().entrySet()) {
                buffer.writeUtf(detail.getKey(), 64);
                buffer.writeUtf(detail.getValue(), 1024);
            }
        }
        buffer.writeLong(packet.totalEventCount);
        buffer.writeBoolean(packet.historyLoaded);
        buffer.writeBoolean(packet.writesEnabled);
    }

    public static Packet_WorldHistoryResponse decode(FriendlyByteBuf buffer) {
        int eventCount = buffer.readVarInt();
        if (eventCount < 0 || eventCount > MAX_EVENTS) {
            throw new IllegalArgumentException("世界历史事件数量非法：" + eventCount);
        }

        List<HistoryEntry> entries = new ArrayList<>(eventCount);
        for (int index = 0; index < eventCount; index++) {
            long sequence = buffer.readLong();
            long activeTick = buffer.readLong();
            long recordedAtEpochMillis = buffer.readLong();
            String type = buffer.readUtf(64);
            String subjectId = buffer.readUtf(128);
            String actor = buffer.readUtf(128);

            int detailCount = buffer.readVarInt();
            if (detailCount < 0 || detailCount > MAX_DETAILS) {
                throw new IllegalArgumentException("世界历史详细字段数量非法：" + detailCount);
            }
            Map<String, String> details = new LinkedHashMap<>();
            for (int detailIndex = 0; detailIndex < detailCount; detailIndex++) {
                details.put(buffer.readUtf(64), buffer.readUtf(1024));
            }
            entries.add(new HistoryEntry(
                    sequence,
                    activeTick,
                    recordedAtEpochMillis,
                    type,
                    subjectId,
                    actor,
                    details));
        }

        return new Packet_WorldHistoryResponse(
                entries,
                buffer.readLong(),
                buffer.readBoolean(),
                buffer.readBoolean());
    }

    public static void handle(Packet_WorldHistoryResponse packet, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(packet));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(Packet_WorldHistoryResponse packet) {
        ServerScreenUI_Screen.setHistoryData(
                packet.entries,
                packet.totalEventCount,
                packet.historyLoaded,
                packet.writesEnabled);
    }

    /** 客户端只读历史条目，不包含世界旗标或未公开运营字段。 */
    public record HistoryEntry(
            long sequence,
            long activeTick,
            long recordedAtEpochMillis,
            String type,
            String subjectId,
            String actor,
            Map<String, String> details) {
        public HistoryEntry {
            type = type == null ? "" : type;
            subjectId = subjectId == null ? "" : subjectId;
            actor = actor == null ? "system" : actor;
            details = details == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(details));
        }
    }
}
