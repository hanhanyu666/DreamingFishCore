package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;

/**
 * 卡片：抬升一级的表面。设置 {@code onClick} 后变为可交互卡片：
 * 悬停时上浮、阴影加深、边框提亮，按下时轻微缩小。
 */
public class Card extends InteractiveNode<Card> {
    private ColorRole surface = ColorRole.SURFACE_RAISED;
    private ColorRole hoverSurface = ColorRole.SURFACE_HOVER;
    private ColorRole outline = ColorRole.OUTLINE;
    private int accentColor;
    private ColorRole accentRole;
    private float accentWidth = 3.0F;
    private Theme.Shadow elevation = Theme.Elevation.LEVEL1;
    private Theme.Shadow hoverElevation = Theme.Elevation.LEVEL3;
    private boolean outlined = true;
    private boolean liftOnHover = true;
    private int tint;

    public Card() {
        column();
        padding(Theme.Space.LG);
        gap(Theme.Space.SM);
        radius(Theme.Radius.LG);
    }

    public static Card of(UiNode<?>... children) {
        return new Card().add(children);
    }

    public Card surface(ColorRole role) {
        surface = role;
        return this;
    }

    public Card hoverSurface(ColorRole role) {
        hoverSurface = role;
        return this;
    }

    public Card outline(ColorRole role) {
        outline = role;
        outlined = role != null;
        return this;
    }

    public Card noOutline() {
        outlined = false;
        return this;
    }

    /** 左侧强调色条。 */
    public Card accent(ColorRole role) {
        accentRole = role;
        accentColor = 0;
        return this;
    }

    public Card accent(int argb) {
        accentColor = argb;
        accentRole = null;
        return this;
    }

    public Card accentWidth(float width) {
        accentWidth = width;
        return this;
    }

    /** 叠加在表面上的色调（带不透明度的颜色），用于未读、警告等状态。 */
    public Card tint(int argb) {
        tint = argb;
        return this;
    }

    public Card elevation(Theme.Shadow resting, Theme.Shadow hovered) {
        elevation = resting;
        hoverElevation = hovered;
        return this;
    }

    public Card flat() {
        elevation = Theme.Shadow.NONE;
        hoverElevation = Theme.Shadow.NONE;
        return this;
    }

    public Card lift(boolean value) {
        liftOnHover = value;
        return this;
    }

    private boolean interactive() {
        return onClickHandler() != null && !isEffectivelyDisabled();
    }

    @Override
    protected void onStateChanged() {
        super.onStateChanged();
        if (interactive()) {
            animateTranslate(0.0F, liftOnHover && isHovered() ? -1.0F : 0.0F);
            animateScale(isPressed() ? 0.985F : 1.0F);
        }
    }

    @Override
    protected void paintBackground(UiCanvas canvas) {
        Theme theme = theme();
        float hover = interactive() ? hover() : 0.0F;
        float selected = selection();
        int fill = UiColor.lerp(theme.color(surface), theme.color(hoverSurface), Math.max(hover, selected * 0.6F));
        if (UiColor.alpha(tint) > 0) {
            fill = UiColor.over(tint, fill);
        }
        int accent = accentRole != null ? theme.color(accentRole) : accentColor;
        UiCanvas.Shape shape = canvas.shape(0.0F, 0.0F, width(), height())
                .radius(radiusValue()).fill(fill);
        if (outlined) {
            int border = theme.color(outline);
            if (hover > 0.0F || selected > 0.0F) {
                int highlight = UiColor.alpha(accent) > 0 ? UiColor.multiplyAlpha(accent, 0.7F)
                        : theme.color(ColorRole.OUTLINE_STRONG);
                border = UiColor.lerp(border, highlight, Math.max(hover, selected));
            }
            shape.border(1.0F, border);
        }
        Theme.Shadow shadow = hover > 0.5F ? hoverElevation : elevation;
        if (shadow != null && shadow.blur() > 0.0F) {
            shape.shadow(shadow).shadowAlpha(hover > 0.5F ? 0.6F + hover * 0.4F : 1.0F - hover * 0.5F);
        }
        shape.draw();
        if (UiColor.alpha(accent) > 0 && accentWidth > 0.0F) {
            float inset = Math.max(0.0F, radiusValue() * 0.6F);
            canvas.shape(0.0F, inset, accentWidth, Math.max(0.0F, height() - inset * 2.0F))
                    .radius(0.0F, accentWidth * 0.5F, accentWidth * 0.5F, 0.0F).fill(accent).draw();
        }
    }
}
