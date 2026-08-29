package com.hhy.dreamingfishcore.server.notice_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.notice_system.client.NotificationClientDisplay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 服务端确认教程查看状态已经保存，客户端据此移除常驻提示。 */
public record Packet_NewPlayerGuideCompleted() implements CustomPacketPayload {
    public static final Type<Packet_NewPlayerGuideCompleted> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "notice_system/new_player_guide_completed"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_NewPlayerGuideCompleted>
            STREAM_CODEC = StreamCodec.of(Packet_NewPlayerGuideCompleted::encode, Packet_NewPlayerGuideCompleted::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_NewPlayerGuideCompleted packet) {
    }

    private static Packet_NewPlayerGuideCompleted decode(FriendlyByteBuf buffer) {
        return new Packet_NewPlayerGuideCompleted();
    }

    public static void handle(Packet_NewPlayerGuideCompleted packet, IPayloadContext context) {
        context.enqueueWork(Packet_NewPlayerGuideCompleted::handleClient);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient() {
        NotificationClientDisplay.dismissNewPlayerGuide();
    }
}
