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
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
    private static final int UNFOCUSED_FULL_MS = 9_000;
    private static final int UNFOCUSED_FADE_MS = 4_000;
    private static final int MIN_ALPHA = 4;
    private static final int DRAG_HANDLE_HEIGHT = 8;
    private static final int RESIZE_HANDLE_SIZE = 11;
    private static final int BOTTOM_RESIZE_HEIGHT = 6;

    private static final int PANEL_BG = 0xA025282A;

    // One shared deep-gray chat surface. Individual entries do not draw their own cards.
    private static final int MESSAGE_BG = 0xFF25282A;
    private static final int BODY_COLOR = 0xFFE8E8E4;
    private static final int NAME_COLOR = 0xFFF1F1EE;
    private static final int MUTED_COLOR = 0xFFA7AAA7;

    private static final List<ChatEntry> MESSAGES = new ArrayList<>();
    private static final List<HitLine> HIT_LINES = new ArrayList<>();
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
        scrollOffsetPx = 0;
        maxScrollPx = 0;
        HIT_LINES.clear();
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY, boolean focused) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
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

        if (focused) {
            drawFocusedPanel(graphics, layout);
        }

        int viewportX = layout.x() + OUTER_PADDING;
        int viewportY = layout.y() + (focused ? DRAG_HANDLE_HEIGHT + 4 : 4);
        int viewportWidth = layout.width() - OUTER_PADDING * 2 - (focused ? 4 : 0);
        int viewportBottom = layout.bottom() - OUTER_PADDING;
        int viewportHeight = Math.max(20, viewportBottom - viewportY);
        if (viewportWidth < 80 || viewportHeight < 20) {
            return;
        }

        Font font = mc.font;
        List<EntryLayout> layouts = buildLayouts(font, Math.max(40, viewportWidth));
        int totalHeight = 0;
        for (EntryLayout entryLayout : layouts) {
            totalHeight += entryLayout.height() + ENTRY_GAP;
        }
        maxScrollPx = Math.max(0, totalHeight - viewportHeight);
        scrollOffsetPx = Math.max(0, Math.min(scrollOffsetPx, maxScrollPx));

        HIT_LINES.clear();
        long now = System.currentTimeMillis();
        if (!focused) {
            drawUnfocusedPanel(graphics, layouts, viewportX, viewportY, viewportWidth, viewportBottom, now);
        }

        graphics.enableScissor(layout.x(), layout.y(), layout.right(), layout.bottom());
        int cursorBottom = viewportBottom + (focused ? scrollOffsetPx : 0);

        for (int index = layouts.size() - 1; index >= 0; index--) {
            EntryLayout entryLayout = layouts.get(index);
            ChatEntry entry = entryLayout.entry();
            int alpha = focused ? 255 : unfocusedAlpha(entry.timestamp(), now);
            if (!focused && alpha <= MIN_ALPHA) {
                continue;
            }

            int entryTop = cursorBottom - entryLayout.height();
            if (entryTop < viewportBottom && cursorBottom > viewportY) {
                if (entry.kind() == EntryKind.PLAYER) {
                    drawPlayerEntry(graphics, font, entryLayout, viewportX, entryTop, viewportWidth, alpha, focused);
                } else {
                    drawSystemEntry(graphics, font, entryLayout, viewportX, entryTop, viewportWidth, alpha, focused);
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
        }
    }

    public static void positionInput(EditBox input, int screenWidth, int screenHeight) {
        activeInput = input;

        // The input belongs to the screen, not to the draggable chat history panel.
        // Keeping it close to vanilla also lets CommandSuggestions anchor above it naturally.
        int marginX = 7;
        int inputHeight = 12;

        input.setX(marginX);
        // Match vanilla's vertical anchor. CommandSuggestions is also anchored around screenHeight - 12.
        input.setY(screenHeight - 12);
        input.setWidth(Math.max(80, screenWidth - marginX * 2));
        input.setHeight(inputHeight);
    }

    public static void drawInputBackground(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        int x = 2;
        int y = screenHeight - 18;
        int width = screenWidth - 4;
        int height = 18;

        // One background and one quiet border: no top accent, shadow, or inset frame.
        UiPanelRenderer.roundedRect(graphics, x, y, width, height, 3, 0xB80A0C0E);
        UiPanelRenderer.roundedBorder(graphics, x, y, width, height, 3, 0x426B7276);
    }

    public static boolean handleMouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (button != 0) {
            return false;
        }
        ImmersiveChatConfig.Layout layout = ImmersiveChatConfig.resolve(screenWidth, screenHeight);

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
        if (button != 0 || dragMode == DragMode.NONE) {
            return false;
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
        if (button != 0 || dragMode == DragMode.NONE) {
            return false;
        }
        dragMode = DragMode.NONE;
        ImmersiveChatConfig.saveNow();
        return true;
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

    private static List<EntryLayout> buildLayouts(Font font, int viewportWidth) {
        List<EntryLayout> result = new ArrayList<>(MESSAGES.size());
        for (ChatEntry entry : MESSAGES) {
            if (entry.kind() == EntryKind.PLAYER) {
                int contentWidth = Math.max(48, viewportWidth - PLAYER_HEAD_SIZE - PLAYER_HEAD_GAP);
                List<FormattedCharSequence> bodyLines = font.split(entry.body(), unscaledWidth(contentWidth));
                int bodyHeight = Math.max(BODY_LINE_HEIGHT, bodyLines.size() * BODY_LINE_HEIGHT);
                int headerHeight = shouldWrapPlayerHeader(font, entry, contentWidth)
                        ? HEADER_HEIGHT * 2
                        : HEADER_HEIGHT;
                int height = Math.max(PLAYER_HEAD_SIZE + 3, headerHeight + bodyHeight + 3);
                result.add(new EntryLayout(entry, bodyLines, height));
            } else {
                int contentWidth = Math.max(60, viewportWidth - 10);
                List<FormattedCharSequence> bodyLines = font.split(entry.body(), unscaledWidth(contentWidth));
                int height = Math.max(SYSTEM_LINE_HEIGHT + 4, bodyLines.size() * SYSTEM_LINE_HEIGHT + 6);
                result.add(new EntryLayout(entry, bodyLines, height));
            }
        }
        return result;
    }

    private static void drawUnfocusedPanel(GuiGraphics graphics, List<EntryLayout> layouts,
                                           int viewportX, int viewportY, int viewportWidth,
                                           int viewportBottom, long now) {
        int cursorBottom = viewportBottom;
        int visibleTop = viewportBottom;
        int strongestAlpha = 0;
        boolean hasVisibleEntry = false;

        for (int index = layouts.size() - 1; index >= 0; index--) {
            EntryLayout entryLayout = layouts.get(index);
            int alpha = unfocusedAlpha(entryLayout.entry().timestamp(), now);
            if (alpha <= MIN_ALPHA) {
                continue;
            }

            int entryTop = cursorBottom - entryLayout.height();
            if (entryTop < viewportBottom && cursorBottom > viewportY) {
                hasVisibleEntry = true;
                visibleTop = Math.max(viewportY, entryTop);
                strongestAlpha = Math.max(strongestAlpha, alpha);
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
        UiPanelRenderer.roundedRect(
                graphics,
                viewportX - 5,
                backgroundTop,
                viewportWidth + 10,
                backgroundBottom - backgroundTop,
                4,
                withAlpha(MESSAGE_BG, backgroundAlpha)
        );
    }

    private static void drawPlayerEntry(GuiGraphics graphics, Font font, EntryLayout layout, int x, int y,
                                        int width, int alpha, boolean focused) {
        ChatEntry entry = layout.entry();

        int headY = y + 2;
        drawPlayerHead(graphics, entry, x, headY, alpha);
        int contentX = x + PLAYER_HEAD_SIZE + PLAYER_HEAD_GAP;
        int contentWidth = Math.max(42, width - PLAYER_HEAD_SIZE - PLAYER_HEAD_GAP);
        boolean wrappedHeader = shouldWrapPlayerHeader(font, entry, contentWidth);
        drawPlayerHeader(graphics, font, entry, contentX, y + 1, contentWidth, alpha);

        int headerHeight = wrappedHeader ? HEADER_HEIGHT * 2 : HEADER_HEIGHT;
        int bodyY = y + headerHeight + 2;
        for (FormattedCharSequence line : layout.bodyLines()) {
            int lineColor = withAlpha(BODY_COLOR, Math.min(255, alpha));
            drawScaledString(graphics, font, line, contentX, bodyY, lineColor, true);
            if (focused) {
                int hitWidth = Math.min(contentWidth, Math.max(1, scaledTextWidth(font, line)));
                HIT_LINES.add(new HitLine(contentX, bodyY, hitWidth, BODY_LINE_HEIGHT, line));
            }
            bodyY += BODY_LINE_HEIGHT;
        }
    }

    private static boolean shouldWrapPlayerHeader(Font font, ChatEntry entry, int width) {
        boolean hasRank = !isEmptyRank(entry.rank());
        boolean hasTitle = !entry.title().isBlank();
        int rankWidth = hasRank ? scaledTextWidth(font, entry.rank()) + 7 : 0;
        int titleWidth = hasTitle ? scaledTextWidth(font, entry.title()) + 7 : 0;
        int nameWidth = scaledTextWidth(font, entry.playerName());
        int gaps = (hasRank ? 1 : 0) + (hasTitle ? 1 : 0);
        return rankWidth + titleWidth + nameWidth + gaps * 3 > width;
    }

    private static void drawPlayerHeader(GuiGraphics graphics, Font font, ChatEntry entry, int x, int y,
                                         int width, int alpha) {
        boolean hasRank = !isEmptyRank(entry.rank());
        boolean hasTitle = !entry.title().isBlank();
        boolean wrapped = shouldWrapPlayerHeader(font, entry, width);

        int rankNaturalWidth = hasRank ? scaledTextWidth(font, entry.rank()) + 7 : 0;
        int titleNaturalWidth = hasTitle ? scaledTextWidth(font, entry.title()) + 7 : 0;
        int nameNaturalWidth = scaledTextWidth(font, entry.playerName());
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

        int nameWidth = Math.min(nameNaturalWidth, width);
        if (nameWidth > 0) {
            String displayName = trimToScaledWidth(font, entry.playerName(), nameWidth);
            drawScaledString(graphics, font, displayName, x, y + HEADER_HEIGHT + 1,
                    withAlpha(nameColor, alpha), true);
        }
    }

    private static void drawChip(GuiGraphics graphics, Font font, String text, int x, int y, int width,
                                 int rgbColor, int alpha) {
        int color = 0xFF000000 | (rgbColor & 0x00FFFFFF);
        int background = withAlpha(color, Math.min(110, Math.round(alpha * 0.35f)));
        int border = withAlpha(color, Math.min(190, Math.round(alpha * 0.68f)));
        UiPanelRenderer.roundedRect(graphics, x, y, width, 9, 2, background);
        UiPanelRenderer.roundedBorder(graphics, x, y, width, 9, 2, border);
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
        int textY = y + 2;
        int contentWidth = Math.max(40, width - 11);
        for (FormattedCharSequence line : layout.bodyLines()) {
            drawScaledString(graphics, font, line, textX, textY, withAlpha(MUTED_COLOR, alpha), true);
            if (focused) {
                int hitWidth = Math.min(contentWidth, Math.max(1, scaledTextWidth(font, line)));
                HIT_LINES.add(new HitLine(textX, textY, hitWidth, SYSTEM_LINE_HEIGHT, line));
            }
            textY += SYSTEM_LINE_HEIGHT;
        }
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

        UiPanelRenderer.roundedRect(graphics, x, y, PLAYER_HEAD_SIZE, PLAYER_HEAD_SIZE, 3,
                withAlpha(0xFF242A2E, Math.min(alpha, 210)));
        String initial = entry.playerName().isBlank()
                ? "?"
                : entry.playerName().substring(0, 1).toUpperCase(Locale.ROOT);
        int textX = x + (PLAYER_HEAD_SIZE - scaledTextWidth(mc.font, initial)) / 2;
        drawScaledString(graphics, mc.font, initial, textX, y + 4,
                withAlpha(NAME_COLOR, alpha), false);
    }

    private static void drawFocusedPanel(GuiGraphics graphics, ImmersiveChatConfig.Layout layout) {
        // Exactly one deep-gray translucent surface. No border, inset, shadow, or per-entry card.
        UiPanelRenderer.roundedRect(graphics, layout.x(), layout.y(), layout.width(), layout.height(), 4, PANEL_BG);

        int handleWidth = Math.min(34, Math.max(18, layout.width() / 9));
        int handleX = layout.x() + (layout.width() - handleWidth) / 2;
        graphics.fill(handleX, layout.y() + 3, handleX + handleWidth, layout.y() + 4, 0x667F888C);
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
            return;
        }
        int trackX = layout.right() - 4;
        int thumbHeight = Math.max(14, Math.round(viewportHeight * (viewportHeight / (float) totalHeight)));
        int travel = Math.max(1, viewportHeight - thumbHeight);
        float progress = scrollOffsetPx / (float) maxScrollPx;
        int thumbY = viewportY + travel - Math.round(progress * travel);
        graphics.fill(trackX, viewportY, trackX + 1, viewportY + viewportHeight, 0x3AFFFFFF);
        graphics.fill(trackX - 1, thumbY, trackX + 2, thumbY + thumbHeight, 0x86C4C8C7);
    }

    private static int unfocusedAlpha(long timestamp, long now) {
        long age = Math.max(0L, now - timestamp);
        if (age <= UNFOCUSED_FULL_MS) {
            return 230;
        }
        if (age >= UNFOCUSED_FULL_MS + UNFOCUSED_FADE_MS) {
            return 0;
        }
        float remaining = 1.0f - (age - UNFOCUSED_FULL_MS) / (float) UNFOCUSED_FADE_MS;
        return Math.max(0, Math.min(230, Math.round(230 * remaining * remaining)));
    }

    private static void addEntry(ChatEntry entry, boolean persist) {
        MESSAGES.add(entry);
        while (MESSAGES.size() > MAX_MESSAGES) {
            MESSAGES.remove(0);
        }
        if (persist) {
            appendHistory(entry);
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
        scrollOffsetPx = 0;
        maxScrollPx = 0;
        loadHistory();
    }

    private static String getSessionKey(Minecraft mc) {
        if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null && !mc.getCurrentServer().ip.isBlank()) {
            return "server_" + mc.getCurrentServer().ip;
        }
        if (mc.hasSingleplayerServer()) {
            return "singleplayer";
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
        if (activeSafeSessionKey.isBlank()) {
            return;
        }
        try {
            Path directory = ImmersiveChatConfig.historyDirectory();
            Files.createDirectories(directory);
            LocalDate date = Instant.ofEpochMilli(entry.timestamp()).atZone(ZoneId.systemDefault()).toLocalDate();
            Path file = directory.resolve(activeSafeSessionKey + "_" + date + ".jsonl");
            HistoryRecord record = HistoryRecord.from(entry);
            Files.writeString(file, GSON.toJson(record) + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception exception) {
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
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(activeSafeSessionKey + "_") && name.endsWith(".jsonl");
                    })
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
        RESIZE_TOP_RIGHT,
        RESIZE_BOTTOM,
        RESIZE_BOTTOM_RIGHT
    }

    private enum EntryKind {
        PLAYER,
        SYSTEM
    }

    private record ChatEntry(EntryKind kind, long timestamp, @Nullable UUID playerId, String rank, int rankColor,
                             String title, int titleColor, String playerName, Component body) {
        static ChatEntry player(long timestamp, UUID playerId, String rank, int rankColor, String title,
                                int titleColor, String playerName, String body) {
            return new ChatEntry(EntryKind.PLAYER, timestamp, playerId, rank, rankColor, title, titleColor,
                    playerName, Component.literal(body));
        }

        static ChatEntry system(long timestamp, Component body) {
            return new ChatEntry(EntryKind.SYSTEM, timestamp, null, "", 0x9BA4A8, "", 0x9BA4A8,
                    "", body == null ? Component.empty() : body);
        }
    }

    private record EntryLayout(ChatEntry entry, List<FormattedCharSequence> bodyLines, int height) {
    }

    private record HitLine(int x, int y, int width, int height, FormattedCharSequence content) {
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
