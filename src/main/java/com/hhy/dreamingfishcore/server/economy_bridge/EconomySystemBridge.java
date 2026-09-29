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

    /** 一次账户变更的结果；刻意不使用 EconomySystem 的枚举，保持本类可独立加载。 */
    public enum MutationResult {
        /** 变更成功。 */
        SUCCESS,
        /** 经济服务未安装、版本不兼容，或调用过程出错 —— 调用方应据此**放弃收费**而不是判失败。 */
        NOT_AVAILABLE,
        /** 余额不足。 */
        INSUFFICIENT_FUNDS,
        /** 其它拒绝：金额非法、超出余额上限、持久化失败、来源串格式非法。 */
        REJECTED
    }

    /** 来源串必须匹配 {@code [a-z0-9_.-]+:[a-z0-9/._-]+}；调用方传子路径，这里统一加命名空间。 */
    public static final String SOURCE_NAMESPACE = "dreamingfishcore";

    /** 从玩家账户扣除梦鱼币。{@code amount <= 0} 视为非法，由调用方先行判断。 */
    public static MutationResult debit(ServerPlayer player, int amount, String sourcePath, String reason) {
        if (player == null || amount <= 0) {
            return MutationResult.REJECTED;
        }
        if (!ModList.get().isLoaded(MOD_ID)) {
            return MutationResult.NOT_AVAILABLE;
        }
        return ApiAccess.mutate(player, amount, sourcePath, reason, true);
    }

    /** 给玩家账户入账（用于退款）。 */
    public static MutationResult credit(ServerPlayer player, int amount, String sourcePath, String reason) {
        if (player == null || amount <= 0) {
            return MutationResult.REJECTED;
        }
        if (!ModList.get().isLoaded(MOD_ID)) {
            return MutationResult.NOT_AVAILABLE;
        }
        return ApiAccess.mutate(player, amount, sourcePath, reason, false);
    }

    /** 余额；经济服务不可用时返回 {@code -1}，便于调用方区分"余额为 0"与"读不到"。 */
    public static int balance(ServerPlayer player) {
        if (player == null || !ModList.get().isLoaded(MOD_ID)) {
            return -1;
        }
        return ApiAccess.balanceOf(player);
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

        private static MutationResult mutate(ServerPlayer player, int amount, String sourcePath,
                                             String reason, boolean debit) {
            try {
                if (!com.mo.economy_system.api.EconomySystemApi.isCompatibleMajor(REQUIRED_API_MAJOR)) {
                    return MutationResult.NOT_AVAILABLE;
                }
                com.mo.economy_system.api.EconomyApiSession session =
                        com.mo.economy_system.api.EconomySystemApi.forPlayer(player);
                if (!session.capabilities().accounts()) {
                    return MutationResult.NOT_AVAILABLE;
                }

                String source = sourceNamespace(sourcePath);
                if (source == null) {
                    // 来源串写错属于代码缺陷：绝不能退化成"扣款失败就当免费"。
                    DreamingFishCore.LOGGER.error("经济服务来源串非法，已拒绝本次账户变更：{}", sourcePath);
                    return MutationResult.REJECTED;
                }

                var accounts = session.accounts();
                var note = com.mo.economy_system.api.account.EconomyAccountApi.TransactionNote.of(
                        source, truncate(reason,
                                com.mo.economy_system.api.account.EconomyAccountApi.MAX_REASON_LENGTH));
                var status = debit
                        ? accounts.debit(player.getUUID(), amount, note)
                        : accounts.credit(player.getUUID(), amount, note);
                return switch (status) {
                    case SUCCESS -> MutationResult.SUCCESS;
                    case INSUFFICIENT_FUNDS -> MutationResult.INSUFFICIENT_FUNDS;
                    default -> MutationResult.REJECTED;
                };
            } catch (Throwable error) {
                DreamingFishCore.LOGGER.warn("调用 EconomySystem 账户 API 失败", error);
                return MutationResult.NOT_AVAILABLE;
            }
        }

        private static int balanceOf(ServerPlayer player) {
            try {
                if (!com.mo.economy_system.api.EconomySystemApi.isCompatibleMajor(REQUIRED_API_MAJOR)) {
                    return -1;
                }
                var session = com.mo.economy_system.api.EconomySystemApi.forPlayer(player);
                if (!session.capabilities().accounts()) {
                    return -1;
                }
                return session.accounts().balance(player.getUUID());
            } catch (Throwable error) {
                DreamingFishCore.LOGGER.warn("读取 EconomySystem 余额失败", error);
                return -1;
            }
        }

        /** 拼出符合 API 约定的来源串；不合法时返回 null。 */
        private static String sourceNamespace(String sourcePath) {
            String path = sourcePath == null ? "" : sourcePath.trim().toLowerCase(java.util.Locale.ROOT);
            String source = SOURCE_NAMESPACE + ":" + path;
            if (source.length() > com.mo.economy_system.api.account.EconomyAccountApi.MAX_SOURCE_LENGTH
                    || !com.mo.economy_system.api.account.EconomyAccountApi.SOURCE_PATTERN
                            .matcher(source).matches()) {
                return null;
            }
            return source;
        }

        private static String truncate(String text, int maxLength) {
            String safe = text == null ? "" : text;
            return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
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
