package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ProgressRing;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule;
import com.hhy.dreamingfishcore.gameplay.disaster_system.client.DisasterClientCache;
import com.hhy.dreamingfishcore.gameplay.disaster_system.network.Packet_DisasterEditRequest;
import com.hhy.dreamingfishcore.gameplay.disaster_system.network.Packet_DisasterSnapshotRequest;
import com.hhy.dreamingfishcore.gameplay.disaster_system.network.Packet_DisasterSnapshotResponse;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import java.util.List;

/**
 * 某一天的天灾详情 / 编辑页（从 {@link DisasterPage} 点某一天进来）。
 *
 * <p>八种天灾每种占**一整行**，所以名字不会再被挤成省略号；管理员点一行即可加上或去掉，
 * 玩家点不会有任何效果（只读）。</p>
 *
 * <p>数据仍来自服务端推的全量快照：改完服务端回推一份，本页通过 {@link Dynamic} 自动刷新。</p>
 */
final class DisasterDayEditorPage extends TerminalPage {

    private final long day;

    DisasterDayEditorPage(TerminalScreen terminal, long day) {
        super(terminal, "第 " + day + " 天 · 天灾");
        this.day = day;
    }

    @Override
    protected void onShow() {
        DreamingFishCore_NetworkManager.sendToServer(new Packet_DisasterSnapshotRequest());
    }

    @Override
    protected UiNode<?> build() {
        return Dynamic.of(DisasterClientCache::snapshot, this::content);
    }

    private UiNode<?> content(Packet_DisasterSnapshotResponse snapshot) {
        if (snapshot == null) {
            return Ui.column(ProgressRing.spinner().diameter(22.0F),
                    Text.of("正在读取这一天…").style(TextStyle.CAPTION))
                    .gap(Theme.Space.SM).alignItems(Align.CENTER).padding(Theme.Space.XL);
        }
        var entry = snapshot.days().stream()
                .filter(candidate -> candidate.day() == day)
                .findFirst().orElse(null);
        List<?> events = entry == null ? List.of() : entry.events();

        Box rows = Ui.column().gap(Theme.Space.SM);
        int index = 0;
        for (var type : DisasterSchedule.EventType.values()) {
            boolean present = events.contains(type);
            rows.add(row(snapshot, type, present, index++));
        }

        Box header = Ui.column().gap(Theme.Space.SM);
        header.add(Text.of("第 " + day + " 天").style(TextStyle.TITLE)
                .color(day == snapshot.today() ? TerminalUi.GOLD : 0xFFFFFFFF).singleLine());
        header.add(Text.of(DisasterPage.summaryOf(entry)).style(TextStyle.CAPTION)
                .color(events.isEmpty() ? TerminalUi.STEEL : TerminalUi.GOLD).singleLine());
        header.add(Text.of(snapshot.canEdit()
                        ? "点一行即加上；再点一次即去掉。可以同一天叠多个天灾。"
                        : "只有管理员能调整日程。")
                .style(TextStyle.CAPTION)
                .color(snapshot.canEdit() ? TerminalUi.GREEN : TerminalUi.STEEL).singleLine());

        return ScrollView.of(header, TerminalUi.sectionLabel("天灾 · DISASTERS"), rows)
                .gap(Theme.Space.LG)
                .padding(Theme.Space.XL, Theme.Space.MD)
                .edgeFade(ColorRole.SURFACE);
    }

    /** 一种天灾一整行：名字 + 状态；管理员可点。 */
    private UiNode<?> row(Packet_DisasterSnapshotResponse snapshot,
                          DisasterSchedule.EventType type, boolean present, int index) {
        int color = present ? DisasterPage.colorOf(type) : TerminalUi.STEEL;
        Box line = Ui.row().alignItems(Align.CENTER).gap(Theme.Space.SM);
        line.add(Text.of(type.displayName()).style(TextStyle.LABEL).color(color).singleLine());
        line.add(Text.of(type.isTerrainEvent() ? "地形" : "通用").style(TextStyle.CAPTION)
                .color(TerminalUi.STEEL).singleLine());
        line.add(Text.of(present ? "已安排" : "未安排").style(TextStyle.CAPTION)
                .color(color).singleLine().grow(1.0F));
        if (snapshot.canEdit()) {
            line.add(Text.of(present ? "点击去掉" : "点击加上").style(TextStyle.CAPTION)
                    .color(TerminalUi.STEEL).singleLine());
        }

        var card = TerminalUi.card().accent(color).add(line)
                .enter(EnterEffect.FADE_UP.delayed(Math.min(10, index) * 20.0F));
        if (snapshot.canEdit()) {
            return card.onClick(() -> DreamingFishCore_NetworkManager.sendToServer(
                    new Packet_DisasterEditRequest(day, type.ordinal(), !present)));
        }
        return card;
    }
}
