package com.hhy.dreamingfishcore.gameplay.zombie_system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZombieSpawnPoolRewriterTest {
    @Test
    void fortySixtySplitPreservesTheOriginalWeight() {
        assertEquals(40, ZombieSpawnPoolRewriter.proportionalWeight(100, 40, 100));
        assertEquals(60, 100 - ZombieSpawnPoolRewriter.proportionalWeight(100, 40, 100));

        // The normal overworld zombie weight is 95; the split remains exact.
        int vanilla = ZombieSpawnPoolRewriter.proportionalWeight(95, 40, 100);
        assertEquals(38, vanilla);
        assertEquals(57, 95 - vanilla);

        // Desert templates are 19 (zombie) and 80 (husk).
        int desertZombie = ZombieSpawnPoolRewriter.proportionalWeight(19, 40, 100);
        int desertHusk = ZombieSpawnPoolRewriter.proportionalWeight(80, 40, 100);
        assertEquals(8, desertZombie);
        assertEquals(11, 19 - desertZombie);
        assertEquals(32, desertHusk);
        assertEquals(48, 80 - desertHusk);
    }

    @Test
    void familyAndOtherMultipliersUseTheOriginalWeightAsTheirBase() {
        int boostedFamily = ZombieSpawnPoolRewriter.scaledPercentWeight(100, 120);
        assertEquals(120, boostedFamily);
        assertEquals(48, ZombieSpawnPoolRewriter.proportionalWeight(boostedFamily, 40, 100));
        assertEquals(72, boostedFamily
                - ZombieSpawnPoolRewriter.proportionalWeight(boostedFamily, 40, 100));
        assertEquals(80, ZombieSpawnPoolRewriter.scaledPercentWeight(100, 80));

        // Rounding never changes the fact that the custom share is the
        // remainder of the scaled family weight.
        int scaled = ZombieSpawnPoolRewriter.scaledPercentWeight(19, 120);
        int vanilla = ZombieSpawnPoolRewriter.proportionalWeight(scaled, 40, 100);
        assertEquals(23, scaled);
        assertEquals(scaled, vanilla + (scaled - vanilla));
    }

    @Test
    void unchangedSettingsAreIdentitySafe() {
        assertEquals(100, ZombieSpawnPoolRewriter.scaledPercentWeight(100, 100));
        assertEquals(80, ZombieSpawnPoolRewriter.scaledPercentWeight(100, 80));
        assertEquals(120, ZombieSpawnPoolRewriter.scaledPercentWeight(100, 120));
    }

    @Test
    void legacyFactorsRemainAvailableForCompatibility() {
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
