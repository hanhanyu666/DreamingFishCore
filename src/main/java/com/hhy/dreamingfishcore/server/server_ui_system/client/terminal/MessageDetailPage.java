package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcConversationViewData;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageRecord;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageViewData;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.client.cache.NpcMessageClientCache;

/** 单封私信的完整正文与回复。 */
final class MessageDetailPage extends TerminalPage {
    private final int npcId;
    private final String recordId;

    MessageDetailPage(TerminalScreen terminal, int npcId, String recordId) {
        super(terminal, "私信详情");
        this.npcId = npcId;
        this.recordId = recordId;
    }

    @Override
    protected UiNode<?> build() {
        return Dynamic.of(() -> NpcMessageClientCache.getConversation(npcId), value -> {
            NpcConversationViewData conversation = (NpcConversationViewData) value;
            NpcMessageViewData message = conversation == null ? null : conversation.messages().stream()
                    .filter(candidate -> recordId.equals(candidate.recordId())).findFirst().orElse(null);
            if (message == null) {
                return MessageViews.emptyConversations();
            }
            return content(conversation, message);
        });
    }

    private UiNode<?> content(NpcConversationViewData conversation, NpcMessageViewData message) {
        boolean outgoing = TerminalData.isOutgoing(message);
        boolean unread = TerminalData.isUnreadIncoming(message);
        int accent = unread ? MessageViews.WARM : outgoing ? TerminalUi.MINT : MessageViews.BLUE;
        String subject = message.subject().isBlank() ? "私信详情" : message.subject();
        String route = outgoing ? "你 → " + conversation.npcName() : conversation.npcName() + " → 你";

        UiNode<?> header = Ui.column(
                Ui.row(TerminalUi.iconBadge(Icons.MAIL, accent, 22.0F),
                        Text.of(subject).style(TextStyle.HEADLINE).maxLines(2).grow(1.0F).shrink(1.0F)).gap(Theme.Space.MD),
                Ui.row(Text.of(route).style(TextStyle.LABEL_STRONG).color(accent).singleLine(), Ui.spacer(),
                        Text.of(TerminalData.fullDate(message.sentAtEpochMillis())).style(TextStyle.CAPTION).singleLine())
        ).gap(Theme.Space.SM).enter(EnterEffect.FADE_UP);

        UiNode<?> body = TerminalUi.card().surface(ColorRole.SURFACE_SUNKEN).accent(accent).add(
                Text.of(message.content()).style(TextStyle.BODY.withLineGap(4.0F))
        ).enter(EnterEffect.FADE_UP.delayed(60.0F));

        boolean hasReplies = message.direction() == NpcMessageRecord.Direction.NPC_TO_PLAYER
                && !message.replied() && !message.availableReplies().isEmpty();
        UiNode<?> footer = hasReplies
                ? MessageViews.replyBar(message, () -> navigator().pop())
                : Ui.row(Icon.of(outgoing || message.replied() ? Icons.CHECK : Icons.INFO, 9.0F).color(ColorRole.TEXT_MUTED),
                Text.of(outgoing ? "已发送" : message.replied() ? "你已回复这封私信" : "这封私信无需回复")
                        .style(TextStyle.CAPTION).singleLine()).gap(5.0F);

        return Ui.column(ScrollView.of(header, body).gap(Theme.Space.MD).grow(1.0F).basis(0.0F)
                        .edgeFade(ColorRole.SURFACE), footer)
                .gap(Theme.Space.MD).padding(Theme.Space.XL, Theme.Space.MD, Theme.Space.XL, Theme.Space.SM);
    }
}
