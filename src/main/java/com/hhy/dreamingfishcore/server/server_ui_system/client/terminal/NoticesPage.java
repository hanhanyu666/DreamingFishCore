package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Card;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.EmptyState;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Responsive;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Segmented;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.notice_system.NoticeCategory;
import com.hhy.dreamingfishcore.server.notice_system.NoticeData;
import com.hhy.dreamingfishcore.server.notice_system.network.Packet_MarkNoticeReadRequest;
import com.hhy.dreamingfishcore.server.notice_system.network.Packet_NoticeListRequest;

import java.util.List;

/** 梦屿广播：剧情广播与服务器公告，剧情广播可按已开放阶段筛选。 */
final class NoticesPage extends TerminalPage {
    private int tab;
    private String stageId = "";

    NoticesPage(TerminalScreen terminal) {
        super(terminal, "梦屿广播");
    }

    @Override
    protected void onShow() {
        DreamingFishCore_NetworkManager.sendToServer(new Packet_NoticeListRequest());
    }

    private record ListKey(int tab, String stageId, int version, List<String> stages, Responsive.Size size) {
    }

    @Override
    protected UiNode<?> build() {
        Segmented tabs = new Segmented().option("梦屿广播", Icons.MEGAPHONE, 0).option("服务器公告", Icons.INFO, 0);
        tabs.select(tab, false).onSelect(index -> {
            tab = index;
            stageId = "";
        });
        tabs.onUpdate(() -> {
            tabs.setCount(0, TerminalData.countUnread(NoticeCategory.GAME));
            tabs.setCount(1, TerminalData.countUnread(NoticeCategory.MAINTENANCE));
        });
        Box header = Ui.row(
                Ui.column(Text.of("梦屿广播").style(TextStyle.TITLE).singleLine(),
                        Text.of("剧情消息与服务器公告").style(TextStyle.CAPTION).singleLine()).gap(2.0F).grow(1.0F).shrink(1.0F),
                tabs
        ).gap(Theme.Space.LG).alignItems(Align.CENTER);

        Responsive body = Responsive.of(size -> Dynamic.of(
                () -> new ListKey(tab, stageId, TerminalData.noticeVersion(),
                        TerminalData.visibleStageList().stream().map(StoryStageData::getStageId).toList(), size),
                key -> body(key.size())));
        body.grow(1.0F).basis(0.0F).minHeight(0.0F);
        return Ui.column(header, body).gap(Theme.Space.MD)
                .padding(Theme.Space.LG, Theme.Space.MD, Theme.Space.LG, Theme.Space.SM);
    }

    private UiNode<?> body(Responsive.Size size) {
        NoticeCategory category = tab == 0 ? NoticeCategory.GAME : NoticeCategory.MAINTENANCE;
        List<StoryStageData> stages = TerminalData.visibleStageList();
        if (!stageId.isEmpty() && stages.stream().noneMatch(stage -> stageId.equals(stage.getStageId()))) {
            stageId = "";
        }
        List<NoticeData> notices = TerminalData.noticesFor(category, stageId);
        UiNode<?> list = noticeList(notices, size == Responsive.Size.WIDE ? 2 : 1);
        if (category != NoticeCategory.GAME || stages.isEmpty()) {
            return list;
        }
        boolean compact = size == Responsive.Size.COMPACT;
        SideList<String> filter = new SideList<>(compact, id -> stageId = id);
        filter.item("", "全部阶段", "所有已开放的广播", TerminalUi.SKY);
        for (StoryStageData stage : stages) {
            filter.item(stage.getStageId(), TerminalData.stageLabel(stage),
                    stage.isCurrentStage() ? "当前阶段" : "已开放", TerminalUi.SKY);
        }
        filter.select(stageId);
        if (compact) {
            return Ui.column(filter, list.grow(1.0F).basis(0.0F)).gap(Theme.Space.SM);
        }
        UiNode<?> sidebar = ScrollView.of(SideList.titled("故事阶段", filter)).width(132.0F);
        return Ui.row(sidebar, list.grow(1.0F).basis(0.0F)).alignItems(Align.STRETCH).gap(Theme.Space.LG);
    }

    private UiNode<?> noticeList(List<NoticeData> notices, int columns) {
        if (notices.isEmpty()) {
            String text = tab == 0 && !stageId.isEmpty() ? "该阶段暂无游戏公告" : tab == 0 ? "暂无梦屿广播" : "暂无服务器公告";
            return EmptyState.of(Icons.MEGAPHONE, text, "新的广播会在这里出现");
        }
        Box grid = Ui.column().gap(Theme.Space.SM);
        int index = 0;
        for (int i = 0; i < notices.size(); i += columns) {
            Box row = Ui.row().alignItems(Align.STRETCH).gap(Theme.Space.SM);
            for (int j = 0; j < columns; j++) {
                if (i + j < notices.size()) {
                    row.add(noticeCard(notices.get(i + j)).grow(1.0F).basis(0.0F)
                            .enter(EnterEffect.FADE_UP.delayed(Math.min(8, index++) * 35.0F)));
                } else {
                    row.add(Ui.spacer().basis(0.0F));
                }
            }
            grid.add(row);
        }
        return ScrollView.of(grid).edgeFade(ColorRole.SURFACE);
    }

    private Card noticeCard(NoticeData notice) {
        boolean read = TerminalData.isRead(notice);
        int accent = read ? TerminalUi.STEEL : TerminalUi.SKY;
        String category = notice.isGameNotice() ? "梦屿广播" : "服务器公告";
        String meta = notice.isGameNotice()
                ? TerminalData.noticeStageLabel(notice)
                : "发布 " + TerminalData.fullDate(notice.getPublishTime());
        return TerminalUi.card().accent(accent).gap(Theme.Space.SM).onClick(() -> open(notice)).add(
                Ui.row(TerminalUi.chip(category + " · " + (read ? "已读" : "未读"), accent),
                        Ui.spacer(),
                        Text.of(meta).style(TextStyle.CAPTION).singleLine().shrink(1.0F)).gap(Theme.Space.SM),
                Text.of(TerminalData.safe(notice.getNoticeTitle(), "无标题")).style(TextStyle.SUBTITLE).singleLine(),
                Text.of(TerminalData.safe(notice.getNoticeContent(), "暂无内容").replace('\n', ' '))
                        .style(TextStyle.CAPTION).maxLines(2)
        );
    }

    private void open(NoticeData notice) {
        DreamingFishCore_NetworkManager.sendToServer(new Packet_MarkNoticeReadRequest(notice.getNoticeId()));
        TerminalData.markNoticeRead(notice.getNoticeId());
        terminal.push(new NoticeDetailPage(terminal, notice));
    }
}
