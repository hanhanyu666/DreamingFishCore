package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.event;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfectionEventHandlerTest {

    @Test
    void protectiveMaskBlocksOnlyLevelOneSources() {
        PlayerAttributesData levelOne = new PlayerAttributesData();
        levelOne.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);

        PlayerAttributesData levelTwo = new PlayerAttributesData();
        levelTwo.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);

        assertTrue(InfectionEventHandler.isBlockedByProtectiveMask(levelOne, true));
        assertFalse(InfectionEventHandler.isBlockedByProtectiveMask(levelTwo, true));
        assertFalse(InfectionEventHandler.isBlockedByProtectiveMask(levelOne, false));
        assertFalse(InfectionEventHandler.isBlockedByProtectiveMask(null, true));
    }
}
