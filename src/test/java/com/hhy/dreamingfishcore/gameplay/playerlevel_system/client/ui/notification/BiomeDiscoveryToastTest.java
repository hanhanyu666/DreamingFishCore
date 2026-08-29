package com.hhy.dreamingfishcore.gameplay.playerlevel_system.client.ui.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BiomeDiscoveryToastTest {
    @Test
    void buildsStandardTranslationKeyForModdedBiome() {
        assertEquals("biome.biomeswevegone.skyris_vale",
                BiomeDiscoveryToast.biomeTranslationKey("biomeswevegone:skyris_vale"));
    }

    @Test
    void rejectsInvalidBiomeId() {
        assertNull(BiomeDiscoveryToast.biomeTranslationKey("not a biome id"));
    }
}
