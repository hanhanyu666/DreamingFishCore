package com.hhy.dreamingfishcore.client.ui.framework.node;

import com.hhy.dreamingfishcore.client.ui.framework.anim.Easing;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;

/**
 * 进场效果：节点第一次绘制时从起始状态过渡到正常状态。
 *
 * @param offsetX  起始水平偏移
 * @param offsetY  起始垂直偏移
 * @param scale    起始缩放
 * @param alpha    起始不透明度
 * @param duration 时长（毫秒）
 * @param delay    延迟（毫秒），用于列表依次入场
 */
public record EnterEffect(float offsetX, float offsetY, float scale, float alpha,
                          float duration, float delay, Easing easing) {
    public static final EnterEffect NONE = new EnterEffect(0, 0, 1, 1, 0, 0, Easing.LINEAR);
    public static final EnterEffect FADE = new EnterEffect(0, 0, 1, 0, Theme.Motion.NORMAL, 0, Theme.Motion.ENTER);
    public static final EnterEffect FADE_UP = new EnterEffect(0, 8, 1, 0, Theme.Motion.SLOW, 0, Theme.Motion.ENTER);
    public static final EnterEffect FADE_DOWN = new EnterEffect(0, -8, 1, 0, Theme.Motion.SLOW, 0, Theme.Motion.ENTER);
    public static final EnterEffect FADE_LEFT = new EnterEffect(12, 0, 1, 0, Theme.Motion.SLOW, 0, Theme.Motion.ENTER);
    public static final EnterEffect FADE_RIGHT = new EnterEffect(-12, 0, 1, 0, Theme.Motion.SLOW, 0, Theme.Motion.ENTER);
    public static final EnterEffect POP = new EnterEffect(0, 0, 0.94F, 0, Theme.Motion.SLOW, 0, Easing.OUT_BACK);
    public static final EnterEffect ZOOM = new EnterEffect(0, 0, 0.97F, 0, Theme.Motion.SLOW, 0, Theme.Motion.ENTER);

    public EnterEffect delayed(float ms) {
        return new EnterEffect(offsetX, offsetY, scale, alpha, duration, ms, easing);
    }

    public EnterEffect withDuration(float ms) {
        return new EnterEffect(offsetX, offsetY, scale, alpha, ms, delay, easing);
    }

    public boolean isNone() {
        return duration <= 0.0F;
    }
}
