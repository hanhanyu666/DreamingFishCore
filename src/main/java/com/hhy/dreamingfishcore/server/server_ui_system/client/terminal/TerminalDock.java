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
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 终端的模块入口。常规布局下是底部悬浮胶囊（图标 + 文字）；紧凑布局下竖排成左侧导轨（只有图标，
 * 悬停显示名称），把纵向空间留给内容。选中指示块以弹簧动画滑动，未读项显示呼吸红点。
 */
final class TerminalDock extends UiNode<TerminalDock> {
    private final List<Item> items = new ArrayList<>();
    private final AnimatedFloat indicatorX = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
    private final AnimatedFloat indicatorY = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
    private final AnimatedFloat indicatorW = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
    private final AnimatedFloat indicatorH = AnimatedFloat.spring(0.0F, Spring.SNAPPY);
    private final Consumer<TerminalScreen.Tab> onSelect;
    private TerminalScreen.Tab selected = TerminalScreen.Tab.HOME;
    private boolean positioned;
    private boolean rail;

    TerminalDock(Consumer<TerminalScreen.Tab> onSelect) {
        this.onSelect = onSelect;
        radius(Theme.Radius.XL);
        applyOrientation();
    }

    TerminalDock item(TerminalScreen.Tab tab, Icons icon, String label, BooleanSupplier unread) {
        Item item = new Item(this, tab, icon, label, unread);
        item.setRail(rail);
        items.add(item);
        add(item);
        return this;
    }

    /** 切换为左侧竖排导轨（紧凑布局）或底部胶囊。 */
    TerminalDock rail(boolean value) {
        if (rail == value) {
            return this;
        }
        rail = value;
        positioned = false;
        applyOrientation();
        for (Item item : items) {
            item.setRail(value);
        }
        return this;
    }

    boolean isRail() {
        return rail;
    }

    private void applyOrientation() {
        if (rail) {
            column().alignItems(Align.STRETCH).justify(Justify.CENTER).padding(3.0F).gap(3.0F);
        } else {
            row().alignItems(Align.STRETCH).justify(Justify.CENTER).padding(3.0F).gap(2.0F);
        }
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
                    indicatorY.snap(item.y());
                    indicatorW.snap(item.width());
                    indicatorH.snap(item.height());
                    positioned = true;
                } else {
                    indicatorX.set(item.x());
                    indicatorY.set(item.y());
                    indicatorW.set(item.width());
                    indicatorH.set(item.height());
                }
            }
        }
    }

    @Override
    protected void paintBackground(UiCanvas canvas) {
        Theme theme = theme();
        float w = width();
        float h = height();
        canvas.shape(0.0F, 0.0F, w, h).radius(Math.min(w, h) * 0.5F)
                .verticalGradient(0xF0161F28, 0xF00E151C)
                .border(1.0F, theme.color(ColorRole.OUTLINE_STRONG))
                .shadow(Theme.Elevation.LEVEL3).draw();
        // 胶囊外缘的一道反光，中间亮两端暗：横排在顶缘，竖排在左缘
        int glow = UiColor.withAlpha(TerminalUi.CYAN, 0.28F);
        int clear = UiColor.withAlpha(TerminalUi.CYAN, 0.0F);
        if (rail) {
            float edge = w * 0.5F;
            float half = h * 0.5F;
            canvas.shape(0.5F, edge, 1.0F, half - edge).verticalGradient(clear, glow).draw();
            canvas.shape(0.5F, half, 1.0F, half - edge).verticalGradient(glow, clear).draw();
        } else {
            float edge = h * 0.5F;
            float half = w * 0.5F;
            canvas.shape(edge, 0.5F, half - edge, 1.0F).horizontalGradient(clear, glow).draw();
            canvas.shape(half, 0.5F, half - edge, 1.0F).horizontalGradient(glow, clear).draw();
        }
        if (!positioned) {
            return;
        }
        float x = indicatorX.get();
        float y = indicatorY.get();
        float iw = indicatorW.get();
        float ih = indicatorH.get();
        canvas.shape(x, y, iw, ih).radius(Math.min(iw, ih) * 0.5F)
                .verticalGradient(UiColor.withAlpha(TerminalUi.CYAN, 0.24F), UiColor.withAlpha(TerminalUi.CYAN, 0.10F))
                .border(1.0F, UiColor.withAlpha(TerminalUi.CYAN, 0.40F)).draw();
        // 指示块的信标灯：横排在底缘，竖排在左缘
        if (rail) {
            float cy = y + ih * 0.5F;
            float bx = x + 0.75F;
            canvas.shape(bx - 2.25F, cy - 8.0F, 4.0F, 16.0F).radius(2.0F)
                    .fill(UiColor.withAlpha(TerminalUi.CYAN, 0.16F)).draw();
            canvas.shape(bx - 0.75F, cy - 4.5F, 1.5F, 9.0F).radius(0.75F)
                    .verticalGradient(UiColor.withAlpha(TerminalUi.CYAN, 0.5F), 0xFFE8FBFF).draw();
        } else {
            float cx = x + iw * 0.5F;
            float by = y + ih - 0.75F;
            canvas.shape(cx - 9.0F, by - 1.75F, 18.0F, 4.0F).radius(2.0F)
                    .fill(UiColor.withAlpha(TerminalUi.CYAN, 0.16F)).draw();
            canvas.shape(cx - 5.0F, by - 0.5F, 10.0F, 1.5F).radius(0.75F)
                    .horizontalGradient(UiColor.withAlpha(TerminalUi.CYAN, 0.5F), 0xFFE8FBFF).draw();
        }
    }

    private static final class Item extends InteractiveNode<Item> {
        private final TerminalDock owner;
        private final TerminalScreen.Tab tab;
        private final BooleanSupplier unread;
        private final Icon icon;
        private final Text label;
        private final Component name;

        Item(TerminalDock owner, TerminalScreen.Tab tab, Icons iconType, String text, BooleanSupplier unread) {
            this.owner = owner;
            this.tab = tab;
            this.unread = unread;
            this.name = Component.literal(text);
            row().alignItems(Align.CENTER).justify(Justify.CENTER).gap(4.0F);
            cursor(Cursor.POINTER);
            focusable(true);
            icon = Icon.of(iconType, 11.0F);
            label = Text.of(text).style(TextStyle.LABEL).singleLine();
            add(icon, label);
            onClick(() -> owner.onSelect.accept(tab));
        }

        void setRail(boolean railMode) {
            label.visible(!railMode);
            padding(railMode ? 7.0F : 9.0F, railMode ? 6.0F : 0.0F);
            tooltip(railMode ? name : null);
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
                // 角标贴在图标右上角，不挤占文字
                float cx = icon.x() + icon.width() - 0.5F;
                float cy = icon.y() + 1.0F;
                canvas.circle(cx, cy, 2.2F + (float) t * 3.0F, UiColor.withAlpha(TerminalUi.ROSE, (float) (1.0 - t) * 0.35F));
                canvas.circle(cx, cy, 2.2F, TerminalUi.ROSE);
            }
        }
    }
}
