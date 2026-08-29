package com.hhy.dreamingfishcore.gameplay.zombie_system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZombieSpawnPoolRewriterTest {
    @Test
    void fiftyNinetyAndEightyPercentReduceToExactIntegerFactors() {
        ZombieSpawnPoolRewriter.WeightFactors factors =
                ZombieSpawnPoolRewriter.weightFactors(50, 90, 80);

        assertEquals(5, factors.vanillaZombie());
        assertEquals(9, factors.customZombie());
        assertEquals(8, factors.otherMonster());
        assertEquals(10, factors.commonDivisor());

        int originalZombieWeight = 95;
        int vanilla = ZombieSpawnPoolRewriter.scaledWeight(
                originalZombieWeight, factors.vanillaZombie());
        int custom = ZombieSpawnPoolRewriter.scaledWeight(
                originalZombieWeight, factors.customZombie());
        int other = ZombieSpawnPoolRewriter.scaledWeight(
                100, factors.otherMonster());

        assertEquals(475, vanilla);
        assertEquals(855, custom);
        assertEquals(1330, vanilla + custom);
        assertEquals(800, other);
        // Exact 9:5 relationship without floating-point or rounding.
        assertEquals(custom * 5, vanilla * 9);
    }

    @Test
    void arbitraryPercentagesKeepTheirExactRelativeScale() {
        ZombieSpawnPoolRewriter.WeightFactors factors =
                ZombieSpawnPoolRewriter.weightFactors(33, 67, 80);

        assertEquals(33, factors.vanillaZombie());
        assertEquals(67, factors.customZombie());
        assertEquals(80, factors.otherMonster());
        assertEquals(1, factors.commonDivisor());
    }

    @Test
    void zeroPercentProducesNoWeightForThatCategory() {
        ZombieSpawnPoolRewriter.WeightFactors factors =
                ZombieSpawnPoolRewriter.weightFactors(0, 90, 80);

        assertEquals(0, factors.vanillaZombie());
        assertEquals(9, factors.customZombie());
        assertEquals(8, factors.otherMonster());
        assertEquals(0, ZombieSpawnPoolRewriter.scaledWeight(95, factors.vanillaZombie()));
    }

}
