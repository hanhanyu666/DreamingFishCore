package com.hhy.dreamingfishcore.gameplay.map_region_system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hhy.dreamingfishcore.gameplay.map_region_system.client.MapRegionClientCache;
import com.hhy.dreamingfishcore.gameplay.map_region_system.network.Packet_MapRegionSnapshot;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 客户端区域缓存的查询与混合色。
 *
 * <p>要钉住的是"重叠时不能靠运气"：剧情地点常常落在领地里面，
 * 如果实现成"后匹配的盖掉前面的"，显示颜色就取决于服务端返回顺序 ✗。
 * 这里要求**取平均**，结果是确定的。</p>
 */
class MapRegionClientCacheTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:the_nether"));

    @AfterEach
    void clear() {
        MapRegionClientCache.clear();
    }

    @Test
    void singleRegionShowsItsOwnColour() {
        MapRegion region = new MapRegion("a", "甲地", OVERWORLD, 0, 0, 1, 1, 0xFF336699,
                MapRegion.Kind.CLAIM);
        MapRegionClientCache.accept(new Packet_MapRegionSnapshot(List.of(region)));

        assertEquals(0xFF336699, MapRegionClientCache.colorAt(OVERWORLD, 0, 0));
        assertTrue(MapRegionClientCache.tooltipAt(OVERWORLD, 0, 0).contains("甲地"));
        assertTrue(MapRegionClientCache.tooltipAt(OVERWORLD, 0, 0).contains("领地"));
    }

    @Test
    void overlappingRegionsAreAveraged() {
        MapRegion claim = new MapRegion("a", "甲地", OVERWORLD, 0, 0, 1, 1, 0xFF0000FF,
                MapRegion.Kind.CLAIM);
        MapRegion story = new MapRegion("b", "乙剧情", OVERWORLD, 0, 0, 1, 1, 0xFFFF0000,
                MapRegion.Kind.STORY);
        MapRegionClientCache.accept(new Packet_MapRegionSnapshot(List.of(claim, story)));

        int blended = MapRegionClientCache.colorAt(OVERWORLD, 0, 0);
        assertEquals(0xFF, (blended >>> 24) & 0xFF, "透明度取平均后应仍是 FF");
        assertEquals(0x7F, (blended >>> 16) & 0xFF, "红通道取平均（(0+255)/2 取整 = 0x7F）");
        assertEquals(0x7F, blended & 0xFF, "蓝通道取平均（(255+0)/2 取整 = 0x7F）");
        assertTrue(MapRegionClientCache.tooltipAt(OVERWORLD, 0, 0).contains("、"),
                "重叠时提示里应同时出现两块区域");
    }

    @Test
    void otherDimensionIsNotAffected() {
        MapRegion region = new MapRegion("a", "甲地", OVERWORLD, 0, 0, 1, 1, 0xFF112233,
                MapRegion.Kind.STORY);
        MapRegionClientCache.accept(new Packet_MapRegionSnapshot(List.of(region)));

        assertEquals(0, MapRegionClientCache.colorAt(NETHER, 0, 0), "别的维度不该被高亮");
        assertEquals("", MapRegionClientCache.tooltipAt(NETHER, 0, 0));
    }

    @Test
    void emptySnapshotClearsEverything() {
        MapRegion region = new MapRegion("a", "甲地", OVERWORLD, 0, 0, 1, 1, 0xFF112233,
                MapRegion.Kind.STORY);
        MapRegionClientCache.accept(new Packet_MapRegionSnapshot(List.of(region)));
        assertFalse(MapRegionClientCache.isEmpty());

        MapRegionClientCache.accept(new Packet_MapRegionSnapshot(List.of()));
        assertTrue(MapRegionClientCache.isEmpty());
        assertEquals(0, MapRegionClientCache.colorAt(OVERWORLD, 0, 0));
    }
}
