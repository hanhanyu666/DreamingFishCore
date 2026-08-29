package com.hhy.dreamingfishcore.gameplay.npc_message_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcConversationViewData;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageRecord;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageViewData;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcReplyViewData;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.client.cache.NpcMessageClientCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** 服务端返回的 NPC 私信只读快照。 */
public record Packet_NpcMessageSnapshotResponse(List<NpcConversationViewData> conversations)
        implements CustomPacketPayload {
    private static final int MAX_CONVERSATIONS = 64;
    private static final int MAX_MESSAGES = 256;
    private static final int MAX_REPLIES = 8;

    public static final Type<Packet_NpcMessageSnapshotResponse> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "npc_message/snapshot_response"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_NpcMessageSnapshotResponse>
            STREAM_CODEC = StreamCodec.of(Packet_NpcMessageSnapshotResponse::encode, Packet_NpcMessageSnapshotResponse::decode);

    public Packet_NpcMessageSnapshotResponse {
        conversations = conversations == null ? List.of() : List.copyOf(conversations);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_NpcMessageSnapshotResponse packet) {
        buffer.writeVarInt(packet.conversations.size());
        for (NpcConversationViewData conversation : packet.conversations) {
            buffer.writeVarInt(conversation.npcId());
            buffer.writeUtf(conversation.npcName(), 128);
            buffer.writeInt(conversation.favorability());
            buffer.writeUtf(conversation.relationName(), 64);
            buffer.writeVarInt(conversation.unreadCount());
            buffer.writeLong(conversation.lastMessageAtEpochMillis());
            buffer.writeVarInt(conversation.messages().size());
            for (NpcMessageViewData message : conversation.messages()) {
                buffer.writeUtf(message.recordId(), 64);
                buffer.writeUtf(message.definitionId(), 256);
                buffer.writeUtf(message.subject(), 256);
                buffer.writeEnum(message.direction());
                buffer.writeUtf(message.content(), 4096);
                buffer.writeLong(message.sentAtEpochMillis());
                buffer.writeBoolean(message.read());
                buffer.writeBoolean(message.replied());
                buffer.writeVarInt(message.availableReplies().size());
                for (NpcReplyViewData reply : message.availableReplies()) {
                    buffer.writeUtf(reply.id(), 64);
                    buffer.writeUtf(reply.text(), 1024);
                }
            }
        }
    }

    private static Packet_NpcMessageSnapshotResponse decode(FriendlyByteBuf buffer) {
        int conversationCount = readCount(buffer, MAX_CONVERSATIONS, "会话");
        List<NpcConversationViewData> conversations = new ArrayList<>(conversationCount);
        for (int conversationIndex = 0; conversationIndex < conversationCount; conversationIndex++) {
            int npcId = buffer.readVarInt();
            String npcName = buffer.readUtf(128);
            int favorability = buffer.readInt();
            String relationName = buffer.readUtf(64);
            int unreadCount = buffer.readVarInt();
            long lastMessageAt = buffer.readLong();
            int messageCount = readCount(buffer, MAX_MESSAGES, "消息");
            List<NpcMessageViewData> messages = new ArrayList<>(messageCount);
            for (int messageIndex = 0; messageIndex < messageCount; messageIndex++) {
                String recordId = buffer.readUtf(64);
                String definitionId = buffer.readUtf(256);
                String subject = buffer.readUtf(256);
                NpcMessageRecord.Direction direction = buffer.readEnum(NpcMessageRecord.Direction.class);
                String content = buffer.readUtf(4096);
                long sentAt = buffer.readLong();
                boolean read = buffer.readBoolean();
                boolean replied = buffer.readBoolean();
                int replyCount = readCount(buffer, MAX_REPLIES, "回复");
                List<NpcReplyViewData> replies = new ArrayList<>(replyCount);
                for (int replyIndex = 0; replyIndex < replyCount; replyIndex++) {
                    replies.add(new NpcReplyViewData(buffer.readUtf(64), buffer.readUtf(1024)));
                }
                messages.add(new NpcMessageViewData(
                        recordId, definitionId, subject, direction, content, sentAt, read, replied, replies));
            }
            conversations.add(new NpcConversationViewData(
                    npcId, npcName, favorability, relationName, unreadCount, lastMessageAt, messages));
        }
        return new Packet_NpcMessageSnapshotResponse(conversations);
    }

    public static void handle(Packet_NpcMessageSnapshotResponse packet, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(packet));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(Packet_NpcMessageSnapshotResponse packet) {
        NpcMessageClientCache.set(packet.conversations);
    }

    private static int readCount(FriendlyByteBuf buffer, int maximum, String label) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException(label + "数量非法：" + count);
        }
        return count;
    }
}
