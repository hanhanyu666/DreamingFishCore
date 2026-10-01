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
import com.hhy.dreamingfishcore.client.ui.framework.widget.EmptyState;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ProgressRing;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Responsive;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.TextField;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.organization_system.OrganizationViewData;
import com.hhy.dreamingfishcore.gameplay.organization_system.client.cache.OrganizationClientCache;
import com.hhy.dreamingfishcore.gameplay.organization_system.network.Packet_OrganizationSnapshotRequest;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 组织：左侧组织名录，右侧组织详情；紧凑布局下点组织进入单独的详情页。
 *
 * <p>页面只读 {@link OrganizationClientCache} 里的服务端快照。服务端已把“能否审批 / 邀请 / 编辑公告 /
 * 管理领地”等开关算好下发，本页只按开关决定显示哪些按钮，点下去仍只是发请求，成不成由服务端决定。</p>
 */
final class OrganizationPage extends TerminalPage {
    static final int ACCENT = TerminalUi.PERIWINKLE;

    // 视图状态跨终端重开保留，避免玩家每次回来都要重新筛选、重新选择
    private static String selectedId = "";
    private static String filter = "";

    private final TextField search = TextField.of("搜索名称或会长").icon(Icons.SEARCH).maxLength(24)
            .accentColor(ACCENT);

    OrganizationPage(TerminalScreen terminal) {
        super(terminal, "组织");
        search.value(filter);
        search.onChange(value -> filter = value.trim());
        search.height(22.0F);
    }

    @Override
    protected void onShow() {
        requestSnapshot();
    }

    static void requestSnapshot() {
        DreamingFishCore_NetworkManager.sendToServer(new Packet_OrganizationSnapshotRequest());
    }

    /** Dock 角标：收到邀请，或有待自己审批的入会申请。 */
    static boolean needsAttention() {
        OrganizationViewData.Snapshot snapshot = OrganizationClientCache.get();
        if (!snapshot.enabled()) {
            return false;
        }
        for (OrganizationViewData.Summary summary : snapshot.organizations()) {
            if (summary.relation() == OrganizationViewData.Relation.INVITED) {
                return true;
            }
        }
        OrganizationViewData.Detail mine = snapshot.myOrganization();
        return mine != null && mine.canReviewApplications() && !mine.applicants().isEmpty();
    }

    private record State(boolean loaded, boolean enabled, Responsive.Size size) {
    }

    private record ListKey(long version, String filter, String selected, boolean compact) {
    }

    private record DetailKey(long version, String selected) {
    }

    @Override
    protected UiNode<?> build() {
        UiNode<?> content = Responsive.of(size -> Dynamic.of(
                () -> new State(OrganizationClientCache.isLoaded(), OrganizationClientCache.get().enabled(), size),
                this::content));
        return Ui.stack(content, new OrganizationViews.ResultToast()).alignItems(Align.STRETCH)
                .padding(Theme.Space.LG, Theme.Space.MD, Theme.Space.LG, Theme.Space.SM);
    }

    private UiNode<?> content(State state) {
        if (!state.loaded()) {
            return Ui.column(ProgressRing.spinner().diameter(22.0F),
                    Text.of("正在同步组织数据").style(TextStyle.SUBTITLE).color(ColorRole.TEXT_SECONDARY),
                    Text.of("终端正在向服务端拉取组织名录").style(TextStyle.CAPTION)).gap(Theme.Space.SM).center().grow(1.0F);
        }
        if (!state.enabled()) {
            return EmptyState.of(Icons.USERS, "组织功能未启用", "服主可在 config/dreamingfishcore/organization.json 中开启");
        }
        boolean compact = state.size() == Responsive.Size.COMPACT;
        Box directory = directory(compact);
        if (compact) {
            return directory;
        }
        Dynamic<DetailKey> detail = Dynamic.of(() -> new DetailKey(OrganizationClientCache.version(), selectedId), key -> {
            OrganizationViewData.Snapshot snapshot = OrganizationClientCache.get();
            OrganizationViewData.Summary summary = OrganizationViews.find(snapshot, key.selected());
            if (summary == null) {
                return EmptyState.of(Icons.USERS, "从左侧选择一个组织", "或点击「创建组织」建立你自己的组织");
            }
            return OrganizationViews.detail(terminal, snapshot, summary);
        });
        Card detailPanel = TerminalUi.card().surface(ColorRole.SURFACE_SUNKEN).noOutline().flat()
                .padding(Theme.Space.MD).add(detail.grow(1.0F));
        return Ui.row(directory.width(178.0F), detailPanel.grow(1.0F).basis(0.0F))
                .alignItems(Align.STRETCH).gap(Theme.Space.MD);
    }

    // ==================== 组织名录 ====================

    private Box directory(boolean compact) {
        Text count = Text.of(() -> Component.literal(OrganizationClientCache.get().organizations().size() + " 个"))
                .style(TextStyle.CAPTION).singleLine();
        Dynamic<Long> create = Dynamic.of(OrganizationClientCache::version, ignored -> {
            OrganizationViewData.Snapshot snapshot = OrganizationClientCache.get();
            if (!snapshot.myOrganizationId().isBlank()) {
                return Ui.space(0.0F);
            }
            int cost = Math.max(0, snapshot.creationCost());
            return Button.of(cost > 0 ? "创建组织 · " + cost + " 梦鱼币" : "创建组织").leadingIcon(Icons.PLUS)
                    .filled().accentColor(ACCENT).small().onClick(() -> OrganizationViews.promptCreate(terminal));
        });
        Dynamic<ListKey> list = Dynamic.of(
                () -> new ListKey(OrganizationClientCache.version(), filter, selectedId, compact),
                key -> organizationList(key.compact()));
        return Ui.column(
                Ui.row(TerminalUi.sectionLabel("组织名录 · DIRECTORY"), Ui.spacer(), count).alignItems(Align.CENTER),
                search,
                create,
                ScrollView.of(list).grow(1.0F).basis(0.0F).edgeFade(ColorRole.SURFACE)
        ).gap(Theme.Space.SM).alignItems(Align.STRETCH);
    }

    private UiNode<?> organizationList(boolean compact) {
        OrganizationViewData.Snapshot snapshot = OrganizationClientCache.get();
        List<OrganizationViewData.Summary> visible = filtered(snapshot.organizations());
        if (visible.isEmpty()) {
            return Text.of(filter.isBlank() ? "还没有任何组织" : "没有匹配的组织").style(TextStyle.CAPTION)
                    .centered().margin(0.0F, Theme.Space.LG, 0.0F, 0.0F);
        }
        if (!compact) {
            ensureSelection(snapshot);
        }
        Box column = Ui.column().gap(Theme.Space.XS).alignItems(Align.STRETCH);
        int index = 0;
        for (OrganizationViewData.Summary summary : visible) {
            boolean mine = summary.id().equals(snapshot.myOrganizationId());
            boolean active = !compact && summary.id().equals(selectedId);
            column.add(OrganizationViews.summaryCard(summary, mine, active, () -> {
                if (compact) {
                    terminal.push(new OrganizationDetailPage(terminal, summary.id(), summary.name()));
                } else {
                    selectedId = summary.id();
                }
            }).enter(EnterEffect.FADE_RIGHT.delayed(Math.min(index++, 8) * 30.0F)));
        }
        return column;
    }

    private static void ensureSelection(OrganizationViewData.Snapshot snapshot) {
        if (!selectedId.isBlank() && OrganizationViews.find(snapshot, selectedId) != null) {
            return;
        }
        if (!snapshot.myOrganizationId().isBlank()) {
            selectedId = snapshot.myOrganizationId();
            return;
        }
        selectedId = snapshot.organizations().isEmpty() ? "" : snapshot.organizations().get(0).id();
    }

    private static List<OrganizationViewData.Summary> filtered(List<OrganizationViewData.Summary> organizations) {
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

    /** 紧凑布局下的组织详情页。 */
    static final class OrganizationDetailPage extends TerminalPage {
        private final String organizationId;

        OrganizationDetailPage(TerminalScreen terminal, String organizationId, String name) {
            super(terminal, name);
            this.organizationId = organizationId;
        }

        @Override
        protected UiNode<?> build() {
            UiNode<?> body = Dynamic.of(OrganizationClientCache::version, ignored -> {
                OrganizationViewData.Snapshot snapshot = OrganizationClientCache.get();
                OrganizationViewData.Summary summary = OrganizationViews.find(snapshot, organizationId);
                if (summary == null) {
                    return EmptyState.of(Icons.USERS, "这个组织已不存在", "它可能已被解散，返回名录查看最新列表");
                }
                return OrganizationViews.detail(terminal, snapshot, summary);
            });
            return Ui.stack(body, new OrganizationViews.ResultToast()).alignItems(Align.STRETCH)
                    .padding(Theme.Space.MD, Theme.Space.SM);
        }
    }
}
