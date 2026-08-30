package com.hhy.dreamingfishcore.server.login_system;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLoginDataTest {
    @Test
    void legacySha256CredentialsCanLoginAndAreMigrated() throws Exception {
        UUID uuid = UUID.randomUUID();
        String password = "legacy-pass";
        String legacyHash = toHex(MessageDigest.getInstance("SHA-256")
                .digest((password + uuid).getBytes(StandardCharsets.UTF_8)));
        PlayerLoginData data = new PlayerLoginData(uuid, legacyHash, "127.0.0.1",
                null, null, null);

        assertFalse(data.isUsingModernPasswordHash());
        assertTrue(data.verifyPassword(password));
        assertTrue(data.isUsingModernPasswordHash());
        assertTrue(data.verifyPassword(password));
        assertFalse(data.verifyPassword("wrong-pass"));
    }

    @Test
    void newCredentialsUseRandomSalt() {
        UUID uuid = UUID.randomUUID();
        PlayerLoginData first = new PlayerLoginData();
        first.setPlayerUUID(uuid);
        first.setPassword("same-pass");
        PlayerLoginData second = new PlayerLoginData();
        second.setPlayerUUID(uuid);
        second.setPassword("same-pass");

        assertTrue(first.isUsingModernPasswordHash());
        assertNotEquals(first.getPasswordAfterHash(), second.getPasswordAfterHash());
        assertTrue(first.verifyPassword("same-pass"));
        assertTrue(second.verifyPassword("same-pass"));
    }

    @Test
    void passwordLengthIsBoundedForNewCredentials() {
        PlayerLoginData data = new PlayerLoginData();
        data.setPlayerUUID(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> data.setPassword("123"));
        assertThrows(IllegalArgumentException.class,
                () -> data.setPassword("x".repeat(PlayerLoginData.MAX_PASSWORD_LENGTH + 1)));
    }

    @Test
    void futureLogoutTimestampCannotEnableQuickLogin() {
        PlayerLoginData data = new PlayerLoginData();
        data.setLastLoginIP("127.0.0.1");
        data.setLastLogoutTime(System.currentTimeMillis() + 60_000L);
        assertFalse(data.canQuickLogin("127.0.0.1"));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
