package com.hhy.dreamingfishcore.gameplay.zombie_system.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZombieDigSpeedTest {
    @Test
    void emptyHandMatchesVanillaHarvestableAndWrongToolDivisors() {
        // Planks: hardness 2, harvestable by hand -> 2 * 30 = 60 ticks.
        assertEquals(60, ZombieDigSpeed.requiredTicks(2.0F, 1.0F, true));
        // Stone: hardness 1.5, empty hand is not a correct tool -> 1.5 * 100.
        assertEquals(150, ZombieDigSpeed.requiredTicks(1.5F, 1.0F, false));
    }

    @Test
    void futurePickaxeOnlyAcceleratesItsEffectiveMaterial() {
        // Representative iron-pickaxe speed on stone: ceil(1.5 * 30 / 6).
        assertEquals(8, ZombieDigSpeed.requiredTicks(1.5F, 6.0F, true));
        // A pickaxe reports baseline speed 1 against planks, so wood is unchanged.
        assertEquals(60, ZombieDigSpeed.requiredTicks(2.0F, 1.0F, true));
    }

    @Test
    void zeroHardnessStillBreaksOnTheFirstTick() {
        assertEquals(1, ZombieDigSpeed.requiredTicks(0.0F, 1.0F, true));
    }
}
