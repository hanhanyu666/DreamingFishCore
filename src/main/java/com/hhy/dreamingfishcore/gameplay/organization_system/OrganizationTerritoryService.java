package com.hhy.dreamingfishcore.gameplay.organization_system;

import com.hhy.dreamingfishcore.server.economy_bridge.EconomySystemBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 组织领地的只读查询与展示数据装配。
 *
 * <p>为什么写操作不在这里：登记/移除/失效摘除都是"改写组织事实"，与成员、职位、公告同级，
 * 因此统一留在 {@link OrganizationManager}（它同时负责写盘与只读保护）。本类只做查询，
 * 供终端页面、命令与聚居地抑制设备使用。</p>
 *
 * <p>领地的真值始终在 EconomySystem：这里读到的 {@code info == null} 只表示"这块地现在读不到"，
 * 调用方必须先用 {@link EconomySystemBridge#isTerritoryReadable} 区分"经济服务不可用"与
 * "领地真的没了"，否则会把组织领地成批误判为失效。</p>
 */
public final class OrganizationTerritoryService {
    private OrganizationTerritoryService() {
    }

    /**
     * 一条已登记的领地：登记时间 + 当前从 EconomySystem 读到的信息（读不到时为 null）。
     *
     * <p>无论能否读到，{@code territoryId} 都要带出来：否则"已失效"的登记在界面上会丢掉 id，
     * 玩家就再也删不掉它，只能找服主用命令清。</p>
     */
    public record LinkedTerritory(String territoryId, long registeredAt,
                                  EconomySystemBridge.TerritoryInfo info) {
        /** 读不到对应领地（被移除，或经济服务不可用）。 */
        public boolean missing() {
            return info == null;
        }
    }

    /** 终端「组织 → 领地」分区所需的数据。 */
    public record Listing(boolean economyReadable,
                          List<LinkedTerritory> linked,
                          List<EconomySystemBridge.TerritoryInfo> available,
                          int limit) {
        public Listing {
            linked = List.copyOf(linked == null ? List.of() : linked);
            available = List.copyOf(available == null ? List.of() : available);
            limit = Math.max(0, limit);
        }
    }

    /** 组装某个玩家所在组织的领地视图；没有组织或经济服务不可用时返回空列表而不是报错。 */
    public static Listing listing(ServerPlayer actor) {
        if (actor == null) {
            return new Listing(false, List.of(), List.of(), 0);
        }
        Organization organization = OrganizationManager.findByPlayer(actor.getUUID()).orElse(null);
        int limit = OrganizationConfig.current().getMaxRegisteredTerritories();
        if (organization == null) {
            return new Listing(EconomySystemBridge.isTerritoryReadable(actor.getServer()),
                    List.of(), List.of(), limit);
        }

        MinecraftServer server = actor.getServer();
        boolean readable = EconomySystemBridge.isTerritoryReadable(server);

        List<LinkedTerritory> linked = new ArrayList<>();
        for (var entry : organization.territoryIds().entrySet()) {
            EconomySystemBridge.TerritoryInfo info = readable
                    ? EconomySystemBridge.findTerritory(server, entry.getKey()).orElse(null)
                    : null;
            linked.add(new LinkedTerritory(entry.getKey(),
                    entry.getValue() == null ? 0L : entry.getValue(), info));
        }

        List<EconomySystemBridge.TerritoryInfo> available = readable
                ? selectAvailable(
                        EconomySystemBridge.territoriesOwnedBy(server, actor.getUUID()),
                        claimedTerritoryIds())
                : List.of();
        linked.sort(java.util.Comparator.comparingLong(LinkedTerritory::registeredAt));
        available.sort(java.util.Comparator.comparing(EconomySystemBridge.TerritoryInfo::name));
        return new Listing(readable, linked, available, limit);
    }

    /** 组织登记的领地 id（只读副本）。 */
    public static Set<String> registeredIds(Organization organization) {
        return organization == null ? Set.of() : Set.copyOf(organization.territoryIds().keySet());
    }

    /**
     * 组织的某块已登记领地是否覆盖给定水平坐标。
     *
     * <p>只看 X/Z 并与领地自己的维度比较：EconomySystem 的领地是一根贯穿全高的竖直柱
     * （docs/TASK_LOCATION_SYSTEM.md）。这个方法只在维护周期等低频路径上调用，
     * 不参与每 tick 的判定。</p>
     */
    public static boolean covers(MinecraftServer server, Organization organization,
                                 String dimensionId, int x, int z) {
        if (organization == null || server == null
                || !EconomySystemBridge.isTerritoryReadable(server)) {
            return false;
        }
        for (String id : organization.territoryIds().keySet()) {
            EconomySystemBridge.TerritoryInfo info =
                    EconomySystemBridge.findTerritory(server, id).orElse(null);
            if (coversPosition(info, dimensionId, x, z)) {
                return true;
            }
        }
        return false;
    }

    /** 当前世界里所有被任何组织登记过的领地 id —— 用于把"已被别人登记"的领地排除出可选列表。 */
    private static Set<String> claimedTerritoryIds() {
        Set<String> claimed = new LinkedHashSet<>();
        for (Organization organization : OrganizationManager.all()) {
            claimed.addAll(organization.territoryIds().keySet());
        }
        return claimed;
    }

    /**
     * 可登记的领地 = 自己名下 − 已被任何组织登记。
     *
     * <p>返回<b>可变</b>列表：调用方（终端装配）会按名称排序。
     * 用 {@code Stream.toList()} 会得到不可变列表，排序时直接抛
     * {@code UnsupportedOperationException} —— 这个坑被 gametest 抓到过一次。</p>
     */
    static List<EconomySystemBridge.TerritoryInfo> selectAvailable(
            List<EconomySystemBridge.TerritoryInfo> owned, Set<String> claimed) {
        if (owned == null || owned.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> taken = claimed == null ? Set.of() : claimed;
        List<EconomySystemBridge.TerritoryInfo> available = new ArrayList<>();
        for (EconomySystemBridge.TerritoryInfo info : owned) {
            if (info != null && !taken.contains(info.territoryId())) {
                available.add(info);
            }
        }
        return available;
    }

    /** 坐标/维度判定。纯函数，便于单测。 */
    static boolean coversPosition(EconomySystemBridge.TerritoryInfo info,
                                  String dimensionId, int x, int z) {
        if (info == null) {
            return false;
        }
        String dimension = dimensionId == null ? "" : dimensionId;
        return info.dimensionId().equals(dimension) && info.covers(x, z);
    }
}
