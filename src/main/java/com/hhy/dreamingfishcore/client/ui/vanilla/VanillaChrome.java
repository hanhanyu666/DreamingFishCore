package com.hhy.dreamingfishcore.client.ui.vanilla;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.Cursor;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.InteractiveNode;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;

/**
 * 原版界面改造共用的外观：玻璃面板、玻璃小按钮与常用配色。
 * 这些界面盖在背景图或游戏画面之上，所以比终端更依赖半透明与描边来保证可读性。
 */
public final class VanillaChrome {
    public static final int BLUE = 0xFF4DB2FF;
    public static final int CYAN = 0xFF5CCFE6;
    public static final int GREEN = 0xFF5FE3A1;
    public static final int GOLD = 0xFFFFB86B;
    public static final int RED = 0xFFEF7373;
    public static final int CREAM = 0xFFF4EAD0;
    public static final int CREAM_SOFT = 0xFFE3D8BC;
    public static final int TEXT = 0xFFE9EEF2;
    public static final int MUTED = 0xFF9AA4AE;
    public static final int FAINT = 0xFF6E7881;

    private VanillaChrome() {
    }

    /** 半透明深色玻璃面板。 */
    public static Box glassPanel() {
        return new GlassPanel(0, false);
    }

    /** 带强调色描边的玻璃面板。 */
    public static Box glassPanel(int accent) {
        return new GlassPanel(accent, false);
    }

    /** 弹窗用的不透明面板（避免底下的文字透出来）。 */
    public static Box dialogPanel(int accent) {
        return new GlassPanel(accent, true);
    }

    public static void paintGlass(UiCanvas canvas, float w, float h, float radius, int accent) {
        paintGlass(canvas, w, h, radius, accent, false);
    }

    public static void paintGlass(UiCanvas canvas, float w, float h, float radius, int accent, boolean opaque) {
        int border = accent == 0 ? 0x24FFFFFF : UiColor.withAlpha(accent, 0.45F);
        canvas.shape(0.0F, 0.0F, w, h).radius(radius)
                .verticalGradient(opaque ? 0xFA111821 : 0xD00C1219, opaque ? 0xFC080B10 : 0xD8070A0F)
                .border(1.0F, border).shadow(new Theme.Shadow(0.0F, 8.0F, 24.0F, 0.0F, 0x88000000)).draw();
        int glow = accent == 0 ? 0x40FFFFFF : UiColor.withAlpha(accent, 0.55F);
        float inset = Math.min(14.0F, w * 0.1F);
        float half = (w - inset * 2.0F) * 0.5F;
        canvas.shape(inset, 0.0F, half, 1.0F).horizontalGradient(UiColor.withAlpha(glow, 0.0F), glow).draw();
        canvas.shape(inset + half, 0.0F, half, 1.0F).horizontalGradient(glow, UiColor.withAlpha(glow, 0.0F)).draw();
    }

    /** 玻璃小按钮：图标 + 文字，悬停时描边与底线亮起。 */
    public static Chip chip(String label, Icons icon, int accent, Runnable action) {
        return new Chip(label, icon, accent, action);
    }

    private static final class GlassPanel extends Box {
        private final int accent;
        private final boolean opaque;

        GlassPanel(int accent, boolean opaque) {
            this.accent = accent;
            this.opaque = opaque;
            radius(Theme.Radius.LG);
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            paintGlass(canvas, width(), height(), radiusValue(), accent, opaque);
        }
    }

    public static final class Chip extends InteractiveNode<Chip> {
        private final int accent;
        private final Icon icon;
        private final Text label;

        Chip(String text, Icons iconType, int accent, Runnable action) {
            this.accent = accent;
            row().alignItems(Align.CENTER).gap(5.0F).padding(8.0F, 0.0F).height(20.0F).radius(Theme.Radius.MD);
            icon = Icon.of(iconType, 10.0F);
            label = Text.of(text).style(TextStyle.LABEL).singleLine();
            if (iconType != null) {
                add(icon);
            }
            add(label);
            cursor(Cursor.POINTER);
            onClick(action);
        }

        @Override
        protected void update() {
            float h = hover();
            icon.color(UiColor.lerp(0xFFB8C2CB, accent, h));
            label.color(UiColor.lerp(0xFFD7DDE2, 0xFFFFFFFF, h));
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            float h = hover();
            float w = width();
            float ht = height();
            canvas.shape(0.0F, 0.0F, w, ht).radius(radiusValue())
                    .fill(UiColor.lerp(0x99080C11, 0xC0121A22, h))
                    .border(1.0F, UiColor.lerp(0x22FFFFFF, UiColor.withAlpha(accent, 0.6F), h)).draw();
            float lineW = (w - 12.0F) * (0.35F + 0.65F * h);
            canvas.shape((w - lineW) * 0.5F, ht - 2.0F, lineW, 1.0F).radius(0.5F)
                    .fill(UiColor.withAlpha(accent, 0.35F + 0.5F * h)).draw();
            canvas.shape(0.0F, 0.0F, w, ht).radius(radiusValue()).fill(0)
                    .innerShadow(new Theme.Shadow(0.0F, 0.0F, 6.0F, 0.0F, UiColor.withAlpha(accent, 0.18F * press()))).draw();
        }
    }
}
