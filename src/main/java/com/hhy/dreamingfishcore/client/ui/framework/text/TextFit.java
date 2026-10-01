package com.hhy.dreamingfishcore.client.ui.framework.text;

import net.minecraft.client.gui.Font;

/** 单行文字按宽度截断，超出部分以省略号结尾。 */
public final class TextFit {
    private static final String ELLIPSIS = "…";

    private TextFit() {
    }

    public static String trim(String text, Font font, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ELLIPSIS))) + ELLIPSIS;
    }
}
