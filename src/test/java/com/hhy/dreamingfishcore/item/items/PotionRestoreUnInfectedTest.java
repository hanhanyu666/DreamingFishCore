package com.hhy.dreamingfishcore.item.items;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionRestoreUnInfectedTest {
    @Test
    void reagentCanClearNoninfectedAndCureOnlyLevelOne() {
        assertTrue(Potion_RestoreUnInfected.canUseForInfectionLevel(
                PlayerAttributesData.INFECTION_LEVEL_NONE));
        assertTrue(Potion_RestoreUnInfected.canUseForInfectionLevel(
                PlayerAttributesData.INFECTION_LEVEL_ONE));
        assertFalse(Potion_RestoreUnInfected.canUseForInfectionLevel(
                PlayerAttributesData.INFECTION_LEVEL_TWO));
    }
}
