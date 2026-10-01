package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import net.minecraft.network.chat.Component;

/**
 * 常用节点的构造入口。
 *
 * <pre>{@code
 * Ui.column(
 *         Ui.title("世界历史"),
 *         Ui.row(Icon.of(Icons.CLOCK), Ui.text("1 条记录")).gap(4)
 * ).gap(8).padding(12)
 * }</pre>
 */
public final class Ui {
    private Ui() {
    }

    public static Box row(UiNode<?>... children) {
        return new Box().row().alignItems(Align.CENTER).add(children);
    }

    public static Box column(UiNode<?>... children) {
        return new Box().column().add(children);
    }

    public static Box stack(UiNode<?>... children) {
        return new Box().stack().add(children);
    }

    /** 占满剩余空间的弹性空白。 */
    public static Box spacer() {
        return new Box().grow(1.0F).pointerEvents(false);
    }

    /** 固定尺寸空白。 */
    public static Box space(float size) {
        return new Box().size(size, size).pointerEvents(false);
    }

    public static Text text(String text) {
        return Text.of(text);
    }

    public static Text text(Component text) {
        return Text.of(text);
    }

    public static Text text(String text, TextStyle style) {
        return Text.of(text).style(style);
    }

    public static Text title(String text) {
        return Text.of(text).style(TextStyle.TITLE);
    }

    public static Text caption(String text) {
        return Text.of(text).style(TextStyle.CAPTION);
    }

    public static Text label(String text, ColorRole color) {
        return Text.of(text).style(TextStyle.LABEL).color(color);
    }
}
