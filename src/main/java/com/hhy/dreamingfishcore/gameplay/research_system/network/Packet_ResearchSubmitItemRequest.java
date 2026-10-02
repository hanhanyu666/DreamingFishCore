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
 * 客户端点了「解锁这个配方」。
 *
 * <p>只报「我在哪张研究桌旁点了提交」：槽里放的是什么、放了多少、够不够、能不能解锁，
 * 全部由服务端读自己那份 {@code ResearchTableMenu} 重新判定——客户端甚至不需要把这件物品
 * 告诉服务端，避免了"伪造物品 / 伪造数量"这一类作弊面。</p>
 */
public record Packet_ResearchSubmitItemRequest(BlockPos pos) implements CustomPacketPayload {

    public static final Type<Packet_ResearchSubmitItemRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "research_table/submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_ResearchSubmitItemRequest> STREAM_CODEC =
            StreamCodec.of(Packet_ResearchSubmitItemRequest::encode, Packet_ResearchSubmitItemRequest::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, Packet_ResearchSubmitItemRequest packet) {
        buffer.writeBlockPos(packet.pos());
    }

    private static Packet_ResearchSubmitItemRequest decode(RegistryFriendlyByteBuf buffer) {
        return new Packet_ResearchSubmitItemRequest(buffer.readBlockPos());
    }

    public static void handle(Packet_ResearchSubmitItemRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ResearchService.handleSubmit(player, packet.pos());
            }
        });
    }
}
