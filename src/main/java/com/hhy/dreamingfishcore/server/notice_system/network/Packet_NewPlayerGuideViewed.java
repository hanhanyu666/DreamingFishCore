package com.hhy.dreamingfishcore.server.notice_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.notice_system.NoticeDeliveryService;
import com.hhy.dreamingfishcore.server.notice_system.event.NewPlayerGuide;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端在新玩家帮助页真正显示后提交查看回执。 */
public record Packet_NewPlayerGuideViewed() implements CustomPacketPayload {
    public static final Type<Packet_NewPlayerGuideViewed> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "notice_system/new_player_guide_viewed"));
    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_NewPlayerGuideViewed>
            STREAM_CODEC = StreamCodec.of(Packet_NewPlayerGuideViewed::encode, Packet_NewPlayerGuideViewed::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_NewPlayerGuideViewed packet) {
    }

    private static Packet_NewPlayerGuideViewed decode(FriendlyByteBuf buffer) {
        return new Packet_NewPlayerGuideViewed();
    }

    public static void handle(Packet_NewPlayerGuideViewed packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            NewPlayerGuide.ViewResult result = NewPlayerGuide.markViewed(player);
            if (result == NewPlayerGuide.ViewResult.DATA_MISSING) {
                return;
            }

            // 先移除客户端常驻教程提示，再处理首次完成后的公告投递。
            DreamingFishCore_NetworkManager.sendToClient(
                    new Packet_NewPlayerGuideCompleted(), player);
            if (result == NewPlayerGuide.ViewResult.COMPLETED_NOW) {
                NoticeDeliveryService.deliverPendingGameAfterTutorial(player);
            }
        });
    }
}
