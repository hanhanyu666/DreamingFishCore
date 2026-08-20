package com.hhy.dreamingfishcore.server.title_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.chat.ImmersiveChatManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Structured server-to-client player chat message used by the immersive chat renderer. */
public record Packet_RichChatMessage(UUID playerId, String rank, int rankColor, String title, int titleColor,
                                     String playerName, String body, long timestamp) implements CustomPacketPayload {
    public static final Type<Packet_RichChatMessage> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "chat/rich_player_message"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_RichChatMessage> STREAM_CODEC = StreamCodec.of(
            Packet_RichChatMessage::encode, Packet_RichChatMessage::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, Packet_RichChatMessage packet) {
        buf.writeUUID(packet.playerId);
        buf.writeUtf(packet.rank, 128);
        buf.writeInt(packet.rankColor);
        buf.writeUtf(packet.title, 256);
        buf.writeInt(packet.titleColor);
        buf.writeUtf(packet.playerName, 64);
        buf.writeUtf(packet.body, 256);
        buf.writeLong(packet.timestamp);
    }

    private static Packet_RichChatMessage decode(RegistryFriendlyByteBuf buf) {
        UUID playerId = buf.readUUID();
        String rank = buf.readUtf(128);
        int rankColor = buf.readInt();
        String title = buf.readUtf(256);
        int titleColor = buf.readInt();
        String playerName = buf.readUtf(64);
        String body = buf.readUtf(256);
        long timestamp = buf.readLong();
        return new Packet_RichChatMessage(playerId, rank, rankColor, title, titleColor, playerName, body, timestamp);
    }

    public static void handle(Packet_RichChatMessage packet, IPayloadContext context) {
        context.enqueueWork(() -> ImmersiveChatManager.receivePlayerMessage(
                packet.playerId,
                packet.rank,
                packet.rankColor,
                packet.title,
                packet.titleColor,
                packet.playerName,
                packet.body,
                packet.timestamp));
    }
}
