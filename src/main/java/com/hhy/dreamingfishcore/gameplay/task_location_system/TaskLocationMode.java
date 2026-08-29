package com.hhy.dreamingfishcore.gameplay.task_location_system;

import java.util.Locale;

/**
 * Runtime rules for an official story location.
 *
 * <p>{@link #PROTECTED} is the authored-scene mode: survival players are moved to Adventure and
 * the scene is protected. {@link #BUILDABLE} keeps players in Survival and lets them build and
 * interact with containers/bookshelves; TNT, ordinary flint-and-steel use, mob explosions, and
 * lava are blocked there, while flint-and-steel remains allowed when it actually creates a
 * Nether portal. EconomySystem private claims may touch the region.</p>
 */
public enum TaskLocationMode {
    PROTECTED,
    BUILDABLE;

    public static TaskLocationMode parse(String value) {
        if (value == null || value.isBlank()) {
            return PROTECTED;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("任务地点模式非法：" + value, exception);
        }
    }
}
