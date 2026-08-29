package com.hhy.dreamingfishcore.server.notice_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.gameplay.story_system.StoryManager;
import com.hhy.dreamingfishcore.server.login_system.PlayerLoginData;
import com.hhy.dreamingfishcore.server.login_system.PlayerLoginDataManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Set;

/**
 * 公告左上角投递服务。
 *
 * <p>公告的“已读”与“已投递”是两条独立链路：终端可见性只按公告类别和当前故事阶段
 * 判定，不要求玩家先完成教程；左上角自动投递仍会按教程状态选择公告，并在发送成功后
 * 记录 delivered。终端打开公告详情时仍由原有 read 状态管理器负责标记已读。</p>
 */
public final class NoticeDeliveryService {
    private static final int DISPLAY_DURATION_MILLIS = 15_000;
    private static final int GAME_NOTICE_AGGREGATE_THRESHOLD = 2;
    private static final String TERMINAL_HINT = "按 U 打开终端，在“梦屿广播”中查看详情";
    private static final String MULTIPLE_GAME_NOTICES =
            "§6您收到了多条梦屿广播消息\n§7" + TERMINAL_HINT;

    private NoticeDeliveryService() {
    }

    /**
     * 登录后补投当前玩家此刻可自动投递且尚未投递的公告。
     * 教程未完成时，游戏公告仍可在终端查看，但不会自动弹出；这里只补投服务器通知。
     */
    public static void deliverPendingOnLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }

        try {
            List<NoticeData> pending = selectPending(player);
            deliverBatch(player, pending);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.error(
                    "为玩家 {} 补投公告失败", player.getScoreboardName(), exception);
        }
    }

    /**
     * 故事阶段切换后，立即为当前在线玩家补投新阶段公告。
     *
     * <p>具体的教程状态、阶段可见性和 delivered 幂等判断继续复用登录补投链路，
     * 因此未完成教程的玩家不会提前收到游戏公告弹窗，已经投递过的公告也不会重复弹出。</p>
     */
    public static void deliverPendingToAllOnlinePlayers() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            deliverPendingOnLogin(player);
        }
    }

    /**
     * 新手教程首次完成后补投当前故事阶段尚未投递的游戏公告。
     * 服务器通知（MAINTENANCE）不在此回调中重复投递；它们由登录链路负责。
     */
    public static void deliverPendingGameAfterTutorial(ServerPlayer player) {
        if (player == null) {
            return;
        }

        try {
            List<NoticeData> pending = selectPending(player).stream()
                    .filter(NoticeData::isGameNotice)
                    .toList();
            deliverBatch(player, pending);
        } catch (RuntimeException exception) {
            DreamingFishCore.LOGGER.error(
                    "为玩家 {} 补投教程完成后的游戏公告失败",
                    player.getScoreboardName(), exception);
        }
    }

    /**
     * 公告成功写入配置后，向当前在线且符合该公告投递条件的玩家投递一次。
     * 服务器通知（MAINTENANCE）面向所有在线玩家；游戏公告只面向已完成教程且处于对应故事阶段的玩家。
     */
    public static void publishToEligibleOnlinePlayers(NoticeData notice) {
        if (notice == null) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                if (isDeliverableToPlayer(player, notice)
                        && !PlayerNoticeDataManager.hasDeliveredNotice(
                        player.getUUID(), notice.getNoticeId())) {
                    deliver(player, notice);
                }
            } catch (RuntimeException exception) {
                DreamingFishCore.LOGGER.error(
                        "向玩家 {} 发布公告 #{} 失败",
                        player.getScoreboardName(), notice.getNoticeId(), exception);
            }
        }
    }

    /** Returns the notices readable by this player in the current world state. */
    public static List<NoticeData> getVisibleNotices(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }

        StoryManager.Snapshot snapshot = StoryManager.getSnapshot();
        return NoticeManager.getVisibleNotices(snapshot.currentStageId());
    }

    /** Returns whether a notice is readable in the terminal right now. */
    public static boolean isVisibleToPlayer(ServerPlayer player, NoticeData notice) {
        if (player == null || notice == null) {
            return false;
        }

        StoryManager.Snapshot snapshot = StoryManager.getSnapshot();
        return NoticeVisibilityPolicy.isVisible(notice, snapshot.currentStageId());
    }

    /** Returns whether a notice may be pushed automatically right now. */
    public static boolean isDeliverableToPlayer(ServerPlayer player, NoticeData notice) {
        if (player == null || notice == null) {
            return false;
        }

        StoryManager.Snapshot snapshot = StoryManager.getSnapshot();
        return NoticeVisibilityPolicy.isDeliverable(
                notice, snapshot.currentStageId(), isTutorialCompleted(player));
    }

    private static List<NoticeData> selectPending(ServerPlayer player) {
        StoryManager.Snapshot snapshot = StoryManager.getSnapshot();
        boolean tutorialCompleted = isTutorialCompleted(player);
        Set<Integer> deliveredNoticeIds = PlayerNoticeDataManager.getDeliveredNoticeIds(
                player.getUUID());
        return NoticeVisibilityPolicy.selectForDelivery(
                NoticeManager.getNotices(),
                deliveredNoticeIds,
                snapshot.currentStageId(),
                tutorialCompleted);
    }

    private static boolean isTutorialCompleted(ServerPlayer player) {
        PlayerLoginData loginData = PlayerLoginDataManager.getLoginData(player.getUUID());
        return loginData != null && loginData.gethasCompletedNewPlayerGuidence();
    }

    /**
     * 同一批剧情类广播超过两条时只弹出一条汇总；服务器公告始终逐条显示，
     * 因而一条剧情广播和一条服务器公告同时到达时仍会分成两条提示。
     */
    private static void deliverBatch(ServerPlayer player, List<NoticeData> pending) {
        if (player == null || pending == null || pending.isEmpty()) {
            return;
        }
        boolean aggregateGameNotices = shouldAggregateGameNotices(pending);
        boolean aggregateSent = false;
        for (NoticeData notice : pending) {
            if (notice == null) {
                continue;
            }
            if (aggregateGameNotices && notice.isGameNotice()) {
                if (!aggregateSent) {
                    NotificationPushHelper.sendTopLeftNotification(
                            player, MULTIPLE_GAME_NOTICES, DISPLAY_DURATION_MILLIS);
                    aggregateSent = true;
                }
                PlayerNoticeDataManager.markAsDelivered(player.getUUID(), notice.getNoticeId());
                continue;
            }
            deliver(player, notice);
        }
    }

    private static void deliver(ServerPlayer player, NoticeData notice) {
        if (player == null || notice == null) {
            return;
        }

        NotificationPushHelper.sendTopLeftNotification(
                player, formatSummary(notice), DISPLAY_DURATION_MILLIS);
        PlayerNoticeDataManager.markAsDelivered(player.getUUID(), notice.getNoticeId());
    }

    static boolean shouldAggregateGameNotices(List<NoticeData> notices) {
        if (notices == null) {
            return false;
        }
        return notices.stream()
                .filter(notice -> notice != null && notice.isGameNotice())
                .count() > GAME_NOTICE_AGGREGATE_THRESHOLD;
    }

    static String formatSummary(NoticeData notice) {
        String title = notice.getNoticeTitle() == null ? "" : notice.getNoticeTitle().trim();
        if (notice.isGameNotice()) {
            // 剧情广播只显示标题，不再重复展示终端栏目名称。
            return "§6" + title
                    + "\n§7" + TERMINAL_HINT;
        }
        return "§b服务器公告§f " + title + "\n§7" + TERMINAL_HINT;
    }
}
