package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;

import java.util.List;
import java.util.function.Function;

/**
 * 响应式容器：按界面宽度所属档位构建内容，档位变化时重建。
 *
 * <p>档位按 GUI 像素宽度划分：紧凑（&lt; 560，如 1080p 下 GUI 缩放 4）、
 * 常规（560–860）、宽屏（≥ 860）。</p>
 */
public class Responsive extends UiNode<Responsive> {
    public enum Size {
        COMPACT,
        REGULAR,
        WIDE;

        public boolean atLeast(Size other) {
            return ordinal() >= other.ordinal();
        }
    }

    public static final float REGULAR_MIN = 560.0F;
    public static final float WIDE_MIN = 860.0F;

    private final Function<Size, UiNode<?>> builder;
    private Size current;

    public Responsive(Function<Size, UiNode<?>> builder) {
        this.builder = builder;
        // 叠放 + 拉伸：唯一的子节点铺满容器，同时容器仍可按内容测量尺寸
        stack().alignItems(Align.STRETCH);
    }

    public static Responsive of(Function<Size, UiNode<?>> builder) {
        return new Responsive(builder);
    }

    public static Size classify(float guiWidth) {
        if (guiWidth >= WIDE_MIN) {
            return Size.WIDE;
        }
        return guiWidth >= REGULAR_MIN ? Size.REGULAR : Size.COMPACT;
    }

    public Size size() {
        return current == null ? Size.REGULAR : current;
    }

    @Override
    protected void update() {
        float width = root() != null && root().width() > 0.0F ? root().width()
                : net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
        Size next = classify(width);
        if (next != current) {
            current = next;
            UiNode<?> child = builder.apply(next);
            setChildren(child == null ? List.of() : List.of(child));
        }
    }
}
