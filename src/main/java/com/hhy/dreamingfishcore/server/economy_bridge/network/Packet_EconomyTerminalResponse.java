package com.hhy.dreamingfishcore.server.economy_bridge.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.cache.EconomyTerminalClientCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Server-authoritative economy/territory/market snapshot for the DreamingFish terminal. */
public record Packet_EconomyTerminalResponse(
        boolean available,
        boolean compatible,
        int balance,
        int ownedTerritoryCount,
        String currentTerritoryName,
        String currentRelationship,
        int salesOrderCount,
        int demandOrderCount,
        int ownOrderCount,
        List<MarketOrderData> marketOrders,
        String statusText) implements CustomPacketPayload {

    private static final int MAX_MARKET_ORDERS = 24;

    public static final Type<Packet_EconomyTerminalResponse> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "economy_bridge/terminal_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, Packet_EconomyTerminalResponse> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> encode(packet, buffer),
                    Packet_EconomyTerminalResponse::decode);

    public Packet_EconomyTerminalResponse {
        currentTerritoryName = currentTerritoryName == null ? "" : currentTerritoryName;
        currentRelationship = currentRelationship == null ? "NONE" : currentRelationship;
        marketOrders = List.copyOf(marketOrders == null ? List.of() : marketOrders);
        statusText = statusText == null ? "" : statusText;
    }

    public record MarketOrderData(
            String type,
            String itemId,
            int quantity,
            int totalPrice,
            String ownerName,
            long expirationTime) {
        public MarketOrderData {
            type = type == null ? "SALES" : type;
            itemId = itemId == null ? "" : itemId;
            ownerName = ownerName == null ? "" : ownerName;
            quantity = Math.max(0, quantity);
            totalPrice = Math.max(0, totalPrice);
            expirationTime = Math.max(0L, expirationTime);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(Packet_EconomyTerminalResponse packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.available);
        buffer.writeBoolean(packet.compatible);
        buffer.writeVarInt(Math.max(0, packet.balance));
        buffer.writeVarInt(Math.max(0, packet.ownedTerritoryCount));
        buffer.writeUtf(packet.currentTerritoryName, 128);
        buffer.writeUtf(packet.currentRelationship, 32);
        buffer.writeVarInt(Math.max(0, packet.salesOrderCount));
        buffer.writeVarInt(Math.max(0, packet.demandOrderCount));
        buffer.writeVarInt(Math.max(0, packet.ownOrderCount));

        int orderCount = Math.min(MAX_MARKET_ORDERS, packet.marketOrders.size());
        buffer.writeVarInt(orderCount);
        for (int i = 0; i < orderCount; i++) {
            MarketOrderData order = packet.marketOrders.get(i);
            buffer.writeUtf(order.type, 16);
            buffer.writeUtf(order.itemId, 128);
            buffer.writeVarInt(order.quantity);
            buffer.writeVarInt(order.totalPrice);
            buffer.writeUtf(order.ownerName, 64);
            buffer.writeLong(order.expirationTime);
        }
        buffer.writeUtf(packet.statusText, 192);
    }

    private static Packet_EconomyTerminalResponse decode(FriendlyByteBuf buffer) {
        boolean available = buffer.readBoolean();
        boolean compatible = buffer.readBoolean();
        int balance = buffer.readVarInt();
        int territoryCount = buffer.readVarInt();
        String territoryName = buffer.readUtf(128);
        String relationship = buffer.readUtf(32);
        int salesCount = buffer.readVarInt();
        int demandCount = buffer.readVarInt();
        int ownCount = buffer.readVarInt();
        int orderCount = Math.min(MAX_MARKET_ORDERS, Math.max(0, buffer.readVarInt()));
        List<MarketOrderData> orders = new ArrayList<>(orderCount);
        for (int i = 0; i < orderCount; i++) {
            orders.add(new MarketOrderData(
                    buffer.readUtf(16),
                    buffer.readUtf(128),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readUtf(64),
                    buffer.readLong()));
        }
        String statusText = buffer.readUtf(192);
        return new Packet_EconomyTerminalResponse(
                available,
                compatible,
                balance,
                territoryCount,
                territoryName,
                relationship,
                salesCount,
                demandCount,
                ownCount,
                orders,
                statusText);
    }

    public static void handle(Packet_EconomyTerminalResponse packet, IPayloadContext context) {
        context.enqueueWork(() -> EconomyTerminalClientCache.update(
                packet.available,
                packet.compatible,
                packet.balance,
                packet.ownedTerritoryCount,
                packet.currentTerritoryName,
                packet.currentRelationship,
                packet.salesOrderCount,
                packet.demandOrderCount,
                packet.ownOrderCount,
                packet.marketOrders.stream()
                        .map(order -> new EconomyTerminalClientCache.MarketOrderView(
                                order.type,
                                order.itemId,
                                order.quantity,
                                order.totalPrice,
                                order.ownerName,
                                order.expirationTime))
                        .toList(),
                packet.statusText));
    }
}
