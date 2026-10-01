package com.hhy.dreamingfishcore.gameplay.task_location_system.client;

import com.hhy.dreamingfishcore.client.ui.framework.hud.HudFrame;
import com.hhy.dreamingfishcore.client.ui.framework.hud.HudLayer;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import com.hhy.dreamingfishcore.client.input.KeybindHandler;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceEntry;
import com.hhy.dreamingfishcore.gameplay.guidance_system.GuidanceViewData;
import com.hhy.dreamingfishcore.gameplay.guidance_system.client.cache.GuidanceClientCache;
import com.hhy.dreamingfishcore.gameplay.npc_message_system.client.cache.NpcMessageClientCache;
import com.hhy.dreamingfishcore.gameplay.task_system.TaskPlayerData;
import com.hhy.dreamingfishcore.gameplay.task_system.client.cache.TaskClientCache;
import com.hhy.dreamingfishcore.server.notice_system.client.cache.NoticeClientCache;
import com.hhy.dreamingfishcore.server.notice_system.client.cache.NoticeClientCache.UnreadNotice;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.neoforged.neoforge.client.settings.KeyModifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Small, independent reminder card above the existing region-status card.
 *
 * <p>The reminder card only appears when there is something useful to show and
 * keeps a fixed gap above the region card. Both cards feed the shared gameplay
 * HUD batch without changing each other's layout.</p>
 */
public final class TaskLocationReminderHudRenderer {
    private static final int RIGHT_MARGIN = 2;
    private static final int BOTTOM_MARGIN = 4;
    private static final int CARD_GAP = 3;
    private static final float TEXT_SCALE = 0.82f;
    private static final float GUIDANCE_TEXT_SCALE = 0.72f;
    private static final int GUIDANCE_MAX_WIDTH = 160;
    /** Wider than the old count-only card so announcement titles are visible at a glance. */
    private static final int MAX_PANEL_WIDTH = 360;
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

    private static final int REFRESH_INTERVAL_TICKS = 5;

    /** 右下角提醒卡片与剧情追踪卡片在统一 HUD 画布中的区域，画在保护区卡片之后。 */
    public static final HudLayer LAYER = new HudLayer() {
        @Override
        public int order() {
            return 30;
        }

        @Override
        public boolean visible(Minecraft minecraft) {
            return shouldRenderHud(minecraft);
        }

        @Override
        public void paint(HudFrame frame) {
            render(frame.canvas(), frame.font(), getSnapshot());
        }
    };
    private static volatile ReminderSnapshot cachedSnapshot = ReminderSnapshot.EMPTY;
    private static int lastRefreshTick = Integer.MIN_VALUE;
    private static Object lastRefreshLevel;
    private static boolean refreshInitialized;
    private static ReminderSnapshot preparedSnapshot;
    private static Font preparedFont;
    private static int preparedPanelMaxWidth = -1;
    private static PreparedLayout cachedPreparedLayout = PreparedLayout.EMPTY;
    private static CachedGuidanceLayout collapsedGuidanceLayout;
    private static CachedGuidanceLayout expandedGuidanceLayout;
    private static InputConstants.Key cachedGuidanceKey;
    private static KeyModifier cachedGuidanceKeyModifier;
    private static Language cachedGuidanceLanguage;
    private static String cachedGuidanceKeyHint = "";

    private TaskLocationReminderHudRenderer() {
    }

    public static void invalidateLayoutCache() {
        preparedSnapshot = null;
        preparedFont = null;
        preparedPanelMaxWidth = -1;
        cachedPreparedLayout = PreparedLayout.EMPTY;
        collapsedGuidanceLayout = null;
        expandedGuidanceLayout = null;
        cachedGuidanceKey = null;
        cachedGuidanceKeyModifier = null;
        cachedGuidanceLanguage = null;
        cachedGuidanceKeyHint = "";
    }

    public static boolean shouldRenderHud(Minecraft minecraft) {
        return minecraft.player != null && minecraft.level != null
                && !minecraft.options.hideGui
                && !minecraft.getDebugOverlay().showDebugScreen()
                && minecraft.screen == null
                && (!getSnapshot().lines().isEmpty() || !GuidanceClientCache.getActiveEntries().isEmpty());
    }

    private static void render(UiCanvas canvas, Font font, ReminderSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int availableWidth = Math.max(48, screenWidth - RIGHT_MARGIN * 2);
        int panelMaxWidth = Math.min(MAX_PANEL_WIDTH, availableWidth);
        PreparedLayout layout = getPreparedLayout(font, snapshot, panelMaxWidth);
        List<PreparedLine> lines = layout.lines();

        int panelWidth = layout.panelWidth();
        int scaledTextHeight = layout.scaledTextHeight();
        int panelHeight = layout.panelHeight();
        int x = screenWidth - panelWidth - RIGHT_MARGIN;

        TaskLocationClientState.Snapshot regionSnapshot = TaskLocationClientState.get();
        int cardBottom = regionSnapshot == null
                ? screenHeight - BOTTOM_MARGIN
                : TaskLocationHudRenderer.panelTop(screenHeight, font) - CARD_GAP;
        int y = cardBottom - panelHeight;

        renderGuidanceStack(canvas, minecraft, panelMaxWidth, y - (lines.isEmpty() ? 0 : CARD_GAP));
        if (lines.isEmpty()) {
            return;
        }

        canvas.shape(x, y, panelWidth, panelHeight).radius(PANEL_RADIUS).fill(PANEL_BACKGROUND)
                .border(1.0F, UiColor.withAlpha(snapshot.primaryAccent(), 118)).draw();
        canvas.shape(x + LEFT_PADDING, y + VERTICAL_PADDING, ACCENT_WIDTH, panelHeight - VERTICAL_PADDING * 2)
                .radius(1.0F).fill(UiColor.withAlpha(snapshot.primaryAccent(), 224)).draw();

        float textX = x + LEFT_PADDING + ACCENT_WIDTH + ACCENT_GAP;
        float textY = y + (panelHeight - scaledTextHeight) / 2;
        float lineY = textY;
        for (PreparedLine line : lines) {
            canvas.text(line.prefix(), textX, lineY, line.accent(), TEXT_SCALE, false);
            canvas.text(line.detail(), textX + line.detailX() * TEXT_SCALE, lineY, TEXT_COLOR, TEXT_SCALE, false);
            lineY += (font.lineHeight + LINE_GAP) * TEXT_SCALE;
        }
    }

    /** 按可见文字收缩卡片，正文最多三行；按住切换键时才展开其他任务。 */
    private static void renderGuidanceStack(UiCanvas canvas, Minecraft minecraft, int maxWidth, int bottom) {
        List<GuidanceViewData> active = GuidanceClientCache.getActiveEntries();
        GuidanceViewData selected = GuidanceClientCache.getTrackedEntry();
        if (selected == null || active.isEmpty()) {
            return;
        }
        Font font = minecraft.font;
        int widthLimit = Math.min(GUIDANCE_MAX_WIDTH, Math.min(maxWidth,
                Math.max(48, minecraft.getWindow().getGuiScaledWidth() / 3)));
        boolean expanded = active.size() > 1 && KeybindHandler.GUIDANCE_SCROLL_KEY.isDown();
        PreparedGuidanceLayout layout = getPreparedGuidanceLayout(font, active, selected, widthLimit,
                expanded, expanded ? getGuidanceKeyHint() : "");
        int compactCount = layout.compactCards().size();
        int width = layout.width();
        int x = minecraft.getWindow().getGuiScaledWidth() - width - RIGHT_MARGIN;
        int rawLineHeight = layout.rawLineHeight();
        int mainHeight = layout.mainHeight();
        int compactHeight = layout.compactHeight();
        // Only placement depends on the space left by notices/the region card.
        // Moving this stack must not trigger text measurement or re-wrapping.
        while (compactCount > 0 && bottom - mainHeight - compactCount * (compactHeight + CARD_GAP) < 4) {
            compactCount--;
        }
        int mainY = Math.max(4, bottom - mainHeight - compactCount * (compactHeight + CARD_GAP));
        canvas.shape(x, mainY, width, mainHeight).radius(PANEL_RADIUS).fill(0x98141D28)
                .border(1.0F, UiColor.withAlpha(TASK_ACCENT, 110)).draw();
        float textX = x + 5.0F;
        float textY = mainY + 4.0F;
        float step = rawLineHeight * GUIDANCE_TEXT_SCALE;
        canvas.text(layout.title(), textX, textY, TASK_ACCENT, GUIDANCE_TEXT_SCALE, false);
        for (int line = 0; line < layout.description().size(); line++) {
            canvas.text(layout.description().get(line), textX, textY + (line + 1) * step, TEXT_COLOR, GUIDANCE_TEXT_SCALE, false);
        }
        if (expanded) {
            canvas.text(layout.hint(), textX, textY + (layout.description().size() + 1) * step, 0xFF9BB8C9,
                    GUIDANCE_TEXT_SCALE, false);
        }
        for (int offset = 1; offset <= compactCount; offset++) {
            PreparedGuidanceCard other = layout.compactCards().get(offset - 1);
            int y = mainY + mainHeight + CARD_GAP + (offset - 1) * (compactHeight + CARD_GAP);
            int otherWidth = other.width();
            int otherX = minecraft.getWindow().getGuiScaledWidth() - otherWidth - RIGHT_MARGIN;
            canvas.shape(otherX, y, otherWidth, compactHeight).radius(PANEL_RADIUS).fill(PANEL_BACKGROUND)
                    .border(1.0F, 0x66445B6A).draw();
            canvas.text(other.title(), otherX + 5.0F, y + 3.0F, 0xFFB4C5D2, GUIDANCE_TEXT_SCALE, false);
        }
    }

    private static String getGuidanceKeyHint() {
        InputConstants.Key key = KeybindHandler.GUIDANCE_SCROLL_KEY.getKey();
        KeyModifier modifier = KeybindHandler.GUIDANCE_SCROLL_KEY.getKeyModifier();
        Language language = Language.getInstance();
        if (cachedGuidanceKey != key || cachedGuidanceKeyModifier != modifier
                || cachedGuidanceLanguage != language) {
            cachedGuidanceKeyHint = KeybindHandler.GUIDANCE_SCROLL_KEY.getTranslatedKeyMessage().getString()
                    + " + 滚轮切换";
            cachedGuidanceKey = key;
            cachedGuidanceKeyModifier = modifier;
            cachedGuidanceLanguage = language;
        }
        return cachedGuidanceKeyHint;
    }

    /** Immutable guidance snapshots make the unchanged-frame check allocation-free. */
    static PreparedGuidanceLayout getPreparedGuidanceLayout(Font font, List<GuidanceViewData> active,
                                                            GuidanceViewData selected, int widthLimit,
                                                            boolean expanded, String keyHint) {
        CachedGuidanceLayout cached = expanded ? expandedGuidanceLayout : collapsedGuidanceLayout;
        if (cached != null && cached.font() == font && cached.active() == active
                && cached.selected() == selected && cached.widthLimit() == widthLimit
                && cached.keyHint().equals(keyHint)) {
            return cached.layout();
        }

        int textWidth = Math.max(24, (int) ((widthLimit - 10) / GUIDANCE_TEXT_SCALE));
        int selectedIndex = Math.max(0, active.indexOf(selected));
        int compactCount = expanded ? Math.min(2, active.size() - 1) : 0;
        String count = active.size() > 1 ? " " + (selectedIndex + 1) + "/" + active.size() : "";
        String title = compactGuidanceText(font, "追踪 · " + selected.title(), textWidth - font.width(count)) + count;
        String content = safeTaskName(selected.content());
        List<String> wrapped = content.isBlank() ? List.of() : wrapText(content, font, textWidth);
        List<String> description = new ArrayList<>(wrapped.subList(0, Math.min(3, wrapped.size())));
        if (wrapped.size() > description.size()) {
            int last = description.size() - 1;
            description.set(last, compactGuidanceText(font, description.get(last) + "…", textWidth));
        }
        String hint = expanded ? compactGuidanceText(font, keyHint, textWidth) : "";
        int contentWidth = Math.max(font.width(title), font.width(hint));
        for (String line : description) contentWidth = Math.max(contentWidth, font.width(line));
        int width = Math.min(widthLimit, (int) Math.ceil(contentWidth * GUIDANCE_TEXT_SCALE) + 10);
        int rawLineHeight = font.lineHeight + 1;
        int mainHeight = (int) Math.ceil((1 + description.size() + (expanded ? 1 : 0))
                * rawLineHeight * GUIDANCE_TEXT_SCALE) + 8;
        int compactHeight = (int) Math.ceil(rawLineHeight * GUIDANCE_TEXT_SCALE) + 6;
        List<PreparedGuidanceCard> compactCards = new ArrayList<>(compactCount);
        for (int offset = 1; offset <= compactCount; offset++) {
            GuidanceViewData other = active.get((selectedIndex + offset) % active.size());
            String otherTitle = compactGuidanceText(font, other.title(), textWidth);
            int otherWidth = Math.min(widthLimit, (int) Math.ceil(font.width(otherTitle) * GUIDANCE_TEXT_SCALE) + 10);
            compactCards.add(new PreparedGuidanceCard(otherTitle, otherWidth));
        }

        PreparedGuidanceLayout layout = new PreparedGuidanceLayout(title, List.copyOf(description), hint,
                width, rawLineHeight, mainHeight, compactHeight, List.copyOf(compactCards));
        CachedGuidanceLayout replacement = new CachedGuidanceLayout(font, active, selected, widthLimit, keyHint, layout);
        if (expanded) {
            expandedGuidanceLayout = replacement;
        } else {
            collapsedGuidanceLayout = replacement;
        }
        return layout;
    }

    private static String compactGuidanceText(Font font, String text, int maxWidth) {
        String line = safeTaskName(text);
        if (font.width(line) <= maxWidth) return line;
        return font.plainSubstrByWidth(line, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    private static PreparedLayout getPreparedLayout(Font font, ReminderSnapshot snapshot,
                                                     int panelMaxWidth) {
        if (preparedSnapshot == snapshot
                && preparedFont == font
                && preparedPanelMaxWidth == panelMaxWidth) {
            return cachedPreparedLayout;
        }

        List<PreparedLine> lines = List.copyOf(prepareLines(font, snapshot.lines(), panelMaxWidth));
        int contentWidth = lines.stream().mapToInt(PreparedLine::width).max().orElse(1);
        int chromeWidth = LEFT_PADDING + ACCENT_WIDTH + ACCENT_GAP + RIGHT_PADDING;
        int panelWidth = Math.min(panelMaxWidth,
                chromeWidth + Math.round(contentWidth * TEXT_SCALE));
        int rawTextHeight = lines.size() * font.lineHeight
                + Math.max(0, lines.size() - 1) * LINE_GAP;
        int scaledTextHeight = lines.isEmpty()
                ? 0
                : Math.max(1, Math.round(rawTextHeight * TEXT_SCALE));
        int panelHeight = lines.isEmpty()
                ? 0
                : Math.max(MIN_PANEL_HEIGHT, VERTICAL_PADDING * 2 + scaledTextHeight);

        cachedPreparedLayout = new PreparedLayout(lines, panelWidth, scaledTextHeight, panelHeight);
        preparedSnapshot = snapshot;
        preparedFont = font;
        preparedPanelMaxWidth = panelMaxWidth;
        return cachedPreparedLayout;
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
            List<String> wrappedDetails = wrapText(line.detail(), font, detailWidth);
            int prefixWidth = font.width(prefix);
            for (int index = 0; index < wrappedDetails.size(); index++) {
                String detail = wrappedDetails.get(index);
                String shownPrefix = index == 0 ? prefix : "";
                // Continuation lines align with the detail text. The label is
                // intentionally shown only once for each announcement.
                prepared.add(new PreparedLine(shownPrefix, detail, prefixWidth,
                        prefixWidth + font.width(detail), line.accent()));
            }
        }
        return prepared;
    }

    /** Wraps a title without an ellipsis so every visible character remains in the HUD. */
    private static List<String> wrapText(String text, Font font, int maxWidth) {
        String normalized = stripFormattingCodes(text);
        if (normalized.isBlank()) {
            return List.of("未命名公告");
        }

        List<String> result = new ArrayList<>();
        String[] paragraphs = normalized.split("\\R", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                result.add("");
                continue;
            }
            String remaining = paragraph;
            while (!remaining.isEmpty()) {
                String part = font.plainSubstrByWidth(remaining, Math.max(8, maxWidth));
                if (part == null || part.isEmpty()) {
                    int firstCodePoint = remaining.codePointAt(0);
                    part = new String(Character.toChars(firstCodePoint));
                }
                result.add(part);
                if (part.length() >= remaining.length()) {
                    break;
                }
                remaining = remaining.substring(part.length());
            }
        }
        return result.isEmpty() ? List.of("未命名公告") : result;
    }

    private static String stripFormattingCodes(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\u00A7' && index + 1 < text.length()) {
                index++;
                continue;
            }
            result.append(current);
        }
        return result.toString();
    }

    private static ReminderSnapshot getSnapshot() {
        Minecraft minecraft = Minecraft.getInstance();
        int tick = minecraft.player == null ? 0 : minecraft.player.tickCount;
        Object level = minecraft.level;
        boolean refreshDue = !refreshInitialized
                || lastRefreshLevel != level
                || tick < lastRefreshTick
                || tick - lastRefreshTick >= REFRESH_INTERVAL_TICKS;
        if (refreshDue) {
            synchronized (TaskLocationReminderHudRenderer.class) {
                refreshDue = !refreshInitialized
                        || lastRefreshLevel != level
                        || tick < lastRefreshTick
                        || tick - lastRefreshTick >= REFRESH_INTERVAL_TICKS;
                if (refreshDue) {
                    ReminderSnapshot refreshedSnapshot = collectSnapshot();
                    // Preserve object identity while the reminder content is
                    // unchanged so the wrapped/layout cache remains hot.
                    if (!refreshedSnapshot.equals(cachedSnapshot)) {
                        cachedSnapshot = refreshedSnapshot;
                    }
                    lastRefreshTick = tick;
                    lastRefreshLevel = level;
                    refreshInitialized = true;
                }
            }
        }
        return cachedSnapshot;
    }

    private static ReminderSnapshot collectSnapshot() {
        List<UnreadNotice> unreadNotices = NoticeClientCache.getUnreadNotices();
        List<ReminderLine> lines = new ArrayList<>(unreadNotices.size() + 2);

        for (UnreadNotice notice : unreadNotices) {
            // 每条未读公告单独占一组文本；长标题由 prepareLines 换行，
            // 不再用“有几条公告”概括掉真正能吸引玩家的标题。
            lines.add(new ReminderLine("公告", notice.title(), NOTICE_ACCENT));
        }
        // A very early login can receive only the boolean check hint before the
        // full notice snapshot. Keep a visible fallback instead of dropping the
        // reminder for that short window.
        if (unreadNotices.isEmpty() && NoticeClientCache.hasUnread()) {
            lines.add(new ReminderLine("公告", "有新的公告要查看", NOTICE_ACCENT));
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

    /**
     * 主线目标只读取服务端投影出的活动引导。
     *
     * <p>客户端不再扫描故事阶段/任务卡来猜测“当前任务”。阶段脚本每次只保留
     * 一个活动主线引导，因此这里不会把历史阶段的建设任务重新选出来。普通任务
     * 仍然使用通用任务缓存作为兜底。</p>
     */
    private static String findCurrentTask() {
        if (!GuidanceClientCache.getActiveEntries().isEmpty()) {
            return ""; // 剧情行动由上方独立卡片堆叠显示。
        }

        List<TaskPlayerData> playerTasks = new ArrayList<>(TaskClientCache.getPlayerTasks().values());
        playerTasks.removeIf(task -> task == null);
        playerTasks.sort(Comparator.comparingInt(TaskPlayerData::getTaskId));
        for (TaskPlayerData task : playerTasks) {
            // 故事任务不再从通用任务缓存投影；主线目标已经由活动引导返回。
            if (!TaskClientCache.isStoryTaskId(task.getTaskId())
                    && !task.isClientPlayerFinished()) {
                return safeTaskName(task.getTaskName());
            }
        }
        return "";
    }

    private static String safeTaskName(String name) {
        return name == null ? "" : name.replaceAll("\\s+", " ").trim();
    }

    private record ReminderLine(String label, String detail, int accent) {
    }

    private record PreparedLine(String prefix, String detail, int detailX, int width, int accent) {
    }

    private record PreparedLayout(List<PreparedLine> lines, int panelWidth,
                                  int scaledTextHeight, int panelHeight) {
        private static final PreparedLayout EMPTY = new PreparedLayout(List.of(), 0, 0, 0);
    }

    record PreparedGuidanceCard(String title, int width) {
    }

    record PreparedGuidanceLayout(String title, List<String> description, String hint, int width,
                                  int rawLineHeight, int mainHeight, int compactHeight,
                                  List<PreparedGuidanceCard> compactCards) {
    }

    private record CachedGuidanceLayout(Font font, List<GuidanceViewData> active, GuidanceViewData selected,
                                        int widthLimit, String keyHint, PreparedGuidanceLayout layout) {
    }

    private record ReminderSnapshot(List<ReminderLine> lines, int primaryAccent) {
        private static final ReminderSnapshot EMPTY = new ReminderSnapshot(List.of(), NPC_ACCENT);
    }
}
