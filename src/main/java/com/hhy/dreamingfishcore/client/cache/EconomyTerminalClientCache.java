package com.hhy.dreamingfishcore.client.cache;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/** Client-only snapshot of the EconomySystem data shown by the DreamingFish terminal. */
@OnlyIn(Dist.CLIENT)
public final class EconomyTerminalClientCache {
    private static Snapshot snapshot = Snapshot.empty();

    private EconomyTerminalClientCache() {
    }

    public static Snapshot get() {
        return snapshot;
    }

    public static void update(boolean available, boolean compatible, int balance, int ownedTerritoryCount,
                              String currentTerritoryName, String currentRelationship,
                              int salesOrderCount, int demandOrderCount, int ownOrderCount,
                              List<MarketOrderView> marketOrders, String statusText) {
        snapshot = new Snapshot(
                available,
                compatible,
                Math.max(0, balance),
                Math.max(0, ownedTerritoryCount),
                currentTerritoryName,
                currentRelationship,
                Math.max(0, salesOrderCount),
                Math.max(0, demandOrderCount),
                Math.max(0, ownOrderCount),
                marketOrders,
                statusText,
                System.currentTimeMillis());
    }

    public static void clear() {
        snapshot = Snapshot.empty();
    }

    public record MarketOrderView(
            String type,
            String itemId,
            int quantity,
            int totalPrice,
            String ownerName,
            long expirationTime) {
        public MarketOrderView {
            type = type == null ? "SALES" : type;
            itemId = itemId == null ? "" : itemId;
            ownerName = ownerName == null ? "" : ownerName;
            quantity = Math.max(0, quantity);
            totalPrice = Math.max(0, totalPrice);
            expirationTime = Math.max(0L, expirationTime);
        }
    }

    public record Snapshot(
            boolean available,
            boolean compatible,
            int balance,
            int ownedTerritoryCount,
            String currentTerritoryName,
            String currentRelationship,
            int salesOrderCount,
            int demandOrderCount,
            int ownOrderCount,
            List<MarketOrderView> marketOrders,
            String statusText,
            long updatedAt) {

        public Snapshot {
            currentTerritoryName = currentTerritoryName == null ? "" : currentTerritoryName;
            currentRelationship = currentRelationship == null ? "NONE" : currentRelationship;
            marketOrders = List.copyOf(marketOrders == null ? List.of() : marketOrders);
            statusText = statusText == null ? "" : statusText;
        }

        public static Snapshot empty() {
            return new Snapshot(false, false, 0, 0, "", "NONE", 0, 0, 0, List.of(), "", 0L);
        }

        public boolean loaded() {
            return updatedAt > 0L;
        }
    }
}
