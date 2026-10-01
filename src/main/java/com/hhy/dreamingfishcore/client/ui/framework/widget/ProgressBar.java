package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Spring;
import com.hhy.dreamingfishcore.client.ui.framework.node.Size;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;

import java.util.function.Supplier;

/** 线性进度条。数值变化时平滑过渡；可选分段刻度与末端高光。 */
public class ProgressBar extends UiNode<ProgressBar> {
    private final AnimatedFloat value = AnimatedFloat.spring(0.0F, Spring.GENTLE);
    private ColorRole role = ColorRole.ACCENT;
    private int color;
    private boolean explicit;
    private int colorEnd;
    private float thickness = 4.0F;
    private int segments;
    private boolean glow = true;

    public ProgressBar() {
        pointerEvents(false);
    }

    public static ProgressBar of(float progress) {
        ProgressBar bar = new ProgressBar();
        bar.value.snap(clamp(progress));
        return bar;
    }

    public static ProgressBar of(Supplier<Float> progress) {
        ProgressBar bar = new ProgressBar();
        bar.bind(progress, bar::progress);
        return bar;
    }

    public ProgressBar progress(float progress) {
        value.set(clamp(progress));
        return this;
    }

    public ProgressBar role(ColorRole value) {
        role = value;
        explicit = false;
        return this;
    }

    public ProgressBar color(int argb) {
        color = argb;
        explicit = true;
        return this;
    }

    /** 从主色渐变到指定颜色。 */
    public ProgressBar gradientTo(int argb) {
        colorEnd = argb;
        return this;
    }

    public ProgressBar thickness(float value) {
        thickness = value;
        markDirty();
        return this;
    }

    public ProgressBar segments(int count) {
        segments = count;
        return this;
    }

    public ProgressBar glow(boolean value) {
        glow = value;
        return this;
    }

    private static float clamp(float v) {
        return Float.isNaN(v) ? 0.0F : Math.max(0.0F, Math.min(1.0F, v));
    }

    @Override
    protected void measureContent(float availableWidth, float availableHeight, Size out) {
        out.set(0.0F, thickness);
    }

    @Override
    protected void paintContent(UiCanvas canvas) {
        float x = padLeft();
        float y = padTop() + (innerHeight() - thickness) * 0.5F;
        float w = innerWidth();
        float r = thickness * 0.5F;
        int base = explicit ? color : theme().color(role);
        canvas.shape(x, y, w, thickness).radius(r).fill(theme().color(ColorRole.TRACK)).draw();
        float p = value.get();
        float fillW = w * p;
        if (fillW > 0.5F) {
            UiCanvas.Shape shape = canvas.shape(x, y, Math.max(thickness, fillW), thickness).radius(r);
            if (UiColor.alpha(colorEnd) > 0) {
                shape.horizontalGradient(base, colorEnd);
            } else {
                shape.fill(base);
            }
            if (glow) {
                shape.shadow(new com.hhy.dreamingfishcore.client.ui.framework.theme.Theme.Shadow(
                        0.0F, 0.0F, thickness * 2.5F, 0.0F, UiColor.withAlpha(base, 0.45F)));
            }
            shape.draw();
        }
        if (segments > 1) {
            int gapColor = theme().color(ColorRole.SURFACE_RAISED);
            for (int i = 1; i < segments; i++) {
                float sx = x + w * i / segments;
                canvas.fill(sx - 0.5F, y, 1.0F, thickness, UiColor.withAlpha(gapColor, 0.9F));
            }
        }
    }
}
