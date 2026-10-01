package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.Size;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;

/** 自定义绘制：在节点范围内用画布自由绘制（图表、装饰、特效）。 */
public class CustomPaint extends UiNode<CustomPaint> {
    @FunctionalInterface
    public interface Painter {
        void paint(UiCanvas canvas, float width, float height);
    }

    private Painter painter;
    private float preferredWidth;
    private float preferredHeight;

    public CustomPaint(Painter painter) {
        this.painter = painter;
        pointerEvents(false);
    }

    public static CustomPaint of(Painter painter) {
        return new CustomPaint(painter);
    }

    public CustomPaint painter(Painter value) {
        painter = value;
        return this;
    }

    /** 未指定尺寸时的内容尺寸。 */
    public CustomPaint preferredSize(float widthValue, float heightValue) {
        preferredWidth = widthValue;
        preferredHeight = heightValue;
        markDirty();
        return this;
    }

    @Override
    protected void measureContent(float availableWidth, float availableHeight, Size out) {
        out.set(preferredWidth, preferredHeight);
    }

    @Override
    protected void paintContent(UiCanvas canvas) {
        if (painter != null) {
            painter.paint(canvas, width(), height());
        }
    }
}
