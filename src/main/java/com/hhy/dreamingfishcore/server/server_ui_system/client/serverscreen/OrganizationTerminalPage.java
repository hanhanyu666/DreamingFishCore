package com.hhy.dreamingfishcore.server.server_ui_system.client.serverscreen;

import com.hhy.dreamingfishcore.client.ui.components.UiPanelRenderer;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationPermissions;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationRank;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationViewData;
import com.hhy.dreamingfishcore.gameplay.organization_system.client.Screen_OrganizationPrompt;
import com.hhy.dreamingfishcore.gameplay.organization_system.client.cache.OrganizationClientCache;
import com.hhy.dreamingfishcore.gameplay.organization_system.network.Packet_OrganizationActionRequest;
import com.hhy.dreamingfishcore.gameplay.organization_system.network.Packet_OrganizationSnapshotRequest;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 终端里的「组织」页面（一级模块，索引见 {@link ServerScreenUI_Screen#ORGANIZATION_PAGE_INDEX}）。
 *
 * <p>页面逻辑独立于那 4700 行的终端主类：主类只负责把虚拟坐标传进来、转发鼠标事件。
 * 这样组织页的改动不会牵连终端其它页面。</p>
 *
 * <p><b>数据来源</b>：客户端只读 {@link OrganizationClientCache} 里的服务端快照，从不自己
 * 推断权限。服务端已经把「能否审批 / 能否邀请 / 能否编辑公告 / 能否管理成员」算好下发，
 * 本页只按这些开关决定画不画按钮；每个按钮点下去仍然只是发一个请求，成不成由服务端说了算。</p>
 *
 * <p>视图状态（选中组织、筛选词、滚动位置）是 <b>static</b> 的：输入弹窗关闭后终端会被重建，
 * 用实例字段会让玩家每次输完字都回到列表顶部。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class OrganizationTerminalPage {

    // ==================== 配色（沿用终端平板配色） ====================
    private static final int PANEL_BG = 0xFF18232D;
    private static final int PANEL_BORDER = 0xFF344555;
    private static final int CARD_BG = 0xFF24313E;
    private static final int CARD_HOVER = 0xFF2C3B4A;
    private static final int CARD_ACTIVE = 0xFF294052;
    private static final int CARD_BORDER = 0xFF344555;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFFA7B2BE;
    private static final int ACCENT = 0xFF8CCEFF;
    private static final int ACCENT_GREEN = 0xFF78D6A3;
    private static final int ACCENT_GOLD = 0xFFFFC857;
    private static final int DANGER = 0xFFFF7A88;
    private static final int SECTION_TITLE = 0xFF9FB6C6;

    private static final int BTN_BG = 0xFF2A3A48;
    private static final int BTN_BG_HOVER = 0xFF37495A;
    private static final int BTN_BORDER = 0xFF3E5266;
    private static final int BTN_PRIMARY_BG = 0xFF1F4A5C;
    private static final int BTN_PRIMARY_BORDER = 0xFF4FA8C7;
    private static final int BTN_DANGER_BG = 0xFF4A2A31;
    private static final int BTN_DANGER_BORDER = 0xFF9C4A57;

    private static final int GAP = 10;
    private static final int ROW_HEIGHT = 40;
    private static final int ROW_GAP = 6;
    private static final int MEMBER_ROW_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 16;
    private static final int SMALL_BUTTON_HEIGHT = 14;
    /** 领地行高度：比成员行略高，因为要放坐标与面积。 */
    private static final int TERRITORY_ROW_HEIGHT = 20;

    // ==================== 跨界面保留的视图状态 ====================
    private static String selectedOrgId = "";
    private static String filter = "";
    private static long leftScroll = 0L;
    private static long rightScroll = 0L;

    /** 重置视图状态（仅测试/调试用）。 */
    public static void resetViewState() {
        selectedOrgId = "";
        filter = "";
        leftScroll = 0L;
        rightScroll = 0L;
    }

    // ==================== 实例状态 ====================
    /** 本帧登记的可点击区域；后画的在上层，点击时倒序命中。 */
    private final List<Hit> hits = new ArrayList<>();
    private long leftMaxScroll = 0L;
    private long rightMaxScroll = 0L;
    private int listAreaTop = 0;
    private int listAreaBottom = 0;
    private int detailAreaTop = 0;
    private int detailAreaBottom = 0;
    private int listPanelX1 = 0;
    private int listPanelX2 = 0;
    private int detailPanelX1 = 0;
    private int detailPanelX2 = 0;

    private enum ButtonStyle {
        NORMAL,
        PRIMARY,
        DANGER
    }

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
    }

    /** 纵向布局游标：自动套用滚动偏移，并累计内容总高度。 */
    private static final class Cursor {
        private final int top;
        private final int bottom;
        private int y;
        private int total;

        Cursor(int top, int bottom, long scroll) {
            this.top = top;
            this.bottom = bottom;
            this.y = top - (int) Math.max(0L, scroll);
        }

        boolean visible(int height) {
            return y + height > top && y < bottom;
        }

        int y() {
            return y;
        }

        void advance(int height) {
            y += height;
            total += height;
        }

        int maxScroll() {
            return (int) Math.max(0L, (long) total - (bottom - top));
        }
    }

    // ==================== 生命周期 ====================

    /** 进入页面时拉一次快照，避免显示上一次会话的陈旧数据。 */
    public void onOpened() {
        DreamingFishCore_NetworkManager.sendToServer(new Packet_OrganizationSnapshotRequest());
    }

    // ==================== 渲染 ====================

    public void render(GuiGraphics guiGraphics, Font font, float mouseX, float mouseY,
                       int x, int y, int width, int height, float uiScale) {
        hits.clear();
        OrganizationViewData.Snapshot snapshot = OrganizationClientCache.get();

        if (!snapshot.enabled()) {
            renderPlaceholder(guiGraphics, font, x, y, width, height,
                    "组织功能未启用", "服主可在 config/dreamingfishcore/organization.json 中开启");
            return;
        }
        if (!OrganizationClientCache.isLoaded()) {
            renderPlaceholder(guiGraphics, font, x, y, width, height,
                    "正在同步组织数据", "终端正在向服务端拉取组织列表");
            return;
        }

        ensureSelection(snapshot);

        int listWidth = clamp((int) (width * 0.30f), 128, 190);
        int listX = x;
        int rightX = x + listWidth + GAP;
        int rightWidth = width - listWidth - GAP;

        // 记下两栏的横向范围，滚轮时用它区分「在滚左栏还是右栏」。
        listPanelX1 = listX;
        listPanelX2 = listX + listWidth;
        detailPanelX1 = rightX;
        detailPanelX2 = rightX + rightWidth;

        renderList(guiGraphics, font, mouseX, mouseY, listX, y, listWidth, height, snapshot);
        renderDetail(guiGraphics, font, mouseX, mouseY, rightX, y, rightWidth, height, snapshot);
    }

    private void renderPlaceholder(GuiGraphics guiGraphics, Font font, int x, int y, int width, int height,
                                   String title, String hint) {
        drawPanel(guiGraphics, x, y, width, height);
        drawCentered(guiGraphics, font, title, x + width / 2, y + height / 2 - 14, TEXT);
        drawCentered(guiGraphics, font, hint, x + width / 2, y + height / 2 + 4, MUTED);
    }

    private void ensureSelection(OrganizationViewData.Snapshot snapshot) {
        List<OrganizationViewData.Summary> organizations = snapshot.organizations();
        if (!selectedOrgId.isBlank() && findSummary(organizations, selectedOrgId) != null) {
            return;
        }
        if (!snapshot.myOrganizationId().isBlank()) {
            selectedOrgId = snapshot.myOrganizationId();
            return;
        }
        selectedOrgId = organizations.isEmpty() ? "" : organizations.get(0).id();
    }

    // ==================== 左侧：组织列表 ====================

    private void renderList(GuiGraphics guiGraphics, Font font, float mouseX, float mouseY,
                            int x, int y, int width, int height,
                            OrganizationViewData.Snapshot snapshot) {
        drawPanel(guiGraphics, x, y, width, height);
        drawText(guiGraphics, font, "组织列表", x + 10, y + 9, TEXT);
        String countText = snapshot.organizations().size() + " 个";
        drawText(guiGraphics, font, countText,
                x + width - font.width(countText) - 10, y + 9, MUTED);

        int toolY = y + 26;
        int innerWidth = width - 20;
        int halfWidth = (innerWidth - 6) / 2;
        String searchLabel = filter.isBlank() ? "搜索" : "筛选：" + filter;
        button(guiGraphics, font, mouseX, mouseY, x + 10, toolY, halfWidth, BUTTON_HEIGHT,
                fit(font, searchLabel, halfWidth - 8), ButtonStyle.NORMAL, this::promptSearch);
        boolean ownsOrganization = !snapshot.myOrganizationId().isBlank();
        button(guiGraphics, font, mouseX, mouseY, x + 16 + halfWidth, toolY, halfWidth, BUTTON_HEIGHT,
                "创建组织", ButtonStyle.PRIMARY, ownsOrganization ? null : this::promptCreate);

        int rowsTop = toolY + BUTTON_HEIGHT + 8;
        int rowsBottom = y + height - 8;
        listAreaTop = rowsTop;
        listAreaBottom = rowsBottom;

        List<OrganizationViewData.Summary> visibleOrganizations = filtered(snapshot.organizations());
        if (visibleOrganizations.isEmpty()) {
            drawCentered(guiGraphics, font, filter.isBlank() ? "还没有任何组织" : "没有匹配的组织",
                    x + width / 2, rowsTop + 24, MUTED);
            leftMaxScroll = 0L;
            leftScroll = 0L;
            return;
        }

        int available = Math.max(1, (rowsBottom - rowsTop + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
        int contentMax = Math.max(0, visibleOrganizations.size() - available);
        leftMaxScroll = contentMax;
        leftScroll = clamp(leftScroll, 0, contentMax);

        int drawn = 0;
        for (int index = (int) leftScroll; index < visibleOrganizations.size(); index++) {
            int rowY = rowsTop + drawn * (ROW_HEIGHT + ROW_GAP);
            if (rowY + ROW_HEIGHT > rowsBottom) {
                break;
            }
            renderListRow(guiGraphics, font, mouseX, mouseY, x + 7, rowY, width - 14, ROW_HEIGHT,
                    visibleOrganizations.get(index), snapshot);
            drawn++;
        }
        if (contentMax > 0) {
            drawScrollBar(guiGraphics, x + width - 4, rowsTop, rowsBottom - rowsTop,
                    visibleOrganizations.size(), available, contentMax, leftScroll);
        }
    }

    private void renderListRow(GuiGraphics guiGraphics, Font font, float mouseX, float mouseY,
                               int x, int y, int width, int height,
                               OrganizationViewData.Summary summary,
                               OrganizationViewData.Snapshot snapshot) {
        boolean selected = summary.id().equals(selectedOrgId);
        boolean mine = summary.id().equals(snapshot.myOrganizationId());
        boolean hovered = inside(mouseX, mouseY, x, y, x + width, y + height);
        drawRounded(guiGraphics, x, y, width, height, 3,
                selected ? CARD_ACTIVE : (hovered ? CARD_HOVER : CARD_BG),
                selected || hovered || mine ? (mine ? ACCENT_GOLD : ACCENT) : CARD_BORDER);
        if (mine) {
            guiGraphics.fill(x, y + 4, x + 3, y + height - 4, ACCENT_GOLD);
        }

        String relationLabel = relationLabel(summary.relation());
        int relationWidth = relationLabel.isEmpty() ? 0 : font.width(relationLabel) + 8;
        drawFitText(guiGraphics, font, summary.name(), x + 9, y + 6, width - 18 - relationWidth,
                selected ? TEXT : TEXT);
        if (!relationLabel.isEmpty()) {
            drawText(guiGraphics, font, relationLabel, x + width - font.width(relationLabel) - 8, y + 6,
                    relationColor(summary.relation()));
        }

        String sub = summary.memberCount() + " 人 · 会长 " + summary.leaderName();
        drawFitText(guiGraphics, font, sub, x + 9, y + 22, width - 18, MUTED);

        registerHit(x, y, x + width, y + height, () -> selectedOrgId = summary.id());

        switch (summary.relation()) {
            case NONE -> button(guiGraphics, font, mouseX, mouseY, x + width - 54, y + height - 17,
                    46, SMALL_BUTTON_HEIGHT, "申请", ButtonStyle.PRIMARY,
                    () -> send(Packet_OrganizationActionRequest.Action.APPLY, summary.id(), "", false));
            case APPLIED -> button(guiGraphics, font, mouseX, mouseY, x + width - 66, y + height - 17,
                    58, SMALL_BUTTON_HEIGHT, "撤回申请", ButtonStyle.NORMAL,
                    () -> send(Packet_OrganizationActionRequest.Action.CANCEL_APPLICATION,
                            summary.id(), "", false));
            case INVITED -> button(guiGraphics, font, mouseX, mouseY, x + width - 54, y + height - 17,
                    46, SMALL_BUTTON_HEIGHT, "接受", ButtonStyle.PRIMARY,
                    () -> send(Packet_OrganizationActionRequest.Action.RESPOND_INVITE,
                            summary.id(), "", true));
            case MEMBER -> drawText(guiGraphics, font, "已在会中",
                    x + width - font.width("已在会中") - 8, y + height - 14, ACCENT_GOLD);
            default -> {
            }
        }
    }

    // ==================== 右侧：组织详情 ====================

    private void renderDetail(GuiGraphics guiGraphics, Font font, float mouseX, float mouseY,
                              int x, int y, int width, int height,
                              OrganizationViewData.Snapshot snapshot) {
        drawPanel(guiGraphics, x, y, width, height);
        OrganizationViewData.Summary summary = findSummary(snapshot.organizations(), selectedOrgId);
        if (summary == null) {
            drawCentered(guiGraphics, font, "从左侧选择一个组织", x + width / 2, y + height / 2 - 10, MUTED);
            drawCentered(guiGraphics, font, "或点击「创建组织」建立你自己的组织",
                    x + width / 2, y + height / 2 + 8, MUTED);
            detailAreaTop = y;
            detailAreaBottom = y;
            rightMaxScroll = 0L;
            return;
        }

        boolean mine = summary.id().equals(snapshot.myOrganizationId());
        OrganizationViewData.Detail detail = mine ? snapshot.myOrganization() : null;

        drawText(guiGraphics, font, fit(font, summary.name(), width - 120), x + 12, y + 9, TEXT);
        String membersText = summary.memberCount() + "/" + snapshot.maxMembers() + " 人";
        drawText(guiGraphics, font, membersText, x + width - font.width(membersText) - 12, y + 9, MUTED);
        String leaderText = "会长 " + summary.leaderName()
                + (detail != null ? " · 我是" + detail.myRankName() : "");
        drawFitText(guiGraphics, font, leaderText, x + 12, y + 24, width - 24, MUTED);

        int actionY = y + 40;
        if (detail != null) {
            renderMemberActions(guiGraphics, font, mouseX, mouseY, x, actionY, width, snapshot, detail);
        } else {
            renderOutsiderActions(guiGraphics, font, mouseX, mouseY, x, actionY, width, summary);
        }

        int contentTop = actionY + BUTTON_HEIGHT + 8;
        detailAreaTop = contentTop;
        detailAreaBottom = y + height - 8;

        rightScroll = clamp(rightScroll, 0L, rightMaxScroll);
        Cursor cursor = new Cursor(contentTop, detailAreaBottom, rightScroll);

        if (detail == null) {
            cursor.advance(4);
            renderAnnouncementBox(guiGraphics, font, cursor, x + 10, width - 20, "");
            cursor.advance(GAP);
            renderSectionTitle(guiGraphics, font, cursor, x + 10, "成员");
            if (cursor.visible(14)) {
                // 快照只对成员下发完整名单；外面的人看得到人数，看不到具体是谁。
                drawText(guiGraphics, font, "成员名单仅组织成员可见", x + 10, cursor.y() + 2, MUTED);
            }
            cursor.advance(14);
        } else {
            cursor.advance(4);
            renderAnnouncementBox(guiGraphics, font, cursor, x + 10, width - 20, detail.announcement());
            cursor.advance(GAP);
            renderTerritorySection(guiGraphics, font, mouseX, mouseY, cursor, x + 10, width - 20, detail);
            if (!detail.applicants().isEmpty()) {
                renderSectionTitle(guiGraphics, font, cursor, x + 10,
                        "入会申请（" + detail.applicants().size() + "）");
                for (OrganizationViewData.MemberLine applicant : detail.applicants()) {
                    renderApplicantRow(guiGraphics, font, mouseX, mouseY, cursor, x + 10, width - 20,
                            applicant, detail);
                }
                cursor.advance(GAP);
            }
            if (!detail.invited().isEmpty()) {
                renderSectionTitle(guiGraphics, font, cursor, x + 10,
                        "已邀请，等待对方接受（" + detail.invited().size() + "）");
                for (OrganizationViewData.MemberLine invitedLine : detail.invited()) {
                    renderInvitedRow(guiGraphics, font, cursor, x + 10, width - 20, invitedLine);
                }
                cursor.advance(GAP);
            }
            renderSectionTitle(guiGraphics, font, cursor, x + 10,
                    "成员（" + detail.members().size() + "/" + snapshot.maxMembers() + "）");
            renderMemberLines(guiGraphics, font, mouseX, mouseY, cursor, x + 10, width - 20,
                    detail.members(), detail);
            cursor.advance(GAP);
            renderFooterActions(guiGraphics, font, mouseX, mouseY, cursor, x + 10, width - 20, detail);
        }

        rightMaxScroll = clamp(cursor.maxScroll(), 0, Integer.MAX_VALUE);
        if (rightMaxScroll > 0) {
            int available = Math.max(1, detailAreaBottom - detailAreaTop);
            drawScrollBar(guiGraphics, x + width - 4, detailAreaTop, available,
                    available + (int) rightMaxScroll, available, (int) rightMaxScroll, rightScroll);
        }
    }

    private void renderMemberActions(GuiGraphics guiGraphics, Font font, float mouseX, float mouseY,
                                     int x, int y, int width,
                                     OrganizationViewData.Snapshot snapshot,
                                     OrganizationViewData.Detail detail) {
        OrganizationRank myRank = OrganizationRank.parse(detail.myRankId());
        int cursorX = x + 12;
        boolean canEditAnnouncement = detail.canEditAnnouncement();
        boolean canInvite = detail.canInvite();
        boolean canRename = OrganizationPermissions.canRename(myRank);
        boolean canDisband = OrganizationPermissions.canDisband(myRank);

        if (canEditAnnouncement) {
            cursorX += button(guiGraphics, font, mouseX, mouseY, cursorX, y, 68, BUTTON_HEIGHT,
                    "编辑公告", ButtonStyle.NORMAL,
                    () -> promptAnnouncement(detail.announcement(), snapshot.announcementMaxLength())) + 6;
        }
        if (canInvite) {
            cursorX += button(guiGraphics, font, mouseX, mouseY, cursorX, y, 62, BUTTON_HEIGHT,
                    "邀请成员", ButtonStyle.NORMAL, this::promptInvite) + 6;
        }
        if (canRename) {
            cursorX += button(guiGraphics, font, mouseX, mouseY, cursorX, y, 46, BUTTON_HEIGHT,
                    "改名", ButtonStyle.NORMAL,
                    () -> promptRename(detail.name(), snapshot.nameMaxLength())) + 6;
        }
        if (cursorX < x + width - 12) {
            button(guiGraphics, font, mouseX, mouseY, cursorX, y, 44, BUTTON_HEIGHT,
                    "刷新", ButtonStyle.NORMAL, this::onOpened);
        }
        if (canDisband) {
            // 解散是会长专属的不可逆操作，固定在右侧，和普通按钮拉开距离。
            int disbandX = x + width - 12 - 84;
            if (disbandX > cursorX + 46) {
                button(guiGraphics, font, mouseX, mouseY, disbandX, y, 84, BUTTON_HEIGHT,
                        "解散组织", ButtonStyle.DANGER, this::promptDisband);
            }
        }
    }

    private void renderOutsiderActions(GuiGraphics guiGraphics, Font font, float mouseX, float mouseY,
                                       int x, int y, int width,
                                       OrganizationViewData.Summary summary) {
        String hint = switch (summary.relation()) {
            case APPLIED -> "已提交申请，等待管理层审批";
            case INVITED -> "你收到了这个组织的邀请";
            default -> "你还没有加入这个组织";
        };
        drawFitText(guiGraphics, font, hint, x + 12, y + 4, width - 24, MUTED);

        int buttonX = x + width - 12 - 76;
        switch (summary.relation()) {
            case APPLIED -> button(guiGraphics, font, mouseX, mouseY, buttonX, y, 76, BUTTON_HEIGHT,
                    "撤回申请", ButtonStyle.NORMAL,
                    () -> send(Packet_OrganizationActionRequest.Action.CANCEL_APPLICATION,
                            summary.id(), "", false));
            case INVITED -> {
                button(guiGraphics, font, mouseX, mouseY, x + 12, y, 60, BUTTON_HEIGHT,
                        "接受邀请", ButtonStyle.PRIMARY,
                        () -> send(Packet_OrganizationActionRequest.Action.RESPOND_INVITE,
                                summary.id(), "", true));
                button(guiGraphics, font, mouseX, mouseY, x + 78, y, 60, BUTTON_HEIGHT,
                        "拒绝邀请", ButtonStyle.NORMAL,
                        () -> send(Packet_OrganizationActionRequest.Action.RESPOND_INVITE,
                                summary.id(), "", false));
            }
            default -> button(guiGraphics, font, mouseX, mouseY, buttonX, y, 76, BUTTON_HEIGHT,
                    "申请加入", ButtonStyle.PRIMARY,
                    () -> send(Packet_OrganizationActionRequest.Action.APPLY, summary.id(), "", false));
        }
    }

    private void renderAnnouncementBox(GuiGraphics guiGraphics, Font font, Cursor cursor,
                                       int x, int width, String announcement) {
        String body = announcement == null || announcement.isBlank() ? "（暂无公告）" : announcement;
        List<String> lines = wrap(font, body, width - 16, 6);
        int boxHeight = 20 + lines.size() * (font.lineHeight + 2);
        if (!cursor.visible(boxHeight)) {
            cursor.advance(boxHeight);
            return;
        }
        int boxY = cursor.y();
        drawRounded(guiGraphics, x, boxY, width, boxHeight, 3, 0xFF15202A, CARD_BORDER);
        drawText(guiGraphics, font, "公告", x + 8, boxY + 5, SECTION_TITLE);
        int lineY = boxY + 19;
        for (String line : lines) {
            drawText(guiGraphics, font, line, x + 8, lineY,
                    "（暂无公告）".equals(body) ? MUTED : TEXT);
            lineY += font.lineHeight + 2;
        }
        cursor.advance(boxHeight);
    }

    private void renderSectionTitle(GuiGraphics guiGraphics, Font font, Cursor cursor,
                                    int x, String title) {
        int height = font.lineHeight + 8;
        if (cursor.visible(height)) {
            drawText(guiGraphics, font, title, x, cursor.y() + 2, SECTION_TITLE);
        }
        cursor.advance(height);
    }

    private void renderMemberLines(GuiGraphics guiGraphics, Font font, float mouseX, float mouseY,
                                   Cursor cursor, int x, int width,
                                   List<OrganizationViewData.MemberLine> members,
                                   OrganizationViewData.Detail detail) {
        if (members == null || members.isEmpty()) {
            if (cursor.visible(14)) {
                drawText(guiGraphics, font, "暂无成员", x, cursor.y() + 2, MUTED);
            }
            cursor.advance(14);
            return;
        }
        OrganizationRank myRank = detail == null ? null : OrganizationRank.parse(detail.myRankId());
        for (OrganizationViewData.MemberLine member : members) {
            if (cursor.visible(MEMBER_ROW_HEIGHT)) {
                renderMemberRow(guiGraphics, font, mouseX, mouseY, x, cursor.y(), width,
                        member, myRank);
            }
            cursor.advance(MEMBER_ROW_HEIGHT);
        }
    }

    private void renderMemberRow(GuiGraphics guiGraphics, Font font, float mouseX, float mouseY,
                                 int x, int y, int width,
                                 OrganizationViewData.MemberLine member,
                                 OrganizationRank myRank) {
        OrganizationRank targetRank = OrganizationRank.parse(member.rankId());
        boolean isSelf = isLocalPlayer(member.playerId());
        guiGraphics.fill(x, y + 3, x + 3, y + MEMBER_ROW_HEIGHT - 3,
                member.online() ? ACCENT_GREEN : 0xFF4A5A66);

        int actionsWidth = 0;
        int actionX = x + width;
        // 先算右侧按钮占宽，避免名字被按钮压住。
        boolean canKick = myRank != null && !isSelf
                && OrganizationPermissions.canKick(myRank, targetRank);
        OrganizationRank promote = rankByOffset(targetRank, 1);
        OrganizationRank demote = rankByOffset(targetRank, -1);
        boolean canPromote = myRank != null && !isSelf
                && OrganizationPermissions.canChangeRank(myRank, targetRank, promote);
        boolean canDemote = myRank != null && !isSelf
                && OrganizationPermissions.canChangeRank(myRank, targetRank, demote);
        boolean canTransfer = myRank != null && !isSelf
                && OrganizationPermissions.canTransferLeadership(myRank)
                && !targetRank.atLeast(OrganizationRank.LEADER);
        if (canTransfer) {
            actionsWidth += 42;
        }
        if (canDemote) {
            actionsWidth += 26;
        }
        if (canPromote) {
            actionsWidth += 26;
        }
        if (canKick) {
            actionsWidth += 30;
        }

        drawFitText(guiGraphics, font, member.name(), x + 9, y + 5,
                Math.max(24, width - 18 - actionsWidth - font.width("副会长") - 12), TEXT);
        String rankName = targetRank.displayName();
        drawText(guiGraphics, font, rankName, x + width - actionsWidth - font.width(rankName) - 8,
                y + 5, targetRank.atLeast(OrganizationRank.OFFICER) ? ACCENT_GOLD : MUTED);

        actionX -= 42;
        if (canTransfer) {
            int buttonX = actionX;
            button(guiGraphics, font, mouseX, mouseY, buttonX, y + 2, 40, SMALL_BUTTON_HEIGHT, "转让",
                    ButtonStyle.NORMAL, () -> promptTransfer(member));
        }
        actionX -= 26;
        if (canDemote) {
            int buttonX = actionX;
            button(guiGraphics, font, mouseX, mouseY, buttonX, y + 2, 24, SMALL_BUTTON_HEIGHT, "降",
                    ButtonStyle.NORMAL, () -> send(
                            Packet_OrganizationActionRequest.Action.SET_RANK,
                            member.playerId(), demote.name(), false));
        }
        actionX -= 26;
        if (canPromote) {
            int buttonX = actionX;
            button(guiGraphics, font, mouseX, mouseY, buttonX, y + 2, 24, SMALL_BUTTON_HEIGHT, "升",
                    ButtonStyle.NORMAL, () -> send(
                            Packet_OrganizationActionRequest.Action.SET_RANK,
                            member.playerId(), promote.name(), false));
        }
        actionX -= 30;
        if (canKick) {
            int buttonX = actionX;
            button(guiGraphics, font, mouseX, mouseY, buttonX, y + 2, 28, SMALL_BUTTON_HEIGHT, "踢",
                    ButtonStyle.DANGER, () -> promptKick(member));
        }
        guiGraphics.fill(x, y + MEMBER_ROW_HEIGHT - 1, x + width, y + MEMBER_ROW_HEIGHT,
                0x22FFFFFF);
    }

    private void renderApplicantRow(GuiGraphics guiGraphics, Font font, float mouseX, float mouseY,
                                    Cursor cursor, int x, int width,
                                    OrganizationViewData.MemberLine applicant,
                                    OrganizationViewData.Detail detail) {
        int height = 22;
        if (cursor.visible(height)) {
            int rowY = cursor.y();
            drawRounded(guiGraphics, x, rowY, width, height, 2, 0xFF1E2A35, CARD_BORDER);
            drawText(guiGraphics, font, applicant.name(), x + 8, rowY + 6, TEXT);
            if (detail.canReviewApplications()) {
                button(guiGraphics, font, mouseX, mouseY, x + width - 78, rowY + 4,
                        34, SMALL_BUTTON_HEIGHT, "批准", ButtonStyle.PRIMARY,
                        () -> send(Packet_OrganizationActionRequest.Action.REVIEW_APPLICATION,
                                applicant.playerId(), "", true));
                button(guiGraphics, font, mouseX, mouseY, x + width - 40, rowY + 4,
                        34, SMALL_BUTTON_HEIGHT, "拒绝", ButtonStyle.DANGER,
                        () -> send(Packet_OrganizationActionRequest.Action.REVIEW_APPLICATION,
                                applicant.playerId(), "", false));
            }
        }
        cursor.advance(height);
    }

    private void renderInvitedRow(GuiGraphics guiGraphics, Font font, Cursor cursor,
                                  int x, int width, OrganizationViewData.MemberLine invitedLine) {
        int height = 18;
        if (cursor.visible(height)) {
            drawText(guiGraphics, font, invitedLine.name() + (invitedLine.online() ? "（在线）" : "（离线）"),
                    x + 4, cursor.y() + 3, MUTED);
        }
        cursor.advance(height);
    }

    private void renderFooterActions(GuiGraphics guiGraphics, Font font, float mouseX, float mouseY,
                                     Cursor cursor, int x, int width,
                                     OrganizationViewData.Detail detail) {
        int height = BUTTON_HEIGHT + 6;
        OrganizationRank myRank = OrganizationRank.parse(detail.myRankId());
        boolean isLeader = myRank == OrganizationRank.LEADER;
        if (cursor.visible(height)) {
            int rowY = cursor.y() + 3;
            if (isLeader) {
                button(guiGraphics, font, mouseX, mouseY, x, rowY, 84, BUTTON_HEIGHT,
                        "解散组织", ButtonStyle.DANGER, this::promptDisband);
                drawText(guiGraphics, font, "会长需先转让或解散组织才能离开",
                        x + 90, rowY + 5, MUTED);
            } else {
                button(guiGraphics, font, mouseX, mouseY, x, rowY, 72, BUTTON_HEIGHT,
                        "退出组织", ButtonStyle.DANGER, this::promptLeave);
                drawText(guiGraphics, font, "退出后可以重新申请加入",
                        x + 78, rowY + 5, MUTED);
            }
        }
        cursor.advance(height);
    }

    // ==================== 领地与资金（组织 ↔ 圈地联动） ====================

    /**
     * 资金池 + 组织领地 + 可登记领地 + 设备。
     *
     * <p>所有按钮的可点性都由服务端下发的开关决定（{@code canDepositFunds} /
     * {@code canManageTerritories}），界面不自己判断权限，避免"按钮画得出来、服务端拒绝"。</p>
     */
    private void renderTerritorySection(GuiGraphics guiGraphics, Font font, float mouseX, float mouseY,
                                        Cursor cursor, int x, int width,
                                        OrganizationViewData.Detail detail) {
        renderSectionTitle(guiGraphics, font, cursor, x,
                "领地与资金（领地 " + detail.territories().size() + "/" + detail.maxTerritories()
                        + " · 设备 " + detail.devices().size() + "/" + detail.maxFilterDevices() + "）");
        if (cursor.visible(BUTTON_HEIGHT + 4)) {
            int rowY = cursor.y();
            drawText(guiGraphics, font, "资金池：" + detail.funds() + " 梦鱼币", x, rowY + 4, ACCENT_GREEN);
            int buttonWidth = 48;
            button(guiGraphics, font, mouseX, mouseY, x + width - buttonWidth, rowY, buttonWidth,
                    BUTTON_HEIGHT, "捐款", ButtonStyle.PRIMARY,
                    detail.canDepositFunds() ? this::promptDeposit : null);
        }
        cursor.advance(BUTTON_HEIGHT + 6);

        if (detail.territories().isEmpty()) {
            if (cursor.visible(14)) {
                drawText(guiGraphics, font, "还没有登记组织领地；登记后在领地内放置并绑定聚居地过滤装置即可",
                        x, cursor.y() + 2, MUTED);
            }
            cursor.advance(14);
        } else {
            for (OrganizationViewData.TerritoryLine line : detail.territories()) {
                if (cursor.visible(TERRITORY_ROW_HEIGHT)) {
                    renderTerritoryRow(guiGraphics, font, mouseX, mouseY, x, cursor.y(), width,
                            line, detail, true);
                }
                cursor.advance(TERRITORY_ROW_HEIGHT);
            }
        }
        cursor.advance(GAP);

        if (detail.canManageTerritories()) {
            renderSectionTitle(guiGraphics, font, cursor, x, "可登记的自己名下领地");
            if (detail.availableTerritories().isEmpty()) {
                if (cursor.visible(14)) {
                    drawText(guiGraphics, font, "没有可登记的领地（需要先用圈地杖与 /confirm_claim 圈地）",
                            x, cursor.y() + 2, MUTED);
                }
                cursor.advance(14);
            } else {
                for (OrganizationViewData.TerritoryLine line : detail.availableTerritories()) {
                    if (cursor.visible(TERRITORY_ROW_HEIGHT)) {
                        renderTerritoryRow(guiGraphics, font, mouseX, mouseY, x, cursor.y(), width,
                                line, detail, false);
                    }
                    cursor.advance(TERRITORY_ROW_HEIGHT);
                }
            }
            cursor.advance(GAP);
        }

        if (!detail.devices().isEmpty()) {
            renderSectionTitle(guiGraphics, font, cursor, x, "聚居地过滤装置（右键设备绑定/解绑）");
            for (OrganizationViewData.DeviceLine device : detail.devices()) {
                if (cursor.visible(14)) {
                    String status = device.active() ? "§a● 工作中" : "§c● 已停机";
                    drawText(guiGraphics, font,
                            status + " §7" + shortDimension(device.dimensionId())
                                    + " (" + device.x() + ", " + device.y() + ", " + device.z() + ")",
                            x, cursor.y() + 2, MUTED);
                }
                cursor.advance(14);
            }
            cursor.advance(GAP);
        }
    }

    private void renderTerritoryRow(GuiGraphics guiGraphics, Font font, float mouseX, float mouseY,
                                    int x, int y, int width,
                                    OrganizationViewData.TerritoryLine line,
                                    OrganizationViewData.Detail detail, boolean registered) {
        boolean actionable = detail.canManageTerritories();
        int buttonWidth = actionable ? 48 : 0;
        int textWidth = width - (buttonWidth > 0 ? buttonWidth + 8 : 0);

        String title;
        if (line.missing()) {
            title = "§c已失效的登记（领地不存在或读不到）";
        } else {
            title = "§f" + line.name() + " §7" + shortDimension(line.dimensionId())
                    + " [" + line.minX() + "," + line.minZ() + " → " + line.maxX() + "," + line.maxZ()
                    + "] §7面积 " + line.area();
        }
        drawText(guiGraphics, font, fit(font, title, textWidth), x, y + 3, TEXT);

        if (!actionable || line.territoryId().isBlank()) {
            return;
        }
        button(guiGraphics, font, mouseX, mouseY, x + width - buttonWidth, y, buttonWidth,
                SMALL_BUTTON_HEIGHT, registered ? "移除" : "登记",
                registered ? ButtonStyle.DANGER : ButtonStyle.NORMAL,
                registered
                        ? () -> promptUnregisterTerritory(line)
                        : () -> send(Packet_OrganizationActionRequest.Action.REGISTER_TERRITORY,
                                line.territoryId(), "", false));
    }

    private void promptDeposit() {
        OrganizationViewData.Snapshot snapshot = OrganizationClientCache.get();
        OrganizationViewData.Detail detail = snapshot.myOrganization();
        int limit = detail == null ? 0 : Math.max(1, detail.maxDeposit());
        Screen_OrganizationPrompt.openText("向组织资金池捐款",
                "从你自己的梦鱼币账户扣除；单次最多 " + limit + " 梦鱼币。余额只用于设备维护费，不能取现。",
                12, "", List.of(), value -> {
                    int amount = parseAmount(value);
                    if (amount <= 0) {
                        return;
                    }
                    send(Packet_OrganizationActionRequest.Action.DEPOSIT, "",
                            "", false, Math.min(amount, limit));
                });
    }

    private void promptUnregisterTerritory(OrganizationViewData.TerritoryLine line) {
        Screen_OrganizationPrompt.openConfirm("移除组织领地",
                "把「" + line.name() + "」从组织领地中移除？领地本身不会被取消，只是不再算作组织领地。",
                true, value -> send(Packet_OrganizationActionRequest.Action.UNREGISTER_TERRITORY,
                        line.territoryId(), "", false));
    }

    /** 把输入解析成正整数；非数字或 ≤0 一律返回 0（由调用方静默忽略）。 */
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

    // ==================== 提示与动作 ====================

    private void promptSearch() {
        Screen_OrganizationPrompt.openText("搜索组织", "留空表示显示全部；按名称包含匹配",
                24, filter, List.of(), value -> filter = value.trim());
    }

    private void promptCreate() {
        int maxName = Math.max(2, Math.min(32, OrganizationClientCache.get().nameMaxLength()));
        int cost = Math.max(0, OrganizationClientCache.get().creationCost());
        String fee = cost > 0 ? "消耗 " + cost + " 梦鱼币 · " : "免费 · ";
        Screen_OrganizationPrompt.openText("创建组织",
                fee + "名称 " + 2 + "-" + maxName + " 字，不能包含空格或颜色代码",
                maxName, "", List.of(),
                value -> send(Packet_OrganizationActionRequest.Action.CREATE, "", value, false));
    }

    private void promptRename(String currentName, int maxName) {
        Screen_OrganizationPrompt.openText("修改组织名称", "当前：" + currentName,
                Math.max(2, maxName), currentName, List.of(),
                value -> send(Packet_OrganizationActionRequest.Action.RENAME, "", value, false));
    }

    private void promptAnnouncement(String currentAnnouncement, int maxLength) {
        Screen_OrganizationPrompt.openText("编辑公告",
                "最多 " + maxLength + " 字，换行会被保留",
                Math.max(1, maxLength),
                currentAnnouncement == null ? "" : currentAnnouncement, List.of(),
                value -> send(Packet_OrganizationActionRequest.Action.ANNOUNCE, "", value, false));
    }

    private void promptInvite() {
        // 邀请按在线玩家名解析，所以补全里只放当前在线的人。
        List<String> online = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            for (var info : mc.getConnection().getOnlinePlayers()) {
                online.add(info.getProfile().getName());
            }
        }
        online.sort(String.CASE_INSENSITIVE_ORDER);
        Screen_OrganizationPrompt.openText("邀请成员", "输入在线玩家的名字",
                16, "", online,
                value -> send(Packet_OrganizationActionRequest.Action.INVITE, "", value, false));
    }

    private void promptKick(OrganizationViewData.MemberLine member) {
        Screen_OrganizationPrompt.openConfirm("移出成员",
                "确定要把 " + member.name() + " 移出组织吗？", true,
                value -> send(Packet_OrganizationActionRequest.Action.KICK,
                        member.playerId(), "", false));
    }

    private void promptTransfer(OrganizationViewData.MemberLine member) {
        Screen_OrganizationPrompt.openConfirm("转让会长",
                "把会长转让给 " + member.name() + "？转让后你将成为副会长。", true,
                value -> send(Packet_OrganizationActionRequest.Action.TRANSFER_LEADERSHIP,
                        member.playerId(), "", false));
    }

    private void promptLeave() {
        Screen_OrganizationPrompt.openConfirm("退出组织",
                "确定要退出当前组织吗？", true,
                value -> send(Packet_OrganizationActionRequest.Action.LEAVE, "", "", false));
    }

    private void promptDisband() {
        Screen_OrganizationPrompt.openConfirm("解散组织",
                "解散后所有成员都会离开，且无法恢复。确定吗？", true,
                value -> send(Packet_OrganizationActionRequest.Action.DISBAND, "", "", false));
    }

    private void send(Packet_OrganizationActionRequest.Action action, String targetId,
                      String text, boolean flag) {
        send(action, targetId, text, flag, 0);
    }

    private void send(Packet_OrganizationActionRequest.Action action, String targetId,
                      String text, boolean flag, int amount) {
        DreamingFishCore_NetworkManager.sendToServer(
                new Packet_OrganizationActionRequest(action, targetId, text, flag, amount));
    }

    // ==================== 输入路由 ====================

    /**
     * 处理点击。{@code virtualX}/{@code virtualY} 已是终端虚拟坐标。
     *
     * @return 是否消耗了这次点击
     */
    public boolean mouseClicked(double virtualX, double virtualY) {
        Runnable action = null;
        for (int index = hits.size() - 1; index >= 0; index--) {
            Hit hit = hits.get(index);
            if (hit.action() != null
                    && virtualX >= hit.x1() && virtualX <= hit.x2()
                    && virtualY >= hit.y1() && virtualY <= hit.y2()) {
                action = hit.action();
                break;
            }
        }
        if (action == null) {
            return false;
        }
        // 点击动作可能打开子界面；先取出再执行，避免在遍历中改变界面状态。
        action.run();
        return true;
    }

    public boolean mouseScrolled(double virtualX, double virtualY, double scrollY) {
        if (inside(virtualX, virtualY, listPanelX1, listAreaTop, listPanelX2, listAreaBottom)) {
            leftScroll = clamp(leftScroll - Math.round(scrollY), 0L, leftMaxScroll);
            return leftMaxScroll > 0;
        }
        if (inside(virtualX, virtualY, detailPanelX1, detailAreaTop, detailPanelX2, detailAreaBottom)) {
            rightScroll = clamp(rightScroll - Math.round(scrollY), 0L, rightMaxScroll);
            return rightMaxScroll > 0;
        }
        return false;
    }

    // ==================== 绘制小工具 ====================

    private void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        drawRounded(guiGraphics, x, y, width, height, 3, PANEL_BG, PANEL_BORDER);
    }

    private void drawRounded(GuiGraphics guiGraphics, int x, int y, int width, int height,
                             int radius, int fill, int border) {
        UiPanelRenderer.smoothRoundedRect(guiGraphics, x, y, width, height, radius, fill, border);
    }

    private void drawText(GuiGraphics guiGraphics, Font font, String text, int x, int y, int color) {
        guiGraphics.drawString(font, text == null ? "" : text, x, y, color, false);
    }

    private void drawFitText(GuiGraphics guiGraphics, Font font, String text, int x, int y,
                             int maxWidth, int color) {
        drawText(guiGraphics, font, fit(font, text, maxWidth), x, y, color);
    }

    private void drawCentered(GuiGraphics guiGraphics, Font font, String text, int centerX, int y,
                              int color) {
        String safe = text == null ? "" : text;
        drawText(guiGraphics, font, safe, centerX - font.width(safe) / 2, y, color);
    }

    /**
     * 画一个按钮并登记点击。
     *
     * @return 按钮宽度（供调用方推进布局游标）
     */
    private int button(GuiGraphics guiGraphics, Font font, float mouseX, float mouseY,
                       int x, int y, int width, int height, String label,
                       ButtonStyle style, Runnable action) {
        boolean enabled = action != null;
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, x + width, y + height);
        int fill;
        int border;
        int textColor;
        switch (style) {
            case PRIMARY -> {
                fill = hovered ? 0xFF2A6379 : BTN_PRIMARY_BG;
                border = BTN_PRIMARY_BORDER;
                textColor = 0xFFDDF3FF;
            }
            case DANGER -> {
                fill = hovered ? 0xFF5E353D : BTN_DANGER_BG;
                border = BTN_DANGER_BORDER;
                textColor = 0xFFFFD5DB;
            }
            default -> {
                fill = hovered ? BTN_BG_HOVER : BTN_BG;
                border = BTN_BORDER;
                textColor = hovered ? TEXT : 0xFFC3D2DE;
            }
        }
        if (!enabled) {
            fill = 0xFF202A34;
            border = 0xFF2B3742;
            textColor = 0xFF6B7A87;
        }
        drawRounded(guiGraphics, x, y, width, height, 3, fill, border);
        String safe = fit(font, label, width - 6);
        drawText(guiGraphics, font, safe, x + (width - font.width(safe)) / 2,
                y + (height - font.lineHeight) / 2, textColor);
        if (enabled) {
            registerHit(x, y, x + width, y + height, action);
        }
        return width;
    }

    private void registerHit(int x1, int y1, int x2, int y2, Runnable action) {
        hits.add(new Hit(x1, y1, x2, y2, action));
    }

    private void drawScrollBar(GuiGraphics guiGraphics, int x, int y, int height,
                               int total, int visible, int maxOffset, long offset) {
        if (maxOffset <= 0 || total <= visible) {
            return;
        }
        guiGraphics.fill(x, y, x + 2, y + height, 0x22FFFFFF);
        int thumbHeight = Math.max(12, (int) ((float) visible / total * height));
        int travel = Math.max(1, height - thumbHeight);
        int thumbY = y + (int) ((float) offset / maxOffset * travel);
        guiGraphics.fill(x, thumbY, x + 2, thumbY + thumbHeight, 0x66A8C4D8);
    }

    // ==================== 纯工具 ====================

    private static OrganizationViewData.Summary findSummary(
            List<OrganizationViewData.Summary> organizations, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (OrganizationViewData.Summary summary : organizations) {
            if (summary.id().equals(id)) {
                return summary;
            }
        }
        return null;
    }

    private List<OrganizationViewData.Summary> filtered(List<OrganizationViewData.Summary> organizations) {
        if (filter.isBlank()) {
            return organizations;
        }
        String needle = filter.toLowerCase(Locale.ROOT);
        List<OrganizationViewData.Summary> result = new ArrayList<>();
        for (OrganizationViewData.Summary summary : organizations) {
            if (summary.name().toLowerCase(Locale.ROOT).contains(needle)
                    || summary.leaderName().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(summary);
            }
        }
        return result;
    }

    private static String relationLabel(OrganizationViewData.Relation relation) {
        return switch (relation) {
            case MEMBER -> "我的组织";
            case APPLIED -> "已申请";
            case INVITED -> "已受邀";
            default -> "";
        };
    }

    private static int relationColor(OrganizationViewData.Relation relation) {
        return switch (relation) {
            case MEMBER -> ACCENT_GOLD;
            case APPLIED -> ACCENT;
            case INVITED -> ACCENT_GREEN;
            default -> MUTED;
        };
    }

    private static OrganizationRank rankByOffset(OrganizationRank rank, int offset) {
        if (rank == null) {
            return null;
        }
        int target = rank.ordinal() + offset;
        OrganizationRank[] values = OrganizationRank.values();
        if (target < 0 || target >= values.length) {
            return null;
        }
        return values[target];
    }

    private boolean isLocalPlayer(String playerId) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getUUID().toString().equals(playerId);
    }

    private static boolean inside(float x, float y, int x1, int y1, int x2, int y2) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2;
    }

    private static boolean inside(double x, double y, int x1, int y1, int x2, int y2) {
        return x >= x1 && x <= x2 && y >= y1 && y <= y2;
    }

    private static String fit(Font font, String text, int maxWidth) {
        String safe = text == null ? "" : text;
        if (maxWidth <= 0 || font.width(safe) <= maxWidth) {
            return safe;
        }
        int ellipsis = font.width("...");
        if (maxWidth <= ellipsis) {
            return "...";
        }
        return font.plainSubstrByWidth(safe, maxWidth - ellipsis) + "...";
    }

    /** 按宽度换行；超过 {@code maxLines} 行时最后一行加省略号。 */
    private static List<String> wrap(Font font, String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return lines;
        }
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            String remaining = paragraph;
            while (!remaining.isEmpty()) {
                if (lines.size() == maxLines) {
                    break;
                }
                int fitted = font.plainSubstrByWidth(remaining, maxWidth).length();
                if (fitted <= 0) {
                    fitted = 1;
                }
                lines.add(remaining.substring(0, fitted));
                remaining = remaining.substring(fitted);
            }
            if (lines.size() == maxLines && !remaining.isEmpty()) {
                String last = lines.get(maxLines - 1);
                String trimmed = font.plainSubstrByWidth(last, Math.max(1, maxWidth - font.width("...")))
                        + "...";
                lines.set(maxLines - 1, trimmed);
                break;
            }
        }
        return lines;
    }

    private static int clamp(int value, int lower, int upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private static long clamp(long value, long lower, long upper) {
        return Math.max(lower, Math.min(upper, value));
    }
}
