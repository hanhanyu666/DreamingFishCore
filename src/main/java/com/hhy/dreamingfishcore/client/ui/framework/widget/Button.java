package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Cursor;
import com.hhy.dreamingfishcore.client.ui.framework.node.Justify;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import net.minecraft.network.chat.Component;

/**
 * 按钮。五种样式：实心、色调、描边、幽灵、危险；三种尺寸。
 * 按下时轻微缩小，悬停时颜色过渡，键盘焦点时显示焦点环。
 */
public class Button extends InteractiveNode<Button> {
    public enum Variant {
        FILLED,
        TONAL,
        OUTLINED,
        GHOST,
        DANGER
    }

    public enum Scale {
        SMALL(16.0F, 7.0F, TextStyle.LABEL, 9.0F),
        MEDIUM(20.0F, 10.0F, TextStyle.BODY, 11.0F),
        LARGE(26.0F, 14.0F, TextStyle.BODY, 12.0F);

        final float height;
        final float paddingX;
        final TextStyle text;
        final float iconSize;

        Scale(float height, float paddingX, TextStyle text, float iconSize) {
            this.height = height;
            this.paddingX = paddingX;
            this.text = text;
            this.iconSize = iconSize;
        }
    }

    private final Text label = new Text().singleLine();
    private Icon leading;
    private Icon trailing;
    private Variant variant = Variant.FILLED;
    private Scale buttonScale = Scale.MEDIUM;
    private ColorRole accent = ColorRole.ACCENT;

    public Button() {
        row().alignItems(Align.CENTER).justify(Justify.CENTER).gap(5.0F);
        focusable(true);
        cursor(Cursor.POINTER);
        add(label);
        applyScale();
    }

    public static Button of(String text) {
        return new Button().label(text);
    }

    public static Button of(Component text) {
        return new Button().label(text);
    }

    public static Button icon(Icons icon) {
        Button button = new Button().leadingIcon(icon);
        button.label.visible(false);
        return button;
    }

    public Button label(String text) {
        label.text(text);
        label.visible(text != null && !text.isEmpty());
        return this;
    }

    public Button label(Component text) {
        label.text(text);
        label.visible(text != null);
        return this;
    }

    public Button leadingIcon(Icons icon) {
        if (leading == null) {
            leading = Icon.of(icon, buttonScale.iconSize);
            insert(0, leading);
        } else {
            leading.icon(icon);
        }
        return this;
    }

    public Button trailingIcon(Icons icon) {
        if (trailing == null) {
            trailing = Icon.of(icon, buttonScale.iconSize);
            add(trailing);
        } else {
            trailing.icon(icon);
        }
        return this;
    }

    public Button variant(Variant value) {
        variant = value;
        return this;
    }

    public Button filled() {
        return variant(Variant.FILLED);
    }

    public Button tonal() {
        return variant(Variant.TONAL);
    }

    public Button outlined() {
        return variant(Variant.OUTLINED);
    }

    public Button ghost() {
        return variant(Variant.GHOST);
    }

    public Button danger() {
        return variant(Variant.DANGER);
    }

    /** 主色（默认 ACCENT），用于实心与色调样式。 */
    public Button accent(ColorRole role) {
        accent = role;
        return this;
    }

    public Button scale(Scale value) {
        buttonScale = value;
        applyScale();
        return this;
    }

    public Button small() {
        return scale(Scale.SMALL);
    }

    public Button large() {
        return scale(Scale.LARGE);
    }

    private void applyScale() {
        height(buttonScale.height);
        padding(buttonScale.paddingX, 0.0F);
        label.style(buttonScale.text);
        if (leading != null) {
            leading.iconSize(buttonScale.iconSize);
        }
        if (trailing != null) {
            trailing.iconSize(buttonScale.iconSize);
        }
        radius(Theme.Radius.MD);
        boolean iconOnly = !label.isVisible();
        if (iconOnly) {
            width(buttonScale.height);
            padding(0.0F);
        }
    }

    @Override
    protected void update() {
        int content = contentColor();
        label.color(content);
        if (leading != null) {
            leading.color(content);
        }
        if (trailing != null) {
            trailing.color(content);
        }
    }

    @Override
    protected void onStateChanged() {
        super.onStateChanged();
        animateScale(isPressed() && !isEffectivelyDisabled() ? 0.965F : 1.0F);
    }

    private int baseColor() {
        return theme().color(variant == Variant.DANGER ? ColorRole.DANGER : accent);
    }

    private int contentColor() {
        Theme theme = theme();
        return switch (variant) {
            case FILLED, DANGER -> theme.color(ColorRole.TEXT_ON_ACCENT);
            case TONAL -> baseColor();
            case OUTLINED -> UiColor.lerp(theme.color(ColorRole.TEXT), baseColor(), hover() * 0.6F);
            case GHOST -> UiColor.lerp(theme.color(ColorRole.TEXT_SECONDARY), theme.color(ColorRole.TEXT), hover());
        };
    }

    @Override
    protected void paintBackground(UiCanvas canvas) {
        Theme theme = theme();
        float hover = hover();
        float press = press();
        int base = baseColor();
        int fill;
        int border = 0;
        float borderWidth = 0.0F;
        switch (variant) {
            case FILLED, DANGER -> fill = UiColor.darken(UiColor.lighten(base, hover * 0.12F), press * 0.12F);
            case TONAL -> fill = UiColor.withAlpha(base, (int) (UiColor.alpha(theme.color(ColorRole.ACCENT_SOFT))
                    * (1.0F + hover * 0.7F + press * 0.4F)));
            case OUTLINED -> {
                fill = UiColor.withAlpha(theme.color(ColorRole.SURFACE_HOVER), (int) (hover * 255));
                border = UiColor.lerp(theme.color(ColorRole.OUTLINE_STRONG), base, hover);
                borderWidth = 1.0F;
            }
            default -> fill = UiColor.withAlpha(0xFFFFFFFF, (int) (hover * 18 + press * 14));
        }
        UiCanvas.Shape shape = canvas.shape(0.0F, 0.0F, width(), height()).radius(radiusValue()).fill(fill);
        if (borderWidth > 0.0F) {
            shape.border(borderWidth, border);
        }
        if (variant == Variant.FILLED || variant == Variant.DANGER) {
            shape.shadow(Theme.Elevation.LEVEL1).shadowAlpha(0.5F + hover * 0.5F);
        }
        shape.draw();
        float focus = focus();
        if (focus > 0.01F) {
            canvas.shape(-2.0F, -2.0F, width() + 4.0F, height() + 4.0F).radius(radiusValue() + 2.0F)
                    .fill(0).border(1.5F, UiColor.multiplyAlpha(base, focus * 0.9F)).draw();
        }
    }

    @Override
    protected float currentOpacity() {
        float base = super.currentOpacity();
        return isEffectivelyDisabled() ? base * 0.45F : base;
    }
}
