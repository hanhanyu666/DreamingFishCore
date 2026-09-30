package com.hhy.dreamingfishcore.server.server_ui_system.client.terminal;

import com.hhy.dreamingfishcore.client.cache.ClientCacheManager;
import com.hhy.dreamingfishcore.client.ui.framework.node.Align;
import com.hhy.dreamingfishcore.client.ui.framework.node.Box;
import com.hhy.dreamingfishcore.client.ui.framework.node.EnterEffect;
import com.hhy.dreamingfishcore.client.ui.framework.node.UiNode;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextStyle;
import com.hhy.dreamingfishcore.client.ui.framework.theme.ColorRole;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Button;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Dynamic;
import com.hhy.dreamingfishcore.client.ui.framework.widget.EmptyState;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icon;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Icons;
import com.hhy.dreamingfishcore.client.ui.framework.widget.ScrollView;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Text;
import com.hhy.dreamingfishcore.client.ui.framework.widget.Ui;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceEntry;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceViewData;
import com.hhy.dreamingfishcore.gameplay.guidance_system.client.cache.GuidanceClientCache;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryTaskData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 故事任务详情：任务说明、个人线索、目标地点与追踪。 */
final class StoryTaskPage extends TerminalPage {
    private final String stageId;
    private final String taskKey;

    StoryTaskPage(TerminalScreen terminal, String stageId, String taskKey) {
        super(terminal, "任务详情");
        this.stageId = stageId;
        this.taskKey = taskKey;
    }

    private record Key(Map<Integer, StoryStageData> stages, List<GuidanceViewData> guidance, GuidanceViewData tracked) {
    }

    @Override
    protected UiNode<?> build() {
        return Dynamic.of(() -> new Key(ClientCacheManager.getStoryStages(), GuidanceClientCache.getEntries(),
                GuidanceClientCache.getTrackedEntry()), key -> content());
    }

    private UiNode<?> content() {
        Map<Integer, StoryStageData> stages = TerminalData.visibleStages();
        StoryStageData stage = stages.values().stream().filter(Objects::nonNull)
                .filter(candidate -> Objects.equals(candidate.getStageId(), stageId)).findFirst().orElse(null);
        StoryTaskData task = stage == null || stage.getTasks() == null ? null : stage.getTasks().stream()
                .filter(candidate -> candidate != null && taskKey.equals(candidate.getTaskKey())).findFirst().orElse(null);
        if (task == null) {
            return EmptyState.of(Icons.BOOK, "任务不存在", "返回故事列表后重新选择");
        }
        boolean historical = stage.getStageNumber() < TerminalData.currentVisibleStageNumber(stages);
        int color = StoryPage.toneColor(TerminalData.taskTone(task, historical));
        GuidanceViewData guidance = TerminalData.guidanceFor(task, GuidanceClientCache.getEntries());

        Box actions = Ui.row().gap(Theme.Space.SM);
        if (!historical && task.isActionRequired() && guidance != null && guidance.status() == GuidanceEntry.Status.ACTIVE) {
            GuidanceViewData tracked = GuidanceClientCache.getTrackedEntry();
            boolean isTracked = tracked != null && tracked.definitionId().equals(guidance.definitionId());
            Button track = Button.of(isTracked ? "正在追踪" : "追踪行动").leadingIcon(Icons.TARGET).small();
            if (isTracked) {
                track.tonal().accent(ColorRole.SUCCESS);
            } else {
                track.filled();
            }
            String definition = guidance.definitionId();
            track.onClick(() -> GuidanceClientCache.track(definition));
            actions.add(track);
        }

        UiNode<?> header = Ui.column(
                Ui.row(TerminalUi.chip(TerminalData.taskStatusLabel(task, historical), color), Ui.spacer(), actions)
                        .alignItems(Align.CENTER).gap(Theme.Space.SM),
                Text.of(TerminalData.safe(task.getTaskName(), "未命名任务")).style(TextStyle.HEADLINE).maxLines(2),
                Text.of(TerminalData.stageLabel(stage)).style(TextStyle.CAPTION).singleLine()
        ).gap(Theme.Space.SM).enter(EnterEffect.FADE_UP);

        List<UiNode<?>> sections = new ArrayList<>();
        if (task.getTaskContent() != null && !task.getTaskContent().isBlank()) {
            sections.add(section(Icons.LIST, "任务说明", task.getTaskContent(), color));
        }
        if (guidance != null) {
            String prefix = historical || task.isArchived() || task.isWaived() ? "历史引导" : "个人线索";
            StringBuilder text = new StringBuilder();
            if (!guidance.title().isBlank()) {
                text.append(guidance.title());
            }
            if (!guidance.content().isBlank()) {
                text.append(text.isEmpty() ? "" : "\n").append(guidance.content());
            }
            sections.add(section(Icons.TARGET, prefix, text.toString(), TerminalUi.MINT));
            if (guidance.hasLocation() && !guidance.locationLabel().isBlank()) {
                sections.add(section(Icons.PIN, "目标地点", guidance.locationLabel(), TerminalUi.GOLD));
            }
        }
        String personal;
        if (task.isPersonalTask()) {
            personal = task.isClientPlayerFinished() ? "个人状态：亲自完成"
                    : task.isWaived() ? "个人状态：通过前情接入后续，无需补做"
                    : historical || task.isArchived() ? "个人状态：未亲自完成，已归档，无需补做"
                    : "个人状态：进行中";
        } else {
            personal = task.getFinishedPlayerCount() > 0 ? "参与人数：" + task.getFinishedPlayerCount() : "";
        }
        if (!personal.isBlank()) {
            sections.add(Ui.row(Icon.of(Icons.USER, 9.0F).color(ColorRole.TEXT_MUTED),
                    Text.of(personal).style(TextStyle.LABEL).singleLine()).gap(5.0F));
        }
        if (sections.isEmpty()) {
            sections.add(Text.of("这项任务暂时没有更多说明。").style(TextStyle.BODY_SECONDARY));
        }
        Box body = Ui.column().gap(Theme.Space.MD);
        int index = 0;
        for (UiNode<?> node : sections) {
            body.add(node.enter(EnterEffect.FADE_UP.delayed(50.0F + index++ * 40.0F)));
        }
        return ScrollView.of(header, body).gap(Theme.Space.LG)
                .padding(Theme.Space.XL, Theme.Space.MD).edgeFade(ColorRole.SURFACE);
    }

    private static UiNode<?> section(Icons icon, String label, String text, int accent) {
        return TerminalUi.card().surface(ColorRole.SURFACE_SUNKEN).accent(accent).add(
                Ui.row(Icon.of(icon, 10.0F).color(accent), Text.of(label).style(TextStyle.LABEL_STRONG).color(accent)).gap(5.0F),
                Text.of(text).style(TextStyle.BODY.withLineGap(4.0F)));
    }
}
