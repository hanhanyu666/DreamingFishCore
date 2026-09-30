package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.anim.AnimatedFloat;
import com.hhy.dreamingfishcore.client.ui.framework.anim.Spring;
import com.hhy.dreamingfishcore.client.ui.framework.core.UiClock;
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
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.InteractiveNode;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 终端底部 Dock：悬浮胶囊中的模块入口，选中指示块以弹簧动画滑动，未读项显示呼吸红点。
 */
final class TerminalDock extends UiNode<TerminalDock> {
    private final List<Item> items = new ArrayList<>();
    private final AnimatedFloat indicatorX = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
    private final AnimatedFloat indicatorW = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
    private final Consumer<TerminalScreen.Tab> onSelect;
    private TerminalScreen.Tab selected = TerminalScreen.Tab.HOME;
    private boolean positioned;
    private boolean compact;

    TerminalDock(Consumer<TerminalScreen.Tab> onSelect) {
        this.onSelect = onSelect;
        row().alignItems(Align.STRETCH).justify(Justify.CENTER).padding(3.0F).gap(2.0F).radius(Theme.Radius.XL);
    }

    TerminalDock item(TerminalScreen.Tab tab, Icons icon, String label, BooleanSupplier unread) {
        Item item = new Item(this, tab, icon, label, unread);
        items.add(item);
        add(item);
        return this;
    }

    /** 紧凑模式：只显示图标。 */
    TerminalDock compact(boolean value) {
        compact = value;
        for (Item item : items) {
            item.setCompact(value);
        }
        return this;
    }

    void select(TerminalScreen.Tab tab) {
        selected = tab;
        for (Item item : items) {
            item.selected(item.tab == tab);
        }
    }

    @Override
    protected void update() {
        for (Item item : items) {
            if (item.tab == selected && item.width() > 0.0F) {
                if (!positioned) {
                    indicatorX.snap(item.x());
                    indicatorW.snap(item.width());
                    positioned = true;
                } else {
                    indicatorX.set(item.x());
                    indicatorW.set(item.width());
                }
            }
        }
    }

    @Override
    protected void paintBackground(UiCanvas canvas) {
        Theme theme = theme();
        canvas.shape(0.0F, 0.0F, width(), height()).radius(height() * 0.5F)
                .verticalGradient(0xF0161F28, 0xF00E151C)
                .border(1.0F, theme.color(ColorRole.OUTLINE_STRONG))
                .shadow(Theme.Elevation.LEVEL3).draw();
        if (positioned) {
            float x = indicatorX.get();
            float w = indicatorW.get();
            float inset = padTop();
            float h = height() - inset * 2.0F;
            canvas.shape(x, inset, w, h).radius(h * 0.5F)
                    .verticalGradient(UiColor.withAlpha(TerminalUi.CYAN, 0.24F), UiColor.withAlpha(TerminalUi.CYAN, 0.12F))
                    .border(1.0F, UiColor.withAlpha(TerminalUi.CYAN, 0.45F)).draw();
        }
    }

    private static final class Item extends InteractiveNode<Item> {
        private final TerminalDock owner;
        private final TerminalScreen.Tab tab;
        private final BooleanSupplier unread;
        private final Icon icon;
        private final Text label;

        Item(TerminalDock owner, TerminalScreen.Tab tab, Icons iconType, String text, BooleanSupplier unread) {
            this.owner = owner;
            this.tab = tab;
            this.unread = unread;
            row().alignItems(Align.CENTER).justify(Justify.CENTER).gap(4.0F).padding(9.0F, 0.0F);
            cursor(Cursor.POINTER);
            focusable(true);
            icon = Icon.of(iconType, 11.0F);
            label = Text.of(text).style(TextStyle.LABEL).singleLine();
            add(icon, label);
            onClick(() -> owner.onSelect.accept(tab));
        }

        void setCompact(boolean compactMode) {
            label.visible(!compactMode);
            padding(compactMode ? 8.0F : 9.0F, 0.0F);
        }

        @Override
        protected void onMouseUp(double guiX, double guiY, int button, boolean inside) {
            if (inside && button == 0) {
                UiSounds.soft();
                owner.onSelect.accept(tab);
            }
        }

        @Override
        protected void update() {
            Theme theme = theme();
            float s = selection();
            int base = UiColor.lerp(theme.color(ColorRole.TEXT_MUTED), theme.color(ColorRole.TEXT_SECONDARY), hover());
            label.color(UiColor.lerp(base, theme.color(ColorRole.TEXT), s));
            icon.color(UiColor.lerp(base, TerminalUi.CYAN, s));
        }

        @Override
        protected void paintOverlay(UiCanvas canvas) {
            if (unread != null && unread.getAsBoolean()) {
                double t = (UiClock.now() % 1800.0) / 1800.0;
                float cx = width() - 7.0F;
                float cy = 5.5F;
                canvas.circle(cx, cy, 2.2F + (float) t * 3.0F, UiColor.withAlpha(TerminalUi.ROSE, (float) (1.0 - t) * 0.35F));
                canvas.circle(cx, cy, 2.2F, TerminalUi.ROSE);
            }
        }
    }
}
