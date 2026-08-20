package com.hhy.dreamingfishcore.gameplay.story_system.network;

import com.hhy.dreamingfishcore.gameplay.story_system.WorldHistoryLog;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 客户端请求当前世界可公开查看的重大历史。
 *
 * <p>世界历史日志还包含世界旗标和内容热重载等运营记录。它们可能涉及尚未公开的剧情状态，
 * 因此这里明确筛选玩家可见事件，而不是把日志文件原样发送给客户端。</p>
 */
public final class Packet_WorldHistoryRequest implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    private static final int MAX_VISIBLE_EVENTS = 60;

    public static final Type<Packet_WorldHistoryRequest> TYPE = new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    com.hhy.dreamingfishcore.DreamingFishCore.MODID,
                    "story_system/packet_world_history_request"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_WorldHistoryRequest>
            STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of(
                    (buffer, packet) -> Packet_WorldHistoryRequest.encode(packet, buffer),
                    Packet_WorldHistoryRequest::decode);

    @Override
    public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(Packet_WorldHistoryRequest packet, FriendlyByteBuf buffer) {
        // 请求没有参数。服务端统一决定公开范围和最大返回数量。
    }

    public static Packet_WorldHistoryRequest decode(FriendlyByteBuf buffer) {
        return new Packet_WorldHistoryRequest();
    }

    public static void handle(Packet_WorldHistoryRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            List<WorldHistoryLog.HistoryEvent> visibleEvents = WorldHistoryLog
                    .getRecentEvents(200)
                    .stream()
                    .filter(Packet_WorldHistoryRequest::isVisibleToPlayers)
                    .toList();
            int fromIndex = Math.max(0, visibleEvents.size() - MAX_VISIBLE_EVENTS);
            List<WorldHistoryLog.HistoryEvent> responseEvents = visibleEvents.subList(fromIndex, visibleEvents.size());
            WorldHistoryLog.Status status = WorldHistoryLog.getStatus();

            DreamingFishCore_NetworkManager.sendToClient(
                    player,
                    Packet_WorldHistoryResponse.fromServerEvents(
                            responseEvents,
                            visibleEvents.size(),
                            status.loaded(),
                            status.writesEnabled()));
        });
    }

    private static boolean isVisibleToPlayers(WorldHistoryLog.HistoryEvent event) {
        return switch (event.getType()) {
            case STAGE_CHANGED,
                 OPERATION_ROUND_STARTED,
                 OPERATION_ROUND_PUBLISHED,
                 TASK_PUBLISHED,
                 TASK_SUCCEEDED,
                 TASK_FAILED,
                 ENDING_CHANGED -> true;
            case WORLD_FLAG_CHANGED, CONTENT_RELOADED -> false;
        };
    }
}
