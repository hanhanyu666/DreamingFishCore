package com.hhy.dreamingfishcore.server.economy_bridge;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.List;

/**
 * Optional server-side bridge to EconomySystem Public API v1.
 *
 * <p>The outer bridge deliberately has no EconomySystem types in its public signatures. This keeps
 * DreamingFishCore loadable when EconomySystem is absent. Direct API references are isolated in
 * {@link ApiAccess}, which is only loaded after the mod-presence check succeeds.</p>
 */
public final class EconomySystemBridge {
    public static final String MOD_ID = "economy_system";
    public static final int REQUIRED_API_MAJOR = 1;
    private static final int MAX_MARKET_PREVIEW_ORDERS = 24;

    private EconomySystemBridge() {
    }

    public static EconomySummary query(ServerPlayer player) {
        if (player == null) {
            return EconomySummary.unavailable("经济服务暂不可用");
        }
        if (!ModList.get().isLoaded(MOD_ID)) {
            return EconomySummary.unavailable("经济服务未启用");
        }
        return ApiAccess.query(player);
    }

    public record MarketOrderSummary(
            String type,
            String itemId,
            int quantity,
            int totalPrice,
            String ownerName,
            long expirationTime) {
        public MarketOrderSummary {
            type = type == null ? "SALES" : type;
            itemId = itemId == null ? "" : itemId;
            ownerName = ownerName == null ? "" : ownerName;
            quantity = Math.max(0, quantity);
            totalPrice = Math.max(0, totalPrice);
            expirationTime = Math.max(0L, expirationTime);
        }
    }

    public record EconomySummary(
            boolean available,
            boolean compatible,
            int balance,
            int ownedTerritoryCount,
            String currentTerritoryName,
            String currentRelationship,
            int salesOrderCount,
            int demandOrderCount,
            int ownOrderCount,
            List<MarketOrderSummary> marketOrders,
            String statusText) {

        public EconomySummary {
            currentTerritoryName = currentTerritoryName == null ? "" : currentTerritoryName;
            currentRelationship = currentRelationship == null ? "NONE" : currentRelationship;
            marketOrders = List.copyOf(marketOrders == null ? List.of() : marketOrders);
            statusText = statusText == null ? "" : statusText;
        }

        public static EconomySummary unavailable(String statusText) {
            return new EconomySummary(false, false, 0, 0, "", "NONE", 0, 0, 0, List.of(), statusText);
        }

        public static EconomySummary incompatible(String statusText) {
            return new EconomySummary(true, false, 0, 0, "", "NONE", 0, 0, 0, List.of(), statusText);
        }
    }

    /** Loaded only when EconomySystem is actually installed. */
    private static final class ApiAccess {
        private ApiAccess() {
        }

        private static EconomySummary query(ServerPlayer player) {
            try {
                if (!com.mo.economy_system.api.EconomySystemApi.isCompatibleMajor(REQUIRED_API_MAJOR)) {
                    return EconomySummary.incompatible("经济服务版本不兼容");
                }

                com.mo.economy_system.api.EconomyApiSession api =
                        com.mo.economy_system.api.EconomySystemApi.forPlayer(player);
                var accounts = api.accounts();
                var territories = api.territories();
                var market = api.market();

                int balance = accounts.balance(player.getUUID());
                int ownedTerritoryCount = territories.territoriesByOwner(player.getUUID()).size();

                var pos = player.blockPosition();
                var currentTerritory = territories.territoryAt(pos.getX(), pos.getY(), pos.getZ());
                String territoryName = currentTerritory.map(view -> view.name()).orElse("");
                String relationship = currentTerritory
                        .map(view -> territories.relationship(view.territoryId(), player.getUUID()).name())
                        .orElse("NONE");

                long now = System.currentTimeMillis();
                var activeOrders = market.orders().stream()
                        .filter(order -> !order.delivered() && !order.expired(now))
                        .sorted(java.util.Comparator.comparingLong(
                                com.mo.economy_system.api.market.EconomyMarketApi.OrderView::listingTime).reversed())
                        .toList();

                int salesCount = (int) activeOrders.stream()
                        .filter(order -> order.type() == com.mo.economy_system.api.market.EconomyMarketApi.OrderType.SALES)
                        .count();
                int demandCount = (int) activeOrders.stream()
                        .filter(order -> order.type() == com.mo.economy_system.api.market.EconomyMarketApi.OrderType.DEMAND)
                        .count();
                int ownCount = (int) activeOrders.stream()
                        .filter(order -> player.getUUID().equals(order.ownerId()))
                        .count();

                List<MarketOrderSummary> marketPreview = activeOrders.stream()
                        .limit(MAX_MARKET_PREVIEW_ORDERS)
                        .map(order -> new MarketOrderSummary(
                                order.type().name(),
                                order.itemId(),
                                order.quantity(),
                                order.totalPrice(),
                                order.ownerName(),
                                order.expirationTime()))
                        .toList();

                return new EconomySummary(
                        true,
                        true,
                        balance,
                        ownedTerritoryCount,
                        territoryName,
                        relationship,
                        salesCount,
                        demandCount,
                        ownCount,
                        marketPreview,
                        "");
            } catch (Throwable error) {
                DreamingFishCore.LOGGER.warn("读取 EconomySystem Public API 失败", error);
                return EconomySummary.unavailable("经济数据暂时不可用");
            }
        }
    }
}
