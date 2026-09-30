package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Badge;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Card;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ProgressBar;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;

import java.util.function.Supplier;

/** 梦屿终端的视觉组件与模块配色。 */
public final class TerminalUi {
    // 模块强调色
    public static final int CYAN = 0xFF5CCFE6;
    public static final int SKY = 0xFF4FC3F7;
    public static final int MINT = 0xFF6FDDA8;
    public static final int GREEN = 0xFF5BD69A;
    public static final int GOLD = 0xFFFFC857;
    public static final int AMBER = 0xFFFFB84D;
    public static final int VIOLET = 0xFFB58BFF;
    public static final int PERIWINKLE = 0xFF8EA7FF;
    public static final int ROSE = 0xFFF06A7A;
    public static final int RED = 0xFFF0605A;
    public static final int STEEL = 0xFF8FA3B5;
    public static final int WARM_TEXT = 0xFFFFF0C2;

    private TerminalUi() {
    }

    /** 带色调底的图标徽章。 */
    public static Box iconBadge(Icons icon, int accent, float size) {
        return Ui.stack(Icon.of(icon, size * 0.56F).color(accent))
                .alignItems(Align.CENTER)
                .size(size, size)
                .radius(size * 0.3F)
                .background(UiColor.withAlpha(accent, 0.14F))
                .border(1.0F, UiColor.withAlpha(accent, 0.22F));
    }

    /** 卡片标题行：图标徽章 + 标题 + 右侧附加内容。 */
    public static Box header(Icons icon, int accent, String title, UiNode<?> trailing) {
        Box row = Ui.row(iconBadge(icon, accent, 18.0F),
                Text.of(title).style(TextStyle.SUBTITLE).singleLine().grow(1).shrink(1))
                .gap(Theme.Space.MD);
        if (trailing != null) {
            row.add(trailing);
        }
        return row;
    }

    /** 状态标签：带色调底色的胶囊。 */
    public static Badge chip(String text, int color) {
        return Badge.of(text).color(color);
    }

    public static Badge chip(Supplier<String> text, int color) {
        Badge badge = Badge.of("").color(color);
        badge.bind(text, badge::text);
        return badge;
    }

    /** 小号标题（区块标签）。 */
    public static Text sectionLabel(String text) {
        return Text.of(text).style(TextStyle.CAPTION_STRONG).color(ColorRole.TEXT_MUTED).singleLine();
    }

    /** 标签 + 数值的指标块。 */
    public static Box metric(String label, Supplier<String> value, int valueColor) {
        return Ui.column(
                Text.of(label).style(TextStyle.CAPTION).singleLine(),
                Text.of(() -> net.minecraft.network.chat.Component.literal(value.get()))
                        .style(TextStyle.TITLE).color(valueColor).singleLine()
        ).gap(2.0F);
    }

    /** 带左侧强调色条的数据块（个人档案等）。 */
    public static Card statTile(String label, Supplier<String> value, int accent) {
        return Card.of(
                Text.of(label).style(TextStyle.CAPTION).singleLine(),
                Text.of(() -> net.minecraft.network.chat.Component.literal(value.get()))
                        .style(TextStyle.SUBTITLE).singleLine()
        ).accent(accent).padding(Theme.Space.LG, Theme.Space.MD).gap(3.0F).elevation(Theme.Shadow.NONE, Theme.Elevation.LEVEL2);
    }

    /** 标签 + 数值 + 进度条。 */
    public static Box meter(String label, Supplier<String> value, Supplier<Float> progress, int color) {
        return Ui.column(
                Ui.row(Text.of(label).style(TextStyle.LABEL).singleLine().grow(1),
                        Text.of(() -> net.minecraft.network.chat.Component.literal(value.get()))
                                .style(TextStyle.LABEL_STRONG).singleLine()),
                ProgressBar.of(progress).color(color).gradientTo(UiColor.lighten(color, 0.25F)).thickness(4.0F)
        ).gap(4.0F);
    }

    /** 卡片底部的"查看更多"提示。 */
    public static Box footerLink(String text, int accent) {
        return Ui.row(Text.of(text).style(TextStyle.LABEL).color(accent).singleLine(),
                Icon.of(Icons.CHEVRON_RIGHT, 8.0F).color(accent)).gap(2.0F);
    }

    /** 标准终端卡片。 */
    public static Card card() {
        return new Card().padding(Theme.Space.LG).gap(Theme.Space.MD).radius(Theme.Radius.LG);
    }

    public static int rgb(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }
}
