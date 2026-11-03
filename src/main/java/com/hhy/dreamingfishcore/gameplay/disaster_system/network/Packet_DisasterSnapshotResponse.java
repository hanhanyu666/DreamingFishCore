package com.hhy.dreamingfishcore.gameplay.disaster_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C：天灾日程快照（终端 UI 用）。
 *
 * <p>一次带完整 14 天——日程很短、一次全量最省事，也天然避免"增量不同步"。
 * 事件是列表，所以**一天多个天灾叠加**在数据层面本来就支持，UI 照着列表画即可。</p>
 */
public record Packet_DisasterSnapshotResponse(
        long today,
        long firstDay,
        long seed,
        boolean canEdit,
        List<DayEntry> days) implements CustomPacketPayload {

    /** 一天的安排。{@code events} 为空表示平安日。 */
    public record DayEntry(long day, List<DisasterSchedule.EventType> events) {
        public DayEntry {
            events = events == null ? List.of() : List.copyOf(events);
        }
    }

    public static final Type<Packet_DisasterSnapshotResponse> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "disaster/snapshot_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_DisasterSnapshotResponse> STREAM_CODEC =
            StreamCodec.of(Packet_DisasterSnapshotResponse::encode, Packet_DisasterSnapshotResponse::decode);

    public Packet_DisasterSnapshotResponse {
        days = days == null ? List.of() : List.copyOf(days);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_DisasterSnapshotResponse packet) {
        buffer.writeLong(packet.today());
        buffer.writeLong(packet.firstDay());
        buffer.writeLong(packet.seed());
        buffer.writeBoolean(packet.canEdit());
        buffer.writeVarInt(packet.days().size());
        for (DayEntry entry : packet.days()) {
            buffer.writeLong(entry.day());
            buffer.writeVarInt(entry.events().size());
            for (DisasterSchedule.EventType type : entry.events()) {
                buffer.writeByte(type.ordinal());
            }
        }
    }

    private static Packet_DisasterSnapshotResponse decode(FriendlyByteBuf buffer) {
        long today = buffer.readLong();
        long firstDay = buffer.readLong();
        long seed = buffer.readLong();
        boolean canEdit = buffer.readBoolean();
        int count = buffer.readVarInt();
        List<DayEntry> days = new ArrayList<>(Math.max(0, count));
        for (int index = 0; index < count; index++) {
            long day = buffer.readLong();
            int eventCount = buffer.readVarInt();
            List<DisasterSchedule.EventType> events = new ArrayList<>(Math.max(0, eventCount));
            DisasterSchedule.EventType[] values = DisasterSchedule.EventType.values();
            for (int eventIndex = 0; eventIndex < eventCount; eventIndex++) {
                int ordinal = buffer.readByte() & 0xFF;
                if (ordinal < values.length) {
                    events.add(values[ordinal]);
                }
            }
            days.add(new DayEntry(day, events));
        }
        return new Packet_DisasterSnapshotResponse(today, firstDay, seed, canEdit, days);
    }

    public static void handle(Packet_DisasterSnapshotResponse packet, IPayloadContext context) {
        context.enqueueWork(() -> com.hhy.dreamingfishcore.gameplay.disaster_system.client
                .DisasterClientCache.accept(packet));
    }

    /** 服务端：把当前日程发给某个玩家（编辑权限按他的权限等级判断）。 */
    public static void send(ServerPlayer player) {
        if (player == null || player.getServer() == null) {
            return;
        }
        DisasterService.ensureLoaded(player.getServer());
        DisasterSchedule.Schedule schedule = DisasterService.schedule();
        List<DayEntry> days = new ArrayList<>();
        if (schedule != null) {
            schedule.days().forEach(plan -> days.add(new DayEntry(plan.day(), plan.events())));
        }
        // 编辑世界事实需要 2 级权限（与 /dreamingfish disaster reroll 的 3 级区分：UI 只做单日微调）
        boolean canEdit = player.hasPermissions(2);
        PacketDistributor.sendToPlayer(player, new Packet_DisasterSnapshotResponse(
                DisasterService.today(player.getServer()),
                schedule == null ? 0L : schedule.firstDay(),
                schedule == null ? 0L : schedule.seed(),
                canEdit, days));
    }
}
