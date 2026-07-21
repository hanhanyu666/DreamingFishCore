package com.hhy.dreamingfishcore.server.notice_system.network;

import com.hhy.dreamingfishcore.server.notice_system.NoticeManager;
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
                // 获取公告列表
                var notices = NoticeManager.getNotices();
                // 获取玩家已读公告ID
                var readNoticeIds = PlayerNoticeDataManager.getReadNoticeIds(player.getUUID());

                // 发送响应
                DreamingFishCore_NetworkManager.sendToClient(
                    player,
                    new Packet_NoticeListResponse(notices, readNoticeIds)
                );
            }
        });
    }
}
