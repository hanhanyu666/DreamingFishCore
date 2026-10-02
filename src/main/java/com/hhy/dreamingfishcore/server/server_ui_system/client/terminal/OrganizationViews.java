package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextFit;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Badge;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Button;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Card;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationPermissions;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationRank;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationViewData;
import com.hhy.dreamingfishcore.gameplay.organization_system.client.cache.OrganizationClientCache;
import com.hhy.dreamingfishcore.gameplay.organization_system.network.Packet_OrganizationActionRequest;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** 组织页的组件：名录卡片、组织详情、各类弹窗与操作结果提示。 */
final class OrganizationViews {
    private static final int ACCENT = OrganizationPage.ACCENT;
    private static final int OFFLINE = 0xFF4A5A66;

    private OrganizationViews() {
    }

    static OrganizationViewData.Summary find(OrganizationViewData.Snapshot snapshot, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (OrganizationViewData.Summary summary : snapshot.organizations()) {
            if (summary.id().equals(id)) {
                return summary;
            }
        }
        return null;
    }

    // ==================== 名录卡片 ====================

    static Card summaryCard(OrganizationViewData.Summary summary, boolean mine, boolean active, Runnable onOpen) {
        int accent = mine ? TerminalUi.GOLD : ACCENT;
        Card card = TerminalUi.card().padding(Theme.Space.MD, Theme.Space.SM).gap(3.0F).onClick(onOpen);
        if (mine || active) {
            card.accent(accent);
        }
        card.selectedImmediately(active);
        Box top = Ui.row(Text.of(summary.name()).style(TextStyle.LABEL_STRONG)
                .color(mine ? TerminalUi.WARM_TEXT : 0xFFE6EDF3).singleLine().grow(1.0F).shrink(1.0F)).gap(Theme.Space.XS)
                .alignItems(Align.CENTER);
        String relation = relationLabel(summary.relation());
        if (!relation.isEmpty()) {
            top.add(TerminalUi.chip(relation, relationColor(summary.relation())));
        }
        card.add(top, Text.of(summary.memberCount() + " 人 · 会长 " + summary.leaderName())
                .style(TextStyle.CAPTION).singleLine());
        return card;
    }

    static String relationLabel(OrganizationViewData.Relation relation) {
        return switch (relation) {
            case MEMBER -> "我的组织";
            case APPLIED -> "已申请";
            case INVITED -> "邀请你";
            default -> "";
        };
    }

    static int relationColor(OrganizationViewData.Relation relation) {
        return switch (relation) {
            case MEMBER -> TerminalUi.GOLD;
            case APPLIED -> TerminalUi.SKY;
            case INVITED -> TerminalUi.MINT;
            default -> TerminalUi.STEEL;
        };
    }

    // ==================== 详情 ====================

    static UiNode<?> detail(TerminalScreen terminal, OrganizationViewData.Snapshot snapshot,
                            OrganizationViewData.Summary summary) {
        boolean mine = summary.id().equals(snapshot.myOrganizationId());
        OrganizationViewData.Detail detail = mine ? snapshot.myOrganization() : null;
        Box column = Ui.column(hero(terminal, snapshot, summary, detail)).gap(Theme.Space.MD).alignItems(Align.STRETCH);
        if (detail == null) {
            column.add(outsiderNote(summary));
        } else {
            column.add(announcement(detail));
            column.add(fundsAndTerritory(terminal, detail));
            if (!detail.applicants().isEmpty()) {
                column.add(applicants(detail));
            }
            if (!detail.invited().isEmpty()) {
                column.add(invited(detail));
            }
            column.add(members(terminal, snapshot, detail));
            column.add(footer(terminal, detail));
        }
        int index = 0;
        for (UiNode<?> child : column.children()) {
            child.enter(EnterEffect.FADE_UP.delayed(index++ * 35.0F));
        }
        return ScrollView.of(column).edgeFade(ColorRole.SURFACE_SUNKEN);
    }

    private static UiNode<?> hero(TerminalScreen terminal, OrganizationViewData.Snapshot snapshot,
                                  OrganizationViewData.Summary summary, OrganizationViewData.Detail detail) {
        boolean mine = detail != null;
        int accent = mine ? TerminalUi.GOLD : ACCENT;
        Box chips = Ui.row(TerminalUi.chip("会长 " + summary.leaderName(), TerminalUi.STEEL),
                TerminalUi.chip(summary.memberCount() + "/" + snapshot.maxMembers() + " 人", TerminalUi.STEEL))
                .gap(Theme.Space.XS).wrap(true);
        if (mine) {
            chips.add(TerminalUi.chip("我是" + detail.myRankName(), TerminalUi.GOLD));
        } else if (summary.relation() != OrganizationViewData.Relation.NONE) {
            chips.add(TerminalUi.chip(relationLabel(summary.relation()), relationColor(summary.relation())));
        }
        Box title = Ui.row(
                TerminalUi.iconBadge(Icons.USERS, accent, 26.0F),
                Ui.column(
                        Text.of(mine ? "ORGANIZATION · 我的组织" : "ORGANIZATION").style(TextStyle.CAPTION_STRONG.withScale(0.62F))
                                .color(UiColor.withAlpha(accent, 0.8F)).singleLine(),
                        Text.of(summary.name()).style(TextStyle.TITLE).singleLine(),
                        chips
                ).gap(3.0F).grow(1.0F).shrink(1.0F)
        ).gap(Theme.Space.MD).alignItems(Align.CENTER);
        Card card = TerminalUi.card().accent(accent).add(title);
        Box actions = Ui.row().gap(Theme.Space.XS).wrap(true).alignItems(Align.CENTER);
        if (mine) {
            OrganizationRank myRank = OrganizationRank.parse(detail.myRankId());
            if (detail.canEditAnnouncement()) {
                actions.add(Button.of("编辑公告").leadingIcon(Icons.MEGAPHONE).tonal().accentColor(ACCENT).small()
                        .onClick(() -> promptAnnouncement(terminal, detail.announcement(), snapshot.announcementMaxLength())));
            }
            if (detail.canInvite()) {
                actions.add(Button.of("邀请成员").leadingIcon(Icons.PLUS).tonal().accentColor(ACCENT).small()
                        .onClick(() -> promptInvite(terminal)));
            }
            if (OrganizationPermissions.canRename(myRank)) {
                actions.add(Button.of("改名").tonal().accentColor(ACCENT).small()
                        .onClick(() -> promptRename(terminal, detail.name(), snapshot.nameMaxLength())));
            }
        } else {
            boolean inOther = !snapshot.myOrganizationId().isBlank();
            switch (summary.relation()) {
                case APPLIED -> actions.add(Button.of("撤回申请").outlined().small()
                        .onClick(() -> send(Packet_OrganizationActionRequest.Action.CANCEL_APPLICATION, summary.id(), "", false, 0)));
                case INVITED -> actions.add(
                        Button.of("接受邀请").leadingIcon(Icons.CHECK).filled().accentColor(TerminalUi.MINT).small()
                                .onClick(() -> send(Packet_OrganizationActionRequest.Action.RESPOND_INVITE, summary.id(), "", true, 0)),
                        Button.of("拒绝").ghost().small()
                                .onClick(() -> send(Packet_OrganizationActionRequest.Action.RESPOND_INVITE, summary.id(), "", false, 0)));
                default -> {
                    Button apply = Button.of("申请加入").leadingIcon(Icons.ARROW_RIGHT).filled().accentColor(ACCENT).small()
                            .onClick(() -> send(Packet_OrganizationActionRequest.Action.APPLY, summary.id(), "", false, 0));
                    apply.disabled(inOther);
                    actions.add(apply);
                }
            }
            actions.add(Text.of(outsiderHint(summary.relation(), inOther)).style(TextStyle.CAPTION).singleLine()
                    .shrink(1.0F).margin(Theme.Space.XS, 0.0F, 0.0F, 0.0F));
        }
        actions.add(Ui.spacer(), Button.icon(Icons.REFRESH).ghost().small().onClick(OrganizationPage::requestSnapshot)
                .tooltip(Component.literal("刷新")));
        card.add(actions);
        return card;
    }

    private static String outsiderHint(OrganizationViewData.Relation relation, boolean inOther) {
        return switch (relation) {
            case APPLIED -> "已提交申请，等待管理层审批";
            case INVITED -> "这个组织邀请你加入";
            default -> inOther ? "你已在其他组织中" : "申请后由管理层审批";
        };
    }

    private static UiNode<?> outsiderNote(OrganizationViewData.Summary summary) {
        return section(Icons.LOCK, TerminalUi.STEEL, "成员档案", null).add(
                Text.of("公告、成员名单与组织领地仅对成员开放。").style(TextStyle.BODY_SECONDARY),
                Text.of("目前共有 " + summary.memberCount() + " 名成员，由 " + summary.leaderName() + " 担任会长。")
                        .style(TextStyle.CAPTION));
    }

    private static UiNode<?> announcement(OrganizationViewData.Detail detail) {
        String text = detail.announcement() == null || detail.announcement().isBlank() ? null : detail.announcement();
        Box quote = Ui.row(
                Ui.stack().width(2.0F).radius(1.0F).background(UiColor.withAlpha(ACCENT, 0.6F)).shrink(0.0F),
                Text.of(text == null ? "（暂无公告）" : text).style(TextStyle.BODY)
                        .color(text == null ? ColorRole.TEXT_MUTED : ColorRole.TEXT).grow(1.0F).shrink(1.0F)
        ).gap(Theme.Space.SM).alignItems(Align.STRETCH);
        return section(Icons.MEGAPHONE, ACCENT, "组织公告", null).add(quote);
    }

    // ==================== 资金与领地 ====================

    private static UiNode<?> fundsAndTerritory(TerminalScreen terminal, OrganizationViewData.Detail detail) {
        Box stats = Ui.row(
                stat("资金池", detail.funds() + " 梦鱼币", TerminalUi.GOLD),
                stat("组织领地", detail.territories().size() + " / " + detail.maxTerritories(), TerminalUi.GREEN),
                stat("过滤装置", detail.devices().size() + " / " + detail.maxFilterDevices(), TerminalUi.CYAN)
        ).gap(Theme.Space.SM).alignItems(Align.STRETCH);
        Button deposit = Button.of("捐款").leadingIcon(Icons.COIN).tonal().accentColor(TerminalUi.GOLD).small()
                .onClick(() -> promptDeposit(terminal));
        deposit.disabled(!detail.canDepositFunds());
        Card card = section(Icons.FLAG, TerminalUi.GREEN, "资金与领地", deposit).add(stats);

        card.add(TerminalUi.sectionLabel("已登记的组织领地"));
        if (detail.territories().isEmpty()) {
            card.add(hint("还没有登记组织领地；登记后在领地内放置并绑定聚居地过滤装置即可"));
        } else {
            for (OrganizationViewData.TerritoryLine line : detail.territories()) {
                card.add(territoryRow(terminal, line, detail.canManageTerritories(), true));
            }
        }
        if (detail.canManageTerritories()) {
            card.add(TerminalUi.sectionLabel("可登记的自己名下领地"));
            if (detail.availableTerritories().isEmpty()) {
                card.add(hint("没有可登记的领地（需要先用圈地杖与 /confirm_claim 圈地）"));
            } else {
                for (OrganizationViewData.TerritoryLine line : detail.availableTerritories()) {
                    card.add(territoryRow(terminal, line, true, false));
                }
            }
        }
        if (!detail.devices().isEmpty()) {
            card.add(TerminalUi.sectionLabel("聚居地过滤装置 · 右键设备绑定或解绑"));
            for (OrganizationViewData.DeviceLine device : detail.devices()) {
                int color = device.active() ? TerminalUi.MINT : TerminalUi.ROSE;
                card.add(Ui.row(dot(color),
                        Text.of(device.active() ? "工作中" : "已停机").style(TextStyle.LABEL_STRONG).color(color).singleLine(),
                        Text.of(shortDimension(device.dimensionId()) + " (" + device.x() + ", " + device.y() + ", " + device.z() + ")")
                                .style(TextStyle.CAPTION).singleLine().grow(1.0F).shrink(1.0F)
                ).gap(6.0F).alignItems(Align.CENTER));
            }
        }
        return card;
    }

    private static UiNode<?> stat(String label, String value, int color) {
        return Ui.column(Text.of(label).style(TextStyle.CAPTION).singleLine(),
                        Text.of(value).style(TextStyle.LABEL_STRONG).color(color).singleLine())
                .gap(2.0F).padding(Theme.Space.SM, Theme.Space.XS + 1.0F).radius(Theme.Radius.MD)
                .background(0x40060A0E).border(1.0F, UiColor.withAlpha(color, 0.18F)).grow(1.0F).basis(0.0F);
    }

    private static UiNode<?> territoryRow(TerminalScreen terminal, OrganizationViewData.TerritoryLine line,
                                          boolean actionable, boolean registered) {
        UiNode<?> info;
        if (line.missing()) {
            info = Text.of("已失效的登记（领地不存在或读不到）").style(TextStyle.LABEL).color(TerminalUi.ROSE).singleLine();
        } else {
            info = Ui.column(Text.of(line.name()).style(TextStyle.LABEL_STRONG).singleLine(),
                    Text.of(shortDimension(line.dimensionId()) + " [" + line.minX() + ", " + line.minZ() + " → "
                            + line.maxX() + ", " + line.maxZ() + "] · 面积 " + line.area())
                            .style(TextStyle.CAPTION).singleLine()).gap(1.0F);
        }
        Box row = Ui.row(Icon.of(Icons.MAP, 10.0F).color(line.missing() ? TerminalUi.ROSE : TerminalUi.GREEN),
                        info.grow(1.0F).shrink(1.0F))
                .gap(Theme.Space.SM).alignItems(Align.CENTER).padding(Theme.Space.SM, Theme.Space.XS + 1.0F)
                .radius(Theme.Radius.MD).background(0x30060A0E);
        if (actionable && !line.territoryId().isBlank()) {
            row.add(registered
                    ? Button.of("移除").outlined().small().onClick(() -> TerminalPrompt.confirm(terminal, "移除组织领地",
                            "把「" + line.name() + "」从组织领地中移除？领地本身不会被取消，只是不再算作组织领地。", true,
                            () -> send(Packet_OrganizationActionRequest.Action.UNREGISTER_TERRITORY, line.territoryId(), "", false, 0)))
                    : Button.of("登记").tonal().accentColor(TerminalUi.GREEN).small()
                            .onClick(() -> send(Packet_OrganizationActionRequest.Action.REGISTER_TERRITORY, line.territoryId(), "", false, 0)));
        }
        return row;
    }

    // ==================== 成员 ====================

    private static UiNode<?> applicants(OrganizationViewData.Detail detail) {
        Card card = section(Icons.BELL, TerminalUi.GOLD, "入会申请 · " + detail.applicants().size(), null);
        card.accent(TerminalUi.GOLD);
        for (OrganizationViewData.MemberLine applicant : detail.applicants()) {
            Box row = Ui.row(dot(applicant.online() ? TerminalUi.MINT : OFFLINE),
                    Text.of(applicant.name()).style(TextStyle.LABEL_STRONG).singleLine().grow(1.0F).shrink(1.0F))
                    .gap(6.0F).alignItems(Align.CENTER);
            if (detail.canReviewApplications()) {
                row.add(Button.of("批准").filled().accentColor(TerminalUi.MINT).small()
                                .onClick(() -> send(Packet_OrganizationActionRequest.Action.REVIEW_APPLICATION, applicant.playerId(), "", true, 0)),
                        Button.of("拒绝").ghost().small()
                                .onClick(() -> send(Packet_OrganizationActionRequest.Action.REVIEW_APPLICATION, applicant.playerId(), "", false, 0)));
            } else {
                row.add(Text.of("等待管理层审批").style(TextStyle.CAPTION).singleLine());
            }
            card.add(row);
        }
        return card;
    }

    private static UiNode<?> invited(OrganizationViewData.Detail detail) {
        Box names = Ui.row().gap(Theme.Space.XS).wrap(true);
        for (OrganizationViewData.MemberLine line : detail.invited()) {
            names.add(TerminalUi.chip(line.name() + (line.online() ? " · 在线" : " · 离线"),
                    line.online() ? TerminalUi.MINT : TerminalUi.STEEL));
        }
        return section(Icons.MAIL, TerminalUi.SKY, "已邀请 · 等待对方接受", null).add(names);
    }

    private static UiNode<?> members(TerminalScreen terminal, OrganizationViewData.Snapshot snapshot,
                                     OrganizationViewData.Detail detail) {
        long online = detail.members().stream().filter(OrganizationViewData.MemberLine::online).count();
        Card card = section(Icons.USERS, ACCENT, "成员 · " + detail.members().size() + "/" + snapshot.maxMembers(),
                TerminalUi.chip(online + " 人在线", TerminalUi.MINT));
        if (detail.members().isEmpty()) {
            return card.add(hint("暂无成员"));
        }
        OrganizationRank myRank = OrganizationRank.parse(detail.myRankId());
        Box list = Ui.column().gap(2.0F).alignItems(Align.STRETCH);
        for (OrganizationViewData.MemberLine member : detail.members()) {
            list.add(memberRow(terminal, member, myRank));
        }
        return card.add(list);
    }

    private static UiNode<?> memberRow(TerminalScreen terminal, OrganizationViewData.MemberLine member,
                                       OrganizationRank myRank) {
        OrganizationRank rank = OrganizationRank.parse(member.rankId());
        boolean self = isLocalPlayer(member.playerId());
        Box row = Ui.row(dot(member.online() ? TerminalUi.MINT : OFFLINE),
                        Text.of(member.name() + (self ? "（我）" : "")).style(TextStyle.LABEL_STRONG)
                                .color(member.online() ? ColorRole.TEXT : ColorRole.TEXT_SECONDARY).singleLine()
                                .grow(1.0F).shrink(1.0F),
                        TerminalUi.chip(rank.displayName(), rank.atLeast(OrganizationRank.OFFICER) ? TerminalUi.GOLD : TerminalUi.STEEL))
                .gap(6.0F).alignItems(Align.CENTER).padding(Theme.Space.SM, 3.0F).radius(Theme.Radius.MD);
        row.onHover(hovered -> row.background(hovered ? 0x14FFFFFF : 0));
        if (self) {
            return row;
        }
        OrganizationRank promote = rankByOffset(rank, 1);
        OrganizationRank demote = rankByOffset(rank, -1);
        if (promote != null && OrganizationPermissions.canChangeRank(myRank, rank, promote)) {
            row.add(Button.icon(Icons.CHEVRON_UP).ghost().small().tooltip(Component.literal("升为" + promote.displayName()))
                    .onClick(() -> send(Packet_OrganizationActionRequest.Action.SET_RANK, member.playerId(), promote.name(), false, 0)));
        }
        if (demote != null && OrganizationPermissions.canChangeRank(myRank, rank, demote)) {
            row.add(Button.icon(Icons.CHEVRON_DOWN).ghost().small().tooltip(Component.literal("降为" + demote.displayName()))
                    .onClick(() -> send(Packet_OrganizationActionRequest.Action.SET_RANK, member.playerId(), demote.name(), false, 0)));
        }
        if (OrganizationPermissions.canTransferLeadership(myRank) && !rank.atLeast(OrganizationRank.LEADER)) {
            row.add(Button.of("转让").ghost().small().onClick(() -> TerminalPrompt.confirm(terminal, "转让会长",
                    "把会长转让给 " + member.name() + "？转让后你将成为管理员。", true,
                    () -> send(Packet_OrganizationActionRequest.Action.TRANSFER_LEADERSHIP, member.playerId(), "", false, 0))));
        }
        if (OrganizationPermissions.canKick(myRank, rank)) {
            row.add(Button.of("移出").ghost().small().accentColor(TerminalUi.ROSE).onClick(() -> TerminalPrompt.confirm(terminal,
                    "移出成员", "确定要把 " + member.name() + " 移出组织吗？", true,
                    () -> send(Packet_OrganizationActionRequest.Action.KICK, member.playerId(), "", false, 0))));
        }
        return row;
    }

    private static UiNode<?> footer(TerminalScreen terminal, OrganizationViewData.Detail detail) {
        OrganizationRank myRank = OrganizationRank.parse(detail.myRankId());
        if (OrganizationPermissions.canDisband(myRank)) {
            return Ui.row(Button.of("解散组织").leadingIcon(Icons.WARNING).danger().small().onClick(() -> TerminalPrompt.confirm(terminal,
                            "解散组织", "解散后所有成员都会离开，且无法恢复。确定吗？", true,
                            () -> send(Packet_OrganizationActionRequest.Action.DISBAND, "", "", false, 0))),
                    hint("会长需先转让或解散组织才能离开")).gap(Theme.Space.SM).alignItems(Align.CENTER);
        }
        return Ui.row(Button.of("退出组织").danger().small().onClick(() -> TerminalPrompt.confirm(terminal,
                        "退出组织", "确定要退出当前组织吗？", true,
                        () -> send(Packet_OrganizationActionRequest.Action.LEAVE, "", "", false, 0))),
                hint("退出后可以重新申请加入")).gap(Theme.Space.SM).alignItems(Align.CENTER);
    }

    // ==================== 弹窗 ====================

    static void promptCreate(TerminalScreen terminal) {
        OrganizationViewData.Snapshot snapshot = OrganizationClientCache.get();
        int maxName = Math.max(2, Math.min(32, snapshot.nameMaxLength()));
        int cost = Math.max(0, snapshot.creationCost());
        String fee = cost > 0 ? "消耗 " + cost + " 梦鱼币。" : "免费创建。";
        TerminalPrompt.text(terminal, Icons.USERS, "创建组织",
                fee + "名称 2-" + maxName + " 字，不能包含空格或颜色代码。", maxName, "", List.of(),
                value -> send(Packet_OrganizationActionRequest.Action.CREATE, "", value, false, 0));
    }

    private static void promptRename(TerminalScreen terminal, String currentName, int maxName) {
        TerminalPrompt.text(terminal, Icons.USERS, "修改组织名称", "当前：" + currentName,
                Math.max(2, maxName), currentName, List.of(),
                value -> send(Packet_OrganizationActionRequest.Action.RENAME, "", value, false, 0));
    }

    private static void promptAnnouncement(TerminalScreen terminal, String current, int maxLength) {
        TerminalPrompt.text(terminal, Icons.MEGAPHONE, "编辑公告", "最多 " + maxLength + " 字。",
                Math.max(1, maxLength), current == null ? "" : current, List.of(),
                value -> send(Packet_OrganizationActionRequest.Action.ANNOUNCE, "", value, false, 0));
    }

    private static void promptInvite(TerminalScreen terminal) {
        // 邀请按在线玩家名解析，所以候选里只放当前在线的人
        List<String> online = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            for (var info : minecraft.getConnection().getOnlinePlayers()) {
                if (minecraft.player == null || !info.getProfile().getId().equals(minecraft.player.getUUID())) {
                    online.add(info.getProfile().getName());
                }
            }
        }
        online.sort(String.CASE_INSENSITIVE_ORDER);
        TerminalPrompt.text(terminal, Icons.PLUS, "邀请成员", "输入在线玩家的名字。", 16, "", online,
                value -> send(Packet_OrganizationActionRequest.Action.INVITE, "", value, false, 0));
    }

    private static void promptDeposit(TerminalScreen terminal) {
        OrganizationViewData.Detail detail = OrganizationClientCache.get().myOrganization();
        int limit = detail == null ? 0 : Math.max(1, detail.maxDeposit());
        TerminalPrompt.text(terminal, Icons.COIN, "向组织资金池捐款",
                "从你自己的梦鱼币账户扣除，单次最多 " + limit + " 梦鱼币。余额只用于设备维护费，不能取现。",
                12, "", List.of(), value -> {
                    int amount = parseAmount(value);
                    if (amount > 0) {
                        send(Packet_OrganizationActionRequest.Action.DEPOSIT, "", "", false, Math.min(amount, limit));
                    }
                });
    }

    private static void send(Packet_OrganizationActionRequest.Action action, String targetId, String text,
                             boolean flag, int amount) {
        DreamingFishCore_NetworkManager.sendToServer(new Packet_OrganizationActionRequest(action, targetId, text, flag, amount));
    }

    // ==================== 小工具 ====================

    private static Card section(Icons icon, int accent, String title, UiNode<?> trailing) {
        return TerminalUi.card().gap(Theme.Space.SM).add(TerminalUi.header(icon, accent, title, trailing));
    }

    private static Text hint(String text) {
        return Text.of(text).style(TextStyle.CAPTION).maxLines(2);
    }

    private static UiNode<?> dot(int color) {
        return Ui.stack().size(5.0F, 5.0F).radius(2.5F).background(color).shrink(0.0F);
    }

    /** 非数字或不大于 0 的输入一律视为 0，由调用方忽略。 */
    private static int parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String shortDimension(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return "未知维度";
        }
        int colon = dimensionId.indexOf(':');
        return colon < 0 ? dimensionId : dimensionId.substring(colon + 1);
    }

    private static OrganizationRank rankByOffset(OrganizationRank rank, int offset) {
        OrganizationRank[] values = OrganizationRank.values();
        int target = rank.ordinal() + offset;
        return target < 0 || target >= values.length ? null : values[target];
    }

    private static boolean isLocalPlayer(String playerId) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getUUID().toString().equals(playerId);
    }

    /** 页面底部的操作结果提示：服务端回话会进聊天栏，但终端挡住了聊天栏，这里再显示几秒。 */
    static final class ResultToast extends UiNode<ResultToast> {
        private static final long VISIBLE_MS = 4200L;

        ResultToast() {
            pointerEvents(false);
        }

        @Override
        protected void paintOverlay(UiCanvas canvas) {
            OrganizationClientCache.ActionResult result = OrganizationClientCache.lastResult();
            if (result == null || result.message().isBlank()) {
                return;
            }
            long age = Util.getMillis() - result.timeMillis();
            if (age < 0L || age > VISIBLE_MS) {
                return;
            }
            float alpha = Math.min(1.0F, age / 160.0F) * Math.min(1.0F, (VISIBLE_MS - age) / 500.0F);
            Font font = Minecraft.getInstance().font;
            int color = result.success() ? TerminalUi.MINT : TerminalUi.ROSE;
            String text = TextFit.trim(result.message(), font, (int) Math.max(40.0F, width() - 60.0F));
            float textW = font.width(text);
            float w = textW + 32.0F;
            float h = 20.0F;
            float x = (width() - w) * 0.5F;
            float y = height() - h - 6.0F + (1.0F - alpha) * 6.0F;
            canvas.pushAlpha(alpha);
            canvas.shape(x, y, w, h).radius(h * 0.5F).fill(0xF0101820)
                    .border(1.0F, UiColor.withAlpha(color, 0.55F)).shadow(Theme.Elevation.LEVEL3).draw();
            Icon.paint(canvas, result.success() ? Icons.CHECK : Icons.WARNING, x + 8.0F, y + 5.0F, 10.0F, color);
            canvas.text(text, x + 22.0F, y + (h - 8.0F) * 0.5F, 0xFFE8EDF2, 1.0F, false);
            canvas.popAlpha();
        }
    }
}
