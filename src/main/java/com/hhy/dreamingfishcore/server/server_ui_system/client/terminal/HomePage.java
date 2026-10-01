package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.cache.ClientCacheManager;
import com.hhy.dreamingfishcore.client.cache.EconomyTerminalClientCache;
import com.hhy.dreamingfishcore.client.integration.EconomySystemUiBridge;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Badge;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Card;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Divider;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.PlayerHead;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Responsive;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceViewData;
import com.hhy.dreamingfishcore.gameplay.guidance_system.client.cache.GuidanceClientCache;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.NpcConversationViewData;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.client.cache.NpcMessageClientCache;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.client.cache.PlayerAttributesClientCache;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.courage.PlayerCourageManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.infection.PlayerInfectionManager;
import com.hhy.dreamingfishcore.gameplay.playerattributes_system.strength.client.sync.PlayerStrengthClientSync;
import com.hhy.dreamingfishcore.gameplay.playerlevel_system.overalllevel.PlayerLevelManager;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData;
import com.hhy.dreamingfishcore.gameplay.story_system.network.Packet_WorldHistoryResponse;
import com.hhy.dreamingfishcore.server.notice_system.NoticeData;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerData;
import com.hhy.dreamingfishcore.server.rank_system.PlayerRankManager;
import com.hhy.dreamingfishcore.server.rank_system.Rank;
import com.hhy.dreamingfishcore.server.title_system.PlayerTitleManager;
import com.hhy.dreamingfishcore.server.title_system.Title;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/** 终端主页：个人、经济、故事、广播、私信、历史与手册的概览卡片。 */
final class HomePage extends TerminalPage {
    private static final int MESSAGE_BLUE = 0xFF8CCEFF;

    HomePage(TerminalScreen terminal) {
        super(terminal, "主页");
    }

    @Override
    protected UiNode<?> build() {
        return Responsive.of(size -> size == Responsive.Size.COMPACT ? compact() : regular(size == Responsive.Size.WIDE));
    }

    private boolean wide;

    private UiNode<?> regular(boolean wideLayout) {
        wide = wideLayout;
        Box left = Ui.column(profileCard().grow(1.15F).basis(0), economyCard().grow(0.85F).basis(0))
                .gap(Theme.Space.MD).grow(1.0F).basis(0);
        Box center = Ui.column(storyCard().grow(1.2F).basis(0), noticeCard().grow(0.8F).basis(0))
                .gap(Theme.Space.MD).grow(1.0F).basis(0);
        Box right = Ui.column(messageCard().grow(1.1F).basis(0),
                        Ui.row(historyCard().grow(1.0F).basis(0), helpCard().grow(1.0F).basis(0))
                                .alignItems(Align.STRETCH).gap(Theme.Space.MD).grow(0.9F).basis(0))
                .gap(Theme.Space.MD).grow(1.0F).basis(0);
        stagger(left, center, right);
        return Ui.row(left, center, right).alignItems(Align.STRETCH).gap(Theme.Space.MD)
                .padding(Theme.Space.LG, Theme.Space.MD, Theme.Space.LG, Theme.Space.SM);
    }

    private UiNode<?> compact() {
        wide = false;
        Box rows = Ui.column(
                pair(profileCard(), storyCard()),
                pair(noticeCard(), messageCard()),
                pair(economyCard(), historyCard()),
                pair(helpCard(), Ui.spacer())
        ).gap(Theme.Space.SM);
        int index = 0;
        for (UiNode<?> row : rows.children()) {
            for (UiNode<?> card : row.children()) {
                card.enter(EnterEffect.FADE_UP.delayed(index++ * 40.0F));
            }
        }
        return ScrollView.of(rows).padding(Theme.Space.MD, Theme.Space.SM).edgeFade(ColorRole.SURFACE);
    }

    private static Box pair(UiNode<?> a, UiNode<?> b) {
        return Ui.row(a.grow(1.0F).basis(0), b.grow(1.0F).basis(0)).alignItems(Align.STRETCH)
                .gap(Theme.Space.SM);
    }

    private static void stagger(Box... columns) {
        int index = 0;
        for (Box column : columns) {
            for (UiNode<?> child : column.children()) {
                if (child.children().size() > 1 && !(child instanceof Card)) {
                    for (UiNode<?> nested : child.children()) {
                        nested.enter(EnterEffect.FADE_UP.delayed(index++ * 45.0F));
                    }
                } else {
                    child.enter(EnterEffect.FADE_UP.delayed(index++ * 45.0F));
                }
            }
        }
    }

    private static Card baseCard(Runnable onClick) {
        return TerminalUi.card().onClick(onClick).clip(true);
    }

    // ==================== 个人档案 ====================

    private Card profileCard() {
        Supplier<LocalPlayer> player = () -> Minecraft.getInstance().player;
        Supplier<Integer> rankColor = () -> {
            LocalPlayer p = player.get();
            Rank rank = p == null ? null : PlayerRankManager.getPlayerRankClient(p);
            return rank == null ? TerminalUi.STEEL : TerminalUi.rgb(rank.getRankColor());
        };
        Text name = Text.of(() -> Component.literal(player.get() == null ? "等待玩家数据…" : player.get().getScoreboardName()))
                .style(TextStyle.TITLE).singleLine();
        name.onUpdate(() -> name.color(rankColor.get()));
        Text rankLine = Text.of(() -> {
            LocalPlayer p = player.get();
            if (p == null) {
                return Component.empty();
            }
            Rank rank = PlayerRankManager.getPlayerRankClient(p);
            Title title = PlayerTitleManager.getPlayerTitleClient(p);
            String rankName = rank == null ? "NO RANK" : rank.getRankName();
            String titleName = title == null || title.getTitleName() == null ? "" : title.getTitleName();
            return Component.literal(titleName.isBlank() ? rankName : rankName + " · " + titleName);
        }).style(TextStyle.LABEL).singleLine();

        PlayerHead head = PlayerHead.local().headSize(30.0F).cornerRadius(5.0F);
        Box avatar = TerminalWidgets.scanFrame(head, rankColor);

        Badge level = TerminalUi.chip(() -> "LV." + (player.get() == null ? 0 : PlayerLevelManager.getPlayerLevelClient(player.get())),
                TerminalUi.GOLD);
        Badge status = TerminalUi.chip(() -> TerminalData.identity().displayName(), TerminalUi.GREEN);
        status.onUpdate(() -> status.color(TerminalData.identityColor(TerminalData.identity())));

        Text footer = Text.of(() -> {
            LocalPlayer p = player.get();
            PlayerData data = p == null ? null : ClientCacheManager.getPlayerData(p.getUUID());
            long playTime = data == null ? 0L : data.getTotalPlayTime();
            String prefix = data != null && data.isZhuiguangMember() ? "逐光会成员 · " : "";
            return Component.literal(prefix + "游玩 " + TerminalData.playDuration(playTime));
        }).style(TextStyle.CAPTION).singleLine();

        UiNode<?> exp = TerminalUi.meter("等级经验",
                () -> player.get() == null ? "--" : PlayerLevelManager.getPlayerExperienceClient(player.get())
                        + " / " + PlayerLevelManager.getExperienceNeededForNextLevelClient(player.get()),
                () -> player.get() == null ? 0.0F : PlayerLevelManager.getExperienceProgressClient(player.get()),
                TerminalUi.GOLD);
        Supplier<Float> reserve = () -> player.get() == null ? 0.0F : ClientCacheManager.getRespawnPoint(player.get().getUUID());
        Supplier<Float> cost = () -> player.get() == null ? 10.0F
                : PlayerAttributesClientCache.getNormalRespawnCost(player.get().getUUID());
        UiNode<?> template = Ui.column(
                Ui.row(Text.of("模板储备").style(TextStyle.LABEL).singleLine().grow(1),
                        Text.of(() -> Component.literal(player.get() == null ? "--" : String.format("%.1f / 100", reserve.get())))
                                .style(TextStyle.LABEL_STRONG).singleLine()),
                TerminalWidgets.reserveCells(reserve, cost)
        ).gap(4.0F);
        Card card = baseCard(() -> terminal.switchTab(TerminalScreen.Tab.PROFILE)).add(
                TerminalUi.sectionLabel("身份档案 · SURVIVOR FILE"),
                Ui.row(avatar, Ui.column(name, rankLine, Ui.row(level, status).gap(Theme.Space.XS).margin(0.0F, 2.0F, 0.0F, 0.0F))
                        .gap(2.0F).grow(1.0F).shrink(1.0F)).gap(Theme.Space.MD).alignItems(Align.CENTER));
        if (wide) {
            // 大屏时补一块“体征回传”，与游戏内左下角的读数一致
            card.add(vitalsGrid());
        }
        card.add(Ui.spacer(), exp, template);
        if (wide) {
            card.add(footer);
        }
        return card;
    }

    /** 生命、感染、勇气、体力四项读数。 */
    private static UiNode<?> vitalsGrid() {
        Supplier<LocalPlayer> player = () -> Minecraft.getInstance().player;
        UiNode<?> health = vital("生命", TerminalUi.GREEN, () -> {
            LocalPlayer p = player.get();
            return p == null ? "--" : String.format("%.0f / %.0f", p.getHealth(), p.getMaxHealth());
        }, () -> {
            LocalPlayer p = player.get();
            return p == null ? 0.0F : p.getHealth() / Math.max(1.0F, p.getMaxHealth());
        });
        UiNode<?> infection = vital("感染", 0xFF9FD46C, () -> {
            LocalPlayer p = player.get();
            if (p == null) {
                return "--";
            }
            if (infected()) {
                return TerminalData.identity().displayName();
            }
            return Math.round(infectionRatio(p) * 100.0F) + "%";
        }, () -> {
            LocalPlayer p = player.get();
            return p == null ? 0.0F : infected() ? 1.0F : infectionRatio(p);
        });
        UiNode<?> courage = vital("勇气", TerminalUi.VIOLET, () -> {
            LocalPlayer p = player.get();
            return p == null ? "--" : Math.round(PlayerCourageManager.getCurrentCourageClient(p)) + " / "
                    + Math.round(PlayerCourageManager.getMaxCourageClient(p));
        }, () -> {
            LocalPlayer p = player.get();
            return p == null ? 0.0F : PlayerCourageManager.getCurrentCourageClient(p)
                    / Math.max(1.0F, PlayerCourageManager.getMaxCourageClient(p));
        });
        UiNode<?> stamina = vital("体力", TerminalUi.AMBER, () -> {
            LocalPlayer p = player.get();
            return p == null ? "--" : PlayerStrengthClientSync.getCurrentStrengthClient(p) + " / "
                    + PlayerStrengthClientSync.getMaxStrengthClient(p);
        }, () -> {
            LocalPlayer p = player.get();
            return p == null ? 0.0F : PlayerStrengthClientSync.getCurrentStrengthClient(p)
                    / (float) Math.max(1, PlayerStrengthClientSync.getMaxStrengthClient(p));
        });
        return Ui.column(
                TerminalUi.sectionLabel("体征回传 · VITALS").margin(0.0F, 4.0F, 0.0F, 0.0F),
                Ui.row(health.grow(1.0F).basis(0), infection.grow(1.0F).basis(0)).gap(Theme.Space.SM).alignItems(Align.STRETCH),
                Ui.row(courage.grow(1.0F).basis(0), stamina.grow(1.0F).basis(0)).gap(Theme.Space.SM).alignItems(Align.STRETCH)
        ).gap(Theme.Space.SM);
    }

    private static float infectionRatio(LocalPlayer p) {
        float max = Math.max(1, PlayerInfectionManager.getInfectionMaximumClient(p));
        return Math.max(0.0F, Math.min(1.0F, PlayerInfectionManager.getCurrentInfectionClient(p) / max));
    }

    private static UiNode<?> vital(String label, int color, Supplier<String> value, Supplier<Float> ratio) {
        return Ui.column(
                Ui.row(Ui.stack().size(4.0F, 4.0F).radius(2.0F).background(color).shrink(0.0F),
                        Text.of(label).style(TextStyle.CAPTION).singleLine().grow(1),
                        Text.of(() -> Component.literal(value.get())).style(TextStyle.LABEL_STRONG).color(color).singleLine())
                        .gap(4.0F).alignItems(Align.CENTER),
                com.hhy.dreamingfishcore.client.ui.framework.widget.ProgressBar.of(ratio).color(color).thickness(2.0F)
        ).gap(3.0F).padding(Theme.Space.SM, Theme.Space.XS + 1.0F).radius(Theme.Radius.MD)
                .background(0x40060A0E).border(1.0F, UiColor.withAlpha(color, 0.16F));
    }

    private static boolean infected() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && PlayerAttributesClientCache.isInfected(p.getUUID());
    }

    // ==================== 经济 ====================

    private Card economyCard() {
        Supplier<EconomyTerminalClientCache.Snapshot> snap = EconomyTerminalClientCache::get;
        Badge state = TerminalUi.chip(() -> economyState(snap.get()), TerminalUi.STEEL);
        state.onUpdate(() -> state.color(economyStateColor(snap.get())));
        Box metrics = Ui.row(
                TerminalUi.metric("梦鱼币", () -> TerminalData.economyMetric(snap.get(), snap.get().balance()), TerminalUi.GOLD).grow(1.0F).basis(0),
                Divider.vertical(),
                TerminalUi.metric("领地", () -> TerminalData.economyMetric(snap.get(), snap.get().ownedTerritoryCount()), TerminalUi.GREEN).grow(1.0F).basis(0),
                Divider.vertical(),
                TerminalUi.metric("挂单", () -> TerminalData.economyMetric(snap.get(),
                        snap.get().salesOrderCount() + snap.get().demandOrderCount()), TerminalUi.SKY).grow(1.0F).basis(0)
        ).alignItems(Align.STRETCH).gap(Theme.Space.MD);
        Text footer = Text.of(() -> Component.literal(economyFooter(snap.get()))).style(TextStyle.CAPTION).singleLine()
                .grow(1.0F).shrink(1.0F);
        return baseCard(this::openEconomy).add(
                TerminalUi.header(Icons.COIN, TerminalUi.GOLD, "经济系统", "MARKET", state),
                metrics,
                Ui.spacer(),
                Ui.row(footer, TerminalUi.footerLink("打开", TerminalUi.GOLD)).gap(Theme.Space.SM)
        );
    }

    private void openEconomy() {
        terminal.rememberTab();
        if (!EconomySystemUiBridge.openHome(terminal)) {
            terminal.push(new MarketPage(terminal));
        }
    }

    private static String economyState(EconomyTerminalClientCache.Snapshot snapshot) {
        if (!snapshot.loaded()) {
            return "同步中";
        }
        if (!snapshot.available() || !snapshot.compatible()) {
            return "暂不可用";
        }
        return snapshot.salesOrderCount() + snapshot.demandOrderCount() > 0 ? "市场活跃" : "市场空闲";
    }

    private static int economyStateColor(EconomyTerminalClientCache.Snapshot snapshot) {
        if (!snapshot.loaded()) {
            return TerminalUi.GOLD;
        }
        if (!snapshot.available() || !snapshot.compatible()) {
            return TerminalUi.ROSE;
        }
        return snapshot.salesOrderCount() + snapshot.demandOrderCount() > 0 ? TerminalUi.GREEN : TerminalUi.STEEL;
    }

    private static String economyFooter(EconomyTerminalClientCache.Snapshot snapshot) {
        if (!snapshot.loaded()) {
            return "正在读取经济数据…";
        }
        if (!snapshot.available() || !snapshot.compatible()) {
            return snapshot.statusText().isBlank() ? "经济功能暂时不可用" : snapshot.statusText();
        }
        if (!snapshot.currentTerritoryName().isBlank()) {
            String relation = TerminalData.relationship(snapshot.currentRelationship());
            return "当前位置 · " + snapshot.currentTerritoryName() + (relation.isBlank() ? "" : " · " + relation);
        }
        return "出售 " + snapshot.salesOrderCount() + " · 求购 " + snapshot.demandOrderCount();
    }

    // ==================== 故事 ====================

    private Card storyCard() {
        Dynamic<Object> body = Dynamic.of(TerminalData::currentStage, stage -> {
            StoryStageData current = (StoryStageData) stage;
            if (current == null) {
                return Text.of("当前阶段尚未同步").style(TextStyle.BODY_SECONDARY);
            }
            Box column = Ui.column(
                    Text.of("阶段 " + current.getStageNumber() + " · " + TerminalData.safe(current.getStageName(), "未命名"))
                            .style(TextStyle.TITLE).singleLine(),
                    TerminalWidgets.stageTimeline(TerminalData::visibleStageList, TerminalData::currentStage)
                            .margin(0.0F, 2.0F, 0.0F, 2.0F),
                    Text.of(TerminalData.safe(current.getStageDescription(), "新的剧情会随着公告与 NPC 对话逐步展开。"))
                            .style(TextStyle.BODY_SECONDARY).maxLines(wide ? 3 : 2)
            ).gap(4.0F);
            if (wide && current.getTasks() != null && !current.getTasks().isEmpty()) {
                Box tasks = Ui.column(TerminalUi.sectionLabel("本阶段任务")).gap(4.0F).margin(0.0F, 4.0F, 0.0F, 0.0F);
                current.getTasks().stream().filter(java.util.Objects::nonNull).limit(4).forEach(task -> {
                    int color = StoryPage.toneColor(TerminalData.taskTone(task, false));
                    tasks.add(Ui.row(Ui.stack().size(5.0F, 5.0F).radius(2.5F).background(color),
                            Text.of(TerminalData.safe(task.getTaskName(), "未命名任务")).style(TextStyle.LABEL).singleLine()
                                    .grow(1.0F).shrink(1.0F),
                            Text.of(TerminalData.taskStatusLabel(task, false)).style(TextStyle.CAPTION).color(color).singleLine())
                            .gap(6.0F));
                });
                column.add(tasks);
            }
            return column;
        });
        Text clue = Text.of(() -> {
            GuidanceViewData tracked = GuidanceClientCache.getTrackedEntry();
            return Component.literal(tracked == null ? "等待新的故事线索" : "线索 · " + tracked.title());
        }).style(TextStyle.LABEL).singleLine().grow(1.0F).shrink(1.0F);
        clue.onUpdate(() -> clue.color(GuidanceClientCache.getTrackedEntry() == null
                ? clue.theme().color(ColorRole.TEXT_MUTED) : clue.theme().color(ColorRole.TEXT)));
        Box clueRow = Ui.row(Icon.of(Icons.TARGET, 10.0F).color(TerminalUi.MINT), clue)
                .gap(6.0F).padding(8.0F, 5.0F).radius(Theme.Radius.MD)
                .background(UiColor.withAlpha(TerminalUi.MINT, 0.08F)).border(1.0F, UiColor.withAlpha(TerminalUi.MINT, 0.18F));
        Badge threat = TerminalUi.chip(HomePage::threatLabel, TerminalUi.STEEL);
        threat.onUpdate(() -> threat.color(threatColor()));
        return baseCard(() -> terminal.switchTab(TerminalScreen.Tab.STORY)).accent(TerminalUi.MINT).add(
                TerminalUi.header(Icons.BOOK, TerminalUi.MINT, "故事进展", "STORY · 梦屿纪事", threat),
                body,
                Ui.spacer(),
                clueRow
        );
    }

    /** 当前阶段的感染体强化倍率，作为“威胁”读数。 */
    private static float threatMultiplier() {
        StoryStageData stage = TerminalData.currentStage();
        if (stage == null || stage.getMonsterModifier() == null) {
            return 1.0F;
        }
        StoryStageData.MonsterModifier modifier = stage.getMonsterModifier();
        return Math.max(modifier.getHealthMultiplier(), modifier.getDamageMultiplier());
    }

    private static String threatLabel() {
        float value = threatMultiplier();
        return value <= 1.001F ? "威胁 平稳" : String.format("威胁 ×%.1f", value);
    }

    private static int threatColor() {
        float value = threatMultiplier();
        return value <= 1.001F ? TerminalUi.STEEL : value < 1.5F ? TerminalUi.GOLD : TerminalUi.ROSE;
    }

    // ==================== 广播 ====================

    private Card noticeCard() {
        Badge state = TerminalUi.chip(() -> {
            int unread = TerminalData.unreadNotices();
            return unread > 0 ? unread + " 条未读" : "全部已读";
        }, TerminalUi.MINT);
        state.onUpdate(() -> state.color(TerminalData.unreadNotices() > 0 ? TerminalUi.GOLD : TerminalUi.MINT));
        if (wide) {
            return wideNoticeCard(state);
        }
        Dynamic<Object> body = Dynamic.of(TerminalData::latestNotice, value -> {
            NoticeData latest = (NoticeData) value;
            if (latest == null) {
                return Ui.column(Text.of("暂无公告").style(TextStyle.BODY),
                        Text.of("新的服务器广播会显示在这里").style(TextStyle.CAPTION)).gap(3.0F);
            }
            String category = latest.isGameNotice() ? "梦屿广播" : "服务器公告";
            return Ui.column(
                    Text.of("【" + category + "】" + TerminalData.safe(latest.getNoticeTitle(), "无标题"))
                            .style(TextStyle.BODY).singleLine(),
                    Text.of(TerminalData.safe(latest.getNoticeContent(), "").replace('\n', ' '))
                            .style(TextStyle.CAPTION).maxLines(2)
            ).gap(3.0F);
        });
        return baseCard(() -> terminal.switchTab(TerminalScreen.Tab.NOTICES)).add(
                TerminalUi.header(Icons.MEGAPHONE, TerminalUi.SKY, "梦屿广播", "BROADCAST", state),
                body,
                Ui.spacer(),
                TerminalUi.footerLink("查看全部广播", TerminalUi.SKY)
        );
    }

    /** 大屏：列出最近三条广播，未读的带强调点。 */
    private Card wideNoticeCard(Badge state) {
        Dynamic<Object> body = Dynamic.of(TerminalData::noticeVersion, ignored -> {
            List<NoticeData> all = TerminalData.notices();
            if (all.isEmpty()) {
                return Ui.column(Text.of("暂无公告").style(TextStyle.BODY),
                        Text.of("新的服务器广播会显示在这里").style(TextStyle.CAPTION)).gap(3.0F);
            }
            Box list = Ui.column().gap(Theme.Space.SM);
            for (int i = all.size() - 1, shown = 0; i >= 0 && shown < 3; i--, shown++) {
                NoticeData notice = all.get(i);
                boolean unread = !TerminalData.isRead(notice);
                list.add(Ui.row(
                        Ui.stack().size(4.0F, 4.0F).radius(2.0F).background(unread ? TerminalUi.GOLD : 0x40FFFFFF)
                                .shrink(0.0F).margin(0.0F, 4.0F, 0.0F, 0.0F),
                        Ui.column(Text.of(TerminalData.safe(notice.getNoticeTitle(), "无标题")).style(TextStyle.LABEL_STRONG)
                                        .singleLine(),
                                Text.of(TerminalData.safe(notice.getNoticeContent(), "").replace('\n', ' ')).style(TextStyle.CAPTION)
                                        .singleLine()).gap(1.0F).grow(1.0F).shrink(1.0F)
                ).gap(6.0F).alignItems(Align.START));
            }
            return list;
        });
        return baseCard(() -> terminal.switchTab(TerminalScreen.Tab.NOTICES)).add(
                TerminalUi.header(Icons.MEGAPHONE, TerminalUi.SKY, "梦屿广播", "BROADCAST", state),
                body,
                Ui.spacer(),
                TerminalUi.footerLink("查看全部广播", TerminalUi.SKY)
        );
    }

    // ==================== 私信 ====================

    private Card messageCard() {
        Badge state = TerminalUi.chip(() -> {
            if (!NpcMessageClientCache.isLoaded()) {
                return "同步中";
            }
            int unread = NpcMessageClientCache.getUnreadCount();
            return unread > 0 ? unread + " 条未读" : "全部已读";
        }, TerminalUi.STEEL);
        state.onUpdate(() -> state.color(NpcMessageClientCache.getUnreadCount() > 0 ? TerminalUi.GOLD : TerminalUi.STEEL));
        Dynamic<Object> body = Dynamic.of(NpcMessageClientCache::getConversations, value -> {
            @SuppressWarnings("unchecked")
            List<NpcConversationViewData> conversations = (List<NpcConversationViewData>) value;
            if (conversations.isEmpty()) {
                return Ui.column(Text.of("还没有建立 NPC 私人频道").style(TextStyle.BODY_SECONDARY),
                        Text.of("与剧情 NPC 交谈后会在这里留下记录").style(TextStyle.CAPTION)).gap(3.0F);
            }
            if (wide) {
                Box list = Ui.column().gap(Theme.Space.SM);
                for (int i = 0; i < Math.min(3, conversations.size()); i++) {
                    NpcConversationViewData conversation = conversations.get(i);
                    list.add(Ui.column(
                            Ui.row(Text.of(conversation.npcName()).style(TextStyle.LABEL_STRONG).color(0xFFB8DDF4).singleLine(),
                                    Text.of(conversation.relationName()).style(TextStyle.CAPTION).singleLine()).gap(6.0F),
                            Text.of(TerminalData.latestPreview(conversation)).style(TextStyle.CAPTION).maxLines(2)
                    ).gap(2.0F).padding(Theme.Space.SM, Theme.Space.XS + 1.0F).radius(Theme.Radius.MD).background(0x30060A0E));
                }
                return list;
            }
            NpcConversationViewData latest = conversations.get(0);
            return Ui.column(
                    Ui.row(Text.of(latest.npcName()).style(TextStyle.BODY).color(0xFFB8DDF4).singleLine(),
                            Text.of(latest.relationName()).style(TextStyle.CAPTION).singleLine()).gap(6.0F),
                    Text.of(TerminalData.latestPreview(latest)).style(TextStyle.CAPTION).maxLines(2)
            ).gap(3.0F);
        });
        return baseCard(() -> terminal.switchTab(TerminalScreen.Tab.MESSAGES)).add(
                TerminalUi.header(Icons.MAIL, MESSAGE_BLUE, "NPC 私信", "PRIVATE CHANNEL", state),
                body,
                Ui.spacer(),
                TerminalUi.footerLink("查看会话", MESSAGE_BLUE)
        );
    }

    // ==================== 历史与手册 ====================

    private Card historyCard() {
        Dynamic<Object> body = Dynamic.of(TerminalData::history, value -> {
            @SuppressWarnings("unchecked")
            List<Packet_WorldHistoryResponse.HistoryEntry> entries = (List<Packet_WorldHistoryResponse.HistoryEntry>) value;
            if (!TerminalData.historyLoaded()) {
                return Text.of("正在读取世界年表").style(TextStyle.CAPTION);
            }
            if (entries.isEmpty()) {
                return Ui.column(Text.of("尚未留下公开历史").style(TextStyle.LABEL_STRONG).singleLine(),
                        Text.of("故事事件会记录在这里").style(TextStyle.CAPTION).maxLines(2)).gap(2.0F);
            }
            TerminalData.HistoryView view = TerminalData.describe(entries.get(entries.size() - 1));
            return Ui.column(Text.of(view.title()).style(TextStyle.LABEL_STRONG).maxLines(2),
                    Text.of(view.subtitle()).style(TextStyle.CAPTION).singleLine()).gap(2.0F);
        });
        return baseCard(() -> terminal.push(new HistoryPage(terminal))).add(
                TerminalUi.header(Icons.HISTORY, TerminalUi.GOLD, "历史", "ARCHIVE", null),
                body,
                Ui.spacer(),
                Text.of(() -> Component.literal(TerminalData.historyLoaded() ? TerminalData.historyTotal() + " 条公开记录" : "同步中"))
                        .style(TextStyle.CAPTION).color(TerminalUi.GOLD).singleLine()
        );
    }

    private Card helpCard() {
        return baseCard(() -> terminal.push(new HelpPage(terminal))).add(
                TerminalUi.header(Icons.HELP, TerminalUi.GREEN, "手册", "MANUAL", null),
                Ui.column(Text.of("从梦屿基础开始").style(TextStyle.LABEL_STRONG).singleLine(),
                        Text.of("身体、感染、死亡与剧情规则").style(TextStyle.CAPTION).maxLines(2)).gap(2.0F),
                Ui.spacer(),
                Text.of("6 个章节").style(TextStyle.CAPTION).color(TerminalUi.GREEN).singleLine()
        );
    }
}
