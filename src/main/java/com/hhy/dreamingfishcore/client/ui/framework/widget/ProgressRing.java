package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Spring;
import com.hhy.dreamingfishcore.client.ui.framework.core.UiClock;
import com.hhy.dreamingfishcore.client.ui.framework.node.Size;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;

/** 环形进度；{@link #indeterminate()} 为旋转的加载圈。子节点居中叠放在环内。 */
public class ProgressRing extends UiNode<ProgressRing> {
    private final AnimatedFloat value = AnimatedFloat.spring(0.0F, Spring.GENTLE);
    private float diameter = 24.0F;
    private float thickness = 3.0F;
    private ColorRole role = ColorRole.ACCENT;
    private int color;
    private int colorEnd;
    private boolean explicit;
    private boolean spinning;

    public ProgressRing() {
        stack().center();
        size(diameter, diameter);
        pointerEvents(false);
    }

    public static ProgressRing of(float progress) {
        ProgressRing ring = new ProgressRing();
        ring.value.snap(Math.max(0.0F, Math.min(1.0F, progress)));
        return ring;
    }

    public static ProgressRing spinner() {
        return new ProgressRing().indeterminate();
    }

    public ProgressRing progress(float progress) {
        value.set(Math.max(0.0F, Math.min(1.0F, progress)));
        return this;
    }

    public ProgressRing diameter(float value) {
        diameter = value;
        size(value, value);
        return this;
    }

    public ProgressRing thickness(float value) {
        thickness = value;
        return this;
    }

    public ProgressRing role(ColorRole value) {
        role = value;
        explicit = false;
        return this;
    }

    public ProgressRing color(int argb) {
        color = argb;
        explicit = true;
        return this;
    }

    public ProgressRing gradientTo(int argb) {
        colorEnd = argb;
        return this;
    }

    public ProgressRing indeterminate() {
        spinning = true;
        return this;
    }

    @Override
    protected void measureContent(float availableWidth, float availableHeight, Size out) {
        out.set(diameter, diameter);
    }

    @Override
    protected void paintContent(UiCanvas canvas) {
        float cx = width() * 0.5F;
        float cy = height() * 0.5F;
        float radius = Math.min(width(), height()) * 0.5F - thickness * 0.5F - 0.5F;
        int base = explicit ? color : theme().color(role);
        int end = UiColor.alpha(colorEnd) > 0 ? colorEnd : base;
        float full = (float) (Math.PI * 2.0);
        canvas.arc(cx, cy, radius, thickness, 0.0F, full,
                theme().color(ColorRole.TRACK), theme().color(ColorRole.TRACK));
        if (spinning) {
            float t = (float) (UiClock.now() % 1100.0 / 1100.0);
            float start = t * full - full / 4.0F;
            float sweep = full * (0.22F + 0.12F * (float) Math.sin(UiClock.now() / 420.0));
            canvas.arc(cx, cy, radius, thickness, start, sweep, UiColor.withAlpha(base, 0.2F), base);
            return;
        }
        float p = value.get();
        if (p > 0.001F) {
            canvas.arc(cx, cy, radius, thickness, -full / 4.0F, full * p, base, end);
        }
    }
}
