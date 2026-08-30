package com.hhy.dreamingfishcore.gameplay.zombie_system;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZombieSpeciesConfigTest {
    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void resetStoreSession() {
        ZombieSpeciesConfig.reload(temporaryDirectory.resolve("defaults-after-test.json"));
        JsonDataStore.resetSession();
    }

    @Test
    void missingConfigWritesUsableDefaults() throws IOException {
        Path path = temporaryDirectory.resolve("zombie_species.json");

        ZombieSpeciesConfig config = ZombieSpeciesConfig.load(path);
        ZombieSpeciesConfig.ResolvedSettings settings = config.resolveForStage(
                "dreamingfishcore:dream_beginning");

        assertTrue(Files.exists(path));
        assertEquals(ZombieSpeciesConfig.CURRENT_SCHEMA_VERSION, config.getSchemaVersion());
        assertTrue(settings.enabled());
        assertEquals(1.35D, settings.speedMultiplier());
        assertTrue(settings.digging());
        assertTrue(settings.openDoors());
        assertTrue(settings.breakingDoors());
        assertTrue(settings.placingBlocks());
        assertTrue(settings.stacking());
        assertTrue(settings.hearing());
        assertTrue(settings.broadcasting());
        assertTrue(settings.surrounding());
        assertTrue(settings.taskLocationProtection());
        assertTrue(settings.taskLocationRegeneration());
        assertTrue(settings.taskLocationSpawnProtection());
        assertEquals(45.0D, settings.trackingRange());
        assertEquals(45.0D, settings.hearingRange());
        assertEquals(20.0D, settings.broadcastRange());
        assertEquals(4, settings.broadcastMaxHops());
        assertEquals(64, settings.broadcastMaxRecipients());
        assertEquals(4.0D, settings.alertRetargetDistance());
        assertEquals(160, settings.breachCommitmentTicks());
        assertEquals(12.0D, settings.surroundActivationRange());
        assertEquals(2.4D, settings.surroundRadius());
        assertEquals(0.55D, settings.surroundSteeringStrength());
        assertEquals(0, settings.digCooldownTicks());
        assertEquals(4.0D, settings.stackMinimumTargetHeight());
        assertTrue(settings.naturalSpawn());
        assertEquals(120, settings.zombieFamilySpawnPercent());
        assertEquals(40, settings.vanillaZombieSpawnPercent());
        assertEquals(60, settings.customZombieSpawnPercent());
        assertEquals(80, settings.otherMonsterSpawnPercent());

        JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertTrue(json.has("stageOverrides"));
        assertEquals(16, json.get("placementBlocks").getAsInt());
        assertEquals(3, json.get("maxStackHeight").getAsInt());
        assertEquals(45.0D, json.get("trackingRange").getAsDouble());
        assertEquals(20.0D, json.get("broadcastRange").getAsDouble());
        assertTrue(json.get("surrounding").getAsBoolean());
        assertTrue(json.get("taskLocationProtection").getAsBoolean());
        assertTrue(json.get("taskLocationRegeneration").getAsBoolean());
        assertTrue(json.get("taskLocationSpawnProtection").getAsBoolean());
        assertEquals(2.4D, json.get("surroundRadius").getAsDouble());
        assertEquals(4.0D, json.get("alertRetargetDistance").getAsDouble());
        assertEquals(160, json.get("breachCommitmentTicks").getAsInt());
        assertEquals(4.0D, json.get("stackMinimumTargetHeight").getAsDouble());
        assertTrue(json.get("naturalSpawn").getAsBoolean());
        assertEquals(120, json.get("zombieFamilySpawnPercent").getAsInt());
        assertEquals(40, json.get("vanillaZombieSpawnPercent").getAsInt());
        assertEquals(60, json.get("customZombieSpawnPercent").getAsInt());
        assertEquals(80, json.get("otherMonsterSpawnPercent").getAsInt());
        assertFalse(json.has("naturalSpawnWeight"));
        assertFalse(json.has("naturalSpawnMinCount"));
        assertFalse(json.has("naturalSpawnMaxCount"));
        assertFalse(json.has("digTicksPerHardness"));
        assertEquals(0, json.get("digCooldownTicks").getAsInt());
    }

    @Test
    void stageOverrideChangesOnlyFieldsItDeclares() throws IOException {
        Path path = temporaryDirectory.resolve("zombie_species.json");
        Files.writeString(path, """
                {
                  "schemaVersion": 1,
                  "speedMultiplier": 1.25,
                  "stageOverrides": {
                    "dreamingfishcore:dream_beginning": {
                      "digging": false,
                      "placingBlocks": false,
                      "stacking": false,
                      "surrounding": false,
                      "taskLocationProtection": false,
                      "taskLocationRegeneration": false,
                      "taskLocationSpawnProtection": false,
                      "naturalSpawn": false
                    },
                    "dreamingfishcore:afterdream": {
                      "speedMultiplier": 1.6,
                      "trackingRange": 52.0,
                      "broadcastMaxHops": 2,
                      "alertRetargetDistance": 6.0,
                      "breachCommitmentTicks": 240,
                      "surrounding": true,
                      "surroundRadius": 3.0,
                      "stackMinimumTargetHeight": 5.0,
                      "digging": true,
                      "placingBlocks": true,
                      "stacking": true,
                      "taskLocationProtection": true,
                      "taskLocationRegeneration": true,
                      "taskLocationSpawnProtection": true,
                      "naturalSpawn": true,
                      "zombieFamilySpawnPercent": 150,
                      "vanillaZombieSpawnPercent": 40,
                      "customZombieSpawnPercent": 120,
                      "otherMonsterSpawnPercent": 75
                    }
                  }
                }
                """, StandardCharsets.UTF_8);

        ZombieSpeciesConfig config = ZombieSpeciesConfig.load(path);
        ZombieSpeciesConfig.ResolvedSettings early = config.resolveForStage(
                "dreamingfishcore:dream_beginning");
        ZombieSpeciesConfig.ResolvedSettings late = config.resolveForStage(
                "dreamingfishcore:afterdream");

        assertEquals(1.25D, early.speedMultiplier());
        assertFalse(early.digging());
        assertFalse(early.placingBlocks());
        assertFalse(early.stacking());
        assertFalse(early.surrounding());
        assertFalse(early.taskLocationProtection());
        assertFalse(early.taskLocationRegeneration());
        assertFalse(early.taskLocationSpawnProtection());
        assertTrue(early.openDoors());
        assertFalse(early.naturalSpawn());

        assertEquals(1.6D, late.speedMultiplier());
        assertTrue(late.digging());
        assertTrue(late.placingBlocks());
        assertTrue(late.stacking());
        assertEquals(52.0D, late.trackingRange());
        assertEquals(2, late.broadcastMaxHops());
        assertEquals(6.0D, late.alertRetargetDistance());
        assertEquals(240, late.breachCommitmentTicks());
        assertTrue(late.surrounding());
        assertTrue(late.taskLocationProtection());
        assertTrue(late.taskLocationRegeneration());
        assertTrue(late.taskLocationSpawnProtection());
        assertEquals(3.0D, late.surroundRadius());
        assertEquals(5.0D, late.stackMinimumTargetHeight());
        assertTrue(late.openDoors());
        assertTrue(late.naturalSpawn());
        assertEquals(150, late.zombieFamilySpawnPercent());
        assertEquals(40, late.vanillaZombieSpawnPercent());
        assertEquals(120, late.customZombieSpawnPercent());
        assertEquals(75, late.otherMonsterSpawnPercent());
    }

    @Test
    void invalidSpawnPercentFallsBackWithoutOverwritingOperatorFile() throws IOException {
        Path path = temporaryDirectory.resolve("zombie_species.json");
        String invalid = """
                {
                  "schemaVersion": 1,
                  "customZombieSpawnPercent": 1001,
                  "stageOverrides": {}
                }
                """;
        Files.writeString(path, invalid, StandardCharsets.UTF_8);

        ZombieSpeciesConfig config = ZombieSpeciesConfig.load(path);

        assertEquals(120, config.resolveForStage("dreamingfishcore:afterdream")
                .zombieFamilySpawnPercent());
        assertEquals(40, config.resolveForStage("dreamingfishcore:afterdream")
                .vanillaZombieSpawnPercent());
        assertEquals(60, config.resolveForStage("dreamingfishcore:afterdream")
                .customZombieSpawnPercent());
        assertEquals(80, config.resolveForStage("dreamingfishcore:afterdream")
                .otherMonsterSpawnPercent());
        assertEquals(invalid, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void invalidStageOverrideFallsBackWithoutOverwritingOperatorFile() throws IOException {
        Path path = temporaryDirectory.resolve("zombie_species.json");
        String invalid = """
                {
                  "schemaVersion": 1,
                  "stageOverrides": {
                    "dreamingfishcore:afterdream": {
                      "maxStackHeight": 99
                    }
                  }
                }
                """;
        Files.writeString(path, invalid, StandardCharsets.UTF_8);

        ZombieSpeciesConfig config = ZombieSpeciesConfig.load(path);

        assertTrue(config.getStageOverrides().isEmpty());
        assertEquals(3, config.resolveForStage("dreamingfishcore:afterdream").maxStackHeight());
        assertEquals(invalid, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void unsafeBroadcastBudgetIsRejectedWithoutOverwritingOperatorFile() throws IOException {
        Path path = temporaryDirectory.resolve("zombie_species.json");
        String invalid = """
                {
                  "schemaVersion": 1,
                  "broadcastMaxHops": 9,
                  "broadcastMaxRecipients": 1000,
                  "stageOverrides": {}
                }
                """;
        Files.writeString(path, invalid, StandardCharsets.UTF_8);

        ZombieSpeciesConfig config = ZombieSpeciesConfig.load(path);

        assertEquals(4, config.resolveForStage("dreamingfishcore:afterdream").broadcastMaxHops());
        assertEquals(64, config.resolveForStage("dreamingfishcore:afterdream").broadcastMaxRecipients());
        assertEquals(invalid, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void invalidSurroundGeometryIsRejectedWithoutOverwritingOperatorFile() throws IOException {
        Path path = temporaryDirectory.resolve("zombie_species.json");
        String invalid = """
                {
                  "schemaVersion": 1,
                  "surroundActivationRange": 5.0,
                  "surroundRadius": 5.0,
                  "stageOverrides": {}
                }
                """;
        Files.writeString(path, invalid, StandardCharsets.UTF_8);

        ZombieSpeciesConfig config = ZombieSpeciesConfig.load(path);

        assertTrue(config.resolveForStage("dreamingfishcore:afterdream").surrounding());
        assertEquals(12.0D, config.resolveForStage("dreamingfishcore:afterdream")
                .surroundActivationRange());
        assertEquals(2.4D, config.resolveForStage("dreamingfishcore:afterdream")
                .surroundRadius());
        assertEquals(invalid, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void strictReloadKeepsLastKnownGoodSettingsWhenAnEditIsInvalid() throws IOException {
        Path path = temporaryDirectory.resolve("zombie_species.json");
        Files.writeString(path, """
                {
                  "schemaVersion": 1,
                  "speedMultiplier": 1.7,
                  "stageOverrides": {}
                }
                """, StandardCharsets.UTF_8);
        ZombieSpeciesConfig valid = ZombieSpeciesConfig.reload(path);

        Files.writeString(path, "{broken", StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> ZombieSpeciesConfig.reload(path));
        assertSame(valid, ZombieSpeciesConfig.current());
        assertEquals(1.7D, ZombieSpeciesConfig.current()
                .resolveForStage("dreamingfishcore:afterdream")
                .speedMultiplier());
    }

    @Test
    void inGameAbilityTogglePersistsOnlyForTheSelectedStage() throws IOException {
        Path path = temporaryDirectory.resolve("zombie_species.json");
        ZombieSpeciesConfig.reload(path);

        ZombieSpeciesConfig.ResolvedSettings changed = ZombieSpeciesConfig.setAbilityForStage(
                path,
                "dreamingfishcore:dream_beginning",
                ZombieSpeciesConfig.Ability.DIGGING,
                false);

        assertFalse(changed.digging());
        assertTrue(changed.openDoors());
        assertTrue(ZombieSpeciesConfig.current()
                .resolveForStage("dreamingfishcore:afterdream")
                .digging());

        ZombieSpeciesConfig reloaded = ZombieSpeciesConfig.reload(path);
        assertFalse(reloaded.resolveForStage("dreamingfishcore:dream_beginning").digging());
        JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertFalse(json.getAsJsonObject("stageOverrides")
                .getAsJsonObject("dreamingfishcore:dream_beginning")
                .get("digging")
                .getAsBoolean());
    }

    @Test
    void inGameEnableAllTogglePersistsAllAbilitiesForOnlyTheSelectedStage() throws IOException {
        Path path = temporaryDirectory.resolve("zombie_species.json");
        ZombieSpeciesConfig.reload(path);

        ZombieSpeciesConfig.ResolvedSettings changed = ZombieSpeciesConfig.setAllAbilitiesForStage(
                path,
                "dreamingfishcore:dream_beginning",
                true);

        for (ZombieSpeciesConfig.Ability ability : ZombieSpeciesConfig.Ability.values()) {
            assertTrue(changed.isAbilityEnabled(ability), ability.name());
        }
        assertTrue(ZombieSpeciesConfig.current()
                .resolveForStage("dreamingfishcore:afterdream")
                .digging());

        ZombieSpeciesConfig reloaded = ZombieSpeciesConfig.reload(path);
        ZombieSpeciesConfig.ResolvedSettings persisted = reloaded.resolveForStage(
                "dreamingfishcore:dream_beginning");
        for (ZombieSpeciesConfig.Ability ability : ZombieSpeciesConfig.Ability.values()) {
            assertTrue(persisted.isAbilityEnabled(ability), ability.name());
        }
        JsonObject override = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonObject("stageOverrides")
                .getAsJsonObject("dreamingfishcore:dream_beginning");
        for (String field : new String[] {
                "digging", "openDoors", "breakingDoors", "placingBlocks",
                "stacking", "hearing", "broadcasting", "surrounding",
                "taskLocationProtection", "taskLocationRegeneration",
                "taskLocationSpawnProtection"}) {
            assertTrue(override.get(field).getAsBoolean(), field);
        }
    }
}
