package com.hhy.dreamingfishcore.server.economy_bridge.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.economy_bridge.EconomySystemBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Requests the economy/territory/market snapshot displayed by the DreamingFish terminal. */
public final class Packet_EconomyTerminalRequest implements CustomPacketPayload {
    public static final Type<Packet_EconomyTerminalRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "economy_bridge/terminal_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_EconomyTerminalRequest> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> encode(packet, buffer),
                    Packet_EconomyTerminalRequest::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(Packet_EconomyTerminalRequest packet, FriendlyByteBuf buffer) {
        // No request parameters. The server always answers for the requesting player.
    }

    private static Packet_EconomyTerminalRequest decode(FriendlyByteBuf buffer) {
        return new Packet_EconomyTerminalRequest();
    }

    public static void handle(Packet_EconomyTerminalRequest packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            EconomySystemBridge.EconomySummary summary = EconomySystemBridge.query(player);
            DreamingFishCore_NetworkManager.sendToClient(
                    new Packet_EconomyTerminalResponse(
                            summary.available(),
                            summary.compatible(),
                            summary.balance(),
                            summary.ownedTerritoryCount(),
                            summary.currentTerritoryName(),
                            summary.currentRelationship(),
                            summary.salesOrderCount(),
                            summary.demandOrderCount(),
                            summary.ownOrderCount(),
                            summary.marketOrders().stream()
                                    .map(order -> new Packet_EconomyTerminalResponse.MarketOrderData(
                                            order.type(),
                                            order.itemId(),
                                            order.quantity(),
                                            order.totalPrice(),
                                            order.ownerName(),
                                            order.expirationTime()))
                                    .toList(),
                            summary.statusText()),
                    player);
        });
    }
}
