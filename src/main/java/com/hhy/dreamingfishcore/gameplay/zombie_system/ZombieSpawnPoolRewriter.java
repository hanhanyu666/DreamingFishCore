package com.hhy.dreamingfishcore.gameplay.zombie_system;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reweights the natural monster pool while keeping the vanilla mob cap intact.
 *
 * <p>Each vanilla zombie-family entry is scaled by the configured zombie
 * family multiplier and then split between the vanilla family member and the
 * custom siege zombie. Every other monster entry is scaled independently by
 * the configured other-monster multiplier. The event handler replaces the
 * affected entries; it never adds a second copy of the original entries.</p>
 *
 * <p>The weighted list controls which entity an existing spawn attempt picks.
 * The number of attempts and the {@code MONSTER} cap remain controlled by
 * vanilla/ServerCore.</p>
 */
public final class ZombieSpawnPoolRewriter {
    /** Stable entries required by NaturalSpawner.canSpawnMobAt(). */
    private static final Map<SpawnEntryKey, MobSpawnSettings.SpawnerData> STABLE_ENTRIES = new HashMap<>();

    private ZombieSpawnPoolRewriter() {
    }

    /**
     * Rewrites one monster pool.
     *
     * @param zombieFamilyPercent total zombie-family weight relative to the
     *                             original zombie-family entry (120 means +20%)
     * @param vanillaZombiePercent share of the scaled zombie-family weight
     *                             assigned to the vanilla member
     * @param customZombiePercent share of the scaled zombie-family weight
     *                            assigned to the custom member
     * @param otherMonsterPercent weight multiplier for every other entry
     *                            (80 means -20%)
     */
    public static List<MobSpawnSettings.SpawnerData> rewrite(
            List<MobSpawnSettings.SpawnerData> original,
            EntityType<?> customZombieType,
            int zombieFamilyPercent,
            int vanillaZombiePercent,
            int customZombiePercent,
            int otherMonsterPercent) {
        if (original == null || original.isEmpty() || customZombieType == null) {
            return original;
        }

        long zombieShareTotal = (long) Math.max(0, vanillaZombiePercent)
                + Math.max(0, customZombiePercent);
        if (zombieShareTotal <= 0L) {
            return original;
        }

        boolean hasZombieTemplate = false;
        for (MobSpawnSettings.SpawnerData entry : original) {
            if (entry == null || entry.type == null) {
                continue;
            }
            if (entry.type == customZombieType) {
                // A datapack or another integration already supplied the
                // custom entity. Do not create a competing second entry.
                return original;
            }
            if (isZombieTemplate(entry.type)) {
                hasZombieTemplate = true;
            }
        }
        if (!hasZombieTemplate) {
            // Do not alter dimensions/biomes that do not have a vanilla
            // zombie-family spawn template (for example the End).
            return original;
        }

        List<MobSpawnSettings.SpawnerData> rewritten = new ArrayList<>(original.size() + 2);
        boolean changed = false;
        for (MobSpawnSettings.SpawnerData entry : original) {
            if (entry == null || entry.type == null) {
                rewritten.add(entry);
                continue;
            }

            int baseWeight = entry.getWeight().asInt();
            if (isZombieTemplate(entry.type)) {
                if (baseWeight <= 0) {
                    rewritten.add(entry);
                    continue;
                }

                int scaledZombieWeight = scaledPercentWeight(baseWeight, zombieFamilyPercent);
                if (scaledZombieWeight <= 0) {
                    // An explicit zero multiplier removes this template; the
                    // rest of the monster pool remains available.
                    changed = true;
                    continue;
                }

                int vanillaWeight = proportionalWeight(
                        scaledZombieWeight,
                        Math.max(0, vanillaZombiePercent),
                        zombieShareTotal);
                int customWeight = scaledZombieWeight - vanillaWeight;

                // Preserve the original object when the requested settings
                // happen to be a no-op (100% family, 100% vanilla, 0% custom).
                if (scaledZombieWeight == baseWeight && customWeight == 0) {
                    rewritten.add(entry);
                    continue;
                }

                if (vanillaWeight > 0) {
                    rewritten.add(stableEntry(
                            entry.type, vanillaWeight, entry.minCount, entry.maxCount));
                }
                if (customWeight > 0) {
                    rewritten.add(stableEntry(
                            customZombieType, customWeight, entry.minCount, entry.maxCount));
                }
                changed = true;
                continue;
            }

            // Non-zombie entries are reweighted independently. With the
            // default 80 this turns a weight of 100 into 80; unlike the old
            // implementation, no extra copy of the original is retained.
            if (baseWeight <= 0) {
                rewritten.add(entry);
                continue;
            }
            int scaledOtherWeight = scaledPercentWeight(baseWeight, otherMonsterPercent);
            if (scaledOtherWeight == baseWeight) {
                rewritten.add(entry);
            } else {
                if (scaledOtherWeight > 0) {
                    rewritten.add(stableEntry(
                            entry.type, scaledOtherWeight, entry.minCount, entry.maxCount));
                }
                changed = true;
            }
        }

        return changed ? List.copyOf(rewritten) : original;
    }

    /**
     * Previous four-argument form: only split the zombie family and leave all
     * other entries at their original weights.
     */
    public static List<MobSpawnSettings.SpawnerData> rewrite(
            List<MobSpawnSettings.SpawnerData> original,
            EntityType<?> customZombieType,
            int vanillaZombiePercent,
            int customZombiePercent) {
        return rewrite(
                original,
                customZombieType,
                100,
                vanillaZombiePercent,
                customZombiePercent,
                100);
    }

    /**
     * Compatibility overload for the previous three-category signature. The
     * old third category is now used as the other-monster multiplier while the
     * zombie family itself remains at its original total weight.
     */
    @Deprecated
    public static List<MobSpawnSettings.SpawnerData> rewrite(
            List<MobSpawnSettings.SpawnerData> original,
            EntityType<?> customZombieType,
            int vanillaZombiePercent,
            int customZombiePercent,
            int otherMonsterPercent) {
        return rewrite(
                original,
                customZombieType,
                100,
                vanillaZombiePercent,
                customZombiePercent,
                otherMonsterPercent);
    }

    /**
     * Legacy name retained for source compatibility. It returns entries that
     * differ from the original pool; new code should use the six-argument
     * {@link #rewrite(List, EntityType, int, int, int, int)} form.
     */
    @Deprecated
    public static List<MobSpawnSettings.SpawnerData> additions(
            List<MobSpawnSettings.SpawnerData> original,
            EntityType<?> customZombieType,
            int vanillaZombiePercent,
            int customZombiePercent,
            int otherMonsterPercent) {
        List<MobSpawnSettings.SpawnerData> rewritten = rewrite(
                original, customZombieType, vanillaZombiePercent, customZombiePercent, otherMonsterPercent);
        if (rewritten == original) {
            return List.of();
        }
        List<MobSpawnSettings.SpawnerData> differences = new ArrayList<>();
        for (MobSpawnSettings.SpawnerData entry : rewritten) {
            if (original == null || !original.contains(entry)) {
                differences.add(entry);
            }
        }
        return List.copyOf(differences);
    }

    /** Vanilla uses HUSK as the zombie-family overworld entry in deserts. */
    public static boolean isZombieTemplate(EntityType<?> type) {
        return type == EntityType.ZOMBIE || type == EntityType.HUSK;
    }

    /** Clears stable potential-spawn entries when a server lifecycle ends. */
    public static synchronized void clearCache() {
        STABLE_ENTRIES.clear();
    }

    /** Scales a vanilla integer weight by a percentage, rounding to nearest. */
    static int scaledPercentWeight(int baseWeight, int percent) {
        if (baseWeight <= 0 || percent <= 0) {
            return 0;
        }
        long rounded = ((long) baseWeight * percent + 50L) / 100L;
        return (int) Math.min(Integer.MAX_VALUE, rounded);
    }

    /** Returns the nearest integer share of a weight without exceeding it. */
    static int proportionalWeight(int baseWeight, int numerator, long denominator) {
        if (baseWeight <= 0 || numerator <= 0 || denominator <= 0L) {
            return 0;
        }
        if (numerator >= denominator) {
            return baseWeight;
        }
        long rounded = ((long) baseWeight * numerator + denominator / 2L) / denominator;
        return (int) Math.max(0L, Math.min((long) baseWeight, rounded));
    }

    private static synchronized MobSpawnSettings.SpawnerData stableEntry(
            EntityType<?> type, int weight, int minCount, int maxCount) {
        SpawnEntryKey key = new SpawnEntryKey(type, weight, minCount, maxCount);
        return STABLE_ENTRIES.computeIfAbsent(
                key,
                ignored -> new MobSpawnSettings.SpawnerData(type, weight, minCount, maxCount));
    }

    /** Kept for compatibility with older pure tests/integrations. */
    @Deprecated
    static WeightFactors weightFactors(
            int vanillaZombiePercent,
            int customZombiePercent,
            int otherMonsterPercent) {
        int divisor = greatestCommonDivisor(
                vanillaZombiePercent,
                customZombiePercent,
                otherMonsterPercent);
        return new WeightFactors(
                vanillaZombiePercent / divisor,
                customZombiePercent / divisor,
                otherMonsterPercent / divisor,
                divisor);
    }

    /** Kept for compatibility with the previous pure weight helper. */
    @Deprecated
    static int scaledWeight(int baseWeight, int factor) {
        if (baseWeight <= 0 || factor <= 0) {
            return 0;
        }
        long scaled = (long) baseWeight * factor;
        return (int) Math.min(Integer.MAX_VALUE, scaled);
    }

    private static int greatestCommonDivisor(int first, int second, int third) {
        int divisor = gcd(Math.abs(first), Math.abs(second));
        divisor = gcd(divisor, Math.abs(third));
        return Math.max(1, divisor);
    }

    private static int gcd(int left, int right) {
        while (right != 0) {
            int remainder = left % right;
            left = right;
            right = remainder;
        }
        return left;
    }

    @Deprecated
    record WeightFactors(
            int vanillaZombie,
            int customZombie,
            int otherMonster,
            int commonDivisor) {
    }

    private record SpawnEntryKey(
            EntityType<?> type,
            int weight,
            int minCount,
            int maxCount) {
    }
}
