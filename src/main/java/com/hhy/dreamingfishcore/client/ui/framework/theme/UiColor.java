package com.hhy.dreamingfishcore.client.ui.framework.theme;

/** ARGB 颜色工具。所有颜色均为 {@code 0xAARRGGBB} 格式的 int。 */
public final class UiColor {
    public static final int TRANSPARENT = 0x00000000;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;

    private UiColor() {
    }

    public static int argb(int alpha, int red, int green, int blue) {
        return (clamp255(alpha) << 24) | (clamp255(red) << 16) | (clamp255(green) << 8) | clamp255(blue);
    }

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    /** 替换不透明度（0–255）。 */
    public static int withAlpha(int color, int alpha) {
        return (clamp255(alpha) << 24) | (color & 0x00FFFFFF);
    }

    /** 替换不透明度（0–1）。 */
    public static int withAlpha(int color, float alpha) {
        return withAlpha(color, Math.round(alpha * 255.0F));
    }

    /** 不透明度乘以系数。 */
    public static int multiplyAlpha(int color, float factor) {
        if (factor >= 1.0F) {
            return color;
        }
        return withAlpha(color, Math.round(alpha(color) * Math.max(0.0F, factor)));
    }

    /** 逐通道线性插值（含不透明度）。 */
    public static int lerp(int from, int to, float t) {
        if (t <= 0.0F) {
            return from;
        }
        if (t >= 1.0F) {
            return to;
        }
        return argb(
                Math.round(alpha(from) + (alpha(to) - alpha(from)) * t),
                Math.round(red(from) + (red(to) - red(from)) * t),
                Math.round(green(from) + (green(to) - green(from)) * t),
                Math.round(blue(from) + (blue(to) - blue(from)) * t));
    }

    /** 与白色混合提亮（保持不透明度）。 */
    public static int lighten(int color, float amount) {
        return withAlpha(lerp(color | 0xFF000000, WHITE, amount), alpha(color));
    }

    /** 与黑色混合压暗（保持不透明度）。 */
    public static int darken(int color, float amount) {
        return withAlpha(lerp(color | 0xFF000000, BLACK, amount), alpha(color));
    }

    /** 把 {@code top} 按其不透明度叠加到 {@code bottom} 上。 */
    public static int over(int top, int bottom) {
        float ta = alpha(top) / 255.0F;
        float ba = alpha(bottom) / 255.0F;
        float outA = ta + ba * (1.0F - ta);
        if (outA <= 0.0F) {
            return TRANSPARENT;
        }
        int r = Math.round((red(top) * ta + red(bottom) * ba * (1.0F - ta)) / outA);
        int g = Math.round((green(top) * ta + green(bottom) * ba * (1.0F - ta)) / outA);
        int b = Math.round((blue(top) * ta + blue(bottom) * ba * (1.0F - ta)) / outA);
        return argb(Math.round(outA * 255.0F), r, g, b);
    }

    /** 解析 {@code #RGB}、{@code #RRGGBB}、{@code #AARRGGBB}。 */
    public static int parse(String text) {
        String hex = text.strip();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        } else if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        long value = Long.parseLong(hex, 16);
        if (hex.length() <= 6) {
            value |= 0xFF000000L;
        }
        return (int) value;
    }

    public static String format(int color) {
        return String.format("#%08X", color);
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
