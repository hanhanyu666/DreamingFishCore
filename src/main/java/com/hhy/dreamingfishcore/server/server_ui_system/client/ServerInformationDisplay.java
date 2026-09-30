package com.hhy.dreamingfishcore.server.server_ui_system.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.components.UiPanelRenderer;
import com.hhy.dreamingfishcore.client.ui.render.PlayerFaceBatchRenderer;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.server_ui_system.network.Packet_OnlinePlayerCountRequest;
import com.hhy.dreamingfishcore.gameplay.playerlevel_system.overalllevel.PlayerLevelManager;
import com.hhy.dreamingfishcore.gameplay.npc_system.client.ui.screen.Screen_NpcDialogue;
import com.hhy.dreamingfishcore.server.title_system.PlayerTitleManager;
import com.hhy.dreamingfishcore.server.title_system.Title;
import com.hhy.dreamingfishcore.server.title_system.TitleRegistry;
import com.hhy.dreamingfishcore.server.rank_system.PlayerRankManager;
import com.hhy.dreamingfishcore.server.rank_system.Rank;
import com.hhy.dreamingfishcore.server.server_ui_system.client.SystemMessageDisplay;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;


@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public class ServerInformationDisplay {
    private static boolean SHOW_UI = true;                  // UI开关
    private static final boolean USE_LEGACY_INFO_BOXES = false;
    private static final String SERVER_NAME_DREAMING = "Dreaming";
    private static final String SERVER_NAME_FISH = "fish";
    private static final String ONLINE_SUFFIX = " 在线";
    private static final String TPS_ICON = "⚡";
    private static final String UNKNOWN_TIME_TEXT = "未知";
    private static final int COMPACT_INFO_RADIUS = 4;
    private static final float MAX_TPS = 10_000.0F;
    private static final int BOX_PADDING = 8;              // 框内边距
    private static final int BOX_SPACING = 3;              // 框之间间距
    private static final int RIGHT_OFFSET = 2;             // 右侧偏移
    private static final int TOP_OFFSET = 3;               // 顶部偏移
    private static final int LEFT_OFFSET = 2;              // 左侧偏移
    private static final int BOTTOM_OFFSET = 2;            // 底部偏移
    private static final int BOX_HEIGHT = 12;              // 框高度
    private static final int INFO_BOX_TEXT_PADDING = 5;    // 文字左右内边距
    private static final float INFO_TEXT_SCALE = 0.82f;    // 文字缩放比例
    private static final int PROGRESS_BAR_HEIGHT = 5;      // 进度条高度

    // 客户端缓存数据（从网络包获取）
    public static int ONLINE_PLAYERS = 0;

    private static long LAST_SERVER_STATUS_UPDATE = 0;     // 服务器状态最后刷新时间
    // NeoForge 的 TPS 统计基于最近 100 个 tick；低频复用在线人数请求，
    // 每次刷新只产生一个很小的请求/响应，不在渲染帧或服务端逐 tick 运行。
    private static final long UPDATE_INTERVAL = 5_000L;

    // 性能优化：缓存RGB颜色值
    private static int CACHED_DYNAMIC_COLOR = 0xFFDDAA55;
    private static long LAST_COLOR_UPDATE = 0;
    private static final long COLOR_UPDATE_INTERVAL = 100; // 100ms更新一次颜色
    /** Latest server-authoritative sample, or NaN until a sample is received. */
    private static float CACHED_SERVER_TPS = Float.NaN;
    private static long LAST_TPS_UPDATE = Long.MIN_VALUE;
    // 留出一次网络刷新延迟，避免 5 秒轮询周期内短暂显示为未知。
    private static final long TPS_STALE_INTERVAL = UPDATE_INTERVAL * 2L;
    private static String CACHED_GAME_TIME = UNKNOWN_TIME_TEXT;
    private static long LAST_GAME_TIME_UPDATE = Long.MIN_VALUE;
    private static final long GAME_TIME_CACHE_INTERVAL = 1000L;
    private static float LAST_FORMATTED_TPS = Float.NaN;
    private static String CACHED_TPS_TEXT = TPS_ICON + "--";
    private static String CACHED_TPS_VALUE_TEXT = "--";

    // 获取当前玩家UUID
    public static UUID getCurrentPlayerUUID() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getUUID() : null;
    }

    @SubscribeEvent
    public static void onClientLoginToServer(ClientPlayerNetworkEvent.LoggingIn event) {
        resetTpsCache();
        LAST_SERVER_STATUS_UPDATE = 0L;
        TerminalLensOverlay.invalidate();

        // 单人游戏和多人游戏默认显示右上角信息面板，保留O键手动开关
        SHOW_UI = true;
        System.out.println("玩家进服：默认显示信息面板");

        // [已禁用] 进服“按 O 关闭信息面板”提示：O 键功能已禁用，不再发这条提示。
        // if (mc.isSingleplayer() && mc.player != null) {
        //     mc.player.sendSystemMessage(Component.literal("§e[DreamingfishCore]§f信息面板默认显示，可以按§6O§f临时关闭"));
        // }
    }

    /** Rebuild retained font/panel geometry after client resources change. */
    public static void invalidateCompactRenderCache() {
        TerminalLensOverlay.invalidate();
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetTpsCache();
        LAST_SERVER_STATUS_UPDATE = 0L;
        TerminalLensOverlay.invalidate();
    }

    //客户端Tick，触发网络请求 =====================
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long currentTime = System.currentTimeMillis();

        //请求在线玩家数
        if (currentTime - LAST_SERVER_STATUS_UPDATE > UPDATE_INTERVAL) {
            DreamingFishCore_NetworkManager.sendToServer(new Packet_OnlinePlayerCountRequest());
            LAST_SERVER_STATUS_UPDATE = currentTime;
        }

    }

    // HUD渲染（左上角小框 + 右上角玩家信息）
    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (!shouldRenderInformationHud(mc)) return;

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int playerLevel = PlayerLevelManager.getPlayerLevelClient(mc.player);
        List<com.hhy.dreamingfishcore.client.ui.notification.NotificationManager.ActiveNotification>
                systemMessages = SystemMessageDisplay.getActiveMessages();

        if (USE_LEGACY_INFO_BOXES) {
            // Legacy mode is kept as a compatibility fallback and still uses
            // the ordinary managed GUI buffer.
            guiGraphics.drawManaged(() -> {
                PoseStack poseStack = guiGraphics.pose();
                poseStack.pushPose();
                List<InfoBox> leftBoxes = new ArrayList<>();
                leftBoxes.add(new InfoBox(Component.literal("§7" + SERVER_NAME_DREAMING + SERVER_NAME_FISH),
                        0xFF666666, 0xDD151520));
                leftBoxes.add(new InfoBox(Component.literal("§7" + ONLINE_PLAYERS + ONLINE_SUFFIX),
                        0xFF666666, 0xDD151520));
                leftBoxes.add(new InfoBox(Component.literal("§7" + getGameTimeString(mc)),
                        0xFF666666, 0xDD151520));
                renderLeftBoxes(guiGraphics, font, leftBoxes);
                Rank playerRank = PlayerRankManager.getPlayerRankClient(mc.player);
                int[] anchor = renderPlayerInfo(guiGraphics, font, screenWidth, screenHeight, mc,
                        playerRank.getRankName(),
                        PlayerTitleManager.getPlayerTitleClient(mc.player).getTitleName(), playerLevel);
                SystemMessageDisplay.renderSystemMessages(guiGraphics, font, screenWidth,
                        anchor[0], anchor[1], systemMessages);
                poseStack.popPose();
            });
            return;
        }

        Rank playerRank = PlayerRankManager.getPlayerRankClient(mc.player);
        TerminalLensOverlay.Anchor anchor = TerminalLensOverlay.render(
                guiGraphics, mc, font, playerLevel, playerRank);
        if (!systemMessages.isEmpty()) {
            guiGraphics.drawManaged(() -> SystemMessageDisplay.renderSystemMessages(
                    guiGraphics, font, screenWidth, anchor.y(), anchor.height(), systemMessages));
        }
    }

    @SubscribeEvent
    public static void replaceVanillaEffects(RenderGuiLayerEvent.Pre event) {
        if (VanillaGuiLayers.EFFECTS.equals(event.getName())
                && !USE_LEGACY_INFO_BOXES
                && shouldRenderInformationHud(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    private static boolean shouldRenderInformationHud(Minecraft mc) {
        return SHOW_UI
                && !mc.isPaused()
                && (mc.screen == null || mc.screen instanceof Screen_NpcDialogue)
                && mc.player != null
                && !mc.options.hideGui
                && !mc.getDebugOverlay().showDebugScreen();
    }

    /**
     * Receives the server-authoritative sample used by all connected clients.
     * Packet handlers call this on the client thread.
     */
    public static void updateServerTps(float tps) {
        if (!Float.isFinite(tps)) {
            return;
        }

        CACHED_SERVER_TPS = Mth.clamp(tps, 0.0F, MAX_TPS);
        LAST_TPS_UPDATE = System.currentTimeMillis();
    }

    /**
     * Returns the latest server-authoritative TPS sample.  Both integrated and
     * remote servers use the same response packet, so the render path never
     * re-calculates tick timings or falls back to a hard-coded 20 TPS value.
     */
    public static float getClientTps(Minecraft mc) {
        if (mc == null || mc.player == null) {
            return Float.NaN;
        }

        long currentTime = System.currentTimeMillis();
        if (Float.isFinite(CACHED_SERVER_TPS)
                && LAST_TPS_UPDATE != Long.MIN_VALUE
                && currentTime - LAST_TPS_UPDATE <= TPS_STALE_INTERVAL) {
            return CACHED_SERVER_TPS;
        }

        return Float.NaN;
    }

    /**
     * Format the cached sample only when its value changes.  Rendering reuses
     * the resulting immutable strings instead of allocating once per frame.
     */
    public static String getClientTpsText(Minecraft mc) {
        updateTpsTextCache(mc);
        return CACHED_TPS_TEXT;
    }

    /** Numeric text shared by the compact HUD and the terminal status bars. */
    public static String getServerTpsText(Minecraft mc) {
        updateTpsTextCache(mc);
        return CACHED_TPS_VALUE_TEXT;
    }

    private static void updateTpsTextCache(Minecraft mc) {
        float tps = getClientTps(mc);
        if (Float.compare(tps, LAST_FORMATTED_TPS) != 0) {
            CACHED_TPS_VALUE_TEXT = formatTps(tps);
            CACHED_TPS_TEXT = TPS_ICON + CACHED_TPS_VALUE_TEXT;
            LAST_FORMATTED_TPS = tps;
        }
    }

    private static String formatTps(float tps) {
        return Float.isFinite(tps)
                ? String.format(Locale.ROOT, "%.1f", tps)
                : "--";
    }

    private static void resetTpsCache() {
        CACHED_SERVER_TPS = Float.NaN;
        LAST_TPS_UPDATE = Long.MIN_VALUE;
        LAST_FORMATTED_TPS = Float.NaN;
        CACHED_TPS_TEXT = TPS_ICON + "--";
        CACHED_TPS_VALUE_TEXT = "--";
    }

    // 渲染左上角小框（水平排列）
    private static void renderLeftBoxes(GuiGraphics guiGraphics, Font font, List<InfoBox> boxes) {
        int totalWidth = 0;
        for (InfoBox box : boxes) {
            box.textWidth = font.width(box.text);
            int scaledTextWidth = (int)(box.textWidth * INFO_TEXT_SCALE);
            box.boxWidth = scaledTextWidth + INFO_BOX_TEXT_PADDING * 2;
            totalWidth += box.boxWidth;
        }
        totalWidth += (boxes.size() - 1) * BOX_SPACING;

        // 左上角起始坐标
        int currentX = TOP_OFFSET;
        int baseY = TOP_OFFSET;

        // 渲染所有小框
        for (InfoBox box : boxes) {
            renderEnhancedSmallBox(guiGraphics, font, currentX, baseY, box);
            currentX += box.boxWidth + BOX_SPACING;
        }

    }

    // 渲染右上角玩家信息框
    private static int[] renderPlayerInfo(GuiGraphics guiGraphics, Font font, int screenWidth, int screenHeight,
                                         Minecraft mc, String rankId, String titleName, int playerLevel) {
        // 计算文本宽度
        String nameText = mc.player.getName().getString();
        String levelText = "Lv." + playerLevel;
        long currentExp = PlayerLevelManager.getPlayerExperienceClient(mc.player);
        long nextLevelExp = PlayerLevelManager.getExperienceNeededForNextLevelClient(mc.player);
        float expProgress = PlayerLevelManager.getExperienceProgressClient(mc.player);

        // 获取颜色
        Title titleObj = TitleRegistry.getTitleByName(titleName);
        int titleColor = titleObj != null ? titleObj.getColor() : 0xFFAAAAAA;
        int rankColor = getRankColorByName(rankId);

        // ========== 头像和布局配置 ==========
        int lineHeight = font.lineHeight;
        int spacing = 3; // 行间距（增加到3像素，让昵称和rank之间更宽松）
        int avatarSize = lineHeight * 2 + 4; // 头像高度 = 前两行高度 + 额外4像素（稍微大一点）
        int avatarSpacing = 3; // 头像和右侧文字的间距

        // emoji（已移除皇冠图标）

        // 计算各部分宽度
        int nameWidth = font.width(nameText);
        int levelWidth = font.width(levelText);
        int rankTextWidth = font.width(rankId);
        int titleWidth = font.width(titleName);

        // 框的尺寸
        int padding = 4; // 框的内边距（四边统一为4像素，更紧凑）
        int elementSpacing = 5; // 元素之间的间距（增大间距，显得不拥挤）

        // 进度条到框四边的间距（统一）
        int progressBarMargin = 4; // 进度条上下左右到框边缘的间距，统一为4像素

        // 第1行宽度：头像 + 头像间距 + 等级 + 间距 + 昵称
        int line1Width = avatarSize + avatarSpacing + levelWidth + elementSpacing + nameWidth;
        // 第2行宽度：头像 + 头像间距 + rank + 间距 + 称号
        int line2Width = avatarSize + avatarSpacing + rankTextWidth + elementSpacing + titleWidth;

        int boxWidth = Math.max(line1Width, line2Width) + padding * 2;
        int boxHeight = padding + lineHeight * 2 + spacing + progressBarMargin + PROGRESS_BAR_HEIGHT + padding;

        // 框的位置（右上角）
        int boxX = screenWidth - boxWidth - RIGHT_OFFSET;
        int boxY = TOP_OFFSET;

        // ========== 背景和边框 ==========
        int bgColor = 0xD0181818;
        int dynamicColor = getDynamicBorderColor();
        int glowColor = 0x30000000 | (dynamicColor & 0x00FFFFFF);
        UiPanelRenderer.smoothRoundedRectBatched(guiGraphics, boxX - 1, boxY - 1,
                boxWidth + 2, boxHeight + 2, 5, glowColor, 0);
        UiPanelRenderer.smoothRoundedRectBatched(guiGraphics, boxX, boxY,
                boxWidth, boxHeight, 4, bgColor, dynamicColor);

        // ========== 左侧：玩家头像（覆盖前两行） ==========
        int avatarX = boxX + padding;
        int avatarY = boxY + padding;

        // 渲染头像（覆盖前两行）
        guiGraphics.flush();
        PlayerFaceBatchRenderer.drawOne(guiGraphics, mc.player.getSkin().texture(),
                avatarX, avatarY, avatarSize, 1.0F);

        // ========== 右侧内容区域 ==========
        int contentX = avatarX + avatarSize + avatarSpacing;
        int line1Y = boxY + padding; // 第1行Y坐标
        int line2Y = line1Y + lineHeight + spacing; // 第2行Y坐标

        // ========== 第1行：等级 + 间距 + 玩家昵称 ==========
        int currentX = contentX;

        // 等级（金色）
        guiGraphics.drawString(font, Component.literal(levelText), currentX, line1Y, 0xFFCC8800);
        currentX += levelWidth + elementSpacing;

        // 昵称（黄色）
        guiGraphics.drawString(font, Component.literal(nameText), currentX, line1Y, 0xFFFFAA);

        // ========== 第2行：rank + 间距 + 称号 ==========
        currentX = contentX;

        // Rank（彩色rank）
        guiGraphics.drawString(font, Component.literal(rankId), currentX, line2Y, rankColor);
        currentX += rankTextWidth + elementSpacing;

        // 称号（彩色）
        guiGraphics.drawString(font, Component.literal(titleName), currentX, line2Y, titleColor);

        // ========== 第3行：全宽进度条（跟随框变色） ==========
        int progressBarY = line2Y + lineHeight + progressBarMargin; // 进度条顶部到第二行底部的距离
        int progressBarX = boxX + progressBarMargin; // 进度条左边到框左边 = progressBarMargin
        int progressBarWidth = boxWidth - progressBarMargin * 2; // 进度条右边到框右边 = progressBarMargin

        int progressGlowColor = 0x40000000 | (dynamicColor & 0x00FFFFFF);
        UiPanelRenderer.smoothRoundedRectBatched(guiGraphics, progressBarX - 1, progressBarY - 1,
                progressBarWidth + 2, PROGRESS_BAR_HEIGHT + 2, 3, progressGlowColor, 0);
        UiPanelRenderer.smoothRoundedRectBatched(guiGraphics, progressBarX, progressBarY,
                progressBarWidth, PROGRESS_BAR_HEIGHT, 2, 0xDD1A1A1A, dynamicColor);

        // 进度条前景（使用动态RGB颜色）
        int progressWidth = (int)(progressBarWidth * expProgress);
        if (progressWidth > 2) {
            UiPanelRenderer.smoothRoundedRectBatched(guiGraphics, progressBarX + 1, progressBarY + 1,
                    progressWidth - 2, PROGRESS_BAR_HEIGHT - 2, 1, dynamicColor, 0);
            guiGraphics.fill(RenderType.gui(), progressBarX + 1, progressBarY + 1,
                progressBarX + progressWidth - 1, progressBarY + 2, 0xFFFFFFFF);
        }

        // 返回玩家信息框的位置信息（Y坐标和高度）供系统消息使用
        return new int[]{boxY, boxHeight};
    }

    // 渲染增强版小框（带发光背景，无边框线）
    private static void renderEnhancedSmallBox(GuiGraphics guiGraphics, Font font, int x, int y, InfoBox box) {
        int boxHeight = BOX_HEIGHT;

        // 圆角背景
        int radius = COMPACT_INFO_RADIUS;
        drawRoundedRect(guiGraphics, x, y, box.boxWidth, boxHeight, radius, box.backgroundColor);

        // 文本居中渲染（应用缩放）
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        float scaledTextWidth = box.textWidth * INFO_TEXT_SCALE;
        float scaledTextHeight = font.lineHeight * INFO_TEXT_SCALE;

        int textX = x + (box.boxWidth - (int)scaledTextWidth) / 2;
        int textY = y + (boxHeight - (int)scaledTextHeight) / 2;

        poseStack.translate(textX, textY, 0);
        poseStack.scale(INFO_TEXT_SCALE, INFO_TEXT_SCALE, 1.0f);

        // 主文本
        guiGraphics.drawString(font, box.text, 0, 0, 0xFFFFFFFF);

        poseStack.popPose();
    }

    private static void drawRoundedRect(GuiGraphics guiGraphics, int x, int y, int width, int height, int radius, int color) {
        UiPanelRenderer.roundedRect(guiGraphics, x, y, width, height, radius, color);
    }

    private static void drawRoundedBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int radius, int color) {
        UiPanelRenderer.roundedBorder(guiGraphics, x, y, width, height, radius, color);
    }

    // 渲染小框（带文字缩放）- 保留旧方法备用
    private static void renderSmallBox(GuiGraphics guiGraphics, Font font, int x, int y, InfoBox box) {
        UiPanelRenderer.roundedRect(guiGraphics, x, y, box.boxWidth, BOX_HEIGHT,
                COMPACT_INFO_RADIUS, box.backgroundColor);
        UiPanelRenderer.roundedBorder(guiGraphics, x, y, box.boxWidth, BOX_HEIGHT,
                COMPACT_INFO_RADIUS, box.borderColor);

        // 文本居中渲染（应用缩放）
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();

        float scaledTextWidth = box.textWidth * INFO_TEXT_SCALE;
        float scaledTextHeight = font.lineHeight * INFO_TEXT_SCALE;

        int textX = x + (box.boxWidth - (int)scaledTextWidth) / 2;
        int textY = y + (BOX_HEIGHT - (int)scaledTextHeight) / 2;

        poseStack.translate(textX, textY, 0);
        poseStack.scale(INFO_TEXT_SCALE, INFO_TEXT_SCALE, 1.0f);
        guiGraphics.drawString(font, box.text, 0, 0, 0xFFFFFF);

        poseStack.popPose();
    }

    // 获取游戏时间字符串
    private static String getGameTimeString(Minecraft mc) {
        if (mc.level == null) return UNKNOWN_TIME_TEXT;

        long currentTime = System.currentTimeMillis();
        if (LAST_GAME_TIME_UPDATE != Long.MIN_VALUE
                && currentTime - LAST_GAME_TIME_UPDATE < GAME_TIME_CACHE_INTERVAL) {
            return CACHED_GAME_TIME;
        }

        java.time.LocalDateTime realTime = java.time.LocalDateTime.now();
        int year = realTime.getYear();
        int month = realTime.getMonthValue();
        int day = realTime.getDayOfMonth();
        int hour = realTime.getHour();
        int minute = realTime.getMinute();

        CACHED_GAME_TIME = String.format("%d.%d.%d %02d:%02d", year, month, day, hour, minute);
        LAST_GAME_TIME_UPDATE = currentTime;
        return CACHED_GAME_TIME;
    }

    // 信息框数据类
    private static class InfoBox {
        Component text;
        int borderColor;
        int backgroundColor;
        int textWidth;
        int boxWidth;

        InfoBox(Component text, int borderColor, int backgroundColor) {
            this.text = text;
            this.borderColor = borderColor;
            this.backgroundColor = backgroundColor;
        }
    }

    /**
     * 获取动态RGB变色的边框颜色（基于系统时间循环，颜色更淡，使用缓存优化性能）
     */
    private static int getDynamicBorderColor() {
        long currentTime = System.currentTimeMillis();

        // 每100ms更新一次颜色，避免每帧计算
        if (currentTime - LAST_COLOR_UPDATE > COLOR_UPDATE_INTERVAL) {
            int red = (int) (Math.sin(currentTime * 0.001) * 100 + 155);
            int green = (int) (Math.sin(currentTime * 0.001 + 2) * 100 + 155);
            int blue = (int) (Math.sin(currentTime * 0.001 + 4) * 100 + 155);
            CACHED_DYNAMIC_COLOR = 0xFF000000 | (red << 16) | (green << 8) | blue;
            LAST_COLOR_UPDATE = currentTime;
        }

        return CACHED_DYNAMIC_COLOR;
    }

    /**
     * 根据Rank名称获取对应的颜色
     */
    private static int getRankColorByName(String rankName) {
        return switch (rankName) {
            case "FISH" -> 0xFF55FF55;
            case "FISH+" -> 0xFF55FFFF;
            case "FISH++" -> 0xFFFFAA00;  // 金色（与 SystemMessage 保持一致）
            case "BUILDER FISH" -> 0xFF55FF55;
            case "SUPER BUILDER FISH" -> 0xFF55FFFF;
            case "WORLD SHAPER FISH" -> 0xFFFFAA00;
            case "MYTH SHAPER FISH" -> 0xFFFF69B4;
            case "OPERATOR" -> 0xFFFF5555;
            default -> 0xAAAAAA;
        };
    }

    /**
     * 将RGB颜色值转换为Minecraft颜色代码
     * @param rgb RGB颜色值（如0xFFFFFF）
     * @return 颜色代码字符串（如"§f"）
     */
    private static String rgbToColorCode(int rgb) {
        // 提取RGB分量
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;

        // 寻找最接近的Minecraft颜色
        if (red == 0 && green == 0 && blue == 0) return "§0";       // 黑色
        if (red == 0 && green == 0 && blue == 170) return "§1";   // 深蓝色
        if (red == 0 && green == 170 && blue == 0) return "§2";   // 深绿色
        if (red == 0 && green == 170 && blue == 170) return "§3"; // 深青色
        if (red == 170 && green == 0 && blue == 0) return "§4";   // 深红色
        if (red == 170 && green == 0 && blue == 170) return "§5"; // 深紫色
        if (red == 255 && green == 170 && blue == 0) return "§6"; // 金色
        if (red == 170 && green == 170 && blue == 170) return "§7"; // 灰色
        if (red == 85 && green == 85 && blue == 85) return "§8";  // 深灰色
        if (red == 85 && green == 85 && blue == 255) return "§9"; // 蓝色
        if (red == 85 && green == 255 && blue == 85) return "§a"; // 绿色
        if (red == 85 && green == 255 && blue == 255) return "§b"; // 青色
        if (red == 255 && green == 85 && blue == 85) return "§c";  // 红色
        if (red == 255 && green == 85 && blue == 255) return "§d"; // 粉色
        if (red == 255 && green == 255 && blue == 85) return "§e"; // 黄色
        if (red == 255 && green == 255 && blue == 255) return "§f"; // 白色

        // 默认白色（如果找不到精确匹配）
        return "§f";
    }

    // 对外控制方法
    public static void toggleUI() {
        SHOW_UI = !SHOW_UI;
    }

    public static boolean isShowUI() {
        return SHOW_UI;
    }

    public static void refreshData() {
        LAST_SERVER_STATUS_UPDATE = 0;
    }
}
