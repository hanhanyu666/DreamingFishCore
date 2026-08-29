package com.hhy.dreamingfishcore.server.rank_system;

/**
 * Shared privilege tiers for ranks. Multiple named ranks may belong to the same tier while
 * retaining their own identity, color, and presentation.
 */
public enum RankTier {
    UNKNOWN(-1),
    NO_RANK(0),
    FISH(1),
    FISH_PLUS(2),
    FISH_PLUS_PLUS(3),
    MYTH(4);

    private final int level;

    RankTier(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public boolean isAtLeast(RankTier minimum) {
        return this != UNKNOWN && minimum != UNKNOWN && level >= minimum.level;
    }

    public static RankTier fromLevel(int level) {
        return switch (level) {
            case 0 -> NO_RANK;
            case 1 -> FISH;
            case 2 -> FISH_PLUS;
            case 3 -> FISH_PLUS_PLUS;
            case 4 -> MYTH;
            default -> UNKNOWN;
        };
    }
}
