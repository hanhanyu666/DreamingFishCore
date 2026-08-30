package com.hhy.dreamingfishcore.server.notice_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.notice_system.NoticeCategory;
import com.hhy.dreamingfishcore.server.notice_system.client.cache.NoticeClientCache;
import com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen.ServerScreenUI_Screen;
import com.hhy.dreamingfishcore.server.notice_system.NoticeData;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 公告列表响应数据包（服务端 -> 客户端）
 * 包含所有公告和玩家已读状态
 */
public class Packet_NoticeListResponse implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    /** Hard limits keep a malformed client payload from allocating unbounded collections/strings. */
    private static final int MAX_NOTICE_COUNT = 1024;
    private static final int MAX_READ_NOTICE_COUNT = 4096;
    private static final int MAX_TEXT_LENGTH = 32_767;
    private static final int MAX_CATEGORY_LENGTH = 32;
    private static final int MAX_STAGE_ID_LENGTH = 256;
    private static final int MAX_STORY_DATE_LENGTH = 256;

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_NoticeListResponse> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "notice_system/packet_notice_list_response"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_NoticeListResponse> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_NoticeListResponse.encode(packet, buf), Packet_NoticeListResponse::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    private final List<NoticeData> notices;
    private final Set<Integer> readNoticeIds;

    public Packet_NoticeListResponse(List<NoticeData> notices, Set<Integer> readNoticeIds) {
        this.notices = notices;
        this.readNoticeIds = readNoticeIds;
    }

    public static void encode(Packet_NoticeListResponse msg, FriendlyByteBuf buf) {
        List<NoticeData> notices = msg.notices == null ? List.of() : msg.notices;
        Set<Integer> readNoticeIds = msg.readNoticeIds == null ? Set.of() : msg.readNoticeIds;
        requireCount("公告", notices.size(), MAX_NOTICE_COUNT);
        requireCount("已读公告", readNoticeIds.size(), MAX_READ_NOTICE_COUNT);

        buf.writeInt(notices.size());
        for (NoticeData notice : notices) {
            if (notice == null) {
                throw new IllegalArgumentException("公告列表不能包含 null");
            }
            buf.writeInt(notice.getNoticeId());
            buf.writeUtf(normalize(notice.getNoticeTitle()), MAX_TEXT_LENGTH);
            buf.writeUtf(normalize(notice.getNoticeContent()), MAX_TEXT_LENGTH);
            buf.writeLong(notice.getPublishTime());
            buf.writeUtf(notice.getCategory().name(), MAX_CATEGORY_LENGTH);
            buf.writeUtf(notice.getStoryStageId(), MAX_STAGE_ID_LENGTH);
            buf.writeUtf(notice.getStoryDate(), MAX_STORY_DATE_LENGTH);
        }

        buf.writeInt(readNoticeIds.size());
        for (Integer readId : readNoticeIds) {
            if (readId == null) {
                throw new IllegalArgumentException("已读公告 ID 不能为 null");
            }
            buf.writeInt(readId);
        }
    }

    public static Packet_NoticeListResponse decode(FriendlyByteBuf buf) {
        List<NoticeData> notices = new ArrayList<>();
        Set<Integer> readNoticeIds = new HashSet<>();

        int noticeCount = readCount(buf, "公告", MAX_NOTICE_COUNT);
        for (int i = 0; i < noticeCount; i++) {
            int noticeId = buf.readInt();
            String title = buf.readUtf(MAX_TEXT_LENGTH);
            String content = buf.readUtf(MAX_TEXT_LENGTH);
            long publishTime = buf.readLong();
            NoticeCategory category = decodeCategory(buf.readUtf(MAX_CATEGORY_LENGTH));
            String storyStageId = buf.readUtf(MAX_STAGE_ID_LENGTH);
            String storyDate = buf.readUtf(MAX_STORY_DATE_LENGTH);
            notices.add(new NoticeData(
                    noticeId, title, content, publishTime,
                    category, storyStageId, storyDate, ""));
        }

        int readCount = readCount(buf, "已读公告", MAX_READ_NOTICE_COUNT);
        for (int i = 0; i < readCount; i++) {
            readNoticeIds.add(buf.readInt());
        }

        return new Packet_NoticeListResponse(notices, readNoticeIds);
    }

    public static void handle(Packet_NoticeListResponse msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            handleClient(msg);
        });
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(Packet_NoticeListResponse msg) {
        DreamingFishCore.LOGGER.info("收到 {} 条公告", msg.notices.size());
        NoticeClientCache.set(msg.notices, msg.readNoticeIds);
        // 将公告数据传递给UI
        ServerScreenUI_Screen.setNoticeData(msg.notices, msg.readNoticeIds);
    }

    public List<NoticeData> getNotices() {
        return notices;
    }

    public Set<Integer> getReadNoticeIds() {
        return readNoticeIds;
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    private static void requireCount(String label, int count, int maximum) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(
                    label + "数量非法：" + count + "（允许范围 0-" + maximum + "）");
        }
    }

    private static int readCount(FriendlyByteBuf buf, String label, int maximum) {
        int count = buf.readInt();
        requireCount(label, count, maximum);
        return count;
    }

    /** Unknown enum values are treated as legacy maintenance notices. */
    private static NoticeCategory decodeCategory(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return NoticeCategory.MAINTENANCE;
        }
        try {
            return NoticeCategory.valueOf(encoded);
        } catch (IllegalArgumentException ignored) {
            DreamingFishCore.LOGGER.warn("收到未知公告类别 '{}'，按服务器通知（MAINTENANCE）处理", encoded);
            return NoticeCategory.MAINTENANCE;
        }
    }
}
