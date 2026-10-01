package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.Justify;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Button;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Card;
import com.hhy.dreamingfishcore.client.ui.framework.widget.EmptyState;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcConversationViewData;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcMessageViewData;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcReplyViewData;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.network.Packet_NpcMessageReadRequest;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.network.Packet_NpcMessageReplyRequest;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;

import java.util.function.IntConsumer;

/** 私信相关的共用视图：会话卡、消息气泡、回复栏。 */
final class MessageViews {
    static final int BLUE = 0xFF8CCEFF;
    static final int WARM = TerminalUi.GOLD;
    static final int WARM_SURFACE = 0x33FFC857;

    private MessageViews() {
    }

    static Card conversationCard(NpcConversationViewData conversation, boolean active, IntConsumer onOpen) {
        boolean unread = conversation.unreadCount() > 0;
        int accent = unread ? WARM : BLUE;
        Card card = TerminalUi.card().padding(Theme.Space.MD, Theme.Space.SM).gap(3.0F)
                .onClick(() -> onOpen.accept(conversation.npcId()));
        if (unread) {
            card.tint(WARM_SURFACE).accent(WARM);
        } else if (active) {
            card.accent(BLUE);
        }
        card.selectedImmediately(active);
        Box top = Ui.row(Text.of(conversation.npcName()).style(TextStyle.LABEL_STRONG)
                        .color(unread ? TerminalUi.WARM_TEXT : 0xFFE6EDF3).singleLine().grow(1.0F).shrink(1.0F))
                .gap(Theme.Space.XS);
        if (unread) {
            top.add(TerminalUi.chip("未读 " + Math.min(99, conversation.unreadCount()), WARM));
        }
        card.add(top, Text.of(TerminalData.latestPreview(conversation)).style(TextStyle.CAPTION)
                .color(unread ? 0xFFE8D5A5 : 0xFF8697A6).singleLine());
        return card;
    }

    /** 消息线程：头部 + 气泡列表 + 回复栏。 */
    static UiNode<?> thread(TerminalScreen terminal, NpcConversationViewData conversation) {
        Box header = Ui.row(
                TerminalUi.iconBadge(Icons.CHAT, BLUE, 20.0F),
                Ui.column(Text.of(conversation.npcName()).style(TextStyle.SUBTITLE).singleLine(),
                        Text.of(conversation.relationName() + " · 好感 " + conversation.favorability())
                                .style(TextStyle.CAPTION).color(TerminalUi.MINT).singleLine()).gap(1.0F).grow(1.0F)
        ).gap(Theme.Space.MD);

        Box bubbles = Ui.column().gap(Theme.Space.SM);
        for (NpcMessageViewData message : conversation.messages()) {
            bubbles.add(bubble(terminal, conversation, message));
        }
        ScrollView scroll = ScrollView.of(bubbles).grow(1.0F).basis(0.0F).edgeFade(ColorRole.SURFACE);
        boolean[] positioned = {false};
        scroll.onUpdate(() -> {
            if (!positioned[0] && scroll.height() > 0.0F) {
                positioned[0] = true;
                scroll.scrollTo(scroll.maxScroll(), false);
            }
        });

        NpcMessageViewData source = TerminalData.latestReplySource(conversation.messages());
        UiNode<?> replies = source == null
                ? Ui.row(Icon.of(Icons.INFO, 9.0F).color(ColorRole.TEXT_MUTED),
                Text.of("当前没有可用回复；新的选项会随关系与消息出现。").style(TextStyle.CAPTION).singleLine().shrink(1.0F)).gap(5.0F)
                : replyBar(source, null);
        return Ui.column(header, scroll, replies).gap(Theme.Space.MD);
    }

    private static UiNode<?> bubble(TerminalScreen terminal, NpcConversationViewData conversation, NpcMessageViewData message) {
        boolean outgoing = TerminalData.isOutgoing(message);
        boolean unread = TerminalData.isUnreadIncoming(message);
        int accent = unread ? WARM : outgoing ? TerminalUi.MINT : BLUE;
        Card card = TerminalUi.card().padding(Theme.Space.MD, Theme.Space.SM).gap(3.0F).accent(accent)
                .onClick(() -> openMessage(terminal, conversation, message));
        if (unread) {
            card.tint(WARM_SURFACE);
        } else if (outgoing) {
            card.tint(0x1A6FDDA8);
        }
        Box top = Ui.row(Text.of(outgoing ? "你" : conversation.npcName()).style(TextStyle.LABEL_STRONG).color(accent).singleLine(),
                Ui.spacer(),
                Text.of(TerminalData.historyDate(message.sentAtEpochMillis())).style(TextStyle.CAPTION).singleLine()).gap(Theme.Space.SM);
        if (unread) {
            top.add(TerminalUi.chip("未读", WARM));
        }
        card.add(top, Ui.row(Text.of(message.content().replace('\n', ' ')).style(TextStyle.BODY)
                        .color(unread ? TerminalUi.WARM_TEXT : 0xFFE6EDF3).maxLines(2).grow(1.0F).shrink(1.0F),
                Icon.of(Icons.CHEVRON_RIGHT, 9.0F)).gap(Theme.Space.SM));
        card.widthPercent(0.82F);
        return Ui.row(card).justify(outgoing ? Justify.END : Justify.START).enter(EnterEffect.FADE_UP);
    }

    static void openMessage(TerminalScreen terminal, NpcConversationViewData conversation, NpcMessageViewData message) {
        if (TerminalData.isUnreadIncoming(message)) {
            DreamingFishCore_NetworkManager.sendToServer(new Packet_NpcMessageReadRequest(conversation.npcId()));
        }
        terminal.push(new MessageDetailPage(terminal, conversation.npcId(), message.recordId()));
    }

    static UiNode<?> replyBar(NpcMessageViewData source, Runnable afterReply) {
        Box row = Ui.row().gap(Theme.Space.SM).alignItems(Align.STRETCH);
        int count = Math.min(3, source.availableReplies().size());
        for (int i = 0; i < count; i++) {
            NpcReplyViewData reply = source.availableReplies().get(i);
            Button button = Button.of(reply.text()).tonal().grow(1.0F).basis(0.0F);
            button.onClick(() -> {
                DreamingFishCore_NetworkManager.sendToServer(new Packet_NpcMessageReplyRequest(source.recordId(), reply.id()));
                if (afterReply != null) {
                    afterReply.run();
                }
            });
            row.add(button);
        }
        return Ui.column(TerminalUi.sectionLabel("回复 · 选择一项"), row).gap(Theme.Space.XS);
    }

    static UiNode<?> emptyConversations() {
        return EmptyState.of(Icons.MAIL, "暂无 NPC 私信", "与剧情 NPC 交谈，或等待对方向终端发送消息");
    }
}
