package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.server.notice_system.NoticeData;

/** 公告详情。进入时已标记为已读。 */
final class NoticeDetailPage extends TerminalPage {
    private final NoticeData notice;

    NoticeDetailPage(TerminalScreen terminal, NoticeData notice) {
        super(terminal, notice.isGameNotice() ? "梦屿广播" : "服务器公告");
        this.notice = notice;
    }

    @Override
    protected UiNode<?> build() {
        boolean game = notice.isGameNotice();
        int accent = game ? TerminalUi.SKY : TerminalUi.AMBER;
        String meta = game ? "梦屿广播 · " + TerminalData.noticeStageLabel(notice)
                : "服务器公告 · 发布时间 " + TerminalData.fullDate(notice.getPublishTime());
        UiNode<?> header = Ui.column(
                Ui.row(TerminalUi.iconBadge(game ? Icons.MEGAPHONE : Icons.INFO, accent, 22.0F),
                        Text.of(TerminalData.safe(notice.getNoticeTitle(), "无标题")).style(TextStyle.HEADLINE).maxLines(2)
                                .grow(1.0F).shrink(1.0F)).gap(Theme.Space.MD),
                Text.of(meta).style(TextStyle.CAPTION).singleLine()
        ).gap(Theme.Space.SM).enter(EnterEffect.FADE_UP);

        UiNode<?> body = TerminalUi.card().surface(ColorRole.SURFACE_SUNKEN).accent(accent).add(
                Ui.row(Icon.of(Icons.LIST, 10.0F).color(accent), TerminalUi.sectionLabel("正文")).gap(5.0F),
                Text.of(TerminalData.safe(notice.getNoticeContent(), "暂无内容")).style(TextStyle.BODY.withLineGap(4.0F))
        ).enter(EnterEffect.FADE_UP.delayed(60.0F));

        UiNode<?> footer = Ui.row(Icon.of(Icons.CHECK, 9.0F).color(TerminalUi.MINT),
                Text.of("已查看").style(TextStyle.CAPTION).singleLine()).gap(4.0F);

        return ScrollView.of(header, body, footer).gap(Theme.Space.MD)
                .padding(Theme.Space.XL, Theme.Space.MD).edgeFade(ColorRole.SURFACE);
    }
}
