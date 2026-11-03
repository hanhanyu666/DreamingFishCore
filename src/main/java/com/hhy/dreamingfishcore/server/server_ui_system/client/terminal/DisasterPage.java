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
import com.hhy.dreamingfishcore.gameplay.disaster_system.network.Packet_DisasterEditRequest;
import com.hhy.dreamingfishcore.gameplay.disaster_system.network.Packet_DisasterSnapshotRequest;
import com.hhy.dreamingfishcore.gameplay.disaster_system.network.Packet_DisasterSnapshotResponse;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;

/**
 * 天灾预警（终端 UI 页面）。
 *
 * <p>玩家看到：**今天的天灾**（可能多条叠加）+ 完整的 14 天日程。
 * 管理员（2 级权限）还看到每天后面的「+ 类型」标签，点一下就能给那天加上或去掉某个天灾。</p>
 *
 * <p>数据是服务端推的全量快照（{@link Packet_DisasterSnapshotResponse}），
 * 所以每次改动都会回来一份新快照，界面自动刷新，不需要客户端自己猜结果。</p>
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
        box.add(Text.of("三灾：大尸潮 / 寒霜之夜 / 风暴天，可以叠在同一天。")
                .style(TextStyle.CAPTION).singleLine());
        if (snapshot.canEdit()) {
            box.add(Text.of("你是管理员：点「+ 名称」给某天加上天灾，点「− 名称」去掉。")
                    .style(TextStyle.CAPTION).color(TerminalUi.GREEN).singleLine());
        } else {
            box.add(Text.of("只有管理员能调整日程；你看到的是服务端当前的安排。")
                    .style(TextStyle.CAPTION).singleLine());
        }
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

    private UiNode<?> dayRow(Packet_DisasterSnapshotResponse snapshot,
                             Packet_DisasterSnapshotResponse.DayEntry entry) {
        boolean isToday = entry.day() == snapshot.today();
        Box line = Ui.row().alignItems(Align.CENTER).gap(Theme.Space.SM);
        line.add(Text.of("第 " + entry.day() + " 天").style(TextStyle.LABEL)
                .color(isToday ? TerminalUi.GOLD : TerminalUi.STEEL).singleLine());
        if (entry.events().isEmpty()) {
            line.add(Text.of("平安").style(TextStyle.CAPTION).singleLine().grow(1.0F));
        }
        // 已存在的天灾：管理员点在「−」上可移除，普通玩家只是看到名字
        for (var type : entry.events()) {
            line.add(chip(type, true, snapshot.canEdit(),
                    () -> edit(entry.day(), type, false, snapshot.canEdit())));
        }
        // 管理员额外能加：把还没有的类型以「+ 名称」列在后面
        if (snapshot.canEdit()) {
            for (var type : DisasterSchedule.EventType.values()) {
                if (!entry.events().contains(type)) {
                    line.add(chip(type, false, true, () -> edit(entry.day(), type, true, true)));
                }
            }
        }
        return TerminalUi.card().accent(isToday ? TerminalUi.AMBER : TerminalUi.STEEL).add(line);
    }

    private UiNode<?> chip(DisasterSchedule.EventType type, boolean present, boolean clickable,
                           Runnable onClick) {
        int color = present ? colorOf(type) : TerminalUi.STEEL;
        String label = (present ? (clickable ? "− " : "") : "+ ") + type.displayName();
        var card = TerminalUi.card().accent(color).add(
                Text.of(label).style(TextStyle.CAPTION).color(color).singleLine());
        return clickable ? card.onClick(onClick) : card;
    }

    private static void edit(long day, DisasterSchedule.EventType type, boolean add, boolean allowed) {
        if (!allowed) {
            return;     // 非管理员点击没有任何效果（服务端也会再校验一次权限）
        }
        DreamingFishCore_NetworkManager.sendToServer(
                new Packet_DisasterEditRequest(day, type.ordinal(), add));
    }

    private static int colorOf(DisasterSchedule.EventType type) {
        return switch (type) {
            case ZOMBIE_HORDE -> 0xFFFF7A88;
            case FROST_NIGHT -> TerminalUi.SKY;
            case STORM_DAY -> TerminalUi.VIOLET;
        };
    }
}
