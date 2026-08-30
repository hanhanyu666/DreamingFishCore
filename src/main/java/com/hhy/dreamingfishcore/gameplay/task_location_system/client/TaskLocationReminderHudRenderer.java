package com.hhy.dreamingfishcore.gameplay.task_location_system.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.components.UiPanelRenderer;
import com.hhy.dreamingfishcore.client.ui.loading.LoadingScreenUi;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.client.cache.NpcMessageClientCache;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryStageData;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryTaskData;
import com.hhy.dreamingfishcore.gameplay.task_system.TaskPlayerData;
import com.hhy.dreamingfishcore.gameplay.task_system.client.cache.TaskClientCache;
import com.hhy.dreamingfishcore.server.notice_system.client.cache.NoticeClientCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Small, independent reminder card above the existing region-status card.
 *
 * <p>The two cards intentionally have separate render paths and a fixed gap.  The
 * reminder card only appears when there is something useful to show, so it never
 * changes the layout of the region card itself.</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class TaskLocationReminderHudRenderer {
    private static final int RIGHT_MARGIN = 2;
    private static final int BOTTOM_MARGIN = 4;
    private static final int CARD_GAP = 3;
    private static final float TEXT_SCALE = 0.82f;
    private static final int MAX_PANEL_WIDTH = 196;
    private static final int MIN_PANEL_HEIGHT = 15;
    private static final int PANEL_RADIUS = 4;
    private static final int LEFT_PADDING = 5;
    private static final int RIGHT_PADDING = 6;
    private static final int VERTICAL_PADDING = 3;
    private static final int ACCENT_WIDTH = 2;
    private static final int ACCENT_GAP = 4;
    private static final int LINE_GAP = 1;

    private static final int PANEL_BACKGROUND = 0xC4141D28;
    private static final int TEXT_COLOR = 0xFFF0F4F8;
    private static final int NOTICE_ACCENT = 0xFFFFC857;
    private static final int NPC_ACCENT = 0xFF8CCEFF;
    private static final int TASK_ACCENT = 0xFF68D9AE;

    private static final long REFRESH_INTERVAL_NANOS = 250_000_000L;
    private static volatile ReminderSnapshot cachedSnapshot = ReminderSnapshot.EMPTY;
    private static long nextRefreshNanos;
    private static boolean refreshInitialized;

    private TaskLocationReminderHudRenderer() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.options.hideGui
                || minecraft.getDebugOverlay().showDebugScreen()
                || minecraft.screen != null) {
            return;
        }

        ReminderSnapshot snapshot = getSnapshot();
        if (snapshot.lines().isEmpty()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        graphics.drawManaged(() -> render(graphics, minecraft.font, snapshot));
    }

    private static void render(GuiGraphics graphics, Font font, ReminderSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int availableWidth = Math.max(48, screenWidth - RIGHT_MARGIN * 2);
        int panelMaxWidth = Math.min(MAX_PANEL_WIDTH, availableWidth);
        List<PreparedLine> lines = prepareLines(font, snapshot.lines(), panelMaxWidth);
        if (lines.isEmpty()) {
            return;
        }

        int contentWidth = lines.stream().mapToInt(PreparedLine::width).max().orElse(1);
        int chromeWidth = LEFT_PADDING + ACCENT_WIDTH + ACCENT_GAP + RIGHT_PADDING;
        int panelWidth = Math.min(panelMaxWidth,
                chromeWidth + Math.round(contentWidth * TEXT_SCALE));
        int rawTextHeight = lines.size() * font.lineHeight
                + Math.max(0, lines.size() - 1) * LINE_GAP;
        int scaledTextHeight = Math.max(1, Math.round(rawTextHeight * TEXT_SCALE));
        int panelHeight = Math.max(MIN_PANEL_HEIGHT,
                VERTICAL_PADDING * 2 + scaledTextHeight);
        int x = screenWidth - panelWidth - RIGHT_MARGIN;

        TaskLocationClientState.Snapshot regionSnapshot = TaskLocationClientState.get();
        int cardBottom = regionSnapshot == null
                ? screenHeight - BOTTOM_MARGIN
                : TaskLocationHudRenderer.panelTop(screenHeight, font) - CARD_GAP;
        int y = cardBottom - panelHeight;

        UiPanelRenderer.smoothRoundedRectBatched(graphics, x, y, panelWidth, panelHeight,
                PANEL_RADIUS, PANEL_BACKGROUND,
                UiPanelRenderer.withAlpha(snapshot.primaryAccent(), 118));
        UiPanelRenderer.roundedRect(graphics, x + LEFT_PADDING, y + VERTICAL_PADDING,
                ACCENT_WIDTH, panelHeight - VERTICAL_PADDING * 2, 1,
                UiPanelRenderer.withAlpha(snapshot.primaryAccent(), 224));

        int textX = x + LEFT_PADDING + ACCENT_WIDTH + ACCENT_GAP;
        int textY = y + (panelHeight - scaledTextHeight) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(textX, textY, 0.0f);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0f);
        int rawTextY = 0;
        for (PreparedLine line : lines) {
            graphics.drawString(font, line.prefix(), 0, rawTextY, line.accent(), false);
            graphics.drawString(font, line.detail(), font.width(line.prefix()), rawTextY,
                    TEXT_COLOR, false);
            rawTextY += font.lineHeight + LINE_GAP;
        }
        graphics.pose().popPose();
    }

    private static List<PreparedLine> prepareLines(Font font, List<ReminderLine> rawLines,
                                                   int panelMaxWidth) {
        int chromeWidth = LEFT_PADDING + ACCENT_WIDTH + ACCENT_GAP + RIGHT_PADDING;
        int maxRawTextWidth = Math.max(24,
                (int) ((panelMaxWidth - chromeWidth) / TEXT_SCALE));
        List<PreparedLine> prepared = new ArrayList<>(rawLines.size());
        for (ReminderLine line : rawLines) {
            String prefix = line.label() + " · ";
            int detailWidth = Math.max(1, maxRawTextWidth - font.width(prefix));
            String detail = LoadingScreenUi.trimToWidth(line.detail(), font, detailWidth);
            prepared.add(new PreparedLine(prefix, detail,
                    font.width(prefix) + font.width(detail), line.accent()));
        }
        return prepared;
    }

    private static ReminderSnapshot getSnapshot() {
        long now = System.nanoTime();
        if (!refreshInitialized || now >= nextRefreshNanos) {
            synchronized (TaskLocationReminderHudRenderer.class) {
                if (!refreshInitialized || now >= nextRefreshNanos) {
                    cachedSnapshot = collectSnapshot();
                    nextRefreshNanos = now + REFRESH_INTERVAL_NANOS;
                    refreshInitialized = true;
                }
            }
        }
        return cachedSnapshot;
    }

    private static ReminderSnapshot collectSnapshot() {
        List<ReminderLine> lines = new ArrayList<>(3);

        int unreadNotices = NoticeClientCache.getUnreadCount();
        if (NoticeClientCache.hasUnread()) {
            String detail = unreadNotices > 1
                    ? "有新的公告要查看（" + unreadNotices + "）"
                    : "有新的公告要查看";
            lines.add(new ReminderLine("公告", detail, NOTICE_ACCENT));
        }

        int unreadNpcMessages = NpcMessageClientCache.getUnreadCount();
        if (NpcMessageClientCache.isLoaded() && unreadNpcMessages > 0) {
            String detail = unreadNpcMessages > 1
                    ? "有新的消息（" + unreadNpcMessages + "）"
                    : "有新的消息";
            lines.add(new ReminderLine("NPC消息", detail, NPC_ACCENT));
        }

        String currentTask = findCurrentTask();
        if (!currentTask.isBlank()) {
            lines.add(new ReminderLine("当前任务", currentTask, TASK_ACCENT));
        }

        int primaryAccent = lines.isEmpty() ? NPC_ACCENT : lines.get(0).accent();
        return new ReminderSnapshot(List.copyOf(lines), primaryAccent);
    }

    /** Finds the first unfinished task, preferring personal tasks in the current story. */
    private static String findCurrentTask() {
        List<StoryStageData> stages = new ArrayList<>(TaskClientCache.getStoryStages().values());
        stages.removeIf(stage -> stage == null);
        stages.sort(Comparator.comparing(StoryStageData::isCurrentStage).reversed()
                .thenComparingInt(StoryStageData::getStageNumber));

        // Personal tasks are the player's immediate work even when the world task is locked.
        for (StoryStageData stage : stages) {
            String name = findUnfinishedStoryTask(stage, true);
            if (!name.isBlank()) {
                return name;
            }
        }
        for (StoryStageData stage : stages) {
            String name = findUnfinishedStoryTask(stage, false);
            if (!name.isBlank()) {
                return name;
            }
        }

        List<TaskPlayerData> playerTasks = new ArrayList<>(TaskClientCache.getPlayerTasks().values());
        playerTasks.removeIf(task -> task == null);
        playerTasks.sort(Comparator.comparingInt(TaskPlayerData::getTaskId));
        for (TaskPlayerData task : playerTasks) {
            if (!task.isClientPlayerFinished()) {
                return safeTaskName(task.getTaskName());
            }
        }
        return "";
    }

    private static String findUnfinishedStoryTask(StoryStageData stage, boolean personalOnly) {
        if (stage == null || stage.getTasks() == null) {
            return "";
        }
        for (StoryTaskData task : stage.getTasks()) {
            if (task == null || task.isClientPlayerFinished()
                    || (!task.isTaskState() && !task.isPersonalTask())
                    || task.isPersonalTask() != personalOnly) {
                continue;
            }
            String name = safeTaskName(task.getTaskName());
            if (!name.isBlank()) {
                return name;
            }
        }
        return "";
    }

    private static String safeTaskName(String name) {
        return name == null ? "" : name.replaceAll("\\s+", " ").trim();
    }

    private record ReminderLine(String label, String detail, int accent) {
    }

    private record PreparedLine(String prefix, String detail, int width, int accent) {
    }

    private record ReminderSnapshot(List<ReminderLine> lines, int primaryAccent) {
        private static final ReminderSnapshot EMPTY = new ReminderSnapshot(List.of(), NPC_ACCENT);
    }
}
