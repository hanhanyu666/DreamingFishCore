package com.hhy.dreamingfishcore.server.title_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.title_system.event.ChangeChatEvent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client request for sending a chat message with a quoted player message. */
public record Packet_QuotedChatMessage(String body, String quotedPlayerName, String quotedBody)
        implements CustomPacketPayload {
    public static final Type<Packet_QuotedChatMessage> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "chat/quoted_player_message"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_QuotedChatMessage> STREAM_CODEC = StreamCodec.of(
            Packet_QuotedChatMessage::encode, Packet_QuotedChatMessage::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, Packet_QuotedChatMessage packet) {
        buf.writeUtf(packet.body == null ? "" : packet.body, 256);
        buf.writeUtf(packet.quotedPlayerName == null ? "" : packet.quotedPlayerName, 64);
        buf.writeUtf(packet.quotedBody == null ? "" : packet.quotedBody, 256);
    }

    private static Packet_QuotedChatMessage decode(RegistryFriendlyByteBuf buf) {
        return new Packet_QuotedChatMessage(buf.readUtf(256), buf.readUtf(64), buf.readUtf(256));
    }

    public static void handle(Packet_QuotedChatMessage packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                String body = packet.body == null ? "" : packet.body.trim();
                if (!body.isBlank() && !body.startsWith("/")) {
                    ChangeChatEvent.broadcastRichChat(player, body, packet.quotedPlayerName, packet.quotedBody);
                }
            }
        });
    }
}
