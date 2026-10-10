package com.hhy.dreamingfishcore.gameplay.zombie_system.boss;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 仅使用临时路径和基础数值快照，不调用配置目录入口或引导任何游戏注册表。 */
class ZombieCommanderConfigTest {
    private static final String[] DOUBLE_FIELDS = {
            "maxHealth", "movementSpeed", "followRange", "attackDamage", "armor", "knockbackResistance",
            "preferredMinDistance", "rangedAttackRange", "projectileDamage", "projectileSpeed",
            "projectileGravity", "projectileMaxRange", "enrageHealthFraction", "particleIntensity"
    };
    private static final String[] PERIOD_FIELDS = {
            "chargeTicks", "attackIntervalTicks", "enragedAttackIntervalTicks", "burstSpacingTicks",
            "summonCooldownTicks", "summonChargeTicks"
    };
    private static final String[] COUNT_FIELDS = {
            "burstCount", "enragedBurstCount", "summonCount", "enragedSummonCount"
    };

    @TempDir
    Path temp;

    @Test
    void constructorHasAllDocumentedDefaults() {
        assertDefaults(new ZombieCommanderConfig().resolve());
        assertEquals(1, ZombieCommanderConfig.CURRENT_SCHEMA_VERSION);
        assertEquals("zombie_commander.json", ZombieCommanderConfig.CONFIG_FILE_NAME);
    }

    @Test
    void missingFileAtomicallyWritesCompleteDefaultsAndLeavesNoTemporaryFile() throws IOException {
        Path path = temp.resolve("nested").resolve(ZombieCommanderConfig.CONFIG_FILE_NAME);
        ZombieCommanderConfig before = ZombieCommanderConfig.current();

        ZombieCommanderConfig loaded = ZombieCommanderConfig.load(path);

        assertTrue(Files.isRegularFile(path));
        assertEquals(ZombieCommanderConfig.CURRENT_SCHEMA_VERSION, loaded.getSchemaVersion());
        assertDefaults(loaded.resolve());
        JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals(32, json.size(), "文件应包含 schemaVersion 和全部 31 个运行时字段");
        assertEquals(1, json.get("schemaVersion").getAsInt());
        assertEquals(loaded.resolve(), ZombieCommanderConfig.load(path).resolve());
        assertSame(before, ZombieCommanderConfig.current(), "load 不应修改运行时配置");
        assertOnlyConfigFile(path);
    }

    @Test
    void partialJsonOverridesSpecifiedFieldsAndKeepsEveryOtherDefault() throws IOException {
        Path path = temp.resolve("partial.json");
        String json = """
                {
                  "enabled": false,
                  "maxHealth": 300.0,
                  "chargeTicks": 12,
                  "projectileGravity": 0.0,
                  "summonCount": 4
                }
                """;
        Files.writeString(path, json);

        ZombieCommanderConfig.Resolved resolved = ZombieCommanderConfig.load(path).resolve();
        ZombieCommanderConfig.Resolved defaults = new ZombieCommanderConfig().resolve();
        ZombieCommanderConfig.Resolved expected = new ZombieCommanderConfig.Resolved(
                false, 300.0D, defaults.movementSpeed(), defaults.followRange(), defaults.attackDamage(),
                defaults.armor(), defaults.knockbackResistance(), defaults.preferredMinDistance(),
                defaults.rangedAttackRange(), 12, defaults.attackIntervalTicks(),
                defaults.enragedAttackIntervalTicks(), defaults.burstCount(), defaults.enragedBurstCount(),
                defaults.burstSpacingTicks(), defaults.projectileDamage(), defaults.projectileSpeed(),
                0.0D, defaults.projectileMaxRange(), defaults.summonCooldownTicks(),
                defaults.summonChargeTicks(), 4, defaults.enragedSummonCount(), defaults.maxMinions(),
                defaults.minionLifetimeTicks(), defaults.enrageHealthFraction(), defaults.experienceReward(),
                defaults.particleIntensity(), defaults.meleeRange(), defaults.meleeChainRange(),
                defaults.meleeComboCooldownTicks());

        assertEquals(expected, resolved);
        assertFalse(resolved.enabled());
        assertEquals(json, Files.readString(path), "读取部分配置不应重写用户文件");
    }

    @Test
    void validReloadPublishesNewSnapshotAndDoesNotMutateTheOldOne() throws IOException {
        ZombieCommanderConfig previous = installKnownGood();
        ZombieCommanderConfig.Resolved oldSnapshot = previous.resolve();
        Path path = temp.resolve("reload.json");
        Files.writeString(path, """
                {
                  "schemaVersion": 1,
                  "enabled": false,
                  "maxHealth": 480,
                  "attackIntervalTicks": 60,
                  "enragedBurstCount": 6,
                  "summonCooldownTicks": 200,
                  "maxMinions": 8
                }
                """);

        ZombieCommanderConfig reloaded = ZombieCommanderConfig.reload(path);

        assertSame(reloaded, ZombieCommanderConfig.current());
        assertFalse(reloaded.resolve().enabled());
        assertEquals(480.0D, reloaded.resolve().maxHealth());
        assertEquals(60, reloaded.resolve().attackIntervalTicks());
        assertEquals(6, reloaded.resolve().enragedBurstCount());
        assertEquals(200, reloaded.resolve().summonCooldownTicks());
        assertEquals(8, reloaded.resolve().maxMinions());
        assertEquals(oldSnapshot, previous.resolve());
        assertEquals(300.0D, oldSnapshot.maxHealth());
    }

    @Test
    void correctedFileCanReloadAfterARejectedReload() throws IOException {
        installKnownGood();
        assertRejected("{");
        Path path = temp.resolve("invalid.json");
        Files.writeString(path, "{\"maxHealth\":360,\"burstCount\":6}");

        ZombieCommanderConfig reloaded = ZombieCommanderConfig.reload(path);

        assertSame(reloaded, ZombieCommanderConfig.current());
        assertEquals(360.0D, reloaded.resolve().maxHealth());
        assertEquals(6, reloaded.resolve().burstCount());
    }

    @Test
    void emptyObjectIsAValidPartialConfigurationWithAllDefaults() throws IOException {
        Path path = temp.resolve("empty_object.json");
        Files.writeString(path, "{}");

        assertDefaults(ZombieCommanderConfig.reload(path).resolve());
        assertEquals("{}", Files.readString(path));
    }

    @Test
    void reloadOfMissingFilePublishesAndWritesDefaults() throws IOException {
        installKnownGood();
        Path path = temp.resolve("reload_missing").resolve(ZombieCommanderConfig.CONFIG_FILE_NAME);

        ZombieCommanderConfig reloaded = ZombieCommanderConfig.reload(path);

        assertSame(reloaded, ZombieCommanderConfig.current());
        assertDefaults(reloaded.resolve());
        assertEquals(reloaded.resolve(), ZombieCommanderConfig.load(path).resolve());
        assertOnlyConfigFile(path);
    }

    @Test
    void badLoadFallsBackInMemoryWithoutOverwritingFileOrCurrent() throws IOException {
        ZombieCommanderConfig before = installKnownGood();
        Path path = temp.resolve("bad_load.json");
        for (String invalid : new String[] {"{", "", "null", "{\"maxHealth\":1025}",
                "{\"schemaVersion\":99}", "{\"rangedAttackRange\":37}"}) {
            Files.writeString(path, invalid);
            assertDefaults(ZombieCommanderConfig.load(path).resolve());
            assertEquals(invalid, Files.readString(path));
            assertSame(before, ZombieCommanderConfig.current());
        }
    }

    @Test
    void reloadRejectsScalarRangesAndRetainsTheExactLastKnownGoodObject() throws IOException {
        installKnownGood();
        String[][] ranges = {
                {"maxHealth", "0", "1025"},
                {"movementSpeed", "0.049", "1.001"},
                {"followRange", "0", "129"},
                {"attackDamage", "-1", "101"},
                {"armor", "-1", "31"},
                {"knockbackResistance", "-0.01", "1.01"},
                {"preferredMinDistance", "-1", "129"},
                {"rangedAttackRange", "0", "129"},
                {"projectileDamage", "-1", "101"},
                {"projectileSpeed", "0.049", "5.001"},
                {"projectileGravity", "-0.01", "1.01"},
                {"projectileMaxRange", "0", "129"},
                {"maxMinions", "0", "25"},
                {"minionLifetimeTicks", "19", "12001"},
                {"enrageHealthFraction", "-0.01", "1.01"},
                {"experienceReward", "-1", "10001"},
                {"particleIntensity", "-0.01", "3.01"}
        };
        for (String[] range : ranges) {
            assertRejected("{\"" + range[0] + "\":" + range[1] + "}");
            assertRejected("{\"" + range[0] + "\":" + range[2] + "}");
        }
        for (String field : COUNT_FIELDS) {
            for (int invalid : new int[] {-1, 0, 13}) {
                assertRejected("{\"" + field + "\":" + invalid + "}");
            }
        }
    }

    @Test
    void everyPeriodMustBePositiveAndBounded() throws IOException {
        installKnownGood();
        for (String field : PERIOD_FIELDS) {
            for (int invalid : new int[] {-1, 0, 12001}) {
                assertRejected("{\"" + field + "\":" + invalid + "}");
            }
        }
    }

    @Test
    void distanceOrderingRejectsEachBrokenLinkIncludingEqualPreferredDistance() throws IOException {
        installKnownGood();
        for (String json : new String[] {
                "{\"preferredMinDistance\":24}",
                "{\"preferredMinDistance\":25}",
                "{\"rangedAttackRange\":37}",
                "{\"projectileMaxRange\":23}",
                "{\"projectileMaxRange\":41}",
                "{\"followRange\":35}"
        }) {
            assertRejected(json);
        }
    }

    @Test
    void inclusiveRangeBoundariesAndEqualOuterDistancesAreValid() throws IOException {
        Path path = temp.resolve("boundaries.json");
        Files.writeString(path, """
                {
                  "maxHealth": 1024, "movementSpeed": 1, "followRange": 128,
                  "attackDamage": 100, "armor": 30, "knockbackResistance": 1,
                  "preferredMinDistance": 127, "rangedAttackRange": 128,
                  "chargeTicks": 12000, "attackIntervalTicks": 12000,
                  "enragedAttackIntervalTicks": 12000, "burstCount": 12,
                  "enragedBurstCount": 12, "burstSpacingTicks": 12000,
                  "projectileDamage": 100, "projectileSpeed": 5, "projectileGravity": 1,
                  "projectileMaxRange": 128, "summonCooldownTicks": 12000,
                  "summonChargeTicks": 12000, "summonCount": 12, "enragedSummonCount": 12,
                  "maxMinions": 24, "minionLifetimeTicks": 12000,
                  "enrageHealthFraction": 1, "experienceReward": 10000, "particleIntensity": 3,
                  "meleeRange": 8, "meleeChainRange": 12, "meleeComboCooldownTicks": 12000
                }
                """);
        ZombieCommanderConfig.Resolved upper = ZombieCommanderConfig.reload(path).resolve();
        assertEquals(new ZombieCommanderConfig.Resolved(
                true, 1024.0D, 1.0D, 128.0D, 100.0D, 30.0D, 1.0D,
                127.0D, 128.0D, 12000, 12000, 12000, 12, 12, 12000,
                100.0D, 5.0D, 1.0D, 128.0D, 12000, 12000, 12, 12, 24, 12000, 1.0D, 10000, 3.0D,
                8.0D, 12.0D, 12000), upper);

        Files.writeString(path, """
                {
                  "maxHealth": 1, "movementSpeed": 0.05, "followRange": 1,
                  "attackDamage": 0, "armor": 0, "knockbackResistance": 0,
                  "preferredMinDistance": 0, "rangedAttackRange": 1,
                  "chargeTicks": 1, "attackIntervalTicks": 1, "enragedAttackIntervalTicks": 1,
                  "burstCount": 1, "enragedBurstCount": 1, "burstSpacingTicks": 1,
                  "projectileDamage": 0, "projectileSpeed": 0.05, "projectileGravity": 0,
                  "projectileMaxRange": 1, "summonCooldownTicks": 1, "summonChargeTicks": 1,
                  "summonCount": 1, "enragedSummonCount": 1, "maxMinions": 1,
                  "minionLifetimeTicks": 20, "enrageHealthFraction": 0, "experienceReward": 0,
                  "particleIntensity": 0,
                  "meleeRange": 0.5, "meleeChainRange": 0.5, "meleeComboCooldownTicks": 1
                }
                """);
        ZombieCommanderConfig.Resolved lower = ZombieCommanderConfig.reload(path).resolve();
        assertEquals(new ZombieCommanderConfig.Resolved(
                true, 1.0D, 0.05D, 1.0D, 0.0D, 0.0D, 0.0D,
                0.0D, 1.0D, 1, 1, 1, 1, 1, 1,
                0.0D, 0.05D, 0.0D, 1.0D, 1, 1, 1, 1, 1, 20, 0.0D, 0, 0.0D,
                0.5D, 0.5D, 1), lower);
    }

    @Test
    void emptyMalformedNullAndNonObjectConfigsAreRejected() throws IOException {
        installKnownGood();
        for (String invalid : new String[] {"", " \n\t ", "null", "{", "[]", "true", "1", "\"配置\""}) {
            assertRejected(invalid);
        }
        Path defaultPath = temp.resolve("null_field_defaults.json");
        ZombieCommanderConfig.load(defaultPath);
        JsonObject defaults = JsonParser.parseString(Files.readString(defaultPath)).getAsJsonObject();
        for (String field : defaults.keySet()) {
            assertRejected("{\"" + field + "\":null}");
        }
    }

    @Test
    void wrongOrFractionalSchemaAndFractionalIntegerValuesAreRejected() throws IOException {
        installKnownGood();
        for (String version : new String[] {"-1", "0", "2", "99", "1.5", "\"未知版本\""}) {
            assertRejected("{\"schemaVersion\":" + version + "}");
        }
        for (String field : PERIOD_FIELDS) {
            assertRejected("{\"" + field + "\":1.5}");
        }
        for (String field : COUNT_FIELDS) {
            assertRejected("{\"" + field + "\":1.5}");
        }
        for (String field : new String[] {"maxMinions", "minionLifetimeTicks", "experienceReward"}) {
            assertRejected("{\"" + field + "\":20.5}");
        }
    }

    @Test
    void everyDoubleFieldRejectsNonFiniteValues() throws IOException {
        installKnownGood();
        for (String field : DOUBLE_FIELDS) {
            for (String invalid : new String[] {"\"NaN\"", "\"Infinity\"", "\"-Infinity\"", "1e309", "-1e309"}) {
                assertRejected("{\"" + field + "\":" + invalid + "}");
            }
        }
    }

    private ZombieCommanderConfig installKnownGood() throws IOException {
        Path path = temp.resolve("known_good.json");
        Files.writeString(path, """
                {"schemaVersion":1,"enabled":true,"maxHealth":300,"burstCount":4}
                """);
        return ZombieCommanderConfig.reload(path);
    }

    private void assertRejected(String json) throws IOException {
        Path path = temp.resolve("invalid.json");
        Files.writeString(path, json);
        ZombieCommanderConfig before = ZombieCommanderConfig.current();
        ZombieCommanderConfig.Resolved snapshot = before.resolve();

        assertThrows(IllegalStateException.class, () -> ZombieCommanderConfig.reload(path), json);
        assertSame(before, ZombieCommanderConfig.current(), json);
        assertEquals(snapshot, ZombieCommanderConfig.current().resolve(), json);
        assertEquals(json, Files.readString(path), "拒绝重载不得覆盖坏文件");
    }

    private static void assertOnlyConfigFile(Path path) throws IOException {
        try (var files = Files.list(path.getParent())) {
            assertEquals(java.util.List.of(path), files.toList(), "原子创建后不应留下临时文件或多余备份");
        }
    }

    private static void assertDefaults(ZombieCommanderConfig.Resolved resolved) {
        assertTrue(resolved.enabled());
        assertEquals(240.0D, resolved.maxHealth());
        assertEquals(0.26D, resolved.movementSpeed());
        assertEquals(40.0D, resolved.followRange());
        assertEquals(6.0D, resolved.attackDamage());
        assertEquals(4.0D, resolved.armor());
        assertEquals(0.5D, resolved.knockbackResistance());
        assertEquals(8.0D, resolved.preferredMinDistance());
        assertEquals(24.0D, resolved.rangedAttackRange());
        assertEquals(24, resolved.chargeTicks());
        assertEquals(50, resolved.attackIntervalTicks());
        assertEquals(30, resolved.enragedAttackIntervalTicks());
        assertEquals(3, resolved.burstCount());
        assertEquals(5, resolved.enragedBurstCount());
        assertEquals(6, resolved.burstSpacingTicks());
        assertEquals(6.0D, resolved.projectileDamage());
        assertEquals(1.25D, resolved.projectileSpeed());
        assertEquals(0.015D, resolved.projectileGravity());
        assertEquals(36.0D, resolved.projectileMaxRange());
        assertEquals(400, resolved.summonCooldownTicks());
        assertEquals(40, resolved.summonChargeTicks());
        assertEquals(2, resolved.summonCount());
        assertEquals(3, resolved.enragedSummonCount());
        assertEquals(6, resolved.maxMinions());
        assertEquals(1200, resolved.minionLifetimeTicks());
        assertEquals(0.5D, resolved.enrageHealthFraction());
        assertEquals(80, resolved.experienceReward());
        assertEquals(1.0D, resolved.particleIntensity());
        assertEquals(2.6D, resolved.meleeRange());
        assertEquals(3.6D, resolved.meleeChainRange());
        assertEquals(40, resolved.meleeComboCooldownTicks());
    }
}
