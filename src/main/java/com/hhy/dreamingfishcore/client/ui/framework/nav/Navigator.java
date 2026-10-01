package com.hhy.dreamingfishcore.client.ui.framework.nav;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 页面栈。推入时新页从右侧滑入、旧页向左淡出；返回时反向。
 * 切换顶层页面（{@link #reset}）使用淡入 + 轻微上移。
 */
public class Navigator extends UiNode<Navigator> {
    private static final float SLIDE = 28.0F;

    private final List<Page> stack = new ArrayList<>();
    private final List<Consumer<Page>> listeners = new ArrayList<>();
    private final AnimatedFloat progress = AnimatedFloat.tween(1.0F, Theme.Motion.PAGE, Theme.Motion.ENTER);
    private UiNode<?> leavingNode;
    private int direction;

    public Navigator() {
        stack().alignItems(Align.STRETCH);
        clip(true);
    }

    public static Navigator of(Page root) {
        Navigator navigator = new Navigator();
        navigator.reset(root, false);
        return navigator;
    }

    public Navigator onChange(Consumer<Page> listener) {
        listeners.add(listener);
        return this;
    }

    public Page current() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    public int depth() {
        return stack.size();
    }

    public boolean canPop() {
        return stack.size() > 1;
    }

    public List<Page> pages() {
        return List.copyOf(stack);
    }

    public void push(Page page) {
        Page previous = current();
        if (previous != null) {
            previous.onHide();
        }
        page.attach(this);
        stack.add(page);
        show(previous, page, 1, true);
    }

    public boolean pop() {
        if (!canPop()) {
            return false;
        }
        Page leaving = stack.remove(stack.size() - 1);
        leaving.onHide();
        show(leaving, current(), -1, true);
        return true;
    }

    /** 返回：先交给当前页处理，再尝试出栈。 */
    public boolean back() {
        Page page = current();
        if (page != null && page.onBack()) {
            return true;
        }
        return pop();
    }

    /** 替换整个栈为单个页面。 */
    public void reset(Page root, boolean animate) {
        Page previous = current();
        if (previous != null) {
            previous.onHide();
        }
        stack.clear();
        root.attach(this);
        stack.add(root);
        show(previous, root, 0, animate);
    }

    /** 替换当前页（不增加栈深度）。 */
    public void replace(Page page) {
        Page previous = current();
        if (previous != null) {
            previous.onHide();
            stack.remove(stack.size() - 1);
        }
        page.attach(this);
        stack.add(page);
        show(previous, page, 0, true);
    }

    private void show(Page from, Page to, int dir, boolean animate) {
        UiNode<?> entering = to.node();
        UiNode<?> leaving = from != null && from.isBuilt() ? from.node() : null;
        if (leaving == entering) {
            leaving = null;
        }
        direction = dir;
        List<UiNode<?>> nodes = new ArrayList<>(2);
        if (animate && leaving != null) {
            nodes.add(leaving);
            leavingNode = leaving;
            leaving.pointerEvents(false);
        } else {
            leavingNode = null;
        }
        entering.pointerEvents(true);
        nodes.add(entering);
        setChildren(nodes);
        if (animate) {
            progress.snap(0.0F).set(1.0F);
        } else {
            progress.snap(1.0F);
        }
        to.onShow();
        for (Consumer<Page> listener : listeners) {
            listener.accept(to);
        }
    }

    @Override
    protected void update() {
        if (leavingNode != null && progress.get() >= 1.0F) {
            UiNode<?> done = leavingNode;
            leavingNode = null;
            remove(done);
            done.opacity(1.0F).translate(0.0F, 0.0F);
        }
    }

    @Override
    protected void paintChildren(UiCanvas canvas) {
        float p = progress.get();
        UiNode<?> entering = children().isEmpty() ? null : children().get(children().size() - 1);
        for (UiNode<?> child : children()) {
            if (child == leavingNode) {
                float offset = direction == 0 ? 0.0F : -direction * SLIDE * 0.5F * p;
                child.opacity(1.0F - p).translate(offset, direction == 0 ? -4.0F * p : 0.0F);
            } else if (child == entering) {
                float offset = direction == 0 ? 0.0F : direction * SLIDE * (1.0F - p);
                child.opacity(p < 1.0F ? Math.min(1.0F, p * 1.3F) : 1.0F)
                        .translate(offset, direction == 0 ? 6.0F * (1.0F - p) : 0.0F);
            }
            child.paint(canvas);
        }
    }
}
