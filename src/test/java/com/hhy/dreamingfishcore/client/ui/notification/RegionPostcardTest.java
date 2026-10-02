package com.hhy.dreamingfishcore.client.ui.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 群系没有通用标签时，按 id 或名称里的关键词选明信片场景。 */
class RegionPostcardTest {
    @Test
    void picksSceneFromModdedBiomeIds() {
        assertEquals(RegionPostcard.Scene.CHERRY, RegionPostcard.sceneForName("terralith:sakura_valley", null));
        assertEquals(RegionPostcard.Scene.VILLAGE, RegionPostcard.sceneForName("biomesoplenty:lavender_field", null));
        assertEquals(RegionPostcard.Scene.SWAMP, RegionPostcard.sceneForName("biome.minecraft.mangrove_swamp", null));
        assertEquals(RegionPostcard.Scene.MOUNTAINS, RegionPostcard.sceneForName("terralith:volcanic_peaks", null));
        assertEquals(RegionPostcard.Scene.CAVE, RegionPostcard.sceneForName("minecraft:dripstone_caves", null));
    }

    @Test
    void ordersOverlappingKeywords() {
        // 雪 + 山峰 → 山峰；雪 + 针叶林 → 雪原；冰冻海洋仍是海岸
        assertEquals(RegionPostcard.Scene.MOUNTAINS, RegionPostcard.sceneForName("minecraft:frozen_peaks", null));
        assertEquals(RegionPostcard.Scene.SNOWY, RegionPostcard.sceneForName("minecraft:snowy_taiga", null));
        assertEquals(RegionPostcard.Scene.COAST, RegionPostcard.sceneForName("minecraft:frozen_ocean", null));
        assertEquals(RegionPostcard.Scene.FOREST, RegionPostcard.sceneForName("biomesoplenty:seasonal_forest", null));
    }

    @Test
    void readsChineseNamesAndFallsBack() {
        assertEquals(RegionPostcard.Scene.CHERRY, RegionPostcard.sceneForName("樱花树林", null));
        assertEquals(RegionPostcard.Scene.DESERT, RegionPostcard.sceneForName("沙漠", null));
        assertNull(RegionPostcard.sceneForName("regions_unexplored:bayou", null));
    }
}
