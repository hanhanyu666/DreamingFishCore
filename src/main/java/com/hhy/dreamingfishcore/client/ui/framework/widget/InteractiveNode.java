package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Spring;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;

/**
 * 可交互组件的基类：悬停、按下、焦点三种状态各有一个 0–1 的弹簧动画值，
 * 子类在绘制时据此插值颜色、位移和缩放。
 */
public abstract class InteractiveNode<S extends InteractiveNode<S>> extends UiNode<S> {
    protected final AnimatedFloat hoverAnim = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
    protected final AnimatedFloat pressAnim = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
    protected final AnimatedFloat focusAnim = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
    private boolean selected;
    protected final AnimatedFloat selectAnim = AnimatedFloat.spring(0.0F, Spring.GENTLE);

    @Override
    protected void onStateChanged() {
        boolean enabled = !isEffectivelyDisabled();
        hoverAnim.set(isHovered() && enabled ? 1.0F : 0.0F);
        pressAnim.set(isPressed() && enabled ? 1.0F : 0.0F);
        focusAnim.set(isFocused() ? 1.0F : 0.0F);
    }

    /** 选中状态（用于标签、分段控件、列表项）。 */
    public S selected(boolean value) {
        if (selected != value) {
            selected = value;
            selectAnim.set(value ? 1.0F : 0.0F);
        }
        return self();
    }

    /** 立即切换选中状态，不播放动画。 */
    public S selectedImmediately(boolean value) {
        selected = value;
        selectAnim.snap(value ? 1.0F : 0.0F);
        return self();
    }

    public boolean isSelected() {
        return selected;
    }

    protected float hover() {
        return hoverAnim.get();
    }

    protected float press() {
        return pressAnim.get();
    }

    protected float focus() {
        return focusAnim.get();
    }

    protected float selection() {
        return selectAnim.get();
    }
}
