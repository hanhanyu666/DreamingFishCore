package com.hhy.dreamingfishcore.gameplay.zombie_system;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds safe additions for the configured natural monster proportions.
 *
 * <p>The potential-spawns event is shared by vanilla, datapacks and several
 * server optimisation mods.  Replacing its list in-place is unsafe: one
 * incompatible entry (or one failed conversion) can turn the whole pool into
 * an empty list and stop every hostile mob from spawning.  This helper therefore
 * never removes or mutates an existing entry.  It only returns extra entries;
 * the caller appends them to the original list.</p>
 */
public final class ZombieSpawnPoolRewriter {
    private ZombieSpawnPoolRewriter() {
    }

    /**
     * Returns only the entries that should be appended to one monster pool.
     * Existing entries are deliberately left untouched.  The common-divisor
     * factors retain the old ratio semantics while turning the old replacement
     * operation into an additive operation: with 50/90/80, an original zombie
     * weight {@code W} remains in the pool, then receives {@code 4W} extra
     * vanilla weight and {@code 9W} custom weight; every other entry receives
     * {@code 7W} extra weight.  Thus the effective weights are still 5/9/8,
     * but the original list is always available as a fail-safe.
     *
     * <p>If a category is configured as zero, its original entries cannot be
     * removed by this safe additive path and are consequently retained.  This
     * is intentional: preserving a working vanilla spawn pool is safer than
     * interpreting a malformed/edge configuration by deleting it.</p>
     */
    public static List<MobSpawnSettings.SpawnerData> additions(
            List<MobSpawnSettings.SpawnerData> original,
            EntityType<?> customZombieType,
            int vanillaZombiePercent,
            int customZombiePercent,
            int otherMonsterPercent) {
        if (original == null || original.isEmpty() || customZombieType == null) {
            return List.of();
        }

        WeightFactors factors = weightFactors(
                vanillaZombiePercent,
                customZombiePercent,
                otherMonsterPercent);

        boolean hasVanillaZombie = false;
        boolean alreadyContainsCustomZombie = false;
        for (MobSpawnSettings.SpawnerData entry : original) {
            if (entry == null || entry.type == null) {
                continue;
            }
            if (entry.type == customZombieType) {
                alreadyContainsCustomZombie = true;
            }
            if (entry.type == EntityType.ZOMBIE) {
                hasVanillaZombie = true;
            }
        }

        // Only mirror biomes that already support vanilla zombies.  Never
        // manufacture a hostile pool in dimensions/biomes that do not have it,
        // and never add a second custom entry supplied by a datapack/mod.
        if (!hasVanillaZombie || alreadyContainsCustomZombie) {
            return List.of();
        }

        List<MobSpawnSettings.SpawnerData> additions = new ArrayList<>(original.size() + 1);
        for (MobSpawnSettings.SpawnerData entry : original) {
            if (entry == null || entry.type == null) {
                continue;
            }

            int baseWeight = entry.getWeight().asInt();
            if (entry.type == EntityType.ZOMBIE) {
                // The original zombie entry already contributes one factor.
                // Add only the missing factors instead of removing/recreating
                // it, so an event consumer can never observe an empty pool.
                addScaled(additions, EntityType.ZOMBIE, entry, baseWeight,
                        Math.max(0, factors.vanillaZombie() - 1));
                addScaled(additions, customZombieType, entry, baseWeight, factors.customZombie());
            } else {
                addScaled(additions, entry.type, entry, baseWeight,
                        Math.max(0, factors.otherMonster() - 1));
            }
        }
        return List.copyOf(additions);
    }

    /**
     * Compatibility entry point for callers compiled against the first
     * implementation.  It now returns the original entries plus safe
     * additions; it never performs the old destructive replacement.
     */
    @Deprecated
    public static List<MobSpawnSettings.SpawnerData> rewrite(
            List<MobSpawnSettings.SpawnerData> original,
            EntityType<?> customZombieType,
            int vanillaZombiePercent,
            int customZombiePercent,
            int otherMonsterPercent) {
        if (original == null || original.isEmpty()) {
            return List.of();
        }
        List<MobSpawnSettings.SpawnerData> result = new ArrayList<>(original);
        result.addAll(additions(
                original,
                customZombieType,
                vanillaZombiePercent,
                customZombiePercent,
                otherMonsterPercent));
        return List.copyOf(result);
    }

    private static void addScaled(
            List<MobSpawnSettings.SpawnerData> output,
            EntityType<?> type,
            MobSpawnSettings.SpawnerData template,
            int baseWeight,
            int factor) {
        if (baseWeight <= 0 || factor <= 0) {
            return;
        }
        int safeWeight = scaledWeight(baseWeight, factor);
        output.add(new MobSpawnSettings.SpawnerData(
                type,
                safeWeight,
                template.minCount,
                template.maxCount));
    }

    /** Pure percentage reduction used by unit tests without bootstrapping Minecraft registries. */
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

    record WeightFactors(
            int vanillaZombie,
            int customZombie,
            int otherMonster,
            int commonDivisor) {
    }
}
