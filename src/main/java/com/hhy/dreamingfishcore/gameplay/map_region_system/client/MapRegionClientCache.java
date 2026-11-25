package com.hhy.dreamingfishcore.gameplay.map_region_system.client;

import com.hhy.dreamingfishcore.gameplay.map_region_system.MapRegion;
import com.hhy.dreamingfishcore.gameplay.map_region_system.network.Packet_MapRegionSnapshot;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 客户端手里的区域快照（只读）。
 *
 * <p>查询按 (维度, chunkX, chunkZ) 做点测试；**重叠时颜色取平均**，
 * 这样"剧情地点在领地里面"这种情况会显示成混合色，而不是后注册的盖住前面的
 * （谁先谁后取决于服务端返回顺序，那种"看运气"的显示是不能接受的）。</p>
 */
public final class MapRegionClientCache {

    private static volatile List<MapRegion> regions = List.of();

    private MapRegionClientCache() {
    }

    public static void accept(Packet_MapRegionSnapshot snapshot) {
        regions = snapshot == null ? List.of() : snapshot.regions();
    }

    public static List<MapRegion> regions() {
        return regions;
    }

    public static boolean isEmpty() {
        return regions.isEmpty();
    }

    /** 覆盖该区块的区域（可能多个）。 */
    public static List<MapRegion> regionsAt(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return regions.stream()
                .filter(region -> region.dimension().equals(dimension))
                .filter(region -> region.contains(chunkX, chunkZ))
                .toList();
    }

    /**
     * 该区块的显示颜色；没有区域返回 0。
     *
     * <p>多个区域重叠时对 ARGB 各通道取平均（透明度也平均），得到"混合色"。</p>
     */
    public static int colorAt(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        List<MapRegion> hits = regionsAt(dimension, chunkX, chunkZ);
        if (hits.isEmpty()) {
            return 0;
        }
        long a = 0;
        long r = 0;
        long g = 0;
        long b = 0;
        for (MapRegion region : hits) {
            int argb = region.argb();
            a += (argb >>> 24) & 0xFF;
            r += (argb >>> 16) & 0xFF;
            g += (argb >>> 8) & 0xFF;
            b += argb & 0xFF;
        }
        int count = hits.size();
        return ((int) (a / count) << 24)
                | ((int) (r / count) << 16)
                | ((int) (g / count) << 8)
                | (int) (b / count);
    }

    /** 该区块的悬停提示：区域名与类型，重叠时用 "、" 连接。 */
    public static String tooltipAt(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        List<MapRegion> hits = regionsAt(dimension, chunkX, chunkZ);
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (MapRegion region : hits) {
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(region.name()).append('（').append(region.kind().displayName()).append('）');
        }
        return builder.toString();
    }

    public static void clear() {
        regions = List.of();
    }
}
