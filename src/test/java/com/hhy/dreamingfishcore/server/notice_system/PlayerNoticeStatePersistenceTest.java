package com.hhy.dreamingfishcore.server.notice_system;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerNoticeStatePersistenceTest {
    private static final Gson GSON = new Gson();
    private static final Type STATE_TYPE = new TypeToken<Map<UUID, Set<Integer>>>() {}.getType();

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void resetStoreSession() {
        JsonDataStore.resetSession();
    }

    @Test
    void missingDeliveryStateMigratesReadIdsWithoutCreatingAnEmptyFile() throws IOException {
        UUID player = UUID.randomUUID();
        Path deliveryPath = temporaryDirectory.resolve("player_delivery_state.json");
        Map<UUID, Set<Integer>> readState = Map.of(player, Set.of(3, 8));

        PlayerNoticeStatePersistence.LoadResult result =
                PlayerNoticeStatePersistence.readOrMigrate(
                        deliveryPath, GSON, STATE_TYPE, readState);

        assertTrue(result.migrated());
        assertTrue(result.dirty());
        assertEquals(readState, result.values());
        assertFalse(Files.exists(deliveryPath));
    }

    @Test
    void freshEmptyStateDoesNotForceCreationOfDeliveryFile() throws IOException {
        Path deliveryPath = temporaryDirectory.resolve("player_delivery_state.json");

        PlayerNoticeStatePersistence.LoadResult result =
                PlayerNoticeStatePersistence.readOrMigrate(
                        deliveryPath, GSON, STATE_TYPE, Map.of());

        assertFalse(result.migrated());
        assertFalse(result.dirty());
        assertTrue(result.values().isEmpty());
        assertFalse(Files.exists(deliveryPath));
    }

    @Test
    void existingZeroByteFilesAreWriteProtectedIndependently() throws IOException {
        Path readPath = temporaryDirectory.resolve("player_read_state.json");
        Path deliveryPath = temporaryDirectory.resolve("player_delivery_state.json");
        Files.createFile(readPath);
        Files.createFile(deliveryPath);

        PlayerNoticeStatePersistence.ReadResult readResult =
                PlayerNoticeStatePersistence.readWithWriteProtection(
                        readPath, GSON, STATE_TYPE);
        PlayerNoticeStatePersistence.LoadResult deliveryResult =
                PlayerNoticeStatePersistence.readOrMigrate(
                        deliveryPath, GSON, STATE_TYPE, Map.of());

        assertFalse(readResult.writesEnabled());
        assertFalse(deliveryResult.writesEnabled());
        assertTrue(readResult.values().isEmpty());
        assertTrue(deliveryResult.values().isEmpty());

        Path validIndependentFile = temporaryDirectory.resolve("independent.json");
        JsonDataStore.writeAtomic(validIndependentFile, GSON, Map.of(UUID.randomUUID(), Set.of(6)));
        assertTrue(Files.size(validIndependentFile) > 0L);
        assertEquals(0L, Files.size(readPath));
        assertEquals(0L, Files.size(deliveryPath));
    }

    @Test
    void migratedDeliveryStateIsADeepCopyAndDoesNotChangeReadState() throws IOException {
        UUID player = UUID.randomUUID();
        Set<Integer> readIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
        readIds.add(10);
        Map<UUID, Set<Integer>> readState = new java.util.HashMap<>();
        readState.put(player, readIds);
        Path deliveryPath = temporaryDirectory.resolve("player_delivery_state.json");

        PlayerNoticeStatePersistence.LoadResult result =
                PlayerNoticeStatePersistence.readOrMigrate(
                        deliveryPath, GSON, STATE_TYPE, readState);

        Set<Integer> deliveryIds = result.values().get(player);
        assertNotSame(readIds, deliveryIds);
        deliveryIds.add(20);
        readIds.add(30);
        assertEquals(Set.of(10, 20), deliveryIds);
        assertEquals(Set.of(10, 30), readIds);
    }

    @Test
    void corruptDeliveryFileDoesNotBlockIndependentReadFile() throws IOException {
        UUID player = UUID.randomUUID();
        Path readPath = temporaryDirectory.resolve("player_read_state.json");
        Path deliveryPath = temporaryDirectory.resolve("player_delivery_state.json");
        Map<UUID, Set<Integer>> first = Map.of(player, Set.of(1));
        Map<UUID, Set<Integer>> second = Map.of(player, Set.of(2));

        JsonDataStore.writeAtomic(readPath, GSON, first);
        JsonDataStore.writeAtomic(deliveryPath, GSON, first);
        JsonDataStore.writeAtomic(deliveryPath, GSON, second);
        Files.writeString(deliveryPath, "{broken-primary", StandardCharsets.UTF_8);
        Files.writeString(
                deliveryPath.resolveSibling("player_delivery_state.json.bak"),
                "{broken-backup",
                StandardCharsets.UTF_8);

        assertThrows(IOException.class,
                () -> PlayerNoticeStatePersistence.read(deliveryPath, GSON, STATE_TYPE));
        assertEquals(first, PlayerNoticeStatePersistence.read(readPath, GSON, STATE_TYPE));

        JsonDataStore.writeAtomic(readPath, GSON, second);
        assertEquals(second, PlayerNoticeStatePersistence.read(readPath, GSON, STATE_TYPE));
        assertEquals("{broken-primary", Files.readString(deliveryPath, StandardCharsets.UTF_8));
    }
}
