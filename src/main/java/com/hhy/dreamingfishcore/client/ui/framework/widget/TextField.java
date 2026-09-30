package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Cursor;
import com.hhy.dreamingfishcore.client.ui.framework.node.Size;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 单行输入框。编辑、选区、复制粘贴和输入法全部交给内嵌的原版 {@link EditBox}
 * （装有 Modern UI 时由其增强），外框、占位与焦点效果由框架绘制。
 */
public class TextField extends InteractiveNode<TextField> {
    private final EditBox box;
    private Icons leadingIcon;
    private String placeholder = "";
    private Runnable onSubmit;
    private boolean password;

    public TextField() {
        row().alignItems(Align.CENTER);
        height(22.0F);
        padding(8.0F, 0.0F);
        radius(Theme.Radius.MD);
        focusable(true);
        cursor(Cursor.TEXT);
        box = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 10, Component.empty());
        box.setBordered(false);
        box.setMaxLength(256);
        box.setCanLoseFocus(true);
    }

    public static TextField of(String placeholder) {
        return new TextField().placeholder(placeholder);
    }

    public TextField placeholder(String text) {
        placeholder = text == null ? "" : text;
        return this;
    }

    public TextField icon(Icons icon) {
        leadingIcon = icon;
        return this;
    }

    public TextField value(String text) {
        box.setValue(text == null ? "" : text);
        return this;
    }

    public String value() {
        return box.getValue();
    }

    public TextField maxLength(int length) {
        box.setMaxLength(length);
        return this;
    }

    public TextField filter(Predicate<String> filter) {
        box.setFilter(filter);
        return this;
    }

    public TextField onChange(Consumer<String> listener) {
        box.setResponder(listener);
        return this;
    }

    public TextField onSubmit(Runnable action) {
        onSubmit = action;
        return this;
    }

    /** 密码模式：显示为圆点。 */
    public TextField password(boolean value) {
        password = value;
        if (value) {
            box.setFormatter((text, offset) -> FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
        } else {
            box.setFormatter((text, offset) -> FormattedCharSequence.forward(text, Style.EMPTY));
        }
        return this;
    }

    public EditBox editBox() {
        return box;
    }

    @Override
    protected void onStateChanged() {
        super.onStateChanged();
        box.setFocused(isFocused());
    }

    @Override
    protected void measureContent(float availableWidth, float availableHeight, Size out) {
        out.set(80.0F, 10.0F);
    }

    private float textLeft() {
        return padLeft() + (leadingIcon != null ? 16.0F : 0.0F);
    }

    @Override
    protected void paintBackground(UiCanvas canvas) {
        Theme theme = theme();
        float focus = focus();
        int border = UiColor.lerp(UiColor.lerp(theme.color(ColorRole.OUTLINE), theme.color(ColorRole.OUTLINE_STRONG), hover()),
                theme.color(ColorRole.ACCENT), focus);
        canvas.shape(0.0F, 0.0F, width(), height()).radius(radiusValue())
                .fill(theme.color(ColorRole.SURFACE_SUNKEN)).border(1.0F, border).draw();
        if (focus > 0.01F) {
            canvas.shape(-2.0F, -2.0F, width() + 4.0F, height() + 4.0F).radius(radiusValue() + 2.0F).fill(0)
                    .border(2.0F, UiColor.multiplyAlpha(theme.color(ColorRole.ACCENT), focus * 0.25F)).draw();
        }
    }

    @Override
    protected void paintContent(UiCanvas canvas) {
        Theme theme = theme();
        if (leadingIcon != null) {
            Icon.paint(canvas, leadingIcon, padLeft(), (height() - 11.0F) * 0.5F, 11.0F,
                    UiColor.lerp(theme.color(ColorRole.TEXT_MUTED), theme.color(ColorRole.ACCENT), focus()));
        }
        float left = textLeft();
        float textWidth = Math.max(8.0F, width() - left - padRight());
        int x = Math.round(left);
        int y = Math.round((height() - 8.0F) * 0.5F);
        box.setX(x);
        box.setY(y);
        box.setWidth(Math.round(textWidth));
        box.setHeight(10);
        box.setTextColor(theme.color(ColorRole.TEXT));
        if (!placeholder.isEmpty()) {
            box.setHint(Component.literal(placeholder).withStyle(style -> style.withColor(
                    theme.color(ColorRole.TEXT_MUTED) & 0xFFFFFF)));
        }
        int mouseX = (int) localX(root() != null ? root().mouseX() : 0.0);
        int mouseY = (int) localY(root() != null ? root().mouseY() : 0.0);
        canvas.custom(0.0F, 0.0F, width(), height(), g -> box.renderWidget(g, mouseX, mouseY, 0.0F));
    }

    private float localScale() {
        float guiWidth = guiRight() - guiLeft();
        return width() > 0.0F && guiWidth > 0.0F ? guiWidth / width() : 1.0F;
    }

    private double localX(double guiX) {
        return (guiX - guiLeft()) / localScale();
    }

    private double localY(double guiY) {
        return (guiY - guiTop()) / localScale();
    }

    @Override
    protected boolean onMouseDown(double guiX, double guiY, int button) {
        if (isEffectivelyDisabled()) {
            return false;
        }
        box.setFocused(true);
        double lx = Math.max(box.getX(), Math.min(box.getX() + box.getWidth() - 1, localX(guiX)));
        box.mouseClicked(lx, box.getY() + 4, button);
        return true;
    }

    @Override
    protected boolean onMouseDrag(double guiX, double guiY, int button, double dragX, double dragY) {
        double lx = Math.max(box.getX(), Math.min(box.getX() + box.getWidth() - 1, localX(guiX)));
        return box.mouseDragged(lx, box.getY() + 4, button, dragX, dragY);
    }

    @Override
    protected boolean onKeyDown(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && onSubmit != null) {
            onSubmit.run();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }
        if (box.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        // 输入框聚焦时吞掉普通按键，避免触发界面快捷键
        return keyCode >= GLFW.GLFW_KEY_SPACE && keyCode <= GLFW.GLFW_KEY_GRAVE_ACCENT;
    }

    @Override
    protected boolean onCharTyped(char character, int modifiers) {
        return box.charTyped(character, modifiers);
    }

    public boolean isPassword() {
        return password;
    }
}
