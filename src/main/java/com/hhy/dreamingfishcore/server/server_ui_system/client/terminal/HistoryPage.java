package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.CustomPaint;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.EmptyState;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ProgressRing;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.story_system.network.Packet_WorldHistoryRequest;
import com.hhy.dreamingfishcore.gameplay.story_system.network.Packet_WorldHistoryResponse;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;

import java.util.List;

/** 梦屿世界年表：服务端公开的重大事件，按时间线从新到旧排列。 */
final class HistoryPage extends TerminalPage {
    HistoryPage(TerminalScreen terminal) {
        super(terminal, "世界历史");
    }

    @Override
    protected void onShow() {
        DreamingFishCore_NetworkManager.sendToServer(new Packet_WorldHistoryRequest());
    }

    private record Key(List<Packet_WorldHistoryResponse.HistoryEntry> entries, boolean loaded, boolean writable, long total) {
    }

    @Override
    protected UiNode<?> build() {
        return Dynamic.of(() -> new Key(TerminalData.history(), TerminalData.historyLoaded(),
                TerminalData.historyWritable(), TerminalData.historyTotal()), this::content);
    }

    static int toneColor(TerminalData.HistoryTone tone) {
        return switch (tone) {
            case STAGE -> TerminalUi.VIOLET;
            case DISCUSSION -> TerminalUi.SKY;
            case RESPONSE, SUCCESS -> TerminalUi.GREEN;
            case TASK, ENDING -> TerminalUi.GOLD;
            case FAILURE -> 0xFFFF7A88;
            case OTHER -> 0xFF7AA8C7;
        };
    }

    private UiNode<?> content(Key key) {
        String status;
        int statusColor;
        if (!key.loaded()) {
            status = "正在同步服务器记录";
            statusColor = TerminalUi.STEEL;
        } else if (!key.writable()) {
            status = "历史日志处于只读保护";
            statusColor = 0xFFFF7A88;
        } else {
            status = "持续记录中";
            statusColor = TerminalUi.GREEN;
        }
        UiNode<?> summary = TerminalUi.card().accent(TerminalUi.GOLD).row().alignItems(Align.CENTER).add(
                TerminalUi.iconBadge(Icons.HISTORY, TerminalUi.GOLD, 26.0F),
                Ui.column(Text.of("梦屿世界年表").style(TextStyle.TITLE).singleLine(),
                        Text.of(status).style(TextStyle.LABEL).color(statusColor).singleLine()).gap(2.0F).grow(1.0F).shrink(1.0F),
                Ui.column(Text.of(String.valueOf(key.total())).style(TextStyle.HEADLINE).color(TerminalUi.GOLD).singleLine(),
                        Text.of("条公开历史 · 已载入 " + key.entries().size()).style(TextStyle.CAPTION).singleLine())
                        .alignItems(Align.END).gap(1.0F)
        ).gap(Theme.Space.LG).enter(EnterEffect.FADE_UP);

        UiNode<?> body;
        if (!key.loaded()) {
            body = Ui.column(ProgressRing.spinner().diameter(22.0F), Text.of("正在读取世界历史…").style(TextStyle.CAPTION))
                    .gap(Theme.Space.SM).alignItems(Align.CENTER).padding(Theme.Space.XL);
        } else if (key.entries().isEmpty()) {
            body = EmptyState.of(Icons.HISTORY, "这个世界的公开历史尚未开始", "故事事件发生后会记录在这里");
        } else {
            Box timeline = Ui.column().gap(0.0F);
            List<Packet_WorldHistoryResponse.HistoryEntry> entries = key.entries();
            for (int i = entries.size() - 1, index = 0; i >= 0; i--, index++) {
                boolean last = i == 0;
                timeline.add(entry(entries.get(i), index == 0, last).enter(EnterEffect.FADE_UP.delayed(Math.min(10, index) * 35.0F)));
            }
            body = timeline;
        }
        return ScrollView.of(summary, body).gap(Theme.Space.LG)
                .padding(Theme.Space.XL, Theme.Space.MD).edgeFade(ColorRole.SURFACE);
    }

    private static UiNode<?> entry(Packet_WorldHistoryResponse.HistoryEntry entry, boolean first, boolean last) {
        TerminalData.HistoryView view = TerminalData.describe(entry);
        int color = toneColor(view.tone());
        CustomPaint rail = CustomPaint.of((UiCanvas canvas, float w, float h) -> {
            float cx = w * 0.5F;
            int line = UiColor.withAlpha(0xFFFFFFFF, 0.08F);
            if (!first) {
                canvas.fill(cx - 0.5F, 0.0F, 1.0F, 14.0F, line);
            }
            if (!last) {
                canvas.fill(cx - 0.5F, 26.0F, 1.0F, h - 26.0F, line);
            }
            canvas.circle(cx, 20.0F, 6.0F, UiColor.withAlpha(color, 0.18F));
            canvas.circle(cx, 20.0F, 3.0F, color);
        }).width(20.0F);
        UiNode<?> card = TerminalUi.card().padding(Theme.Space.MD, Theme.Space.SM).gap(3.0F).row().alignItems(Align.CENTER).add(
                Ui.stack(Text.of(view.glyph()).style(TextStyle.LABEL_STRONG).color(color))
                        .alignItems(Align.CENTER).size(20.0F, 20.0F).radius(Theme.Radius.SM)
                        .background(UiColor.withAlpha(color, 0.14F)),
                Ui.column(Text.of(view.title()).style(TextStyle.BODY).singleLine(),
                        Text.of(view.subtitle()).style(TextStyle.CAPTION).singleLine()).gap(2.0F).grow(1.0F).shrink(1.0F),
                Text.of(TerminalData.historyDate(entry.recordedAtEpochMillis())).style(TextStyle.CAPTION).singleLine()
        ).margin(0.0F, 3.0F);
        return Ui.row(rail, card.grow(1.0F).shrink(1.0F)).alignItems(Align.STRETCH).gap(Theme.Space.SM);
    }
}
