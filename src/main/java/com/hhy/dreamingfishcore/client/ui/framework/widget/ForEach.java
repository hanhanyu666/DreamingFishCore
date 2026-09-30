package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 按 key 同步的列表。数据变化时只重建变化的项，未变化的项保留节点（含其动画与状态）。
 *
 * <p>默认纵向排列，可像普通容器一样设置 gap、方向与换行。新出现的项可依次播放进场效果。</p>
 */
public class ForEach<T> extends UiNode<ForEach<T>> {
    private final Supplier<? extends List<T>> source;
    private final Function<T, Object> keyOf;
    private final Function<T, UiNode<?>> builder;
    private Map<Object, Entry<T>> entries = new HashMap<>();
    private List<T> lastItems;
    private EnterEffect enterEffect = EnterEffect.NONE;
    private float staggerMs;
    private Supplier<UiNode<?>> emptyBuilder;
    private UiNode<?> emptyNode;
    private boolean initialized;

    public ForEach(Supplier<? extends List<T>> source, Function<T, Object> keyOf, Function<T, UiNode<?>> builder) {
        this.source = source;
        this.keyOf = keyOf;
        this.builder = builder;
        column().alignItems(Align.STRETCH);
    }

    public static <T> ForEach<T> of(Supplier<? extends List<T>> source, Function<T, Object> keyOf,
                                    Function<T, UiNode<?>> builder) {
        return new ForEach<>(source, keyOf, builder);
    }

    /** 新项的进场效果，按顺序依次延迟 {@code staggerMs}。 */
    public ForEach<T> enter(EnterEffect effect, float staggerMs) {
        this.enterEffect = effect == null ? EnterEffect.NONE : effect;
        this.staggerMs = staggerMs;
        return this;
    }

    /** 列表为空时显示的内容。 */
    public ForEach<T> empty(Supplier<UiNode<?>> builderForEmpty) {
        this.emptyBuilder = builderForEmpty;
        return this;
    }

    /** 强制下一帧重新比对。 */
    public ForEach<T> refresh() {
        lastItems = null;
        return this;
    }

    @Override
    protected void update() {
        List<T> items = source.get();
        if (items == null) {
            items = List.of();
        }
        if (initialized && lastItems != null && lastItems.equals(items)) {
            return;
        }
        lastItems = new ArrayList<>(items);
        if (items.isEmpty()) {
            entries.clear();
            if (emptyBuilder != null) {
                if (emptyNode == null) {
                    emptyNode = emptyBuilder.get();
                }
                setChildren(List.of(emptyNode));
            } else {
                clearChildren();
            }
            initialized = true;
            return;
        }
        emptyNode = null;
        Map<Object, Entry<T>> next = new HashMap<>();
        List<UiNode<?>> nodes = new ArrayList<>(items.size());
        int created = 0;
        for (T item : items) {
            Object key = keyOf.apply(item);
            Entry<T> entry = entries.get(key);
            if (entry == null || !Objects.equals(entry.item, item)) {
                UiNode<?> node = builder.apply(item);
                if (!enterEffect.isNone() && (entry == null)) {
                    node.enter(enterEffect.delayed(enterEffect.delay() + staggerMs * created));
                    created++;
                }
                entry = new Entry<>(item, node);
            }
            next.put(key, entry);
            nodes.add(entry.node);
        }
        entries = next;
        setChildren(nodes);
        initialized = true;
    }

    private record Entry<T>(T item, UiNode<?> node) {
    }
}
