package com.hhy.dreamingfishcore.server.notice_system.network;

import com.hhy.dreamingfishcore.server.notice_system.NoticeDeliveryService;
import com.hhy.dreamingfishcore.server.notice_system.PlayerNoticeDataManager;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;


/**
 * 公告列表请求数据包（客户端 -> 服务端）
 * 玩家点击"服务器公告"按钮时发送
 */
public class Packet_NoticeListRequest implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_NoticeListRequest> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "notice_system/packet_notice_list_request"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_NoticeListRequest> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_NoticeListRequest.encode(packet, buf), Packet_NoticeListRequest::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    public Packet_NoticeListRequest() {
    }

    public static void encode(Packet_NoticeListRequest msg, FriendlyByteBuf buf) {
        // 无需写入数据
    }

    public static Packet_NoticeListRequest decode(FriendlyByteBuf buf) {
        return new Packet_NoticeListRequest();
    }

    public static void handle(Packet_NoticeListRequest msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player != null) {
                // 只把当前玩家在当前故事阶段可读的公告发给客户端。
                // 教程完成状态不参与这里的筛选；旧阶段公告仍不会污染列表和未读角标。
                var notices = NoticeDeliveryService.getVisibleNotices(player);
                // 只同步当前可见公告对应的已读 ID。旧阶段历史状态继续留在服务端存档，
                // 但不会占用当前响应的数量上限，也不会干扰客户端未读角标。
                var allReadNoticeIds = PlayerNoticeDataManager.getReadNoticeIds(player.getUUID());
                var readNoticeIds = new java.util.HashSet<Integer>();
                for (var notice : notices) {
                    if (allReadNoticeIds.contains(notice.getNoticeId())) {
                        readNoticeIds.add(notice.getNoticeId());
                    }
                }

                // 发送响应
                DreamingFishCore_NetworkManager.sendToClient(
                    player,
                    new Packet_NoticeListResponse(notices, readNoticeIds)
                );
            }
        });
    }
}
