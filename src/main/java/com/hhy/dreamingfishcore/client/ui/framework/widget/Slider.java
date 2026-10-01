package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Spring;
import com.hhy.dreamingfishcore.client.ui.framework.node.Cursor;
import com.hhy.dreamingfishcore.client.ui.framework.node.Size;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** 滑块。可拖动、点击跳转，聚焦后可用方向键微调。 */
public class Slider extends InteractiveNode<Slider> {
    private final AnimatedFloat display = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
    private float min;
    private float max = 1.0F;
    private float step;
    private float value;
    private Consumer<Float> onChange;

    public Slider() {
        height(14.0F);
        focusable(true);
        cursor(Cursor.POINTER);
    }

    public static Slider of(float min, float max, float initial, Consumer<Float> onChange) {
        Slider slider = new Slider();
        slider.min = min;
        slider.max = max;
        slider.value = initial;
        slider.display.snap(slider.fraction());
        slider.onChange = onChange;
        return slider;
    }

    public Slider step(float value) {
        step = value;
        return this;
    }

    public float value() {
        return value;
    }

    public Slider value(float next) {
        setValue(next, false);
        return this;
    }

    private float fraction() {
        return max > min ? (value - min) / (max - min) : 0.0F;
    }

    private void setValue(float next, boolean notify) {
        float clamped = Math.max(min, Math.min(max, next));
        if (step > 0.0F) {
            clamped = min + Math.round((clamped - min) / step) * step;
        }
        if (clamped != value) {
            value = clamped;
            display.set(fraction());
            if (notify && onChange != null) {
                onChange.accept(value);
            }
        }
    }

    private void setFromGui(double guiX) {
        float scale = width() > 0.0F ? (guiRight() - guiLeft()) / width() : 1.0F;
        float local = (float) (guiX - guiLeft()) / scale;
        float thumb = height() * 0.5F;
        float t = (local - thumb) / Math.max(1.0F, width() - thumb * 2.0F);
        setValue(min + Math.max(0.0F, Math.min(1.0F, t)) * (max - min), true);
    }

    @Override
    protected boolean onMouseDown(double guiX, double guiY, int button) {
        if (button != 0 || isEffectivelyDisabled()) {
            return false;
        }
        setFromGui(guiX);
        return true;
    }

    @Override
    protected boolean onMouseDrag(double guiX, double guiY, int button, double dragX, double dragY) {
        setFromGui(guiX);
        return true;
    }

    @Override
    protected void onMouseUp(double guiX, double guiY, int button, boolean inside) {
    }

    @Override
    protected boolean onKeyDown(int keyCode, int scanCode, int modifiers) {
        float delta = step > 0.0F ? step : (max - min) / 20.0F;
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_DOWN) {
            setValue(value - delta, true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_UP) {
            setValue(value + delta, true);
            return true;
        }
        return false;
    }

    @Override
    protected void measureContent(float availableWidth, float availableHeight, Size out) {
        out.set(80.0F, 14.0F);
    }

    @Override
    protected void paintBackground(UiCanvas canvas) {
        Theme theme = theme();
        float h = height();
        float thumbR = h * 0.5F;
        float trackH = 3.0F;
        float left = thumbR;
        float span = Math.max(1.0F, width() - thumbR * 2.0F);
        float t = display.get();
        float y = (h - trackH) * 0.5F;
        int accent = theme.color(ColorRole.ACCENT);
        canvas.shape(left, y, span, trackH).radius(trackH * 0.5F).fill(theme.color(ColorRole.TRACK)).draw();
        canvas.shape(left, y, span * t, trackH).radius(trackH * 0.5F).fill(accent).draw();
        float active = Math.max(hover(), Math.max(press(), focus()));
        float r = thumbR * (0.62F + active * 0.18F + press() * 0.1F);
        float cx = left + span * t;
        if (active > 0.01F) {
            canvas.circle(cx, h * 0.5F, r + 4.0F * active, UiColor.withAlpha(accent, 0.18F * active));
        }
        canvas.shape(cx - r, h * 0.5F - r, r * 2.0F, r * 2.0F).radius(r).fill(0xFFFFFFFF)
                .border(1.5F, accent).shadow(Theme.Elevation.LEVEL1).draw();
    }
}
