package com.hhy.dreamingfishcore.server.notice_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.opening_story_system.OpeningStoryProgressManager;
import com.hhy.dreamingfishcore.server.notice_system.NoticeData;
import com.hhy.dreamingfishcore.server.notice_system.NoticeDeliveryService;
import com.hhy.dreamingfishcore.server.notice_system.NoticeManager;
import com.hhy.dreamingfishcore.server.notice_system.PlayerNoticeDataManager;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;


/**
 * 标记公告已打开/已查看的数据包（客户端 -> 服务端）。
 * 正文是否滚动到底不参与查看判定。
 */
public class Packet_MarkNoticeReadRequest implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_MarkNoticeReadRequest> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "notice_system/packet_mark_notice_read_request"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_MarkNoticeReadRequest> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_MarkNoticeReadRequest.encode(packet, buf), Packet_MarkNoticeReadRequest::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    private final int noticeId;

    public Packet_MarkNoticeReadRequest(int noticeId) {
        this.noticeId = noticeId;
    }

    public static void encode(Packet_MarkNoticeReadRequest msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.noticeId);
    }

    public static Packet_MarkNoticeReadRequest decode(FriendlyByteBuf buf) {
        int noticeId = buf.readInt();
        return new Packet_MarkNoticeReadRequest(noticeId);
    }

    public static void handle(Packet_MarkNoticeReadRequest msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            handleServer(msg, context);
        });
    }

    private static void handleServer(Packet_MarkNoticeReadRequest msg, IPayloadContext context) {
        var serverPlayer = context.player() instanceof net.minecraft.server.level.ServerPlayer player ? player : null;
        if (serverPlayer == null) {
            DreamingFishCore.LOGGER.warn("Packet_MarkNoticeReadRequest: serverPlayer is null");
            return;
        }

        NoticeData notice = NoticeManager.getNoticeById(msg.noticeId);
        if (notice == null) {
            DreamingFishCore.LOGGER.warn(
                    "玩家 {} 请求标记不存在的公告 {} 为已读",
                    serverPlayer.getName().getString(), msg.noticeId);
            return;
        }
        // 终端已读权限与教程完成状态无关；服务端仍会校验公告属于当前阶段。
        if (!NoticeDeliveryService.isVisibleToPlayer(serverPlayer, notice)) {
            DreamingFishCore.LOGGER.warn(
                    "玩家 {} 请求标记当前不可读的公告 {} 为已读",
                    serverPlayer.getName().getString(), msg.noticeId);
            return;
        }

        PlayerNoticeDataManager.markAsRead(serverPlayer.getUUID(), msg.noticeId);
        OpeningStoryProgressManager.onNoticeOpened(serverPlayer, notice);
        DreamingFishCore.LOGGER.debug("玩家 {} 标记公告 {} 为已读", serverPlayer.getName().getString(), msg.noticeId);
    }

    public int getNoticeId() {
        return noticeId;
    }
}
