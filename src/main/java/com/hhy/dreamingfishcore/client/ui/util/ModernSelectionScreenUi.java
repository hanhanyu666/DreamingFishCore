package com.hhy.dreamingfishcore.client.ui.util;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.LanServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ModernSelectionScreenUi {

    public enum Kind {
        WORLDS,
        MULTIPLAYER
    }

    private static final String SELECT_WORLD_SCREEN = "net.minecraft.client.gui.screens.worldselection.SelectWorldScreen";
    private static final String JOIN_MULTIPLAYER_SCREEN = "net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen";
    private static final DateTimeFormatter WORLD_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm").withZone(ZoneId.systemDefault());
    private static final float ENTRY_TEXT_SCALE = 1.08F;
    private static final long INTRO_MS = 520L;
    private static final long ENTRY_STAGGER_MS = 42L;
    private static final Map<Screen, Long> OPEN_TIMES = new WeakHashMap<>();

    private ModernSelectionScreenUi() {
    }

    public static void resetAnimation(Screen screen) {
        if (screen != null) {
            OPEN_TIMES.put(screen, System.currentTimeMillis());
        }
    }

    public static boolean isModernSelectionScreen() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            return false;
        }

        String name = screen.getClass().getName();
        return SELECT_WORLD_SCREEN.equals(name) || JOIN_MULTIPLAYER_SCREEN.equals(name);
    }

    public static Layout calculateLayout(Screen screen, boolean hasSearch) {
        VirtualCoordinateHelper.VirtualSizeResult virtualSize = new VirtualCoordinateHelper.VirtualSizeResult();
        VirtualCoordinateHelper.calculateVirtualSize(screen, virtualSize);

        int virtualW = virtualSize.virtualWidth;
        int virtualH = virtualSize.virtualHeight;
        int margin = virtualW >= 720 ? 28 : 22;
        int gap = 0;
        int headerY = 18;
        int panelY = 68;
        int footerY = Math.max(panelY + 170, virtualH - 48);
        int panelH = Math.max(164, footerY - panelY - 10);
        int contentW = virtualW - margin * 2;
        int detailW = 0;
        int listW = contentW;

        int listPanelX = margin;
        int detailX = listPanelX + listW + gap;
        int searchShellY = panelY + 12;
        int searchShellH = 0;
        int listY = panelY;
        int listH = Math.max(84, panelH);

        return new Layout(
                virtualSize.uiScale,
                virtualW,
                virtualH,
                margin,
                headerY,
                panelY,
                panelH,
                listPanelX,
                listW,
                detailX,
                detailW,
                gap,
                listPanelX + 14,
                searchShellY,
                0,
                searchShellH,
                listPanelX,
                listY,
                Math.max(80, listW),
                listH,
                margin,
                footerY,
                contentW,
                34
        );
    }

    public static void applyLayout(Screen screen, Layout layout, EditBox searchBox, AbstractSelectionList<?> listWidget, boolean hasSearch) {
        if (searchBox != null && hasSearch) {
            searchBox.visible = false;
            searchBox.setBordered(false);
            searchBox.setTextColor(0xFFE9F1F2);
            searchBox.setTextColorUneditable(0xFF7F8A8E);
            searchBox.setHint(Component.literal("搜索存档"));
            searchBox.setWidth(toScreen(layout, layout.searchShellW() - 20));
            searchBox.setX(toScreen(layout, layout.searchShellX() + 10));
            searchBox.setY(toScreen(layout, layout.searchShellY() + 5));
        }

        if (listWidget != null) {
            // 1.20.1 selection lists paint their own dirt texture over the
            // screen background. Keep the list functional but let the modern
            // selection screen's image background remain visible.
            listWidget.setRenderBackground(false);
            listWidget.setRenderTopAndBottom(false);
            int listTop = toScreen(layout, layout.listY());
            int listBottom = listTop + toScreen(layout, layout.listH());
            listWidget.updateSize(toScreen(layout, layout.listW()), screen.height, listTop, listBottom);
            listWidget.setLeftPos(toScreen(layout, layout.listX()));
        }

        layoutButtons(screen, layout);
    }

    public static void renderBase(GuiGraphics guiGraphics, Screen screen, Layout layout, Kind kind) {
        ensureAnimationStarted(screen);
        UiBackgroundRenderer.renderCyclingBackgroundCrossfade(guiGraphics, screen.width, screen.height, 1.0F);
        guiGraphics.fillGradient(0, 0, screen.width, screen.height, 0x7805080C, 0xA807090D);
        guiGraphics.fill(0, 0, screen.width, screen.height, 0x12000000);

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.scale(layout.scale(), layout.scale(), 1.0F);

        int accent = accent(kind);
        drawAmbientGrid(guiGraphics, layout);
        drawFooterBackdrop(guiGraphics, layout);
        drawFooterBar(guiGraphics, layout, accent);

        poseStack.popPose();
    }

    public static void renderForeground(GuiGraphics guiGraphics, Screen screen, Layout layout, Kind kind,
                                        int itemCount, boolean hasSelection, int mouseX, int mouseY) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.scale(layout.scale(), layout.scale(), 1.0F);

        renderHeader(guiGraphics, screen, layout, kind);
        renderActionButtons(guiGraphics, screen, layout, kind, mouseX, mouseY);

        poseStack.popPose();
    }

    public static void prepareTransparentButtons(Screen screen) {
        for (Button button : collectButtons(screen)) {
            button.setAlpha(0.0F);
        }
    }

    public static void setButtonsVisible(Screen screen, boolean visible) {
        for (Button button : collectButtons(screen)) {
            button.visible = visible;
        }
    }

    private static void layoutButtons(Screen screen, Layout layout) {
        List<Button> buttons = collectButtons(screen);
        if (buttons.isEmpty()) {
            return;
        }

        int gap = 7;
        int available = layout.footerW() - 22;
        int totalW = gap * Math.max(0, buttons.size() - 1);
        int[] widths = new int[buttons.size()];
        for (int i = 0; i < buttons.size(); i++) {
            int desired = Minecraft.getInstance().font.width(buttons.get(i).getMessage().getString()) + 28;
            widths[i] = Math.max(54, Math.min(126, desired));
            totalW += widths[i];
        }
        if (totalW > available && !buttons.isEmpty()) {
            float ratio = available / (float) totalW;
            totalW = gap * Math.max(0, buttons.size() - 1);
            for (int i = 0; i < buttons.size(); i++) {
                widths[i] = Math.max(46, Math.round(widths[i] * ratio));
                totalW += widths[i];
            }
        }

        int x = layout.footerX() + layout.footerW() - totalW - 11;
        int y = layout.footerY() + 8;

        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            button.setX(toScreen(layout, x));
            button.setY(toScreen(layout, y));
            button.setWidth(toScreen(layout, widths[i]));
            button.setHeight(toScreen(layout, 20));
            button.setAlpha(0.0F);
            x += widths[i] + gap;
        }
    }

    private static List<Button> collectButtons(Screen screen) {
        List<Button> buttons = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button) {
                buttons.add(button);
            }
        }
        return buttons;
    }

    private static void renderHeader(GuiGraphics guiGraphics, Screen screen, Layout layout, Kind kind) {
        float progress = introProgress(screen, 0L);
        int alpha = Math.round(255.0F * progress);
        int offsetY = Math.round((1.0F - progress) * -8.0F);
        int accent = accent(kind);
        String title = kind == Kind.WORLDS ? "世界档案" : "服务器档案";
        String subtitle = kind == Kind.WORLDS ? "继续旅程、创建新世界、管理备份" : "选择服务器、直连地址、刷新局域网";

        drawScaledText(guiGraphics, screen, title, layout.margin(), layout.headerY() + offsetY, 1.72F,
                multiplyAlpha(0xFFF2F5F5, alpha), false);
        guiGraphics.drawString(screen.getMinecraft().font, subtitle, layout.margin() + 2, layout.headerY() + 24 + offsetY,
                multiplyAlpha(0xFF9EA8AA, alpha), false);
        guiGraphics.fill(layout.margin(), layout.headerY() + 38 + offsetY, layout.margin() + 112, layout.headerY() + 40 + offsetY,
                withAlpha(accent, Math.round(190.0F * progress)));
        guiGraphics.fill(layout.margin() + 118, layout.headerY() + 39 + offsetY, layout.margin() + 230, layout.headerY() + 40 + offsetY,
                withAlpha(0xFFFFFFFF, Math.round(82.0F * progress)));

        String corner = kind == Kind.WORLDS ? "LOCAL ARCHIVE" : "NETWORK ARCHIVE";
        int cornerW = screen.getMinecraft().font.width(corner);
        guiGraphics.drawString(screen.getMinecraft().font, corner, layout.virtualW() - layout.margin() - cornerW,
                layout.headerY() + 5 + offsetY, multiplyAlpha(0xFF7E8A8E, alpha), false);
    }

    private static void renderListCaption(GuiGraphics guiGraphics, Layout layout, Kind kind, int itemCount) {
        Minecraft minecraft = Minecraft.getInstance();
        String label = kind == Kind.WORLDS ? "存档列表" : "服务器列表";
        String count = itemCount <= 0 ? "扫描中" : itemCount + " 项";
        int y = layout.panelY() + (kind == Kind.WORLDS ? 42 : 10);
        guiGraphics.drawString(minecraft.font, label, layout.listPanelX() + 14, y, 0xFFE2E7E8, false);
        guiGraphics.drawString(minecraft.font, count,
                layout.listPanelX() + layout.listPanelW() - 14 - minecraft.font.width(count), y,
                0xFF829099, false);
    }

    private static void renderActionButtons(GuiGraphics guiGraphics, Screen screen, Layout layout, Kind kind, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        int accent = accent(kind);
        float progress = introProgress(screen, 150L);
        int alpha = Math.round(255.0F * progress);
        int offsetY = Math.round((1.0F - progress) * 10.0F);

        for (Button button : collectButtons(screen)) {
            int x = Math.round(button.getX() / layout.scale());
            int y = Math.round(button.getY() / layout.scale()) + offsetY;
            int w = Math.round(button.getWidth() / layout.scale());
            int h = Math.round(button.getHeight() / layout.scale());
            boolean hovered = button.active
                    && mouseX >= button.getX()
                    && mouseY >= button.getY()
                    && mouseX < button.getX() + button.getWidth()
                    && mouseY < button.getY() + button.getHeight();
            boolean active = button.active;
            int textColor = multiplyAlpha(active ? (hovered ? 0xFFFFFFFF : 0xFFD6DDDE) : 0xFF6F7477, alpha);
            int lineColor = active ? (hovered ? accent : 0xFF555F64) : 0xFF3C4144;

            if (hovered && active) {
                drawPixelCutRect(guiGraphics, x, y, w, h, withAlpha(accent, Math.round(34.0F * progress)));
            }
            guiGraphics.fill(x + 7, y + h - 3, x + w - 7, y + h - 2, withAlpha(0xFF353A3D, Math.round(102.0F * progress)));
            guiGraphics.fill(x + 11, y + h - 3, x + w - 11, y + h - 1,
                    withAlpha(lineColor, Math.round((hovered && active ? 220.0F : 120.0F) * progress)));

            String label = trim(button.getMessage().getString(), Math.max(8, w - 8));
            int tx = x + w / 2 - minecraft.font.width(label) / 2;
            guiGraphics.drawString(minecraft.font, label, tx, y + 6, textColor, false);
        }
    }

    private static void drawFooterBar(GuiGraphics guiGraphics, Layout layout, int accent) {
        int x = layout.footerX();
        int y = layout.footerY();
        int w = layout.footerW();
        guiGraphics.fill(x + 16, y + 1, x + w - 16, y + 2, withAlpha(accent, 118));
    }

    private static void drawFooterBackdrop(GuiGraphics guiGraphics, Layout layout) {
        int y = Math.max(layout.footerY() - 7, layout.panelY() + layout.panelH() - 4);
        guiGraphics.fill(0, y, layout.virtualW(), layout.virtualH(), 0x68000000);
        guiGraphics.fill(0, y - 7, layout.virtualW(), y, 0x26000000);
    }

    private static void drawSearchShell(GuiGraphics guiGraphics, Layout layout) {
        int x = layout.searchShellX();
        int y = layout.searchShellY();
        int w = layout.searchShellW();
        int h = layout.searchShellH();

        guiGraphics.fill(x + 8, y + h - 3, x + w - 8, y + h - 2, 0x34FFFFFF);
        guiGraphics.fill(x + 8, y + h - 3, x + 48, y + h - 1, 0x8A90C6D8);
    }

    private static void drawPanel(GuiGraphics guiGraphics, int x, int y, int w, int h, int color, int accent) {
        guiGraphics.fill(x, y, x + w, y + h, color);
        guiGraphics.fill(x + 16, y, x + w - 16, y + 1, 0x1EFFFFFF);
        guiGraphics.fill(x + 16, y + h - 1, x + w - 16, y + h, 0x30000000);
    }

    private static void drawAmbientGrid(GuiGraphics guiGraphics, Layout layout) {
        int alpha = 6;
        for (int x = layout.margin(); x < layout.virtualW() - layout.margin(); x += 34) {
            guiGraphics.fill(x, 0, x + 1, layout.virtualH(), withAlpha(0xFFFFFFFF, alpha));
        }
        for (int y = 0; y < layout.virtualH(); y += 28) {
            guiGraphics.fill(0, y, layout.virtualW(), y + 1, withAlpha(0xFFFFFFFF, 4));
        }
    }

    public static void drawModernSelection(GuiGraphics guiGraphics, int top, int listX, int listW, int height) {
        int x = listX + 9;
        int w = Math.max(0, listW - 18);

        guiGraphics.fill(x + 1, top - 1, x + 4, top + height + 1, 0xB07FB8C6);
        guiGraphics.fill(x + 8, top + height, x + w - 8, top + height + 1, 0x4A8FB8C6);
    }

    public static void renderWorldEntry(GuiGraphics guiGraphics, LevelSummary summary, ResourceLocation iconTexture,
                                        int index, int top, int left, int width, int height, boolean hovering) {
        Minecraft minecraft = Minecraft.getInstance();
        float progress = entryProgress(index);
        int alpha = Math.round(255.0F * progress);
        int slideX = Math.round((1.0F - progress) * -14.0F);
        int cardX = left - 8 + slideX;
        int cardY = top;
        int cardW = width + 16;
        int cardH = Math.max(34, height - 2);
        int accentColor = isWorldPrimaryActionActive(summary) ? 0xFF83C8D8 : 0xFF757B80;

        drawPixelCutRect(guiGraphics, cardX, cardY, cardW, cardH,
                withAlpha(hovering ? 0xFF0C151B : 0xFF071016, Math.round((hovering ? 144.0F : 74.0F) * progress)));
        if (hovering) {
            drawPixelCutRect(guiGraphics, cardX, cardY, cardW, cardH, withAlpha(0xFF101C24, Math.round(58.0F * progress)));
        }
        guiGraphics.fill(cardX + 2, cardY + 2, cardX + 4, cardY + cardH - 2,
                withAlpha(accentColor, Math.round((hovering ? 190.0F : 106.0F) * progress)));
        guiGraphics.fill(cardX + 12, cardY + cardH - 1, cardX + cardW - 12, cardY + cardH,
                withAlpha(accentColor, Math.round((hovering ? 72.0F : 32.0F) * progress)));

        int iconX = cardX + 9;
        int iconY = top + Math.max(1, (cardH - 32) / 2);
        guiGraphics.fill(iconX - 1, iconY - 1, iconX + 33, iconY + 33, withAlpha(0xFF000000, Math.round(118.0F * progress)));
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        guiGraphics.blit(iconTexture, iconX, iconY, 0.0F, 0.0F, 32, 32, 32, 32);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        if (!isWorldPrimaryActionActive(summary)) {
            guiGraphics.fill(iconX, iconY, iconX + 32, iconY + 32, withAlpha(0xFF000000, Math.round(144.0F * progress)));
        }

        String name = summary.getLevelName();
        if (name == null || name.isBlank()) {
            name = "世界 " + (index + 1);
        }
        String dateText = summary.getLastPlayed() == -1L ? "未记录时间" : WORLD_TIME_FORMAT.format(Instant.ofEpochMilli(summary.getLastPlayed()));
        String meta = summary.getLevelId() + "  /  " + dateText;
        String info = summary.getInfo().getString();

        int textX = iconX + 42;
        int maxTextW = Math.max(40, cardW - 180);
        drawEntryText(guiGraphics, trimScaled(name, maxTextW), textX, cardY + 4,
                multiplyAlpha(isWorldPrimaryActionActive(summary) ? 0xFFF0F5F6 : 0xFF8A8F92, alpha), false);
        drawEntryText(guiGraphics, trimScaled(meta, maxTextW), textX, cardY + 18, multiplyAlpha(0xFF9BA5A8, alpha), false);
        drawEntryText(guiGraphics, trimScaled(info, maxTextW), textX, cardY + 32, multiplyAlpha(0xFF737E82, alpha), false);

    }

    public static void renderOnlineServerEntry(GuiGraphics guiGraphics, ServerData serverData, ResourceLocation iconTexture,
                                               int index, int top, int left, int width, int height, boolean hovering) {
        String name = serverData.name == null || serverData.name.isBlank() ? serverData.ip : serverData.name;
        String motd = serverData.motd == null ? "" : serverData.motd.getString();
        String status = getServerStatus(serverData);
        int statusColor = getServerStatusColor(serverData);
        renderServerEntry(guiGraphics, iconTexture, name, motd, serverData.ip, status, statusColor,
                index, top, left, width, height, hovering, true);
    }

    public static void renderLanServerEntry(GuiGraphics guiGraphics, LanServer serverData,
                                            int index, int top, int left, int width, int height, boolean hovering,
                                            boolean hideAddress) {
        String address = hideAddress ? "地址已隐藏" : serverData.getAddress();
        renderServerEntry(guiGraphics, null, "局域网世界", serverData.getMotd(), address, "LAN", 0xFF7EC28F,
                index, top, left, width, height, hovering, false);
    }

    public static void renderLanHeader(GuiGraphics guiGraphics, int top, int left, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        int y = top + Math.max(2, height / 2 - 5);
        String label = "正在扫描局域网世界";
        guiGraphics.fill(left + 12, y + 4, left + width / 2 - 62, y + 5, 0x327EC28F);
        guiGraphics.drawString(minecraft.font, label, left + width / 2 - minecraft.font.width(label) / 2, y, 0xFF9FAAAC, false);
        guiGraphics.fill(left + width / 2 + 62, y + 4, left + width - 12, y + 5, 0x327EC28F);
    }

    private static void renderServerEntry(GuiGraphics guiGraphics, ResourceLocation iconTexture, String name, String primary,
                                          String secondary, String status, int statusColor,
                                          int index, int top, int left, int width, int height, boolean hovering, boolean drawIconTexture) {
        Minecraft minecraft = Minecraft.getInstance();
        float progress = entryProgress(index);
        int alpha = Math.round(255.0F * progress);
        int slideX = Math.round((1.0F - progress) * -14.0F);
        int cardX = left - 8 + slideX;
        int cardY = top;
        int cardW = width + 16;
        int cardH = Math.max(34, height - 2);

        drawPixelCutRect(guiGraphics, cardX, cardY, cardW, cardH,
                withAlpha(hovering ? 0xFF0C151B : 0xFF071016, Math.round((hovering ? 208.0F : 184.0F) * progress)));
        guiGraphics.fill(cardX + 2, cardY + 2, cardX + 4, cardY + cardH - 2,
                withAlpha(statusColor, Math.round((hovering ? 190.0F : 106.0F) * progress)));
        guiGraphics.fill(cardX + 12, cardY + cardH - 1, cardX + cardW - 12, cardY + cardH,
                withAlpha(statusColor, Math.round((hovering ? 72.0F : 32.0F) * progress)));

        int iconX = cardX + 9;
        int iconY = top + Math.max(1, (cardH - 32) / 2);
        guiGraphics.fill(iconX - 1, iconY - 1, iconX + 33, iconY + 33, withAlpha(0xFF000000, Math.round(124.0F * progress)));
        if (drawIconTexture && iconTexture != null) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            guiGraphics.blit(iconTexture, iconX, iconY, 0.0F, 0.0F, 32, 32, 32, 32);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        } else {
            drawLanGlyph(guiGraphics, iconX, iconY, statusColor);
        }

        int textX = iconX + 42;
        int maxTextW = Math.max(40, cardW - 194);
        drawEntryText(guiGraphics, trimScaled(name, maxTextW), textX, cardY + 4, multiplyAlpha(0xFFF0F5F6, alpha), false);
        drawEntryText(guiGraphics, trimScaled(primary, maxTextW), textX, cardY + 18, multiplyAlpha(0xFF9BA5A8, alpha), false);
        drawEntryText(guiGraphics, trimScaled(secondary, maxTextW), textX, cardY + 32, multiplyAlpha(0xFF737E82, alpha), false);

        int statusW = Math.max(28, Math.round(minecraft.font.width(status) * ENTRY_TEXT_SCALE));
        int statusX = cardX + cardW - statusW - 10;
        int statusY = cardY + cardH / 2 - 4;
        guiGraphics.fill(statusX - 7, statusY - 3, statusX - 5, statusY + 11,
                withAlpha(statusColor, Math.round(132.0F * progress)));
        drawEntryText(guiGraphics, status, statusX, statusY - 1, multiplyAlpha(0xFFDDE5E6, alpha), false);
    }

    private static void drawLanGlyph(GuiGraphics guiGraphics, int x, int y, int color) {
        guiGraphics.fill(x + 8, y + 21, x + 24, y + 24, withAlpha(color, 158));
        guiGraphics.fill(x + 10, y + 15, x + 22, y + 18, withAlpha(color, 124));
        guiGraphics.fill(x + 13, y + 9, x + 19, y + 12, withAlpha(color, 92));
        guiGraphics.fill(x + 15, y + 5, x + 17, y + 7, 0xFFEAF5F0);
        guiGraphics.fill(x + 15, y + 20, x + 17, y + 25, 0xFFEAF5F0);
    }

    private static String getServerStatus(ServerData serverData) {
        if (!serverData.pinged) {
            return "等待";
        }
        if (serverData.ping == -2L) {
            return "连接中";
        }
        if (serverData.ping < 0L) {
            return "离线";
        }
        if (serverData.protocol != net.minecraft.SharedConstants.getCurrentVersion().getProtocolVersion()) {
            return "版本";
        }
        String text = serverData.status == null ? "" : serverData.status.getString();
        return text == null || text.isBlank() ? "在线" : text;
    }

    private static int getServerStatusColor(ServerData serverData) {
        if (!serverData.pinged || serverData.ping == -2L) {
            return 0xFF9BA5A8;
        }
        if (serverData.ping < 0L || serverData.protocol != net.minecraft.SharedConstants.getCurrentVersion().getProtocolVersion()) {
            return 0xFFD16862;
        }
        return 0xFF7EC28F;
    }

    private static String getWorldStatus(LevelSummary summary) {
        if (summary.isLocked()) {
            return "锁定";
        }
        if (!summary.isCompatible()) {
            return "版本";
        }
        if (summary.requiresManualConversion()) {
            return "转换";
        }
        if (summary.backupStatus().shouldBackup()) {
            return "备份";
        }
        return "可进入";
    }

    private static int getWorldStatusColor(LevelSummary summary) {
        if (summary.isLocked() || !summary.isCompatible() || summary.requiresManualConversion()) {
            return 0xFFD16862;
        }
        if (summary.backupStatus().shouldBackup()) {
            return 0xFFD0A45F;
        }
        return 0xFF83C8D8;
    }

    private static boolean isWorldPrimaryActionActive(LevelSummary summary) {
        return !summary.isDisabled();
    }

    private static int accent(Kind kind) {
        return kind == Kind.WORLDS ? 0xFF83C8D8 : 0xFF7EC28F;
    }

    private static int toScreen(Layout layout, int value) {
        return Math.round(value * layout.scale());
    }

    private static int withAlpha(int color, int alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (clampedAlpha << 24);
    }

    private static int multiplyAlpha(int color, int alpha) {
        int sourceAlpha = (color >>> 24) & 255;
        int finalAlpha = Math.max(0, Math.min(255, Math.round(sourceAlpha * (Math.max(0, Math.min(255, alpha)) / 255.0F))));
        return (color & 0x00FFFFFF) | (finalAlpha << 24);
    }

    private static void ensureAnimationStarted(Screen screen) {
        if (screen != null && !OPEN_TIMES.containsKey(screen)) {
            OPEN_TIMES.put(screen, System.currentTimeMillis());
        }
    }

    private static float introProgress(Screen screen, long delayMs) {
        if (screen == null) {
            return 1.0F;
        }
        long openedAt = OPEN_TIMES.computeIfAbsent(screen, ignored -> System.currentTimeMillis());
        float raw = (System.currentTimeMillis() - openedAt - delayMs) / (float) INTRO_MS;
        return easeOutCubic(raw);
    }

    private static float entryProgress(int index) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            return 1.0F;
        }
        long openedAt = OPEN_TIMES.computeIfAbsent(screen, ignored -> System.currentTimeMillis());
        long delay = 120L + Math.max(0, Math.min(index, 10)) * ENTRY_STAGGER_MS;
        float raw = (System.currentTimeMillis() - openedAt - delay) / (float) INTRO_MS;
        return easeOutCubic(raw);
    }

    private static float easeOutCubic(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        float inverted = 1.0F - t;
        return 1.0F - inverted * inverted * inverted;
    }

    private static String trim(String text, int maxWidth) {
        Minecraft minecraft = Minecraft.getInstance();
        if (text == null || minecraft.font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        return minecraft.font.plainSubstrByWidth(text, Math.max(0, maxWidth - minecraft.font.width(ellipsis))) + ellipsis;
    }

    private static String trimScaled(String text, int maxWidth) {
        return trim(text, Math.round(maxWidth / ENTRY_TEXT_SCALE));
    }

    private static void drawEntryText(GuiGraphics guiGraphics, String text, int x, int y, int color, boolean shadow) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.scale(ENTRY_TEXT_SCALE, ENTRY_TEXT_SCALE, 1.0F);
        int sx = Math.round(x / ENTRY_TEXT_SCALE);
        int sy = Math.round(y / ENTRY_TEXT_SCALE);
        guiGraphics.drawString(Minecraft.getInstance().font, text, sx, sy, color, shadow);
        poseStack.popPose();
    }

    private static void drawScaledText(GuiGraphics guiGraphics, Screen screen, String text, int x, int y, float scale, int color, boolean shadow) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.scale(scale, scale, 1.0F);
        int sx = Math.round(x / scale);
        int sy = Math.round(y / scale);
        guiGraphics.drawString(screen.getMinecraft().font, text, sx, sy, color, shadow);
        poseStack.popPose();
    }

    private static void drawPixelCutRect(GuiGraphics guiGraphics, int x, int y, int w, int h, int color) {
        if ((color >>> 24) == 0 || w <= 0 || h <= 0) {
            return;
        }
        if (w <= 2 || h <= 2) {
            guiGraphics.fill(x, y, x + w, y + h, color);
            return;
        }
        guiGraphics.fill(x + 1, y, x + w - 1, y + h, color);
        guiGraphics.fill(x, y + 1, x + w, y + h - 1, color);
    }

    public record Layout(
            float scale,
            int virtualW,
            int virtualH,
            int margin,
            int headerY,
            int panelY,
            int panelH,
            int listPanelX,
            int listPanelW,
            int detailX,
            int detailW,
            int gap,
            int searchShellX,
            int searchShellY,
            int searchShellW,
            int searchShellH,
            int listX,
            int listY,
            int listW,
            int listH,
            int footerX,
            int footerY,
            int footerW,
            int footerH
    ) {
    }
}
