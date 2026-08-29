package com.hhy.dreamingfishcore.gameplay.kill_effect_system;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.server.persistence.JsonDataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillEffectConfigTest {
    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void resetStoreSession() {
        JsonDataStore.resetSession();
    }

    @Test
    void defaultsAreWrittenWhenConfigIsMissing() throws IOException {
        Path path = temporaryDirectory.resolve("kill_effect.json");

        KillEffectConfig config = KillEffectConfig.load(path);

        assertEquals(2, config.getSchemaVersion());
        assertEquals(List.of(2, 3, 4), config.getEligibleRankLevels());
        assertTrue(Files.exists(path));
        JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertEquals(2, json.get("schemaVersion").getAsInt());
        assertEquals(List.of(2, 3, 4), json.getAsJsonArray("eligibleRankLevels")
                .asList().stream().map(element -> element.getAsInt()).toList());
    }

    @Test
    void legacyDefaultIsMigratedToTierTwoAndAbove() throws IOException {
        Path path = temporaryDirectory.resolve("kill_effect.json");
        Files.writeString(path,
                "{\"schemaVersion\":1,\"eligibleRankLevels\":[1,2,3]}",
                StandardCharsets.UTF_8);

        KillEffectConfig config = KillEffectConfig.load(path);

        assertEquals(2, config.getSchemaVersion());
        assertEquals(List.of(2, 3, 4), config.getEligibleRankLevels());
        JsonObject migrated = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertEquals(2, migrated.get("schemaVersion").getAsInt());
        assertEquals(List.of(2, 3, 4), migrated.getAsJsonArray("eligibleRankLevels")
                .asList().stream().map(element -> element.getAsInt()).toList());
    }

    @Test
    void legacyProfessionLevelsAreMappedIntoTheUnifiedTiers() throws IOException {
        Path path = temporaryDirectory.resolve("kill_effect.json");
        Files.writeString(path,
                "{\"schemaVersion\":1,\"eligibleRankLevels\":[5,6,7,8]}",
                StandardCharsets.UTF_8);

        KillEffectConfig config = KillEffectConfig.load(path);

        assertEquals(List.of(2, 3, 4), config.getEligibleRankLevels());
        assertFalse(config.isEligibleRankLevel(1));
        assertTrue(config.isEligibleRankLevel(2));
        assertTrue(config.isEligibleRankLevel(3));
        assertTrue(config.isEligibleRankLevel(4));
    }

    @Test
    void rankLevelsAreNormalizedWithoutReplacingAnExplicitEmptyList() throws IOException {
        Path path = temporaryDirectory.resolve("kill_effect.json");
        Files.writeString(path,
                "{\"schemaVersion\":2,\"eligibleRankLevels\":[3,-1,3,0,null,2,5,4]}",
                StandardCharsets.UTF_8);

        assertEquals(List.of(3, 2, 4), KillEffectConfig.load(path).getEligibleRankLevels());

        Files.writeString(path, "{\"schemaVersion\":2,\"eligibleRankLevels\":[]}",
                StandardCharsets.UTF_8);
        KillEffectConfig emptyConfig = KillEffectConfig.load(path);
        assertTrue(emptyConfig.getEligibleRankLevels().isEmpty());
        assertFalse(emptyConfig.isEligibleRankLevel(1));
    }

    @Test
    void tierOneCanNeverBeEnabledForTheBlackHoleEffect() {
        KillEffectConfig config = new KillEffectConfig(List.of(1, 2, 3, 4));

        assertFalse(config.isEligibleRankLevel(0));
        assertFalse(config.isEligibleRankLevel(1));
        assertTrue(config.isEligibleRankLevel(2));
        assertTrue(config.isEligibleRankLevel(3));
        assertTrue(config.isEligibleRankLevel(4));
    }

    @Test
    void damagedConfigFallsBackInMemoryWithoutOverwritingFile() throws IOException {
        Path path = temporaryDirectory.resolve("kill_effect.json");
        String damaged = "{broken";
        Files.writeString(path, damaged, StandardCharsets.UTF_8);

        KillEffectConfig config = KillEffectConfig.load(path);

        assertEquals(List.of(2, 3, 4), config.getEligibleRankLevels());
        assertEquals(damaged, Files.readString(path, StandardCharsets.UTF_8));
    }
}
