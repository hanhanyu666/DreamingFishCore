package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.cache.ClientCacheManager;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Card;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.EmptyState;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Responsive;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceEntry;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceViewData;
import com.hhy.dreamingfishcore.gameplay.guidance_system.client.cache.GuidanceClientCache;
import com.hhy.dreamingfishcore.gameplay.guidance_system.network.Packet_GuidanceSnapshotRequest;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryTaskData;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.playerdata_system.PlayerData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 故事进展：已开放阶段、阶段开场与任务清单。阶段由服主推进，任务可点开详情并追踪线索。 */
final class StoryPage extends TerminalPage {
    private String selectedStageId;

    StoryPage(TerminalScreen terminal) {
        super(terminal, "故事进展");
    }

    @Override
    protected void onShow() {
        DreamingFishCore_NetworkManager.sendToServer(new Packet_GuidanceSnapshotRequest());
    }

    private record Key(Map<Integer, StoryStageData> stages, String selected, List<GuidanceViewData> guidance,
                       boolean member, Responsive.Size size) {
    }

    @Override
    protected UiNode<?> build() {
        return Responsive.of(size -> Dynamic.of(() -> new Key(ClientCacheManager.getStoryStages(), selectedStageId,
                GuidanceClientCache.getEntries(), isMember(), size), this::content));
    }

    private static boolean isMember() {
        LocalPlayer player = Minecraft.getInstance().player;
        PlayerData data = player == null ? null : ClientCacheManager.getPlayerData(player.getUUID());
        return data != null && data.isZhuiguangMember();
    }

    private UiNode<?> content(Key key) {
        Map<Integer, StoryStageData> stages = TerminalData.visibleStages();
        if (stages.isEmpty()) {
            return EmptyState.of(Icons.BOOK, "故事阶段尚未同步", "请稍候再打开故事进展");
        }
        List<StoryStageData> ordered = new ArrayList<>(stages.values());
        ordered.removeIf(Objects::isNull);
        StoryStageData selected = ordered.stream().filter(stage -> Objects.equals(stage.getStageId(), selectedStageId))
                .findFirst().orElse(null);
        if (selected == null) {
            selected = ordered.stream().filter(StoryStageData::isCurrentStage).findFirst().orElse(ordered.get(0));
            selectedStageId = selected.getStageId();
        }
        boolean compact = key.size() == Responsive.Size.COMPACT;
        int currentNumber = TerminalData.currentVisibleStageNumber(stages);

        SideList<String> stageList = new SideList<>(compact, id -> selectedStageId = id);
        for (StoryStageData stage : ordered) {
            String subtitle = stage.isCurrentStage() ? "当前阶段" : stage.getStageNumber() < currentNumber ? "已结束" : "";
            stageList.item(stage.getStageId(), TerminalData.stageLabel(stage), subtitle, TerminalUi.MINT);
        }
        stageList.select(selectedStageId);

        UiNode<?> detail = stageDetail(selected, selected.getStageNumber() < currentNumber, key.member());
        if (compact) {
            return ScrollView.of(stageList, detail).gap(Theme.Space.MD)
                    .padding(Theme.Space.MD, Theme.Space.SM).edgeFade(ColorRole.SURFACE);
        }
        int totalTasks = TerminalData.totalTasks(stages);
        UiNode<?> sidebar = Ui.column(
                SideList.titled("任务目录 · 世界故事", stageList),
                Ui.spacer(),
                TerminalUi.card().surface(ColorRole.SURFACE_SUNKEN).flat().padding(Theme.Space.MD).gap(3.0F).add(
                        TerminalUi.sectionLabel("推进方式"),
                        Text.of("每位玩家按自己的经历推进").style(TextStyle.LABEL).color(TerminalUi.MINT),
                        Text.of("阶段由服主手动切换").style(TextStyle.CAPTION),
                        Text.of(stages.size() + " 个阶段 · " + totalTasks + " 项任务").style(TextStyle.CAPTION))
        ).gap(Theme.Space.SM).width(150.0F);
        UiNode<?> scroll = ScrollView.of(detail).grow(1.0F).basis(0.0F).edgeFade(ColorRole.SURFACE);
        return Ui.row(sidebar, scroll).alignItems(Align.STRETCH).gap(Theme.Space.LG)
                .padding(Theme.Space.LG, Theme.Space.MD, Theme.Space.LG, Theme.Space.SM);
    }

    private UiNode<?> stageDetail(StoryStageData stage, boolean historical, boolean member) {
        List<StoryTaskData> tasks = stage.getTasks() == null ? List.of() : stage.getTasks();
        String state = historical ? "已结束" : "阶段由服主推进";
        Box title = Ui.row(
                Ui.column(TerminalUi.sectionLabel("STAGE " + stage.getStageNumber()),
                        Text.of(TerminalData.safe(stage.getStageName(), "未命名阶段")).style(TextStyle.HEADLINE).singleLine())
                        .gap(2.0F).grow(1.0F).shrink(1.0F),
                TerminalUi.chip(state, historical ? TerminalUi.STEEL : TerminalUi.MINT)
        ).alignItems(Align.END).gap(Theme.Space.MD);

        String introTitle = stage.getStageNumber() == 1 ? "本阶段总结" : stage.isCurrentStage() ? "本阶段开场" : "阶段介绍";
        Card intro = TerminalUi.card().accent(TerminalUi.MINT).tint(0x106FDDA8).add(
                Ui.row(Icon.of(Icons.SPARKLE, 10.0F).color(TerminalUi.MINT),
                        Text.of(introTitle).style(TextStyle.LABEL_STRONG).color(TerminalUi.MINT)).gap(5.0F),
                Text.of(TerminalData.safe(stage.getStageDescription(), "新的剧情会随着公告与 NPC 对话逐步展开。"))
                        .style(TextStyle.BODY_SECONDARY.withLineGap(3.0F)));
        if (stage.getStageNumber() == 1) {
            intro.add(Text.of(member ? "当前组织身份：逐光会成员" : "当前组织身份：未加入逐光会")
                    .style(TextStyle.LABEL).color(TerminalUi.GOLD));
        }

        Box taskHeader = Ui.row(Text.of("任务清单").style(TextStyle.SUBTITLE).singleLine(), Ui.spacer(),
                Text.of(historical ? "历史记录 · 无需补做" : "点击卡片查看详情与追踪行动").style(TextStyle.CAPTION).singleLine().shrink(1.0F))
                .gap(Theme.Space.SM);

        Box list = Ui.column().gap(Theme.Space.SM);
        if (tasks.isEmpty()) {
            list.add(EmptyState.of(Icons.LIST, "当前阶段暂无已解锁剧情任务", null));
        }
        List<GuidanceViewData> guidance = GuidanceClientCache.getEntries();
        int index = 0;
        for (StoryTaskData task : tasks) {
            if (task == null) {
                continue;
            }
            list.add(taskCard(stage, task, TerminalData.guidanceFor(task, guidance), historical)
                    .enter(EnterEffect.FADE_UP.delayed(80.0F + index++ * 40.0F)));
        }
        return Ui.column(title.enter(EnterEffect.FADE_UP), intro.enter(EnterEffect.FADE_UP.delayed(40.0F)), taskHeader, list)
                .gap(Theme.Space.MD);
    }

    static int toneColor(TerminalData.TaskTone tone) {
        return switch (tone) {
            case FAILED -> 0xFFE05B62;
            case DONE -> TerminalUi.GREEN;
            case MUTED -> 0xFF7F8E9B;
            case ACTIVE -> TerminalUi.CYAN;
        };
    }

    private Card taskCard(StoryStageData stage, StoryTaskData task, GuidanceViewData guidance, boolean historical) {
        int color = toneColor(TerminalData.taskTone(task, historical));
        String status = TerminalData.taskStatusLabel(task, historical);
        String content = guidance != null && !guidance.content().isBlank() ? guidance.content() : task.getTaskContent();
        Card card = TerminalUi.card().accent(color).gap(Theme.Space.SM)
                .onClick(() -> terminal.push(new StoryTaskPage(terminal, stage.getStageId(), task.getTaskKey())));
        card.add(
                Ui.row(TerminalUi.chip(status, color),
                        Text.of(TerminalData.safe(task.getTaskName(), "未命名任务")).style(TextStyle.SUBTITLE).singleLine()
                                .grow(1.0F).shrink(1.0F),
                        Icon.of(Icons.CHEVRON_RIGHT, 10.0F)).gap(Theme.Space.MD),
                Text.of(TerminalData.safe(content, "")).style(TextStyle.BODY_SECONDARY).maxLines(2));
        if (guidance != null) {
            boolean active = guidance.status() == GuidanceEntry.Status.ACTIVE;
            String clue = guidance.title() + (guidance.hasLocation() && !guidance.locationLabel().isBlank()
                    ? " · " + guidance.locationLabel() : "");
            Box row = Ui.row(Icon.of(Icons.TARGET, 10.0F).color(active ? TerminalUi.MINT : 0xFF7F8E9B),
                    Text.of(clue).style(TextStyle.LABEL).color(active ? TerminalUi.MINT : 0xFF7F8E9B).singleLine()
                            .grow(1.0F).shrink(1.0F)).gap(6.0F);
            if (task.isPersonalTask() && task.isClientPlayerFinished()) {
                row.add(Text.of("已完成").style(TextStyle.CAPTION).singleLine());
            }
            card.add(Ui.row(row.grow(1.0F)).padding(8.0F, 4.0F).radius(Theme.Radius.MD)
                    .background(UiColor.withAlpha(active ? TerminalUi.MINT : 0xFF7F8E9B, 0.07F)));
        }
        return card;
    }
}
