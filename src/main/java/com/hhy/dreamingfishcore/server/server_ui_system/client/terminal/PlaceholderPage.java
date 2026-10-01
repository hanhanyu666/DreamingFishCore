package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.widget.EmptyState;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;

/** 尚未接入数据的模块。 */
final class PlaceholderPage extends TerminalPage {
    private final Icons icon;
    private final String hint;

    PlaceholderPage(TerminalScreen terminal, String title, Icons icon, String hint) {
        super(terminal, title);
        this.icon = icon;
        this.hint = hint;
    }

    @Override
    protected UiNode<?> build() {
        return EmptyState.of(icon, titleText(), hint);
    }
}
