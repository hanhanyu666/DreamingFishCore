package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcConversationViewData;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.client.cache.NpcMessageClientCache;

/** 紧凑布局下的单个会话线程。 */
final class ConversationPage extends TerminalPage {
    private final int npcId;

    ConversationPage(TerminalScreen terminal, int npcId) {
        super(terminal, "私信");
        this.npcId = npcId;
    }

    @Override
    protected UiNode<?> build() {
        return Dynamic.of(() -> NpcMessageClientCache.getConversation(npcId), value -> {
            NpcConversationViewData conversation = (NpcConversationViewData) value;
            return conversation == null ? MessageViews.emptyConversations() : MessageViews.thread(terminal, conversation);
        }).padding(Theme.Space.LG, Theme.Space.MD, Theme.Space.LG, Theme.Space.SM);
    }
}
