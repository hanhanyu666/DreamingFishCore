package com.hhy.dreamingfishcore.client.ui.util;

import net.minecraft.client.gui.screens.Screen;

/**
 * 虚拟坐标系统辅助类
 *
 * 设计原理：
 * 1. 虚拟基准尺寸 640×360（基于 2560×1440 全屏 + GUI缩放4 的内部渲染尺寸）
 * 2. 所有元素按虚拟尺寸设计，运行时自动等比缩放到实际屏幕
 * 3. 保证不同分辨率、不同 GUI 缩放下 UI 显示一致
 */
public class VirtualCoordinateHelper {

    /** 虚拟基准宽度 */
    public static final int BASE_WIDTH = 640;

    /** 虚拟基准高度 */
    public static final int BASE_HEIGHT = 360;

    /**
     * 计算缩放比例和虚拟画布尺寸
     *
     * @param screen 当前屏幕
     * @param result 用于存储计算结果
     */
    public static void calculateVirtualSize(Screen screen, VirtualSizeResult result) {
        // 计算屏幕尺寸到基准尺寸的缩放比例
        int screenWidth = Math.max(1, screen.width);
        int screenHeight = Math.max(1, screen.height);
        float scaleX = (float) screenWidth / BASE_WIDTH;
        float scaleY = (float) screenHeight / BASE_HEIGHT;

        // 取较小值，确保内容完整显示（可能有黑边，但不会裁剪）
        result.uiScale = Math.min(scaleX, scaleY);

        // 反向计算虚拟画布尺寸
        result.virtualWidth = Math.max(1, (int) (screenWidth / result.uiScale));
        result.virtualHeight = Math.max(1, (int) (screenHeight / result.uiScale));
    }

    /** 保持原始大窗口布局，只在画布小于 640×360 时整体缩小。 */
    public static void calculateDownscaledVirtualSize(Screen screen, VirtualSizeResult result) {
        calculateVirtualSize(screen, result);
        if (result.uiScale > 1.0f) {
            result.uiScale = 1.0f;
            result.virtualWidth = Math.max(1, screen.width);
            result.virtualHeight = Math.max(1, screen.height);
        }
    }

    /**
     * 虚拟尺寸计算结果
     */
    public static class VirtualSizeResult {
        /** 虚拟坐标到屏幕坐标的缩放比例 */
        public float uiScale;

        /** 虚拟画布宽度 */
        public int virtualWidth;

        /** 虚拟画布高度 */
        public int virtualHeight;
    }
}
