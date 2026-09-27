package com.hhy.dreamingfishcore.server.server_ui_system.client;

import com.google.common.collect.Ordering;
import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.components.UiPanelRenderer;
import com.hhy.dreamingfishcore.client.ui.render.GuiQuadBatchRenderer;
import com.hhy.dreamingfishcore.client.ui.render.PlayerFaceBatchRenderer;
import com.hhy.dreamingfishcore.client.ui.render.RetainedGuiBuffers;
import com.hhy.dreamingfishcore.client.ui.render.RetainedPlayerFace;
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
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.joml.Matrix4f;


@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public class ServerInformationDisplay {
    private static boolean SHOW_UI = true;                  // UI开关
    private static final boolean USE_LEGACY_INFO_BOXES = false;
    private static final String SERVER_NAME_DREAMING = "Dreaming";
    private static final String SERVER_NAME_FISH = "fish";
    private static final String TOP_INFO_SEPARATOR = " | ";
    private static final String ONLINE_SUFFIX = " 在线";
    private static final String LEVEL_PREFIX = "Lv.";
    private static final String EXP_OPEN = "（";
    private static final String EXP_SEPARATOR = "/";
    private static final String EXP_CLOSE = "）";
    private static final String TPS_ICON = "⚡";
    private static final String ONLINE_ICON = "👤";
    private static final String UNKNOWN_TIME_TEXT = "未知";
    private static final int COMPACT_INFO_OUTLINE = 0xFF555555;
    private static final int COMPACT_INFO_BG = 0xFF212121;
    private static final int COMPACT_INFO_TEXT = 0xFFE6E6E6;
    private static final int COMPACT_INFO_MUTED = 0xFF9A9A9A;
    private static final int COMPACT_INFO_LEVEL = 0xFFFFAA33;
    private static final int COMPACT_INFO_NAME = 0xFFFFEE88;
    private static final int COMPACT_INFO_ONLINE = 0xFFB8D8FF;
    private static final int COMPACT_INFO_DREAMING = 0xFF55FFFF;
    private static final int COMPACT_INFO_FISH = 0xFFFF55FF;
    private static final float COMPACT_INFO_SCALE = 0.75f;
    private static final int COMPACT_INFO_PADDING = 4;
    private static final int COMPACT_INFO_MESSAGE_GAP = 4;
    private static final int COMPACT_INFO_LINE_SPACING = 2;
    private static final int COMPACT_INFO_AVATAR_SIZE = 9;
    private static final int COMPACT_INFO_AVATAR_SPACING = 3;
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
    private static final float COMPACT_EFFECT_SCALE = 0.75f;
    private static final int COMPACT_EFFECT_SIZE = 18;
    private static final int COMPACT_EFFECT_GAP = 2;
    private static final int COMPACT_EFFECT_INFO_GAP = 3;
    private static final ResourceLocation EFFECT_BACKGROUND_AMBIENT_SPRITE =
            ResourceLocation.withDefaultNamespace("hud/effect_background_ambient");
    private static final ResourceLocation EFFECT_BACKGROUND_SPRITE =
            ResourceLocation.withDefaultNamespace("hud/effect_background");

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
    private static int CACHED_ONLINE_PLAYER_COUNT = Integer.MIN_VALUE;
    private static String CACHED_ONLINE_TEXT = ONLINE_ICON + "0";
    private static Font CACHED_COMPACT_FONT;
    private static String CACHED_COMPACT_ONLINE_TEXT;
    private static String CACHED_COMPACT_TIME_TEXT;
    private static String CACHED_COMPACT_TPS_TEXT;
    private static String CACHED_COMPACT_PLAYER_TEXT;
    private static String CACHED_COMPACT_TITLE_TEXT;
    private static String CACHED_COMPACT_LEVEL_TEXT;
    private static String CACHED_COMPACT_RANK_TEXT;
    private static CompactTextLayout CACHED_COMPACT_LAYOUT;
    private static int CACHED_LEVEL_TEXT_LEVEL = Integer.MIN_VALUE;
    private static long CACHED_LEVEL_TEXT_CURRENT_EXP = Long.MIN_VALUE;
    private static long CACHED_LEVEL_TEXT_NEXT_EXP = Long.MIN_VALUE;
    private static String CACHED_LEVEL_TEXT_VALUE = "";
    private static List<MobEffectInstance> CACHED_ORDERED_EFFECTS = List.of();
    private static long LAST_EFFECT_ORDER_UPDATE = Long.MIN_VALUE;
    private static final long EFFECT_ORDER_CACHE_INTERVAL = 250L;
    private static MobEffectInstance[] effectBatchInstances = new MobEffectInstance[8];
    private static IClientMobEffectExtensions[] effectBatchRenderers = new IClientMobEffectExtensions[8];
    private static int[] effectBatchX = new int[8];
    private static int[] effectBatchY = new int[8];
    private static float[] effectBatchAlpha = new float[8];
    private static boolean[] effectBatchCustomHandled = new boolean[8];
    private static TextureAtlasSprite cachedEffectBackground;
    private static TextureAtlasSprite cachedAmbientEffectBackground;
    private static CompactRenderCache compactRenderCache;
    private static volatile boolean compactRenderCacheDirty = true;

    // 获取当前玩家UUID
    public static UUID getCurrentPlayerUUID() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getUUID() : null;
    }

    @SubscribeEvent
    public static void onClientLoginToServer(ClientPlayerNetworkEvent.LoggingIn event) {
        resetTpsCache();
        LAST_SERVER_STATUS_UPDATE = 0L;
        compactRenderCacheDirty = true;

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
        compactRenderCacheDirty = true;
        CACHED_COMPACT_FONT = null;
        CACHED_COMPACT_LAYOUT = null;
        cachedEffectBackground = null;
        cachedAmbientEffectBackground = null;
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetTpsCache();
        LAST_SERVER_STATUS_UPDATE = 0L;
        compactRenderCacheDirty = true;
        CACHED_LEVEL_TEXT_LEVEL = Integer.MIN_VALUE;
        CACHED_LEVEL_TEXT_CURRENT_EXP = Long.MIN_VALUE;
        CACHED_LEVEL_TEXT_NEXT_EXP = Long.MIN_VALUE;
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
        TopInfoAnchor anchor = renderCompactTopInfo(
                guiGraphics, font, screenWidth, mc, playerLevel, playerRank);
        if (!systemMessages.isEmpty()) {
            guiGraphics.drawManaged(() -> SystemMessageDisplay.renderSystemMessages(
                    guiGraphics, font, screenWidth, anchor.y(), anchor.heightOffset(), systemMessages));
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

    private static TopInfoAnchor renderCompactTopInfo(GuiGraphics guiGraphics, Font font, int screenWidth,
                                                      Minecraft mc,
                                                      int playerLevel, Rank playerRank) {
        String serverDreamingText = SERVER_NAME_DREAMING;
        String serverFishText = SERVER_NAME_FISH;
        String onlineText = getOnlineText();
        String timeText = getGameTimeString(mc);
        String tpsText = getClientTpsText(mc);
        String playerIdText = mc.player.getGameProfile().getName();
        Title title = PlayerTitleManager.getPlayerTitleClient(mc.player);
        String titleName = title.getTitleName();
        int titleColor = 0xFF000000 | title.getColor();
        long currentExp = PlayerLevelManager.getPlayerExperienceClient(mc.player);
        long nextLevelExp = PlayerLevelManager.getExperienceNeededForNextLevelClient(mc.player);
        String levelText = getCachedLevelText(playerLevel, currentExp, nextLevelExp);
        String rankText = playerRank.getRankName();
        int rankColor = playerRank.getRankColor();
        ResourceLocation playerSkin = mc.player.getSkin().texture();

        CompactRenderCache cache = getOrBuildCompactRenderCache(
                guiGraphics, font, screenWidth,
                serverDreamingText, serverFishText, onlineText, timeText, tpsText,
                playerIdText, titleName, levelText, rankText, titleColor, rankColor,
                playerSkin);
        CompactRenderLayout layout = cache.layout();

        // No ordinary GUI geometry is produced by the compact path anymore.
        // Draw its retained panel/text buffers directly at the display rate.
        guiGraphics.flush();
        cache.buffers().draw();

        cache.avatar().draw();
        renderCompactEffects(guiGraphics, mc, layout.infoLeftEdge());
        return new TopInfoAnchor(layout.anchorY(), layout.anchorHeightOffset());
    }

    private static CompactRenderCache getOrBuildCompactRenderCache(
            GuiGraphics guiGraphics, Font font, int screenWidth,
            String serverDreamingText, String serverFishText,
            String onlineText, String timeText, String tpsText,
            String playerIdText, String titleName, String levelText, String rankText,
            int titleColor, int rankColor, ResourceLocation playerSkin) {
        Matrix4f currentPose = guiGraphics.pose().last().pose();
        CompactRenderCache current = compactRenderCache;
        if (!compactRenderCacheDirty && current != null
                && current.matches(font, screenWidth, currentPose,
                onlineText, timeText, tpsText, playerIdText,
                titleName, levelText, rankText, titleColor, rankColor, playerSkin)) {
            return current;
        }

        CompactTextLayout textLayout = getCompactTextLayout(font, onlineText, timeText, tpsText,
                playerIdText, titleName, levelText, rankText);
        int padding = INFO_BOX_TEXT_PADDING;
        int scaledAvatarWidth = textLayout.scaledAvatarWidth();
        int scaledAvatarSpacing = textLayout.scaledAvatarSpacing();
        int serverBoxWidth = (int) (textLayout.firstLineWidth() * INFO_TEXT_SCALE) + padding * 2;
        int playerBoxWidth = scaledAvatarWidth + scaledAvatarSpacing
                + (int) (textLayout.secondLineTextWidth() * INFO_TEXT_SCALE) + padding * 2;
        int levelBoxWidth = (int) (textLayout.thirdLineTextWidth() * INFO_TEXT_SCALE) + padding * 2;
        int scaledTextHeight = textLayout.scaledTextHeight();
        int avatarHeight = textLayout.avatarHeight();
        int boxHeight = BOX_HEIGHT;
        int serverBoxX = screenWidth - serverBoxWidth - RIGHT_OFFSET;
        int serverBoxY = TOP_OFFSET;
        int playerBoxX = screenWidth - playerBoxWidth - RIGHT_OFFSET;
        int playerBoxY = serverBoxY + boxHeight + COMPACT_INFO_LINE_SPACING;
        int levelBoxX = screenWidth - levelBoxWidth - RIGHT_OFFSET;
        int levelBoxY = playerBoxY + boxHeight + COMPACT_INFO_LINE_SPACING;
        int serverLineY = serverBoxY + (boxHeight - scaledTextHeight) / 2;
        int playerLineY = playerBoxY + (boxHeight - scaledTextHeight) / 2;
        int levelLineY = levelBoxY + (boxHeight - scaledTextHeight) / 2;
        int avatarY = playerBoxY + (boxHeight - avatarHeight) / 2;
        int infoLeftEdge = Math.min(serverBoxX, Math.min(playerBoxX, levelBoxX));
        int totalHeight = boxHeight * 3 + COMPACT_INFO_LINE_SPACING * 2;

        Matrix4f basePose = new Matrix4f(currentPose);
        RetainedGuiBuffers replacement;
        try (RetainedGuiBuffers.Capture capture = new RetainedGuiBuffers.Capture()) {
            captureVanillaEffectPanel(capture, basePose,
                    serverBoxX, serverBoxY, serverBoxWidth, boxHeight);
            captureVanillaEffectPanel(capture, basePose,
                    playerBoxX, playerBoxY, playerBoxWidth, boxHeight);
            captureVanillaEffectPanel(capture, basePose,
                    levelBoxX, levelBoxY, levelBoxWidth, boxHeight);

            Matrix4f serverTextPose = new Matrix4f(basePose)
                    .translate(serverBoxX + padding, serverLineY, 0.0F)
                    .scale(INFO_TEXT_SCALE, INFO_TEXT_SCALE, 1.0F);
            int currentX = 0;
            currentX = captureCompactPart(capture, font, serverDreamingText, currentX,
                    COMPACT_INFO_DREAMING, textLayout.serverDreamingWidth(), serverTextPose);
            currentX = captureCompactPart(capture, font, serverFishText, currentX,
                    COMPACT_INFO_FISH, textLayout.serverFishWidth(), serverTextPose);
            currentX = captureCompactPart(capture, font, TOP_INFO_SEPARATOR, currentX,
                    COMPACT_INFO_MUTED, textLayout.separatorWidth(), serverTextPose);
            currentX = captureCompactPart(capture, font, onlineText, currentX,
                    COMPACT_INFO_ONLINE, textLayout.onlineWidth(), serverTextPose);
            currentX = captureCompactPart(capture, font, TOP_INFO_SEPARATOR, currentX,
                    COMPACT_INFO_MUTED, textLayout.separatorWidth(), serverTextPose);
            currentX = captureCompactPart(capture, font, timeText, currentX,
                    COMPACT_INFO_MUTED, textLayout.timeWidth(), serverTextPose);
            currentX = captureCompactPart(capture, font, TOP_INFO_SEPARATOR, currentX,
                    COMPACT_INFO_MUTED, textLayout.separatorWidth(), serverTextPose);
            captureCompactPart(capture, font, tpsText, currentX,
                    COMPACT_INFO_LEVEL, textLayout.tpsWidth(), serverTextPose);

            Matrix4f playerTextPose = new Matrix4f(basePose)
                    .translate(playerBoxX + padding + scaledAvatarWidth + scaledAvatarSpacing,
                            playerLineY, 0.0F)
                    .scale(INFO_TEXT_SCALE, INFO_TEXT_SCALE, 1.0F);
            currentX = 0;
            currentX = captureCompactPart(capture, font, playerIdText, currentX,
                    COMPACT_INFO_TEXT, textLayout.playerWidth(), playerTextPose);
            currentX = captureCompactPart(capture, font, TOP_INFO_SEPARATOR, currentX,
                    COMPACT_INFO_MUTED, textLayout.separatorWidth(), playerTextPose);
            currentX = captureCompactPart(capture, font, titleName, currentX,
                    titleColor, textLayout.titleWidth(), playerTextPose);
            currentX = captureCompactPart(capture, font, TOP_INFO_SEPARATOR, currentX,
                    COMPACT_INFO_MUTED, textLayout.separatorWidth(), playerTextPose);
            captureCompactPart(capture, font, rankText, currentX,
                    0xFF000000 | (rankColor & 0x00FFFFFF), textLayout.rankWidth(), playerTextPose);

            Matrix4f levelTextPose = new Matrix4f(basePose)
                    .translate(levelBoxX + padding, levelLineY, 0.0F)
                    .scale(INFO_TEXT_SCALE, INFO_TEXT_SCALE, 1.0F);
            captureCompactPart(capture, font, levelText, 0,
                    COMPACT_INFO_LEVEL, textLayout.levelWidth(), levelTextPose);
            replacement = capture.upload();
        }

        CompactRenderLayout layout = new CompactRenderLayout(
                playerBoxX + padding, avatarY, avatarHeight,
                infoLeftEdge, serverBoxY, totalHeight + COMPACT_INFO_MESSAGE_GAP);
        RetainedPlayerFace replacementAvatar;
        try {
            replacementAvatar = RetainedPlayerFace.create(playerSkin, basePose,
                    layout.avatarX(), layout.avatarY(), layout.avatarHeight());
        } catch (Throwable throwable) {
            replacement.close();
            throw throwable;
        }
        CompactRenderCache rebuilt = new CompactRenderCache(
                font, screenWidth, onlineText, timeText, tpsText,
                playerIdText, titleName, levelText, rankText,
                titleColor, rankColor, playerSkin, basePose, layout,
                replacement, replacementAvatar);
        if (current != null) {
            current.buffers().close();
            current.avatar().close();
        }
        compactRenderCache = rebuilt;
        compactRenderCacheDirty = false;
        return rebuilt;
    }

    private static void captureVanillaEffectPanel(RetainedGuiBuffers.Capture capture, Matrix4f pose,
                                                  int x, int y, int width, int height) {
        UiPanelRenderer.roundedRectTo(capture, pose,
                x, y, width, height, 2, COMPACT_INFO_OUTLINE);
        UiPanelRenderer.roundedRectTo(capture, pose,
                x + 1, y + 1, width - 2, height - 2, 1, COMPACT_INFO_BG);
    }

    private static int captureCompactPart(RetainedGuiBuffers.Capture capture, Font font,
                                          String text, int x, int color, int measuredWidth,
                                          Matrix4f pose) {
        font.drawInBatch(text, x, 0.0F, color, false, pose, capture,
                Font.DisplayMode.NORMAL, 0, 15728880);
        return x + measuredWidth;
    }

    private static String getCachedLevelText(int level, long currentExp, long nextLevelExp) {
        if (level != CACHED_LEVEL_TEXT_LEVEL
                || currentExp != CACHED_LEVEL_TEXT_CURRENT_EXP
                || nextLevelExp != CACHED_LEVEL_TEXT_NEXT_EXP) {
            CACHED_LEVEL_TEXT_LEVEL = level;
            CACHED_LEVEL_TEXT_CURRENT_EXP = currentExp;
            CACHED_LEVEL_TEXT_NEXT_EXP = nextLevelExp;
            CACHED_LEVEL_TEXT_VALUE = LEVEL_PREFIX + level + EXP_OPEN + currentExp
                    + EXP_SEPARATOR + nextLevelExp + EXP_CLOSE;
        }
        return CACHED_LEVEL_TEXT_VALUE;
    }

    private static void renderCompactEffects(GuiGraphics guiGraphics, Minecraft mc, int infoLeftEdge) {
        int beneficialIndex = 0;
        int harmfulIndex = 0;
        int count = 0;
        for (MobEffectInstance effect : getOrderedEffects(mc)) {
            IClientMobEffectExtensions renderer = IClientMobEffectExtensions.of(effect);
            if (!renderer.isVisibleInGui(effect) || !effect.showIcon()) {
                continue;
            }
            boolean beneficial = effect.getEffect().value().isBeneficial();
            int column = beneficial ? beneficialIndex++ : harmfulIndex++;
            int x = infoLeftEdge - COMPACT_EFFECT_INFO_GAP - COMPACT_EFFECT_SIZE
                    - column * (COMPACT_EFFECT_SIZE + COMPACT_EFFECT_GAP);
            int y = TOP_OFFSET + (beneficial ? 0 : COMPACT_EFFECT_SIZE + COMPACT_EFFECT_GAP);
            ensureEffectBatchCapacity(count + 1);
            effectBatchInstances[count] = effect;
            effectBatchRenderers[count] = renderer;
            effectBatchX[count] = x;
            effectBatchY[count] = y;
            effectBatchAlpha[count] = effectAlpha(effect);
            effectBatchCustomHandled[count] = false;
            count++;
        }
        if (count == 0) {
            return;
        }

        // The retained information panel was submitted by the caller. The
        // remaining effect backgrounds and icons each use one atlas batch.
        Matrix4f pose = guiGraphics.pose().last().pose();

        BufferBuilder backgrounds = GuiQuadBatchRenderer.begin();
        ResourceLocation backgroundAtlas = null;
        for (int index = 0; index < count; index++) {
            MobEffectInstance effect = effectBatchInstances[index];
            TextureAtlasSprite background = getEffectBackground(mc, effect.isAmbient());
            if (backgroundAtlas == null) {
                backgroundAtlas = background.atlasLocation();
            }
            GuiQuadBatchRenderer.addSprite(backgrounds, pose, background,
                    effectBatchX[index], effectBatchY[index],
                    COMPACT_EFFECT_SIZE, COMPACT_EFFECT_SIZE, 1.0F);
        }
        GuiQuadBatchRenderer.draw(backgrounds, backgroundAtlas);

        // Preserve third-party custom effect renderers. They are uncommon and
        // may perform arbitrary immediate work, while all default icons below
        // remain in one atlas submission.
        RenderSystem.enableBlend();
        try {
            for (int index = 0; index < count; index++) {
                IClientMobEffectExtensions renderer = effectBatchRenderers[index];
                if (renderer == IClientMobEffectExtensions.DEFAULT) {
                    continue;
                }
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(effectBatchX[index], effectBatchY[index], 0.0F);
                guiGraphics.pose().scale(COMPACT_EFFECT_SCALE, COMPACT_EFFECT_SCALE, 1.0F);
                effectBatchCustomHandled[index] = renderer.renderGuiIcon(
                        effectBatchInstances[index], mc.gui, guiGraphics,
                        0, 0, 0, effectBatchAlpha[index]);
                guiGraphics.pose().popPose();
            }
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }

        BufferBuilder icons = GuiQuadBatchRenderer.begin();
        ResourceLocation iconAtlas = null;
        for (int index = 0; index < count; index++) {
            if (effectBatchCustomHandled[index]) {
                continue;
            }
            TextureAtlasSprite icon = mc.getMobEffectTextures()
                    .get(effectBatchInstances[index].getEffect());
            if (iconAtlas == null) {
                iconAtlas = icon.atlasLocation();
            }
            GuiQuadBatchRenderer.addSprite(icons, pose, icon,
                    effectBatchX[index] + 3.0F * COMPACT_EFFECT_SCALE,
                    effectBatchY[index] + 3.0F * COMPACT_EFFECT_SCALE,
                    18.0F * COMPACT_EFFECT_SCALE,
                    18.0F * COMPACT_EFFECT_SCALE,
                    effectBatchAlpha[index]);
        }
        if (iconAtlas != null) {
            GuiQuadBatchRenderer.draw(icons, iconAtlas);
        } else {
            // Finish the empty builder so Tesselator's shared storage is ready
            // for the next immediate batch.
            icons.build();
        }
    }

    private static float effectAlpha(MobEffectInstance effect) {
        if (effect.isAmbient() || !effect.endsWithin(200)) {
            return 1.0F;
        }
        int duration = effect.getDuration();
        int pulseStep = 10 - duration / 20;
        return Mth.clamp(duration / 10.0F / 5.0F * 0.5F, 0.0F, 0.5F)
                + Mth.cos(duration * (float) Math.PI / 5.0F)
                * Mth.clamp(pulseStep / 10.0F * 0.25F, 0.0F, 0.25F);
    }

    private static TextureAtlasSprite getEffectBackground(Minecraft minecraft, boolean ambient) {
        if (ambient) {
            if (cachedAmbientEffectBackground == null) {
                cachedAmbientEffectBackground = minecraft.getGuiSprites()
                        .getSprite(EFFECT_BACKGROUND_AMBIENT_SPRITE);
            }
            return cachedAmbientEffectBackground;
        }
        if (cachedEffectBackground == null) {
            cachedEffectBackground = minecraft.getGuiSprites().getSprite(EFFECT_BACKGROUND_SPRITE);
        }
        return cachedEffectBackground;
    }

    private static void ensureEffectBatchCapacity(int required) {
        if (required <= effectBatchInstances.length) {
            return;
        }
        int capacity = Math.max(required, effectBatchInstances.length * 2);
        effectBatchInstances = java.util.Arrays.copyOf(effectBatchInstances, capacity);
        effectBatchRenderers = java.util.Arrays.copyOf(effectBatchRenderers, capacity);
        effectBatchX = java.util.Arrays.copyOf(effectBatchX, capacity);
        effectBatchY = java.util.Arrays.copyOf(effectBatchY, capacity);
        effectBatchAlpha = java.util.Arrays.copyOf(effectBatchAlpha, capacity);
        effectBatchCustomHandled = java.util.Arrays.copyOf(effectBatchCustomHandled, capacity);
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
        CACHED_COMPACT_TPS_TEXT = null;
        CACHED_COMPACT_LAYOUT = null;
    }

    private static String getOnlineText() {
        if (CACHED_ONLINE_PLAYER_COUNT != ONLINE_PLAYERS) {
            CACHED_ONLINE_PLAYER_COUNT = ONLINE_PLAYERS;
            CACHED_ONLINE_TEXT = ONLINE_ICON + ONLINE_PLAYERS;
        }
        return CACHED_ONLINE_TEXT;
    }

    /**
     * Cache the width measurements used by the compact top HUD.  The values are
     * invalidated naturally when a player/title/rank/experience string changes
     * or when Minecraft swaps the active font (for example after a resource-pack
     * reload).  Drawing still occurs every frame, but repeated font splitter
     * work and temporary layout objects do not.
     */
    private static CompactTextLayout getCompactTextLayout(Font font, String onlineText,
                                                           String timeText, String tpsText,
                                                           String playerText, String titleText,
                                                           String levelText, String rankText) {
        if (CACHED_COMPACT_LAYOUT != null
                && CACHED_COMPACT_FONT == font
                && Objects.equals(CACHED_COMPACT_ONLINE_TEXT, onlineText)
                && Objects.equals(CACHED_COMPACT_TIME_TEXT, timeText)
                && Objects.equals(CACHED_COMPACT_TPS_TEXT, tpsText)
                && Objects.equals(CACHED_COMPACT_PLAYER_TEXT, playerText)
                && Objects.equals(CACHED_COMPACT_TITLE_TEXT, titleText)
                && Objects.equals(CACHED_COMPACT_LEVEL_TEXT, levelText)
                && Objects.equals(CACHED_COMPACT_RANK_TEXT, rankText)) {
            return CACHED_COMPACT_LAYOUT;
        }

        int serverDreamingWidth = font.width(SERVER_NAME_DREAMING);
        int serverFishWidth = font.width(SERVER_NAME_FISH);
        int separatorWidth = font.width(TOP_INFO_SEPARATOR);
        int onlineWidth = font.width(onlineText);
        int timeWidth = font.width(timeText);
        int tpsWidth = font.width(tpsText);
        int playerWidth = font.width(playerText);
        int titleWidth = font.width(titleText);
        int rankWidth = font.width(rankText);
        int levelWidth = font.width(levelText);
        int firstLineWidth = serverDreamingWidth
                + serverFishWidth
                + separatorWidth * 3
                + onlineWidth
                + timeWidth
                + tpsWidth;
        int secondLineTextWidth = playerWidth
                + separatorWidth * 2
                + titleWidth
                + rankWidth;
        int thirdLineTextWidth = levelWidth;
        int scaledAvatarWidth = (int) (COMPACT_INFO_AVATAR_SIZE * INFO_TEXT_SCALE);
        int scaledAvatarSpacing = (int) (COMPACT_INFO_AVATAR_SPACING * INFO_TEXT_SCALE);
        int scaledTextHeight = (int) (font.lineHeight * INFO_TEXT_SCALE);
        int avatarHeight = (int) (COMPACT_INFO_AVATAR_SIZE * INFO_TEXT_SCALE);

        CACHED_COMPACT_FONT = font;
        CACHED_COMPACT_ONLINE_TEXT = onlineText;
        CACHED_COMPACT_TIME_TEXT = timeText;
        CACHED_COMPACT_TPS_TEXT = tpsText;
        CACHED_COMPACT_PLAYER_TEXT = playerText;
        CACHED_COMPACT_TITLE_TEXT = titleText;
        CACHED_COMPACT_LEVEL_TEXT = levelText;
        CACHED_COMPACT_RANK_TEXT = rankText;
        CACHED_COMPACT_LAYOUT = new CompactTextLayout(firstLineWidth, secondLineTextWidth,
                thirdLineTextWidth, scaledAvatarWidth, scaledAvatarSpacing,
                scaledTextHeight, avatarHeight, serverDreamingWidth, serverFishWidth,
                separatorWidth, onlineWidth, timeWidth, tpsWidth, playerWidth,
                titleWidth, rankWidth, levelWidth);
        return CACHED_COMPACT_LAYOUT;
    }

    private static List<MobEffectInstance> getOrderedEffects(Minecraft mc) {
        Collection<MobEffectInstance> activeEffects = mc.player.getActiveEffects();
        if (activeEffects.isEmpty()) {
            if (!CACHED_ORDERED_EFFECTS.isEmpty()) {
                CACHED_ORDERED_EFFECTS = List.of();
                LAST_EFFECT_ORDER_UPDATE = Long.MIN_VALUE;
            }
            return CACHED_ORDERED_EFFECTS;
        }

        long now = System.currentTimeMillis();
        if (activeEffects.size() == CACHED_ORDERED_EFFECTS.size()
                && LAST_EFFECT_ORDER_UPDATE != Long.MIN_VALUE
                && now - LAST_EFFECT_ORDER_UPDATE < EFFECT_ORDER_CACHE_INTERVAL) {
            return CACHED_ORDERED_EFFECTS;
        }

        CACHED_ORDERED_EFFECTS = List.copyOf(Ordering.<MobEffectInstance>natural()
                .reverse()
                .sortedCopy(activeEffects));
        LAST_EFFECT_ORDER_UPDATE = now;
        return CACHED_ORDERED_EFFECTS;
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

    private record CompactTextLayout(int firstLineWidth, int secondLineTextWidth,
                                     int thirdLineTextWidth, int scaledAvatarWidth,
                                     int scaledAvatarSpacing, int scaledTextHeight,
                                     int avatarHeight, int serverDreamingWidth,
                                     int serverFishWidth, int separatorWidth, int onlineWidth,
                                     int timeWidth, int tpsWidth, int playerWidth,
                                     int titleWidth, int rankWidth, int levelWidth) {
    }

    private record TopInfoAnchor(int y, int heightOffset) {
    }

    private record CompactRenderLayout(int avatarX, int avatarY, int avatarHeight,
                                       int infoLeftEdge, int anchorY, int anchorHeightOffset) {
    }

    private record CompactRenderCache(Font font, int screenWidth,
                                      String onlineText, String timeText, String tpsText,
                                      String playerText, String titleText, String levelText,
                                      String rankText, int titleColor, int rankColor,
                                      ResourceLocation playerSkin, Matrix4f pose,
                                      CompactRenderLayout layout, RetainedGuiBuffers buffers,
                                      RetainedPlayerFace avatar) {
        private boolean matches(Font currentFont, int currentScreenWidth, Matrix4f currentPose,
                                String currentOnlineText, String currentTimeText,
                                String currentTpsText, String currentPlayerText,
                                String currentTitleText, String currentLevelText,
                                String currentRankText, int currentTitleColor,
                                int currentRankColor, ResourceLocation currentPlayerSkin) {
            return font == currentFont
                    && screenWidth == currentScreenWidth
                    && titleColor == currentTitleColor
                    && rankColor == currentRankColor
                    && pose.equals(currentPose)
                    && Objects.equals(playerSkin, currentPlayerSkin)
                    && Objects.equals(onlineText, currentOnlineText)
                    && Objects.equals(timeText, currentTimeText)
                    && Objects.equals(tpsText, currentTpsText)
                    && Objects.equals(playerText, currentPlayerText)
                    && Objects.equals(titleText, currentTitleText)
                    && Objects.equals(levelText, currentLevelText)
                    && Objects.equals(rankText, currentRankText);
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
