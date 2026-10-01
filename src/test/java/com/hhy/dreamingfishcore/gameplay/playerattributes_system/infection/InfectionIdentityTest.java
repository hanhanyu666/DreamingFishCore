package com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection;

import com.hhy.dreamingfishcore.gameplay.playerattributes_system.PlayerAttributesData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfectionIdentityTest {

    @Test
    void survivorIsTheDefaultForMissingOrCleanData() {
        assertEquals(InfectionIdentity.SURVIVOR, InfectionIdentity.of(null));
        assertEquals(InfectionIdentity.SURVIVOR, InfectionIdentity.of(new PlayerAttributesData()));
    }

    @Test
    void levelOneIsUnstableAndLevelTwoIsStable() {
        PlayerAttributesData unstable = new PlayerAttributesData();
        unstable.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);

        PlayerAttributesData stable = new PlayerAttributesData();
        stable.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);

        assertEquals(InfectionIdentity.UNSTABLE, InfectionIdentity.of(unstable));
        assertEquals(InfectionIdentity.STABLE, InfectionIdentity.of(stable));
        assertEquals(unstable.getInfectionIdentity(), InfectionIdentity.of(unstable));
    }

    @Test
    void relapseIsTemporaryStateOfAStableInfected() {
        PlayerAttributesData relapsing = new PlayerAttributesData();
        relapsing.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_TWO);
        assertTrue(relapsing.beginRelapse(6_000L));

        assertEquals(InfectionIdentity.RELAPSE, InfectionIdentity.of(relapsing));
        assertTrue(relapsing.isRelapsing());
        assertFalse(relapsing.isStableInfected());

        relapsing.endRelapse();
        assertEquals(InfectionIdentity.STABLE, InfectionIdentity.of(relapsing));
        assertTrue(relapsing.isStableInfected());
    }

    @Test
    void relapseWindowCannotBeOpenedForAnUnstableInfected() {
        PlayerAttributesData unstable = new PlayerAttributesData();
        unstable.setInfectionLevel(PlayerAttributesData.INFECTION_LEVEL_ONE);

        assertFalse(unstable.beginRelapse(6_000L));
        assertEquals(InfectionIdentity.UNSTABLE, InfectionIdentity.of(unstable));
    }

    @Test
    void displayNamesMatchTheGlossary() {
        assertEquals("幸存者", InfectionIdentity.SURVIVOR.displayName());
        assertEquals("不稳定感染者", InfectionIdentity.UNSTABLE.displayName());
        assertEquals("稳定感染者", InfectionIdentity.STABLE.displayName());
        assertEquals("传播复发", InfectionIdentity.RELAPSE.displayName());
    }

    @Test
    void stabilizedCoversStableAndRelapseOnly() {
        assertFalse(InfectionIdentity.SURVIVOR.isStabilized());
        assertFalse(InfectionIdentity.UNSTABLE.isStabilized());
        assertTrue(InfectionIdentity.STABLE.isStabilized());
        assertTrue(InfectionIdentity.RELAPSE.isStabilized());
    }
}
