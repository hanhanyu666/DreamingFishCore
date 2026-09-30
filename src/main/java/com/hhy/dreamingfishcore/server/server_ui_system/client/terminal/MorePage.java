package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.integration.EconomySystemUiBridge;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Card;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Responsive;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.OptionsScreen;

import java.util.ArrayList;
import java.util.List;

/** 全部模块入口。 */
final class MorePage extends TerminalPage {
    private record Module(Icons icon, String title, String subtitle, int accent, Runnable action) {
    }

    MorePage(TerminalScreen terminal) {
        super(terminal, "全部模块");
    }

    @Override
    protected UiNode<?> build() {
        return Responsive.of(size -> content(size == Responsive.Size.COMPACT ? 2 : size == Responsive.Size.WIDE ? 4 : 3));
    }

    private List<Module> modules() {
        List<Module> list = new ArrayList<>();
        list.add(new Module(Icons.HELP, "生存手册", "身体、感染与重生规则", TerminalUi.GREEN,
                () -> terminal.push(new HelpPage(terminal))));
        list.add(new Module(Icons.HISTORY, "世界历史", "全服共同经历的年表", TerminalUi.GOLD,
                () -> terminal.push(new HistoryPage(terminal))));
        list.add(new Module(Icons.COIN, "经济系统", "梦鱼币、交易与账户", TerminalUi.GOLD, () -> {
            terminal.rememberTab();
            if (!EconomySystemUiBridge.openHome(terminal)) {
                terminal.push(new MarketPage(terminal));
            }
        }));
        list.add(new Module(Icons.BAG, "市场行情", "当前出售与求购挂单", TerminalUi.SKY,
                () -> terminal.push(new MarketPage(terminal))));
        list.add(new Module(Icons.FLAG, "领地", "查看与管理你的领地", TerminalUi.GREEN, () -> {
            terminal.rememberTab();
            if (!EconomySystemUiBridge.openTerritory(terminal)) {
                terminal.push(new PlaceholderPage(terminal, "领地", Icons.FLAG, "领地功能需要经济系统模组"));
            }
        }));
        list.add(new Module(Icons.TROPHY, "玩家与排行", "排行面板还在接入数据", TerminalUi.PERIWINKLE,
                () -> terminal.push(new PlaceholderPage(terminal, "玩家与排行", Icons.TROPHY, "排行面板还在接入数据"))));
        list.add(new Module(Icons.STAR, "服务器成就", "成就面板还在接入数据", TerminalUi.AMBER,
                () -> terminal.push(new PlaceholderPage(terminal, "服务器成就", Icons.STAR, "成就面板还在接入数据"))));
        list.add(new Module(Icons.SETTINGS, "游戏设置", "打开 Minecraft 设置", TerminalUi.STEEL, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            terminal.openSubScreen(new OptionsScreen(terminal, minecraft.options));
        }));
        return list;
    }

    private UiNode<?> content(int columns) {
        List<Module> modules = modules();
        Box grid = Ui.column().gap(Theme.Space.SM);
        int index = 0;
        for (int i = 0; i < modules.size(); i += columns) {
            Box row = Ui.row().alignItems(Align.STRETCH).gap(Theme.Space.SM);
            for (int j = 0; j < columns; j++) {
                if (i + j < modules.size()) {
                    row.add(tile(modules.get(i + j)).grow(1.0F).basis(0.0F)
                            .enter(EnterEffect.FADE_UP.delayed(index++ * 35.0F)));
                } else {
                    row.add(Ui.spacer().basis(0.0F));
                }
            }
            grid.add(row);
        }
        return ScrollView.of(Ui.column(TerminalUi.sectionLabel("全部模块 · MODULES"), grid).gap(Theme.Space.MD))
                .padding(Theme.Space.LG, Theme.Space.MD).edgeFade(ColorRole.SURFACE);
    }

    private static Card tile(Module module) {
        return TerminalUi.card().onClick(module.action()).gap(Theme.Space.SM).add(
                Ui.row(TerminalUi.iconBadge(module.icon(), module.accent(), 24.0F), Ui.spacer(),
                        Icon.of(Icons.ARROW_UP_RIGHT, 9.0F)).alignItems(Align.START),
                Text.of(module.title()).style(TextStyle.SUBTITLE).singleLine(),
                Text.of(module.subtitle()).style(TextStyle.CAPTION).maxLines(2));
    }
}
