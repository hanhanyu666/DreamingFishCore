package com.hhy.dreamingfishcore.gameplay.zombie_system.archer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.minecraft.world.Difficulty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ArcherZombieConfig} 的默认值、解析与校验。
 *
 * <p>只用显式临时路径，不碰 {@code getConfigPath()}——那个走 FML 的配置目录，在纯 JUnit 里
 * 没有引导上下文。</p>
 */
class ArcherZombieConfigTest {

    @Test
    void missingFileWritesDocumentedDefaults() throws IOException {
        Path path = temp.resolve("archer_zombie.json");

        ArcherZombieConfig loaded = ArcherZombieConfig.load(path);

        assertTrue(Files.exists(path), "缺失的配置文件应当被创建为默认文件");
        assertEquals(ArcherZombieConfig.CURRENT_SCHEMA_VERSION, loaded.getSchemaVersion());
        ArcherZombieConfig.Resolved resolved = loaded.resolve();
        // 生命 16（低于原版僵尸的 20）、移速 0.21、射程 16、间隔 40 tick、骨刺伤害 4。
        assertTrue(resolved.enabled());
        assertTrue(resolved.naturalSpawn());
        assertEquals(40, resolved.spawnPercentOfCustomZombie());
        assertEquals(16.0D, resolved.maxHealth(), 1.0E-9D);
        assertEquals(0.21D, resolved.movementSpeed(), 1.0E-9D);
        assertEquals(16.0D, resolved.rangedAttackRange(), 1.0E-9D);
        assertEquals(16, resolved.chargeTicks());
        assertEquals(40, resolved.attackIntervalTicks());
        assertEquals(4.0D, resolved.projectileDamage(), 1.0E-9D);
        assertEquals(1.6D, resolved.projectileSpeed(), 1.0E-9D);
        assertEquals(0.045D, resolved.projectileGravity(), 1.0E-9D);
        assertEquals(24.0D, resolved.projectileMaxRange(), 1.0E-9D);
        assertTrue(resolved.slowEnabled());
        assertTrue(resolved.bleedEnabled());
        // maxPursuitDistance 取「射程 ×1.5」与索敌距离中的较大者。
        assertEquals(35.0D, resolved.maxPursuitDistance(), 1.0E-9D);
    }

    /** 骨刺伤害按原版难度缩放：默认倍率在基础伤害 4 时复刻原版 mob_projectile 的 3 / 4 / 6。 */
    @Test
    void defaultDifficultyMultipliersMatchVanillaMobProjectileFeel() throws IOException {
        Path path = temp.resolve("difficulty.json");

        ArcherZombieConfig loaded = ArcherZombieConfig.load(path);

        assertEquals(0.0D, loaded.damageMultiplier(Difficulty.PEACEFUL), 1.0E-9D);
        assertEquals(0.75D, loaded.damageMultiplier(Difficulty.EASY), 1.0E-9D);
        assertEquals(1.0D, loaded.damageMultiplier(Difficulty.NORMAL), 1.0E-9D);
        assertEquals(1.5D, loaded.damageMultiplier(Difficulty.HARD), 1.0E-9D);
        // 倍率乘上基础伤害 4 就是各难度下的实际伤害。
        assertEquals(3.0D, 4.0D * loaded.damageMultiplier(Difficulty.EASY), 1.0E-9D);
        assertEquals(6.0D, 4.0D * loaded.damageMultiplier(Difficulty.HARD), 1.0E-9D);
        // 未知难度（null）按普通处理，避免任何调用点漏判。
        assertEquals(1.0D, loaded.damageMultiplier(null), 1.0E-9D);
    }

    @Test
    void difficultyMultipliersAreOverridablePerFile() throws IOException {
        Path path = temp.resolve("difficulty_override.json");
        Files.writeString(path, """
                {
                  "difficultyDamageMultiplierEasy": 0.25,
                  "difficultyDamageMultiplierHard": 3.0
                }
                """);

        ArcherZombieConfig loaded = ArcherZombieConfig.load(path);

        assertEquals(0.25D, loaded.damageMultiplier(Difficulty.EASY), 1.0E-9D);
        assertEquals(3.0D, loaded.damageMultiplier(Difficulty.HARD), 1.0E-9D);
        assertEquals(1.0D, loaded.damageMultiplier(Difficulty.NORMAL), 1.0E-9D);
    }

    @Test
    void partialJsonKeepsUntouchedDefaults() throws IOException {
        Path path = temp.resolve("partial.json");
        Files.writeString(path, """
                {
                  "maxHealth": 40.0,
                  "rangedAttackRange": 24.0,
                  "spawnPercentOfCustomZombie": 55,
                  "bleedEnabled": false
                }
                """);

        ArcherZombieConfig.Resolved resolved = ArcherZombieConfig.load(path).resolve();

        assertEquals(40.0D, resolved.maxHealth(), 1.0E-9D);
        assertEquals(24.0D, resolved.rangedAttackRange(), 1.0E-9D);
        assertEquals(55, resolved.spawnPercentOfCustomZombie());
        assertFalse(resolved.bleedEnabled());
        // 未出现在 JSON 里的字段继承默认值。
        assertEquals(0.21D, resolved.movementSpeed(), 1.0E-9D);
        assertEquals(40, resolved.attackIntervalTicks());
    }

    @Test
    void reloadRejectsCrossFieldInversionAndKeepsLastKnownGood() throws IOException {
        Path path = temp.resolve("invalid.json");
        // rangedAttackRange 必须大于 preferredMinDistance；这里刻意写反。
        Files.writeString(path, """
                {
                  "preferredMinDistance": 10.0,
                  "rangedAttackRange": 8.0
                }
                """);

        ArcherZombieConfig.Resolved before = ArcherZombieConfig.current().resolve();
        assertThrows(IllegalStateException.class, () -> ArcherZombieConfig.reload(path));
        // 一份坏配置不能污染正在跑的运行时快照。
        assertEquals(before, ArcherZombieConfig.current().resolve());
    }

    @Test
    void reloadRejectsOutOfRangeValue() throws IOException {
        Path path = temp.resolve("out_of_range.json");
        Files.writeString(path, """
                {
                  "projectileGravity": 5.0
                }
                """);

        assertThrows(IllegalStateException.class, () -> ArcherZombieConfig.reload(path));
    }

    @TempDir
    Path temp;
}
