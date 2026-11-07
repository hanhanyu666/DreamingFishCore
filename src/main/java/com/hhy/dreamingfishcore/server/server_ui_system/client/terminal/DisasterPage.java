package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ProgressRing;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.disaster_system.DisasterSchedule;
import com.hhy.dreamingfishcore.gameplay.disaster_system.client.DisasterClientCache;
import com.hhy.dreamingfishcore.gameplay.disaster_system.network.Packet_DisasterSnapshotRequest;
import com.hhy.dreamingfishcore.gameplay.disaster_system.network.Packet_DisasterSnapshotResponse;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import java.util.ArrayList;
import java.util.List;

/**
 * 天灾预警（终端 UI 页面）。
 *
 * <p>玩家看到：**今天的天灾**（可能多条叠加）+ 完整的 14 天日程。
 * 每一行都**可以点进去**，进入那一天自己的页面查看/调整（管理员可改，玩家只读）。</p>
 *
 * <p>为什么编辑不摆在这一页：八种天灾挤在一行会把日期和名称压成省略号（实测过）。
 * 列表页只负责"看"，编辑交给单独页面。</p>
 */
final class DisasterPage extends TerminalPage {

    DisasterPage(TerminalScreen terminal) {
        super(terminal, "天灾预警");
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
                    Text.of("正在读取天灾日程…").style(TextStyle.CAPTION))
                    .gap(Theme.Space.SM).alignItems(Align.CENTER).padding(Theme.Space.XL);
        }
        return ScrollView.of(todayCard(snapshot), legend(snapshot), dayList(snapshot))
                .gap(Theme.Space.LG)
                .padding(Theme.Space.XL, Theme.Space.MD)
                .edgeFade(ColorRole.SURFACE);
    }

    /** 今天的天灾（大卡片，可能多条叠加）。 */
    private UiNode<?> todayCard(Packet_DisasterSnapshotResponse snapshot) {
        var today = snapshot.days().stream()
                .filter(entry -> entry.day() == snapshot.today())
                .findFirst().orElse(null);
        Box body = Ui.column().gap(Theme.Space.SM);
        body.add(Text.of("今天 · 第 " + snapshot.today() + " 天").style(TextStyle.LABEL)
                .color(TerminalUi.GOLD).singleLine());
        if (today == null || today.events().isEmpty()) {
            body.add(Text.of("今日平安").style(TextStyle.TITLE).singleLine());
        } else {
            for (var type : today.events()) {
                body.add(Text.of("· " + type.displayName()).style(TextStyle.TITLE)
                        .color(colorOf(type)).singleLine());
            }
        }
        return TerminalUi.card().accent(TerminalUi.AMBER).row().alignItems(Align.CENTER).add(
                        TerminalUi.iconBadge(Icons.STAR, TerminalUi.AMBER, 26.0F),
                        body.grow(1.0F).shrink(1.0F))
                .gap(Theme.Space.LG).enter(EnterEffect.FADE_UP);
    }

    private UiNode<?> legend(Packet_DisasterSnapshotResponse snapshot) {
        Box box = Ui.column().gap(Theme.Space.SM);
        box.add(Text.of("点任意一天可以进入那一天的页面查看详情。")
                .style(TextStyle.CAPTION).singleLine());
        box.add(Text.of(snapshot.canEdit()
                        ? "你是管理员：在那一天的页面里可以增删天灾（支持一天多条叠加）。"
                        : "只有管理员能调整日程；你看到的是服务端当前的安排。")
                .style(TextStyle.CAPTION)
                .color(snapshot.canEdit() ? TerminalUi.GREEN : TerminalUi.STEEL)
                .singleLine());
        return TerminalUi.card().add(box);
    }

    private UiNode<?> dayList(Packet_DisasterSnapshotResponse snapshot) {
        Box list = Ui.column().gap(Theme.Space.SM);
        list.add(TerminalUi.sectionLabel("日程 · SCHEDULE"));
        int index = 0;
        for (var entry : snapshot.days()) {
            list.add(dayRow(snapshot, entry)
                    .enter(EnterEffect.FADE_UP.delayed(Math.min(12, index++) * 20.0F)));
        }
        return list;
    }

    /** 一行 = 日期 + 当日摘要；整行可点，点进去是那一天自己的页面。 */
    private UiNode<?> dayRow(Packet_DisasterSnapshotResponse snapshot,
                             Packet_DisasterSnapshotResponse.DayEntry entry) {
        boolean isToday = entry.day() == snapshot.today();
        Box line = Ui.row().alignItems(Align.CENTER).gap(Theme.Space.SM);
        line.add(Text.of("第 " + entry.day() + " 天").style(TextStyle.LABEL)
                .color(isToday ? TerminalUi.GOLD : TerminalUi.STEEL).singleLine());
        line.add(Text.of(summaryOf(entry)).style(TextStyle.CAPTION)
                .color(entry.events().isEmpty() ? TerminalUi.STEEL : TerminalUi.GOLD)
                .singleLine().grow(1.0F));
        line.add(Text.of("查看 ›").style(TextStyle.CAPTION)
                .color(TerminalUi.STEEL).singleLine());
        return TerminalUi.card().accent(isToday ? TerminalUi.AMBER : TerminalUi.STEEL)
                .onClick(() -> terminal.push(new DisasterDayEditorPage(terminal, entry.day())))
                .add(line);
    }

    /** 当日摘要：平安 / 已存在的天灾名字。 */
    static String summaryOf(Packet_DisasterSnapshotResponse.DayEntry entry) {
        if (entry == null || entry.events().isEmpty()) {
            return "平安";
        }
        List<String> names = new ArrayList<>();
        entry.events().forEach(type -> names.add(type.displayName()));
        return String.join(" · ", names);
    }

    /** 各天灾在 UI 里的配色（编辑页复用）。 */
    static int colorOf(DisasterSchedule.EventType type) {
        return switch (type) {
            case ZOMBIE_HORDE -> 0xFFFF7A88;
            case FROST_NIGHT -> TerminalUi.SKY;
            case STORM_DAY -> TerminalUi.VIOLET;
            case OCEAN_BLIGHT -> TerminalUi.MINT;
            case SPORE_PLAGUE -> TerminalUi.GREEN;
            case METEOR_RAIN -> TerminalUi.GOLD;
            case ACID_RAIN -> TerminalUi.GREEN;
            case SANDSTORM -> TerminalUi.AMBER;
        };
    }
}
