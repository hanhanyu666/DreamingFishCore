package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.core.UiClock;
import com.hhy.dreamingfishcore.client.ui.framework.node.Size;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextLayout;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * 打字机文本：按固定速度逐字显示，超过每页行数时分页。
 * 第一次 {@link #advance()} 结束当前页的打字动画，之后翻页。
 */
public class Typewriter extends UiNode<Typewriter> {
    private String text = "";
    private TextStyle style = TextStyle.BODY;
    private int color;
    private boolean explicitColor;
    private int linesPerPage = 8;
    private float charsPerSecond = 55.0F;
    private int page;
    private double startMs;
    private List<String> lines = List.of();
    private float wrappedWidth = -1.0F;

    public Typewriter() {
        pointerEvents(false);
        startMs = UiClock.now();
    }

    public static Typewriter of(String text) {
        return new Typewriter().text(text);
    }

    public Typewriter text(String value) {
        String next = value == null ? "" : value;
        if (!next.equals(text)) {
            text = next;
            wrappedWidth = -1.0F;
            page = 0;
            restart();
            markDirty();
        }
        return this;
    }

    public Typewriter style(TextStyle value) {
        style = value;
        wrappedWidth = -1.0F;
        markDirty();
        return this;
    }

    public Typewriter color(int argb) {
        color = argb;
        explicitColor = true;
        return this;
    }

    public Typewriter linesPerPage(int value) {
        linesPerPage = Math.max(1, value);
        markDirty();
        return this;
    }

    public Typewriter speed(float charactersPerSecond) {
        charsPerSecond = Math.max(1.0F, charactersPerSecond);
        return this;
    }

    public Typewriter restart() {
        startMs = UiClock.now();
        return this;
    }

    public int page() {
        return page;
    }

    public int pageCount() {
        ensureLines(innerWidth());
        return Math.max(1, (lines.size() + linesPerPage - 1) / linesPerPage);
    }

    /** 当前页是否已完整显示。 */
    public boolean isPageFinished() {
        return visibleCharacters() >= pageCharacterCount();
    }

    /** 结束打字动画；已结束时翻到下一页。返回 false 表示已经是最后一页且显示完毕。 */
    public boolean advance() {
        if (!isPageFinished()) {
            startMs = UiClock.now() - pageCharacterCount() * 1000.0 / charsPerSecond;
            return true;
        }
        if (page + 1 < pageCount()) {
            page++;
            restart();
            return true;
        }
        return false;
    }

    private int visibleCharacters() {
        return (int) Math.max(0.0, (UiClock.now() - startMs) * charsPerSecond / 1000.0);
    }

    private int pageCharacterCount() {
        ensureLines(innerWidth());
        int start = page * linesPerPage;
        int end = Math.min(lines.size(), start + linesPerPage);
        int count = 0;
        for (int i = start; i < end; i++) {
            count += lines.get(i).length() + 1;
        }
        return Math.max(1, count);
    }

    private void ensureLines(float width) {
        if (width <= 0.0F) {
            return;
        }
        if (Math.abs(width - wrappedWidth) < 0.01F) {
            return;
        }
        wrappedWidth = width;
        Font font = TextLayout.font();
        int limit = Math.max(1, (int) Math.floor(width / style.scale()));
        List<String> result = new ArrayList<>();
        for (FormattedText line : font.getSplitter().splitLines(text, limit, Style.EMPTY)) {
            result.add(line.getString());
        }
        lines = result;
    }

    private float lineAdvance() {
        return TextLayout.lineHeight(style.scale()) + style.lineGap();
    }

    @Override
    protected void measureContent(float availableWidth, float availableHeight, Size out) {
        float width = Float.isFinite(availableWidth) ? availableWidth : 240.0F;
        ensureLines(width);
        int shown = Math.max(1, Math.min(linesPerPage, lines.size()));
        out.set(width, shown * lineAdvance() - style.lineGap());
    }

    @Override
    protected void paintContent(UiCanvas canvas) {
        ensureLines(innerWidth());
        int c = explicitColor ? color : theme().color(style.color() == null ? ColorRole.TEXT : style.color());
        int remaining = visibleCharacters();
        int start = page * linesPerPage;
        int end = Math.min(lines.size(), start + linesPerPage);
        float y = padTop();
        for (int i = start; i < end && remaining > 0; i++) {
            String line = lines.get(i);
            int visible = Math.min(line.length(), remaining);
            if (visible > 0) {
                canvas.text(line.substring(0, visible), padLeft(), y, c, style.scale(), false);
                if (visible < line.length()) {
                    // 光标：正在打出的字后面画一个淡色竖条
                    float cursorX = padLeft() + TextLayout.width(line.substring(0, visible), style.scale()) + 1.0F;
                    float blink = (float) (0.5 + 0.5 * Math.sin(UiClock.now() / 90.0));
                    canvas.fill(cursorX, y, 1.0F, TextLayout.lineHeight(style.scale()) - 1.0F, UiColor.multiplyAlpha(c, blink * 0.8F));
                }
            }
            remaining -= line.length() + 1;
            y += lineAdvance();
        }
    }
}
