package com.hhy.dreamingfishcore.client.ui.framework.text;

import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;

/**
 * 字号层级。字号通过 PoseStack 缩放实现，Modern UI 的 SDF 文字在非整数缩放下保持清晰。
 *
 * @param scale    相对原版 8px 字形的缩放
 * @param bold     是否加粗
 * @param color    默认颜色角色
 * @param lineGap  多行文字的额外行距（GUI 像素，已含缩放）
 */
public record TextStyle(float scale, boolean bold, ColorRole color, float lineGap) {
    public static final TextStyle DISPLAY = new TextStyle(2.0F, true, ColorRole.TEXT, 4.0F);
    public static final TextStyle HEADLINE = new TextStyle(1.5F, true, ColorRole.TEXT, 3.0F);
    public static final TextStyle TITLE = new TextStyle(1.25F, true, ColorRole.TEXT, 3.0F);
    public static final TextStyle SUBTITLE = new TextStyle(1.0F, true, ColorRole.TEXT, 3.0F);
    public static final TextStyle BODY = new TextStyle(1.0F, false, ColorRole.TEXT, 3.0F);
    public static final TextStyle BODY_SECONDARY = new TextStyle(1.0F, false, ColorRole.TEXT_SECONDARY, 3.0F);
    public static final TextStyle LABEL = new TextStyle(0.875F, false, ColorRole.TEXT_SECONDARY, 2.0F);
    public static final TextStyle LABEL_STRONG = new TextStyle(0.875F, true, ColorRole.TEXT, 2.0F);
    public static final TextStyle CAPTION = new TextStyle(0.75F, false, ColorRole.TEXT_MUTED, 2.0F);
    public static final TextStyle CAPTION_STRONG = new TextStyle(0.75F, true, ColorRole.TEXT_SECONDARY, 2.0F);

    public TextStyle withScale(float newScale) {
        return new TextStyle(newScale, bold, color, lineGap);
    }

    public TextStyle withBold(boolean newBold) {
        return new TextStyle(scale, newBold, color, lineGap);
    }

    public TextStyle withColor(ColorRole newColor) {
        return new TextStyle(scale, bold, newColor, lineGap);
    }

    public TextStyle withLineGap(float newGap) {
        return new TextStyle(scale, bold, color, newGap);
    }
}
