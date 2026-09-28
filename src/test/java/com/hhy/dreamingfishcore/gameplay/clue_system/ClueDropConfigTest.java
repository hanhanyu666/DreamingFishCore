package com.hhy.dreamingfishcore.gameplay.clue_system;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 线索掉落配置：默认值、越界修正、损坏回退、关闭开关。 */
class ClueDropConfigTest {

    private static Path write(Path dir, String json) throws Exception {
        Path path = dir.resolve("clue_drop.json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return path;
    }

    @Test
    void missingFileCreatesDefaults(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("clue_drop.json");

        ClueDropConfig config = ClueDropConfig.load(path);

        assertTrue(Files.exists(path), "缺失配置应写入默认文件");
        assertTrue(config.isEnabled());
        assertEquals(0.01D, config.getNormalZombieDropPercent(), 1.0E-9D);
        assertEquals(0.01D, config.getSiegeZombieDropPercent(), 1.0E-9D);
    }

    @Test
    void subOnePercentIsPreserved(@TempDir Path dir) throws Exception {
        Path path = write(dir, """
                {"schemaVersion": 1, "enabled": true,
                 "normalZombieDropPercent": 0.01, "siegeZombieDropPercent": 0.5}
                """);

        ClueDropConfig config = ClueDropConfig.load(path);

        assertEquals(0.01D, config.getNormalZombieDropPercent(), 1.0E-9D,
                "小数概率不能被修正成 0 或 1");
        assertEquals(0.5D, config.getSiegeZombieDropPercent(), 1.0E-9D);
    }

    @Test
    void outOfRangePercentIsClampedAndWrittenBack(@TempDir Path dir) throws Exception {
        Path path = write(dir, """
                {"schemaVersion": 1, "enabled": true,
                 "normalZombieDropPercent": 150, "siegeZombieDropPercent": -5}
                """);

        ClueDropConfig config = ClueDropConfig.load(path);

        assertEquals(100.0D, config.getNormalZombieDropPercent(), 1.0E-9D);
        assertEquals(0.0D, config.getSiegeZombieDropPercent(), 1.0E-9D);

        String writtenBack = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(writtenBack.contains("100.0"),
                "修正结果应写回文件：" + writtenBack);
    }

    @Test
    void disabledConfigReportsZeroPercent(@TempDir Path dir) throws Exception {
        Path path = write(dir, """
                {"schemaVersion": 1, "enabled": false,
                 "normalZombieDropPercent": 70, "siegeZombieDropPercent": 90}
                """);

        ClueDropConfig config = ClueDropConfig.load(path);

        assertFalse(config.isEnabled());
        assertEquals(0.0D, config.getNormalZombieDropPercent(), 1.0E-9D, "关闭后不再掉落");
        assertEquals(0.0D, config.getSiegeZombieDropPercent(), 1.0E-9D, "关闭后不再掉落");
    }

    @Test
    void damagedFileFallsBackToDefaultsWithoutOverwriting(@TempDir Path dir) throws Exception {
        String damaged = "{ this is not json";
        Path path = write(dir, damaged);

        ClueDropConfig config = ClueDropConfig.load(path);

        assertEquals(0.01D, config.getNormalZombieDropPercent(), 1.0E-9D);
        assertEquals(0.01D, config.getSiegeZombieDropPercent(), 1.0E-9D);
        assertEquals(damaged, Files.readString(path, StandardCharsets.UTF_8),
                "损坏的服主文件不能被默认值覆盖");
    }

    @Test
    void unknownSchemaVersionFallsBackToDefaults(@TempDir Path dir) throws Exception {
        Path path = write(dir, """
                {"schemaVersion": 99, "enabled": true,
                 "normalZombieDropPercent": 5, "siegeZombieDropPercent": 20}
                """);

        ClueDropConfig config = ClueDropConfig.load(path);

        assertEquals(0.01D, config.getNormalZombieDropPercent(), 1.0E-9D);
        assertEquals(0.01D, config.getSiegeZombieDropPercent(), 1.0E-9D);
    }
}
