package com.hhy.dreamingfishcore.gameplay.storybook_system.network;

import com.hhy.dreamingfishcore.gameplay.storybook_system.StoryBookDataManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class Packet_UpdateStoryBookOrder implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_UpdateStoryBookOrder> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "storybook_system/packet_update_story_book_order"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_UpdateStoryBookOrder> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_UpdateStoryBookOrder.encode(packet, buf), Packet_UpdateStoryBookOrder::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
    private final List<String> orderedClueIds;

    public Packet_UpdateStoryBookOrder(List<String> orderedClueIds) {
        this.orderedClueIds = orderedClueIds;
    }

    public static void encode(Packet_UpdateStoryBookOrder packet, FriendlyByteBuf buf) {
        if (packet == null || packet.orderedClueIds == null
                || packet.orderedClueIds.size()
                > StoryBookDataManager.MAX_NETWORK_ORDER_ENTRIES) {
            throw new IllegalArgumentException("随记本排序条目超过上限");
        }
        buf.writeVarInt(packet.orderedClueIds.size());
        for (String clueId : packet.orderedClueIds) {
            buf.writeUtf(clueId, 256);
        }
    }

    public static Packet_UpdateStoryBookOrder decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > StoryBookDataManager.MAX_NETWORK_ORDER_ENTRIES) {
            throw new IllegalArgumentException("随记本排序条目数量非法：" + size);
        }
        List<String> orderedIds = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            orderedIds.add(buf.readUtf(256));
        }
        return new Packet_UpdateStoryBookOrder(orderedIds);
    }

    public static void handle(Packet_UpdateStoryBookOrder packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player != null) {
                StoryBookDataManager.updateClueOrderForPlayer(player.getUUID(), packet.orderedClueIds);
            }
        });
    }
}
