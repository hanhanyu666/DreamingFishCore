package com.hhy.dreamingfishcore.gameplay.creeper_system.ender;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link EnderCreeperConfig} 的默认值、解析与校验。 */
class EnderCreeperConfigTest {

    @Test
    void missingFileWritesDocumentedDefaults() throws IOException {
        Path path = temp.resolve("ender_creeper.json");

        EnderCreeperConfig loaded = EnderCreeperConfig.load(path);

        assertTrue(Files.exists(path), "缺失的配置文件应当被创建为默认文件");
        EnderCreeperConfig.Resolved resolved = loaded.resolve();
        assertEquals(20.0D, resolved.maxHealth(), 1.0E-9D);
        assertEquals(0.28D, resolved.movementSpeed(), 1.0E-9D);
        // 索敌要比原版苦力怕（16）远，否则 20 格外的玩家它根本看不见，「会瞬移」没意义。
        assertEquals(40.0D, resolved.followRange(), 1.0E-9D);
        assertTrue(resolved.teleportEnabled());
        assertEquals(80, resolved.teleportCooldownTicks());
        assertEquals(5.0D, resolved.teleportMinDistance(), 1.0E-9D);
        assertEquals(32.0D, resolved.teleportMaxDistance(), 1.0E-9D);
        assertEquals(3.0D, resolved.landingRadius(), 1.0E-9D);
        assertEquals(0.35D, resolved.dodgeChance(), 1.0E-9D);
        assertTrue(resolved.auraParticles());
        // 默认不怕水：开了等于给一条「下雨天它自己化了」的免费解法，这是取舍不是遗漏。
        assertFalse(resolved.waterHurts());
    }

    /**
     * 最小距离必须小于最大距离。
     *
     * <p>两者相等（或写反）时触发窗口是空的：瞬移永远不生效，而配置看起来完全正常——
     * 典型的静默失效，所以在加载期就拒绝。</p>
     */
    @Test
    void emptyTeleportWindowIsRejected() throws IOException {
        Path same = temp.resolve("same.json");
        write(same, "{\"teleportMinDistance\": 8.0, \"teleportMaxDistance\": 8.0}");
        assertThrows(IllegalStateException.class, () -> EnderCreeperConfig.reload(same));

        Path reversed = temp.resolve("reversed.json");
        write(reversed, "{\"teleportMinDistance\": 20.0, \"teleportMaxDistance\": 10.0}");
        assertThrows(IllegalStateException.class, () -> EnderCreeperConfig.reload(reversed));
    }

    @Test
    void partialJsonKeepsUntouchedDefaults() throws IOException {
        Path path = temp.resolve("partial.json");
        write(path, "{\"dodgeChance\": 0.0, \"teleportCooldownTicks\": 200}");

        EnderCreeperConfig.Resolved resolved = EnderCreeperConfig.load(path).resolve();

        assertEquals(0.0D, resolved.dodgeChance(), 1.0E-9D);
        assertEquals(200, resolved.teleportCooldownTicks());
        // 没写的字段保持默认。
        assertEquals(20.0D, resolved.maxHealth(), 1.0E-9D);
        assertTrue(resolved.teleportEnabled());
    }

    @Test
    void outOfRangeValuesAreRejected() throws IOException {
        Path chance = temp.resolve("chance.json");
        write(chance, "{\"dodgeChance\": 1.5}");
        assertThrows(IllegalStateException.class, () -> EnderCreeperConfig.reload(chance));

        Path radius = temp.resolve("radius.json");
        write(radius, "{\"landingRadius\": 0.0}");
        assertThrows(IllegalStateException.class, () -> EnderCreeperConfig.reload(radius));
    }

    private static void write(Path path, String json) throws IOException {
        Files.writeString(path, json);
    }

    @TempDir
    Path temp;
}
