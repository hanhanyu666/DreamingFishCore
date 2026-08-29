package com.hhy.dreamingfishcore.client.ui.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.components.UiPanelRenderer;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.storage.LevelResource;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Client-owned chat renderer and history store.
 *
 * <p>The vanilla chat component still receives non-player/system messages so other mods keep working, but its
 * renderer is replaced by this manager. Player chat arrives through a small structured DreamingFishCore payload,
 * which lets the UI keep identity metadata on its own row instead of forcing rank/title/name into the message text.</p>
 */
public final class ImmersiveChatManager {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_MESSAGES = 800;
    private static final int HISTORY_LOAD_LIMIT = 450;
    private static final int HISTORY_FILE_DAYS = 5;
    private static final int OUTER_PADDING = 8;
    private static final int PLAYER_HEAD_SIZE = 16;
    private static final int PLAYER_HEAD_GAP = 5;
    private static final int HEADER_HEIGHT = 10;
    private static final int BODY_LINE_HEIGHT = 10;
    private static final int ENTRY_GAP = 4;
    private static final int SYSTEM_LINE_HEIGHT = 10;
    private static final float CHAT_TEXT_SCALE = 0.90f;
    private static final int UNFOCUSED_SIDE_ANIMATION_MS = 220;
    private static final int UNFOCUSED_LIFETIME_MS = 13_000;
    private static final int MIN_ALPHA = 4;
    private static final int DRAG_HANDLE_HEIGHT = 8;
    private static final int RESIZE_HANDLE_SIZE = 11;
    private static final int BOTTOM_RESIZE_HEIGHT = 6;
    private static final int FOCUSED_PANEL_RADIUS = 6;
    private static final int UNFOCUSED_PANEL_RADIUS = 5;
    private static final int INPUT_PANEL_RADIUS = 5;
    private static final int INPUT_BOTTOM_MARGIN = 5;
    private static final int INPUT_PANEL_HEIGHT = 18;
    private static final int COMMAND_SUGGESTION_GAP = 2;
    private static final int SCROLLBAR_HIT_PADDING = 3;
    private static final long CLEAR_CONFIRMATION_MS = 3_000L;

    private static final int PANEL_BG = 0xA025282A;
    private static final int MENTION_HIGHLIGHT_BG = 0xFF596166;
    private static final int MENTION_LABEL_COLOR = 0xFFFFD54A;
    private static final String MENTION_LABEL = "（@了我）";
    private static final String REPEAT_LABEL_PREFIX = " (重复 ";
    private static final String REPEAT_LABEL_SUFFIX = ")";
    private static final String CLEAR_CONFIRMATION_TEXT = "再按两下右键才能清空当前聊天记录";
    private static final String CLEAR_FINAL_CONFIRMATION_TEXT = "再按一下右键确认清空当前聊天记录";

    // One shared deep-gray chat surface. Individual entries do not draw their own cards.
    private static final int MESSAGE_BG = 0xFF25282A;
    private static final int BODY_COLOR = 0xFFE8E8E4;
    private static final int NAME_COLOR = 0xFFF1F1EE;
    private static final int MUTED_COLOR = 0xFFA7AAA7;

    private static final List<ChatEntry> MESSAGES = new ArrayList<>();
    /**
     * Persist chat history away from the render thread.  A single daemon
     * writer preserves arrival order; clear operations enqueue a barrier on the
     * same executor so pending appends cannot recreate a deleted session file.
     */
    private static final Object HISTORY_IO_LOCK = new Object();
    private static final ExecutorService HISTORY_WRITER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "DreamingFish-ChatHistory");
        thread.setDaemon(true);
        return thread;
    });
    // Layout is independent of the current animation time.  Rebuilding every
    // frame used to split up to 450 messages and allocate a component/list for
    // each row, even when no message or window size had changed.
    private static List<EntryLayout> CACHED_LAYOUTS = List.of();
    private static long MESSAGE_LAYOUT_REVISION;
    private static long CACHED_LAYOUT_REVISION = Long.MIN_VALUE;
    private static int CACHED_LAYOUT_WIDTH = -1;
    @Nullable
    private static Font CACHED_LAYOUT_FONT;
    private static final List<HitLine> HIT_LINES = new ArrayList<>();
    private static final List<HitAvatar> HIT_AVATARS = new ArrayList<>();
    private static final List<HitPlayerMessage> HIT_PLAYER_MESSAGES = new ArrayList<>();
    private static String activeSessionKey = "";
    private static String activeSafeSessionKey = "";
    private static int scrollOffsetPx = 0;
    private static int maxScrollPx = 0;
    private static EditBox activeInput;
    private static DragMode dragMode = DragMode.NONE;
    private static int dragPointerOffsetX;
    private static int dragPointerOffsetY;
    private static int resizeStartX;
    private static int resizeStartY;
    private static int resizeStartWidth;
    private static int resizeStartHeight;
    private static double resizeStartMouseX;
    private static double resizeStartMouseY;
    @Nullable
    private static ScrollbarMetrics scrollbarMetrics;
    private static int scrollbarDragOffsetY;
    private static long clearConfirmationUntilMs;
    private static int clearConfirmationClicksRemaining;

    private ImmersiveChatManager() {
    }

    public static void receivePlayerMessage(UUID playerId, String rank, int rankColor, String title, int titleColor,
                                            String playerName, String body, long timestamp) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ensureSession(mc);

        // Fade timing must use the client's receipt time, not the server clock.
        // A remote server can be several seconds out of sync with the client; using the packet timestamp here
        // can make a brand-new message look older than the entire HUD visibility window and disappear instantly.
        long receivedAt = System.currentTimeMillis();
        ChatEntry entry = ChatEntry.player(receivedAt, playerId, clean(rank), rankColor, clean(title), titleColor,
                clean(playerName), cleanBody(body));
        addEntry(entry, true);
    }

    public static void captureVanillaMessage(Component message, @Nullable GuiMessageTag tag) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || message == null || message.getString().isBlank()) {
            return;
        }
        ensureSession(mc);
        ChatEntry entry = ChatEntry.system(System.currentTimeMillis(), message.copy());
        addEntry(entry, true);
    }

    public static void clearVisibleMessages() {
        MESSAGES.clear();
        invalidateLayoutCache();
        scrollOffsetPx = 0;
        maxScrollPx = 0;
        HIT_LINES.clear();
        HIT_AVATARS.clear();
        HIT_PLAYER_MESSAGES.clear();
        scrollbarMetrics = null;
        clearConfirmationUntilMs = 0L;
        clearConfirmationClicksRemaining = 0;
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY, boolean focused) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()
                || mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
            return;
        }
        ensureSession(mc);

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        if (focused && dragMode != DragMode.NONE) {
            long window = mc.getWindow().getWindow();
            if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                handleMouseDragged(mouseX, mouseY, 0, screenWidth, screenHeight);
            } else {
                handleMouseReleased(0);
            }
        }
        ImmersiveChatConfig.Layout layout = ImmersiveChatConfig.resolve(screenWidth, screenHeight);
        if (focused && activeInput != null) {
            positionInput(activeInput, screenWidth, screenHeight);
        }

        int viewportX = layout.x() + OUTER_PADDING;
        int viewportY = layout.y() + (focused ? DRAG_HANDLE_HEIGHT + 4 : 4);
        int viewportWidth = layout.width() - OUTER_PADDING * 2 - (focused ? 4 : 0);
        int viewportBottom = layout.bottom() - OUTER_PADDING;
        int viewportHeight = Math.max(20, viewportBottom - viewportY);
        HIT_LINES.clear();
        HIT_AVATARS.clear();
        HIT_PLAYER_MESSAGES.clear();
        if (viewportWidth < 80 || viewportHeight < 20) {
            scrollbarMetrics = null;
            return;
        }

        Font font = mc.font;
        List<EntryLayout> layouts = getCachedLayouts(font, Math.max(40, viewportWidth));
        int calculatedTotalHeight = 0;
        for (EntryLayout entryLayout : layouts) {
            calculatedTotalHeight += entryLayout.height() + ENTRY_GAP;
        }
        final int totalHeight = calculatedTotalHeight;
        maxScrollPx = Math.max(0, totalHeight - viewportHeight);
        scrollOffsetPx = Math.max(0, Math.min(scrollOffsetPx, maxScrollPx));

        long now = System.currentTimeMillis();

        // Render the complete chat pass as one managed batch.  GuiGraphics
        // otherwise flushes after every text/fill primitive while the chat
        // component is called from the unmanaged HUD event.  The scissor
        // transitions used by the reveal animation still flush when needed,
        // but ordinary rows and rounded panels now share one submission.
        graphics.drawManaged(() -> {
            if (focused) {
                drawFocusedPanel(graphics, layout);
            } else {
                drawUnfocusedPanel(graphics, layouts, viewportX, viewportY, viewportWidth, viewportBottom, now);
            }

            graphics.enableScissor(layout.x(), layout.y(), layout.right(), layout.bottom());
            int cursorBottom = viewportBottom + (focused ? scrollOffsetPx : 0);

            for (int index = layouts.size() - 1; index >= 0; index--) {
                EntryLayout entryLayout = layouts.get(index);
                ChatEntry entry = entryLayout.entry();
                float visibility = focused ? 1.0f : unfocusedVisibility(entry.timestamp(), now);
                int renderAlpha = focused ? 255 : Math.round(230.0f * visibility);
                if (!focused && renderAlpha <= MIN_ALPHA) {
                    continue;
                }

                int entryTop = cursorBottom - entryLayout.height();
                if (renderAlpha > 0 && entryTop < viewportBottom && cursorBottom > viewportY) {
                    boolean reveal = !focused && visibility < 0.999f;
                    if (reveal) {
                        int revealRight = viewportX + Math.max(1, Math.round(viewportWidth * visibility));
                        graphics.enableScissor(viewportX, Math.max(viewportY, entryTop),
                                revealRight, Math.min(viewportBottom, cursorBottom));
                    }
                    drawEntry(graphics, font, entryLayout, viewportX, entryTop,
                            viewportWidth, renderAlpha, focused);
                    if (reveal) {
                        graphics.disableScissor();
                    }
                }
                cursorBottom = entryTop - ENTRY_GAP;

                if (!focused && cursorBottom < viewportY) {
                    break;
                }
                if (focused && cursorBottom < viewportY - scrollOffsetPx - viewportHeight) {
                    break;
                }
            }
            graphics.disableScissor();

            if (focused) {
                drawScrollbar(graphics, layout, viewportY, viewportHeight, totalHeight);
                drawResizeHandle(graphics, layout);
                drawClearConfirmation(graphics, mc.font, layout);
            } else {
                scrollbarMetrics = null;
            }
        });
    }

    public static void positionInput(EditBox input, int screenWidth, int screenHeight) {
        activeInput = input;

        // The input belongs to the screen, not to the draggable chat history panel.
        // Keeping it close to vanilla also lets CommandSuggestions anchor above it naturally.
        int marginX = 7;
        int inputHeight = 12;

        input.setX(marginX);
        input.setY(screenHeight - inputHeight - INPUT_BOTTOM_MARGIN);
        input.setWidth(Math.max(80, screenWidth - marginX * 2));
        input.setHeight(inputHeight);
    }

    /**
     * Vanilla derives both command-list and command-usage bottoms as {@code screenHeight - 15}.
     * Return a synthetic height that places that bottom just above the custom input panel.
     */
    public static int commandSuggestionScreenHeight(int screenHeight) {
        int panelTop = screenHeight - INPUT_PANEL_HEIGHT - INPUT_BOTTOM_MARGIN;
        return panelTop - COMMAND_SUGGESTION_GAP + 15;
    }

    public static void drawInputBackground(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        int x = 2;
        int y = screenHeight - 18 - INPUT_BOTTOM_MARGIN;
        int width = screenWidth - 4;
        int height = INPUT_PANEL_HEIGHT;

        // One background and one quiet border: no top accent, shadow, or inset frame.
        UiPanelRenderer.smoothRoundedRect(graphics, x, y, width, height,
                INPUT_PANEL_RADIUS, 0xB80A0C0E, 0x426B7276);
    }

    public static boolean handleMouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        ImmersiveChatConfig.Layout layout = ImmersiveChatConfig.resolve(screenWidth, screenHeight);

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            // 右键玩家消息本身即可引用发送者；头像仍保留同样的快捷操作。
            for (int index = HIT_PLAYER_MESSAGES.size() - 1; index >= 0; index--) {
                HitPlayerMessage message = HIT_PLAYER_MESSAGES.get(index);
                if (isInside(mouseX, mouseY, message.x(), message.y(), message.width(), message.height())) {
                    cancelClearConfirmation();
                    return insertMention(message.playerName());
                }
            }
            for (int index = HIT_AVATARS.size() - 1; index >= 0; index--) {
                HitAvatar avatar = HIT_AVATARS.get(index);
                if (isInside(mouseX, mouseY, avatar.x(), avatar.y(), avatar.size(), avatar.size())) {
                    cancelClearConfirmation();
                    return insertMention(avatar.playerName());
                }
            }
            if (isInside(mouseX, mouseY, layout.x(), layout.y(), layout.width(), layout.height())) {
                long now = System.currentTimeMillis();
                if (now <= clearConfirmationUntilMs && clearConfirmationClicksRemaining > 0) {
                    clearConfirmationClicksRemaining--;
                    if (clearConfirmationClicksRemaining == 0) {
                        clearCurrentSessionHistory();
                    } else {
                        clearConfirmationUntilMs = now + CLEAR_CONFIRMATION_MS;
                    }
                } else {
                    clearConfirmationUntilMs = now + CLEAR_CONFIRMATION_MS;
                    clearConfirmationClicksRemaining = 2;
                }
                return true;
            }
            cancelClearConfirmation();
            return false;
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        cancelClearConfirmation();

        ScrollbarMetrics metrics = scrollbarMetrics;
        if (metrics != null && metrics.contains(mouseX, mouseY)) {
            dragMode = DragMode.SCROLLBAR;
            scrollbarDragOffsetY = metrics.containsThumb(mouseX, mouseY)
                    ? (int) Math.round(mouseY) - metrics.thumbY()
                    : metrics.thumbHeight() / 2;
            updateScrollbarDrag(mouseY);
            return true;
        }

        // Resize zones take priority over the top move strip so the top-right corner never starts a move by accident.
        if (isInside(mouseX, mouseY, layout.right() - RESIZE_HANDLE_SIZE, layout.y(),
                RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE)) {
            beginResize(layout, mouseX, mouseY, DragMode.RESIZE_TOP_RIGHT);
            return true;
        }
        if (isInside(mouseX, mouseY, layout.right() - RESIZE_HANDLE_SIZE, layout.bottom() - RESIZE_HANDLE_SIZE,
                RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE)) {
            beginResize(layout, mouseX, mouseY, DragMode.RESIZE_BOTTOM_RIGHT);
            return true;
        }
        if (isInside(mouseX, mouseY, layout.x(), layout.bottom() - BOTTOM_RESIZE_HEIGHT,
                layout.width(), BOTTOM_RESIZE_HEIGHT)) {
            beginResize(layout, mouseX, mouseY, DragMode.RESIZE_BOTTOM);
            return true;
        }
        if (isInside(mouseX, mouseY, layout.x(), layout.y(), layout.width(), DRAG_HANDLE_HEIGHT)) {
            dragMode = DragMode.MOVE;
            dragPointerOffsetX = (int) Math.round(mouseX) - layout.x();
            dragPointerOffsetY = (int) Math.round(mouseY) - layout.y();
            return true;
        }
        return false;
    }

    private static boolean insertMention(String playerName) {
        if (activeInput == null || playerName == null || playerName.isBlank()) {
            return false;
        }

        int cursor = activeInput.getCursorPosition();
        String value = activeInput.getValue();
        boolean needsLeadingSpace = cursor > 0 && !Character.isWhitespace(value.charAt(cursor - 1));
        boolean hasTrailingSpace = cursor < value.length() && Character.isWhitespace(value.charAt(cursor));
        String insertion = (needsLeadingSpace ? " " : "") + "@" + playerName + (hasTrailingSpace ? "" : " ");
        activeInput.insertText(insertion);
        activeInput.setFocused(true);
        return true;
    }

    private static void clearCurrentSessionHistory() {
        String sessionKey = activeSafeSessionKey;
        if (sessionKey.isBlank()) {
            clearVisibleMessages();
            return;
        }

        Path directory = ImmersiveChatConfig.historyDirectory();
        try {
            Future<?> clearBarrier = HISTORY_WRITER.submit(() -> {
                synchronized (HISTORY_IO_LOCK) {
                    try {
                        if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                            try (Stream<Path> stream = Files.list(directory)) {
                                List<Path> files = stream
                                        .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                                        .filter(path -> isHistoryFileForSession(path, sessionKey))
                                        .toList();
                                for (Path file : files) {
                                    Files.deleteIfExists(file);
                                }
                            }
                        }
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                }
            });
            // This wait only runs after the user explicitly confirms a clear.
            // It orders deletion after queued appends so a stale write cannot
            // recreate the file we just removed.
            clearBarrier.get(5L, TimeUnit.SECONDS);
            clearVisibleMessages();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            DreamingFishCore.LOGGER.warn("清理聊天历史时被中断", exception);
        } catch (ExecutionException | TimeoutException | RejectedExecutionException exception) {
            DreamingFishCore.LOGGER.warn("无法清理当前会话的聊天历史记录", exception);
        }
    }

    private static void cancelClearConfirmation() {
        clearConfirmationUntilMs = 0L;
        clearConfirmationClicksRemaining = 0;
    }

    private static void beginResize(ImmersiveChatConfig.Layout layout, double mouseX, double mouseY, DragMode mode) {
        dragMode = mode;
        resizeStartX = layout.x();
        resizeStartY = layout.y();
        resizeStartWidth = layout.width();
        resizeStartHeight = layout.height();
        resizeStartMouseX = mouseX;
        resizeStartMouseY = mouseY;
    }

    public static boolean handleMouseDragged(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || dragMode == DragMode.NONE) {
            return false;
        }
        if (dragMode == DragMode.SCROLLBAR) {
            updateScrollbarDrag(mouseY);
            return true;
        }
        ImmersiveChatConfig.Layout layout = ImmersiveChatConfig.resolve(screenWidth, screenHeight);
        if (dragMode == DragMode.MOVE) {
            int x = (int) Math.round(mouseX) - dragPointerOffsetX;
            int y = (int) Math.round(mouseY) - dragPointerOffsetY;
            ImmersiveChatConfig.set(x, y, layout.width(), layout.height(), screenWidth, screenHeight, false);
        } else {
            int deltaX = (int) Math.round(mouseX - resizeStartMouseX);
            int deltaY = (int) Math.round(mouseY - resizeStartMouseY);

            switch (dragMode) {
                case RESIZE_TOP_RIGHT -> {
                    int width = resizeStartWidth + deltaX;
                    int height = resizeStartHeight - deltaY;
                    int y = resizeStartY + deltaY;
                    ImmersiveChatConfig.set(resizeStartX, y, width, height, screenWidth, screenHeight, false);
                }
                case RESIZE_BOTTOM -> {
                    int height = resizeStartHeight + deltaY;
                    ImmersiveChatConfig.set(resizeStartX, resizeStartY, resizeStartWidth, height,
                            screenWidth, screenHeight, false);
                }
                case RESIZE_BOTTOM_RIGHT -> {
                    int width = resizeStartWidth + deltaX;
                    int height = resizeStartHeight + deltaY;
                    ImmersiveChatConfig.set(resizeStartX, resizeStartY, width, height,
                            screenWidth, screenHeight, false);
                }
                default -> {
                    // MOVE and NONE are handled outside this resize switch.
                }
            }
        }
        if (activeInput != null) {
            positionInput(activeInput, screenWidth, screenHeight);
        }
        return true;
    }

    public static boolean handleMouseReleased(int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || dragMode == DragMode.NONE) {
            return false;
        }
        DragMode releasedMode = dragMode;
        dragMode = DragMode.NONE;
        if (releasedMode != DragMode.SCROLLBAR) {
            ImmersiveChatConfig.saveNow();
        }
        return true;
    }

    private static void updateScrollbarDrag(double mouseY) {
        ScrollbarMetrics metrics = scrollbarMetrics;
        if (metrics == null || maxScrollPx <= 0) {
            return;
        }

        int travel = metrics.travel();
        int desiredThumbY = (int) Math.round(mouseY) - scrollbarDragOffsetY;
        int thumbY = Math.max(metrics.trackY(), Math.min(desiredThumbY, metrics.trackY() + travel));
        float progress = (metrics.trackY() + travel - thumbY) / (float) Math.max(1, travel);
        scrollOffsetPx = Math.max(0, Math.min(maxScrollPx, Math.round(progress * maxScrollPx)));
    }

    public static void scroll(int amount) {
        if (amount == 0) {
            return;
        }
        scrollOffsetPx = Math.max(0, Math.min(maxScrollPx, scrollOffsetPx + amount * BODY_LINE_HEIGHT));
    }

    public static int getEstimatedLinesPerPage(int screenWidth, int screenHeight) {
        ImmersiveChatConfig.Layout layout = ImmersiveChatConfig.resolve(screenWidth, screenHeight);
        return Math.max(1, (layout.height() - 18) / BODY_LINE_HEIGHT);
    }

    @Nullable
    public static Style getStyleAt(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        for (HitLine line : HIT_LINES) {
            if (mouseX >= line.x() && mouseX <= line.x() + line.width()
                    && mouseY >= line.y() && mouseY <= line.y() + line.height()) {
                int localX = Math.max(0, (int) Math.floor((mouseX - line.x()) / CHAT_TEXT_SCALE));
                return mc.font.getSplitter().componentStyleAtWidth(line.content(), localX);
            }
        }
        return null;
    }

    private static List<EntryLayout> getCachedLayouts(Font font, int viewportWidth) {
        if (CACHED_LAYOUT_REVISION == MESSAGE_LAYOUT_REVISION
                && CACHED_LAYOUT_WIDTH == viewportWidth
                && CACHED_LAYOUT_FONT == font) {
            return CACHED_LAYOUTS;
        }

        CACHED_LAYOUTS = buildLayouts(font, viewportWidth);
        CACHED_LAYOUT_REVISION = MESSAGE_LAYOUT_REVISION;
        CACHED_LAYOUT_WIDTH = viewportWidth;
        CACHED_LAYOUT_FONT = font;
        return CACHED_LAYOUTS;
    }

    private static List<EntryLayout> buildLayouts(Font font, int viewportWidth) {
        List<EntryLayout> result = new ArrayList<>(MESSAGES.size());
        for (ChatEntry entry : MESSAGES) {
            Component displayBody = displayBody(entry);
            if (entry.kind() == EntryKind.PLAYER) {
                int contentWidth = Math.max(48, viewportWidth - PLAYER_HEAD_SIZE - PLAYER_HEAD_GAP);
                List<FormattedCharSequence> bodyLines = font.split(displayBody, unscaledWidth(contentWidth));
                int bodyHeight = Math.max(BODY_LINE_HEIGHT, bodyLines.size() * BODY_LINE_HEIGHT);
                boolean mentioned = isMentionedForLocalPlayer(entry);
                boolean wrappedHeader = shouldWrapPlayerHeader(font, entry, contentWidth, mentioned);
                int headerHeight = wrappedHeader
                        ? HEADER_HEIGHT * 2
                        : HEADER_HEIGHT;
                int height = Math.max(PLAYER_HEAD_SIZE + 3, headerHeight + bodyHeight + 3);
                result.add(new EntryLayout(entry, bodyLines, height, mentioned, wrappedHeader));
            } else {
                int contentWidth = Math.max(60, viewportWidth - 10);
                List<FormattedCharSequence> bodyLines = font.split(displayBody, unscaledWidth(contentWidth));
                int height = Math.max(SYSTEM_LINE_HEIGHT + 4, bodyLines.size() * SYSTEM_LINE_HEIGHT + 6);
                result.add(new EntryLayout(entry, bodyLines, height, false, false));
            }
        }
        return List.copyOf(result);
    }

    private static void invalidateLayoutCache() {
        MESSAGE_LAYOUT_REVISION++;
        CACHED_LAYOUT_REVISION = Long.MIN_VALUE;
        CACHED_LAYOUTS = List.of();
    }

    private static Component displayBody(ChatEntry entry) {
        if (entry.repeatCount() <= 1) {
            return entry.body();
        }
        return entry.body().copy().append(Component.literal(
                REPEAT_LABEL_PREFIX + entry.repeatCount() + REPEAT_LABEL_SUFFIX));
    }

    private static void drawUnfocusedPanel(GuiGraphics graphics, List<EntryLayout> layouts,
                                           int viewportX, int viewportY, int viewportWidth,
                                           int viewportBottom, long now) {
        int cursorBottom = viewportBottom;
        int visibleTop = viewportBottom;
        int strongestAlpha = 0;
        float panelVisibility = 0.0f;
        boolean hasVisibleEntry = false;

        for (int index = layouts.size() - 1; index >= 0; index--) {
            EntryLayout entryLayout = layouts.get(index);
            float visibility = unfocusedVisibility(entryLayout.entry().timestamp(), now);
            int alpha = Math.round(230.0f * visibility);
            if (alpha <= MIN_ALPHA) {
                continue;
            }

            int entryTop = cursorBottom - entryLayout.height();
            if (entryTop < viewportBottom && cursorBottom > viewportY) {
                hasVisibleEntry = true;
                visibleTop = Math.max(viewportY, entryTop);
                strongestAlpha = Math.max(strongestAlpha, alpha);
                panelVisibility = Math.max(panelVisibility, visibility);
            }

            cursorBottom = entryTop - ENTRY_GAP;
            if (cursorBottom < viewportY) {
                break;
            }
        }

        if (!hasVisibleEntry) {
            return;
        }

        int backgroundTop = Math.max(viewportY - 4, visibleTop - 4);
        int backgroundBottom = viewportBottom + 4;
        int backgroundAlpha = Math.min(138, Math.round(strongestAlpha * 0.56f));
        boolean revealPanel = panelVisibility < 0.999f;
        if (revealPanel) {
            int panelWidth = viewportWidth + 10;
            int revealRight = viewportX - 5 + Math.max(1, Math.round(panelWidth * panelVisibility));
            graphics.enableScissor(viewportX - 5, backgroundTop, revealRight, backgroundBottom);
        }
        UiPanelRenderer.smoothRoundedRectBatched(
                graphics,
                viewportX - 5,
                backgroundTop,
                viewportWidth + 10,
                backgroundBottom - backgroundTop,
                UNFOCUSED_PANEL_RADIUS,
                withAlpha(MESSAGE_BG, backgroundAlpha),
                0x00000000
        );
        if (revealPanel) {
            graphics.disableScissor();
        }
    }

    private static void drawEntry(GuiGraphics graphics, Font font, EntryLayout layout,
                                  int x, int y, int width, int alpha, boolean focused) {
        if (layout.entry().kind() == EntryKind.PLAYER) {
            drawPlayerEntry(graphics, font, layout, x, y, width, alpha, focused);
        } else {
            drawSystemEntry(graphics, font, layout, x, y, width, alpha, focused);
        }
    }

    private static void drawPlayerEntry(GuiGraphics graphics, Font font, EntryLayout layout, int x, int y,
                                        int width, int alpha, boolean focused) {
        ChatEntry entry = layout.entry();

        if (layout.mentioned()) {
            int highlightAlpha = focused ? 154 : Math.min(138, Math.round(alpha * 0.68f));
            UiPanelRenderer.smoothRoundedRectBatched(graphics, x - 4, y - 1, width + 8, layout.height() + 2,
                    4, withAlpha(MENTION_HIGHLIGHT_BG, highlightAlpha), 0x00000000);
        }

        int headY = y + 2;
        drawPlayerHead(graphics, entry, x, headY, alpha);
        if (focused && !entry.playerName().isBlank()) {
            HIT_AVATARS.add(new HitAvatar(x, headY, PLAYER_HEAD_SIZE, entry.playerName()));
            HIT_PLAYER_MESSAGES.add(new HitPlayerMessage(x - 4, y, width + 8, layout.height(), entry.playerName()));
        }
        int contentX = x + PLAYER_HEAD_SIZE + PLAYER_HEAD_GAP;
        int contentWidth = Math.max(42, width - PLAYER_HEAD_SIZE - PLAYER_HEAD_GAP);
        boolean wrappedHeader = layout.wrappedHeader();
        drawPlayerHeader(graphics, font, entry, contentX, y + 1, contentWidth, alpha,
                layout.mentioned(), wrappedHeader);

        int headerHeight = wrappedHeader ? HEADER_HEIGHT * 2 : HEADER_HEIGHT;
        int bodyY = y + headerHeight + 2;
        int lineColor = withAlpha(BODY_COLOR, Math.min(255, alpha));
        graphics.pose().pushPose();
        graphics.pose().translate(contentX, bodyY, 0.0F);
        graphics.pose().scale(CHAT_TEXT_SCALE, CHAT_TEXT_SCALE, 1.0F);
        int lineIndex = 0;
        for (FormattedCharSequence line : layout.bodyLines()) {
            graphics.drawString(font, line, 0, Math.round(lineIndex * BODY_LINE_HEIGHT / CHAT_TEXT_SCALE),
                    lineColor, true);
            if (focused) {
                int hitWidth = Math.min(contentWidth, Math.max(1, scaledTextWidth(font, line)));
                HIT_LINES.add(new HitLine(contentX, bodyY, hitWidth, BODY_LINE_HEIGHT, line));
            }
            bodyY += BODY_LINE_HEIGHT;
            lineIndex++;
        }
        graphics.pose().popPose();
    }

    private static boolean shouldWrapPlayerHeader(Font font, ChatEntry entry, int width, boolean mentioned) {
        boolean hasRank = !isEmptyRank(entry.rank());
        boolean hasTitle = !entry.title().isBlank();
        int rankWidth = hasRank ? scaledTextWidth(font, entry.rank()) + 7 : 0;
        int titleWidth = hasTitle ? scaledTextWidth(font, entry.title()) + 7 : 0;
        int nameWidth = scaledTextWidth(font, entry.playerName())
                + (mentioned ? 3 + scaledTextWidth(font, MENTION_LABEL) : 0);
        int gaps = (hasRank ? 1 : 0) + (hasTitle ? 1 : 0);
        return rankWidth + titleWidth + nameWidth + gaps * 3 > width;
    }

    private static void drawPlayerHeader(GuiGraphics graphics, Font font, ChatEntry entry, int x, int y,
                                         int width, int alpha, boolean mentioned, boolean wrapped) {
        boolean hasRank = !isEmptyRank(entry.rank());
        boolean hasTitle = !entry.title().isBlank();

        int rankNaturalWidth = hasRank ? scaledTextWidth(font, entry.rank()) + 7 : 0;
        int titleNaturalWidth = hasTitle ? scaledTextWidth(font, entry.title()) + 7 : 0;
        int nameNaturalWidth = scaledTextWidth(font, entry.playerName());
        int mentionLabelWidth = mentioned ? scaledTextWidth(font, MENTION_LABEL) : 0;
        int nameColor = hasRank
                ? (0xFF000000 | (entry.rankColor() & 0x00FFFFFF))
                : NAME_COLOR;

        if (!wrapped) {
            int currentX = x;
            if (hasRank) {
                drawChip(graphics, font, entry.rank(), currentX, y, rankNaturalWidth, entry.rankColor(), alpha);
                currentX += rankNaturalWidth + 3;
            }
            if (hasTitle) {
                drawChip(graphics, font, entry.title(), currentX, y, titleNaturalWidth, entry.titleColor(), alpha);
                currentX += titleNaturalWidth + 3;
            }
            drawScaledString(graphics, font, entry.playerName(), currentX, y + 1,
                    withAlpha(nameColor, alpha), true);
            if (mentioned) {
                drawScaledString(graphics, font, MENTION_LABEL, currentX + nameNaturalWidth + 3, y + 1,
                        withAlpha(MENTION_LABEL_COLOR, alpha), true);
            }
            return;
        }

        // Wrapped layout: Rank + title stay on the first row; the player name moves to a second row.
        // Only trim an element when that row cannot fit inside the chat content width by itself.
        int currentX = x;
        int remainingFirstRow = width;

        if (hasRank && remainingFirstRow >= 16) {
            int rankWidth = Math.min(rankNaturalWidth, remainingFirstRow);
            drawChip(graphics, font, entry.rank(), currentX, y, rankWidth, entry.rankColor(), alpha);
            currentX += rankWidth + 3;
            remainingFirstRow = Math.max(0, remainingFirstRow - rankWidth - 3);
        }

        if (hasTitle && remainingFirstRow >= 16) {
            int titleWidth = Math.min(titleNaturalWidth, remainingFirstRow);
            drawChip(graphics, font, entry.title(), currentX, y, titleWidth, entry.titleColor(), alpha);
        }

        int reservedMentionWidth = mentioned ? mentionLabelWidth + 3 : 0;
        int nameWidth = Math.min(nameNaturalWidth, Math.max(0, width - reservedMentionWidth));
        if (nameWidth > 0) {
            String displayName = trimToScaledWidth(font, entry.playerName(), nameWidth);
            drawScaledString(graphics, font, displayName, x, y + HEADER_HEIGHT + 1,
                    withAlpha(nameColor, alpha), true);
            if (mentioned) {
                int labelX = x + scaledTextWidth(font, displayName) + 3;
                drawScaledString(graphics, font, MENTION_LABEL, labelX, y + HEADER_HEIGHT + 1,
                        withAlpha(MENTION_LABEL_COLOR, alpha), true);
            }
        }
    }

    private static void drawChip(GuiGraphics graphics, Font font, String text, int x, int y, int width,
                                 int rgbColor, int alpha) {
        int color = 0xFF000000 | (rgbColor & 0x00FFFFFF);
        int background = withAlpha(color, Math.min(110, Math.round(alpha * 0.35f)));
        int border = withAlpha(color, Math.min(190, Math.round(alpha * 0.68f)));
        UiPanelRenderer.smoothRoundedRectBatched(graphics, x, y, width, 9,
                3, background, border);
        String clipped = trimToScaledWidth(font, text, Math.max(4, width - 6));
        drawScaledString(graphics, font, clipped, x + 3, y + 1,
                withAlpha(blendWithWhite(color, 0.34f), alpha), false);
    }

    private static void drawSystemEntry(GuiGraphics graphics, Font font, EntryLayout layout, int x, int y,
                                        int width, int alpha, boolean focused) {
        // System lines share the same single chat surface; only the vertical rule distinguishes them.
        graphics.fill(x + 1, y + 2, x + 3, y + layout.height() - 2,
                withAlpha(MUTED_COLOR, Math.min(145, alpha)));
        int textX = x + 8;
        int textY = y + 4;
        int contentWidth = Math.max(40, width - 11);
        int lineColor = withAlpha(MUTED_COLOR, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(textX, textY, 0.0F);
        graphics.pose().scale(CHAT_TEXT_SCALE, CHAT_TEXT_SCALE, 1.0F);
        int lineIndex = 0;
        for (FormattedCharSequence line : layout.bodyLines()) {
            graphics.drawString(font, line, 0, Math.round(lineIndex * SYSTEM_LINE_HEIGHT / CHAT_TEXT_SCALE),
                    lineColor, true);
            if (focused) {
                int hitWidth = Math.min(contentWidth, Math.max(1, scaledTextWidth(font, line)));
                HIT_LINES.add(new HitLine(textX, textY, hitWidth, SYSTEM_LINE_HEIGHT, line));
            }
            textY += SYSTEM_LINE_HEIGHT;
            lineIndex++;
        }
        graphics.pose().popPose();
    }

    private static void drawPlayerHead(GuiGraphics graphics, ChatEntry entry, int x, int y, int alpha) {
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo playerInfo = mc.getConnection() == null || entry.playerId() == null
                ? null
                : mc.getConnection().getPlayerInfo(entry.playerId());
        if (playerInfo != null) {
            graphics.setColor(1.0f, 1.0f, 1.0f, alpha / 255.0f);
            PlayerFaceRenderer.draw(graphics, playerInfo.getSkin(), x, y, PLAYER_HEAD_SIZE);
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            return;
        }

        UiPanelRenderer.smoothRoundedRectBatched(graphics, x, y, PLAYER_HEAD_SIZE, PLAYER_HEAD_SIZE,
                4, withAlpha(0xFF242A2E, Math.min(alpha, 210)), 0x00000000);
        String initial = entry.playerName().isBlank()
                ? "?"
                : entry.playerName().substring(0, 1).toUpperCase(Locale.ROOT);
        int textX = x + (PLAYER_HEAD_SIZE - scaledTextWidth(mc.font, initial)) / 2;
        drawScaledString(graphics, mc.font, initial, textX, y + 4,
                withAlpha(NAME_COLOR, alpha), false);
    }

    private static void drawFocusedPanel(GuiGraphics graphics, ImmersiveChatConfig.Layout layout) {
        // Exactly one deep-gray translucent surface. No border, inset, shadow, or per-entry card.
        UiPanelRenderer.smoothRoundedRectBatched(graphics, layout.x(), layout.y(), layout.width(), layout.height(),
                FOCUSED_PANEL_RADIUS, PANEL_BG, 0x00000000);

        int handleWidth = Math.min(34, Math.max(18, layout.width() / 9));
        int handleX = layout.x() + (layout.width() - handleWidth) / 2;
        graphics.fill(handleX, layout.y() + 3, handleX + handleWidth, layout.y() + 4, 0x667F888C);
    }

    private static void drawClearConfirmation(GuiGraphics graphics, Font font,
                                              ImmersiveChatConfig.Layout layout) {
        if (System.currentTimeMillis() > clearConfirmationUntilMs) {
            return;
        }

        String confirmationText = clearConfirmationClicksRemaining >= 2
                ? CLEAR_CONFIRMATION_TEXT
                : CLEAR_FINAL_CONFIRMATION_TEXT;
        int maxTextWidth = Math.max(40, layout.width() - 26);
        String displayText = trimToScaledWidth(font, confirmationText, maxTextWidth);
        int boxWidth = Math.min(layout.width() - 12, scaledTextWidth(font, displayText) + 12);
        int boxHeight = 15;
        int boxX = layout.x() + (layout.width() - boxWidth) / 2;
        int boxY = layout.y() + DRAG_HANDLE_HEIGHT + 3;
        UiPanelRenderer.smoothRoundedRectBatched(graphics, boxX, boxY, boxWidth, boxHeight,
                4, 0xEC2A2621, 0xC4FFD54A);
        drawScaledString(graphics, font, displayText, boxX + 6, boxY + 3,
                0xFFFFD54A, true);
    }

    private static void drawResizeHandle(GuiGraphics graphics, ImmersiveChatConfig.Layout layout) {
        int color = 0x8F8A9499;
        int right = layout.right() - 3;
        int top = layout.y() + 3;
        int bottom = layout.bottom() - 3;

        // Top-right corner: width + height resize.
        graphics.fill(right - 7, top, right, top + 1, color);
        graphics.fill(right - 4, top + 3, right, top + 4, color);
        graphics.fill(right - 1, top + 6, right, top + 7, color);

        // Bottom edge: vertical resize from anywhere along the lower strip; this centered mark is only a hint.
        int bottomHandleWidth = Math.min(34, Math.max(18, layout.width() / 8));
        int bottomHandleX = layout.x() + (layout.width() - bottomHandleWidth) / 2;
        graphics.fill(bottomHandleX, bottom, bottomHandleX + bottomHandleWidth, bottom + 1, color);

        // Bottom-right corner: width + height resize.
        graphics.fill(right - 7, bottom - 1, right, bottom, color);
        graphics.fill(right - 4, bottom - 4, right, bottom - 3, color);
        graphics.fill(right - 1, bottom - 7, right, bottom - 6, color);
    }

    private static void drawScrollbar(GuiGraphics graphics, ImmersiveChatConfig.Layout layout,
                                      int viewportY, int viewportHeight, int totalHeight) {
        if (totalHeight <= viewportHeight || maxScrollPx <= 0) {
            scrollbarMetrics = null;
            return;
        }
        int trackX = layout.right() - 4;
        int thumbHeight = Math.max(14, Math.round(viewportHeight * (viewportHeight / (float) totalHeight)));
        int travel = Math.max(1, viewportHeight - thumbHeight);
        float progress = scrollOffsetPx / (float) maxScrollPx;
        int thumbY = viewportY + travel - Math.round(progress * travel);
        graphics.fill(trackX, viewportY, trackX + 1, viewportY + viewportHeight, 0x3AFFFFFF);
        graphics.fill(trackX - 1, thumbY, trackX + 2, thumbY + thumbHeight, 0x86C4C8C7);
        scrollbarMetrics = new ScrollbarMetrics(trackX, viewportY, viewportHeight, thumbY, thumbHeight);
    }

    private static boolean isMentionedForLocalPlayer(ChatEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        return entry.kind() == EntryKind.PLAYER
                && mc.player != null
                && containsMention(entry.body().getString(), mc.player.getGameProfile().getName());
    }

    static boolean containsMention(String message, String playerName) {
        if (message == null || playerName == null || playerName.isBlank()) {
            return false;
        }

        int searchFrom = 0;
        while (searchFrom < message.length()) {
            int marker = message.indexOf('@', searchFrom);
            if (marker < 0) {
                return false;
            }
            int nameStart = marker + 1;
            int nameEnd = nameStart + playerName.length();
            boolean nameMatches = nameEnd <= message.length()
                    && message.regionMatches(true, nameStart, playerName, 0, playerName.length());
            boolean validStart = marker == 0 || !isPlayerNameCharacter(message.charAt(marker - 1));
            boolean validEnd = nameEnd >= message.length() || !isPlayerNameCharacter(message.charAt(nameEnd));
            if (nameMatches && validStart && validEnd) {
                return true;
            }
            searchFrom = marker + 1;
        }
        return false;
    }

    private static boolean isPlayerNameCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private static float unfocusedVisibility(long timestamp, long now) {
        long age = Math.max(0L, now - timestamp);
        if (age >= UNFOCUSED_LIFETIME_MS) {
            return 0.0f;
        }
        float intro = easeOutCubic(age / (float) UNFOCUSED_SIDE_ANIMATION_MS);
        long outroStart = UNFOCUSED_LIFETIME_MS - UNFOCUSED_SIDE_ANIMATION_MS;
        float outro = age > outroStart
                ? easeInCubic((age - outroStart) / (float) UNFOCUSED_SIDE_ANIMATION_MS)
                : 0.0f;
        return intro * (1.0f - outro);
    }

    private static float easeOutCubic(float progress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        float remaining = 1.0f - clamped;
        return 1.0f - remaining * remaining * remaining;
    }

    private static float easeInCubic(float progress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        return clamped * clamped * clamped;
    }

    private static void addEntry(ChatEntry entry, boolean persist) {
        ChatEntry rawEntry = entry;
        if (!MESSAGES.isEmpty()) {
            int lastIndex = MESSAGES.size() - 1;
            ChatEntry previous = MESSAGES.get(lastIndex);
            if (previous.hasSameRepeatIdentity(entry)) {
                // 只合并当前列表末尾的连续消息；中间出现任何其他消息都会截断重复计数。
                entry = entry.withRepeatCount(previous.repeatCount() + entry.repeatCount());
                MESSAGES.set(lastIndex, entry);
            } else {
                MESSAGES.add(entry);
            }
        } else {
            MESSAGES.add(entry);
        }
        while (MESSAGES.size() > MAX_MESSAGES) {
            MESSAGES.remove(0);
        }
        invalidateLayoutCache();
        if (persist) {
            // Keep the history append-only. Replaying each raw occurrence rebuilds the same folded count.
            appendHistory(rawEntry);
        }
    }

    private static void ensureSession(Minecraft mc) {
        String sessionKey = getSessionKey(mc);
        if (sessionKey.equals(activeSessionKey)) {
            return;
        }
        activeSessionKey = sessionKey;
        activeSafeSessionKey = safeSessionKey(sessionKey);
        MESSAGES.clear();
        invalidateLayoutCache();
        scrollOffsetPx = 0;
        maxScrollPx = 0;
        loadHistory();
    }

    private static String getSessionKey(Minecraft mc) {
        if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null && !mc.getCurrentServer().ip.isBlank()) {
            return "server_" + mc.getCurrentServer().ip;
        }
        if (mc.hasSingleplayerServer()) {
            Path worldPath = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                    .toAbsolutePath().normalize();
            UUID worldId = UUID.nameUUIDFromBytes(worldPath.toString().getBytes(StandardCharsets.UTF_8));
            return "singleplayer_" + worldId + "_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        return "local";
    }

    private static String safeSessionKey(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.length() > 72) {
            safe = safe.substring(0, 72);
        }
        return safe.isBlank() ? "local" : safe;
    }

    private static void appendHistory(ChatEntry entry) {
        String sessionKey = activeSafeSessionKey;
        if (sessionKey.isBlank()) {
            return;
        }

        Path directory = ImmersiveChatConfig.historyDirectory();
        LocalDate date = Instant.ofEpochMilli(entry.timestamp()).atZone(ZoneId.systemDefault()).toLocalDate();
        Path file = directory.resolve(sessionKey + "_" + date + ".jsonl");
        String serialized;
        try {
            serialized = GSON.toJson(HistoryRecord.from(entry)) + System.lineSeparator();
        } catch (Exception exception) {
            DreamingFishCore.LOGGER.debug("无法序列化聊天历史记录", exception);
            return;
        }
        try {
            HISTORY_WRITER.execute(() -> {
                synchronized (HISTORY_IO_LOCK) {
                    try {
                        Files.createDirectories(directory);
                        Files.writeString(file, serialized, StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (Exception exception) {
                        DreamingFishCore.LOGGER.debug("无法写入聊天历史记录", exception);
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            DreamingFishCore.LOGGER.debug("无法写入聊天历史记录", exception);
        }
    }

    private static void loadHistory() {
        Path directory = ImmersiveChatConfig.historyDirectory();
        if (activeSafeSessionKey.isBlank() || !Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(ImmersiveChatManager::isHistoryFileForActiveSession)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            if (files.size() > HISTORY_FILE_DAYS) {
                files = files.subList(files.size() - HISTORY_FILE_DAYS, files.size());
            }

            List<ChatEntry> loaded = new ArrayList<>();
            for (Path file : files) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        HistoryRecord record = GSON.fromJson(line, HistoryRecord.class);
                        ChatEntry entry = record == null ? null : record.toEntry();
                        if (entry != null) {
                            loaded.add(entry);
                        }
                    } catch (Exception ignored) {
                        // One malformed history row must not make the whole chat log unusable.
                    }
                }
            }
            int start = Math.max(0, loaded.size() - HISTORY_LOAD_LIMIT);
            for (int index = start; index < loaded.size(); index++) {
                addEntry(loaded.get(index), false);
            }
        } catch (IOException exception) {
            DreamingFishCore.LOGGER.debug("无法读取聊天历史记录", exception);
        }
    }

    private static boolean isHistoryFileForActiveSession(Path path) {
        return isHistoryFileForSession(path, activeSafeSessionKey);
    }

    private static boolean isHistoryFileForSession(Path path, String sessionKey) {
        if (path == null || sessionKey == null || sessionKey.isBlank()) {
            return false;
        }
        String name = path.getFileName().toString();
        String prefix = sessionKey + "_";
        String suffix = ".jsonl";
        if (!name.startsWith(prefix) || !name.endsWith(suffix)) {
            return false;
        }
        String date = name.substring(prefix.length(), name.length() - suffix.length());
        try {
            LocalDate.parse(date);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static String cleanBody(String value) {
        return value == null ? "" : value.replace('\r', ' ').strip();
    }

    private static boolean isEmptyRank(String rank) {
        return rank == null || rank.isBlank() || "NO_RANK".equalsIgnoreCase(rank) || "NULL".equalsIgnoreCase(rank);
    }

    private static int unscaledWidth(int scaledWidth) {
        return Math.max(1, (int) Math.floor(scaledWidth / CHAT_TEXT_SCALE));
    }

    private static int scaledTextWidth(Font font, String text) {
        return Math.max(0, (int) Math.ceil(font.width(text == null ? "" : text) * CHAT_TEXT_SCALE));
    }

    private static int scaledTextWidth(Font font, FormattedCharSequence text) {
        return Math.max(0, (int) Math.ceil(font.width(text) * CHAT_TEXT_SCALE));
    }

    private static String trimToScaledWidth(Font font, String text, int maxWidth) {
        return trimToWidth(font, text, unscaledWidth(maxWidth));
    }

    private static void drawScaledString(GuiGraphics graphics, Font font, String text, int x, int y,
                                         int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(CHAT_TEXT_SCALE, CHAT_TEXT_SCALE, 1.0F);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    private static void drawScaledString(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y,
                                         int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(CHAT_TEXT_SCALE, CHAT_TEXT_SCALE, 1.0F);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    private static String trimToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return ellipsis;
        }
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static int blendWithWhite(int color, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.round(r + (255 - r) * t);
        g = Math.round(g + (255 - g) * t);
        b = Math.round(b + (255 - b) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private enum DragMode {
        NONE,
        MOVE,
        SCROLLBAR,
        RESIZE_TOP_RIGHT,
        RESIZE_BOTTOM,
        RESIZE_BOTTOM_RIGHT
    }

    private enum EntryKind {
        PLAYER,
        SYSTEM
    }

    private record ChatEntry(EntryKind kind, long timestamp, @Nullable UUID playerId, String rank, int rankColor,
                             String title, int titleColor, String playerName, Component body, int repeatCount) {
        static ChatEntry player(long timestamp, UUID playerId, String rank, int rankColor, String title,
                                int titleColor, String playerName, String body) {
            return new ChatEntry(EntryKind.PLAYER, timestamp, playerId, rank, rankColor, title, titleColor,
                    playerName, Component.literal(body), 1);
        }

        static ChatEntry system(long timestamp, Component body) {
            return new ChatEntry(EntryKind.SYSTEM, timestamp, null, "", 0x9BA4A8, "", 0x9BA4A8,
                    "", body == null ? Component.empty() : body, 1);
        }

        boolean hasSameRepeatIdentity(ChatEntry other) {
            if (other == null || kind != other.kind) {
                return false;
            }
            if (kind == EntryKind.PLAYER && !Objects.equals(playerId, other.playerId)) {
                return false;
            }
            return body.getString().equals(other.body.getString());
        }

        ChatEntry withRepeatCount(int count) {
            return new ChatEntry(kind, timestamp, playerId, rank, rankColor, title, titleColor,
                    playerName, body, Math.max(1, count));
        }
    }

    private record EntryLayout(ChatEntry entry, List<FormattedCharSequence> bodyLines, int height,
                               boolean mentioned, boolean wrappedHeader) {
    }

    private record HitLine(int x, int y, int width, int height, FormattedCharSequence content) {
    }

    private record HitAvatar(int x, int y, int size, String playerName) {
    }

    private record HitPlayerMessage(int x, int y, int width, int height, String playerName) {
    }

    private record ScrollbarMetrics(int trackX, int trackY, int trackHeight, int thumbY, int thumbHeight) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= trackX - SCROLLBAR_HIT_PADDING
                    && mouseX < trackX + SCROLLBAR_HIT_PADDING + 1
                    && mouseY >= trackY
                    && mouseY < trackY + trackHeight;
        }

        boolean containsThumb(double mouseX, double mouseY) {
            return contains(mouseX, mouseY) && mouseY >= thumbY && mouseY < thumbY + thumbHeight;
        }

        int travel() {
            return Math.max(1, trackHeight - thumbHeight);
        }
    }

    private static final class HistoryRecord {
        String kind;
        long timestamp;
        String playerId;
        String rank;
        int rankColor;
        String title;
        int titleColor;
        String playerName;
        String body;

        static HistoryRecord from(ChatEntry entry) {
            HistoryRecord record = new HistoryRecord();
            record.kind = entry.kind().name();
            record.timestamp = entry.timestamp();
            record.playerId = entry.playerId() == null ? "" : entry.playerId().toString();
            record.rank = entry.rank();
            record.rankColor = entry.rankColor();
            record.title = entry.title();
            record.titleColor = entry.titleColor();
            record.playerName = entry.playerName();
            record.body = entry.body().getString();
            return record;
        }

        @Nullable
        ChatEntry toEntry() {
            if (body == null || kind == null) {
                return null;
            }
            if (EntryKind.PLAYER.name().equals(kind)) {
                try {
                    UUID uuid = playerId == null || playerId.isBlank() ? null : UUID.fromString(playerId);
                    if (uuid == null) {
                        return null;
                    }
                    return ChatEntry.player(timestamp, uuid, clean(rank), rankColor, clean(title), titleColor,
                            clean(playerName), body);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
            return ChatEntry.system(timestamp, Component.literal(body));
        }
    }
}
