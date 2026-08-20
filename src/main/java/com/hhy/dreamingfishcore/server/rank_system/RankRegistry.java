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
            "NULL", -1, 0xAAAAAA
    );
    public static final Rank NO_RANK = new Rank(
            "NO_RANK", 0, 0xAAAAAA
    );
    public static final Rank FISH = new Rank(
            "FISH", 1, 0x55FF55
    );
    public static final Rank FISH_PLUS = new Rank(
            "FISH+", 2, 0x55FFFF
    );
    public static final Rank FISH_PLUS_PLUS = new Rank(
            "FISH++", 3, 0xFFAA00
    );
    public static final Rank OPERATOR = new Rank(
            "OPERATOR", 4, 0xFF5555
    );
    public static final Rank BUILDER_FISH = new Rank(
            "BUILDER FISH", 5, 0x55FF55
    );
    public static final Rank SUPER_BUILDER_FISH = new Rank(
            "SUPER BUILDER FISH", 6, 0x55FFFF
    );
    public static final Rank WORLD_SHAPER_FISH = new Rank(
            "WORLD SHAPER FISH", 7, 0xFFAA00
    );
    public static final Rank MYTH_SHAPER_FISH = new Rank(
            "MYTH SHAPER FISH", 8, 0xFF69B4
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
        return switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "NO_RANK" -> NO_RANK;
            case "FISH" -> FISH;
            case "FISH+" -> FISH_PLUS;
            case "FISH++" -> FISH_PLUS_PLUS;
            case "OPERATOR" -> OPERATOR;
            case "BUILDER FISH", "BUILDER_FISH" -> BUILDER_FISH;
            case "SUPER BUILDER FISH", "SUPER_BUILDER_FISH" -> SUPER_BUILDER_FISH;
            case "WORLD SHAPER FISH", "WORLD_SHAPER_FISH" -> WORLD_SHAPER_FISH;
            case "MYTH SHAPER FISH", "MYTH_SHAPER_FISH" -> MYTH_SHAPER_FISH;
            default -> NO_RANK;
        };
    }

    public static boolean isRegistered(String name) {
        if (name == null) {
            return false;
        }
        String normalizedName = name.trim().toUpperCase(Locale.ROOT);
        return REGISTERED_RANKS.stream()
                .anyMatch(rank -> rank.getRankName().equals(normalizedName)
                        || (rank == BUILDER_FISH && "BUILDER_FISH".equals(normalizedName))
                        || (rank == SUPER_BUILDER_FISH && "SUPER_BUILDER_FISH".equals(normalizedName))
                        || (rank == WORLD_SHAPER_FISH && "WORLD_SHAPER_FISH".equals(normalizedName))
                        || (rank == MYTH_SHAPER_FISH && "MYTH_SHAPER_FISH".equals(normalizedName)));
    }

    public static List<Rank> getRegisteredRanks() {
        return REGISTERED_RANKS;
    }

    // 按等级查找
    public static Rank getRankByLevel(int level) {
        return switch (level) {
            case 0 -> NO_RANK;
            case 1 -> FISH;
            case 2 -> FISH_PLUS;
            case 3 -> FISH_PLUS_PLUS;
            case 4 -> OPERATOR;
            case 5 -> BUILDER_FISH;
            case 6 -> SUPER_BUILDER_FISH;
            case 7 -> WORLD_SHAPER_FISH;
            case 8 -> MYTH_SHAPER_FISH;
            default -> NO_RANK;
        };
    }
}
