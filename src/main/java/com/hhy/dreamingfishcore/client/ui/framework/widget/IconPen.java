package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;

/** 在 24×24 网格上绘制线条图标的画笔。坐标按图标尺寸缩放。 */
public final class IconPen {
    private UiCanvas canvas;
    private float originX;
    private float originY;
    private float unit;
    private float stroke;
    private int color;

    IconPen() {
    }

    IconPen set(UiCanvas targetCanvas, float x, float y, float size, float strokeWidth, int argb) {
        canvas = targetCanvas;
        originX = x;
        originY = y;
        unit = size / 24.0F;
        stroke = strokeWidth * unit;
        color = argb;
        return this;
    }

    private float px(float gridX) {
        return originX + gridX * unit;
    }

    private float py(float gridY) {
        return originY + gridY * unit;
    }

    public void line(float x0, float y0, float x1, float y1) {
        canvas.line(px(x0), py(y0), px(x1), py(y1), stroke, color, true);
    }

    /** 折线：{@code points} 依次为 x0, y0, x1, y1, ... */
    public void poly(float... points) {
        for (int i = 0; i + 3 < points.length; i += 2) {
            line(points[i], points[i + 1], points[i + 2], points[i + 3]);
        }
    }

    /** 闭合折线。 */
    public void polygon(float... points) {
        poly(points);
        if (points.length >= 4) {
            line(points[points.length - 2], points[points.length - 1], points[0], points[1]);
        }
    }

    public void ring(float cx, float cy, float radius) {
        canvas.arc(px(cx), py(cy), radius * unit, stroke, 0.0F, (float) (Math.PI * 2.0), color, color);
    }

    /** 圆弧：角度单位为度，0 指向右，顺时针。 */
    public void arc(float cx, float cy, float radius, float startDegrees, float sweepDegrees) {
        canvas.arc(px(cx), py(cy), radius * unit, stroke,
                (float) Math.toRadians(startDegrees), (float) Math.toRadians(sweepDegrees), color, color);
    }

    public void dot(float cx, float cy, float radius) {
        canvas.circle(px(cx), py(cy), radius * unit, color);
    }

    public void roundRect(float x0, float y0, float x1, float y1, float radius) {
        canvas.shape(px(x0) - stroke * 0.5F, py(y0) - stroke * 0.5F, (x1 - x0) * unit + stroke, (y1 - y0) * unit + stroke)
                .radius(radius * unit + stroke * 0.5F).fill(0).border(stroke, color).draw();
    }

    public void fillRoundRect(float x0, float y0, float x1, float y1, float radius) {
        canvas.shape(px(x0), py(y0), (x1 - x0) * unit, (y1 - y0) * unit).radius(radius * unit).fill(color).draw();
    }

    /** 按参数方程采样的平滑曲线。 */
    public void curve(float[] xs, float[] ys) {
        for (int i = 0; i + 1 < xs.length; i++) {
            line(xs[i], ys[i], xs[i + 1], ys[i + 1]);
        }
    }
}
