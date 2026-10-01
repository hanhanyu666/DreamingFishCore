package com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.ui.hud;

/**
 * 体征 HUD 的统一色板与颜色工具。
 *
 * <p>读数与面板常态使用骨白色；琥珀色表示需要注意，红色表示危险，病态青绿只属于感染读数。
 * 左下角人形例外，按生命值在绿、黄、红之间变化。本类不依赖 Minecraft，人形预览工具也会直接使用它。</p>
 */
final class HudPalette {
    static final int BONE = 0xFFE8E2D2;
    static final int BONE_DIM = 0xFFA8A396;
    static final int AMBER = 0xFFE6A74B;
    static final int RED = 0xFFE2503E;
    static final int DEEP_RED = 0xFFA3291F;
    static final int BODY_HEALTHY = 0xFF74C98A;
    static final int BODY_WARN = 0xFFF0C84B;
    static final int INFECTION = 0xFF9FD46C;
    static final int INFECTION_DIM = 0xFF4C7437;
    /** 人形上的感染腐化色：暗沉的污浊色块，不与随生命变化的绿、黄、红撞色。 */
    static final int INFECTION_ROT = 0xFF2A3320;
    static final int TRACK = 0xFF101314;
    static final int EXPERIENCE = 0xFF76A878;
    static final int OXYGEN = 0xFF6DB3DB;

    private HudPalette() {
    }

    /** 人形与生命读数的颜色：满血绿、半血黄、四分之一以下转红，濒死时转为暗红。 */
    static int bodyColor(float ratio) {
        float value = clamp01(ratio);
        if (value >= 0.5F) {
            return blend(BODY_WARN, BODY_HEALTHY, (value - 0.5F) / 0.5F);
        }
        if (value >= 0.25F) {
            return blend(RED, BODY_WARN, (value - 0.25F) / 0.25F);
        }
        return blend(DEEP_RED, RED, value / 0.25F);
    }

    /** 普通资源条：充足时骨白，低于注意线转琥珀，低于危险线转红。 */
    static int resourceColor(float ratio, float warnRatio, float dangerRatio) {
        float value = clamp01(ratio);
        if (value <= dangerRatio) {
            return RED;
        }
        if (value <= warnRatio) {
            return AMBER;
        }
        return BONE;
    }

    static int withAlpha(int color, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | color & 0x00FFFFFF;
    }

    static int scaleAlpha(int color, float factor) {
        int alpha = Math.round((color >>> 24) * clamp01(factor));
        return withAlpha(color, alpha);
    }

    /** 按比例从 {@code from} 过渡到 {@code to}，结果保持不透明。 */
    static int blend(int from, int to, float amount) {
        float t = clamp01(amount);
        int red = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
        int green = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(1.0F, value);
    }

    static float easeOutCubic(float value) {
        float remaining = 1.0F - clamp01(value);
        return 1.0F - remaining * remaining * remaining;
    }

    /** 0—1 的正弦脉冲，用于危险状态的呼吸闪动。 */
    static float pulse(long timeMillis, long periodMillis) {
        float phase = (timeMillis % periodMillis) / (float) periodMillis;
        return 0.5F + 0.5F * (float) Math.sin(phase * Math.PI * 2.0D);
    }

    /** 以固定速度逼近目标值，速度单位为每秒变化量。 */
    static float approach(float current, float target, float perSecond, float deltaSeconds) {
        float step = perSecond * deltaSeconds;
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }
}
