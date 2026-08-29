package com.hhy.dreamingfishcore.server.rank_system;

import java.util.List;
import java.util.Locale;

public class RankRegistry {
    // 颜色定义：
    // NO_RANK: 灰色 (0xAAAAAA)
    // FISH: 绿色 (0x55FF55 - §a)
    // FISH+: 蓝色 (0x55FFFF - §b)
    // FISH++: 金色 (0xFFAA00 - §6)
    // OPERATOR: 红色 (0xFF5555 - §c)

    public static final Rank NULL = new Rank(
            "NULL", RankTier.UNKNOWN, 0xAAAAAA
    );
    public static final Rank NO_RANK = new Rank(
            "NO_RANK", RankTier.NO_RANK, 0xAAAAAA
    );
    public static final Rank FISH = new Rank(
            "FISH", RankTier.FISH, 0x55FF55
    );
    public static final Rank FISH_PLUS = new Rank(
            "FISH+", RankTier.FISH_PLUS, 0x55FFFF
    );
    public static final Rank FISH_PLUS_PLUS = new Rank(
            "FISH++", RankTier.FISH_PLUS_PLUS, 0xFFAA00
    );
    public static final Rank OPERATOR = new Rank(
            "OPERATOR", RankTier.MYTH, 0xFF5555
    );
    public static final Rank BUILDER_FISH = new Rank(
            "BUILDER FISH", RankTier.FISH, 0x55FF55
    );
    public static final Rank SUPER_BUILDER_FISH = new Rank(
            "SUPER BUILDER FISH", RankTier.FISH_PLUS, 0x55FFFF
    );
    public static final Rank WORLD_SHAPER_FISH = new Rank(
            "WORLD SHAPER FISH", RankTier.FISH_PLUS_PLUS, 0xFFAA00
    );
    public static final Rank MYTH_SHAPER_FISH = new Rank(
            "MYTH SHAPER FISH", RankTier.MYTH, 0xFF69B4
    );

    private static final List<Rank> REGISTERED_RANKS = List.of(
            NO_RANK, FISH, FISH_PLUS, FISH_PLUS_PLUS, OPERATOR, BUILDER_FISH, SUPER_BUILDER_FISH,
            WORLD_SHAPER_FISH, MYTH_SHAPER_FISH
    );

    // 按名称查找
    public static Rank getRankByName(String name) {
        if (name == null) {
            return NO_RANK;
        }
        return switch (normalizeName(name)) {
            case "NO RANK" -> NO_RANK;
            case "FISH" -> FISH;
            case "FISH+" -> FISH_PLUS;
            case "FISH++" -> FISH_PLUS_PLUS;
            case "OPERATOR" -> OPERATOR;
            case "BUILDER FISH" -> BUILDER_FISH;
            case "SUPER", "SUPER BUILDER FISH" -> SUPER_BUILDER_FISH;
            case "WORLD SHAPER FISH" -> WORLD_SHAPER_FISH;
            case "MYTH", "MYTH SHAPER FISH" -> MYTH_SHAPER_FISH;
            default -> NO_RANK;
        };
    }

    public static boolean isRegistered(String name) {
        if (name == null) {
            return false;
        }
        String normalizedName = normalizeName(name);
        return "SUPER".equals(normalizedName)
                || "MYTH".equals(normalizedName)
                || REGISTERED_RANKS.stream()
                .anyMatch(rank -> normalizeName(rank.getRankName()).equals(normalizedName));
    }

    public static List<Rank> getRegisteredRanks() {
        return REGISTERED_RANKS;
    }

    /** Returns the base rank representing a tier. Use {@link #getRanksByLevel(int)} for aliases. */
    public static Rank getRankByLevel(int level) {
        return switch (RankTier.fromLevel(level)) {
            case NO_RANK -> NO_RANK;
            case FISH -> FISH;
            case FISH_PLUS -> FISH_PLUS;
            case FISH_PLUS_PLUS -> FISH_PLUS_PLUS;
            case MYTH -> OPERATOR;
            default -> NO_RANK;
        };
    }

    /** Returns every named rank assigned to the requested privilege tier. */
    public static List<Rank> getRanksByLevel(int level) {
        RankTier tier = RankTier.fromLevel(level);
        if (tier == RankTier.UNKNOWN) {
            return List.of();
        }
        return REGISTERED_RANKS.stream()
                .filter(rank -> rank.getTier() == tier)
                .toList();
    }

    private static String normalizeName(String name) {
        return name.trim().toUpperCase(Locale.ROOT).replace('_', ' ');
    }
}
