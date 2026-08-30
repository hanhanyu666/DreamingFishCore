package com.hhy.dreamingfishcore.server.notice_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.notice_system.client.cache.NoticeClientCache;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;


/**
 * 新公告提醒数据包（服务端 -> 客户端）
 * 玩家登录时，如果有新公告则发送此包提醒玩家
 */
public class Packet_NoticeCheckResponse implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_NoticeCheckResponse> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "notice_system/packet_notice_check_response"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_NoticeCheckResponse> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_NoticeCheckResponse.encode(packet, buf), Packet_NoticeCheckResponse::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    private final boolean hasNewNotice;
    private final int latestNoticeId;
    private final String latestNoticeTitle;

    public Packet_NoticeCheckResponse(boolean hasNewNotice, int latestNoticeId, String latestNoticeTitle) {
        this.hasNewNotice = hasNewNotice;
        this.latestNoticeId = latestNoticeId;
        this.latestNoticeTitle = latestNoticeTitle;
    }

    public static void encode(Packet_NoticeCheckResponse msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.hasNewNotice);
        buf.writeInt(msg.latestNoticeId);
        buf.writeUtf(msg.latestNoticeTitle);
    }

    public static Packet_NoticeCheckResponse decode(FriendlyByteBuf buf) {
        boolean hasNewNotice = buf.readBoolean();
        int latestNoticeId = buf.readInt();
        String latestNoticeTitle = buf.readUtf();
        return new Packet_NoticeCheckResponse(hasNewNotice, latestNoticeId, latestNoticeTitle);
    }

    public static void handle(Packet_NoticeCheckResponse msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            handleClient(msg);
        });
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(Packet_NoticeCheckResponse msg) {
        NoticeClientCache.setUnreadHint(msg.hasNewNotice, msg.latestNoticeId);
        if (msg.hasNewNotice) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                DreamingFishCore.LOGGER.debug("收到新公告提醒，但客户端玩家尚未就绪");
                return;
            }
            // 显示提示消息
            mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§a§l[新公告] §e" + msg.latestNoticeTitle),
                false
            );
            DreamingFishCore.LOGGER.info("收到新公告提醒: {}", msg.latestNoticeTitle);
        }
    }

    public boolean isHasNewNotice() {
        return hasNewNotice;
    }

    public int getLatestNoticeId() {
        return latestNoticeId;
    }

    public String getLatestNoticeTitle() {
        return latestNoticeTitle;
    }
}
