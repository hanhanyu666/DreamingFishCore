package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.Size;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;

/** 分隔线。横向时占满宽度，纵向时占满高度。 */
public class Divider extends UiNode<Divider> {
    private final boolean vertical;
    private float thickness = 1.0F;
    private ColorRole role = ColorRole.OUTLINE;
    private int color;
    private boolean explicit;

    private Divider(boolean vertical) {
        this.vertical = vertical;
        pointerEvents(false);
    }

    public static Divider horizontal() {
        return new Divider(false);
    }

    public static Divider vertical() {
        return new Divider(true);
    }

    public Divider thickness(float value) {
        thickness = value;
        markDirty();
        return this;
    }

    public Divider color(ColorRole value) {
        role = value;
        explicit = false;
        return this;
    }

    public Divider color(int argb) {
        color = argb;
        explicit = true;
        return this;
    }

    @Override
    protected void measureContent(float availableWidth, float availableHeight, Size out) {
        if (vertical) {
            out.set(thickness, 0.0F);
        } else {
            out.set(0.0F, thickness);
        }
    }

    @Override
    protected void paintContent(UiCanvas canvas) {
        int c = explicit ? color : theme().color(role);
        if (vertical) {
            canvas.fill(padLeft() + (innerWidth() - thickness) * 0.5F, padTop(), thickness, innerHeight(), c);
        } else {
            canvas.fill(padLeft(), padTop() + (innerHeight() - thickness) * 0.5F, innerWidth(), thickness, c);
        }
    }
}
