package com.hhy.dreamingfishcore.gameplay.map_region_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/**
 * {@link MapRegion} 的坐标换算与命中判定。
 *
 * <p>重点钉住**负坐标**：方块 -1 属于区块 -1，用整除会算成 0（差一错误会让区域边界
 * 在地图上偏一格，而且只在负坐标区域出现——最难发现的那类 bug）。</p>
 */
class MapRegionTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse("minecraft:overworld"));

    @Test
    void positiveCoordinatesConvertToChunks() {
        MapRegion region = MapRegion.fromBlockBox("a", "甲", OVERWORLD, 0, 0, 31, 31,
                MapRegion.Kind.STORY);
        assertEquals(0, region.minChunkX());
        assertEquals(0, region.minChunkZ());
        assertEquals(1, region.maxChunkX());
        assertEquals(1, region.maxChunkZ());
        assertEquals(4, region.chunkCount());
    }

    @Test
    void negativeCoordinatesUseArithmeticShift() {
        // 方块 -1 属于区块 -1；除以 16 会得到 0（错误）
        MapRegion region = MapRegion.fromBlockBox("b", "乙", OVERWORLD, -1, -1, -1, -1,
                MapRegion.Kind.FRONTIER);
        assertEquals(-1, region.minChunkX());
        assertEquals(-1, region.minChunkZ());
        assertEquals(1, region.chunkCount());
    }

    @Test
    void reversedCornersAreNormalised() {
        MapRegion region = MapRegion.fromBlockBox("c", "丙", OVERWORLD, 31, 31, 0, 0,
                MapRegion.Kind.CLAIM);
        assertEquals(0, region.minChunkX());
        assertEquals(1, region.maxChunkX(), "反着传的角也要归一化成左小右大");
    }

    @Test
    void containsIsInclusiveOnBothEdges() {
        MapRegion region = MapRegion.fromBlockBox("d", "丁", OVERWORLD, 0, 0, 15, 15,
                MapRegion.Kind.STORY);
        assertTrue(region.contains(0, 0));
        assertFalse(region.contains(1, 0), "0~15 方块只有区块 0");
        assertTrue(region.contains(0, 0) && region.contains(0, 0) && region.contains(0, 0));
    }

    @Test
    void everyKindHasAColourAndName() {
        for (MapRegion.Kind kind : MapRegion.Kind.values()) {
            assertTrue(kind.displayName() != null && !kind.displayName().isBlank(),
                    kind + " 缺少显示名");
            assertTrue((kind.defaultColor() >>> 24) > 0, kind + " 的颜色应当带透明度");
        }
    }
}
