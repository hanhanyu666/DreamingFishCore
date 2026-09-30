package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Easing;
import com.hhy.dreamingfishcore.client.ui.framework.core.UiClock;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;

import java.util.function.Consumer;

/**
 * 纵向滚动容器。本身是一个纵向 flex 容器，子节点即滚动内容。
 *
 * <p>滚轮平滑滚动；在空白处按住拖动可直接拖动内容，松手后按速度惯性滑行；
 * 拖过边界有橡皮筋阻尼并回弹。右侧滚动条在滚动或悬停时淡入，可拖动。</p>
 */
public class ScrollView extends UiNode<ScrollView> {
    private static final float WHEEL_STEP = 30.0F;
    private static final float THUMB_HIT = 8.0F;
    private static final float OVERSCROLL_RESISTANCE = 0.35F;

    private final AnimatedFloat offset = AnimatedFloat.tween(0.0F, 220.0F, Easing.OUT_CUBIC);
    private final AnimatedFloat barAlpha = AnimatedFloat.tween(0.0F, 180.0F, Easing.STANDARD);
    private float target;
    private boolean draggingContent;
    private boolean draggingThumb;
    private double dragStartY;
    private float dragStartOffset;
    private double lastDragY;
    private double lastDragTime;
    private float velocity;
    private double lastActivity = -10_000.0;
    private ColorRole fadeRole;
    private boolean showScrollbar = true;
    private Consumer<Float> onScrollChanged;
    private float lastReported = Float.NaN;

    public ScrollView() {
        column();
        clip(true);
        shrink(1.0F);
        minHeight(0.0F);
        alignItems(Align.STRETCH);
    }

    public static ScrollView of(UiNode<?>... children) {
        return new ScrollView().add(children);
    }

    /** 上下边缘渐隐到指定表面颜色（应与容器背景一致）。 */
    public ScrollView edgeFade(ColorRole surface) {
        fadeRole = surface;
        return this;
    }

    public ScrollView scrollbar(boolean value) {
        showScrollbar = value;
        return this;
    }

    public ScrollView onScrollChanged(Consumer<Float> listener) {
        onScrollChanged = listener;
        return this;
    }

    public float scrollY() {
        return offset.get();
    }

    public float scrollTarget() {
        return target;
    }

    public float contentHeight() {
        float bottom = 0.0F;
        for (UiNode<?> child : children()) {
            if (child.isVisible()) {
                bottom = Math.max(bottom, child.y() + child.height());
            }
        }
        return bottom + padBottom();
    }

    public float maxScroll() {
        return Math.max(0.0F, contentHeight() - height());
    }

    public ScrollView scrollTo(float y, boolean animate) {
        target = clamp(y);
        if (animate) {
            offset.set(target);
        } else {
            offset.snap(target);
        }
        markActivity();
        return this;
    }

    /** 滚动到让指定子孙节点完整可见。 */
    public ScrollView reveal(UiNode<?> node, boolean animate) {
        float top = 0.0F;
        for (UiNode<?> n = node; n != null && n != this; n = n.parent()) {
            top += n.y();
        }
        float bottom = top + node.height();
        float view = height();
        float current = target;
        if (top < current + padTop()) {
            scrollTo(top - padTop(), animate);
        } else if (bottom > current + view - padBottom()) {
            scrollTo(bottom - view + padBottom(), animate);
        }
        return this;
    }

    private float clamp(float value) {
        return Math.max(0.0F, Math.min(maxScroll(), value));
    }

    private void markActivity() {
        lastActivity = UiClock.now();
    }

    @Override
    protected void update() {
        float max = maxScroll();
        if (!draggingContent && !draggingThumb) {
            float clamped = Math.max(0.0F, Math.min(max, target));
            if (clamped != target) {
                target = clamped;
                offset.set(target);
            }
        }
        boolean active = UiClock.now() - lastActivity < 900.0 || draggingThumb || draggingContent
                || isHovered() && max > 0.0F;
        barAlpha.set(active && max > 0.0F ? 1.0F : 0.0F);
        if (onScrollChanged != null) {
            float current = offset.get();
            if (current != lastReported) {
                lastReported = current;
                onScrollChanged.accept(current);
            }
        }
    }

    @Override
    protected void paintChildren(UiCanvas canvas) {
        float current = offset.get();
        canvas.push();
        canvas.translate(0.0F, -current);
        super.paintChildren(canvas);
        canvas.pop();
    }

    @Override
    protected void paintOverlay(UiCanvas canvas) {
        float max = maxScroll();
        float current = offset.get();
        if (fadeRole != null && max > 0.0F) {
            int surface = theme().color(fadeRole);
            float fade = Math.min(14.0F, height() * 0.15F);
            float topAmount = Math.min(1.0F, current / 24.0F);
            float bottomAmount = Math.min(1.0F, (max - current) / 24.0F);
            if (topAmount > 0.01F) {
                canvas.shape(0.0F, 0.0F, width(), fade)
                        .verticalGradient(UiColor.multiplyAlpha(surface, topAmount), UiColor.withAlpha(surface, 0)).draw();
            }
            if (bottomAmount > 0.01F) {
                canvas.shape(0.0F, height() - fade, width(), fade)
                        .verticalGradient(UiColor.withAlpha(surface, 0), UiColor.multiplyAlpha(surface, bottomAmount)).draw();
            }
        }
        float alpha = barAlpha.get();
        if (!showScrollbar || max <= 0.0F || alpha <= 0.01F) {
            return;
        }
        float view = height();
        float content = contentHeight();
        float thumbH = Math.max(16.0F, view * view / content);
        float progress = max <= 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, current / max));
        float thumbY = 2.0F + (view - thumbH - 4.0F) * progress;
        float barW = draggingThumb ? 4.0F : 3.0F;
        int color = theme().color(ColorRole.TEXT_MUTED);
        canvas.shape(width() - barW - 2.0F, thumbY, barW, thumbH).radius(barW * 0.5F)
                .fill(UiColor.multiplyAlpha(color, alpha * (draggingThumb ? 0.9F : 0.6F))).draw();
    }

    // ==================== 输入 ====================

    @Override
    protected boolean onScroll(double guiX, double guiY, double scrollX, double scrollY) {
        float max = maxScroll();
        if (max <= 0.0F || scrollY == 0.0) {
            return false;
        }
        float next = clamp(target - (float) scrollY * WHEEL_STEP);
        if (next == target) {
            return false;
        }
        target = next;
        offset.set(target);
        markActivity();
        return true;
    }

    private float localScale() {
        float guiHeight = guiBottom() - guiTop();
        return height() > 0.0F && guiHeight > 0.0F ? guiHeight / height() : 1.0F;
    }

    @Override
    protected boolean onMouseDown(double guiX, double guiY, int button) {
        if (button != 0 || maxScroll() <= 0.0F) {
            return false;
        }
        float scale = localScale();
        float localX = (float) (guiX - guiLeft()) / scale;
        dragStartY = guiY;
        dragStartOffset = offset.get();
        lastDragY = guiY;
        lastDragTime = UiClock.now();
        velocity = 0.0F;
        if (showScrollbar && localX >= width() - THUMB_HIT) {
            draggingThumb = true;
            float localY = (float) (guiY - guiTop()) / scale;
            float view = height();
            float thumbH = Math.max(16.0F, view * view / contentHeight());
            float progress = (localY - thumbH * 0.5F) / Math.max(1.0F, view - thumbH);
            scrollTo(progress * maxScroll(), true);
            dragStartOffset = target;
        } else {
            draggingContent = true;
        }
        markActivity();
        return true;
    }

    @Override
    protected boolean onMouseDrag(double guiX, double guiY, int button, double dragX, double dragY) {
        float scale = localScale();
        float max = maxScroll();
        if (draggingThumb) {
            float view = height();
            float thumbH = Math.max(16.0F, view * view / contentHeight());
            float track = Math.max(1.0F, view - thumbH);
            float delta = (float) (guiY - dragStartY) / scale;
            target = clamp(dragStartOffset + delta / track * max);
            offset.snap(target);
            markActivity();
            return true;
        }
        if (draggingContent) {
            float raw = dragStartOffset - (float) (guiY - dragStartY) / scale;
            if (raw < 0.0F) {
                raw *= OVERSCROLL_RESISTANCE;
            } else if (raw > max) {
                raw = max + (raw - max) * OVERSCROLL_RESISTANCE;
            }
            target = raw;
            offset.snap(raw);
            double now = UiClock.now();
            double dt = Math.max(1.0, now - lastDragTime);
            float instant = (float) (-(guiY - lastDragY) / scale / dt);
            velocity = velocity * 0.6F + instant * 0.4F;
            lastDragY = guiY;
            lastDragTime = now;
            markActivity();
            return true;
        }
        return false;
    }

    @Override
    protected void onMouseUp(double guiX, double guiY, int button, boolean inside) {
        if (draggingContent) {
            draggingContent = false;
            boolean stale = UiClock.now() - lastDragTime > 80.0;
            float fling = stale ? 0.0F : velocity * 260.0F;
            target = clamp(target + fling);
            offset.duration(stale ? 260.0F : 520.0F).set(target);
            offset.duration(220.0F);
        }
        draggingThumb = false;
        markActivity();
    }
}
