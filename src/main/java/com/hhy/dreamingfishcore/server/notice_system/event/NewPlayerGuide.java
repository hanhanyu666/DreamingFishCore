package com.hhy.dreamingfishcore.server.notice_system.event;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.login_system.PlayerLoginData;
import com.hhy.dreamingfishcore.server.login_system.PlayerLoginDataManager;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import net.minecraft.server.level.ServerPlayer;

/** 引导未查看教程的玩家进入终端帮助页。登录事件会在所有游戏模式统一调用。 */
public final class NewPlayerGuide {
    public static final String PROMPT_TEXT =
            "欢迎来到梦屿。请按 U 打开终端，查看『新玩家帮助』。";
    private static final int PERSISTENT_DISPLAY_DURATION = -1;

    public enum ViewResult {
        DATA_MISSING,
        ALREADY_COMPLETED,
        COMPLETED_NOW
    }

    private NewPlayerGuide() {
    }

    public static void sendNewPlayerGuide(ServerPlayer player) {
        if (player == null) {
            return;
        }

        PlayerLoginData loginData = PlayerLoginDataManager.getLoginData(player.getUUID());
        if (loginData == null || loginData.gethasCompletedNewPlayerGuidence()) {
            return;
        }

        NotificationPushHelper.sendTopLeftNotification(
                player, PROMPT_TEXT, PERSISTENT_DISPLAY_DURATION);
        DreamingFishCore.LOGGER.debug(
                "已向玩家 {} 发送常驻新手教程提示", player.getScoreboardName());
    }

    /** 在玩家的帮助页真正渲染后记录教程完成状态，并返回明确的状态迁移结果。 */
    public static ViewResult markViewed(ServerPlayer player) {
        if (player == null) {
            return ViewResult.DATA_MISSING;
        }

        PlayerLoginData loginData = PlayerLoginDataManager.getLoginData(player.getUUID());
        if (loginData == null) {
            DreamingFishCore.LOGGER.warn("无法记录玩家 {} 的新手教程状态：登录数据不存在", player.getUUID());
            return ViewResult.DATA_MISSING;
        }

        if (loginData.gethasCompletedNewPlayerGuidence()) {
            return ViewResult.ALREADY_COMPLETED;
        }

        loginData.setHasCompletedNewPlayerGuidence(true);
        PlayerLoginDataManager.saveLoginData(player.getUUID(), loginData);
        DreamingFishCore.LOGGER.info("玩家 {} 已查看新玩家帮助，教程标记为完成", player.getUUID());
        return ViewResult.COMPLETED_NOW;
    }
}
