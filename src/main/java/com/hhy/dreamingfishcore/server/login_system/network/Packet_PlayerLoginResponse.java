package com.hhy.dreamingfishcore.server.login_system.network;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.server.login_system.AuthSessionGuard;
import com.hhy.dreamingfishcore.server.login_system.PlayerLoginData;
import com.hhy.dreamingfishcore.server.login_system.PlayerLoginDataManager;
import com.hhy.dreamingfishcore.network.DreamingFishCore_NetworkManager;
import com.hhy.dreamingfishcore.server.notice_system.NotificationPushHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 客户端提交的登录/注册响应。服务端永远以网络会话中的 UUID 为准。 */
public class Packet_PlayerLoginResponse implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<Packet_PlayerLoginResponse> TYPE =
            new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            DreamingFishCore.MODID, "login_system/packet_player_login_response"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Packet_PlayerLoginResponse> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(
                    (buf, packet) -> Packet_PlayerLoginResponse.encode(packet, buf),
                    Packet_PlayerLoginResponse::decode);

    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    @Override
    public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }

    // true 是注册，false 是登录
    private final boolean loginOrRegister;
    private final String password;
    /** 保留旧协议字段；服务端会校验它与 sender UUID 一致，不能由客户端选择账号。 */
    private final UUID playerUUID;

    public Packet_PlayerLoginResponse(boolean loginOrRegister, String password, UUID playerUUID) {
        this.loginOrRegister = loginOrRegister;
        this.password = password;
        this.playerUUID = playerUUID;
    }

    public static void encode(Packet_PlayerLoginResponse packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.loginOrRegister);
        buffer.writeUtf(packet.password == null ? "" : packet.password,
                PlayerLoginData.MAX_PASSWORD_LENGTH);
        buffer.writeUUID(packet.playerUUID == null ? EMPTY_UUID : packet.playerUUID);
    }

    public static Packet_PlayerLoginResponse decode(FriendlyByteBuf buffer) {
        boolean loginOrRegister = buffer.readBoolean();
        String password = buffer.readUtf(PlayerLoginData.MAX_PASSWORD_LENGTH);
        UUID playerUUID = buffer.readUUID();
        return new Packet_PlayerLoginResponse(loginOrRegister, password, playerUUID);
    }

    public static void handle(Packet_PlayerLoginResponse packet, IPayloadContext context) {
        if (packet == null || context == null
                || !(context.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        UUID senderUUID = serverPlayer.getUUID();
        // 所有账号查找、密码验证和状态变更都在服务器主线程执行，避免读取到半更新对象。
        context.enqueueWork(() -> processOnServerThread(serverPlayer, packet, senderUUID));
    }

    private static void processOnServerThread(ServerPlayer serverPlayer,
                                              Packet_PlayerLoginResponse packet,
                                              UUID senderUUID) {
        if (!isCurrentPlayer(serverPlayer, senderUUID)) {
            return;
        }
        if (AuthSessionGuard.isAuthenticated(serverPlayer)) {
            sendResult(serverPlayer, false, "当前会话已经完成登录。");
            return;
        }

        // 频率限制必须在主线程、身份校验之前执行：这样伪造 UUID 的请求也会消耗
        // 同一会话的配额，且多个网络线程同时排队时不会绕过锁定窗口。
        if (!AuthSessionGuard.allowLoginAttempt(senderUUID)) {
            long retryAfter = AuthSessionGuard.loginRetryAfterSeconds(senderUUID);
            sendResult(serverPlayer, false,
                    "尝试过于频繁，请在 " + retryAfter + " 秒后重试。");
            return;
        }

        if (!senderUUID.equals(packet.playerUUID)) {
            AuthSessionGuard.recordLoginFailure(senderUUID);
            DreamingFishCore.LOGGER.warn("拒绝玩家 {} 使用其他 UUID 提交登录响应",
                    serverPlayer.getScoreboardName());
            sendResult(serverPlayer, false, "身份校验失败，请重新连接服务器。");
            return;
        }

        String password = packet.password;
        if (!PlayerLoginData.isPasswordLengthValid(password)) {
            AuthSessionGuard.recordLoginFailure(senderUUID);
            sendResult(serverPlayer, false,
                    "密码长度必须在" + PlayerLoginData.MIN_PASSWORD_LENGTH + "到"
                            + PlayerLoginData.MAX_PASSWORD_LENGTH + "个字符之间。");
            return;
        }

        try {
            PlayerLoginData loginData = PlayerLoginDataManager.getLoginData(senderUUID);
            if (packet.loginOrRegister) {
                processRegistration(serverPlayer, senderUUID, password, loginData);
            } else {
                processLogin(serverPlayer, senderUUID, password, loginData);
            }
        } catch (RuntimeException exception) {
            AuthSessionGuard.recordLoginFailure(senderUUID);
            DreamingFishCore.LOGGER.error("玩家 {} 登录流程异常",
                    serverPlayer.getScoreboardName(), exception);
            sendResult(serverPlayer, false, "登录服务暂时不可用，请稍后重试。");
        }
    }

    private static void processRegistration(ServerPlayer player, UUID playerUUID,
                                            String password, PlayerLoginData existingData) {
        if (existingData != null) {
            AuthSessionGuard.recordLoginFailure(playerUUID);
            sendResult(player, false, "您已经注册过了！请直接登录。");
            return;
        }

        PlayerLoginData newData = new PlayerLoginData(
                playerUUID,
                null,
                player.getIpAddress(),
                player.getIpAddress(),
                String.valueOf(System.currentTimeMillis()),
                GameType.SURVIVAL);
        newData.setPassword(password);
        newData.setLoginSessionCompleted(true);
        if (!PlayerLoginDataManager.saveLoginDataChecked(playerUUID, newData)) {
            AuthSessionGuard.recordLoginFailure(playerUUID);
            sendResult(player, false, "登录数据暂时无法保存，请稍后重试。");
            return;
        }

        player.setGameMode(player.getServer().getDefaultGameType());
        AuthSessionGuard.markAuthenticated(player);
        AuthSessionGuard.recordLoginSuccess(playerUUID);
        NotificationPushHelper.sendTopLeftNotification(player, "§a注册成功！享受服务器吧！");
        DreamingFishCore.LOGGER.info("玩家 {} 注册成功，游戏模式: {}",
                player.getScoreboardName(), player.getServer().getDefaultGameType());
        sendResult(player, true, "注册成功！");
    }

    private static void processLogin(ServerPlayer player, UUID playerUUID,
                                     String password, PlayerLoginData loginData) {
        if (loginData == null) {
            AuthSessionGuard.recordLoginFailure(playerUUID);
            sendResult(player, false, "您还未注册！请先注册账号。");
            return;
        }

        if (!loginData.verifyPassword(password)) {
            AuthSessionGuard.recordLoginFailure(playerUUID);
            sendResult(player, false, "密码错误！请重新输入。");
            return;
        }

        GameType gameMode = loginData.getLastGameMode();
        if (gameMode == null) {
            gameMode = player.getServer().getDefaultGameType();
        }
        loginData.setLastLoginIP(player.getIpAddress());
        loginData.setLastLoginTime(String.valueOf(System.currentTimeMillis()));
        loginData.setLoginSessionCompleted(true);
        if (!PlayerLoginDataManager.saveLoginDataChecked(playerUUID, loginData)) {
            loginData.setLoginSessionCompleted(false);
            // 持久化失败时不能继续沿用旧的同 IP 快速登录窗口，否则下次连接会在
            // 没有可靠落盘的情况下再次绕过密码验证。
            loginData.setLastLogoutTime(0L);
            AuthSessionGuard.recordLoginFailure(playerUUID);
            sendResult(player, false, "登录数据暂时无法保存，请稍后重试。");
            return;
        }

        player.setGameMode(gameMode);
        AuthSessionGuard.markAuthenticated(player);
        AuthSessionGuard.recordLoginSuccess(playerUUID);
        NotificationPushHelper.sendTopLeftNotification(player, "§a登录成功！欢迎回来！");
        DreamingFishCore.LOGGER.info("玩家 {} 登录成功，游戏模式恢复为: {}",
                player.getScoreboardName(), gameMode);
        sendResult(player, true, "登录成功！");
    }

    private static boolean isCurrentPlayer(ServerPlayer player, UUID uuid) {
        return player != null
                && uuid != null
                && player.getServer() != null
                && player.getServer().getPlayerList().getPlayer(uuid) == player;
    }

    private static void sendResult(ServerPlayer player, boolean success, String message) {
        DreamingFishCore_NetworkManager.sendToClient(
                new Packet_PlayerLoginResult(success, message), player);
    }
}
