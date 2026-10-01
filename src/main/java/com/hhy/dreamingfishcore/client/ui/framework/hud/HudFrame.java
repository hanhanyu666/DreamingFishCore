package com.hhy.dreamingfishcore.client.ui.framework.hud;

import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** 一帧 HUD 绘制的上下文：画布、屏幕尺寸与时间。对象逐帧复用，不要保存。 */
public final class HudFrame {
    private UiCanvas canvas;
    private GuiGraphics graphics;
    private Minecraft minecraft;
    private int width;
    private int height;
    private long nowMillis;
    private float deltaSeconds;
    private float partialTick;

    void set(UiCanvas canvas, GuiGraphics graphics, Minecraft minecraft, long nowMillis, float deltaSeconds,
             float partialTick) {
        this.canvas = canvas;
        this.graphics = graphics;
        this.minecraft = minecraft;
        this.width = minecraft.getWindow().getGuiScaledWidth();
        this.height = minecraft.getWindow().getGuiScaledHeight();
        this.nowMillis = nowMillis;
        this.deltaSeconds = deltaSeconds;
        this.partialTick = partialTick;
    }

    public UiCanvas canvas() {
        return canvas;
    }

    /** 原版绘制入口；只在 {@link UiCanvas#custom} 回调或必须立即绘制的场合使用。 */
    public GuiGraphics graphics() {
        return graphics;
    }

    public Minecraft minecraft() {
        return minecraft;
    }

    public Font font() {
        return minecraft.font;
    }

    /** GUI 宽度。 */
    public int width() {
        return width;
    }

    /** GUI 高度。 */
    public int height() {
        return height;
    }

    /** 系统时钟（毫秒）。 */
    public long now() {
        return nowMillis;
    }

    /** 距上一帧 HUD 绘制的秒数，最大 0.1。 */
    public float delta() {
        return deltaSeconds;
    }

    public float partialTick() {
        return partialTick;
    }
}
