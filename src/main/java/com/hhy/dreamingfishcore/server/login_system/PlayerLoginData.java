package com.hhy.dreamingfishcore.server.login_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/** 玩家登录凭据及登录元数据。 */
public class PlayerLoginData {
    public static final int MIN_PASSWORD_LENGTH = 4;
    public static final int MAX_PASSWORD_LENGTH = 64;

    private static final String PASSWORD_SCHEME = "pbkdf2_sha256";
    private static final int PBKDF2_ITERATIONS = 210_000;
    private static final int PBKDF2_KEY_BITS = 256;
    private static final int PBKDF2_SALT_BYTES = 16;
    private static final int MIN_ACCEPTED_ITERATIONS = 100_000;
    private static final int MAX_ACCEPTED_ITERATIONS = 1_000_000;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private UUID playerUUID;
    private String passwordAfterHash;  // 哈希后的密码
    private String registerIP;         // 注册时的 IP
    private String lastLoginIP;        // 上次登录后的 IP
    private String lastLoginTime;      // 上次登录后的时间
    private GameType lastGameMode;
    private long lastLogoutTime;       // 上次退出时间戳（毫秒）- 用于快速登录判断
    private boolean hasCompletedNewPlayerGuidence;  // 是否已完成新手教程
    private boolean loginSessionCompleted;         // 当前会话是否已完成登录验证

    public PlayerLoginData() {
    }

    public PlayerLoginData(UUID playerUUID, String passwordAfterHash, String registerIP,
                           String lastLoginIP, String lastLoginTime, GameType lastGameMode) {
        this.playerUUID = playerUUID;
        this.passwordAfterHash = passwordAfterHash;
        this.registerIP = registerIP;
        this.lastLoginIP = lastLoginIP;
        this.lastLoginTime = lastLoginTime;
        this.lastGameMode = lastGameMode;
        this.lastLogoutTime = 0L;
    }

    /** 设置密码。新账号使用带随机盐的 PBKDF2；旧 SHA-256 记录仍可登录并自动迁移。 */
    public void setPassword(String plainPassword) {
        validatePassword(plainPassword);
        if (playerUUID == null) {
            throw new IllegalStateException("无法为没有 UUID 的玩家设置密码");
        }

        byte[] salt = new byte[PBKDF2_SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] derived = derivePassword(plainPassword, salt, PBKDF2_ITERATIONS);
        this.passwordAfterHash = PASSWORD_SCHEME + "$" + PBKDF2_ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derived);
    }

    /** 验证密码；成功验证旧格式时立即升级为 PBKDF2。 */
    public boolean verifyPassword(String plainPassword) {
        if (!isPasswordLengthValid(plainPassword)
                || passwordAfterHash == null
                || passwordAfterHash.isBlank()) {
            return false;
        }

        if (passwordAfterHash.startsWith(PASSWORD_SCHEME + "$")) {
            return verifyPbkdf2Password(plainPassword, passwordAfterHash);
        }

        boolean legacyMatch = verifyLegacyPassword(plainPassword, passwordAfterHash);
        if (legacyMatch) {
            // 兼容迁移：不会要求玩家重新注册；登录成功后由现有登录流程保存新哈希。
            setPassword(plainPassword);
        }
        return legacyMatch;
    }

    /** 供诊断和迁移测试使用，不暴露明文或哈希内容。 */
    public boolean isUsingModernPasswordHash() {
        return passwordAfterHash != null && passwordAfterHash.startsWith(PASSWORD_SCHEME + "$");
    }

    /** 玩家修改密码。 */
    public boolean changePassword(ServerPlayer targetPlayer, String oldPassword, String newPassword) {
        if (targetPlayer == null || oldPassword == null || newPassword == null) {
            return false;
        }
        if (!isPasswordLengthValid(newPassword)) {
            targetPlayer.sendSystemMessage(Component.literal(
                    "§c新密码长度必须在" + MIN_PASSWORD_LENGTH + "到" + MAX_PASSWORD_LENGTH + "个字符之间"), true);
            return false;
        }

        if (verifyPassword(oldPassword)) {
            setPassword(newPassword);
            targetPlayer.sendSystemMessage(Component.literal("§a您已成功修改了您的密码"), true);
            return true;
        }
        targetPlayer.sendSystemMessage(Component.literal("§c您输入的旧密码有误，无法修改密码"), true);
        return false;
    }

    /** 管理员强制修改密码。 */
    public boolean forgeChangePassword(ServerPlayer adminPlayer, ServerPlayer targetPlayer, String newPassword) {
        if (adminPlayer == null || targetPlayer == null || newPassword == null) {
            return false;
        }

        if (adminPlayer.hasPermissions(2)) {
            if (!isPasswordLengthValid(newPassword)) {
                adminPlayer.sendSystemMessage(Component.literal(
                        "§c新密码长度必须在" + MIN_PASSWORD_LENGTH + "到" + MAX_PASSWORD_LENGTH + "个字符之间"), true);
                return false;
            }
            setPassword(newPassword);
            adminPlayer.sendSystemMessage(Component.literal(
                    "§a您已成功修改玩家§e" + targetPlayer.getName().getString() + "§a的密码"), true);
            targetPlayer.sendSystemMessage(Component.literal(
                    "§a您的登录密码已成功被管理员§e" + adminPlayer.getName().getString() + "§a修改"), true);
            return true;
        }

        adminPlayer.sendSystemMessage(Component.literal("§c不是管理员还想强行修改密码！休想！！"), true);
        return false;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public void setPlayerUUID(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    public String getPasswordAfterHash() {
        return passwordAfterHash;
    }

    public void setPasswordAfterHash(String passwordAfterHash) {
        this.passwordAfterHash = passwordAfterHash;
    }

    public String getRegisterIP() {
        return registerIP;
    }

    public void setRegisterIP(String registerIP) {
        this.registerIP = registerIP;
    }

    public String getLastLoginIP() {
        return lastLoginIP;
    }

    public void setLastLoginIP(String lastLoginIP) {
        this.lastLoginIP = lastLoginIP;
    }

    public String getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(String lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public GameType getLastGameMode() {
        return lastGameMode;
    }

    public void setGameMode(GameType gameMode) {
        this.lastGameMode = gameMode;
    }

    public long getLastLogoutTime() {
        return lastLogoutTime;
    }

    public void setLastLogoutTime(long lastLogoutTime) {
        this.lastLogoutTime = lastLogoutTime;
    }

    /** 检查是否可以快速登录（同 IP 且 5 分钟内退出）。 */
    public boolean canQuickLogin(String currentIp) {
        if (lastLogoutTime <= 0L || currentIp == null || lastLoginIP == null) {
            return false;
        }

        long timeSinceLogout = System.currentTimeMillis() - lastLogoutTime;
        boolean ipMatches = Objects.equals(currentIp, lastLoginIP);
        boolean withinTimeLimit = timeSinceLogout >= 0L && timeSinceLogout <= 300_000L;
        return ipMatches && withinTimeLimit;
    }

    public boolean gethasCompletedNewPlayerGuidence() {
        return hasCompletedNewPlayerGuidence;
    }

    public void setHasCompletedNewPlayerGuidence(boolean hasCompletedNewPlayerGuidence) {
        this.hasCompletedNewPlayerGuidence = hasCompletedNewPlayerGuidence;
    }

    public boolean isLoginSessionCompleted() {
        return loginSessionCompleted;
    }

    public void setLoginSessionCompleted(boolean loginSessionCompleted) {
        this.loginSessionCompleted = loginSessionCompleted;
    }

    private static void validatePassword(String plainPassword) {
        if (!isPasswordLengthValid(plainPassword)) {
            throw new IllegalArgumentException(
                    "密码长度必须在" + MIN_PASSWORD_LENGTH + "到" + MAX_PASSWORD_LENGTH + "个字符之间");
        }
    }

    public static boolean isPasswordLengthValid(String plainPassword) {
        return plainPassword != null
                && plainPassword.length() >= MIN_PASSWORD_LENGTH
                && plainPassword.length() <= MAX_PASSWORD_LENGTH;
    }

    private boolean verifyPbkdf2Password(String plainPassword, String encoded) {
        String[] parts = encoded.split("\\$", -1);
        if (parts.length != 4 || !PASSWORD_SCHEME.equals(parts[0])) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < MIN_ACCEPTED_ITERATIONS || iterations > MAX_ACCEPTED_ITERATIONS) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length < 8 || expected.length == 0) {
                return false;
            }
            byte[] actual = derivePassword(plainPassword, salt, iterations);
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            // 损坏的凭据只能导致登录失败，不能让网络线程抛出异常。
            DreamingFishCore.LOGGER.warn("玩家 {} 的密码凭据格式无效", playerUUID);
            return false;
        }
    }

    private boolean verifyLegacyPassword(String plainPassword, String storedHash) {
        if (playerUUID == null || storedHash.length() != 64
                || !storedHash.matches("[0-9a-fA-F]{64}")) {
            return false;
        }
        try {
            byte[] computed = MessageDigest.getInstance("SHA-256")
                    .digest((plainPassword + playerUUID).getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(computed, hexToBytes(storedHash));
        } catch (NoSuchAlgorithmException | IllegalArgumentException exception) {
            DreamingFishCore.LOGGER.error("兼容旧密码哈希失败", exception);
            return false;
        }
    }

    private static byte[] derivePassword(String plainPassword, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    plainPassword.toCharArray(), salt, iterations, PBKDF2_KEY_BITS);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException exception) {
            DreamingFishCore.LOGGER.error("PBKDF2算法不可用", exception);
            throw new IllegalStateException("PBKDF2 algorithm not available", exception);
        }
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("invalid hex");
            }
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
