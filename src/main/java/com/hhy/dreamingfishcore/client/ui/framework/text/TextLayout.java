package com.hhy.dreamingfishcore.client.ui.framework.text;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * 排好版的文字：按宽度换行、限制行数、末行省略号。
 *
 * <p>测量、换行都走原版 {@link Font} 与 {@code StringSplitter}，装有 Modern UI 时由其接管，
 * 因此宽度一律实测。排版结果不可变，由使用方按（文本、宽度、样式）缓存。</p>
 */
public final class TextLayout {
    public static final String ELLIPSIS = "…";
    private static final TextLayout EMPTY = new TextLayout(List.of(), new float[0], 1.0F, 0.0F, 0.0F, 0.0F, false);

    private final List<FormattedCharSequence> lines;
    private final float[] lineWidths;
    private final float scale;
    private final float lineHeight;
    private final float width;
    private final float height;
    private final boolean truncated;

    private TextLayout(List<FormattedCharSequence> lines, float[] lineWidths, float scale,
                       float lineHeight, float width, float height, boolean truncated) {
        this.lines = lines;
        this.lineWidths = lineWidths;
        this.scale = scale;
        this.lineHeight = lineHeight;
        this.width = width;
        this.height = height;
        this.truncated = truncated;
    }

    public static TextLayout empty() {
        return EMPTY;
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /** 单行字形高度（不含行距）。 */
    public static float lineHeight(float scale) {
        return font().lineHeight * scale;
    }

    public static float width(FormattedText text, float scale) {
        return font().width(text) * scale;
    }

    public static float width(String text, float scale) {
        return font().width(text) * scale;
    }

    public static float width(FormattedCharSequence text, float scale) {
        return font().width(text) * scale;
    }

    /**
     * 排版。
     *
     * @param maxWidth 最大宽度（GUI 像素），{@code <= 0} 或无穷大表示不换行
     * @param maxLines 最大行数，{@code <= 0} 表示不限
     * @param wrap     是否自动换行；为 false 时只保留一行并在超宽时加省略号
     */
    public static TextLayout build(FormattedText text, TextStyle style, float maxWidth, int maxLines, boolean wrap) {
        if (text == null) {
            return EMPTY;
        }
        Font font = font();
        FormattedText styled = style.bold() ? applyBold(text) : text;
        float scale = style.scale();
        float gap = style.lineGap();
        float glyphHeight = font.lineHeight * scale;
        boolean unbounded = maxWidth <= 0.0F || Float.isInfinite(maxWidth);
        int widthLimit = unbounded ? Integer.MAX_VALUE : Math.max(1, (int) Math.floor(maxWidth / scale + 0.01F));

        List<FormattedText> sourceLines = new ArrayList<>();
        if (!wrap || unbounded) {
            for (FormattedText line : font.getSplitter().splitLines(styled, Integer.MAX_VALUE, Style.EMPTY)) {
                sourceLines.add(line);
                if (!wrap) {
                    break;
                }
            }
        } else {
            sourceLines.addAll(font.getSplitter().splitLines(styled, widthLimit, Style.EMPTY));
        }
        if (sourceLines.isEmpty()) {
            return new TextLayout(List.of(), new float[0], scale, glyphHeight, 0.0F, glyphHeight, false);
        }

        int allowed = maxLines > 0 ? Math.min(maxLines, sourceLines.size()) : sourceLines.size();
        boolean truncated = allowed < sourceLines.size() || !wrap && hasMoreThanOneLine(styled, font);
        List<FormattedCharSequence> lines = new ArrayList<>(allowed);
        float[] widths = new float[allowed];
        float maxLineWidth = 0.0F;
        for (int i = 0; i < allowed; i++) {
            FormattedText line = sourceLines.get(i);
            boolean last = i == allowed - 1;
            boolean overflow = !unbounded && font.width(line) > widthLimit;
            if (last && (truncated || overflow) || overflow) {
                line = ellipsize(font, line, widthLimit, last && truncated);
                truncated |= overflow;
            }
            FormattedCharSequence sequence = Language.getInstance().getVisualOrder(line);
            lines.add(sequence);
            float lineWidth = font.width(sequence) * scale;
            widths[i] = lineWidth;
            maxLineWidth = Math.max(maxLineWidth, lineWidth);
        }
        float height = allowed * glyphHeight + Math.max(0, allowed - 1) * gap;
        return new TextLayout(List.copyOf(lines), widths, scale, glyphHeight + gap, maxLineWidth, height, truncated);
    }

    public static TextLayout build(String text, TextStyle style, float maxWidth, int maxLines, boolean wrap) {
        return build(text == null ? null : Component.literal(text), style, maxWidth, maxLines, wrap);
    }

    private static boolean hasMoreThanOneLine(FormattedText text, Font font) {
        return font.getSplitter().splitLines(text, Integer.MAX_VALUE, Style.EMPTY).size() > 1;
    }

    private static FormattedText applyBold(FormattedText text) {
        if (text instanceof Component component) {
            return component.copy().withStyle(style -> style.withBold(true));
        }
        return Component.literal(text.getString()).withStyle(style -> style.withBold(true));
    }

    private static FormattedText ellipsize(Font font, FormattedText line, int widthLimit, boolean forceEllipsis) {
        int ellipsisWidth = font.width(ELLIPSIS);
        if (!forceEllipsis && font.width(line) <= widthLimit) {
            return line;
        }
        int available = Math.max(0, widthLimit - ellipsisWidth);
        FormattedText head = font.substrByWidth(line, available);
        return FormattedText.composite(head, FormattedText.of(ELLIPSIS));
    }

    public List<FormattedCharSequence> lines() {
        return lines;
    }

    public float lineWidth(int index) {
        return lineWidths[index];
    }

    public int lineCount() {
        return lines.size();
    }

    public float scale() {
        return scale;
    }

    /** 行距（字形高度 + 额外行距）。 */
    public float lineAdvance() {
        return lineHeight;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public boolean truncated() {
        return truncated;
    }
}
