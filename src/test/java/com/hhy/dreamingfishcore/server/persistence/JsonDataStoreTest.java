package com.hhy.dreamingfishcore.server.persistence;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDataStoreTest {
    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {}.getType();

    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void resetStoreSession() {
        JsonDataStore.resetSession();
    }

    @Test
    void atomicWriteKeepsPreviousVersionAsBackup() throws IOException {
        Path dataFile = temporaryDirectory.resolve("state.json");

        JsonDataStore.writeAtomic(dataFile, GSON, List.of("first"));
        JsonDataStore.writeAtomic(dataFile, GSON, List.of("second"));

        assertEquals(List.of("second"), read(dataFile));
        assertEquals(List.of("first"), read(backupPath(dataFile)));
    }

    @Test
    void corruptPrimaryRecoversFromBackupWithoutReplacingGoodBackup() throws IOException {
        Path dataFile = temporaryDirectory.resolve("state.json");
        JsonDataStore.writeAtomic(dataFile, GSON, List.of("first"));
        JsonDataStore.writeAtomic(dataFile, GSON, List.of("second"));
        Files.writeString(dataFile, "{broken", StandardCharsets.UTF_8);

        assertEquals(List.of("first"), read(dataFile));

        JsonDataStore.writeAtomic(dataFile, GSON, List.of("recovered"));
        assertEquals(List.of("recovered"), read(dataFile));
        assertEquals(List.of("first"), read(backupPath(dataFile)));
    }

    @Test
    void corruptPrimaryAndBackupBlockFurtherWritesForTheSession() throws IOException {
        Path dataFile = temporaryDirectory.resolve("state.json");
        JsonDataStore.writeAtomic(dataFile, GSON, List.of("first"));
        JsonDataStore.writeAtomic(dataFile, GSON, List.of("second"));
        Files.writeString(dataFile, "{broken-primary", StandardCharsets.UTF_8);
        Files.writeString(backupPath(dataFile), "{broken-backup", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> read(dataFile));
        assertThrows(IOException.class,
                () -> JsonDataStore.writeAtomic(dataFile, GSON, List.of("must-not-overwrite")));
        assertEquals("{broken-primary", Files.readString(dataFile, StandardCharsets.UTF_8));
    }

    @Test
    void legacyGbkJsonIsReadAndMigratedToUtf8() throws IOException {
        Path dataFile = temporaryDirectory.resolve("legacy.json");
        String value = "\u4e2d\u6587";
        Files.write(dataFile, GSON.toJson(List.of(value)).getBytes(Charset.forName("GB18030")));

        assertEquals(List.of(value), read(dataFile));
        assertEquals(List.of(value), GSON.fromJson(
                Files.readString(dataFile, StandardCharsets.UTF_8), STRING_LIST_TYPE));
        assertTrue(Files.exists(dataFile.resolveSibling("legacy.json.gbk.bak")));
    }

    private static List<String> read(Path path) throws IOException {
        return JsonDataStore.read(path, GSON, STRING_LIST_TYPE, List::of);
    }

    private static Path backupPath(Path path) {
        return path.resolveSibling(path.getFileName() + ".bak");
    }
}
