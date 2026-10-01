package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.EmptyState;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ProgressRing;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Responsive;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcConversationViewData;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.client.cache.NpcMessageClientCache;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.network.Packet_NpcMessageSnapshotRequest;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;

import java.util.List;

/** NPC 私信：会话列表与消息线程。紧凑布局下点会话进入单独的线程页。 */
final class MessagesPage extends TerminalPage {
    private int selectedNpc = -1;

    MessagesPage(TerminalScreen terminal) {
        super(terminal, "NPC 私信");
    }

    @Override
    protected void onShow() {
        DreamingFishCore_NetworkManager.sendToServer(new Packet_NpcMessageSnapshotRequest());
    }

    private record Key(boolean loaded, List<NpcConversationViewData> conversations, int selected, Responsive.Size size) {
    }

    @Override
    protected UiNode<?> build() {
        return Responsive.of(size -> Dynamic.of(
                () -> new Key(NpcMessageClientCache.isLoaded(), NpcMessageClientCache.getConversations(), selectedNpc, size),
                this::content)).padding(Theme.Space.LG, Theme.Space.MD, Theme.Space.LG, Theme.Space.SM);
    }

    private UiNode<?> content(Key key) {
        if (!key.loaded()) {
            return Ui.column(ProgressRing.spinner().diameter(22.0F),
                    Text.of("正在同步 NPC 私信").style(TextStyle.SUBTITLE).color(ColorRole.TEXT_SECONDARY),
                    Text.of("终端正在建立私人频道").style(TextStyle.CAPTION)).gap(Theme.Space.SM).center().grow(1.0F);
        }
        List<NpcConversationViewData> conversations = key.conversations();
        if (conversations.isEmpty()) {
            return MessageViews.emptyConversations();
        }
        boolean compact = key.size() == Responsive.Size.COMPACT;
        NpcConversationViewData selected = conversations.stream()
                .filter(conversation -> conversation.npcId() == key.selected()).findFirst().orElse(conversations.get(0));
        if (!compact && selected.npcId() != selectedNpc) {
            selectedNpc = selected.npcId();
        }

        int totalUnread = conversations.stream().mapToInt(NpcConversationViewData::unreadCount).sum();
        Box list = Ui.column().gap(Theme.Space.XS);
        int index = 0;
        for (NpcConversationViewData conversation : conversations) {
            boolean active = !compact && conversation.npcId() == selected.npcId();
            list.add(MessageViews.conversationCard(conversation, active, npcId -> {
                if (compact) {
                    terminal.push(new ConversationPage(terminal, npcId));
                } else {
                    selectedNpc = npcId;
                }
            }).enter(EnterEffect.FADE_RIGHT.delayed(index++ * 30.0F)));
        }
        UiNode<?> listHeader = Ui.row(TerminalUi.sectionLabel("会话 · " + conversations.size() + " 位 NPC"), Ui.spacer(),
                totalUnread > 0 ? TerminalUi.chip(totalUnread + " 条未读", MessageViews.WARM) : Ui.space(0.0F)).gap(Theme.Space.SM);
        UiNode<?> listPanel = Ui.column(listHeader, ScrollView.of(list).grow(1.0F).basis(0.0F).edgeFade(ColorRole.SURFACE))
                .gap(Theme.Space.SM);
        if (compact) {
            return listPanel;
        }
        UiNode<?> thread = TerminalUi.card().surface(ColorRole.SURFACE_SUNKEN).noOutline().flat()
                .add(MessageViews.thread(terminal, selected).grow(1.0F));
        return Ui.row(listPanel.width(160.0F), thread.grow(1.0F).basis(0.0F)).alignItems(Align.STRETCH).gap(Theme.Space.MD);
    }

    static UiNode<?> loading() {
        return EmptyState.of(Icons.MAIL, "正在同步 NPC 私信", "终端正在建立私人频道");
    }
}
