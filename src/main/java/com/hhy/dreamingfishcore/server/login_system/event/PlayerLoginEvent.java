package com.hhy.dreamingfishcore.server.login_system.event;

import com.hhy.dreamingfishcore.server.login_system.PlayerLoginData;
import com.hhy.dreamingfishcore.server.login_system.PlayerLoginDataManager;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.login_system.network.Packet_PlayerLoginRequest;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

//整个登录的流程，都在这里
@EventBusSubscriber(modid = DreamingFishCore.MODID)
public class PlayerLoginEvent {
    //登录注册事件
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLogging(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // PlayerLoggedInEvent 只代表连接建立；先撤销旧会话，后续只有密码验证成功才会放行。
        AuthSessionGuard.beginSession(player);

        //单人游戏跳过
        if (player.getServer().isSingleplayer()) {
            AuthSessionGuard.markAuthenticated(player);
            return;
        }

        UUID playerUUID = player.getUUID();
        DreamingFishCore.LOGGER.info("玩家 {} 尝试登录，UUID: {}", player.getName().getString(), playerUUID);

        // 获取玩家当前IP
        String playerIp = player.getIpAddress();

        // 检查是否已注册
        boolean isRegistered = PlayerLoginDataManager.hasLoginData(playerUUID);

        if (!isRegistered) {
            // 玩家没注册过，第一次进服
            DreamingFishCore.LOGGER.info("玩家 {} 未注册，发送注册请求包 (loginOrRegister=true)", player.getName().getString());
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.literal("§c您尚未登录或者注册，登录后会将您的游戏模式变成生存模式"));
            // 发送注册请求网络包 (true = 注册)
            DreamingFishCore_NetworkManager.sendToClient(new Packet_PlayerLoginRequest(true), player);
        } else {
            // 玩家已注册，检查是否可以快速登录
            PlayerLoginData loginData = PlayerLoginDataManager.getLoginData(playerUUID);

            if (loginData != null && loginData.canQuickLogin(playerIp)) {
                // 符合快速登录条件
                DreamingFishCore.LOGGER.info("玩家 {} 符合快速登录条件，执行自动登录", player.getName().getString());

                // 恢复上次保存的游戏模式
                GameType lastGameMode = loginData.getLastGameMode();
                if (lastGameMode == null) {
                    // 如果没有保存的游戏模式，使用服务器默认
                    lastGameMode = player.getServer().getDefaultGameType();
                }
                player.setGameMode(lastGameMode);
                DreamingFishCore.LOGGER.info("玩家 {} 快速登录成功，游戏模式恢复为: {}",
                    player.getName().getString(), lastGameMode);

                // 更新登录信息
                loginData.setLastLoginIP(playerIp);
                loginData.setLastLoginTime(String.valueOf(System.currentTimeMillis()));
                loginData.setLoginSessionCompleted(true);
                if (!PlayerLoginDataManager.saveLoginDataChecked(playerUUID, loginData)) {
                    loginData.setLoginSessionCompleted(false);
                    loginData.setLastLogoutTime(0L);
                    player.setGameMode(GameType.SPECTATOR);
                    player.sendSystemMessage(Component.literal("§c登录数据暂时无法保存，请稍后重试"));
                    return;
                }
                AuthSessionGuard.markAuthenticated(player);
                NotificationPushHelper.sendTopLeftNotification(
                        player, "§a欢迎回来！已自动登录（5分钟内重连）");
                player.sendSystemMessage(Component.literal("§a欢迎回来！已自动登录（5分钟内同IP重连）"));

                DreamingFishCore.LOGGER.info("玩家 {} 快速登录成功", player.getName().getString());
            } else {
                // 不符合快速登录条件，需要手动登录
                DreamingFishCore.LOGGER.info("玩家 {} 已注册但不符快速登录条件，发送登录请求包 (loginOrRegister=false)", player.getName().getString());
                player.setGameMode(GameType.SPECTATOR);
                // 标记登录验证状态为未完成
                if (loginData != null) {
                    loginData.setLoginSessionCompleted(false);
                    PlayerLoginDataManager.saveLoginData(playerUUID, loginData);
                }
                player.sendSystemMessage(Component.literal("§c请在弹出的窗口中完成登录/注册操作"));
                // 发送登录请求网络包 (false = 登录)
                DreamingFishCore_NetworkManager.sendToClient(new Packet_PlayerLoginRequest(false), player);
            }
        }
    }

    //退出记录事件
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerLoggout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // 退出广播等普通优先级监听器需要先读取本次会话的认证状态；
        // 所以把 invalidate 放到最低优先级，并用 finally 保证异常时也会清理。
        try {
            if (player.getServer() == null || player.getServer().isSingleplayer()) {
                return;
            }

            UUID playerUUID = player.getUUID();

            //保存离开游戏前的模式，时间，ip
            PlayerLoginData playerLoginData = PlayerLoginDataManager.getLoginData(playerUUID);
            if (playerLoginData == null) {
                return;
            }

            // 未完成密码验证的连接不能刷新“同 IP 五分钟快速登录”窗口。
            // 否则，玩家在登录界面直接断线后再次连接会被旧逻辑自动放行。
            if (!AuthSessionGuard.isAuthenticated(player)) {
                playerLoginData.setLoginSessionCompleted(false);
                playerLoginData.setLastLogoutTime(0L);
                PlayerLoginDataManager.saveLoginData(playerUUID, playerLoginData);
                DreamingFishCore.LOGGER.info("玩家 {} 未完成登录验证即退出，已撤销快速登录资格",
                        player.getName().getString());
                return;
            }

            DreamingFishCore.LOGGER.info("玩家 {} 退出，记录退出信息", player.getName().getString());

            // 只有当玩家完成了登录验证时，才保存当前游戏模式
            // 这样可以区分"登录验证状态的SPECTATOR"和"玩家真实的SPECTATOR模式"
            if (playerLoginData.isLoginSessionCompleted()) {
                GameType currentGameMode = player.gameMode.getGameModeForPlayer();
                playerLoginData.setGameMode(currentGameMode);
                DreamingFishCore.LOGGER.info("玩家退出时游戏模式: {}", currentGameMode);
            } else {
                DreamingFishCore.LOGGER.info("玩家未完成登录验证即退出，不保存游戏模式");
            }

            playerLoginData.setLastLoginIP(player.getIpAddress());
            playerLoginData.setLastLoginTime(String.valueOf(System.currentTimeMillis()));

            // 记录退出时间戳（用于快速登录判断）
            playerLoginData.setLastLogoutTime(System.currentTimeMillis());

            PlayerLoginDataManager.saveLoginData(playerUUID, playerLoginData);

            // 完整数据集缓存是服务器运行期间的唯一数据源，无需逐条驱逐。
            PlayerLoginDataManager.clearPlayerCache(playerUUID);

            DreamingFishCore.LOGGER.info("玩家 {} 退出信息已记录：IP={}, 时间={}",
                    player.getName().getString(),
                    player.getIpAddress(),
                    playerLoginData.getLastLoginTime());
        } finally {
            AuthSessionGuard.invalidate(player);
        }
    }
}
