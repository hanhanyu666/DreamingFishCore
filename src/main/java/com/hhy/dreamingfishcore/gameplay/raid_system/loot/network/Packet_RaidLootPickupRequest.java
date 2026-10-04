package com.hhy.dreamingfishcore.gameplay.raid_system.loot.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.raid_system.loot.LooseLootService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：右键请求拾取某个露天物品节点。
 *
 * <p>只带锚点 id —— 玩家离多远、物品是什么、有没有被拿过，全部由服务端重新判定
 * （{@link LooseLootService#requestPickup}）。客户端说什么都不被信任。</p>
 */
public record Packet_RaidLootPickupRequest(String anchorId) implements CustomPacketPayload {

    public static final Type<Packet_RaidLootPickupRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "raid/loose_loot_pickup"));

    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_RaidLootPickupRequest> STREAM_CODEC =
            StreamCodec.of(Packet_RaidLootPickupRequest::encode, Packet_RaidLootPickupRequest::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buffer, Packet_RaidLootPickupRequest packet) {
        buffer.writeUtf(packet.anchorId());
    }

    private static Packet_RaidLootPickupRequest decode(FriendlyByteBuf buffer) {
        return new Packet_RaidLootPickupRequest(buffer.readUtf());
    }

    public static void handle(Packet_RaidLootPickupRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                String reply = LooseLootService.requestPickup(player.getServer(), player, packet.anchorId());
                if (!reply.isEmpty()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(reply), true);
                }
            }
        });
    }
}
