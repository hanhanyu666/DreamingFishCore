package com.hhy.dreamingfishcore.client.ui.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.client.ui.framework.hud.HudCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.render.RankBadge;
import com.hhy.dreamingfishcore.server.title_system.network.Packet_QuotedChatMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    /** 头像边长；名字一行与正文都从头像右侧 {@link #CONTENT_INDENT} 处开始。 */
    private static final int HEAD_SIZE = 12;
    private static final int CONTENT_INDENT = 17;
    private static final int HEADER_HEIGHT = 9;
    private static final int QUOTE_LINE_HEIGHT = 9;
    private static final int BODY_LINE_HEIGHT = 10;
    private static final int ENTRY_GAP = 4;
    /** 系统消息比玩家消息小一号、颜色更淡，前面一个金色小圆点。 */
    private static final int SYSTEM_INDENT = 8;
    private static final float SYSTEM_TEXT_SCALE = 0.84F;
    private static final int SYSTEM_LINE_HEIGHT = 9;
    private static final float CHAT_TEXT_SCALE = 0.90f;
    private static final float NAME_SCALE = 0.8F;
    private static final float TAG_SCALE = 0.5F;
    private static final float TITLE_SCALE = 0.62F;
    private static final int UNFOCUSED_SIDE_ANIMATION_MS = 220;
    private static final int UNFOCUSED_LIFETIME_MS = 13_000;
    private static final int MIN_ALPHA = 4;
    private static final int DRAG_HANDLE_HEIGHT = 8;
    private static final int RESIZE_HANDLE_SIZE = 11;
    private static final int BOTTOM_RESIZE_HEIGHT = 6;
    private static final float PANEL_RADIUS = 7.0F;
    private static final int INPUT_BOTTOM_MARGIN = 5;
    private static final int INPUT_PANEL_HEIGHT = 18;
    /** 输入框左侧留给提示符“›”的宽度。 */
    private static final int INPUT_PROMPT_WIDTH = 9;
    private static final int COMMAND_SUGGESTION_GAP = 2;
    private static final int SCROLLBAR_HIT_PADDING = 3;
    private static final long CLEAR_CONFIRMATION_MS = 3_000L;
    private static final int QUOTE_PREVIEW_HEIGHT = 30;
    private static final int QUOTE_PREVIEW_GAP = 4;
    private static final int QUOTE_PREVIEW_MARGIN_X = 8;
    private static final int QUOTE_PREVIEW_CLOSE_SIZE = 10;

    private static final String MENTION_TAG = "@你";
    private static final String REPEAT_LABEL_PREFIX = " (重复 ";
    private static final String REPEAT_LABEL_SUFFIX = ")";
    private static final String CLEAR_CONFIRMATION_TEXT = "再按两下右键才能清空当前聊天记录";
    private static final String CLEAR_FINAL_CONFIRMATION_TEXT = "再按一下右键确认清空当前聊天记录";

    private static final int BODY_COLOR = 0xFFEEE8D8;
    private static final int NAME_COLOR = 0xFFF1F1EE;
    private static final int SYSTEM_COLOR = 0xFFA9A79F;
    private static final int QUOTE_COLOR = 0xFF8F9496;
    private static final int GOLD = 0xFFE8C482;
    /** 每条消息后面那条向右渐隐的暗带的底色。 */
    private static final int SCRIM = 0x0A0C0E;

    private static final List<ChatEntry> MESSAGES = new ArrayList<>();
    private static final Map<UUID, PlayerInfo> CACHED_PLAYER_INFO = new HashMap<>();
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
    private static boolean sessionIdentityInitialized;
    private static Object cachedSessionConnection;
    private static Object cachedSessionLevel;
    private static Object cachedIntegratedServer;
    private static Object cachedCurrentServer;
    private static long unfocusedVisibleUntilMs;
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
    @Nullable
    private static QuoteTarget quotedMessage;
    @Nullable
    private static QuotePreviewMetrics quotePreviewMetrics;
    private static int scrollbarDragOffsetY;
    private static long clearConfirmationUntilMs;
    private static int clearConfirmationClicksRemaining;

    private ImmersiveChatManager() {
    }

    public static void receivePlayerMessage(UUID playerId, String rank, int rankColor, String title, int titleColor,
                                            String playerName, String body, long timestamp) {
        receivePlayerMessage(playerId, rank, rankColor, title, titleColor, playerName, body, timestamp, "", "");
    }

    public static void receivePlayerMessage(UUID playerId, String rank, int rankColor, String title, int titleColor,
                                            String playerName, String body, long timestamp,
                                            String quotedPlayerName, String quotedBody) {
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
                clean(playerName), cleanBody(body), quotedPlayerName, quotedBody);
        addEntry(entry, true);
    }

    public static boolean submitQuotedMessage(String value) {
        if (quotedMessage == null || value == null) return false;
        String body = cleanBody(value).trim();
        if (body.isBlank() || body.startsWith("/")) return false;
        QuoteTarget quote = quotedMessage;
        DreamingFishCore_NetworkManager.sendToServer(new Packet_QuotedChatMessage(body, quote.playerName(), quote.body()));
        quotedMessage = null;
        quotePreviewMetrics = null;
        return true;
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
        unfocusedVisibleUntilMs = 0L;
        invalidateLayoutCache();
        scrollOffsetPx = 0;
        maxScrollPx = 0;
        HIT_LINES.clear();
        HIT_AVATARS.clear();
        HIT_PLAYER_MESSAGES.clear();
        scrollbarMetrics = null;
        quotedMessage = null;
        quotePreviewMetrics = null;
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
        if (!focused && unfocusedVisibleUntilMs == 0L) {
            scrollbarMetrics = null;
            quotePreviewMetrics = null;
            return;
        }
        long now = System.currentTimeMillis();

        // ChatComponent invokes this method at the display frame rate. Once the
        // newest message has fully faded, stop before resolving the synchronized
        // layout, clearing hit-test lists, or walking cached message rows.
        if (!focused && now >= unfocusedVisibleUntilMs) {
            unfocusedVisibleUntilMs = 0L;
            scrollbarMetrics = null;
            quotePreviewMetrics = null;
            return;
        }

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

        /*
         * The unfocused chat is completely invisible after its 13 second
         * lifetime.  Returning here avoids two reverse scans of as many as 800
         * cached history rows, a scissor transition, and an otherwise empty
         * managed GUI pass on every ordinary gameplay frame.
         */
        if (!focused && !hasVisibleUnfocusedEntry(layouts, now)) {
            scrollbarMetrics = null;
            quotePreviewMetrics = null;
            return;
        }

        int calculatedTotalHeight = 0;
        if (focused) {
            for (EntryLayout entryLayout : layouts) {
                calculatedTotalHeight += entryLayout.height() + ENTRY_GAP;
            }
            maxScrollPx = Math.max(0, calculatedTotalHeight - viewportHeight);
            scrollOffsetPx = Math.max(0, Math.min(scrollOffsetPx, maxScrollPx));
        }
        final int totalHeight = calculatedTotalHeight;

        // 整个聊天窗画在一块框架画布上：面板、文字与头像各自合批，入场展开用画布裁剪。
        HudCanvas.paintNow(graphics, canvas -> {
            if (focused) {
                drawFocusedPanel(canvas, layout);
            }

            canvas.pushClip(layout.x(), layout.y(), layout.width(), layout.height(), 0.0F);
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
                        int clipTop = Math.max(viewportY, entryTop);
                        canvas.pushClip(viewportX, clipTop, revealRight - viewportX,
                                Math.min(viewportBottom, cursorBottom) - clipTop, 0.0F);
                    }
                    drawEntry(canvas, font, entryLayout, viewportX, entryTop,
                            viewportWidth, renderAlpha, focused);
                    if (reveal) {
                        canvas.popClip();
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
            canvas.popClip();

        if (focused) {
            drawScrollbar(canvas, layout, viewportY, viewportHeight, totalHeight);
            drawResizeHandle(canvas, layout);
            drawClearConfirmation(canvas, mc.font, layout);
            drawQuotePreview(canvas, mc.font, screenWidth, screenHeight);
        } else {
            scrollbarMetrics = null;
            quotePreviewMetrics = null;
        }
        });
    }

    public static void positionInput(EditBox input, int screenWidth, int screenHeight) {
        activeInput = input;

        // The input belongs to the screen, not to the draggable chat history panel.
        // Keeping it close to vanilla also lets CommandSuggestions anchor above it naturally.
        int marginX = 7;
        int inputHeight = 12;
        input.setX(marginX + INPUT_PROMPT_WIDTH);
        input.setY(screenHeight - inputHeight - INPUT_BOTTOM_MARGIN);
        input.setWidth(Math.max(80, screenWidth - marginX * 2 - INPUT_PROMPT_WIDTH));
        input.setHeight(inputHeight);
    }

    /**
     * Vanilla derives both command-list and command-usage bottoms as {@code screenHeight - 15}.
     * Return a synthetic height that places that bottom just above the custom input panel.
     */
    public static int commandSuggestionScreenHeight(int screenHeight) {
        int panelTop = screenHeight - INPUT_PANEL_HEIGHT - INPUT_BOTTOM_MARGIN;
        int quoteOffset = quotedMessage == null ? 0 : QUOTE_PREVIEW_HEIGHT + QUOTE_PREVIEW_GAP;
        return panelTop - quoteOffset - COMMAND_SUGGESTION_GAP + 15;
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

        HudCanvas.paintNow(graphics, canvas -> drawInputPanel(canvas, x, y, width, height));
    }

    /** Clears the pending message quote when the chat screen closes. */
    public static void onChatClosed() {
        activeInput = null;
        dragMode = DragMode.NONE;
        quotedMessage = null;
        quotePreviewMetrics = null;
    }

    public static boolean handleMouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        ImmersiveChatConfig.Layout layout = ImmersiveChatConfig.resolve(screenWidth, screenHeight);

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && quotePreviewMetrics != null
                && quotePreviewMetrics.containsClose(mouseX, mouseY)) {
            quotedMessage = null;
            quotePreviewMetrics = null;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            // 右键玩家消息本身即可引用发送者；头像仍保留同样的快捷操作。
            for (int index = HIT_PLAYER_MESSAGES.size() - 1; index >= 0; index--) {
                HitPlayerMessage message = HIT_PLAYER_MESSAGES.get(index);
                if (isInside(mouseX, mouseY, message.x(), message.y(), message.width(), message.height())) {
                    cancelClearConfirmation();
                    return selectQuote(message.playerName(), message.body());
                }
            }
            for (int index = HIT_AVATARS.size() - 1; index >= 0; index--) {
                HitAvatar avatar = HIT_AVATARS.get(index);
                if (isInside(mouseX, mouseY, avatar.x(), avatar.y(), avatar.size(), avatar.size())) {
                    cancelClearConfirmation();
                    return selectQuote(avatar.playerName(), avatar.body());
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

    private static boolean selectQuote(String playerName, String body) {
        if (!insertMention(playerName)) {
            return false;
        }
        quotedMessage = new QuoteTarget(playerName, body);
        quotePreviewMetrics = null;
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

    private static void drawClearConfirmation(UiCanvas canvas, Font font,
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
        panel(canvas, boxX, boxY, boxWidth, boxHeight,
                4, 0xEC2A2621, 0xC4FFD54A);
        drawScaledString(canvas, font, displayText, boxX + 6, boxY + 3,
                0xFFFFD54A, true);
    }

    private static void drawQuotePreview(UiCanvas canvas, Font font, int screenWidth, int screenHeight) {
        QuoteTarget quote = quotedMessage;
        if (quote == null || quote.playerName().isBlank()) {
            quotePreviewMetrics = null;
            return;
        }

        int inputTop = screenHeight - INPUT_PANEL_HEIGHT - INPUT_BOTTOM_MARGIN;
        int boxX = QUOTE_PREVIEW_MARGIN_X;
        int boxWidth = Math.max(80, screenWidth - QUOTE_PREVIEW_MARGIN_X * 2);
        int boxY = inputTop - QUOTE_PREVIEW_GAP - QUOTE_PREVIEW_HEIGHT;
        panel(canvas, boxX, boxY, boxWidth, QUOTE_PREVIEW_HEIGHT,
                4, 0xE51A1E22, 0x9A68747A);
        fillRect(canvas, boxX + 3, boxY + 4, boxX + 5, boxY + QUOTE_PREVIEW_HEIGHT - 4, 0xFF6EB6D8);

        String header = "引用 " + quote.playerName();
        drawScaledString(canvas, font, trimToScaledWidth(font, header, boxWidth - 28),
                boxX + 10, boxY + 4, 0xFFE4E8E8, true);
        String body = quote.body().isBlank() ? "（空消息）" : quote.body();
        String clippedBody = trimToScaledWidth(font, body, boxWidth - 28);
        drawScaledString(canvas, font, clippedBody, boxX + 10, boxY + 15, 0xFFB8C0C2, false);

        int closeX = boxX + boxWidth - QUOTE_PREVIEW_CLOSE_SIZE - 4;
        int closeY = boxY + (QUOTE_PREVIEW_HEIGHT - QUOTE_PREVIEW_CLOSE_SIZE) / 2;
        int closeColor = 0xFF9BA5A8;
        fillRect(canvas, closeX + 2, closeY, closeX + QUOTE_PREVIEW_CLOSE_SIZE - 2, closeY + 1, closeColor);
        fillRect(canvas, closeX + 2, closeY + QUOTE_PREVIEW_CLOSE_SIZE - 1,
                closeX + QUOTE_PREVIEW_CLOSE_SIZE - 2, closeY + QUOTE_PREVIEW_CLOSE_SIZE, closeColor);
        fillRect(canvas, closeX, closeY + 2, closeX + 1, closeY + QUOTE_PREVIEW_CLOSE_SIZE - 2, closeColor);
        fillRect(canvas, closeX + QUOTE_PREVIEW_CLOSE_SIZE - 1, closeY + 2,
                closeX + QUOTE_PREVIEW_CLOSE_SIZE, closeY + QUOTE_PREVIEW_CLOSE_SIZE - 2, closeColor);
        quotePreviewMetrics = new QuotePreviewMetrics(closeX, closeY, QUOTE_PREVIEW_CLOSE_SIZE);
    }

    private static void drawResizeHandle(UiCanvas canvas, ImmersiveChatConfig.Layout layout) {
        int color = 0x8F8A9499;
        int right = layout.right() - 3;
        int top = layout.y() + 3;
        int bottom = layout.bottom() - 3;

        // Top-right corner: width + height resize.
        fillRect(canvas, right - 7, top, right, top + 1, color);
        fillRect(canvas, right - 4, top + 3, right, top + 4, color);
        fillRect(canvas, right - 1, top + 6, right, top + 7, color);

        // Bottom edge: vertical resize from anywhere along the lower strip; this centered mark is only a hint.
        int bottomHandleWidth = Math.min(34, Math.max(18, layout.width() / 8));
        int bottomHandleX = layout.x() + (layout.width() - bottomHandleWidth) / 2;
        fillRect(canvas, bottomHandleX, bottom, bottomHandleX + bottomHandleWidth, bottom + 1, color);

        // Bottom-right corner: width + height resize.
        fillRect(canvas, right - 7, bottom - 1, right, bottom, color);
        fillRect(canvas, right - 4, bottom - 4, right, bottom - 3, color);
        fillRect(canvas, right - 1, bottom - 7, right, bottom - 6, color);
    }

    private static void drawScrollbar(UiCanvas canvas, ImmersiveChatConfig.Layout layout,
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
        fillRect(canvas, trackX, viewportY, trackX + 1, viewportY + viewportHeight, 0x3AFFFFFF);
        fillRect(canvas, trackX - 1, thumbY, trackX + 2, thumbY + thumbHeight, 0x86C4C8C7);
        scrollbarMetrics = new ScrollbarMetrics(trackX, viewportY, viewportHeight, thumbY, thumbHeight);
    }

    private static List<EntryLayout> buildLayouts(Font font, int viewportWidth) {
        List<EntryLayout> result = new ArrayList<>(MESSAGES.size());
        for (ChatEntry entry : MESSAGES) {
            Component displayBody = displayBody(entry);
            if (entry.kind() == EntryKind.PLAYER) {
                int contentWidth = Math.max(48, viewportWidth - CONTENT_INDENT);
                List<FormattedCharSequence> bodyLines = font.split(displayBody, unscaledWidth(contentWidth));
                boolean mentioned = isMentionedForLocalPlayer(entry);
                List<FormattedCharSequence> quoteLines = entry.hasQuote()
                        ? List.of(Component.literal("↳ " + entry.quotedPlayerName() + "：" + entry.quotedBody())
                        .getVisualOrderText())
                        : List.of();
                int quoteHeight = quoteLines.isEmpty() ? 0 : QUOTE_LINE_HEIGHT;
                int height = Math.max(HEAD_SIZE + 2,
                        HEADER_HEIGHT + quoteHeight + Math.max(1, bodyLines.size()) * BODY_LINE_HEIGHT);
                result.add(new EntryLayout(entry, bodyLines, quoteLines, quoteHeight, height, mentioned));
            } else {
                int contentWidth = Math.max(60, viewportWidth - SYSTEM_INDENT);
                List<FormattedCharSequence> bodyLines = font.split(displayBody,
                        Math.max(1, (int) Math.floor(contentWidth / SYSTEM_TEXT_SCALE)));
                int height = Math.max(SYSTEM_LINE_HEIGHT, bodyLines.size() * SYSTEM_LINE_HEIGHT) + 1;
                result.add(new EntryLayout(entry, bodyLines, List.of(), 0, height, false));
            }
        }
        return List.copyOf(result);
    }

    private static void drawEntry(UiCanvas canvas, Font font, EntryLayout layout, int x, int y,
                                        int width, int alpha, boolean focused) {
        ChatEntry entry = layout.entry();
        boolean player = entry.kind() == EntryKind.PLAYER;
        int indent = player ? CONTENT_INDENT : SYSTEM_INDENT;
        float a = alpha / 255.0F;

        // 未打开聊天时：每条消息后面一条向右渐隐的暗带，长度跟着文字走，像字幕的底
        if (!focused) {
            int textWidth = 0;
            for (FormattedCharSequence line : layout.bodyLines()) {
                textWidth = Math.max(textWidth, Math.round(font.width(line) * (player ? CHAT_TEXT_SCALE : SYSTEM_TEXT_SCALE)));
            }
            if (player) {
                textWidth = Math.max(textWidth, headerWidth(font, entry, layout.mentioned()));
            }
            float bandWidth = Math.min(width + 8.0F, indent + textWidth + 30.0F);
            int strength = Math.round((player ? 120 : 92) * a);
            canvas.shape(x - 5.0F, y - 2.0F, bandWidth, layout.height() + 3.0F).radius(4.0F)
                    .horizontalGradient(withAlpha(SCRIM, strength), withAlpha(SCRIM, 0)).draw();
        }
        if (layout.mentioned()) {
            canvas.shape(x - 5.0F, y - 2.0F, Math.min(width + 8.0F, 160.0F), layout.height() + 3.0F).radius(4.0F)
                    .horizontalGradient(withAlpha(GOLD, Math.round(46 * a)), withAlpha(GOLD, 0)).draw();
            canvas.fill(x - 5.0F, y - 1.0F, 1.0F, layout.height() + 1.0F, withAlpha(GOLD, Math.round(220 * a)));
        }

        if (!player) {
            int textX = x + indent;
            canvas.circle(x + 2.0F, y + 4.0F, 0.9F, withAlpha(GOLD, Math.round(170 * a)));
            int textY = y + 1;
            int contentWidth = Math.max(40, width - indent);
            for (FormattedCharSequence line : layout.bodyLines()) {
                canvas.text(line, textX, textY, withAlpha(SYSTEM_COLOR, alpha), SYSTEM_TEXT_SCALE, true);
                if (focused) {
                    HIT_LINES.add(new HitLine(textX, textY, Math.min(contentWidth,
                            Math.max(1, Math.round(font.width(line) * SYSTEM_TEXT_SCALE))), SYSTEM_LINE_HEIGHT, line));
                }
                textY += SYSTEM_LINE_HEIGHT;
            }
            return;
        }

        int contentX = x + indent;
        drawPlayerHead(canvas, entry, x, y, alpha);
        if (focused && !entry.playerName().isBlank()) {
            HIT_AVATARS.add(new HitAvatar(x, y, HEAD_SIZE, entry.playerName(), entry.body().getString()));
        }
        if (focused && !entry.playerName().isBlank()) {
            HIT_PLAYER_MESSAGES.add(new HitPlayerMessage(x - 4, y, width + 8, layout.height(),
                    entry.playerName(), entry.body().getString()));
        }
        drawPlayerHeader(canvas, font, entry, contentX, y, alpha, layout.mentioned());
        int bodyY = y + HEADER_HEIGHT;
        if (layout.quoteHeight() > 0) {
            // 引用只占一行：“↳ 名字：原文”，超出部分裁掉
            FormattedCharSequence quote = layout.quoteLines().get(0);
            canvas.pushClip(contentX, bodyY, Math.max(10, width - indent), QUOTE_LINE_HEIGHT, 0.0F);
            canvas.text(quote, contentX, bodyY + 0.5F, withAlpha(QUOTE_COLOR, alpha), 0.78F, false);
            canvas.popClip();
            bodyY += QUOTE_LINE_HEIGHT;
        }
        int lineColor = withAlpha(BODY_COLOR, alpha);
        int contentWidth = Math.max(42, width - indent);
        for (FormattedCharSequence line : layout.bodyLines()) {
            canvas.text(line, contentX, bodyY, lineColor, CHAT_TEXT_SCALE, true);
            if (focused) {
                HIT_LINES.add(new HitLine(contentX, bodyY, Math.min(contentWidth, Math.max(1, scaledTextWidth(font, line))),
                        BODY_LINE_HEIGHT, line));
            }
            bodyY += BODY_LINE_HEIGHT;
        }
    }

    /** 名字一行：名字用 Rank 色（往骨白靠一点），后面跟描边的 Rank 小标签与称号小字。 */
    private static void drawPlayerHeader(UiCanvas canvas, Font font, ChatEntry entry, int x, int y, int alpha,
                                         boolean mentioned) {
        float a = alpha / 255.0F;
        int nameColor = isEmptyRank(entry.rank()) ? NAME_COLOR : rankTone(entry);
        canvas.text(entry.playerName(), x, y, withAlpha(nameColor, alpha), NAME_SCALE, true);
        float cursor = x + font.width(entry.playerName()) * NAME_SCALE + 4.0F;
        if (!isEmptyRank(entry.rank())) {
            cursor += RankBadge.draw(canvas, font, entry.rank(), entry.rankColor(), cursor, y + 0.8F, a) + 2.0F;
        }
        if (!entry.title().isBlank()) {
            // 称号不套框，调淡后跟在后面当头衔
            int color = blendWithWhite(0xFF000000 | entry.titleColor(), 0.25F);
            canvas.text(entry.title(), cursor, y + 1.2F, withAlpha(color, Math.round(130 * a)), TITLE_SCALE, false);
            cursor += font.width(entry.title()) * TITLE_SCALE + 3.0F;
        }
        if (mentioned) {
            float tagWidth = font.width(MENTION_TAG) * TAG_SCALE + 4.0F;
            canvas.shape(cursor, y + 0.8F, tagWidth, 5.8F).radius(1.0F).fill(withAlpha(GOLD, Math.round(210 * a))).draw();
            canvas.text(MENTION_TAG, cursor + 2.0F, y + 1.6F, withAlpha(0xFF241C0E, alpha), TAG_SCALE, false);
        }
    }

    private static int headerWidth(Font font, ChatEntry entry, boolean mentioned) {
        float width = font.width(entry.playerName()) * NAME_SCALE + 4.0F;
        if (!isEmptyRank(entry.rank())) {
            width += RankBadge.width(font, entry.rank()) + 2.0F;
        }
        if (!entry.title().isBlank()) {
            width += font.width(entry.title()) * TITLE_SCALE + 3.0F;
        }
        if (mentioned) {
            width += font.width(MENTION_TAG) * TAG_SCALE + 4.0F;
        }
        return Math.round(width);
    }

    private static int rankTone(ChatEntry entry) {
        return blendWithWhite(0xFF000000 | entry.rankColor(), 0.28F);
    }

    private static void drawPlayerHead(UiCanvas canvas, ChatEntry entry, int x, int y, int alpha) {
        Minecraft mc = Minecraft.getInstance();
        UUID playerId = entry.playerId();
        PlayerInfo playerInfo = playerId == null ? null : CACHED_PLAYER_INFO.get(playerId);
        if (playerInfo == null && mc.getConnection() != null && playerId != null) {
            playerInfo = mc.getConnection().getPlayerInfo(playerId);
            if (playerInfo != null) {
                CACHED_PLAYER_INFO.put(playerId, playerInfo);
            }
        }
        canvas.shape(x - 0.5F, y - 0.5F, HEAD_SIZE + 1.0F, HEAD_SIZE + 1.0F).radius(3.0F)
                .fill(withAlpha(0xFF0A0C0E, Math.min(200, alpha))).draw();
        if (playerInfo != null) {
            // 皮肤异步下载完成后会替换临时皮肤，所以每次都从缓存的 PlayerInfo 取
            canvas.playerFace(playerInfo.getSkin().texture(), x, y, HEAD_SIZE, 2.5F, withAlpha(0xFFFFFFFF, alpha));
            return;
        }
        String initial = entry.playerName().isBlank() ? "?" : entry.playerName().substring(0, 1).toUpperCase(Locale.ROOT);
        canvas.text(initial, x + (HEAD_SIZE - mc.font.width(initial) * 0.8F) / 2.0F, y + 2.6F,
                withAlpha(NAME_COLOR, alpha), 0.8F, false);
    }

    private static void drawFocusedPanel(UiCanvas canvas, ImmersiveChatConfig.Layout layout) {
        int x = layout.x();
        int y = layout.y();
        int width = layout.width();
        int height = layout.height();
        // 磨砂玻璃：上浅下深，一圈很淡的亮边，顶上一道高光，中间是拖动把手
        canvas.shape(x, y, width, height).radius(PANEL_RADIUS).verticalGradient(0xB4121518, 0xD00B0D10)
                .border(1.0F, 0x1CFFFFFF).draw();
        canvas.shape(x + 10, y + 1, width / 2.0F - 10, 1.0F).horizontalGradient(0x00FFFFFF, 0x1EFFFFFF).draw();
        canvas.shape(x + width / 2.0F, y + 1, width / 2.0F - 10, 1.0F).horizontalGradient(0x1EFFFFFF, 0x00FFFFFF).draw();
        canvas.shape(x + width / 2.0F - 9, y + 3.5F, 18, 1.5F).radius(0.75F).fill(0x55FFFFFF).draw();
    }

    private static void drawInputPanel(UiCanvas canvas, int x, int y, int width, int height) {
        canvas.shape(x, y, width, height).radius(PANEL_RADIUS).verticalGradient(0xC4121518, 0xDC0B0D10)
                .border(1.0F, 0x22FFFFFF).draw();
        canvas.text("›", x + 6.0F, y + 4.5F, GOLD, 1.0F, false);
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

    private static boolean hasVisibleUnfocusedEntry(List<EntryLayout> layouts, long now) {
        for (int index = layouts.size() - 1; index >= 0; index--) {
            long timestamp = layouts.get(index).entry().timestamp();
            if (now - timestamp >= UNFOCUSED_LIFETIME_MS) {
                // Entries are appended chronologically. Once this reverse scan
                // reaches an expired row, every earlier row is expired too.
                return false;
            }
            if (Math.round(230.0f * unfocusedVisibility(timestamp, now)) > MIN_ALPHA) {
                return true;
            }
        }
        return false;
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
        unfocusedVisibleUntilMs = Math.max(unfocusedVisibleUntilMs,
                entry.timestamp() + UNFOCUSED_LIFETIME_MS);
        invalidateLayoutCache();
        if (persist) {
            // Keep the history append-only. Replaying each raw occurrence rebuilds the same folded count.
            appendHistory(rawEntry);
        }
    }

    private static void ensureSession(Minecraft mc) {
        Object connection = mc.getConnection();
        Object level = mc.level;
        Object integratedServer = mc.getSingleplayerServer();
        Object currentServer = mc.getCurrentServer();
        if (sessionIdentityInitialized
                && cachedSessionConnection == connection
                && cachedSessionLevel == level
                && cachedIntegratedServer == integratedServer
                && cachedCurrentServer == currentServer) {
            return;
        }

        sessionIdentityInitialized = true;
        cachedSessionConnection = connection;
        cachedSessionLevel = level;
        cachedIntegratedServer = integratedServer;
        cachedCurrentServer = currentServer;
        CACHED_PLAYER_INFO.clear();
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
        unfocusedVisibleUntilMs = 0L;
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

    private static void drawScaledString(UiCanvas canvas, Font font, String text, int x, int y,
                                         int color, boolean shadow) {
        canvas.text(text, x, y, color, CHAT_TEXT_SCALE, shadow);
    }

    /** 圆角面板；{@code border} 透明时不描边。 */
    private static void panel(UiCanvas canvas, int x, int y, int width, int height, int radius, int fill, int border) {
        if (width <= 0 || height <= 0) {
            return;
        }
        var shape = canvas.shape(x, y, width, height).radius(radius).fill(fill);
        if ((border >>> 24) != 0) {
            shape.border(1.0F, border);
        }
        shape.draw();
    }

    /** 与 {@code GuiGraphics#fill} 相同的两点坐标。 */
    private static void fillRect(UiCanvas canvas, int x0, int y0, int x1, int y1, int color) {
        canvas.fill(Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0), color);
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
                             String title, int titleColor, String playerName, Component body, int repeatCount,
                             String quotedPlayerName, String quotedBody) {
        static ChatEntry player(long timestamp, UUID playerId, String rank, int rankColor, String title,
                                int titleColor, String playerName, String body) {
            return player(timestamp, playerId, rank, rankColor, title, titleColor, playerName, body, "", "");
        }

        static ChatEntry player(long timestamp, UUID playerId, String rank, int rankColor, String title,
                                int titleColor, String playerName, String body, String quotedPlayerName, String quotedBody) {
            return new ChatEntry(EntryKind.PLAYER, timestamp, playerId, rank, rankColor, title, titleColor,
                    playerName, Component.literal(body), 1, clean(quotedPlayerName), cleanBody(quotedBody).replace('\n', ' '));
        }

        static ChatEntry system(long timestamp, Component body) {
            return new ChatEntry(EntryKind.SYSTEM, timestamp, null, "", 0x9BA4A8, "", 0x9BA4A8,
                    "", body == null ? Component.empty() : body, 1, "", "");
        }

        boolean hasQuote() { return !quotedPlayerName.isBlank() && !quotedBody.isBlank(); }

        boolean hasSameRepeatIdentity(ChatEntry other) {
            if (other == null || kind != other.kind) {
                return false;
            }
            if (kind == EntryKind.PLAYER && !Objects.equals(playerId, other.playerId)) {
                return false;
            }
            return body.getString().equals(other.body.getString())
                    && Objects.equals(quotedPlayerName, other.quotedPlayerName)
                    && Objects.equals(quotedBody, other.quotedBody);
        }

        ChatEntry withRepeatCount(int count) {
            return new ChatEntry(kind, timestamp, playerId, rank, rankColor, title, titleColor,
                    playerName, body, Math.max(1, count), quotedPlayerName, quotedBody);
        }
    }

    private record EntryLayout(ChatEntry entry, List<FormattedCharSequence> bodyLines,
                               List<FormattedCharSequence> quoteLines, int quoteHeight, int height,
                               boolean mentioned) {
    }

    private record HitLine(int x, int y, int width, int height, FormattedCharSequence content) {
    }

    private record HitAvatar(int x, int y, int size, String playerName, String body) {
    }

    private record HitPlayerMessage(int x, int y, int width, int height, String playerName, String body) {
    }

    private record QuoteTarget(String playerName, String body) {
        private QuoteTarget {
            playerName = clean(playerName);
            body = cleanBody(body).replace('\n', ' ');
        }
    }

    private record QuotePreviewMetrics(int closeX, int closeY, int size) {
        boolean containsClose(double mouseX, double mouseY) {
            return isInside(mouseX, mouseY, closeX, closeY, size, size);
        }
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
        String quotedPlayerName;
        String quotedBody;

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
            record.quotedPlayerName = entry.quotedPlayerName();
            record.quotedBody = entry.quotedBody();
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
                            clean(playerName), body, quotedPlayerName, quotedBody);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
            return ChatEntry.system(timestamp, Component.literal(body));
        }
    }
}
