package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.event;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfectionEventHandlerTest {

    @Test
    void protectiveMaskBlocksOnlySpreadingSources() {
        PlayerAttributesData unstable = new PlayerAttributesData();
        unstable.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);

        PlayerAttributesData stable = new PlayerAttributesData();
        stable.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);

        // 稳定感染者在正常状态下不产生接触暴露，因此"拦不拦"对它没有意义；
        // 传播复发的稳定感染者则和不稳定感染者一样会被面具拦下。
        PlayerAttributesData relapsing = new PlayerAttributesData();
        relapsing.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);
        relapsing.beginRelapse(6_000L);

        assertTrue(InfectionEventHandler.isBlockedByProtectiveMask(unstable, true));
        assertFalse(InfectionEventHandler.isBlockedByProtectiveMask(stable, true));
        assertTrue(InfectionEventHandler.isBlockedByProtectiveMask(relapsing, true));
        assertFalse(InfectionEventHandler.isBlockedByProtectiveMask(unstable, false));
        assertFalse(InfectionEventHandler.isBlockedByProtectiveMask(relapsing, false));
        assertFalse(InfectionEventHandler.isBlockedByProtectiveMask(null, true));
    }
}
