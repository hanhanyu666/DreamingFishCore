package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Spring;
import com.hhy.dreamingfishcore.client.ui.framework.core.UiSounds;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Cursor;
import com.hhy.dreamingfishcore.client.ui.framework.node.Justify;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 分段选择器 / 标签栏。选中指示块以弹簧动画在选项之间滑动。
 */
public class Segmented extends UiNode<Segmented> {
    private final List<Item> items = new ArrayList<>();
    private final AnimatedFloat indicatorX = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
    private final AnimatedFloat indicatorW = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
    private int selected;
    private IntConsumer onSelect;
    private boolean underline;
    private boolean positioned;

    public Segmented() {
        row().alignItems(Align.STRETCH).padding(2.0F).gap(2.0F).radius(Theme.Radius.MD);
        height(20.0F);
    }

    public static Segmented of(List<String> labels, int selected, IntConsumer onSelect) {
        Segmented segmented = new Segmented();
        for (String label : labels) {
            segmented.option(label, null, 0);
        }
        segmented.selected = Math.max(0, Math.min(labels.size() - 1, selected));
        segmented.onSelect = onSelect;
        segmented.syncSelection();
        return segmented;
    }

    /** 添加选项；{@code count} 大于 0 时显示计数徽标。 */
    public Segmented option(String label, Icons icon, int count) {
        int index = items.size();
        Item item = new Item(this, index, label, icon, count);
        items.add(item);
        add(item);
        return this;
    }

    public Segmented onSelect(IntConsumer listener) {
        onSelect = listener;
        return this;
    }

    /** 下划线样式：无底色，选中项下方显示指示线。 */
    public Segmented underline() {
        underline = true;
        padding(0.0F);
        return this;
    }

    /** 选项等宽平分。 */
    public Segmented equalWidth() {
        for (Item item : items) {
            item.grow(1.0F).basis(0.0F);
        }
        return this;
    }

    public int selected() {
        return selected;
    }

    public Segmented select(int index, boolean notify) {
        if (index < 0 || index >= items.size()) {
            return this;
        }
        boolean changed = index != selected;
        selected = index;
        syncSelection();
        if (changed && notify && onSelect != null) {
            onSelect.accept(index);
        }
        return this;
    }

    public Segmented setCount(int index, int count) {
        if (index >= 0 && index < items.size()) {
            items.get(index).setCount(count);
        }
        return this;
    }

    private void syncSelection() {
        for (Item item : items) {
            item.selected(item.index == selected);
        }
    }

    @Override
    protected void update() {
        if (items.isEmpty() || selected >= items.size()) {
            return;
        }
        Item current = items.get(selected);
        if (current.width() <= 0.0F) {
            return;
        }
        if (!positioned) {
            indicatorX.snap(current.x());
            indicatorW.snap(current.width());
            positioned = true;
        } else {
            indicatorX.set(current.x());
            indicatorW.set(current.width());
        }
    }

    @Override
    protected void paintBackground(UiCanvas canvas) {
        Theme theme = theme();
        if (!underline) {
            canvas.shape(0.0F, 0.0F, width(), height()).radius(radiusValue())
                    .fill(theme.color(ColorRole.SURFACE_SUNKEN)).border(1.0F, theme.color(ColorRole.OUTLINE)).draw();
        }
        if (!positioned) {
            return;
        }
        float x = indicatorX.get();
        float w = indicatorW.get();
        if (underline) {
            float lineW = Math.max(8.0F, w - 8.0F);
            canvas.shape(x + (w - lineW) * 0.5F, height() - 2.0F, lineW, 2.0F).radius(1.0F)
                    .fill(theme.color(ColorRole.ACCENT)).draw();
        } else {
            float inner = Math.max(0.0F, radiusValue() - 1.0F);
            canvas.shape(x, padTop(), w, height() - padTop() - padBottom()).radius(inner)
                    .fill(theme.color(ColorRole.SURFACE_HOVER))
                    .border(1.0F, UiColor.multiplyAlpha(theme.color(ColorRole.ACCENT), 0.35F))
                    .shadow(Theme.Elevation.LEVEL1).draw();
        }
    }

    private static final class Item extends InteractiveNode<Item> {
        private final Segmented owner;
        private final int index;
        private final Text label;
        private final Icon icon;
        private final Badge badge;

        Item(Segmented owner, int index, String text, Icons iconType, int count) {
            this.owner = owner;
            this.index = index;
            row().alignItems(Align.CENTER).justify(Justify.CENTER).gap(4.0F).padding(8.0F, 0.0F);
            cursor(Cursor.POINTER);
            focusable(true);
            icon = iconType != null ? Icon.of(iconType, 10.0F) : null;
            label = Text.of(text).style(TextStyle.LABEL).singleLine();
            badge = Badge.of(String.valueOf(count), ColorRole.ACCENT).solid();
            badge.visible(count > 0);
            if (icon != null) {
                add(icon);
            }
            add(label, badge);
            onClick(() -> owner.select(index, true));
        }

        void setCount(int count) {
            badge.text(count > 99 ? "99+" : String.valueOf(count));
            badge.visible(count > 0);
        }

        @Override
        protected void onMouseUp(double guiX, double guiY, int button, boolean inside) {
            if (inside && button == 0 && !isEffectivelyDisabled()) {
                UiSounds.soft();
                owner.select(index, true);
            }
        }

        @Override
        protected void update() {
            Theme theme = theme();
            int color = UiColor.lerp(UiColor.lerp(theme.color(ColorRole.TEXT_MUTED), theme.color(ColorRole.TEXT_SECONDARY), hover()),
                    theme.color(ColorRole.TEXT), selection());
            label.color(color);
            if (icon != null) {
                icon.color(UiColor.lerp(color, theme.color(ColorRole.ACCENT), selection()));
            }
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            float hover = hover() * (1.0F - selection());
            if (hover > 0.01F) {
                canvas.shape(0.0F, 0.0F, width(), height()).radius(Math.max(0.0F, owner.radiusValue() - 1.0F))
                        .fill(UiColor.withAlpha(0xFFFFFFFF, (int) (hover * 10))).draw();
            }
        }
    }
}
