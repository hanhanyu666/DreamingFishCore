package com.hhy.dreamingfishcore.gameplay.spawner_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.spawner_system.SpawnerService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端请求打开某台刷怪箱的配置界面。
 *
 * <p>客户端只说"我点了这个坐标"，能不能看、能看到什么全由服务端决定：
 * 服务端会重新校验那里确实是刷怪箱，再回一份快照并开屏。</p>
 */
public record Packet_SpawnerOpenRequest(BlockPos pos) implements CustomPacketPayload {

    public static final Type<Packet_SpawnerOpenRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "spawner/open_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_SpawnerOpenRequest> STREAM_CODEC =
            StreamCodec.of(Packet_SpawnerOpenRequest::encode, Packet_SpawnerOpenRequest::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, Packet_SpawnerOpenRequest packet) {
        buffer.writeBlockPos(packet.pos());
    }

    private static Packet_SpawnerOpenRequest decode(RegistryFriendlyByteBuf buffer) {
        return new Packet_SpawnerOpenRequest(buffer.readBlockPos());
    }

    public static void handle(Packet_SpawnerOpenRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SpawnerService.handleOpenRequest(player, packet.pos());
            }
        });
    }
}
