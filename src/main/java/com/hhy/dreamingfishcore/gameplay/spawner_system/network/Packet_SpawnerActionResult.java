package com.hhy.dreamingfishcore.gameplay.spawner_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端下发的配置修改结果。
 *
 * <p>成功时服务端会紧跟着补发一份快照，界面不需要自己拼接增量。</p>
 */
public record Packet_SpawnerActionResult(boolean success, String message)
        implements CustomPacketPayload {

    public static final Type<Packet_SpawnerActionResult> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "spawner/action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_SpawnerActionResult>
            STREAM_CODEC = StreamCodec.of(
                    Packet_SpawnerActionResult::encode,
                    Packet_SpawnerActionResult::decode);

    public Packet_SpawnerActionResult {
        message = message == null ? "" : message;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, Packet_SpawnerActionResult packet) {
        buffer.writeBoolean(packet.success());
        buffer.writeUtf(packet.message(), 512);
    }

    private static Packet_SpawnerActionResult decode(RegistryFriendlyByteBuf buffer) {
        return new Packet_SpawnerActionResult(buffer.readBoolean(), buffer.readUtf(512));
    }

    public static void handle(Packet_SpawnerActionResult packet, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(packet));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(Packet_SpawnerActionResult packet) {
        com.hhy.dreamingfishcore.gameplay.spawner_system.client.SpawnerConfigClientCache
                .setMessage(packet.success(), packet.message());
    }
}
