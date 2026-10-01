package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.Size;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextLayout;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 文字。默认按可用宽度自动换行；{@link #maxLines(int)} 限制行数，超出时末行加省略号。
 *
 * <p>排版结果按宽度缓存，只有文字、样式或宽度变化时才重新排版。</p>
 */
public class Text extends UiNode<Text> {
    private FormattedText content = Component.empty();
    private TextStyle style = TextStyle.BODY;
    private ColorRole colorRole;
    private int color;
    private boolean explicitColor;
    private int maxLines;
    private boolean wrap = true;
    private float align;
    private boolean shadow;

    private TextLayout layout;
    private float layoutWidth = Float.NaN;
    private TextLayout measureCache;
    private float measureCacheWidth = Float.NaN;

    public Text() {
        pointerEvents(false);
    }

    public static Text of(String text) {
        return new Text().text(text);
    }

    public static Text of(FormattedText text) {
        return new Text().text(text);
    }

    /** 每帧读取文字，变化时重新排版。 */
    public static Text of(Supplier<? extends FormattedText> source) {
        Text node = new Text();
        node.bind(source::get, node::text);
        return node;
    }

    public Text text(String value) {
        return text(Component.literal(value == null ? "" : value));
    }

    public Text text(FormattedText value) {
        FormattedText next = value == null ? Component.empty() : value;
        if (!Objects.equals(next, content)) {
            content = next;
            invalidateText();
        }
        return this;
    }

    public FormattedText content() {
        return content;
    }

    public Text style(TextStyle value) {
        if (!Objects.equals(style, value)) {
            style = value;
            invalidateText();
        }
        return this;
    }

    public TextStyle textStyle() {
        return style;
    }

    public Text color(ColorRole role) {
        colorRole = role;
        explicitColor = false;
        return this;
    }

    public Text color(int argb) {
        color = argb;
        explicitColor = true;
        return this;
    }

    public Text maxLines(int lines) {
        if (maxLines != lines) {
            maxLines = lines;
            invalidateText();
        }
        return this;
    }

    /** 单行：不换行，超宽时加省略号。 */
    public Text singleLine() {
        wrap = false;
        maxLines = 1;
        invalidateText();
        return this;
    }

    /** 水平对齐：0 左、0.5 居中、1 右。 */
    public Text align(float value) {
        align = value;
        return this;
    }

    public Text centered() {
        return align(0.5F);
    }

    public Text shadow(boolean value) {
        shadow = value;
        return this;
    }

    public int resolvedColor() {
        if (explicitColor) {
            return color;
        }
        return theme().color(colorRole != null ? colorRole : style.color());
    }

    private void invalidateText() {
        layout = null;
        layoutWidth = Float.NaN;
        measureCache = null;
        measureCacheWidth = Float.NaN;
        markDirty();
    }

    @Override
    protected void measureContent(float availableWidth, float availableHeight, Size out) {
        TextLayout result;
        if (measureCache != null && sameWidth(measureCacheWidth, availableWidth)) {
            result = measureCache;
        } else {
            result = TextLayout.build(content, style, availableWidth, maxLines, wrap);
            measureCache = result;
            measureCacheWidth = availableWidth;
        }
        out.set(result.width(), result.height());
    }

    @Override
    protected void onLayout() {
        float inner = innerWidth();
        if (layout == null || !sameWidth(layoutWidth, inner)) {
            // 给 0.5 像素余量，避免测量与布局取整误差导致最后一个字被挤到下一行
            layout = TextLayout.build(content, style, inner + 0.5F, maxLines, wrap);
            layoutWidth = inner;
        }
    }

    public TextLayout layout() {
        if (layout == null) {
            onLayout();
        }
        return layout;
    }

    @Override
    protected void paintContent(UiCanvas canvas) {
        TextLayout current = layout();
        if (current.lineCount() == 0) {
            return;
        }
        float top = padTop() + Math.max(0.0F, (innerHeight() - current.height()) * 0.5F);
        canvas.text(current, padLeft(), top, innerWidth(), align, resolvedColor(), shadow);
    }

    private static boolean sameWidth(float a, float b) {
        return a == b || Float.isNaN(a) && Float.isNaN(b) || Math.abs(a - b) < 0.01F
                || Float.isInfinite(a) && Float.isInfinite(b);
    }
}
