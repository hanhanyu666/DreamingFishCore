package com.hhy.dreamingfishcore.client.ui.framework.anim;

/**
 * 缓动曲线：把线性进度 [0, 1] 映射为动画进度。
 *
 * <p>三次贝塞尔曲线与 CSS 的 {@code cubic-bezier(x1, y1, x2, y2)} 参数一致，
 * 可以直接照搬网页原型里调好的数值。</p>
 */
@FunctionalInterface
public interface Easing {
    float apply(float t);

    Easing LINEAR = t -> t;
    /** 常规过渡：起步干脆、收尾柔和。 */
    Easing STANDARD = cubicBezier(0.2F, 0.0F, 0.0F, 1.0F);
    /** 元素进入：快速出现，缓慢落定。 */
    Easing DECELERATE = cubicBezier(0.0F, 0.0F, 0.0F, 1.0F);
    /** 元素离开：缓慢启动，加速消失。 */
    Easing ACCELERATE = cubicBezier(0.3F, 0.0F, 1.0F, 1.0F);
    /** 强调进入，用于页面与弹窗。 */
    Easing EMPHASIZED = cubicBezier(0.05F, 0.7F, 0.1F, 1.0F);
    Easing EASE_IN_OUT = cubicBezier(0.42F, 0.0F, 0.58F, 1.0F);
    Easing OUT_CUBIC = t -> {
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    };
    Easing OUT_QUINT = t -> {
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv * inv * inv;
    };
    /** 末端轻微回弹。 */
    Easing OUT_BACK = t -> {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float x = t - 1.0F;
        return 1.0F + c3 * x * x * x + c1 * x * x;
    };

    static Easing cubicBezier(float x1, float y1, float x2, float y2) {
        return new CubicBezier(x1, y1, x2, y2);
    }

    /** 牛顿迭代求解，失败时退回二分；与浏览器实现的精度相当。 */
    final class CubicBezier implements Easing {
        private final float cx;
        private final float bx;
        private final float ax;
        private final float cy;
        private final float by;
        private final float ay;

        CubicBezier(float x1, float y1, float x2, float y2) {
            cx = 3.0F * x1;
            bx = 3.0F * (x2 - x1) - cx;
            ax = 1.0F - cx - bx;
            cy = 3.0F * y1;
            by = 3.0F * (y2 - y1) - cy;
            ay = 1.0F - cy - by;
        }

        @Override
        public float apply(float t) {
            if (t <= 0.0F) {
                return 0.0F;
            }
            if (t >= 1.0F) {
                return 1.0F;
            }
            return sampleY(solveX(t));
        }

        private float sampleX(float t) {
            return ((ax * t + bx) * t + cx) * t;
        }

        private float sampleY(float t) {
            return ((ay * t + by) * t + cy) * t;
        }

        private float sampleDerivativeX(float t) {
            return (3.0F * ax * t + 2.0F * bx) * t + cx;
        }

        private float solveX(float x) {
            float t = x;
            for (int i = 0; i < 8; i++) {
                float error = sampleX(t) - x;
                if (Math.abs(error) < 1.0e-5F) {
                    return t;
                }
                float derivative = sampleDerivativeX(t);
                if (Math.abs(derivative) < 1.0e-6F) {
                    break;
                }
                t -= error / derivative;
            }
            float low = 0.0F;
            float high = 1.0F;
            t = x;
            for (int i = 0; i < 32; i++) {
                float value = sampleX(t);
                if (Math.abs(value - x) < 1.0e-5F) {
                    break;
                }
                if (x > value) {
                    low = t;
                } else {
                    high = t;
                }
                t = (low + high) * 0.5F;
            }
            return t;
        }
    }
}
