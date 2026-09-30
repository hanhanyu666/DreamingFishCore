package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Button;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Card;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Responsive;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.rank_system.PlayerRankManager;
import com.hhy.dreamingfishcore.server.rank_system.Rank;
import com.hhy.dreamingfishcore.server.rank_system.RankRegistry;
import com.hhy.dreamingfishcore.server.rank_system.network.Packet_EquipPlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Rank 装配：列出已拥有的 Rank，点击装配。 */
final class RankPage extends TerminalPage {
    RankPage(TerminalScreen terminal) {
        super(terminal, "Rank 装配");
    }

    private record State(String equipped, Set<String> owned, boolean wide) {
    }

    @Override
    protected UiNode<?> build() {
        return Responsive.of(size -> Dynamic.of(() -> state(size != Responsive.Size.COMPACT), this::content));
    }

    private static State state(boolean wide) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return new State("", Set.of(), wide);
        }
        return new State(PlayerRankManager.getPlayerRankClient(player).getRankName(),
                Set.copyOf(PlayerRankManager.getOwnedRankNamesClient(player)), wide);
    }

    private UiNode<?> content(State state) {
        List<Rank> options = new ArrayList<>();
        options.add(RankRegistry.NO_RANK);
        for (Rank rank : RankRegistry.getRegisteredRanks()) {
            if (rank != RankRegistry.NO_RANK && state.owned().contains(rank.getRankName())) {
                options.add(rank);
            }
        }
        Rank equipped = RankRegistry.NO_RANK;
        for (Rank rank : options) {
            if (rank.getRankName().equals(state.equipped())) {
                equipped = rank;
            }
        }

        Card header = TerminalUi.card().accent(TerminalUi.AMBER).add(
                TerminalUi.header(Icons.SHIELD, TerminalUi.AMBER, "Rank 装配",
                        TerminalUi.chip("已拥有 " + state.owned().size(), TerminalUi.STEEL)),
                Text.of(equipped == RankRegistry.NO_RANK ? "当前未装配 Rank" : "当前装配：" + equipped.getRankName())
                        .style(TextStyle.BODY).color(equipped == RankRegistry.NO_RANK
                                ? header0Muted() : TerminalUi.rgb(equipped.getRankColor())));

        int columns = state.wide() ? 2 : 1;
        Box grid = Ui.column().gap(Theme.Space.SM);
        int index = 0;
        for (int i = 0; i < options.size(); i += columns) {
            Box row = Ui.row().alignItems(Align.STRETCH).gap(Theme.Space.SM);
            for (int j = 0; j < columns; j++) {
                if (i + j < options.size()) {
                    Rank rank = options.get(i + j);
                    row.add(rankCard(rank, rank.getRankName().equals(state.equipped()))
                            .grow(1.0F).basis(0.0F).enter(EnterEffect.FADE_UP.delayed(index++ * 35.0F)));
                } else {
                    row.add(Ui.spacer().basis(0.0F));
                }
            }
            grid.add(row);
        }
        return ScrollView.of(Ui.column(header, grid).gap(Theme.Space.MD))
                .padding(Theme.Space.LG, Theme.Space.MD).edgeFade(ColorRole.SURFACE);
    }

    private static int header0Muted() {
        return 0xFF8B96A3;
    }

    private Card rankCard(Rank rank, boolean equipped) {
        int accent = rank == RankRegistry.NO_RANK ? 0xFF8B96A3 : TerminalUi.rgb(rank.getRankColor());
        Button action = Button.of(equipped ? "已装配" : "装配").small();
        if (equipped) {
            action.tonal().leadingIcon(Icons.CHECK).accent(ColorRole.SUCCESS).disabled(true);
        } else {
            action.outlined().onClick(() -> equip(rank));
        }
        Card card = TerminalUi.card().accent(accent).row().alignItems(Align.CENTER).add(
                Ui.column(
                        Text.of(rank == RankRegistry.NO_RANK ? "不装配" : rank.getRankName()).style(TextStyle.SUBTITLE).color(accent).singleLine(),
                        Text.of(rank == RankRegistry.NO_RANK ? "隐藏聊天与展示中的 Rank" : "已拥有").style(TextStyle.CAPTION).singleLine()
                ).gap(3.0F).grow(1.0F).shrink(1.0F),
                action);
        if (equipped) {
            card.selectedImmediately(true);
        } else {
            card.onClick(() -> equip(rank));
        }
        return card;
    }

    private static void equip(Rank rank) {
        LocalPlayer player = Minecraft.getInstance().player;
        Rank current = player == null ? RankRegistry.NO_RANK : PlayerRankManager.getPlayerRankClient(player);
        if (!current.getRankName().equals(rank.getRankName())) {
            DreamingFishCore_NetworkManager.sendToServer(new Packet_EquipPlayerRank(rank.getRankName()));
        }
    }
}
