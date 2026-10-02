package com.hhy.dreamingfishcore.gameplay.storybook_system.network;

import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookDataManager;
import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookEntryViewData;
import com.hhy.dreamingfishcore.gameplay.storybook_system.client.ui.screen.Screen_StoryBookCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 下发整本随记本的目录。
 *
 * <p>里程碑 2 起每条带稳定 ID 与四项可见论证元数据（来源 / 观察跨度 / 样本 / 观察条件）。
 * 私密定义的内容没有对应字段，因此不可能从这里泄漏。</p>
 */
public class Packet_OpenStoryBookGUI implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final Type<Packet_OpenStoryBookGUI> TYPE = new Type<>(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    com.hhy.dreamingfishcore.DreamingFishCore.MODID,
                    "storybook_system/packet_open_story_book_gui"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf,
            Packet_OpenStoryBookGUI> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of(
            (buf, packet) -> Packet_OpenStoryBookGUI.encode(packet, buf),
            Packet_OpenStoryBookGUI::decode);

    private final List<StoryBookEntryViewData> entries;

    public Packet_OpenStoryBookGUI(List<StoryBookEntryViewData> entries) {
        this.entries = entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(Packet_OpenStoryBookGUI packet, FriendlyByteBuf buf) {
        if (packet == null || packet.entries == null
                || packet.entries.size() > StoryBookDataManager.MAX_NETWORK_BOOK_ENTRIES) {
            throw new IllegalArgumentException("随记本条目超过网络上限");
        }
        buf.writeVarInt(packet.entries.size());
        for (StoryBookEntryViewData entry : packet.entries) {
            buf.writeUtf(entry.getClueId(), 256);
            buf.writeVarInt(entry.getLegacyId());
            buf.writeVarInt(entry.getStageId());
            buf.writeVarInt(entry.getChapterId());
            buf.writeUtf(entry.getTitle(), StoryBookDataManager.MAX_NETWORK_TEXT_LENGTH);
            buf.writeUtf(entry.getContent(), StoryBookDataManager.MAX_NETWORK_TEXT_LENGTH);
            buf.writeUtf(entry.getTime(), 256);
            buf.writeUtf(entry.getAuthorName(), 256);
            buf.writeUtf(entry.getSource(), 256);
            buf.writeUtf(entry.getObservationSpan(), 256);
            buf.writeUtf(entry.getSample(), 256);
            buf.writeUtf(entry.getConditions(), 1024);
            buf.writeBoolean(entry.isRead());
        }
    }

    public static Packet_OpenStoryBookGUI decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > StoryBookDataManager.MAX_NETWORK_BOOK_ENTRIES) {
            throw new IllegalArgumentException("随记本条目数量非法：" + size);
        }
        List<StoryBookEntryViewData> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String clueId = buf.readUtf(256);
            int legacyId = buf.readVarInt();
            int stageId = buf.readVarInt();
            int chapterId = buf.readVarInt();
            String title = buf.readUtf(StoryBookDataManager.MAX_NETWORK_TEXT_LENGTH);
            String content = buf.readUtf(StoryBookDataManager.MAX_NETWORK_TEXT_LENGTH);
            String time = buf.readUtf(256);
            String authorName = buf.readUtf(256);
            String source = buf.readUtf(256);
            String observationSpan = buf.readUtf(256);
            String sample = buf.readUtf(256);
            String conditions = buf.readUtf(1024);
            boolean read = buf.readBoolean();
            entries.add(new StoryBookEntryViewData(clueId, legacyId, stageId, chapterId,
                    title, content, time, authorName, source, observationSpan, sample,
                    conditions, read));
        }
        return new Packet_OpenStoryBookGUI(entries);
    }

    public static void handle(Packet_OpenStoryBookGUI packet, IPayloadContext context) {
        if (FMLLoader.getDist().isClient()) {
            context.enqueueWork(() -> handleClient(packet));
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(Packet_OpenStoryBookGUI packet) {
        Minecraft.getInstance().setScreen(new Screen_StoryBookCatalog(packet.entries));
    }
}
