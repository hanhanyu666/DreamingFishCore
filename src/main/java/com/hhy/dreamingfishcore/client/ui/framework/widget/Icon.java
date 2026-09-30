package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.Size;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;

/** 矢量图标节点。 */
public class Icon extends UiNode<Icon> {
    private static final IconPen PEN = new IconPen();

    private Icons icon;
    private float iconSize = 12.0F;
    private float stroke = 2.0F;
    private ColorRole colorRole = ColorRole.TEXT_SECONDARY;
    private int color;
    private boolean explicitColor;

    public Icon() {
        pointerEvents(false);
    }

    public static Icon of(Icons icon) {
        return new Icon().icon(icon);
    }

    public static Icon of(Icons icon, float size) {
        return new Icon().icon(icon).iconSize(size);
    }

    /** 直接在画布上绘制图标（HUD 等即时绘制场景使用）。 */
    public static void paint(UiCanvas canvas, Icons icon, float x, float y, float size, int color) {
        paint(canvas, icon, x, y, size, 2.0F, color);
    }

    public static void paint(UiCanvas canvas, Icons icon, float x, float y, float size, float stroke, int color) {
        if (icon == null) {
            return;
        }
        // 小尺寸时线条适当加粗，保证可读
        float effectiveStroke = size < 10.0F ? stroke * 1.2F : stroke;
        icon.paint(PEN.set(canvas, x, y, size, effectiveStroke, color));
    }

    public Icon icon(Icons value) {
        icon = value;
        return this;
    }

    public Icons icon() {
        return icon;
    }

    public Icon iconSize(float value) {
        if (iconSize != value) {
            iconSize = value;
            markDirty();
        }
        return this;
    }

    public Icon stroke(float value) {
        stroke = value;
        return this;
    }

    public Icon color(ColorRole role) {
        colorRole = role;
        explicitColor = false;
        return this;
    }

    public Icon color(int argb) {
        color = argb;
        explicitColor = true;
        return this;
    }

    public int resolvedColor() {
        return explicitColor ? color : theme().color(colorRole);
    }

    @Override
    protected void measureContent(float availableWidth, float availableHeight, Size out) {
        out.set(iconSize, iconSize);
    }

    @Override
    protected void paintContent(UiCanvas canvas) {
        float x = padLeft() + (innerWidth() - iconSize) * 0.5F;
        float y = padTop() + (innerHeight() - iconSize) * 0.5F;
        paint(canvas, icon, x, y, iconSize, stroke, resolvedColor());
    }
}
