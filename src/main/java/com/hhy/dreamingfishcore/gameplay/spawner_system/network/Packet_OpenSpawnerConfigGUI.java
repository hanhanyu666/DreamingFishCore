package com.hhy.dreamingfishcore.gameplay.spawner_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端让客户端打开刷怪箱配置界面。
 *
 * <p>样板照 {@code Packet_OpenRevivalCharmGUI}：客户端收到后直接 {@code setScreen}，
 * 界面内容从 {@code SpawnerConfigClientCache} 里取（快照在这之前已经下发）。</p>
 */
public record Packet_OpenSpawnerConfigGUI(BlockPos pos) implements CustomPacketPayload {

    public static final Type<Packet_OpenSpawnerConfigGUI> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "spawner/open_gui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_OpenSpawnerConfigGUI>
            STREAM_CODEC = StreamCodec.of(
                    Packet_OpenSpawnerConfigGUI::encode,
                    Packet_OpenSpawnerConfigGUI::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, Packet_OpenSpawnerConfigGUI packet) {
        buffer.writeBlockPos(packet.pos());
    }

    private static Packet_OpenSpawnerConfigGUI decode(RegistryFriendlyByteBuf buffer) {
        return new Packet_OpenSpawnerConfigGUI(buffer.readBlockPos());
    }

    public static void handle(Packet_OpenSpawnerConfigGUI packet, IPayloadContext context) {
        if (net.neoforged.fml.loading.FMLLoader.getDist().isClient()) {
            context.enqueueWork(() -> openClient(packet));
        }
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void openClient(Packet_OpenSpawnerConfigGUI packet) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.hhy.dreamingfishcore.gameplay.spawner_system.client.Screen_SpawnerConfig(
                        packet.pos()));
    }
}
