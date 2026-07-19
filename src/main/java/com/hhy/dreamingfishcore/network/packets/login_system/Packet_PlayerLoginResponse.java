package com.hhy.dreamingfishcore.network.packets.login_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.core.login_system.PlayerLoginData;
import com.hhy.dreamingfishcore.core.login_system.PlayerLoginDataManager;
import com.hhy.dreamingfishcore.core.playerattributes_system.death.RevivalInfoManager;
import com.hhy.dreamingfishcore.server.notice.NewPlayerGuide;
import com.hhy.dreamingfishcore.screen.server_screen.tips.TipPushHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class Packet_PlayerLoginResponse {
    //true是注册，false是登录
    private final boolean loginOrRegister;
    private final String password;
    private final UUID playerUUID;

    public Packet_PlayerLoginResponse(boolean loginOrRegister, String password, UUID playerUUID) {
        this.loginOrRegister = loginOrRegister;
        this.password = password;
        this.playerUUID = playerUUID;
    }

    public static void encode(Packet_PlayerLoginResponse playerLoginResponse, FriendlyByteBuf buffer) {
        buffer.writeBoolean(playerLoginResponse.loginOrRegister);
        buffer.writeUtf(playerLoginResponse.password);
        buffer.writeUUID(playerLoginResponse.playerUUID);
    }

    public static Packet_PlayerLoginResponse decode(FriendlyByteBuf buffer) {
        boolean loginOrRegister = buffer.readBoolean();
        String password = buffer.readUtf();
        UUID playerUUID = buffer.readUUID();

        return new Packet_PlayerLoginResponse(loginOrRegister, password, playerUUID);
    }

    public static void handle(Packet_PlayerLoginResponse playerLoginResponse, Supplier<NetworkEvent.Context> contextSupplier) {
        com.hhy.dreamingfishcore.DreamingFishCore.LOGGER.info("收到密码响应包！");

        if (playerLoginResponse == null) {
            com.hhy.dreamingfishcore.DreamingFishCore.LOGGER.error("密码响应包为null！");
            return;
        }

        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer serverPlayer = context.getSender();

        if (serverPlayer == null) {
            com.hhy.dreamingfishcore.DreamingFishCore.LOGGER.error("服务端玩家实例为null！");
            return;
        }

        com.hhy.dreamingfishcore.DreamingFishCore.LOGGER.info("玩家 {} 发送密码响应，loginOrRegister={}",
                serverPlayer.getName().getString(), playerLoginResponse.loginOrRegister);

        boolean loginOrRegister = playerLoginResponse.loginOrRegister;
        String password = playerLoginResponse.password;
        UUID playerUUID = playerLoginResponse.playerUUID;
        PlayerLoginData playerLoginData = PlayerLoginDataManager.getLoginData(playerUUID);

        DreamingFishCore.LOGGER.info("准备提交到主线程执行，loginOrRegister={}", loginOrRegister);
        context.enqueueWork(() -> {
            DreamingFishCore.LOGGER.info("进入主线程执行，loginOrRegister={}", loginOrRegister);
            if (loginOrRegister) {
                //注册
                DreamingFishCore.LOGGER.info("执行注册流程");
                if (playerLoginData != null) {
                    // 已注册
                    DreamingFishCore.LOGGER.info("玩家已注册，拒绝重复注册");
                    sendResult(serverPlayer, false, "您已经注册过了！请直接登录。");
                    return;
                }

                if (password == null || password.length() < 4) {
                    DreamingFishCore.LOGGER.info("密码长度不足");
                    sendResult(serverPlayer, false, "密码长度至少需要4个字符！");
                    return;
                }

                DreamingFishCore.LOGGER.info("开始创建新账号数据");
                // 创建新账号
                PlayerLoginData newPlayerLoginData = new PlayerLoginData(playerUUID, null, serverPlayer.getIpAddress(), null, null, GameType.SURVIVAL);
                newPlayerLoginData.setPlayerUUID(playerUUID);

                DreamingFishCore.LOGGER.info("开始哈希密码（这可能需要几秒钟）");
                newPlayerLoginData.setPassword(password);
                DreamingFishCore.LOGGER.info("密码哈希完成");

                DreamingFishCore.LOGGER.info("保存登录数据到文件");
                // 标记登录验证已完成
                newPlayerLoginData.setLoginSessionCompleted(true);
                PlayerLoginDataManager.saveLoginData(playerUUID, newPlayerLoginData);
                DreamingFishCore.LOGGER.info("登录数据保存完成");

                // 使用服务器的默认游戏模式
                GameType defaultGameMode = serverPlayer.getServer().getDefaultGameType();
                serverPlayer.setGameMode(defaultGameMode);
                DreamingFishCore.LOGGER.info("游戏模式已设置为: " + defaultGameMode);

                DreamingFishCore.LOGGER.info("发送提示消息");
                TipPushHelper.sendTipToPlayer(serverPlayer, "§a注册成功！享受服务器吧！");

                // 检查并发送复活提示
                RevivalInfoManager.checkAndSendRevivalTip(serverPlayer);

                // 发送新手教程（仅未完成过教程的玩家）
                if (!newPlayerLoginData.gethasCompletedNewPlayerGuidence()) {
                    NewPlayerGuide.sendNewPlayerGuide(serverPlayer);
                } else {
                    DreamingFishCore.LOGGER.info("玩家已完成过新手教程，跳过推送");
                }
                DreamingFishCore.LOGGER.info("发送注册结果包");
                sendResult(serverPlayer, true, "注册成功！");
            } else {
                //登录
                if (playerLoginData == null) {
                    // 未注册
                    sendResult(serverPlayer, false, "您还未注册！请先注册账号。");
                    return;
                }

                if (playerLoginData.verifyPassword(password)) {
                    // 登录成功 - 恢复上次保存的游戏模式
                    GameType lastGameMode = playerLoginData.getLastGameMode();
                    if (lastGameMode == null) {
                        // 如果没有保存的游戏模式，使用服务器默认
                        lastGameMode = serverPlayer.getServer().getDefaultGameType();
                    }
                    serverPlayer.setGameMode(lastGameMode);
                    DreamingFishCore.LOGGER.info("玩家 {} 登录成功，游戏模式恢复为: {}",
                            serverPlayer.getName().getString(), lastGameMode);

                    // 标记登录验证已完成
                    playerLoginData.setLoginSessionCompleted(true);
                    PlayerLoginDataManager.saveLoginData(playerUUID, playerLoginData);

                    TipPushHelper.sendTipToPlayer(serverPlayer, "§a登录成功！欢迎回来！");

                    // 检查并发送复活提示
                    RevivalInfoManager.checkAndSendRevivalTip(serverPlayer);
                    sendResult(serverPlayer, true, "登录成功！");

                    if (!playerLoginData.gethasCompletedNewPlayerGuidence()) {
                        NewPlayerGuide.sendNewPlayerGuide(serverPlayer);
                    }
                } else {
                    // 密码错误
                    sendResult(serverPlayer, false, "密码错误！请重新输入。");
                }
            }
        });
        context.setPacketHandled(true);
    }

    private static void sendResult(ServerPlayer player, boolean success, String message) {
        com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager.sendToClient(
                new Packet_PlayerLoginResult(success, message),
                player
        );
    }
}
