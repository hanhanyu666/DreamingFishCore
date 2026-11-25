package com.hhy.dreamingfishcore.gameplay.map_region_system;

import com.hhy.dreamingfishcore.gameplay.organization_system.Organization;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationManager;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationDefinition;
import com.hhy.dreamingfishcore.gameplay.task_location_system.TaskLocationManager;
import com.hhy.dreamingfishcore.server.economy_bridge.EconomySystemBridge;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * 把模组里"值得画在地图上"的东西汇总成 {@link MapRegion}。
 *
 * <p>数据源：</p>
 * <ul>
 *   <li><b>任务地点</b>（{@link TaskLocationManager}）→ 剧情地区；带"尸潮区"标记的 → 外缘；</li>
 *   <li><b>组织领地</b>（{@link OrganizationManager} 存引用 + {@link EconomySystemBridge} 读范围）→ 领地。</li>
 * </ul>
 *
 * <p>领地读取是**只读**的（经济系统那边负责所有权与持久化）；读取能力不可用时安静跳过，
 * 不影响剧情地区照常显示。</p>
 */
public final class MapRegionCollector {

    private MapRegionCollector() {
    }

    /** 只汇总剧情地区/外缘（不需要服务器实例，测试与无经济系统时用）。 */
    public static List<MapRegion> collectStoryRegions() {
        List<MapRegion> regions = new ArrayList<>();
        for (TaskLocationDefinition location : TaskLocationManager.getAllLocations()) {
            if (!location.isEnabled()) {
                continue;
            }
            ResourceKey<Level> dimension = dimensionOf(location.getDimension());
            if (dimension == null) {
                continue;
            }
            MapRegion.Kind kind = location.isHorde() ? MapRegion.Kind.FRONTIER : MapRegion.Kind.STORY;
            regions.add(MapRegion.fromBlockBox(
                    location.getId(), location.getName(), dimension,
                    location.getMin().getX(), location.getMin().getZ(),
                    location.getMax().getX(), location.getMax().getZ(), kind));
        }
        return regions;
    }

    /** 汇总全部区域（服务端用）。 */
    public static List<MapRegion> collect(MinecraftServer server) {
        List<MapRegion> regions = new ArrayList<>(collectStoryRegions());
        if (server == null || !EconomySystemBridge.isTerritoryReadable(server)) {
            return regions;
        }
        for (Organization organization : OrganizationManager.all()) {
            for (String territoryId : organization.territoryIds().keySet()) {
                EconomySystemBridge.findTerritory(server, territoryId).ifPresent(info -> {
                    ResourceKey<Level> dimension = dimensionOf(info.dimensionId());
                    if (dimension == null) {
                        return;
                    }
                    String name = info.name().isBlank()
                            ? organization.name() : info.name();
                    regions.add(MapRegion.fromBlockBox(
                            info.territoryId(), name, dimension,
                            info.minX(), info.minZ(), info.maxX(), info.maxZ(),
                            MapRegion.Kind.CLAIM));
                });
            }
        }
        return regions;
    }

    /** 兼容旧调用（无服务器时只出剧情地区）。 */
    public static List<MapRegion> collect() {
        return collectStoryRegions();
    }

    /** 统计文本（调试命令用）。 */
    public static String describe(MinecraftServer server) {
        List<MapRegion> regions = collect(server);
        StringBuilder builder = new StringBuilder();
        builder.append("区域总数：").append(regions.size()).append('\n');
        for (MapRegion.Kind kind : MapRegion.Kind.values()) {
            long count = regions.stream().filter(region -> region.kind() == kind).count();
            int chunks = regions.stream().filter(region -> region.kind() == kind)
                    .mapToInt(MapRegion::chunkCount).sum();
            builder.append("  ").append(kind.displayName()).append("：")
                    .append(count).append(" 个，共 ").append(chunks).append(" 区块\n");
        }
        if (server != null && !EconomySystemBridge.isTerritoryReadable(server)) {
            builder.append("  §7（经济系统不可用：领地部分读不到，只显示剧情地区与外围）\n");
        }
        regions.stream().limit(10).forEach(region ->
                builder.append("    · ").append(region.name())
                        .append("  [").append(region.minChunkX()).append(',').append(region.minChunkZ())
                        .append(" → ").append(region.maxChunkX()).append(',').append(region.maxChunkZ())
                        .append("] ").append(region.dimension().location()).append('\n'));
        if (regions.size() > 10) {
            builder.append("    …（还有 ").append(regions.size() - 10).append(" 个）\n");
        }
        return builder.toString();
    }

    private static ResourceKey<Level> dimensionOf(String raw) {
        ResourceLocation id = raw == null ? null : ResourceLocation.tryParse(raw);
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }
}
