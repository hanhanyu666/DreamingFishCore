package com.hhy.dreamingfishcore.client.ui.framework.theme;

import com.hhy.dreamingfishcore.client.ui.framework.anim.Easing;

import java.util.EnumMap;
import java.util.Map;

/**
 * 设计令牌：颜色、间距、圆角、阴影、字号与动效。
 *
 * <p>终端与 HUD 保留两种风格：终端是深蓝灰的科技终端，HUD 是骨白与琥珀的生存风。
 * 两者共用同一套间距、圆角、字号和动效档位，只有调色不同。</p>
 */
public final class Theme {
    private static Theme terminal = defaultTerminal();
    private static Theme hud = defaultHud();

    private final String name;
    private final EnumMap<ColorRole, Integer> colors;

    private Theme(String name, EnumMap<ColorRole, Integer> colors) {
        this.name = name;
        this.colors = colors;
    }

    public static Theme terminal() {
        return terminal;
    }

    public static Theme hud() {
        return hud;
    }

    public static Theme of(Kind kind) {
        return kind == Kind.HUD ? hud : terminal;
    }

    /** 两种界面风格。 */
    public enum Kind {
        TERMINAL,
        HUD
    }

    public String name() {
        return name;
    }

    public int color(ColorRole role) {
        Integer value = colors.get(role);
        return value == null ? 0xFFFF00FF : value;
    }

    /** 应用覆盖后替换当前主题；由 {@link ThemeLoader} 调用。 */
    static void install(Map<ColorRole, Integer> terminalOverrides, Map<ColorRole, Integer> hudOverrides) {
        Theme newTerminal = defaultTerminal();
        newTerminal.colors.putAll(terminalOverrides);
        Theme newHud = defaultHud();
        newHud.colors.putAll(hudOverrides);
        terminal = newTerminal;
        hud = newHud;
    }

    static Theme defaultTerminal() {
        EnumMap<ColorRole, Integer> c = new EnumMap<>(ColorRole.class);
        c.put(ColorRole.SCRIM, 0x8C03070B);
        c.put(ColorRole.SURFACE, 0xF20B1117);
        c.put(ColorRole.SURFACE_RAISED, 0xFF141D26);
        c.put(ColorRole.SURFACE_HOVER, 0xFF1B2733);
        c.put(ColorRole.SURFACE_OVERLAY, 0xFF111A23);
        c.put(ColorRole.SURFACE_SUNKEN, 0xFF080D12);
        c.put(ColorRole.OUTLINE, 0xFF23303C);
        c.put(ColorRole.OUTLINE_STRONG, 0xFF3A4C5C);
        c.put(ColorRole.TEXT, 0xFFE6EDF3);
        c.put(ColorRole.TEXT_SECONDARY, 0xFFA2B0BD);
        c.put(ColorRole.TEXT_MUTED, 0xFF6C7B89);
        c.put(ColorRole.TEXT_ON_ACCENT, 0xFF04121A);
        c.put(ColorRole.ACCENT, 0xFF5CCFE6);
        c.put(ColorRole.ACCENT_SOFT, 0x2E5CCFE6);
        c.put(ColorRole.SUCCESS, 0xFF5BD69A);
        c.put(ColorRole.WARNING, 0xFFF2B84B);
        c.put(ColorRole.DANGER, 0xFFF06A5F);
        c.put(ColorRole.INFO, 0xFF7AB6FF);
        c.put(ColorRole.GOLD, 0xFFFFC857);
        c.put(ColorRole.INFECTION, 0xFFA5D86E);
        c.put(ColorRole.EXPERIENCE, 0xFF7FC97F);
        c.put(ColorRole.OXYGEN, 0xFF6DB3DB);
        c.put(ColorRole.TRACK, 0xFF0D141B);
        return new Theme("terminal", c);
    }

    static Theme defaultHud() {
        EnumMap<ColorRole, Integer> c = new EnumMap<>(ColorRole.class);
        c.put(ColorRole.SCRIM, 0x66000000);
        c.put(ColorRole.SURFACE, 0xB8101314);
        c.put(ColorRole.SURFACE_RAISED, 0xCC171B1C);
        c.put(ColorRole.SURFACE_HOVER, 0xCC20262A);
        c.put(ColorRole.SURFACE_OVERLAY, 0xE6121516);
        c.put(ColorRole.SURFACE_SUNKEN, 0xCC0A0C0D);
        c.put(ColorRole.OUTLINE, 0x55E8E2D2);
        c.put(ColorRole.OUTLINE_STRONG, 0xAAE8E2D2);
        c.put(ColorRole.TEXT, 0xFFE8E2D2);
        c.put(ColorRole.TEXT_SECONDARY, 0xFFA8A396);
        c.put(ColorRole.TEXT_MUTED, 0xFF6F6B62);
        c.put(ColorRole.TEXT_ON_ACCENT, 0xFF14100A);
        c.put(ColorRole.ACCENT, 0xFFE6A74B);
        c.put(ColorRole.ACCENT_SOFT, 0x33E6A74B);
        c.put(ColorRole.SUCCESS, 0xFF74C98A);
        c.put(ColorRole.WARNING, 0xFFF0C84B);
        c.put(ColorRole.DANGER, 0xFFE2503E);
        c.put(ColorRole.INFO, 0xFF6DB3DB);
        c.put(ColorRole.GOLD, 0xFFFFC857);
        c.put(ColorRole.INFECTION, 0xFF9FD46C);
        c.put(ColorRole.EXPERIENCE, 0xFF76A878);
        c.put(ColorRole.OXYGEN, 0xFF6DB3DB);
        c.put(ColorRole.TRACK, 0xFF101314);
        return new Theme("hud", c);
    }

    /** 间距档位（GUI 像素）。 */
    public static final class Space {
        public static final float XXS = 2.0F;
        public static final float XS = 4.0F;
        public static final float SM = 6.0F;
        public static final float MD = 8.0F;
        public static final float LG = 12.0F;
        public static final float XL = 16.0F;
        public static final float XXL = 24.0F;
        public static final float XXXL = 32.0F;

        private Space() {
        }
    }

    /** 圆角档位（GUI 像素）。 */
    public static final class Radius {
        public static final float NONE = 0.0F;
        public static final float XS = 2.0F;
        public static final float SM = 3.0F;
        public static final float MD = 5.0F;
        public static final float LG = 8.0F;
        public static final float XL = 12.0F;
        public static final float FULL = 9999.0F;

        private Radius() {
        }
    }

    /** 阴影：纵向偏移、模糊半径、扩展与颜色。 */
    public record Shadow(float offsetX, float offsetY, float blur, float spread, int color) {
        public static final Shadow NONE = new Shadow(0.0F, 0.0F, 0.0F, 0.0F, 0);
    }

    /** 抬升层级。数值越大越"浮"。 */
    public static final class Elevation {
        public static final Shadow LEVEL0 = Shadow.NONE;
        public static final Shadow LEVEL1 = new Shadow(0.0F, 1.0F, 3.0F, 0.0F, 0x40000000);
        public static final Shadow LEVEL2 = new Shadow(0.0F, 2.0F, 6.0F, 0.0F, 0x55000000);
        public static final Shadow LEVEL3 = new Shadow(0.0F, 4.0F, 12.0F, 0.0F, 0x66000000);
        public static final Shadow LEVEL4 = new Shadow(0.0F, 8.0F, 24.0F, 0.0F, 0x80000000);

        private Elevation() {
        }
    }

    /** 动效时长（毫秒）与曲线。 */
    public static final class Motion {
        public static final float INSTANT = 80.0F;
        public static final float FAST = 140.0F;
        public static final float NORMAL = 220.0F;
        public static final float SLOW = 360.0F;
        public static final float PAGE = 320.0F;
        public static final Easing STANDARD = Easing.STANDARD;
        public static final Easing ENTER = Easing.EMPHASIZED;
        public static final Easing EXIT = Easing.ACCELERATE;

        private Motion() {
        }
    }
}
