package com.hhy.dreamingfishcore.gameplay.storybook_system.network;

import com.hhy.dreamingfishcore.gameplay.storybook_system.FragmentData;
import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookDataManager;
import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookEntryViewData;
import com.hhy.dreamingfishcore.gameplay.storybook_system.client.ui.screen.Screen_StoryFragment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.network.handling.IPayloadContext;


/**
 * 打开一条线索的详情页。
 *
 * <p>里程碑 2 起下发 {@link StoryBookEntryViewData}（含来源 / 观察跨度 / 样本 / 观察条件）。
 * 这里**只搬玩家可见字段**：私密定义里的立场、证据包、隐藏关系、真伪一律没有对应字段。</p>
 */
public class Packet_OpenStoryFragmentGUI implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_OpenStoryFragmentGUI> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "storybook_system/packet_open_story_fragment_gui"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_OpenStoryFragmentGUI> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_OpenStoryFragmentGUI.encode(packet, buf), Packet_OpenStoryFragmentGUI::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    private final String clueId;
    private final int legacyId;
    private final int stageId;
    private final int chapterId;
    private final String title;
    private final String content;
    private final String time;
    private final String authorName;
    private final String source;
    private final String observationSpan;
    private final String sample;
    private final String conditions;

    /** 从客户端视图构造（里程碑 2 的主路径）。 */
    public Packet_OpenStoryFragmentGUI(StoryBookEntryViewData view) {
        this(view.getClueId(), view.getLegacyId(), view.getStageId(), view.getChapterId(),
                view.getTitle(), view.getContent(), view.getTime(), view.getAuthorName(),
                view.getSource(), view.getObservationSpan(), view.getSample(),
                view.getConditions());
    }

    /** 兼容旧的片段定义（内容文件尚未迁移的调用点）。 */
    public Packet_OpenStoryFragmentGUI(FragmentData fragmentData) {
        this("", fragmentData.getId(), fragmentData.getStageId(), fragmentData.getChapterId(),
                fragmentData.getTitle(), fragmentData.getContent(), fragmentData.getTime(),
                fragmentData.getAuthorName(), "", "", "", "");
    }

    public Packet_OpenStoryFragmentGUI(String clueId, int legacyId, int stageId, int chapterId,
                                       String title, String content, String time, String authorName,
                                       String source, String observationSpan, String sample,
                                       String conditions) {
        this.clueId = clueId == null ? "" : clueId;
        this.legacyId = legacyId;
        this.stageId = stageId;
        this.chapterId = chapterId;
        this.title = title;
        this.content = content;
        this.time = time;
        this.authorName = authorName;
        this.source = source == null ? "" : source;
        this.observationSpan = observationSpan == null ? "" : observationSpan;
        this.sample = sample == null ? "" : sample;
        this.conditions = conditions == null ? "" : conditions;
    }

    public static void encode(Packet_OpenStoryFragmentGUI packet, FriendlyByteBuf buf) {
        if (packet == null
                || packet.title == null
                || packet.content == null
                || packet.title.length() > StoryBookDataManager.MAX_NETWORK_TEXT_LENGTH
                || packet.content.length() > StoryBookDataManager.MAX_NETWORK_TEXT_LENGTH) {
            throw new IllegalArgumentException("随记本片段正文超过网络上限");
        }
        buf.writeUtf(packet.clueId, 256);
        buf.writeVarInt(packet.legacyId);
        buf.writeVarInt(packet.stageId);
        buf.writeVarInt(packet.chapterId);
        buf.writeUtf(packet.title, StoryBookDataManager.MAX_NETWORK_TEXT_LENGTH);
        buf.writeUtf(packet.content, StoryBookDataManager.MAX_NETWORK_TEXT_LENGTH);
        buf.writeUtf(packet.time == null ? "" : packet.time, 256);
        buf.writeUtf(packet.authorName == null ? "" : packet.authorName, 256);
        buf.writeUtf(packet.source, 256);
        buf.writeUtf(packet.observationSpan, 256);
        buf.writeUtf(packet.sample, 256);
        buf.writeUtf(packet.conditions, 1024);
    }

    public static Packet_OpenStoryFragmentGUI decode(FriendlyByteBuf buf) {
        return new Packet_OpenStoryFragmentGUI(
                buf.readUtf(256),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(StoryBookDataManager.MAX_NETWORK_TEXT_LENGTH),
                buf.readUtf(StoryBookDataManager.MAX_NETWORK_TEXT_LENGTH),
                buf.readUtf(256),
                buf.readUtf(256),
                buf.readUtf(256),
                buf.readUtf(256),
                buf.readUtf(256),
                buf.readUtf(1024)
        );
    }

    public static void handle(Packet_OpenStoryFragmentGUI packet, IPayloadContext context) {
        if (FMLLoader.getDist().isClient()) {
            context.enqueueWork(() -> handleClient(packet));
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(Packet_OpenStoryFragmentGUI packet) {
        Minecraft.getInstance().setScreen(new Screen_StoryFragment(
                packet.clueId,
                packet.legacyId,
                packet.stageId,
                packet.chapterId,
                packet.title,
                packet.content,
                packet.time,
                packet.authorName,
                packet.source,
                packet.observationSpan,
                packet.sample,
                packet.conditions
        ));
    }
}
