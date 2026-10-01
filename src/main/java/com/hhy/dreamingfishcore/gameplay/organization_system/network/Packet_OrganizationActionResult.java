package com.hhy.dreamingfishcore.gameplay.organization_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 服务端对一次组织操作的执行结果（只用于给玩家回话，不作为可信状态）。 */
public record Packet_OrganizationActionResult(boolean success, String message)
        implements CustomPacketPayload {

    public static final Type<Packet_OrganizationActionResult> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    DreamingFishCore.MODID, "organization/action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_OrganizationActionResult>
            STREAM_CODEC = StreamCodec.of(
                    Packet_OrganizationActionResult::encode,
                    Packet_OrganizationActionResult::decode);

    public Packet_OrganizationActionResult {
        message = message == null ? "" : message;
    }

    public static Packet_OrganizationActionResult of(OrganizationResult result) {
        return new Packet_OrganizationActionResult(result.success(), result.message());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buffer,
                               Packet_OrganizationActionResult packet) {
        buffer.writeBoolean(packet.success());
        buffer.writeUtf(packet.message(), 512);
    }

    private static Packet_OrganizationActionResult decode(RegistryFriendlyByteBuf buffer) {
        return new Packet_OrganizationActionResult(buffer.readBoolean(), buffer.readUtf(512));
    }

    public static void handle(Packet_OrganizationActionResult packet, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(packet));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(Packet_OrganizationActionResult packet) {
        com.hhy.dreamingfishcore.gameplay.organization_system.client.cache.OrganizationClientCache
                .setLastResult(packet.success(), packet.message());
        net.minecraft.client.Minecraft.getInstance().player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                        (packet.success() ? "§a[组织] §f" : "§c[组织] §f") + packet.message()));
    }
}
