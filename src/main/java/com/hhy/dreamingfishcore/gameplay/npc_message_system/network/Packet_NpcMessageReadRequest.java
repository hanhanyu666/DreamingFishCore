package com.hhy.dreamingfishcore.gameplay.npc_message_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 玩家打开一个 NPC 会话后请求将其中已收到的消息标记为已读。 */
public record Packet_NpcMessageReadRequest(int npcId) implements CustomPacketPayload {
    public static final Type<Packet_NpcMessageReadRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "npc_message/read_request"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_NpcMessageReadRequest>
            STREAM_CODEC = StreamCodec.of(Packet_NpcMessageReadRequest::encode, Packet_NpcMessageReadRequest::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_NpcMessageReadRequest packet) {
        buffer.writeVarInt(packet.npcId);
    }

    private static Packet_NpcMessageReadRequest decode(FriendlyByteBuf buffer) {
        return new Packet_NpcMessageReadRequest(buffer.readVarInt());
    }

    public static void handle(Packet_NpcMessageReadRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && packet.npcId > 0) {
                if (!NpcMessageManager.markConversationRead(player, packet.npcId)) {
                    NpcMessageManager.syncToClient(player);
                }
            }
        });
    }
}
