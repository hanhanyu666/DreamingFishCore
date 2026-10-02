package com.hhy.dreamingfishcore.gameplay.organization_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.block.DreamingFishCore_Blocks;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 聚居地过滤装置的绑定、维护与抑制判定（ADR 0017）。
 *
 * <p>三条规则：</p>
 * <ul>
 *   <li><b>绑定</b>：会长/管理员右键，且设备必须位于本组织已登记的领地内；</li>
 *   <li><b>维护</b>：每个维护周期（默认一个剧情活动日）从组织资金池扣一次维护费，
 *       扣不动就停机；位置校验不过同样停机；</li>
 *   <li><b>抑制</b>：工作中的设备覆盖范围内（水平半径，贯穿高度，与领地语义一致）的幸存者
 *       不会被不稳定感染者/传播复发者持续感染。不治疗既有感染，也不挡受伤、污染物与特殊袭击。</li>
 * </ul>
 */
public final class SettlementFilterService {

    /** 维护扫描的最低频率；周期本身远大于它，这里只是避免每 tick 遍历设备表。 */
    private static final int MAINTENANCE_SCAN_INTERVAL_TICKS = 200;

    private SettlementFilterService() {
    }

    // ==================== 交互：绑定 / 解绑 ====================

    /**
     * 玩家右键设备。
     *
     * @return 是否已处理（false 会让原版走其它交互分支）
     */
    public static boolean onInteract(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        String dimensionId = player.level().dimension().location().toString();
        SettlementFilterRegistry.Device device = SettlementFilterRegistry
                .find(dimensionId, pos.getX(), pos.getY(), pos.getZ())
                .orElse(null);
        if (device == null) {
            // 放置时没登记（命令放置、结构生成等）：补登记，让这台装置可用。
            SettlementFilterRegistry.register(dimensionId, pos.getX(), pos.getY(), pos.getZ());
            device = SettlementFilterRegistry
                    .find(dimensionId, pos.getX(), pos.getY(), pos.getZ())
                    .orElse(null);
            if (device == null) {
                player.displayClientMessage(
                        Component.literal("§c设备登记失败，请检查服务器日志。"), false);
                return false;
            }
        }

        Organization organization = OrganizationManager.findByPlayer(player.getUUID()).orElse(null);
        if (organization == null) {
            player.displayClientMessage(Component.literal("§c你还没有组织，无法绑定设备。"), false);
            return false;
        }
        if (!OrganizationPermissions.canManageTerritories(
                organization.rankOf(player.getUUID()).orElse(null))) {
            player.displayClientMessage(
                    Component.literal("§c只有会长与管理员可以绑定或解除设备。"), false);
            return false;
        }

        if (device.bound()) {
            if (!device.organizationId().equals(organization.id())) {
                player.displayClientMessage(
                        Component.literal("§c这台设备已经绑定给别的组织了。"), false);
                return false;
            }
            SettlementFilterRegistry.unbind(dimensionId, pos.getX(), pos.getY(), pos.getZ());
            setBlockActive(server, dimensionId, pos, false);
            player.displayClientMessage(Component.literal(
                    "§e已解除绑定：设备停止工作，维护费不再扣除。"), false);
            return true;
        }

        if (!OrganizationTerritoryService.covers(
                server, organization, dimensionId, pos.getX(), pos.getZ())) {
            player.displayClientMessage(Component.literal(
                    "§c设备必须放在本组织已登记的领地内才能绑定。"), false);
            return false;
        }
        int limit = OrganizationConfig.current().getMaxFilterDevices();
        if (SettlementFilterRegistry.countOf(organization.id()) >= limit) {
            player.displayClientMessage(Component.literal(
                    "§c每个组织最多绑定 " + limit + " 台设备。"), false);
            return false;
        }
        if (!SettlementFilterRegistry.bind(dimensionId, pos.getX(), pos.getY(), pos.getZ(),
                organization.id())) {
            player.displayClientMessage(Component.literal(
                    "§c绑定失败：设备数据处于只读保护，请重启服务器后重试。"), false);
            return false;
        }
        int cost = OrganizationConfig.current().getFilterMaintenanceCost();
        player.displayClientMessage(Component.literal(
                "§a已绑定到组织「" + organization.name() + "」；下个维护周期开始工作"
                        + (cost > 0 ? "（每周期消耗 " + cost + " 梦鱼币）" : "（当前不收费）") + "。"),
                false);
        DreamingFishCore.LOGGER.info("玩家 {} 为组织「{}」绑定了聚居地过滤装置 {}({},{},{})",
                player.getScoreboardName(), organization.name(), dimensionId,
                pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    // ==================== 维护周期 ====================

    /** 每服务器 tick 调用；内部按节流与维护周期决定要不要真的扫描。 */
    public static void tick(MinecraftServer server) {
        if (server == null || !SettlementFilterRegistry.isLoaded() || !OrganizationManager.isLoaded()) {
            return;
        }
        if (SettlementFilterRegistry.all().isEmpty()) {
            return;
        }
        if (server.getTickCount() % MAINTENANCE_SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        long activeTick = currentActiveTick();
        if (activeTick < 0L) {
            // 活动时钟不可用时不做维护判定：宁可让设备维持现状，也不要在这个周期里误判/误扣。
            return;
        }
        long interval = OrganizationConfig.current().getFilterMaintenanceIntervalTicks();
        int cost = OrganizationConfig.current().getFilterMaintenanceCost();

        for (SettlementFilterRegistry.Device device : List.copyOf(SettlementFilterRegistry.all())) {
            if (!maintenanceDue(device.lastMaintenanceActiveTick(), activeTick, interval)) {
                continue;
            }
            maintainDevice(server, device, activeTick, cost);
        }
    }

    /**
     * 这台设备现在该不该走一次维护。
     *
     * <p>{@code lastActiveTick < 0} 表示"还没排过期"（刚绑定或刚解绑），应当立刻维护一次
     * ——否则玩家会看到一台绑定后永远不工作的设备。</p>
     */
    static boolean maintenanceDue(long lastActiveTick, long activeTick, long intervalTicks) {
        if (activeTick < 0L) {
            return false;
        }
        if (lastActiveTick < 0L) {
            return true;
        }
        long span = intervalTicks > 0L ? intervalTicks : 1L;
        return activeTick - lastActiveTick >= span;
    }

    private static void maintainDevice(MinecraftServer server, SettlementFilterRegistry.Device device,
                                       long activeTick, int maintenanceCost) {
        String dimensionId = device.dimensionId();
        BlockPos pos = new BlockPos(device.x(), device.y(), device.z());
        ServerLevel level = resolveLevel(server, dimensionId);
        if (level == null) {
            return;
        }
        // 区块没加载就不判定：读不到方块不等于方块没了。
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(DreamingFishCore_Blocks.SETTLEMENT_FILTER.get())) {
            SettlementFilterRegistry.unregister(dimensionId, pos.getX(), pos.getY(), pos.getZ());
            DreamingFishCore.LOGGER.info("聚居地过滤装置 {}({},{},{}) 已不存在，摘除登记",
                    dimensionId, pos.getX(), pos.getY(), pos.getZ());
            return;
        }
        if (!device.bound()) {
            settle(server, device, pos, false, activeTick, null);
            return;
        }
        Organization organization = OrganizationManager.findById(device.organizationId()).orElse(null);
        if (organization == null) {
            // 组织解散后设备应停摆；解散时也会主动解绑，这里是兜底。
            SettlementFilterRegistry.unbind(dimensionId, pos.getX(), pos.getY(), pos.getZ());
            settle(server, device, pos, false, activeTick, null);
            return;
        }
        if (!OrganizationTerritoryService.covers(server, organization, dimensionId,
                pos.getX(), pos.getZ())) {
            settle(server, device, pos, false, activeTick,
                    "§e聚居地过滤装置停机：它已经不在组织「" + organization.name() + "」的领地内了。");
            return;
        }
        boolean paid = true;
        if (maintenanceCost > 0) {
            int taken = OrganizationManager.chargeMaintenance(organization.id(), maintenanceCost);
            paid = taken >= maintenanceCost;
        }
        settle(server, device, pos, paid, activeTick, paid
                ? null
                : "§c聚居地过滤装置停机：组织「" + organization.name() + "」资金池不足以支付维护费（"
                        + maintenanceCost + " 梦鱼币）。");
        if (paid) {
            DreamingFishCore.LOGGER.info("组织「{}」的聚居地过滤装置维护完成（扣费 {}，余额 {}）",
                    organization.name(), maintenanceCost, OrganizationManager.fundsOf(organization.id()));
        }
    }

    /** 落状态 + 更新方块显示 + 按需给在线成员提示。 */
    private static void settle(MinecraftServer server, SettlementFilterRegistry.Device device,
                               BlockPos pos, boolean active, long activeTick, String notice) {
        SettlementFilterRegistry.updateState(device.dimensionId(), pos.getX(), pos.getY(), pos.getZ(),
                active, activeTick);
        setBlockActive(server, device.dimensionId(), pos, active);
        if (notice != null) {
            notifyOrganization(server, device.organizationId(), notice);
        }
    }

    /** 把停机原因告诉在线的组织成员，避免玩家对着不工作的设备猜。 */
    private static void notifyOrganization(MinecraftServer server, String organizationId, String message) {
        if (server == null || organizationId == null || organizationId.isBlank()) {
            return;
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (OrganizationManager.findByPlayer(online.getUUID())
                    .map(organization -> organizationId.equals(organization.id()))
                    .orElse(false)) {
                online.displayClientMessage(Component.literal(message), false);
            }
        }
    }

    // ==================== 抑制判定 ====================

    /**
     * 玩家是否被工作中的设备覆盖。
     *
     * <p>水平半径 + 同维度、不限高度：与 EconomySystem 把领地当作贯穿全高的竖直柱保持一致的语义，
     * 避免"在同一块地的地下就不算被保护"这种反直觉结果。</p>
     */
    public static boolean isSuppressed(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        List<SettlementFilterRegistry.Device> active = SettlementFilterRegistry.activeDevices();
        if (active.isEmpty()) {
            return false;
        }
        String dimensionId = player.level().dimension().location().toString();
        int radius = OrganizationConfig.current().getFilterRadius();
        BlockPos pos = player.blockPosition();
        for (SettlementFilterRegistry.Device device : active) {
            if (!device.dimensionId().equals(dimensionId)) {
                continue;
            }
            if (withinRange(device.x() - pos.getX(), device.z() - pos.getZ(), radius)) {
                return true;
            }
        }
        return false;
    }

    /** 水平距离是否在半径内。纯函数，便于单测。 */
    static boolean withinRange(int deltaX, int deltaZ, int radius) {
        if (radius <= 0) {
            return false;
        }
        long dx = deltaX;
        long dz = deltaZ;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    // ==================== 工具 ====================

    private static void setBlockActive(MinecraftServer server, String dimensionId, BlockPos pos,
                                       boolean active) {
        ServerLevel level = resolveLevel(server, dimensionId);
        if (level == null || !level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(DreamingFishCore_Blocks.SETTLEMENT_FILTER.get())
                && state.getValue(com.hhy.dreamingfishcore.block.SettlementFilterBlock.ACTIVE) != active) {
            level.setBlock(pos,
                    state.setValue(com.hhy.dreamingfishcore.block.SettlementFilterBlock.ACTIVE, active),
                    3);
        }
    }

    /** 把存档里的维度字符串解析成服务端世界；解析失败返回 null。 */
    static ServerLevel resolveLevel(MinecraftServer server, String dimensionId) {
        if (server == null || dimensionId == null || dimensionId.isBlank()) {
            return null;
        }
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(dimensionId));
            return server.getLevel(key);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** 活动时钟（仅在线累计）；不可用时返回 -1，调用方必须据此跳过维护判定。 */
    static long currentActiveTick() {
        try {
            return StoryManager.getSnapshot().activeTicks();
        } catch (RuntimeException exception) {
            return -1L;
        }
    }
}
