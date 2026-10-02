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
 * 客户端确认研究。
 *
 * <p>只报「我在哪张研究桌旁点了确认」：经验够不够、这批课题是否还有效、玩家离得远不远，
 * 全部由服务端重新判定。客户端不参与任何结算。</p>
 */
public record Packet_ResearchConfirmRequest(BlockPos pos) implements CustomPacketPayload {

    public static final Type<Packet_ResearchConfirmRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "research_table/confirm"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_ResearchConfirmRequest> STREAM_CODEC =
            StreamCodec.of(Packet_ResearchConfirmRequest::encode, Packet_ResearchConfirmRequest::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, Packet_ResearchConfirmRequest packet) {
        buffer.writeBlockPos(packet.pos());
    }

    private static Packet_ResearchConfirmRequest decode(RegistryFriendlyByteBuf buffer) {
        return new Packet_ResearchConfirmRequest(buffer.readBlockPos());
    }

    public static void handle(Packet_ResearchConfirmRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ResearchService.handleConfirm(player, packet.pos());
            }
        });
    }
}
