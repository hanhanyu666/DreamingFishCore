package com.hhy.dreamingfishcore.server.notice_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.login_system.PlayerLoginData;
import com.hhy.dreamingfishcore.server.login_system.PlayerLoginDataManager;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 新手教程（简化版：引导玩家查看帮助）
 */
@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID)
public class NewPlayerGuide {
    // 固定15秒时长（显示时间） & 15秒延迟（推送间隔，解决堆叠）
    private static final long SECONDS = 15000;

    // 跟踪每个玩家的教程线程，玩家退出时中断
    private static final Map<UUID, Thread> GUIDE_THREADS = new ConcurrentHashMap<>();

    public static void sendNewPlayerGuide(ServerPlayer player) {
        if (player == null) return;

        UUID playerUUID = player.getUUID();
        MinecraftServer server = player.getServer();

        // 如果该玩家已有教程线程在运行，先中断它
        Thread existingThread = GUIDE_THREADS.get(playerUUID);
        if (existingThread != null && existingThread.isAlive()) {
            existingThread.interrupt();
            DreamingFishCore.LOGGER.info("中断玩家 {} 的旧教程线程", player.getName().getString());
        }

        // 第一条：欢迎与快捷键提示（O 键切换信息面板功能已禁用，提示里不再提及 O 键）
        NotificationPushHelper.sendTopLeftNotification(
                player, "§6欢迎游玩梦鱼服，按下[U]打开服务器菜单", (int) SECONDS);

        // 开启子线程，实现延迟推送
        Thread guideThread = new Thread(() -> {
            try {
                Thread.sleep(SECONDS);
                // 第二条：引导查看帮助
                sendTipIfOnline(server, playerUUID, "§6在服务器菜单内您可以查看帮助，了解在梦屿的一些特殊机制，这将有助于您生存");

                Thread.sleep(SECONDS);
                // 第三条：鼓励
                sendTipIfOnline(server, playerUUID, "§6加油生存下去吧，萌新鱼友。");

                PlayerLoginData newPlayerLoginData = PlayerLoginDataManager.getLoginData(playerUUID);
                if (newPlayerLoginData == null) {
                    return;
                }
                // 设置该玩家完成了新手教程
                newPlayerLoginData.setHasCompletedNewPlayerGuidence(true);
                PlayerLoginDataManager.saveLoginData(playerUUID, newPlayerLoginData);
                DreamingFishCore.LOGGER.info("玩家 {} 新手教程已完成，已标记", playerUUID);
            } catch (InterruptedException e) {
                DreamingFishCore.LOGGER.info("玩家 {} 新手教程线程被中断", playerUUID);
                Thread.currentThread().interrupt();
            } finally {
                // 教程结束或中断后，从Map中移除
                GUIDE_THREADS.remove(playerUUID);
            }
        });
        GUIDE_THREADS.put(playerUUID, guideThread);
        guideThread.start();
    }

    /**
     * 如果玩家在线，发送提示消息
     * 支持死亡和重生后的消息发送
     */
    private static void sendTipIfOnline(MinecraftServer server, UUID playerUUID, String message) {
        if (server == null) {
            server = ServerLifecycleHooks.getCurrentServer();
        }
        if (server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
        if (player != null) {
            // 玩家在线，发送消息（即使死亡也能收到）
            NotificationPushHelper.sendTopLeftNotification(player, message, (int) SECONDS);
            DreamingFishCore.LOGGER.info("已向玩家 {} 发送新手教程消息", player.getName().getString());
        } else {
            DreamingFishCore.LOGGER.info("玩家 {} 已离线，跳过新手教程消息", playerUUID);
        }
    }

    /**
     * 玩家退出时中断其教程线程
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerUUID = player.getUUID();
        Thread guideThread = GUIDE_THREADS.remove(playerUUID);

        if (guideThread != null && guideThread.isAlive()) {
            guideThread.interrupt();
            DreamingFishCore.LOGGER.info("玩家 {} 退出，中断其教程线程", player.getName().getString());
        }
    }
}
