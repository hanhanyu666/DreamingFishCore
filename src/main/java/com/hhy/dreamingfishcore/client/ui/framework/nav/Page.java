package com.hhy.dreamingfishcore.client.ui.framework.nav;

import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import net.minecraft.network.chat.Component;

/**
 * 导航页面。{@link #build()} 在页面首次进入时调用一次；返回上一页时复用已构建的节点。
 */
public abstract class Page {
    private Navigator navigator;
    private UiNode<?> node;

    /** 页面标题（显示在外壳的顶栏）。 */
    public Component title() {
        return Component.empty();
    }

    protected abstract UiNode<?> build();

    /** 页面变为当前页时调用（首次进入与从子页返回）。 */
    protected void onShow() {
    }

    /** 页面不再是当前页时调用（进入子页或被移除）。 */
    protected void onHide() {
    }

    /** 页面拦截 Esc / 返回；返回 true 表示已处理。 */
    protected boolean onBack() {
        return false;
    }

    public Navigator navigator() {
        return navigator;
    }

    void attach(Navigator owner) {
        navigator = owner;
    }

    UiNode<?> node() {
        if (node == null) {
            node = build();
        }
        return node;
    }

    boolean isBuilt() {
        return node != null;
    }

    /** 丢弃已构建的节点，下次显示时重建。 */
    protected void invalidate() {
        node = null;
    }
}
