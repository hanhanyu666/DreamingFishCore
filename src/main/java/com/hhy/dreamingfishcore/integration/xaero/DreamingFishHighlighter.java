package com.hhy.dreamingfishcore.integration.xaero;

import com.hhy.dreamingfishcore.gameplay.map_region_system.client.MapRegionClientCache;
import com.hhy.dreamingfishcore.gameplay.map_region_system.MapRegion;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.ChunkHighlighter;

/**
 * 把本模组的区域画到 Xaero 地图上的高亮器。
 *
 * <p>Xaero 对每个区块问三件事：要不要高亮、什么颜色、悬停显示什么。这个类就是回答这三件事。</p>
 *
 * <p><b>为什么返回长度 4 的颜色数组</b>：{@code ChunkHighlighter} 内部会对相邻区块做
 * 边缘混色（{@code getSideBlend}），按四角给色最稳；即使某个版本只读第 0 个，
 * 结果也一样，不会数组越界。</p>
 */
public final class DreamingFishHighlighter extends ChunkHighlighter {

    public DreamingFishHighlighter() {
        super(false);
    }

    private static boolean loggedFirstQuery;

    @Override
    protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        int argb = MapRegionClientCache.colorAt(dimension, chunkX, chunkZ);
        if (!loggedFirstQuery) {
            loggedFirstQuery = true;
            com.hhy.dreamingfishcore.DreamingFishCore.LOGGER.info(
                    "[map] 高亮器已被 Xaero 询问（首查 {} {} {} → 颜色 {}，缓存 {} 个区域）",
                    dimension.location(), chunkX, chunkZ, Integer.toHexString(argb),
                    MapRegionClientCache.regions().size());
        }
        if (argb == 0) {
            return null;
        }
        return new int[]{argb, argb, argb, argb, argb};   // 字节码索引到 4，给够
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return MapRegionClientCache.colorAt(dimension, chunkX, chunkZ) != 0;
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        // 区域（512×512 区块）级别：只要该大区域里有任意一块命中即可
        int baseX = regionX << 5;    // Xaero 的 region = 512 方块 = 32 区块
        int baseZ = regionZ << 5;
        for (int offsetX = 0; offsetX < 32; offsetX++) {
            for (int offsetZ = 0; offsetZ < 32; offsetZ++) {
                if (MapRegionClientCache.colorAt(dimension, baseX + offsetX, baseZ + offsetZ) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        int baseX = regionX << 5;
        int baseZ = regionZ << 5;
        int hash = 17;
        for (int offsetX = 0; offsetX < 32; offsetX++) {
            for (int offsetZ = 0; offsetZ < 32; offsetZ++) {
                int color = MapRegionClientCache.colorAt(dimension, baseX + offsetX, baseZ + offsetZ);
                if (color != 0) {
                    hash = hash * 31 + color;
                }
            }
        }
        return hash;
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return tooltip(dimension, chunkX, chunkZ);
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return tooltip(dimension, chunkX, chunkZ);
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> tooltips, ResourceKey<Level> dimension,
                                                 int blockX, int blockZ, int maxWidth) {
        Component text = tooltip(dimension, blockX >> 4, blockZ >> 4);
        if (text != null) {
            tooltips.add(text);
        }
    }

    private static Component tooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        String text = MapRegionClientCache.tooltipAt(dimension, chunkX, chunkZ);
        if (text == null || text.isBlank()) {
            return null;
        }
        List<MapRegion> hits = MapRegionClientCache.regionsAt(dimension, chunkX, chunkZ);
        int color = hits.isEmpty() ? 0xFFFFFF : hits.get(0).argb() & 0xFFFFFF;
        return Component.literal("§l" + text).withStyle(style -> style.withColor(color));
    }
}
