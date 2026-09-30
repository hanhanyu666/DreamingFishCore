package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.cache.ClientCacheManager;
import com.hhy.dreamingfishcore.client.cache.EconomyTerminalClientCache;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Badge;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Card;
import com.hhy.dreamingfishcore.client.ui.framework.widget.EntityView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.PlayerHead;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ProgressBar;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Responsive;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.cache.PlayerAttributesClientCache;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.courage.PlayerCourageManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.death.TemplateReconstructionRules;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.strength.client.sync.PlayerStrengthClientSync;
import com.hhy.dreamingfishcore.gameplay.playerlevel_system.overalllevel.PlayerLevelManager;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerData;
import com.hhy.dreamingfishcore.server.rank_system.PlayerRankManager;
import com.hhy.dreamingfishcore.server.rank_system.Rank;
import com.hhy.dreamingfishcore.server.rank_system.RankRegistry;
import com.hhy.dreamingfishcore.server.title_system.PlayerTitleManager;
import com.hhy.dreamingfishcore.server.title_system.Title;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** 个人档案：身份、等级、资产与身体状态。点击 Rank 进入装配页。 */
final class ProfilePage extends TerminalPage {
    ProfilePage(TerminalScreen terminal) {
        super(terminal, "个人档案");
    }

    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    private static <T> Supplier<T> fromPlayer(Function<LocalPlayer, T> getter, T fallback) {
        return () -> {
            LocalPlayer p = player();
            return p == null ? fallback : getter.apply(p);
        };
    }

    @Override
    protected UiNode<?> build() {
        return Responsive.of(size -> size == Responsive.Size.COMPACT ? compact() : regular());
    }

    private UiNode<?> regular() {
        Card hero = heroCard(true).width(170.0F).enter(EnterEffect.FADE_RIGHT);
        Box right = Ui.column(levelCard(), tileGrid(3), bodyCard()).gap(Theme.Space.MD);
        int index = 0;
        for (UiNode<?> child : right.children()) {
            child.enter(EnterEffect.FADE_UP.delayed(60.0F + index++ * 50.0F));
        }
        ScrollView scroll = ScrollView.of(right).grow(1.0F).basis(0.0F).edgeFade(ColorRole.SURFACE);
        return Ui.row(hero, scroll).alignItems(Align.STRETCH).gap(Theme.Space.MD)
                .padding(Theme.Space.LG, Theme.Space.MD, Theme.Space.LG, Theme.Space.SM);
    }

    private UiNode<?> compact() {
        Box column = Ui.column(heroCard(false), levelCard(), tileGrid(2), bodyCard()).gap(Theme.Space.SM);
        return ScrollView.of(column).padding(Theme.Space.MD, Theme.Space.SM).edgeFade(ColorRole.SURFACE);
    }

    // ==================== 人物 ====================

    private Card heroCard(boolean withModel) {
        Supplier<Boolean> infected = fromPlayer(p -> PlayerAttributesClientCache.isInfected(p.getUUID()), false);
        Badge status = TerminalUi.chip(() -> infected.get() ? "感染者" : "幸存者", TerminalUi.GREEN);
        status.onUpdate(() -> status.color(infected.get() ? TerminalUi.ROSE : TerminalUi.GREEN));
        Badge member = TerminalUi.chip("逐光会成员", TerminalUi.GOLD);
        member.onUpdate(() -> {
            LocalPlayer p = player();
            PlayerData data = p == null ? null : ClientCacheManager.getPlayerData(p.getUUID());
            member.visible(data != null && data.isZhuiguangMember());
        });
        Text name = Text.of(() -> Component.literal(player() == null ? "--" : player().getScoreboardName()))
                .style(TextStyle.HEADLINE).singleLine();
        Text since = Text.of(() -> {
            LocalPlayer p = player();
            PlayerData data = p == null ? null : ClientCacheManager.getPlayerData(p.getUUID());
            if (data == null) {
                return Component.literal("档案同步中");
            }
            long registered = data.getRegistrationTime() > 0 ? data.getRegistrationTime() : data.getLastLoginTime();
            return Component.literal("注册 " + TerminalData.fullDate(registered) + "\n游玩 " + TerminalData.playDuration(data.getTotalPlayTime()));
        }).style(TextStyle.CAPTION);

        Card card = TerminalUi.card().gap(Theme.Space.SM);
        if (withModel) {
            card.add(TerminalUi.sectionLabel("PLAYER · 身份档案"), name, Ui.row(status, member).gap(4.0F).wrap(true),
                    EntityView.of(ProfilePage::player).grow(1.0F).minHeight(60.0F), since);
        } else {
            card.add(Ui.row(PlayerHead.local().headSize(34.0F).cornerRadius(7.0F),
                    Ui.column(name, Ui.row(status, member).gap(4.0F)).gap(3.0F).grow(1.0F)).gap(Theme.Space.MD), since);
        }
        return card;
    }

    // ==================== 等级 ====================

    private Card levelCard() {
        Text level = Text.of(() -> Component.literal("LEVEL " + fromPlayer(PlayerLevelManager::getPlayerLevelClient, 0).get()))
                .style(TextStyle.TITLE).color(TerminalUi.GOLD).singleLine();
        Text exp = Text.of(() -> Component.literal("EXP " + fromPlayer(PlayerLevelManager::getPlayerExperienceClient, 0L).get()
                + " / " + fromPlayer(PlayerLevelManager::getExperienceNeededForNextLevelClient, 0L).get()))
                .style(TextStyle.LABEL).singleLine();
        ProgressBar bar = ProgressBar.of(fromPlayer(PlayerLevelManager::getExperienceProgressClient, 0.0F))
                .color(TerminalUi.AMBER).gradientTo(TerminalUi.GOLD).thickness(6.0F);
        return TerminalUi.card().accent(TerminalUi.GOLD).add(
                Ui.row(Ui.column(level, exp).gap(2.0F), Ui.space(Theme.Space.XL), bar.grow(1.0F)).alignItems(Align.CENTER));
    }

    // ==================== 数据块 ====================

    private Box tileGrid(int columns) {
        Supplier<EconomyTerminalClientCache.Snapshot> snap = EconomyTerminalClientCache::get;
        List<Card> tiles = new ArrayList<>();
        tiles.add(TerminalUi.statTile("梦鱼币", () -> economyText(snap.get(), String.valueOf(snap.get().balance())), TerminalUi.GOLD)
                .tooltip(ProfilePage::economyTooltip));
        tiles.add(TerminalUi.statTile("领地", () -> economyText(snap.get(), snap.get().ownedTerritoryCount() + " 个"), TerminalUi.GREEN)
                .tooltip(ProfilePage::territoryTooltip));
        tiles.add(TerminalUi.statTile("探索群系", fromPlayer(p -> String.valueOf(nullToZero(ClientCacheManager.getExploredBiomesCount(p.getUUID()))), "0"),
                TerminalUi.SKY));
        tiles.add(TerminalUi.statTile("蓝图", fromPlayer(p -> String.valueOf(nullToZero(ClientCacheManager.getUnlockedRecipesCount(p.getUUID()))), "0"),
                TerminalUi.PERIWINKLE));
        Card rank = TerminalUi.statTile("Rank · 点击装配", fromPlayer(p -> {
            Rank current = PlayerRankManager.getPlayerRankClient(p);
            return current == RankRegistry.NO_RANK ? "未装配" : current.getRankName();
        }, "--"), TerminalUi.ROSE);
        rank.onUpdate(() -> {
            LocalPlayer p = player();
            rank.accent(p == null ? TerminalUi.ROSE : TerminalUi.rgb(PlayerRankManager.getPlayerRankClient(p).getRankColor()));
        });
        rank.onClick(() -> terminal.push(new RankPage(terminal)));
        tiles.add(rank);
        Card title = TerminalUi.statTile("称号", fromPlayer(p -> {
            Title current = PlayerTitleManager.getPlayerTitleClient(p);
            return current == null ? "--" : current.getTitleName();
        }, "--"), TerminalUi.STEEL);
        title.onUpdate(() -> {
            LocalPlayer p = player();
            Title current = p == null ? null : PlayerTitleManager.getPlayerTitleClient(p);
            title.accent(current == null ? TerminalUi.STEEL : TerminalUi.rgb(current.getColor()));
        });
        tiles.add(title);

        Box grid = Ui.column().gap(Theme.Space.SM);
        for (int i = 0; i < tiles.size(); i += columns) {
            Box row = Ui.row().alignItems(Align.STRETCH).gap(Theme.Space.SM);
            for (int j = 0; j < columns; j++) {
                row.add(i + j < tiles.size() ? tiles.get(i + j).grow(1.0F).basis(0.0F) : Ui.spacer().basis(0.0F));
            }
            grid.add(row);
        }
        return grid;
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String economyText(EconomyTerminalClientCache.Snapshot snapshot, String value) {
        if (!snapshot.loaded()) {
            return "同步中…";
        }
        return snapshot.available() && snapshot.compatible() ? value : "未接入";
    }

    private static List<Component> economyTooltip() {
        EconomyTerminalClientCache.Snapshot snapshot = EconomyTerminalClientCache.get();
        if (!snapshot.loaded()) {
            return List.of(Component.literal("§e正在同步经济数据..."));
        }
        if (!snapshot.available() || !snapshot.compatible()) {
            return List.of(Component.literal("§e梦鱼币数据不可用"), Component.literal("§7" + snapshot.statusText()));
        }
        return List.of(Component.literal("§6梦鱼币余额：§f" + snapshot.balance()));
    }

    private static List<Component> territoryTooltip() {
        EconomyTerminalClientCache.Snapshot snapshot = EconomyTerminalClientCache.get();
        if (!snapshot.loaded()) {
            return List.of(Component.literal("§e正在同步领地数据..."));
        }
        if (!snapshot.available() || !snapshot.compatible()) {
            return List.of(Component.literal("§e领地数据不可用"), Component.literal("§7" + snapshot.statusText()));
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§a拥有领地：§f" + snapshot.ownedTerritoryCount()));
        if (!snapshot.currentTerritoryName().isBlank()) {
            lines.add(Component.literal("§7当前位置：§f" + snapshot.currentTerritoryName()));
            String relation = TerminalData.relationship(snapshot.currentRelationship());
            if (!relation.isBlank()) {
                lines.add(Component.literal("§7身份：§f" + relation));
            }
        } else {
            lines.add(Component.literal("§7当前位置不属于任何领地"));
        }
        return lines;
    }

    // ==================== 身体状态 ====================

    private Card bodyCard() {
        Supplier<Boolean> infected = fromPlayer(p -> PlayerAttributesClientCache.isInfected(p.getUUID()), false);
        Supplier<Float> respawnPoint = fromPlayer(p -> ClientCacheManager.getRespawnPoint(p.getUUID()), 0.0F);
        Supplier<Integer> respawnTimes = () -> TemplateReconstructionRules.remainingReconstructions(
                respawnPoint.get(), infected.get() ? 20 : 5);
        Badge warning = TerminalUi.chip(() -> respawnTimes.get() <= 0 ? "无法复活" : "复活不足", TerminalUi.ROSE);
        warning.onUpdate(() -> {
            int times = respawnTimes.get();
            warning.visible(times < 2);
            warning.color(times <= 0 ? TerminalUi.ROSE : TerminalUi.GOLD);
        });
        Box meters = Ui.column(
                meterRow(
                        TerminalUi.meter("生命", fromPlayer(p -> String.format("%.0f/%.0f", p.getHealth(), p.getMaxHealth()), "--"),
                                fromPlayer(p -> p.getHealth() / Math.max(1.0F, p.getMaxHealth()), 0.0F), TerminalUi.ROSE),
                        TerminalUi.meter("饥饿", fromPlayer(p -> p.getFoodData().getFoodLevel() + "/20", "--"),
                                fromPlayer(p -> p.getFoodData().getFoodLevel() / 20.0F, 0.0F), TerminalUi.GOLD)),
                meterRow(
                        TerminalUi.meter("体力", fromPlayer(p -> PlayerStrengthClientSync.getCurrentStrengthClient(p) + "/" + maxStrength(p), "--"),
                                fromPlayer(p -> (float) PlayerStrengthClientSync.getCurrentStrengthClient(p) / maxStrength(p), 0.0F), TerminalUi.GREEN),
                        TerminalUi.meter("勇气", fromPlayer(p -> String.format("%.0f/%.0f", PlayerCourageManager.getCurrentCourageClient(p), maxCourage(p)), "--"),
                                fromPlayer(p -> PlayerCourageManager.getCurrentCourageClient(p) / maxCourage(p), 0.0F), TerminalUi.VIOLET)),
                meterRow(
                        TerminalUi.meter("感染", fromPlayer(p -> String.format("%.1f/%d", PlayerInfectionManager.getCurrentInfectionClient(p),
                                        PlayerInfectionManager.getInfectionMaximumClient(p)), "--"),
                                fromPlayer(p -> PlayerInfectionManager.getCurrentInfectionClient(p)
                                        / Math.max(1, PlayerInfectionManager.getInfectionMaximumClient(p)), 0.0F), 0xFF9B7CF6),
                        TerminalUi.meter("模板重建余量", () -> String.format("%.1f/100 · 可重建 %d 次", respawnPoint.get(), Math.max(0, respawnTimes.get())),
                                () -> respawnPoint.get() / 100.0F, TerminalUi.CYAN))
        ).gap(Theme.Space.MD);
        return TerminalUi.card().add(
                TerminalUi.header(Icons.PULSE, TerminalUi.ROSE, "身体状态", warning),
                meters
        );
    }

    private static Box meterRow(UiNode<?> a, UiNode<?> b) {
        return Ui.row(a.grow(1.0F).basis(0.0F), b.grow(1.0F).basis(0.0F)).gap(Theme.Space.LG).alignItems(Align.START);
    }

    private static int maxStrength(LocalPlayer player) {
        int max = PlayerStrengthClientSync.getMaxStrengthClient(player);
        return max <= 0 ? 100 : max;
    }

    private static float maxCourage(LocalPlayer player) {
        float max = PlayerCourageManager.getMaxCourageClient(player);
        return max <= 0.0F ? 100.0F : max;
    }
}
