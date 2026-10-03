package com.hhy.dreamingfishcore.gameplay.water_gun_system;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link WaterGunConfig} 的默认值、解析与校验。 */
class WaterGunConfigTest {

    @Test
    void missingFileWritesDocumentedDefaults() throws IOException {
        Path path = temp.resolve("water_gun.json");

        WaterGunConfig loaded = WaterGunConfig.load(path);

        assertTrue(Files.exists(path), "缺失的配置文件应当被创建为默认文件");
        assertEquals(WaterGunConfig.CURRENT_SCHEMA_VERSION, loaded.getSchemaVersion());
        WaterGunConfig.Resolved resolved = loaded.resolve();
        assertTrue(resolved.enabled());
        assertEquals(10, resolved.capacity());
        assertEquals(6, resolved.fireIntervalTicks());
        assertEquals(1.2D, resolved.jetSpeed(), 1.0E-9D);
        assertEquals(0.02D, resolved.jetGravity(), 1.0E-9D);
        assertEquals(8.0D, resolved.jetMaxRange(), 1.0E-9D);
        assertEquals(1, resolved.rustStagesPerHit());
        assertTrue(resolved.extinguishEntities());
        assertTrue(resolved.extinguishBlockFire());
        assertTrue(resolved.refillToFull());
        assertEquals(5, resolved.refillAmount());
        assertTrue(resolved.drainWaterCauldron(), "默认应当消耗炼药锅里的水，否则等于无限水源");
    }

    @Test
    void partialJsonKeepsUntouchedDefaults() throws IOException {
        Path path = temp.resolve("partial.json");
        Files.writeString(path, """
                {
                  "capacity": 30,
                  "jetMaxRange": 16.0,
                  "extinguishBlockFire": false
                }
                """);

        WaterGunConfig.Resolved resolved = WaterGunConfig.load(path).resolve();

        assertEquals(30, resolved.capacity());
        assertEquals(16.0D, resolved.jetMaxRange(), 1.0E-9D);
        assertFalse(resolved.extinguishBlockFire());
        // 未出现在 JSON 里的字段继承默认值。
        assertEquals(6, resolved.fireIntervalTicks());
        assertTrue(resolved.extinguishEntities());
    }

    @Test
    void reloadRejectsOutOfRangeValueAndKeepsLastKnownGood() throws IOException {
        Path path = temp.resolve("out_of_range.json");
        Files.writeString(path, """
                {
                  "capacity": 0
                }
                """);

        WaterGunConfig.Resolved before = WaterGunConfig.current().resolve();
        assertThrows(IllegalStateException.class, () -> WaterGunConfig.reload(path));
        assertEquals(before, WaterGunConfig.current().resolve());
    }

    @Test
    void reloadRejectsNonFiniteNumbers() throws IOException {
        Path path = temp.resolve("nan.json");
        Files.writeString(path, """
                {
                  "jetSpeed": -3.0
                }
                """);

        assertThrows(IllegalStateException.class, () -> WaterGunConfig.reload(path));
    }

    @Test
    void wrongSchemaVersionIsRejected() throws IOException {
        Path path = temp.resolve("schema.json");
        Files.writeString(path, """
                {
                  "schemaVersion": 42
                }
                """);

        assertThrows(IllegalStateException.class, () -> WaterGunConfig.reload(path));
    }

    @TempDir
    Path temp;
}
