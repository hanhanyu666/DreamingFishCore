package com.hhy.dreamingfishcore.server.notice_system;

import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.notice_system.network.Packet_SendNotificationToClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.server.ServerLifecycleHooks;

/** 服务端向客户端推送统一通知的入口。 */
public final class NotificationPushHelper {
    private static final int DEFAULT_DISPLAY_DURATION = 15000;
    private static final byte TOP_LEFT = 0;
    private static final byte CENTER_TOP = 1;

    private NotificationPushHelper() {
    }

    /** 普通系统提示：左上角单行或多行显示。 */
    public static void sendTopLeftNotification(
            ServerPlayer targetPlayer, String message, int displayDuration) {
        send(targetPlayer, "", message, displayDuration, TOP_LEFT);
    }

    public static void sendTopLeftNotification(ServerPlayer targetPlayer, String message) {
        sendTopLeftNotification(targetPlayer, message, DEFAULT_DISPLAY_DURATION);
    }

    /** 场景提示：中上方显示标题和较小的说明文字。 */
    public static void sendCenterTopNotification(
            ServerPlayer targetPlayer, String title, String message, int displayDuration) {
        send(targetPlayer, title, message, displayDuration, CENTER_TOP);
    }

    /** 向全服玩家发送左上角普通提示。 */
    public static void broadcastTopLeftNotification(String message, int displayDuration) {
        if (message == null || message.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        Packet_SendNotificationToClient packet =
                new Packet_SendNotificationToClient("", message, displayDuration, TOP_LEFT);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DreamingFishCore_NetworkManager.INSTANCE.sendTo(
                    packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        }
    }

    private static void send(
            ServerPlayer targetPlayer, String title, String message, int displayDuration, byte position) {
        if (targetPlayer == null || (title == null || title.isBlank())
                && (message == null || message.isEmpty())) {
            return;
        }
        Packet_SendNotificationToClient packet = new Packet_SendNotificationToClient(
                title == null ? "" : title,
                message == null ? "" : message,
                displayDuration,
                position);
        DreamingFishCore_NetworkManager.INSTANCE.sendTo(
                packet, targetPlayer.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
