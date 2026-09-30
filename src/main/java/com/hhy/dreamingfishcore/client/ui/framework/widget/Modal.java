package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Easing;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiRoot;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;

/**
 * 模态浮层：全屏遮罩 + 居中内容。点击遮罩或按 Esc 关闭（可禁用），关闭时淡出后移除。
 */
public class Modal extends UiNode<Modal> {
    private final UiNode<?> content;
    private final AnimatedFloat scrim = AnimatedFloat.tween(0.0F, Theme.Motion.NORMAL, Easing.STANDARD);
    private boolean dismissible = true;
    private boolean closing;
    private Runnable onDismiss;

    private Modal(UiNode<?> content) {
        this.content = content;
        stack().alignItems(Align.CENTER);
        padding(Theme.Space.XL);
        add(content);
        content.enter(EnterEffect.POP);
        scrim.set(1.0F);
    }

    /** 在根上弹出模态内容。 */
    public static Modal show(UiRoot root, UiNode<?> content) {
        Modal modal = new Modal(content);
        root.pushOverlay(modal);
        return modal;
    }

    public Modal dismissible(boolean value) {
        dismissible = value;
        return this;
    }

    public Modal onDismiss(Runnable action) {
        onDismiss = action;
        return this;
    }

    public boolean isDismissible() {
        return dismissible;
    }

    public boolean isClosing() {
        return closing;
    }

    /** 播放退出动画后移除。 */
    public void dismiss() {
        if (closing) {
            return;
        }
        closing = true;
        scrim.set(0.0F);
        content.animateOpacity(0.0F);
        content.animateScale(0.97F);
        if (onDismiss != null) {
            onDismiss.run();
        }
    }

    @Override
    protected void update() {
        if (closing && scrim.get() <= 0.01F && root() != null) {
            root().removeOverlay(this);
        }
    }

    @Override
    protected boolean onMouseDown(double guiX, double guiY, int button) {
        if (!content.containsPoint(guiX, guiY) && dismissible && !closing) {
            dismiss();
        }
        return true;
    }

    @Override
    protected void onMouseUp(double guiX, double guiY, int button, boolean inside) {
    }

    @Override
    protected boolean onScroll(double guiX, double guiY, double scrollX, double scrollY) {
        return true;
    }

    @Override
    protected void paintBackground(UiCanvas canvas) {
        int color = theme().color(ColorRole.SCRIM);
        canvas.fill(0.0F, 0.0F, width(), height(), UiColor.multiplyAlpha(color, scrim.get() * 1.2F));
    }
}
