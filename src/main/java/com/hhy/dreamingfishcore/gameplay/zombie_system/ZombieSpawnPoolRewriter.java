package com.hhy.dreamingfishcore.gameplay.zombie_system;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.jetbrains.annotations.Nullable;

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
     * <p>僵尸家族的权重先按 {@code zombieFamilyPercent} 整体缩放，再按
     * {@code vanillaZombiePercent : customZombiePercent} 拆成「原版」与「自定义」两部分；
     * 最后把「自定义」那部分再按 {@code archerZombiePercentOfCustom} 切成「围攻僵尸 + 射手僵尸」。
     * 也就是说射手僵尸是**从自定义丧尸的份子里再分出去的**，不会稀释原版僵尸的比例，也不会
     * 改变僵尸家族的总权重——传入 {@code archerZombieType == null} 或百分比为 0 时，行为与
     * 只拆两方时完全一致。</p>
     *
     * @param zombieFamilyPercent       僵尸家族总权重相对原条目的百分比（120 表示 +20%）
     * @param vanillaZombiePercent      缩放后分给原版僵尸的份额
     * @param customZombiePercent       缩放后分给自定义丧尸的份额
     * @param archerZombiePercentOfCustom 自定义丧尸份额中再分给射手僵尸的百分比（0~100）
     * @param otherMonsterPercent       其他敌对条目的权重倍率（80 表示 -20%）
     */
    public static List<MobSpawnSettings.SpawnerData> rewrite(
            List<MobSpawnSettings.SpawnerData> original,
            EntityType<?> customZombieType,
            @Nullable EntityType<?> archerZombieType,
            int zombieFamilyPercent,
            int vanillaZombiePercent,
            int customZombiePercent,
            int archerZombiePercentOfCustom,
            int otherMonsterPercent) {
        if (original == null || original.isEmpty() || customZombieType == null) {
            return original;
        }

        long zombieShareTotal = (long) Math.max(0, vanillaZombiePercent)
                + Math.max(0, customZombiePercent);
        if (zombieShareTotal <= 0L) {
            return original;
        }
        // 射手份额是「自定义份额的百分比」，所以自定义份额本身就是 0 时不会切出任何射手。
        int archerSharePercent = archerZombieType != null && customZombiePercent > 0
                ? Math.max(0, Math.min(100, archerZombiePercentOfCustom))
                : 0;

        boolean hasZombieTemplate = false;
        for (MobSpawnSettings.SpawnerData entry : original) {
            if (entry == null || entry.type == null) {
                continue;
            }
            if (entry.type == customZombieType
                    || (archerZombieType != null && entry.type == archerZombieType)
                    || entry.type == com.hhy.dreamingfishcore.gameplay.zombie_system.charred.CharredZombieEntities.CHARRED_ZOMBIE.get()
                    || entry.type == com.hhy.dreamingfishcore.gameplay.zombie_system.adamant.AdamantZombieEntities.ADAMANT_ZOMBIE.get()) {
                // A datapack or another integration already supplied one of the
                // custom entities. Do not create a competing second entry.
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

                int[] split = splitZombieFamilyWeight(
                        scaledZombieWeight,
                        vanillaZombiePercent,
                        customZombiePercent,
                        archerSharePercent);
                int vanillaWeight = split[0];
                int customWeight = split[1];
                int archerWeight = split[2];

                // Preserve the original object when the requested settings
                // happen to be a no-op (100% family, 100% vanilla, 0% custom).
                if (scaledZombieWeight == baseWeight && customWeight == 0 && archerWeight == 0) {
                    rewritten.add(entry);
                    continue;
                }

                if (vanillaWeight > 0) {
                    rewritten.add(stableEntry(
                            entry.type, vanillaWeight, entry.minCount, entry.maxCount));
                }
                // 自定义丧尸的份额里再分出焦尸与金刚僵尸（方案 A：25% / 10%，从小往大排）
                int charredWeight = proportionalWeight(customWeight, 25, 100L);
                int adamantWeight = proportionalWeight(customWeight, 10, 100L);
                int siegeWeight = Math.max(0, customWeight - charredWeight - adamantWeight);
                if (siegeWeight > 0) {
                    rewritten.add(stableEntry(
                            customZombieType, siegeWeight, entry.minCount, entry.maxCount));
                }
                if (charredWeight > 0) {
                    rewritten.add(stableEntry(com.hhy.dreamingfishcore.gameplay.zombie_system.charred.CharredZombieEntities.CHARRED_ZOMBIE.get(),
                            charredWeight, entry.minCount, entry.maxCount));
                }
                if (adamantWeight > 0) {
                    rewritten.add(stableEntry(com.hhy.dreamingfishcore.gameplay.zombie_system.adamant.AdamantZombieEntities.ADAMANT_ZOMBIE.get(),
                            adamantWeight, entry.minCount, entry.maxCount));
                }
                if (archerWeight > 0 && archerZombieType != null) {
                    rewritten.add(stableEntry(
                            archerZombieType, archerWeight, entry.minCount, entry.maxCount));
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
     * 只拆「原版 / 自定义」两方的旧签名，等价于不切出射手僵尸。
     */
    public static List<MobSpawnSettings.SpawnerData> rewrite(
            List<MobSpawnSettings.SpawnerData> original,
            EntityType<?> customZombieType,
            int zombieFamilyPercent,
            int vanillaZombiePercent,
            int customZombiePercent,
            int otherMonsterPercent) {
        return rewrite(
                original,
                customZombieType,
                null,
                zombieFamilyPercent,
                vanillaZombiePercent,
                customZombiePercent,
                0,
                otherMonsterPercent);
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

    /**
     * 把一个僵尸家族条目的缩放后权重拆成「原版 / 自定义丧尸 / 射手僵尸」三份。
     *
     * <p>分配顺序刻意是「先扣原版、再从剩余里扣射手、余额全给自定义丧尸」，这样三份之和
     * <b>恒等于</b>输入权重，不会因为两次四舍五入多出或丢掉权重。射手那份是「自定义丧尸份额
     * 的百分比」，所以自定义份额为 0 时不会凭空切出射手。</p>
     *
     * <p>抽成不依赖 {@link EntityType} 的纯函数，是为了能在单元测试里直接验证分配性质。</p>
     *
     * @return 长度 3 的数组：{ 原版, 自定义丧尸, 射手僵尸 }
     */
    static int[] splitZombieFamilyWeight(
            int scaledZombieWeight,
            int vanillaZombiePercent,
            int customZombiePercent,
            int archerPercentOfCustom) {
        int[] split = new int[3];
        if (scaledZombieWeight <= 0) {
            return split;
        }
        long zombieShareTotal = (long) Math.max(0, vanillaZombiePercent)
                + Math.max(0, customZombiePercent);
        if (zombieShareTotal <= 0L) {
            return split;
        }

        int vanillaWeight = proportionalWeight(
                scaledZombieWeight,
                Math.max(0, vanillaZombiePercent),
                zombieShareTotal);
        int customTotalWeight = scaledZombieWeight - vanillaWeight;
        int archerWeight = proportionalWeight(
                customTotalWeight,
                Math.max(0, Math.min(100, archerPercentOfCustom)),
                100);

        split[0] = vanillaWeight;
        split[1] = customTotalWeight - archerWeight;
        split[2] = archerWeight;
        return split;
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
