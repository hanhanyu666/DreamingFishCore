package com.hhy.dreamingfishcore.gameplay.research_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.research_system.ResearchService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端请求把研究桌的最新状态推过来（界面打开时发一次）。
 *
 * <p>为什么需要这条：界面里的"当前经验"和"能不能研究"是服务端算好下发的一份快照，
 * 而玩家可能在打开界面之前刚补过经验（或者服主刚改过配置）。没有这个请求，界面就会
 * 一直显示打开那一刻的旧数字，按钮灰着却不说为什么。</p>
 *
 * <p>它**不会重掷课题**：服务端只在缓存的课题已经失效时才会换一批，所以反复开关界面
 * 依然拿不到不同的随机结果。</p>
 */
public record Packet_ResearchTableOpenRequest(BlockPos pos) implements CustomPacketPayload {

    public static final Type<Packet_ResearchTableOpenRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "research_table/request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_ResearchTableOpenRequest> STREAM_CODEC =
            StreamCodec.of(Packet_ResearchTableOpenRequest::encode, Packet_ResearchTableOpenRequest::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, Packet_ResearchTableOpenRequest packet) {
        buffer.writeBlockPos(packet.pos());
    }

    private static Packet_ResearchTableOpenRequest decode(RegistryFriendlyByteBuf buffer) {
        return new Packet_ResearchTableOpenRequest(buffer.readBlockPos());
    }

    public static void handle(Packet_ResearchTableOpenRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ResearchService.onInteract(player, packet.pos());
            }
        });
    }
}
