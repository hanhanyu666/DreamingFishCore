package com.hhy.dreamingfishcore.client.ui.framework.anim;

import com.hhy.dreamingfishcore.client.ui.framework.core.UiClock;

/**
 * 以目标值驱动的动画数值：调用 {@link #set(float)} 指定目标，读取 {@link #get()} 得到当前值。
 *
 * <p>可选补间（时长 + 缓动）或弹簧两种驱动方式；中途改变目标时从当前值平滑衔接。
 * 时间统一取自 {@link UiClock}，同一帧内多次读取结果一致。</p>
 */
public final class AnimatedFloat {
    private final Spring spring;
    private float durationMs;
    private float activeDurationMs;
    private final Easing easing;
    private final float[] springState = new float[2];

    private float value;
    private float from;
    private float target;
    private double startMs;
    private double lastMs;
    private long lastFrame = -1L;
    private boolean animating;
    private float delayMs;

    private AnimatedFloat(float initial, Spring spring, float durationMs, Easing easing) {
        this.spring = spring;
        this.durationMs = durationMs;
        this.easing = easing;
        this.value = initial;
        this.from = initial;
        this.target = initial;
    }

    public static AnimatedFloat spring(float initial, Spring spring) {
        return new AnimatedFloat(initial, spring, 0.0F, Easing.LINEAR);
    }

    public static AnimatedFloat tween(float initial, float durationMs, Easing easing) {
        return new AnimatedFloat(initial, null, Math.max(1.0F, durationMs), easing);
    }

    /** 设置目标值；与当前目标相同则忽略。 */
    public AnimatedFloat set(float newTarget) {
        if (newTarget == target) {
            return this;
        }
        update();
        target = newTarget;
        from = value;
        activeDurationMs = durationMs;
        startMs = UiClock.now() + delayMs;
        lastMs = UiClock.now();
        delayMs = 0.0F;
        animating = true;
        return this;
    }

    /** 修改补间时长，对下一次 {@link #set(float)} 生效。 */
    public AnimatedFloat duration(float ms) {
        this.durationMs = Math.max(1.0F, ms);
        return this;
    }

    /** 下一次 {@link #set(float)} 延迟开始的时间。 */
    public AnimatedFloat delay(float ms) {
        this.delayMs = Math.max(0.0F, ms);
        return this;
    }

    /** 立即跳到指定值，不播放动画。 */
    public AnimatedFloat snap(float newValue) {
        value = newValue;
        from = newValue;
        target = newValue;
        springState[0] = newValue;
        springState[1] = 0.0F;
        animating = false;
        return this;
    }

    public float get() {
        update();
        return value;
    }

    public float target() {
        return target;
    }

    public boolean isAnimating() {
        update();
        return animating;
    }

    private void update() {
        if (!animating) {
            return;
        }
        long frame = UiClock.frame();
        if (frame == lastFrame) {
            return;
        }
        lastFrame = frame;
        double now = UiClock.now();
        if (now < startMs) {
            lastMs = now;
            return;
        }
        if (spring == null) {
            float progress = (float) ((now - startMs) / activeDurationMs);
            if (progress >= 1.0F) {
                value = target;
                animating = false;
            } else {
                value = from + (target - from) * easing.apply(Math.max(0.0F, progress));
            }
        } else {
            if (springState[0] != value) {
                springState[0] = value;
            }
            float seconds = (float) ((now - Math.max(lastMs, startMs)) / 1000.0);
            boolean resting = spring.step(springState, target, seconds);
            value = springState[0];
            animating = !resting;
        }
        lastMs = now;
    }
}
