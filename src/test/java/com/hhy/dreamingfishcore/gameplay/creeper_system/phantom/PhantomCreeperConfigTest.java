package com.hhy.dreamingfishcore.gameplay.creeper_system.phantom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link PhantomCreeperConfig} 的默认值、解析与校验。 */
class PhantomCreeperConfigTest {

    @Test
    void missingFileWritesDocumentedDefaults() throws IOException {
        Path path = temp.resolve("phantom_creeper.json");

        PhantomCreeperConfig loaded = PhantomCreeperConfig.load(path);

        assertTrue(Files.exists(path), "缺失的配置文件应当被创建为默认文件");
        PhantomCreeperConfig.Resolved resolved = loaded.resolve();
        assertEquals(20.0D, resolved.maxHealth(), 1.0E-9D);
        assertEquals(0.25D, resolved.movementSpeed(), 1.0E-9D);
        // 它会飞，索敌必须比原版苦力怕的 16 远，否则「飞过去」这件事根本触发不了。
        assertEquals(48.0D, resolved.followRange(), 1.0E-9D);
        assertEquals(0.28D, resolved.flySpeed(), 1.0E-9D);
        assertEquals(3.0D, resolved.hoverHeight(), 1.0E-9D);
        assertEquals(6.0D, resolved.diveRange(), 1.0E-9D);
        assertEquals(0.45D, resolved.diveSpeed(), 1.0E-9D);
        assertEquals(0.03D, resolved.idleSinkSpeed(), 1.0E-9D);
        assertTrue(resolved.flapSoundEnabled());
    }

    /**
     * 俯冲必须比巡航快。
     *
     * <p>这不是审美问题：俯冲速度更低时，「切到俯冲」这一段反而比盘旋还慢，玩家会看到它悬停加速失败，
     * 「扑过来」的体感直接消失。配置层拦下来，而不是让它悄悄生效。</p>
     */
    @Test
    void diveSpeedSlowerThanFlySpeedIsRejected() throws IOException {
        Path path = temp.resolve("bad-dive.json");
        write(path, "{\"flySpeed\": 0.6, \"diveSpeed\": 0.3}");

        assertThrows(IllegalStateException.class, () -> PhantomCreeperConfig.reload(path));
    }

    @Test
    void partialJsonKeepsUntouchedDefaults() throws IOException {
        Path path = temp.resolve("partial.json");
        write(path, "{\"hoverHeight\": 6.0, \"flySpeed\": 0.35}");

        PhantomCreeperConfig.Resolved resolved = PhantomCreeperConfig.load(path).resolve();

        assertEquals(6.0D, resolved.hoverHeight(), 1.0E-9D);
        assertEquals(0.35D, resolved.flySpeed(), 1.0E-9D);
        // 没写的字段保持默认，不能被写成 0。
        assertEquals(0.45D, resolved.diveSpeed(), 1.0E-9D);
        assertEquals(20.0D, resolved.maxHealth(), 1.0E-9D);
    }

    @Test
    void outOfRangeValuesAreRejected() throws IOException {
        Path negativeHealth = temp.resolve("negative-health.json");
        write(negativeHealth, "{\"maxHealth\": -1.0}");
        assertThrows(IllegalStateException.class, () -> PhantomCreeperConfig.reload(negativeHealth));

        Path absurdHover = temp.resolve("absurd-hover.json");
        write(absurdHover, "{\"hoverHeight\": 100.0}");
        assertThrows(IllegalStateException.class, () -> PhantomCreeperConfig.reload(absurdHover));
    }

    @Test
    void unsupportedSchemaVersionIsRejected() throws IOException {
        Path path = temp.resolve("future.json");
        write(path, "{\"schemaVersion\": 99}");

        assertThrows(IllegalStateException.class, () -> PhantomCreeperConfig.reload(path));
    }

    private static void write(Path path, String json) throws IOException {
        Files.writeString(path, json);
    }

    @TempDir
    Path temp;
}
