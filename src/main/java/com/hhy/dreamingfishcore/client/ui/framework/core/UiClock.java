package com.hhy.dreamingfishcore.client.ui.framework.core;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * UI 帧时钟：每帧开始时取一次时间，同一帧内所有动画共享同一个时间戳。
 *
 * <p>时间单位为毫秒，从客户端启动起单调递增。支持全局时间倍率，用于调试时放慢动画；
 * 单帧增量上限 100ms，避免卡顿或断点后动画直接跳到终点。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class UiClock {
    private static final double MAX_FRAME_DELTA_MS = 100.0;

    private static long lastNanos = -1L;
    private static double nowMs;
    private static double deltaMs;
    private static long frame;
    private static double timeScale = 1.0;

    private UiClock() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        advance(System.nanoTime());
    }

    static void advance(long nanos) {
        if (lastNanos < 0L) {
            lastNanos = nanos;
        }
        double realDelta = (nanos - lastNanos) / 1_000_000.0;
        lastNanos = nanos;
        deltaMs = Math.max(0.0, Math.min(MAX_FRAME_DELTA_MS, realDelta)) * timeScale;
        nowMs += deltaMs;
        frame++;
    }

    /** 当前帧时间（毫秒）。 */
    public static double now() {
        return nowMs;
    }

    /** 上一帧到本帧经过的时间（毫秒，已乘时间倍率）。 */
    public static double delta() {
        return deltaMs;
    }

    public static long frame() {
        return frame;
    }

    public static double timeScale() {
        return timeScale;
    }

    public static void setTimeScale(double scale) {
        timeScale = Math.max(0.05, Math.min(4.0, scale));
    }
}
