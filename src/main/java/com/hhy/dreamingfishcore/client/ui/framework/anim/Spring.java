package com.hhy.dreamingfishcore.client.ui.framework.anim;

/**
 * 阻尼弹簧参数。用于 hover、按下、拖拽释放等需要"跟手"感的交互反馈。
 *
 * <p>积分采用半隐式欧拉，按 1/240 秒细分步长，帧率高低不影响手感。</p>
 */
public record Spring(float stiffness, float damping, float mass) {
    /** 默认：柔和、不回弹。 */
    public static final Spring GENTLE = new Spring(170.0F, 26.0F, 1.0F);
    /** 灵敏：用于 hover 与按下反馈。 */
    public static final Spring SNAPPY = new Spring(420.0F, 36.0F, 1.0F);
    /** 轻微回弹：用于弹窗、徽标出现。 */
    public static final Spring BOUNCY = new Spring(300.0F, 16.0F, 1.0F);
    /** 缓慢：用于大面积位移。 */
    public static final Spring SLOW = new Spring(90.0F, 20.0F, 1.0F);

    private static final float MAX_STEP_SECONDS = 1.0F / 240.0F;
    private static final float REST_VELOCITY = 0.01F;
    private static final float REST_DISTANCE = 0.001F;

    /**
     * 推进一步。{@code state[0]} 为当前值，{@code state[1]} 为速度。
     *
     * @return 是否已经静止在目标值
     */
    public boolean step(float[] state, float target, float seconds) {
        float remaining = Math.max(0.0F, seconds);
        float value = state[0];
        float velocity = state[1];
        while (remaining > 0.0F) {
            float dt = Math.min(MAX_STEP_SECONDS, remaining);
            float force = -stiffness * (value - target) - damping * velocity;
            velocity += force / mass * dt;
            value += velocity * dt;
            remaining -= dt;
        }
        float scale = Math.max(1.0F, Math.abs(target));
        boolean resting = Math.abs(velocity) < REST_VELOCITY * scale
                && Math.abs(value - target) < REST_DISTANCE * scale;
        if (resting) {
            value = target;
            velocity = 0.0F;
        }
        state[0] = value;
        state[1] = velocity;
        return resting;
    }
}
