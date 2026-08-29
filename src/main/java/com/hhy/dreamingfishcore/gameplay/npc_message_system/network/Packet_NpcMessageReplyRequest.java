package com.hhy.dreamingfishcore.gameplay.npc_message_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 玩家选择一项服务端提供的预设回复。 */
public record Packet_NpcMessageReplyRequest(String messageRecordId, String replyId)
        implements CustomPacketPayload {
    public static final Type<Packet_NpcMessageReplyRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "npc_message/reply_request"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_NpcMessageReplyRequest>
            STREAM_CODEC = StreamCodec.of(Packet_NpcMessageReplyRequest::encode, Packet_NpcMessageReplyRequest::decode);

    public Packet_NpcMessageReplyRequest {
        messageRecordId = messageRecordId == null ? "" : messageRecordId;
        replyId = replyId == null ? "" : replyId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_NpcMessageReplyRequest packet) {
        buffer.writeUtf(packet.messageRecordId, 64);
        buffer.writeUtf(packet.replyId, 64);
    }

    private static Packet_NpcMessageReplyRequest decode(FriendlyByteBuf buffer) {
        return new Packet_NpcMessageReplyRequest(buffer.readUtf(64), buffer.readUtf(64));
    }

    public static void handle(Packet_NpcMessageReplyRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && !NpcMessageManager.reply(player, packet.messageRecordId, packet.replyId)) {
                NpcMessageManager.syncToClient(player);
            }
        });
    }
}
