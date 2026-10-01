package com.hhy.dreamingfishcore.client.ui.framework.widget;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.Justify;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;

/** 空状态：图标 + 标题 + 说明，居中显示。 */
public final class EmptyState {
    private EmptyState() {
    }

    public static UiNode<?> of(Icons icon, String title, String subtitle) {
        Box box = Ui.column().alignItems(Align.CENTER).justify(Justify.CENTER).gap(Theme.Space.SM)
                .padding(Theme.Space.XL);
        Box badge = Ui.stack(Icon.of(icon, 16.0F).color(ColorRole.TEXT_MUTED))
                .alignItems(Align.CENTER).size(34.0F, 34.0F).radius(Theme.Radius.FULL)
                .background(0x10FFFFFF).border(1.0F, 0x14FFFFFF);
        box.add(badge);
        box.add(Text.of(title).style(TextStyle.SUBTITLE).color(ColorRole.TEXT_SECONDARY).centered());
        if (subtitle != null && !subtitle.isBlank()) {
            box.add(Text.of(subtitle).style(TextStyle.CAPTION).centered().maxWidth(220.0F));
        }
        return box;
    }
}
