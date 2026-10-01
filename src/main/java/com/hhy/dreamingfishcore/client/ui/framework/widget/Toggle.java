package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Spring;
import com.hhy.dreamingfishcore.client.ui.framework.core.UiSounds;
import com.hhy.dreamingfishcore.client.ui.framework.node.Cursor;
import com.hhy.dreamingfishcore.client.ui.framework.node.Size;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;

import java.util.function.Consumer;

/** 开关。旋钮以弹簧动画滑动，按下时拉长。 */
public class Toggle extends InteractiveNode<Toggle> {
    private final AnimatedFloat knob = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
    private boolean on;
    private Consumer<Boolean> onChange;

    public Toggle() {
        size(26.0F, 14.0F);
        focusable(true);
        cursor(Cursor.POINTER);
        onClick(() -> set(!on, true));
    }

    public static Toggle of(boolean initial, Consumer<Boolean> onChange) {
        Toggle toggle = new Toggle();
        toggle.on = initial;
        toggle.knob.snap(initial ? 1.0F : 0.0F);
        toggle.onChange = onChange;
        return toggle;
    }

    public boolean isOn() {
        return on;
    }

    public Toggle set(boolean value, boolean notify) {
        if (on != value) {
            on = value;
            knob.set(value ? 1.0F : 0.0F);
            if (notify && onChange != null) {
                onChange.accept(value);
            }
        }
        return this;
    }

    @Override
    protected void onMouseUp(double guiX, double guiY, int button, boolean inside) {
        if (inside && button == 0 && !isEffectivelyDisabled()) {
            UiSounds.soft();
            set(!on, true);
        }
    }

    @Override
    protected void measureContent(float availableWidth, float availableHeight, Size out) {
        out.set(26.0F, 14.0F);
    }

    @Override
    protected void paintBackground(UiCanvas canvas) {
        Theme theme = theme();
        float k = knob.get();
        float h = height();
        float w = width();
        int off = UiColor.lerp(theme.color(ColorRole.SURFACE_SUNKEN), theme.color(ColorRole.OUTLINE), 0.6F + hover() * 0.4F);
        int track = UiColor.lerp(off, theme.color(ColorRole.ACCENT), k);
        canvas.shape(0.0F, 0.0F, w, h).radius(h * 0.5F).fill(track)
                .border(1.0F, UiColor.lerp(theme.color(ColorRole.OUTLINE_STRONG), UiColor.darken(theme.color(ColorRole.ACCENT), 0.2F), k))
                .draw();
        float inset = 2.0F;
        float d = h - inset * 2.0F;
        float stretch = press() * 4.0F;
        float x = inset + (w - inset * 2.0F - d - stretch) * k;
        canvas.shape(x, inset, d + stretch, d).radius(d * 0.5F)
                .fill(UiColor.lerp(theme.color(ColorRole.TEXT_SECONDARY), 0xFFFFFFFF, k))
                .shadow(Theme.Elevation.LEVEL1).draw();
    }
}
