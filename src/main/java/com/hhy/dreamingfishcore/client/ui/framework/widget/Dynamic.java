package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 按数据重建的子树：每帧读取 {@code key}，变化（equals 比较）时用 {@code builder} 重建唯一子节点。
 *
 * <p>适合"数据快照 → 界面"的场景：key 可以是客户端缓存里的列表、记录或版本号。
 * 可设置切换时新内容的进场效果。</p>
 */
public class Dynamic<K> extends UiNode<Dynamic<K>> {
    private final Supplier<K> key;
    private final Function<K, UiNode<?>> builder;
    private EnterEffect transition = EnterEffect.NONE;
    private boolean built;
    private K current;

    public Dynamic(Supplier<K> key, Function<K, UiNode<?>> builder) {
        this.key = key;
        this.builder = builder;
        // 叠放 + 拉伸：唯一的子节点铺满容器，同时容器仍可按内容测量尺寸
        stack().alignItems(Align.STRETCH);
    }

    public static <K> Dynamic<K> of(Supplier<K> key, Function<K, UiNode<?>> builder) {
        return new Dynamic<>(key, builder);
    }

    /** 内容切换时（不含首次）新内容的进场效果。 */
    public Dynamic<K> transition(EnterEffect effect) {
        transition = effect == null ? EnterEffect.NONE : effect;
        return this;
    }

    /** 强制下一帧重建。 */
    public Dynamic<K> refresh() {
        built = false;
        return this;
    }

    @Override
    protected void update() {
        K next = key.get();
        if (built && Objects.equals(next, current)) {
            return;
        }
        boolean first = !built;
        built = true;
        current = next;
        UiNode<?> child = builder.apply(next);
        if (child == null) {
            clearChildren();
            return;
        }
        if (!first && !transition.isNone()) {
            child.enter(transition);
        }
        setChildren(List.of(child));
    }
}
