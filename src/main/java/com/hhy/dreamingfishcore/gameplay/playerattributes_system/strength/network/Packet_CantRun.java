package com.hhy.dreamingfishcore.gameplay.playerattributes_system.strength.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.strength.PlayerStrengthManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public class Packet_CantRun implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_CantRun> TYPE = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.hhy.dreamingfishcore.DreamingFishCore.MODID, "playerattribute_system/strength_system/packet_cant_run"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_CantRun> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.of((buf, packet) -> Packet_CantRun.encode(packet, buf), Packet_CantRun::decode);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
    public Packet_CantRun() {}
    public static void encode(Packet_CantRun packet, FriendlyByteBuf buf) {}
    public static Packet_CantRun decode(FriendlyByteBuf buf) {
        return new Packet_CantRun();
    }

    public static void handle(Packet_CantRun packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // 设置客户端耗尽标记
            PlayerStrengthManager.ClientTickHandler.setClientStrengthExhausted(mc.player.getUUID(), true);

            if (mc.player.isSpectator()) return;

            // 强制停止客户端疾跑
            mc.player.setSprinting(false);

            // 显示提示消息
            mc.player.displayClientMessage(
                    Component.literal("§c老己~，跑不动啦歇会儿吧，休息就能恢复体力啦❤"),
                    true
            );
        });
    }
}
