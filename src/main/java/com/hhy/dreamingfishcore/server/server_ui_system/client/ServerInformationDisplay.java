package com.hhy.dreamingfishcore.server.server_ui_system.client;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.framework.hud.HudFrame;
import com.hhy.dreamingfishcore.client.ui.framework.hud.HudLayer;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.server_ui_system.network.Packet_OnlinePlayerCountRequest;
import com.hhy.dreamingfishcore.gameplay.playerlevel_system.overalllevel.PlayerLevelManager;
import com.hhy.dreamingfishcore.gameplay.npc_system.client.ui.screen.Screen_NpcDialogue;
import com.hhy.dreamingfishcore.server.rank_system.PlayerRankManager;
import com.hhy.dreamingfishcore.server.rank_system.Rank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;
import java.util.Locale;
import java.util.UUID;


@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public class ServerInformationDisplay {
    private static boolean SHOW_UI = true;                  // UI开关
    private static final String TPS_ICON = "⚡";
    private static final float MAX_TPS = 10_000.0F;

    // 客户端缓存数据（从网络包获取）
    public static int ONLINE_PLAYERS = 0;

    private static long LAST_SERVER_STATUS_UPDATE = 0;     // 服务器状态最后刷新时间
    // NeoForge 的 TPS 统计基于最近 100 个 tick；低频复用在线人数请求，
    // 每次刷新只产生一个很小的请求/响应，不在渲染帧或服务端逐 tick 运行。
    private static final long UPDATE_INTERVAL = 5_000L;

    /** Latest server-authoritative sample, or NaN until a sample is received. */
    private static float CACHED_SERVER_TPS = Float.NaN;
    private static long LAST_TPS_UPDATE = Long.MIN_VALUE;
    // 留出一次网络刷新延迟，避免 5 秒轮询周期内短暂显示为未知。
    private static final long TPS_STALE_INTERVAL = UPDATE_INTERVAL * 2L;
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

    /** 右上角终端眼镜读数与其下方的系统消息，在统一 HUD 画布中绘制。 */
    public static final HudLayer LAYER = new HudLayer() {
        @Override
        public int order() {
            return 50;
        }

        @Override
        public boolean visible(Minecraft minecraft) {
            return shouldRenderInformationHud(minecraft);
        }

        @Override
        public void paint(HudFrame frame) {
            Minecraft mc = frame.minecraft();
            Font font = frame.font();
            int playerLevel = PlayerLevelManager.getPlayerLevelClient(mc.player);
            Rank playerRank = PlayerRankManager.getPlayerRankClient(mc.player);
            TerminalLensOverlay.Anchor anchor = TerminalLensOverlay.paint(frame.canvas(), mc, font, playerLevel, playerRank);
            List<com.hhy.dreamingfishcore.client.ui.notification.NotificationManager.ActiveNotification>
                    systemMessages = SystemMessageDisplay.getActiveMessages();
            if (!systemMessages.isEmpty()) {
                SystemMessageDisplay.renderSystemMessages(frame.canvas(), font, frame.width(), anchor.y(), anchor.height(),
                        systemMessages);
            }
        }
    };

    @SubscribeEvent
    public static void replaceVanillaEffects(RenderGuiLayerEvent.Pre event) {
        if (VanillaGuiLayers.EFFECTS.equals(event.getName())
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
