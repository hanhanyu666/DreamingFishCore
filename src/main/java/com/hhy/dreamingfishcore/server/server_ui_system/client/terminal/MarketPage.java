package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.cache.EconomyTerminalClientCache;
import com.hhy.dreamingfishcore.client.integration.EconomySystemUiBridge;
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
import com.hhy.dreamingfishcore.client.ui.framework.widget.EmptyState;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ItemIcon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ProgressRing;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.economy_bridge.network.Packet_EconomyTerminalRequest;
import net.minecraft.world.item.ItemStack;

/** 市场行情：只读的挂单快照，并提供进入经济系统原生界面的入口。 */
final class MarketPage extends TerminalPage {
    MarketPage(TerminalScreen terminal) {
        super(terminal, "市场行情");
    }

    @Override
    protected void onShow() {
        DreamingFishCore_NetworkManager.sendToServer(new Packet_EconomyTerminalRequest());
    }

    @Override
    protected UiNode<?> build() {
        return Dynamic.of(EconomyTerminalClientCache::get, this::content);
    }

    private UiNode<?> content(EconomyTerminalClientCache.Snapshot snapshot) {
        if (!snapshot.loaded()) {
            return Ui.column(ProgressRing.spinner().diameter(22.0F), Text.of("正在同步市场数据…").style(TextStyle.CAPTION))
                    .gap(Theme.Space.SM).center().grow(1.0F);
        }
        if (!snapshot.available() || !snapshot.compatible()) {
            return EmptyState.of(Icons.BAG, "服务器商店暂不可用", snapshot.statusText());
        }
        Box summary = Ui.row(
                tile("梦鱼币", String.valueOf(snapshot.balance()), TerminalUi.GOLD),
                tile("出售", String.valueOf(snapshot.salesOrderCount()), TerminalUi.SKY),
                tile("求购", String.valueOf(snapshot.demandOrderCount()), TerminalUi.AMBER),
                tile("我的挂单", String.valueOf(snapshot.ownOrderCount()), TerminalUi.VIOLET)
        ).alignItems(Align.STRETCH).gap(Theme.Space.SM).enter(EnterEffect.FADE_UP);

        Box actions = Ui.row(
                Button.of("打开服务器商店").leadingIcon(Icons.BAG).small().onClick(() -> {
                    terminal.rememberTab();
                    EconomySystemUiBridge.openShop(terminal);
                }),
                Button.of("经济主页").outlined().small().onClick(() -> {
                    terminal.rememberTab();
                    EconomySystemUiBridge.openHome(terminal);
                }),
                Button.of("领地").outlined().leadingIcon(Icons.FLAG).small().onClick(() -> {
                    terminal.rememberTab();
                    EconomySystemUiBridge.openTerritory(terminal);
                })
        ).gap(Theme.Space.SM);

        Box orders = Ui.column().gap(Theme.Space.SM);
        if (snapshot.marketOrders().isEmpty()) {
            orders.add(EmptyState.of(Icons.BAG, "当前没有有效市场挂单", null));
        }
        int index = 0;
        for (EconomyTerminalClientCache.MarketOrderView order : snapshot.marketOrders()) {
            orders.add(orderRow(order).enter(EnterEffect.FADE_UP.delayed(Math.min(10, index++) * 30.0F)));
        }
        return ScrollView.of(summary, actions, Ui.row(Text.of("市场挂单").style(TextStyle.SUBTITLE), Ui.spacer(),
                        Text.of(snapshot.marketOrders().size() + " 条").style(TextStyle.CAPTION)), orders)
                .gap(Theme.Space.MD).padding(Theme.Space.XL, Theme.Space.MD).edgeFade(ColorRole.SURFACE);
    }

    private static Card tile(String label, String value, int accent) {
        return TerminalUi.card().accent(accent).padding(Theme.Space.LG, Theme.Space.MD).gap(3.0F).grow(1.0F).basis(0.0F).add(
                Text.of(label).style(TextStyle.CAPTION).singleLine(),
                Text.of(value).style(TextStyle.TITLE).color(accent).singleLine());
    }

    private static Card orderRow(EconomyTerminalClientCache.MarketOrderView order) {
        boolean sale = "SALES".equals(order.type());
        int accent = sale ? TerminalUi.SKY : TerminalUi.AMBER;
        ItemStack stack = TerminalData.marketItem(order.itemId());
        UiNode<?> icon = stack.isEmpty()
                ? TerminalUi.iconBadge(Icons.BAG, accent, 22.0F)
                : Ui.stack(ItemIcon.of(stack).iconSize(16.0F).decorations(false)).alignItems(Align.CENTER)
                .size(24.0F, 24.0F).radius(Theme.Radius.MD).background(0x14FFFFFF);
        String owner = order.ownerName().isBlank() ? "匿名玩家" : order.ownerName();
        return TerminalUi.card().accent(accent).padding(Theme.Space.MD, Theme.Space.SM).row().alignItems(Align.CENTER).add(
                icon,
                Ui.column(Ui.row(TerminalUi.chip(sale ? "出售" : "求购", accent),
                                Text.of(TerminalData.marketItemName(order.itemId()) + " ×" + order.quantity())
                                        .style(TextStyle.BODY).singleLine().shrink(1.0F)).gap(Theme.Space.SM),
                        Text.of("发布者 · " + owner).style(TextStyle.CAPTION).singleLine()).gap(3.0F).grow(1.0F).shrink(1.0F),
                Ui.column(Text.of(order.totalPrice() + " 梦鱼币").style(TextStyle.LABEL_STRONG).color(TerminalUi.GOLD).singleLine(),
                        Text.of(TerminalData.marketExpiry(order.expirationTime())).style(TextStyle.CAPTION).singleLine())
                        .alignItems(Align.END).gap(3.0F)
        ).gap(Theme.Space.MD);
    }
}
