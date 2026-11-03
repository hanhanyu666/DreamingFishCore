package com.hhy.dreamingfishcore.gameplay.disaster_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：管理员在终端 UI 里调整某一天的天灾（加一条或去一条）。
 *
 * <p>服务端**自己校验权限**（2 级）：客户端说"我能改"不算数。改完把最新快照回给这个玩家，
 * 所以 UI 不用自己猜结果。</p>
 */
public record Packet_DisasterEditRequest(long day, int eventOrdinal, boolean add)
        implements CustomPacketPayload {

    public static final Type<Packet_DisasterEditRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "disaster/edit_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_DisasterEditRequest> STREAM_CODEC =
            StreamCodec.of(Packet_DisasterEditRequest::encode, Packet_DisasterEditRequest::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_DisasterEditRequest packet) {
        buffer.writeLong(packet.day());
        buffer.writeVarInt(packet.eventOrdinal());
        buffer.writeBoolean(packet.add());
    }

    private static Packet_DisasterEditRequest decode(FriendlyByteBuf buffer) {
        return new Packet_DisasterEditRequest(buffer.readLong(), buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(Packet_DisasterEditRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!player.hasPermissions(2)) {
                // 越权请求直接忽略（不报错刷屏），但把最新快照发回去，避免客户端界面继续显示错误状态
                Packet_DisasterSnapshotResponse.send(player);
                return;
            }
            DisasterSchedule.EventType[] values = DisasterSchedule.EventType.values();
            if (packet.eventOrdinal() < 0 || packet.eventOrdinal() >= values.length) {
                Packet_DisasterSnapshotResponse.send(player);
                return;
            }
            DisasterService.setDayEvent(player.getServer(), packet.day(),
                    values[packet.eventOrdinal()], packet.add());
            Packet_DisasterSnapshotResponse.send(player);
        });
    }
}
