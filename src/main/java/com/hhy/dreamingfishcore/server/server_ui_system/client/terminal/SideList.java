package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.core.UiSounds;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Cursor;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.InteractiveNode;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 纵向选择列表（阶段、手册章节等）。选中项有强调色条与底色，横向模式为标签行。
 */
final class SideList<K> extends UiNode<SideList<K>> {
    private final List<Entry<K>> entries = new ArrayList<>();
    private final Consumer<K> onSelect;
    private final boolean horizontal;
    private K selected;

    SideList(boolean horizontal, Consumer<K> onSelect) {
        this.horizontal = horizontal;
        this.onSelect = onSelect;
        if (horizontal) {
            row().gap(Theme.Space.XS).wrap(true).alignItems(Align.CENTER);
        } else {
            column().gap(Theme.Space.XS).alignItems(Align.STRETCH);
        }
    }

    SideList<K> item(K key, String title, String subtitle, int accent) {
        Entry<K> entry = new Entry<>(this, key, title, subtitle, accent, horizontal);
        entries.add(entry);
        add(entry);
        return this;
    }

    SideList<K> select(K key) {
        selected = key;
        for (Entry<K> entry : entries) {
            entry.selectedImmediately(Objects.equals(entry.key, key));
        }
        return this;
    }

    private void choose(K key) {
        selected = key;
        for (Entry<K> entry : entries) {
            entry.selected(Objects.equals(entry.key, key));
        }
        onSelect.accept(key);
    }

    private static final class Entry<K> extends InteractiveNode<Entry<K>> {
        private final SideList<K> owner;
        private final K key;
        private final int accent;
        private final Text title;
        private final Text subtitle;
        private final boolean chip;

        Entry(SideList<K> owner, K key, String titleText, String subtitleText, int accent, boolean chip) {
            this.owner = owner;
            this.key = key;
            this.accent = accent;
            this.chip = chip;
            cursor(Cursor.POINTER);
            focusable(true);
            radius(Theme.Radius.MD);
            title = Text.of(titleText).style(chip ? TextStyle.LABEL : TextStyle.LABEL_STRONG).singleLine();
            subtitle = subtitleText == null || subtitleText.isBlank() || chip ? null
                    : Text.of(subtitleText).style(TextStyle.CAPTION).singleLine();
            if (chip) {
                row().alignItems(Align.CENTER).padding(8.0F, 4.0F);
                add(title);
            } else {
                column().gap(1.0F).padding(10.0F, 5.0F, 8.0F, 5.0F);
                add(title);
                if (subtitle != null) {
                    add(subtitle);
                }
            }
            onClick(() -> owner.choose(key));
        }

        @Override
        protected void onMouseUp(double guiX, double guiY, int button, boolean inside) {
            if (inside && button == 0) {
                UiSounds.soft();
                owner.choose(key);
            }
        }

        @Override
        protected void update() {
            Theme theme = theme();
            float s = selection();
            title.color(UiColor.lerp(UiColor.lerp(theme.color(ColorRole.TEXT_SECONDARY), theme.color(ColorRole.TEXT), hover()),
                    chip ? accent : theme.color(ColorRole.TEXT), s));
        }

        @Override
        protected void paintBackground(UiCanvas canvas) {
            float s = selection();
            float h = hover();
            float w = width();
            float ht = height();
            int fill = UiColor.lerp(UiColor.withAlpha(0xFFFFFFFF, h * 0.05F), UiColor.withAlpha(accent, 0.14F), s);
            canvas.shape(0.0F, 0.0F, w, ht).radius(chip ? ht * 0.5F : Theme.Radius.MD).fill(fill)
                    .border(1.0F, UiColor.lerp(UiColor.withAlpha(0xFFFFFFFF, chip ? 0.08F : h * 0.06F), UiColor.withAlpha(accent, 0.45F), s))
                    .draw();
            if (!chip && s > 0.01F) {
                float barH = (ht - 10.0F) * s;
                canvas.shape(0.0F, (ht - barH) * 0.5F, 3.0F, barH).radius(0.0F, 1.5F, 1.5F, 0.0F).fill(accent).draw();
            }
        }
    }

    /** 竖排列表外包一层标题。 */
    static UiNode<?> titled(String label, UiNode<?> list) {
        return Ui.column(TerminalUi.sectionLabel(label), list).gap(Theme.Space.SM);
    }
}
