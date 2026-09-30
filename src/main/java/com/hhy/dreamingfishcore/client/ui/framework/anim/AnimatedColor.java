package com.hhy.dreamingfishcore.client.ui.framework.anim;

import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;

/** 以目标颜色驱动的 ARGB 颜色过渡。 */
public final class AnimatedColor {
    private final AnimatedFloat progress;
    private int fromColor;
    private int toColor;

    public AnimatedColor(int initial, float durationMs, Easing easing) {
        this.progress = AnimatedFloat.tween(1.0F, durationMs, easing);
        this.fromColor = initial;
        this.toColor = initial;
    }

    public AnimatedColor set(int color) {
        if (color == toColor) {
            return this;
        }
        fromColor = get();
        toColor = color;
        progress.snap(0.0F).set(1.0F);
        return this;
    }

    public AnimatedColor snap(int color) {
        fromColor = color;
        toColor = color;
        progress.snap(1.0F);
        return this;
    }

    public int get() {
        float t = progress.get();
        return t >= 1.0F ? toColor : UiColor.lerp(fromColor, toColor, t);
    }

    public boolean isAnimating() {
        return progress.isAnimating();
    }
}
