package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.nav.Page;
import net.minecraft.network.chat.Component;

/** 终端页面基类：持有终端外壳，用于切换模块、打开子界面。 */
public abstract class TerminalPage extends Page {
    protected final TerminalScreen terminal;
    private final String title;

    protected TerminalPage(TerminalScreen terminal, String title) {
        this.terminal = terminal;
        this.title = title;
    }

    @Override
    public Component title() {
        return Component.literal(title);
    }

    public String titleText() {
        return title;
    }
}
