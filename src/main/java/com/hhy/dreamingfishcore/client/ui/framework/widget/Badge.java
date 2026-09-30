package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Size;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import net.minecraft.network.chat.Component;

/**
 * 徽标 / 标签：带色调背景的小胶囊。{@link #dot(ColorRole)} 为纯圆点（未读提示）。
 */
public class Badge extends UiNode<Badge> {
    private final Text label;
    private ColorRole role = ColorRole.ACCENT;
    private int color;
    private boolean explicit;
    private boolean solid;
    private boolean dot;
    private boolean pulse;

    private Badge(Text label) {
        this.label = label;
        row().alignItems(Align.CENTER).padding(5.0F, 1.5F).radius(Theme.Radius.FULL);
        pointerEvents(false);
        if (label != null) {
            add(label);
        }
    }

    public static Badge of(String text) {
        return new Badge(Text.of(text).style(TextStyle.CAPTION_STRONG).singleLine());
    }

    public static Badge of(Component text) {
        return new Badge(Text.of(text).style(TextStyle.CAPTION_STRONG).singleLine());
    }

    public static Badge of(String text, ColorRole role) {
        return of(text).role(role);
    }

    public static Badge dot(ColorRole role) {
        Badge badge = new Badge(null).role(role);
        badge.dot = true;
        badge.padding(0.0F).size(5.0F, 5.0F);
        return badge;
    }

    public Badge role(ColorRole value) {
        role = value;
        explicit = false;
        return this;
    }

    public Badge color(int argb) {
        color = argb;
        explicit = true;
        return this;
    }

    /** 实心样式：主色背景 + 深色文字。 */
    public Badge solid() {
        solid = true;
        return this;
    }

    /** 圆点呼吸动画。 */
    public Badge pulse() {
        pulse = true;
        return this;
    }

    public Badge text(String value) {
        if (label != null) {
            label.text(value);
        }
        return this;
    }

    private int baseColor() {
        return explicit ? color : theme().color(role);
    }

    @Override
    protected void update() {
        if (label != null) {
            label.color(solid ? theme().color(ColorRole.TEXT_ON_ACCENT) : baseColor());
        }
    }

    @Override
    protected void measureContent(float availableWidth, float availableHeight, Size out) {
        out.set(0.0F, 0.0F);
    }

    @Override
    protected void paintBackground(UiCanvas canvas) {
        int base = baseColor();
        if (dot) {
            float r = Math.min(width(), height()) * 0.5F;
            if (pulse) {
                double t = (com.hhy.dreamingfishcore.client.ui.framework.core.UiClock.now() % 1600.0) / 1600.0;
                float ring = (float) t;
                canvas.circle(width() * 0.5F, height() * 0.5F, r + ring * r * 1.6F,
                        UiColor.multiplyAlpha(base, (1.0F - ring) * 0.45F));
            }
            canvas.circle(width() * 0.5F, height() * 0.5F, r, base);
            return;
        }
        int fill = solid ? base : UiColor.withAlpha(base, 0.16F);
        canvas.shape(0.0F, 0.0F, width(), height()).radius(height() * 0.5F).fill(fill)
                .border(solid ? 0.0F : 1.0F, UiColor.withAlpha(base, 0.28F)).draw();
    }
}
